package com.mishiranu.dashchan.content.service;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import chan.content.Chan;
import chan.content.ChanConfiguration;
import chan.content.RedirectException;
import chan.util.CommonUtils;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.content.NetworkObserver;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.WatcherNotifications;
import com.mishiranu.dashchan.content.async.ExecutorTask;
import com.mishiranu.dashchan.content.async.ReadPostsTask;
import com.mishiranu.dashchan.content.database.ChanDatabase;
import com.mishiranu.dashchan.content.database.PagesDatabase;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.PendingUserPost;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.widget.ThemeEngine;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

public class WatcherService extends BaseService {
	public static class Counter {
		public enum State {ENABLED, UNAVAILABLE, DISABLED}

		public static final Counter INITIAL = new Counter(State.DISABLED, false, 0, false, false);

		public final State state;
		public final boolean running;
		public final int newCount;
		public final boolean deleted;
		public final boolean error;

		public Counter(State state, boolean running, int newCount, boolean deleted, boolean error) {
			this.state = state;
			this.running = running;
			this.newCount = newCount;
			this.deleted = deleted;
			this.error = error;
		}
	}

	public interface Client {
		interface Callback {
			boolean isWatcherClientForeground();
			void onWatcherUpdate(String chanName, String boardName, String threadNumber, Counter counter);
		}

		Callback getCallback();
		void setCallback(Callback callback);
		void updateConfiguration(String chanName);
		void notifyForeground();
		void refreshAll(String chanName);
		boolean isWatcherSupported(Chan chan);
		Counter getCounter(String chanName, String boardName, String threadNumber);
		Session newSession(String chanName, String boardName, String threadNumber, Session.Callback callback);
	}

	public interface Session {
		interface Callback {
			interface ConsumeReplies {
				void consume();
			}

			void onReadPostsSuccess(PagesDatabase.Cache.State cacheState, ConsumeReplies consumeReplies);
			void onReadPostsRedirect(RedirectException.Target target);
			void onReadPostsFail(ErrorItem errorItem);
		}

		boolean refresh(boolean reload, int checkInterval);
		void notifyExtracted();
		void notifyEraseStarted();
		boolean hasTask();
		void destroy();
	}

	private interface InternalSession extends Session.Callback {
		boolean isUpdateBlocked();
		void notifyRefreshStarted();
	}

	private static class ThreadKey {
		public final String chanName;
		public final String boardName;
		public final String threadNumber;

		public ThreadKey(String chanName, String boardName, String threadNumber) {
			this.chanName = chanName;
			this.boardName = boardName;
			this.threadNumber = threadNumber;
		}

		@Override
		public boolean equals(Object o) {
			if (o == this) {
				return true;
			}
			if (o instanceof ThreadKey) {
				ThreadKey threadKey = (ThreadKey) o;
				return chanName.equals(threadKey.chanName) &&
						CommonUtils.equals(boardName, threadKey.boardName) &&
						threadNumber.equals(threadKey.threadNumber);
			}
			return false;
		}

		@Override
		public int hashCode() {
			int prime = 31;
			int result = 1;
			result = prime * result + chanName.hashCode();
			result = prime * result + (boardName != null ? boardName.hashCode() : 0);
			result = prime * result + threadNumber.hashCode();
			return result;
		}
	}

	private static class Binder extends android.os.Binder {
		public final WatcherService service;

		public Binder(WatcherService service) {
			this.service = service;
		}
	}

	@Override
	public Binder onBind(Intent intent) {
		return new Binder(this);
	}

	public static Client getClient(ComponentActivity activity) {
		return new ViewModelProvider(activity).get(WatcherService.ViewModel.class);
	}

	public static class ViewModel extends ServiceViewModel<Binder> implements Client {
		private final HashSet<ViewModelSession> sessions = new HashSet<>();

		private Callback callback;
		private String chanName;

		public ViewModel() {
			super(WatcherService.class);
		}

		private WatcherService getService() {
			Binder binder = getBinder();
			return binder != null ? binder.service : null;
		}

		@Override
		public void onConnected(Binder binder) {
			binder.service.registerClient(this, chanName);
			for (ViewModelSession session : sessions) {
				session.handleRegister(binder.service, false);
			}
			if (callback != null && !binder.service.watcherItems.isEmpty()) {
				for (ThreadKey threadKey : binder.service.workWatcherKeys) {
					Counter counter = binder.service.getCounter(threadKey);
					callback.onWatcherUpdate(threadKey.chanName, threadKey.boardName, threadKey.threadNumber, counter);
				}
			}
		}

