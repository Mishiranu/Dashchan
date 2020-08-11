package com.mishiranu.dashchan.content.service;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Process;
import chan.content.ChanConfiguration;
import chan.content.ChanManager;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpValidator;
import chan.util.StringUtils;
import com.mishiranu.dashchan.content.NetworkObserver;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;

public class WatcherService extends Service implements FavoritesStorage.Observer, Handler.Callback {
	private static final HashMap<String, ExecutorService> EXECUTORS = new HashMap<>();

	static {
		for (String chanName : ChanManager.getInstance().getAllChanNames()) {
			ChanConfiguration configuration = ChanConfiguration.get(chanName);
			if (configuration.getOption(ChanConfiguration.OPTION_READ_POSTS_COUNT)) {
				EXECUTORS.put(chanName, ConcurrentUtils.newSingleThreadPool(60000, "ThreadsWatcher", chanName,
						Process.THREAD_PRIORITY_BACKGROUND));
			}
		}
	}

	private static final int MESSAGE_STOP = 0;
	private static final int MESSAGE_UPDATE = 1;
	private static final int MESSAGE_RESULT = 2;

	private final Handler handler = new Handler(this);
	private final LinkedHashMap<String, WatcherItem> watching = new LinkedHashMap<>();
	private final HashMap<String, WatcherTask> tasks = new HashMap<>();

	private boolean mergeChans = false;
	private final HashMap<Client, String> clients = new HashMap<>();
	private final HashSet<Client> startedClients = new HashSet<>();

	private boolean started;
	private int interval;
	private boolean refreshPeriodically = true;

	public static final int NEW_POSTS_COUNT_DELETED = -1;
	public static final int POSTS_COUNT_DIFFERENCE_DELETED = Integer.MIN_VALUE;

	public enum State {DISABLED, UNAVAILABLE, ENABLED, BUSY}

	public static class WatcherItem {
		public final String chanName;
		public final String boardName;
		public final String threadNumber;

		private final String key;

		private int postsCount;
		private int newPostsCount;
		private boolean hasNewPosts;
		private boolean error;
		private HttpValidator validator;

		private long lastUpdateTime;
		private boolean lastWasAvailable;
		private State lastState = State.DISABLED;

		public WatcherItem(FavoritesStorage.FavoriteItem favoriteItem) {
			this(favoriteItem.chanName, favoriteItem.boardName, favoriteItem.threadNumber, favoriteItem.postsCount,
					favoriteItem.newPostsCount, favoriteItem.hasNewPosts, favoriteItem.watcherValidator);
		}

		public WatcherItem(String chanName, String boardName, String threadNumber, int postsCount, int newPostsCount,
				boolean hasNewPosts, HttpValidator validator) {
			this.chanName = chanName;
			this.boardName = boardName;
			this.threadNumber = threadNumber;
			this.key = makeKey(chanName, boardName, threadNumber);
			this.postsCount = postsCount;
			this.newPostsCount = newPostsCount;
			this.hasNewPosts = hasNewPosts;
			this.validator = validator;
		}

		public boolean compare(String chanName, String boardName, String threadNumber) {
			return this.chanName.equals(chanName) && StringUtils.equals(this.boardName, boardName) &&
					this.threadNumber.equals(threadNumber);
		}

		public int getPostsCountDifference() {
			return calculatePostsCountDifference(newPostsCount, postsCount);
		}

		public boolean hasNewPosts() {
			return hasNewPosts;
		}

		public boolean isError() {
			return error;
		}

		public State getLastState() {
			return lastState;
		}
	}

	private static int calculatePostsCountDifference(int newPostsCount, int postsCount) {
		if (newPostsCount == NEW_POSTS_COUNT_DELETED) {
			return POSTS_COUNT_DIFFERENCE_DELETED;
		}
		return newPostsCount - postsCount;
	}

	private boolean destroyed = false;