		@Override
		public void onDisconnected(Binder binder) {
			for (ViewModelSession session : sessions) {
				session.handleUnregister(binder.service);
			}
			binder.service.unregisterClient(this);
		}

		@Override
		public Callback getCallback() {
			return callback;
		}

		@Override
		public void setCallback(Callback callback) {
			this.callback = callback;
		}

		@Override
		public void updateConfiguration(String chanName) {
			this.chanName = chanName;
			WatcherService service = getService();
			if (service != null) {
				service.registerClient(this, chanName);
			}
		}

		@Override
		public void notifyForeground() {
			WatcherService service = getService();
			if (service != null) {
				service.startNextFinished(true);
			}
		}

		@Override
		public void refreshAll(String chanName) {
			WatcherService service = getService();
			if (service != null) {
				service.refreshAll(chanName, true, true);
			}
		}

		@Override
		public boolean isWatcherSupported(Chan chan) {
			return WatcherService.isWatcherSupported(chan);
		}

		@Override
		public Counter getCounter(String chanName, String boardName, String threadNumber) {
			WatcherService service = getService();
			if (service != null) {
				ThreadKey threadKey = new ThreadKey(chanName, boardName, threadNumber);
				return service.getCounter(threadKey);
			} else {
				return Counter.INITIAL;
			}
		}

		@Override
		public Session newSession(String chanName, String boardName, String threadNumber, Session.Callback callback) {
			ThreadKey threadKey = new ThreadKey(chanName, boardName, threadNumber);
			ViewModelSession session = new ViewModelSession(this, threadKey, callback);
			sessions.add(session);
			WatcherService service = getService();
			if (service != null) {
				session.handleRegister(service, true);
			}
			return session;
		}

		private void destroySession(ViewModelSession session) {
			if (sessions.remove(session)) {
				WatcherService service = getService();
				if (service != null) {
					session.handleUnregister(service);
				}
			}
		}
	}

	private static class ViewModelSession implements Session, InternalSession {
		private enum Running {NONE, REFRESH, RELOAD}

		private final ViewModel viewModel;
		private final ThreadKey threadKey;
		private final Session.Callback callback;

		private Running running = Running.NONE;
		private boolean notifyExtracted;
		private boolean notifyEraseStarted;
		private boolean erasing;

		public ViewModelSession(ViewModel viewModel, ThreadKey threadKey, Session.Callback callback) {
			this.viewModel = viewModel;
			this.threadKey = threadKey;
			this.callback = callback;
		}

		@Override
		public boolean refresh(boolean reload, int checkInterval) {
			WatcherService service = viewModel.getService();
			if (service != null) {
				boolean shouldStart = checkInterval <= 0 || reload;
				boolean hasTask = service.hasTask(threadKey);
				if (!shouldStart && !hasTask) {
					WatcherItem watcherItem = service.watcherItems.get(threadKey);
					shouldStart = watcherItem != null && watcherItem
							.checkInterval(SystemClock.elapsedRealtime(), checkInterval);
				}
				if (shouldStart) {
					running = reload ? Running.RELOAD : Running.REFRESH;
					if (!hasTask || reload) {
						service.refreshForeground(threadKey, reload);
					}
					return true;
				} else {
					return false;
				}
			} else {
				running = reload ? Running.RELOAD : Running.REFRESH;
				return true;
			}
		}

		@Override
		public void notifyExtracted() {
			erasing = false;
			WatcherService service = viewModel.getService();
			if (service != null) {
				service.notifyExtracted(threadKey);
			} else {
				notifyExtracted = true;
			}
		}

		@Override
		public void notifyEraseStarted() {
			erasing = true;
			WatcherService service = viewModel.getService();
			if (service != null) {
				service.cancelBlockedUpdate(threadKey);
			} else {
				notifyEraseStarted = true;
			}
		}

		@Override
		public boolean hasTask() {
			WatcherService service = viewModel.getService();
			if (service != null) {
				return service.hasTask(threadKey);
			} else {
				return running != Running.NONE;
			}
		}

		@Override
		public void destroy() {
			viewModel.destroySession(this);
		}

		@Override
		public boolean isUpdateBlocked() {
			return erasing;
		}

		@Override
		public void notifyRefreshStarted() {
			if (running == Running.NONE) {
				running = Running.REFRESH;
			}
		}

		@Override
		public void onReadPostsSuccess(PagesDatabase.Cache.State cacheState, ConsumeReplies consumeReplies) {
			running = Running.NONE;
			callback.onReadPostsSuccess(cacheState, consumeReplies);
		}

		@Override
		public void onReadPostsRedirect(RedirectException.Target target) {
			running = Running.NONE;
			callback.onReadPostsRedirect(target);
		}

		@Override
		public void onReadPostsFail(ErrorItem errorItem) {
			running = Running.NONE;
			callback.onReadPostsFail(errorItem);
		}

		public void handleRegister(WatcherService service, boolean immediate) {
			service.registerSession(this, threadKey);
			if (immediate) {
				running = Running.NONE;
				notifyExtracted = false;
				notifyEraseStarted = false;
			} else {
				if (running != Running.NONE) {
					boolean reload = running == Running.RELOAD;
					if (!service.hasTask(threadKey) || reload) {
						service.refreshForeground(threadKey, reload);
					}
				}
				if (notifyExtracted) {
					notifyExtracted = false;
					service.notifyExtracted(threadKey);
				}
				if (notifyEraseStarted) {
					notifyEraseStarted = false;
					service.cancelBlockedUpdate(threadKey);
				}
			}
		}

		public void handleUnregister(WatcherService service) {
			if (running != Running.NONE) {
				running = Running.NONE;
				callback.onReadPostsFail(new ErrorItem(ErrorItem.Type.UNKNOWN));
			}
			service.unregisterSession(this, threadKey);
		}
	}

	private static boolean isWatcherSupported(Chan chan) {
		return chan.name != null && !chan.configuration.getOption(ChanConfiguration.OPTION_LOCAL_MODE);
	}

	private static class ConcurrentIterable<T> implements Iterable<T> {
		private static final Iterator<?> EMPTY = new Iterator<Object>() {
			@Override
			public boolean hasNext() {
				return false;
			}

			@Override
			public Object next() {
				throw new IndexOutOfBoundsException();
			}
		};

		public interface Provider<T> {
			Collection<T> getValues();
		}

		private final Provider<T> provider;
		private final ArrayList<T> workList = new ArrayList<>();
		private Collection<T> valuesOnce;

		public ConcurrentIterable(Provider<T> provider) {
			this.provider = provider;
		}

		@NonNull
		@Override
		public Iterator<T> iterator() {
			Collection<T> values;
			if (valuesOnce != null) {
				values = valuesOnce;
				valuesOnce = null;
			} else if (provider != null) {
				values = provider.getValues();
			} else {
				values = null;
			}
			if (values == null || values.isEmpty()) {
				@SuppressWarnings("unchecked")
				Iterator<T> result = (Iterator<T>) EMPTY;
				return result;
			} else {
				workList.clear();
				workList.addAll(values);
				Iterator<T> iterator = workList.iterator();
				return new Iterator<T>() {
					@Override
					public boolean hasNext() {
						boolean hasNext = iterator.hasNext();
						if (!hasNext) {
							workList.clear();
						}
						return hasNext;
					}

					@Override
					public T next() {
						return iterator.next();
					}
				};
			}
		}
	}

	private static class ResolveItemsTask extends ExecutorTask<Void, List<ResolveItemsTask.Item>> {
		public interface Callback {
			void onResolveItemsResult(List<Item> items);
		}

		public static class Item {
			public final ThreadKey threadKey;
			public final int newCount;
			public final boolean deleted;
			public final boolean error;
			public final long lastUpdate;

			public Item(ThreadKey threadKey, int newCount, boolean deleted, boolean error, long lastUpdate) {
				this.threadKey = threadKey;
				this.newCount = newCount;
				this.deleted = deleted;
				this.error = error;
				this.lastUpdate = lastUpdate;
			}
		}

		private final Callback callback;
		private final Set<ThreadKey> threads;

		public ResolveItemsTask(Callback callback, Set<ThreadKey> threads) {
			this.callback = callback;
			this.threads = threads;
		}

		@Override
		protected List<ResolveItemsTask.Item> run() {
			ArrayList<ResolveItemsTask.Item> items = new ArrayList<>(threads.size());
			for (ThreadKey threadKey : threads) {
				PagesDatabase.WatcherState watcherState = PagesDatabase.getInstance()
						.getWatcherState(new PagesDatabase.ThreadKey(threadKey.chanName,
								threadKey.boardName, threadKey.threadNumber));
				long now = SystemClock.elapsedRealtime();
				long lastUpdate = Math.min(now, watcherState.time - System.currentTimeMillis() + now);
				items.add(new Item(threadKey, watcherState.newCount,
						watcherState.deleted, watcherState.error, lastUpdate));
			}
			return items;
		}