	@Override
	public void onCreate() {
		super.onCreate();
		ArrayList<FavoritesStorage.FavoriteItem> favoriteItems = FavoritesStorage.getInstance().getThreads(null);
		boolean available = isAvailable();
		for (FavoritesStorage.FavoriteItem favoriteItem : favoriteItems) {
			if (favoriteItem.watcherEnabled && EXECUTORS.containsKey(favoriteItem.chanName)) {
				WatcherItem watcherItem = new WatcherItem(favoriteItem);
				watcherItem.lastState = available ? State.ENABLED : State.UNAVAILABLE;
				watcherItem.lastWasAvailable = available;
				watching.put(watcherItem.key, watcherItem);
			}
		}
		FavoritesStorage.getInstance().getObservable().register(this);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		destroyed = true;
		stop(true);
		watching.clear();
		clients.clear();
		startedClients.clear();
		FavoritesStorage.getInstance().getObservable().unregister(this);
	}

	@Override
	public Binder onBind(Intent intent) {
		return new Binder();
	}

	public class Binder extends android.os.Binder {
		public void addClient(Client client) {
			if (!destroyed) {
				clients.put(client, null);
			}
		}

		public void removeClient(Client client) {
			if (!destroyed) {
				clients.remove(client);
			}
		}

		public void setActiveChanName(Client client, String chanName) {
			if (!destroyed) {
				boolean success = !StringUtils.equals(chanName, clients.put(client, chanName));
				if (success) {
					onUpdateConfiguration();
				}
			}
		}

		public void start(Client client) {
			if (!destroyed) {
				if (startedClients.isEmpty()) {
					WatcherService.this.start();
				}
				startedClients.add(client);
			}
		}

		public void stop(Client client) {
			if (!destroyed) {
				startedClients.remove(client);
				if (startedClients.isEmpty()) {
					WatcherService.this.stop(false);
				}
			}
		}

		public void update() {
			if (!destroyed) {
				WatcherService.this.update();
			}
		}

		public void updatePreferences() {
			if (!destroyed) {
				WatcherService.this.updatePreferences(true);
			}
		}

		public WatcherItem getItem(String chanName, String boardName, String threadNumber) {
			return WatcherService.this.getItem(chanName, boardName, threadNumber);
		}

		public TemporalCountData countNewPosts(FavoritesStorage.FavoriteItem favoriteItem) {
			return WatcherService.this.countNewPosts(favoriteItem);
		}
	}

	private void notifyUpdate(WatcherItem watcherItem, State state) {
		watcherItem.lastState = state;
		for (Client client : clients.keySet()) {
			client.notifyUpdate(watcherItem, state);
		}
	}

	private static String makeKey(String chanName, String boardName, String threadNumber) {
		return chanName + "/" + boardName + "/" + threadNumber;
	}

	private WatcherItem getItem(String chanName, String boardName, String threadNumber) {
		return watching.get(makeKey(chanName, boardName, threadNumber));
	}

	private WatcherItem getItem(FavoritesStorage.FavoriteItem favoriteItem) {
		return getItem(favoriteItem.chanName, favoriteItem.boardName, favoriteItem.threadNumber);
	}

	@Override
	public void onFavoritesUpdate(FavoritesStorage.FavoriteItem favoriteItem, int action) {
		switch (action) {
			case FavoritesStorage.ACTION_WATCHER_ENABLE: {
				if (favoriteItem.threadNumber == null) {
					throw new IllegalArgumentException();
				}
				if (EXECUTORS.containsKey(favoriteItem.chanName)) {
					WatcherItem watcherItem = new WatcherItem(favoriteItem);
					watching.put(watcherItem.key, watcherItem);
					if (isActiveChanName(favoriteItem.chanName)) {
						enqueue(watcherItem, true);
					}
				}
				break;
			}
			case FavoritesStorage.ACTION_WATCHER_DISABLE: {
				WatcherItem watcherItem = getItem(favoriteItem);
				if (watcherItem != null) {
					watching.remove(watcherItem.key);
					WatcherTask task = tasks.remove(watcherItem.key);
					if (task != null) {
						task.cancel(false);
					}
					handler.removeMessages(MESSAGE_UPDATE, watcherItem);
					if (isActiveChanName(favoriteItem.chanName)) {
						notifyUpdate(watcherItem, State.DISABLED);
					}
				}
				break;
			}
			case FavoritesStorage.ACTION_WATCHER_SYNCHRONIZE: {
				WatcherItem watcherItem = getItem(favoriteItem);
				if (watcherItem != null) {
					watcherItem.postsCount = favoriteItem.postsCount;
					watcherItem.newPostsCount = favoriteItem.newPostsCount;
					watcherItem.hasNewPosts = false;
					notifyUpdate(watcherItem, null); // State not changed
				}
				break;
			}
		}
	}