		@Override
		protected void onComplete(List<Item> result) {
			callback.onResolveItemsResult(result);
		}
	}

	private static class WatcherTask {
		public final ReadPostsTask task;
		public final Worker worker;

		public WatcherTask(ReadPostsTask task, Worker worker) {
			this.task = task;
			this.worker = worker;
			worker.acquire();
		}

		public void cancel() {
			task.cancel();
			worker.release();
		}
	}

	public enum WatcherState {IDLE, ENQUEUED, UNAVAILABLE}

	private static final Session.Callback.ConsumeReplies CONSUME_REPLIES_EMPTY = () -> {};

	private class WatcherItem implements Comparable<WatcherItem>, ReadPostsTask.Callback {
		public final ThreadKey threadKey;

		public boolean resolved;
		public int newCount;
		public boolean deleted;
		public boolean error;
		public long lastUpdate;

		public WatcherTask task;
		public WatcherState state = WatcherState.IDLE;

		public WatcherItem(ThreadKey threadKey) {
			this.threadKey = threadKey;
		}

		public void cancel() {
			if (task != null) {
				task.cancel();
				task = null;
			}
		}

		public void createAndExecuteTask(Worker worker, boolean reload, boolean notifyBeforeStart) {
			cancel();
			Set<PendingUserPost> pendingUserPosts = PostingService.getPendingUserPosts(threadKey.chanName,
					threadKey.boardName, threadKey.threadNumber);
			ReadPostsTask task = new ReadPostsTask(this, Chan.get(threadKey.chanName),
					threadKey.boardName, threadKey.threadNumber, reload, pendingUserPosts);
			task.execute(worker.executor);
			if (notifyBeforeStart) {
				for (InternalSession session : getSessionConcurrentIterable(threadKey)) {
					session.notifyRefreshStarted();
				}
			}
			this.task = new WatcherTask(task, worker);
			notifyWatcherUpdate(this);
		}

		public boolean checkInterval(long now, int interval) {
			return lastUpdate + interval - 1000 <= now;
		}

		@Override
		public int compareTo(WatcherItem o) {
			return Long.compare(lastUpdate, o.lastUpdate);
		}

		@Override
		public void onPendingUserPostsConsumed(Set<PendingUserPost> pendingUserPosts) {
			if (pendingUserPosts != null && !pendingUserPosts.isEmpty()) {
				PostingService.consumePendingUserPosts(threadKey.chanName, threadKey.boardName,
						threadKey.threadNumber, pendingUserPosts);
			}
		}

		@Override
		public void onReadPostsSuccess(PagesDatabase.Cache.State cacheState,
				List<PagesDatabase.InsertResult.Reply> replies, Integer newCount) {
			if (newCount != null) {
				resolved = true;
				this.newCount = newCount;
			}
			deleted = false;
			error = false;
			onTaskFinished();
			boolean[] notify = replies.isEmpty() ? null : new boolean[] {true};
			Session.Callback.ConsumeReplies consumeReplies = null;
			if (notify == null) {
				consumeReplies = CONSUME_REPLIES_EMPTY;
			}
			for (InternalSession session : getSessionConcurrentIterable(threadKey)) {
				if (consumeReplies == null) {
					consumeReplies = () -> notify[0] = false;
				}
				session.onReadPostsSuccess(cacheState, consumeReplies);
			}
			if (notify != null && notify[0] && !replies.isEmpty()) {
				Set<Preferences.NotificationFeature> notificationFeatures = Preferences.getWatcherNotifications();
				if (notificationFeatures.contains(Preferences.NotificationFeature.ENABLED)) {
					FavoritesStorage.FavoriteItem favoriteItem = FavoritesStorage.getInstance()
							.getFavorite(threadKey.chanName, threadKey.boardName, threadKey.threadNumber);
					String title = favoriteItem != null ? StringUtils.emptyIfNull(favoriteItem.title) : "";
					if (title.trim().isEmpty()) {
						Chan chan = Chan.get(threadKey.chanName);
						title = chan.configuration.getTitle() + " / " +
								threadKey.boardName + " / " + threadKey.threadNumber;
					}
					boolean important = notificationFeatures.contains(Preferences.NotificationFeature.IMPORTANT);
					boolean sound = notificationFeatures.contains(Preferences.NotificationFeature.SOUND);
					boolean vibration = notificationFeatures.contains(Preferences.NotificationFeature.VIBRATION);
					WatcherNotifications.notifyReplies(WatcherService.this,
							notificationColor, important, sound, vibration,
							title, threadKey.chanName, threadKey.boardName, threadKey.threadNumber, replies);
				}
			}
		}