	private void onUpdateConfiguration() {
		if (started) {
			cancelAll();
			updateAllSinceNow();
		}
	}

	private void start() {
		handler.removeMessages(MESSAGE_STOP);
		if (!started) {
			started = true;
			updatePreferences(false);
			if (refreshPeriodically) {
				updateAllSinceNow();
			} else {
				for (WatcherItem watcherItem : watching.values()) {
					if (isActiveChanName(watcherItem.chanName)) {
						notifyUpdate(watcherItem, State.ENABLED);
					}
				}
			}
		}
	}

	private void stop(boolean now) {
		if (started) {
			handler.removeMessages(MESSAGE_STOP);
			if (now) {
				started = false;
				handler.removeMessages(MESSAGE_UPDATE);
				cancelAll();
			} else {
				handler.sendEmptyMessageDelayed(MESSAGE_STOP, 1000);
			}
		}
	}

	private void update() {
		if (started) {
			updateAll();
		}
	}

	private void updatePreferences(boolean restart) {
		int interval = Preferences.getWatcherRefreshInterval() * 1000;
		boolean refreshPeriodically = Preferences.isWatcherRefreshPeriodically();
		boolean mergeChans = Preferences.isMergeChans();
		boolean changed = this.interval != interval ||
				this.refreshPeriodically != refreshPeriodically || this.mergeChans != mergeChans;
		this.interval = interval;
		this.refreshPeriodically = refreshPeriodically;
		this.mergeChans = mergeChans;
		if (restart && changed && !clients.isEmpty()) {
			stop(true);
			start();
		}
	}

	public static class TemporalCountData {
		public int postsCountDifference;
		public boolean hasNewPosts;
		public boolean isError;

		private void set(int postsCountDifference, boolean hasNewPosts, boolean isError) {
			this.postsCountDifference = postsCountDifference;
			this.hasNewPosts = hasNewPosts;
			this.isError = isError;
		}
	}

	private static final TemporalCountData TEMPORAL_COUNT_DATA = new TemporalCountData();

	private TemporalCountData countNewPosts(FavoritesStorage.FavoriteItem favoriteItem) {
		WatcherItem watcherItem = getItem(favoriteItem);
		if (watcherItem != null) {
			TEMPORAL_COUNT_DATA.set(watcherItem.getPostsCountDifference(), watcherItem.hasNewPosts,
					watcherItem.isError());
			return TEMPORAL_COUNT_DATA;
		}
		return null;
	}

	private boolean isActiveChanName(String chanName) {
		return mergeChans || clients.containsValue(chanName);
	}

	private long lastAvailableCheck;
	private boolean lastAvailableValue;

	private boolean isAvailable() {
		long time = System.currentTimeMillis();
		if (time - lastAvailableCheck >= 1000) {
			lastAvailableCheck = time;
			lastAvailableValue = !refreshPeriodically || !Preferences.isWatcherWifiOnly()
					|| NetworkObserver.getInstance().isWifiConnected();
		}
		return lastAvailableValue;
	}

	private void updateAll() {
		handler.removeMessages(MESSAGE_UPDATE);
		for (WatcherItem watcherItem : watching.values()) {
			if (isActiveChanName(watcherItem.chanName)) {
				enqueue(watcherItem, true);
			}
		}
	}

	private void updateAllSinceNow() {
		handler.removeMessages(MESSAGE_UPDATE);
		long time = System.currentTimeMillis();
		boolean available = isAvailable();
		for (WatcherItem watcherItem : watching.values()) {
			if (isActiveChanName(watcherItem.chanName)) {
				long dt = time - watcherItem.lastUpdateTime;
				if (dt >= interval) {
					enqueue(watcherItem, available);
				} else {
					handler.sendMessageDelayed(handler.obtainMessage(MESSAGE_UPDATE, watcherItem), interval - dt);
				}
			}
		}
	}