		@Override
		public void onReadPostsRedirect(RedirectException.Target target) {
			deleted = true;
			error = false;
			onTaskFinished();
			for (InternalSession session : getSessionConcurrentIterable(threadKey)) {
				session.onReadPostsRedirect(target);
			}
		}

		@Override
		public void onReadPostsFail(ErrorItem errorItem) {
			boolean notExists = errorItem.type == ErrorItem.Type.THREAD_NOT_EXISTS;
			deleted = notExists;
			error = !notExists;
			onTaskFinished();
			for (InternalSession session : getSessionConcurrentIterable(threadKey)) {
				session.onReadPostsFail(errorItem);
			}
		}

		private void onTaskFinished() {
			task.worker.release();
			task = null;
			lastUpdate = SystemClock.elapsedRealtime();
			state = WatcherState.IDLE;
			enqueuedWatcherItems.remove(this);
			if (deleted) {
				FavoritesStorage.getInstance().setWatcherEnabled(threadKey.chanName,
						threadKey.boardName, threadKey.threadNumber, false);
			}
			startNext();
			notifyWatcherUpdate(this);
		}
	}

	private final HashMap<Client, String> clients = new HashMap<>();
	private final HashMap<ThreadKey, HashSet<InternalSession>> sessionsMap = new HashMap<>();
	private final HashMap<ThreadKey, WatcherItem> watcherItems = new HashMap<>();
	private final ArrayList<WatcherItem> enqueuedWatcherItems = new ArrayList<>();

	private final Iterable<ThreadKey> workWatcherKeys = new ConcurrentIterable<>(watcherItems::keySet);
	private final Iterable<Client> workClients = new ConcurrentIterable<>(clients::keySet);
	private final ConcurrentIterable<InternalSession> workSessions = new ConcurrentIterable<>(null);
	private final HashSet<String> workPriorityChanNames = new HashSet<>();

	private int notificationColor;
	private ResolveItemsTask resolveItemsTask;
	private long lastRefreshAll;

	@Override
	public void onCreate() {
		super.onCreate();

		WatcherNotifications.configure(this);
		updateNotificationColor();
		addOnDestroyListener(ChanDatabase.getInstance().requireCookies());
		Preferences.PREFERENCES.registerOnSharedPreferenceChangeListener(preferencesListener);
		FavoritesStorage.getInstance().getObservable().register(favoritesObserver);
		for (FavoritesStorage.FavoriteItem favoriteItem : FavoritesStorage.getInstance().getThreads(null)) {
			ThreadKey threadKey = new ThreadKey(favoriteItem.chanName,
					favoriteItem.boardName, favoriteItem.threadNumber);
			addWatcherItem(threadKey, false);
		}
		resolveWatcherItems();
		refreshAll(null, false, true);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		for (WatcherItem watcherItem : watcherItems.values()) {
			if (watcherItem.task != null) {
				watcherItem.cancel();
				for (InternalSession session : getSessionConcurrentIterable(watcherItem.threadKey)) {
					session.onReadPostsFail(new ErrorItem(ErrorItem.Type.UNKNOWN));
				}
			}
		}
		if (resolveItemsTask != null) {
			resolveItemsTask.cancel();
			resolveItemsTask = null;
		}
		Preferences.PREFERENCES.unregisterOnSharedPreferenceChangeListener(preferencesListener);
		FavoritesStorage.getInstance().getObservable().unregister(favoritesObserver);
		ConcurrentUtils.HANDLER.removeCallbacks(refreshAllRunnable);
	}

	private final FavoritesStorage.Observer favoritesObserver = (favoriteItem, action) -> {
		if (favoriteItem.threadNumber == null) {
			return;
		}
		ThreadKey threadKey = new ThreadKey(favoriteItem.chanName, favoriteItem.boardName, favoriteItem.threadNumber);
		switch (action) {
			case ADD: {
				WatcherItem watcherItem = addWatcherItem(threadKey, true);
				notifyWatcherUpdate(watcherItem);
				startNext();
				break;
			}
			case REMOVE: {
				removeOrCancelWatcherItemIfNotNeeded(threadKey);
				break;
			}
			case WATCHER_ENABLE: {
				WatcherItem watcherItem = watcherItems.get(threadKey);
				if (watcherItem.state != WatcherState.ENQUEUED) {
					watcherItem.state = WatcherState.ENQUEUED;
					enqueuedWatcherItems.add(watcherItem);
					Collections.sort(enqueuedWatcherItems);
				}
				if (watcherItem != null) {
					notifyWatcherUpdate(watcherItem);
				}
				startNext();
				break;
			}
			case WATCHER_DISABLE: {
				WatcherItem watcherItem = removeOrCancelWatcherItemIfNotNeeded(threadKey);
				if (watcherItem != null) {
					notifyWatcherUpdate(watcherItem);
				}
				break;
			}
		}
	};

	private void resolveWatcherItems() {
		if (resolveItemsTask == null) {
			HashSet<ThreadKey> resolveThreads = null;
			for (WatcherItem watcherItem : watcherItems.values()) {
				if (!watcherItem.resolved) {
					if (resolveThreads == null) {
						resolveThreads = new HashSet<>();
					}
					resolveThreads.add(watcherItem.threadKey);
				}
			}
			if (resolveThreads != null && !resolveThreads.isEmpty()) {
				resolveItemsTask = new ResolveItemsTask(this::onResolveWatcherItemResult, resolveThreads);
				resolveItemsTask.execute(ConcurrentUtils.PARALLEL_EXECUTOR);
			}
		}
	}

	private void onResolveWatcherItemResult(List<ResolveItemsTask.Item> items) {
		resolveItemsTask = null;
		for (ResolveItemsTask.Item item : items) {
			WatcherItem watcherItem = watcherItems.get(item.threadKey);
			if (watcherItem != null && !watcherItem.resolved) {
				watcherItem.resolved = true;
				watcherItem.newCount = item.newCount;
				watcherItem.deleted = item.deleted;
				watcherItem.error = item.error;
				watcherItem.lastUpdate = item.lastUpdate;
				notifyWatcherUpdate(watcherItem);
			}
		}
		Collections.sort(enqueuedWatcherItems);
		resolveWatcherItems();
		startNext();
	}

	private void startNext() {
		HashSet<String> priorityChanNames = workPriorityChanNames;
		priorityChanNames.clear();
		priorityChanNames.addAll(clients.values());
		Iterator<WatcherItem> iterator = enqueuedWatcherItems.iterator();
		while (iterator.hasNext()) {
			WatcherItem watcherItem = iterator.next();
			if (watcherItem.resolved) {
				if (watcherItem.state != WatcherState.ENQUEUED) {
					iterator.remove();
				} else if (watcherItem.task == null) {
					Chan chan = Chan.get(watcherItem.threadKey.chanName);
					if (isWatcherSupported(chan) && !isBlocked(watcherItem.threadKey)) {
						HashSet<InternalSession> sessions = sessionsMap.get(watcherItem.threadKey);
						Worker worker;
						if (sessions != null && !sessions.isEmpty()) {
							worker = WORKER_FOREGROUND;
						} else if (isEnabled(watcherItem.threadKey)) {
							boolean priority = priorityChanNames.contains(watcherItem.threadKey.chanName);
							worker = priority ? WORKER_PRIORITY : WORKER_BACKGROUND;
						} else {
							watcherItem.state = WatcherState.IDLE;
							iterator.remove();
							worker = null;
						}
						if (worker != null && worker.isAvailable()) {
							watcherItem.createAndExecuteTask(worker, false, true);
						}
					} else {
						watcherItem.state = WatcherState.IDLE;
						iterator.remove();
					}
				}
			}
		}
		startNextFinished(false);
	}

	private void startNextFinished(boolean forceForeground) {
		ConcurrentUtils.HANDLER.removeCallbacks(refreshAllRunnable);
		if (enqueuedWatcherItems.isEmpty()) {
			boolean foreground = forceForeground;
			if (!foreground) {
				for (Client client : workClients) {
					Client.Callback callback = client.getCallback();
					if (callback != null && callback.isWatcherClientForeground()) {
						foreground = true;
						break;
					}
				}
			}
			int interval = getRefreshInterval(foreground);
			if (interval > 0) {
				if (lastRefreshAll == 0) {
					lastRefreshAll = SystemClock.elapsedRealtime();
				}
				long time = Math.max(0, lastRefreshAll + interval - SystemClock.elapsedRealtime());
				ConcurrentUtils.HANDLER.postDelayed(refreshAllRunnable, time);
			}
		}
	}