	private void enqueue(WatcherItem watcherItem, boolean available) {
		if (!tasks.containsKey(watcherItem.key)) {
			if (available) {
				WatcherTask task = new WatcherTask(watcherItem);
				tasks.put(watcherItem.key, task);
				EXECUTORS.get(watcherItem.chanName).execute(task);
				watcherItem.lastWasAvailable = true;
				notifyUpdate(watcherItem, State.BUSY);
			} else {
				enqueueDelayed(watcherItem);
				if (watcherItem.lastWasAvailable) {
					watcherItem.lastWasAvailable = false;
					notifyUpdate(watcherItem, State.UNAVAILABLE);
				}
			}
		}
	}

	private void enqueueDelayed(WatcherItem watcherItem) {
		if (refreshPeriodically) {
			handler.sendMessageDelayed(handler.obtainMessage(MESSAGE_UPDATE, watcherItem), interval);
		}
	}

	private void cancelAll() {
		boolean available = isAvailable();
		for (WatcherTask task : tasks.values()) {
			task.cancel(false);
			notifyUpdate(task.watcherItem, available ? State.ENABLED : State.UNAVAILABLE);
		}
		tasks.clear();
	}

	private static class Result {
		public final WatcherItem watcherItem;
		public int newPostsCount = NEW_POSTS_COUNT_DELETED - 1;
		public HttpValidator validator;
		public boolean error = false;
		public boolean interrupt = false;
		public boolean notModified = false;

		public Result(WatcherItem watcherItem) {
			this.watcherItem = watcherItem;
		}
	}

	private static class WatcherRunnable implements Callable<Result> {
		private final HttpHolder holder = new HttpHolder();
		private final Result result;

		public WatcherRunnable(WatcherItem watcherItem) {
			result = new Result(watcherItem);
		}

		@Override
		public Result call() {
			WatcherItem watcherItem = result.watcherItem;
			try {
				ChanPerformer performer = ChanPerformer.get(watcherItem.chanName);
				ChanPerformer.ReadPostsCountResult result = performer.safe()
						.onReadPostsCount(new ChanPerformer.ReadPostsCountData(watcherItem.boardName,
						watcherItem.threadNumber, 5000, 5000, holder, watcherItem.validator));
				this.result.newPostsCount = result != null ? result.postsCount : 0;
				HttpValidator validator = result != null ? result.validator : null;
				if (validator == null) {
					validator = holder.getValidator();
				}
				this.result.validator = validator;
			} catch (HttpException e) {
				int responseCode = e.getResponseCode();
				if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
					result.notModified = true;
				} else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND ||
						responseCode == HttpURLConnection.HTTP_GONE) {
					result.newPostsCount = NEW_POSTS_COUNT_DELETED;
					result.validator = null;
				} else {
					// Interrupt on client or server error (most likely chan is down)
					if (responseCode >= HttpURLConnection.HTTP_INTERNAL_ERROR) {
						result.interrupt = true;
					}
					result.error = true;
				}
			} catch (ExtensionException | InvalidResponseException e) {
				e.getErrorItemAndHandle();
				result.error = true;
			} finally {
				holder.cleanup();
			}
			return result;
		}
	}

	private class WatcherTask extends FutureTask<Result> {
		public final WatcherItem watcherItem;

		public WatcherTask(WatcherItem watcherItem) {
			super(new WatcherRunnable(watcherItem));
			this.watcherItem = watcherItem;
		}

		@Override
		protected void done() {
			try {
				handler.obtainMessage(MESSAGE_RESULT, get()).sendToTarget();
			} catch (Exception e) {
				// Task cancelled
			}
		}
	}

	@Override
	public boolean handleMessage(Message msg) {
		switch (msg.what) {
			case MESSAGE_STOP: {
				stop(true);
				return true;
			}
			case MESSAGE_UPDATE: {
				WatcherItem watcherItem = (WatcherItem) msg.obj;
				enqueue(watcherItem, isAvailable());
				return true;
			}
			case MESSAGE_RESULT: {
				Result result = (Result) msg.obj;
				WatcherItem watcherItem = result.watcherItem;
				tasks.remove(watcherItem.key);
				long time = System.currentTimeMillis();
				boolean available = isAvailable();
				watcherItem.lastUpdateTime = time;
				watcherItem.lastWasAvailable = available;
				if (result.interrupt) {
					String chanName = result.watcherItem.chanName;
					watcherItem.error = true;
					Iterator<WatcherTask> iterator = tasks.values().iterator();
					while (iterator.hasNext()) {
						WatcherTask task = iterator.next();
						WatcherItem cancelItem = task.watcherItem;
						if (cancelItem.chanName.equals(chanName)) {
							task.cancel(false);
							cancelItem.error = true;
							cancelItem.lastUpdateTime = time;
							cancelItem.lastWasAvailable = available;
							enqueueDelayed(cancelItem);
							notifyUpdate(cancelItem, available ? State.ENABLED : State.UNAVAILABLE);
							iterator.remove();
						}
					}
				} else {
					watcherItem.error = result.error;
					if (!result.notModified) {
						int newPostsCount = result.newPostsCount;
						if (newPostsCount >= NEW_POSTS_COUNT_DELETED) {
							if (newPostsCount > watcherItem.newPostsCount && watcherItem.newPostsCount > 1
									|| newPostsCount > watcherItem.postsCount) {
								watcherItem.hasNewPosts = true;
							}
							watcherItem.newPostsCount = newPostsCount;
							watcherItem.validator = result.validator;
							FavoritesStorage.getInstance().modifyWatcherData(watcherItem.chanName,
									watcherItem.boardName, watcherItem.threadNumber, watcherItem.newPostsCount,
									watcherItem.hasNewPosts, watcherItem.validator);
						}
					}
				}
				enqueueDelayed(watcherItem);
				notifyUpdate(watcherItem, available ? State.ENABLED : State.UNAVAILABLE);
				if (watcherItem.newPostsCount == NEW_POSTS_COUNT_DELETED && Preferences.isWatcherAutoDisable()) {
					FavoritesStorage.getInstance().toggleWatcher(watcherItem.chanName,
							watcherItem.boardName, watcherItem.threadNumber);
				}
				return true;
			}
		}
		return false;
	}

	public static final class Client implements ServiceConnection {
		private final Callback callback;
		private Binder binder;

		private String chanName = null;
		private boolean started = false;

		public Client(Callback callback) {
			this.callback = callback;
		}

		public void bind(Context context) {
			context.bindService(new Intent(context, WatcherService.class), this, BIND_AUTO_CREATE);
		}

		public void unbind(Context context) {
			unbindInternal();
			context.unbindService(this);
		}

		public void updateConfiguration(String chanName) {
			this.chanName = chanName;
			if (binder != null) {
				binder.setActiveChanName(this, chanName);
			}
		}

		public void start() {
			started = true;
			if (binder != null) {
				binder.start(this);
			}
		}

		public void stop() {
			started = false;
			if (binder != null) {
				binder.stop(this);
			}
		}

		public void update() {
			if (binder != null) {
				binder.update();
			}
		}

		public void updatePreferences() {
			if (binder != null) {
				binder.updatePreferences();
			}
		}

		public WatcherItem getItem(String chanName, String boardName, String threadNumber) {
			return binder != null ? binder.getItem(chanName, boardName, threadNumber) : null;
		}

		public TemporalCountData countNewPosts(FavoritesStorage.FavoriteItem favoriteItem) {
			TemporalCountData temporalCountData = binder != null ? binder.countNewPosts(favoriteItem) : null;
			if (temporalCountData == null) {
				temporalCountData = TEMPORAL_COUNT_DATA;
				temporalCountData.set(calculatePostsCountDifference(favoriteItem.newPostsCount,
						favoriteItem.postsCount), favoriteItem.hasNewPosts, false);
			}
			return temporalCountData;
		}

		private void unbindInternal() {
			if (binder != null) {
				if (started) {
					binder.stop(this);
				}
				binder.removeClient(this);
				binder = null;
			}
		}

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			binder = (Binder) service;
			binder.addClient(this);
			if (chanName != null) {
				binder.setActiveChanName(this, chanName);
			}
			if (started) {
				binder.start(this);
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			unbindInternal();
		}

		public void notifyUpdate(WatcherItem watcherItem, State state) {
			callback.onWatcherUpdate(watcherItem, state);
		}

		public interface Callback {
			public void onWatcherUpdate(WatcherItem watcherItem, State state);
		}
	}
}