	private void notifyWatcherUpdate(WatcherItem watcherItem) {
		ThreadKey threadKey = watcherItem.threadKey;
		for (Client client : workClients) {
			Client.Callback callback = client.getCallback();
			if (callback != null) {
				callback.onWatcherUpdate(threadKey.chanName, threadKey.boardName,
						threadKey.threadNumber, getCounter(watcherItem));
			}
		}
	}

	private boolean isBlocked(ThreadKey threadKey) {
		for (InternalSession session : getSessionConcurrentIterable(threadKey)) {
			if (session.isUpdateBlocked()) {
				return true;
			}
		}
		return false;
	}

	private boolean isEnabled(ThreadKey threadKey) {
		FavoritesStorage.FavoriteItem favoriteItem = FavoritesStorage.getInstance()
				.getFavorite(threadKey.chanName, threadKey.boardName, threadKey.threadNumber);
		return favoriteItem != null && favoriteItem.watcherEnabled;
	}

	private boolean isNeeded(WatcherItem watcherItem, boolean enabledOnly) {
		ThreadKey threadKey = watcherItem.threadKey;
		HashSet<InternalSession> sessions = sessionsMap.get(threadKey);
		if (sessions != null && !sessions.isEmpty()) {
			return true;
		}
		FavoritesStorage.FavoriteItem favoriteItem = FavoritesStorage.getInstance().getFavorite(threadKey.chanName,
				threadKey.boardName, threadKey.threadNumber);
		return favoriteItem != null && (!enabledOnly || favoriteItem.watcherEnabled);
	}

	private WatcherItem removeOrCancelWatcherItemIfNotNeeded(ThreadKey threadKey) {
		WatcherItem watcherItem = watcherItems.get(threadKey);
		if (watcherItem != null && !isNeeded(watcherItem, true)) {
			watcherItem.cancel();
			if (isNeeded(watcherItem, false)) {
				notifyWatcherUpdate(watcherItem);
			} else {
				watcherItems.remove(threadKey);
				enqueuedWatcherItems.remove(watcherItem);
			}
			startNext();
			return null;
		} else {
			return watcherItem;
		}
	}

	private WatcherItem addWatcherItem(ThreadKey threadKey, boolean resolve) {
		WatcherItem watcherItem = watcherItems.get(threadKey);
		if (watcherItem == null) {
			watcherItem = new WatcherItem(threadKey);
			watcherItems.put(threadKey, watcherItem);
			if (resolve) {
				resolveWatcherItems();
			}
		}
		return watcherItem;
	}

	private void registerClient(Client client, String chanName) {
		boolean newClient = !clients.containsKey(client);
		String oldChanName = clients.put(client, chanName);
		if (newClient || !CommonUtils.equals(oldChanName, chanName)) {
			startNext();
		}
	}

	private void unregisterClient(Client client) {
		clients.remove(client);
	}

	private void registerSession(InternalSession session, ThreadKey threadKey) {
		HashSet<InternalSession> sessions = sessionsMap.get(threadKey);
		if (sessions == null) {
			sessions = new HashSet<>(1);
			sessionsMap.put(threadKey, sessions);
		}
		sessions.add(session);
		addWatcherItem(threadKey, true);
	}

	private void unregisterSession(InternalSession session, ThreadKey threadKey) {
		HashSet<InternalSession> sessions = sessionsMap.get(threadKey);
		if (sessions != null) {
			boolean removed = sessions.remove(session);
			if (sessions.isEmpty()) {
				sessionsMap.remove(threadKey);
			}
			if (removed) {
				removeOrCancelWatcherItemIfNotNeeded(threadKey);
			}
		}
	}

	private Iterable<InternalSession> getSessionConcurrentIterable(ThreadKey threadKey) {
		workSessions.valuesOnce = sessionsMap.get(threadKey);
		return workSessions;
	}

	private final Runnable refreshAllRunnable = () -> {
		lastRefreshAll = 0;
		refreshAll(null, false, false);
	};

	private void refreshAll(String chanName, boolean forceNetwork, boolean forceNow) {
		long now = SystemClock.elapsedRealtime();
		int interval = getRefreshInterval(true);
		boolean unavailable = !forceNetwork && Preferences.isWatcherWifiOnly() &&
				!NetworkObserver.getInstance().isWifiConnected();
		for (WatcherItem watcherItem : watcherItems.values()) {
			if (chanName == null || chanName.equals(watcherItem.threadKey.chanName)) {
				Chan chan = Chan.get(watcherItem.threadKey.chanName);
				if (isWatcherSupported(chan) && !isBlocked(watcherItem.threadKey) &&
						isEnabled(watcherItem.threadKey)) {
					if (unavailable) {
						if (watcherItem.state == WatcherState.IDLE) {
							watcherItem.state = WatcherState.UNAVAILABLE;
							notifyWatcherUpdate(watcherItem);
						}
					} else {
						if (watcherItem.state != WatcherState.ENQUEUED &&
								(forceNow || watcherItem.checkInterval(now, interval))) {
							watcherItem.state = WatcherState.ENQUEUED;
							notifyWatcherUpdate(watcherItem);
							enqueuedWatcherItems.add(watcherItem);
						}
					}
				}
			}
		}
		Collections.sort(enqueuedWatcherItems);
		startNext();
	}

	private void refreshForeground(ThreadKey threadKey, boolean reload) {
		WatcherItem watcherItem = watcherItems.get(threadKey);
		if (watcherItem != null) {
			watcherItem.createAndExecuteTask(WORKER_FOREGROUND, reload, false);
		}
	}

	private void notifyExtracted(ThreadKey threadKey) {
		WatcherItem watcherItem = watcherItems.get(threadKey);
		if (watcherItem != null && watcherItem.newCount > 0) {
			watcherItem.newCount = 0;
			notifyWatcherUpdate(watcherItem);
		}
	}

	private void cancelBlockedUpdate(ThreadKey threadKey) {
		WatcherItem watcherItem = watcherItems.get(threadKey);
		if (watcherItem != null && watcherItem.task != null) {
			watcherItem.cancel();
			notifyWatcherUpdate(watcherItem);
		}
	}

	private boolean hasTask(ThreadKey threadKey) {
		WatcherItem watcherItem = watcherItems.get(threadKey);
		return watcherItem != null && watcherItem.task != null;
	}

	private Counter getCounter(ThreadKey threadKey) {
		WatcherItem watcherItem = watcherItems.get(threadKey);
		return watcherItem != null ? getCounter(watcherItem) : Counter.INITIAL;
	}

	private Counter getCounter(WatcherItem watcherItem) {
		ThreadKey threadKey = watcherItem.threadKey;
		boolean enabled = isEnabled(threadKey);
		Counter.State state = enabled ? watcherItem.state == WatcherState.UNAVAILABLE
				? Counter.State.UNAVAILABLE : Counter.State.ENABLED : Counter.State.DISABLED;
		return new Counter(state, watcherItem.task != null,
				watcherItem.newCount, watcherItem.deleted, watcherItem.error);
	}

	private int getRefreshInterval(boolean foreground) {
		int interval = Preferences.getWatcherRefreshInterval() * 1000;
		if (!foreground) {
			int backgroundInterval = 10 * 60 * 1000;
			return Math.max(interval, backgroundInterval);
		} else {
			return interval;
		}
	}

	private final SharedPreferences.OnSharedPreferenceChangeListener preferencesListener = (p, key) -> {
		if (Preferences.KEY_WATCHER_REFRESH_INTERVAL.equals(key)) {
			ConcurrentUtils.HANDLER.removeCallbacks(refreshAllRunnable);
			startNext();
		} else if (Preferences.KEY_THEME.equals(key)) {
			updateNotificationColor();
		}
	};

	private void updateNotificationColor() {
		if (C.API_LOLLIPOP) {
			ThemeEngine.Theme theme = ThemeEngine.attachAndApply(this);
			notificationColor = theme.accent;
		}
	}

	private static final Worker WORKER_FOREGROUND = new Worker(ConcurrentUtils.PARALLEL_EXECUTOR);
	private static final Worker WORKER_PRIORITY = new Worker("WatcherPriority", 3);
	private static final Worker WORKER_BACKGROUND = new Worker("WatcherBackground", 3);

	private static class Worker {
		private final Executor executor;
		private final int limit;
		private int count;

		private Worker(Executor executor, int limit) {
			this.executor = executor;
			this.limit = limit;
		}

		public Worker(Executor executor) {
			this(executor, 0);
		}

		public Worker(String name, int limit) {
			this(ConcurrentUtils.newThreadPool(limit, limit, 0, name, null), limit);
		}

		public boolean isAvailable() {
			return limit <= 0 || count < limit;
		}

		public void acquire() {
			count++;
		}

		public void release() {
			count--;
		}
	}
}
