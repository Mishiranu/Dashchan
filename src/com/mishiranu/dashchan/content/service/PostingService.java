package com.mishiranu.dashchan.content.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.PowerManager;
import android.util.Pair;
import androidx.core.app.NotificationCompat;
import chan.content.ApiException;
import chan.content.ChanConfiguration;
import chan.content.ChanMarkup;
import chan.content.ChanPerformer;
import chan.text.CommentEditor;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.LocaleManager;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.async.SendPostTask;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.storage.DraftsStorage;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.content.storage.StatisticsStorage;
import com.mishiranu.dashchan.ui.MainActivity;
import com.mishiranu.dashchan.util.AndroidUtils;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.NavigationUtils;
import com.mishiranu.dashchan.util.WeakObservable;
import com.mishiranu.dashchan.widget.ThemeEngine;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

public class PostingService extends Service implements SendPostTask.Callback<PostingService.Key> {
	private static final String ACTION_CANCEL = "cancel";

	private final HashMap<Key, ArrayList<Callback>> callbacks = new HashMap<>();
	private final WeakObservable<GlobalCallback> globalCallbacks = new WeakObservable<>();
	private final HashMap<Callback, Key> callbackKeys = new HashMap<>();
	private TaskState taskState;

	private NotificationManager notificationManager;
	private int notificationColor;
	private PowerManager.WakeLock wakeLock;

	private Thread notificationsWorker;
	private final LinkedBlockingQueue<NotificationData> notificationsQueue = new LinkedBlockingQueue<>();

	public static final class Key {
		public final String chanName;
		public final String boardName;
		public final String threadNumber;

		private Key(String chanName, String boardName, String threadNumber) {
			this.chanName = chanName;
			this.boardName = boardName;
			this.threadNumber = threadNumber;
		}

		@Override
		public boolean equals(Object o) {
			if (o == this) {
				return true;
			}
			if (o instanceof Key) {
				Key key = (Key) o;
				return StringUtils.equals(key.chanName, chanName) &&
						StringUtils.equals(key.boardName, boardName) &&
						StringUtils.equals(key.threadNumber, threadNumber);
			}
			return false;
		}

		@Override
		public int hashCode() {
			int result = chanName != null ? chanName.hashCode() : 0;
			result = 31 * result + (boardName != null ? boardName.hashCode() : 0);
			result = 31 * result + (threadNumber != null ? threadNumber.hashCode() : 0);
			return result;
		}
	}

	private static class TaskState {
		public final Key key;
		public final SendPostTask<Key> task;
		public final NotificationCompat.Builder builder;
		public final String text;

		private SendPostTask.ProgressState progressState = SendPostTask.ProgressState.CONNECTING;
		private int attachmentIndex = 0;
		private int attachmentsCount = 0;

		private long progress = 0;
		private long progressMax = 0;

		public TaskState(Key key, SendPostTask<Key> task, Context context, String chanName,
				ChanPerformer.SendPostData data) {
			this.key = key;
			this.task = task;
			builder = new NotificationCompat.Builder(context, C.NOTIFICATION_CHANNEL_POSTING);
			text = buildNotificationText(chanName, data.boardName, data.threadNumber, null);
		}
	}

	private static class NotificationData {
		public enum Type {CREATE, UPDATE, CANCEL}

		public final Type type;
		public final TaskState taskState;
		public final CountDownLatch syncLatch;

		private NotificationData(Type type, TaskState taskState, CountDownLatch syncLatch) {
			this.type = type;
			this.taskState = taskState;
			this.syncLatch = syncLatch;
		}
	}

	public static String buildNotificationText(String chanName, String boardName, String threadNumber,
			String postNumber) {
		ChanConfiguration configuration = ChanConfiguration.get(chanName);
		StringBuilder builder = new StringBuilder(configuration.getTitle()).append(", ");
		builder.append(StringUtils.formatThreadTitle(chanName, boardName, threadNumber != null ? threadNumber : "?"));
		if (!StringUtils.isEmpty(postNumber)) {
			builder.append(", #").append(postNumber);
		}
		return builder.toString();
	}

	@Override
	protected void attachBaseContext(Context newBase) {
		super.attachBaseContext(LocaleManager.getInstance().apply(newBase));
	}

	@Override
	public void onCreate() {
		super.onCreate();
		notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		int notificationColor = 0;
		if (C.API_LOLLIPOP) {
			ThemeEngine.Theme theme = ThemeEngine.attachAndApply(this);
			notificationColor = theme.accent;
		}
		this.notificationColor = notificationColor;
		if (C.API_OREO) {
			NotificationChannel channelPosting =
					new NotificationChannel(C.NOTIFICATION_CHANNEL_POSTING,
							getString(R.string.posting), NotificationManager.IMPORTANCE_LOW);
			NotificationChannel channelPostingComplete =
					new NotificationChannel(C.NOTIFICATION_CHANNEL_POSTING_COMPLETE,
							getString(R.string.sent_posts), NotificationManager.IMPORTANCE_HIGH);
			channelPostingComplete.setSound(null, null);
			channelPostingComplete.setVibrationPattern(new long[0]);
			notificationManager.createNotificationChannel(channelPosting);
			notificationManager.createNotificationChannel(channelPostingComplete);
		}
		PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
		wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getPackageName() + ":PostingWakeLock");
		wakeLock.setReferenceCounted(false);
		notificationsWorker = new Thread(notificationsRunnable, "PostingServiceNotificationThread");
		notificationsWorker.start();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		performFinish(null, true);
		wakeLock.release();
		// Ensure queue is empty
		refreshNotification(NotificationData.Type.CANCEL, null);
		notificationsWorker.interrupt();
		try {
			notificationsWorker.join();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return START_NOT_STICKY;
	}

	private final Runnable notificationsRunnable = () -> {
		boolean interrupted = false;
		while (true) {
			NotificationData notificationData = null;
			if (!interrupted) {
				try {
					notificationData = notificationsQueue.take();
				} catch (InterruptedException e) {
					interrupted = true;
				}
			}
			if (interrupted) {
				notificationData = notificationsQueue.poll();
			}
			if (notificationData == null) {
				return;
			}
			if (notificationData.type == NotificationData.Type.CANCEL) {
				stopForeground(true);
				stopSelf();
			} else {
				TaskState taskState = notificationData.taskState;
				NotificationCompat.Builder builder = taskState.builder;
				if (notificationData.type == NotificationData.Type.CREATE) {
					builder.setOngoing(true);
					builder.setSmallIcon(android.R.drawable.stat_sys_upload);
					PendingIntent cancelIntent = PendingIntent.getBroadcast(this, 0, new Intent(this, Receiver.class)
							.setAction(ACTION_CANCEL), PendingIntent.FLAG_UPDATE_CURRENT);
					builder.addAction(C.API_LOLLIPOP ? 0 : R.drawable.ic_action_cancel_dark,
							getString(android.R.string.cancel), cancelIntent);
					builder.setColor(notificationColor);
					AndroidUtils.startAnyService(this, new Intent(this, PostingService.class));
				}
				boolean progressMode = taskState.task.isProgressMode();
				switch (taskState.progressState) {
					case CONNECTING: {
						if (progressMode) {
							builder.setProgress(1, 0, true);
						}
						builder.setContentTitle(getString(R.string.sending__ellipsis));
						break;
					}
					case SENDING: {
						if (progressMode) {
							int max = 1000;
							int progress = (int) (taskState.progress * max / taskState.progressMax);
							builder.setProgress(max, progress, taskState.progressMax <= 0);
							builder.setContentTitle(getString(R.string.sending_number_of_number__ellipsis_format,
									taskState.attachmentIndex + 1, taskState.attachmentsCount));
						} else {
							builder.setContentTitle(getString(R.string.sending__ellipsis));
						}
						break;
					}
					case PROCESSING: {
						if (progressMode) {
							builder.setProgress(1, 1, false);
						}
						builder.setContentTitle(getString(R.string.processing_data__ellipsis));
						break;
					}
				}
				builder.setContentText(taskState.text);
				startForeground(C.NOTIFICATION_ID_POSTING, builder.build());
			}
			if (notificationData.syncLatch != null) {
				notificationData.syncLatch.countDown();
			}
		}
	};

	@Override
	public Binder onBind(Intent intent) {
		return new Binder();
	}

	public interface Callback {
		void onState(boolean progressMode, SendPostTask.ProgressState progressState,
				int attachmentIndex, int attachmentsCount);
		void onProgress(long progress, long progressMax);
		void onStop(boolean success);
	}

	public interface GlobalCallback {
		void onPostSent();
	}

	public class Binder extends android.os.Binder {
		public boolean executeSendPost(String chanName, ChanPerformer.SendPostData data) {
			if (taskState == null) {
				Key key = new Key(chanName, data.boardName, data.threadNumber);
				AndroidUtils.startAnyService(PostingService.this, new Intent(PostingService.this, PostingService.class));
				wakeLock.acquire();
				SendPostTask<Key> task = new SendPostTask<>(key, chanName, PostingService.this, data);
				task.executeOnExecutor(SendPostTask.THREAD_POOL_EXECUTOR);
				TaskState taskState = new TaskState(key, task, PostingService.this, chanName, data);
				refreshNotification(NotificationData.Type.CREATE, taskState);
				PostingService.this.taskState = taskState;
				ArrayList<Callback> callbacks = PostingService.this.callbacks.get(key);
				if (callbacks != null) {
					for (Callback callback : callbacks) {
						notifyInit(callback, taskState);
					}
				}
				return true;
			}
			return false;
		}

		public void cancelSendPost(String chanName, String boardName, String threadNumber) {
			performFinish(new Key(chanName, boardName, threadNumber), true);
		}

		private void cancelCurrentSendPost() {
			performFinish(null, true);
		}

		public void register(Callback callback, String chanName, String boardName, String threadNumber) {
			Key key = new Key(chanName, boardName, threadNumber);
			callbackKeys.put(callback, key);
			ArrayList<Callback> callbacks = PostingService.this.callbacks.get(key);
			if (callbacks == null) {
				callbacks = new ArrayList<>(1);
				PostingService.this.callbacks.put(key, callbacks);
			}
			callbacks.add(callback);
			if (taskState != null && taskState.key.equals(key)) {
				notifyInit(callback, taskState);
			}
		}

		public void unregister(Callback callback) {
			Key key = callbackKeys.remove(callback);
			if (key != null) {
				ArrayList<Callback> callbacks = PostingService.this.callbacks.get(key);
				callbacks.remove(callback);
				if (callbacks.isEmpty()) {
					PostingService.this.callbacks.remove(key);
				}
			}
		}

		public void register(GlobalCallback globalCallback) {
			globalCallbacks.register(globalCallback);
		}

		public void unregister(GlobalCallback globalCallback) {
			globalCallbacks.unregister(globalCallback);
		}
	}

	private void refreshNotification(NotificationData.Type type, TaskState taskState) {
		CountDownLatch syncLatch = type == NotificationData.Type.CREATE || type == NotificationData.Type.CANCEL
				? new CountDownLatch(1) : null;
		notificationsQueue.add(new NotificationData(type, taskState, syncLatch));
		if (syncLatch != null) {
			try {
				syncLatch.await();
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
	}

	private void notifyInit(Callback callback, TaskState taskState) {
		boolean progressMode = taskState.task.isProgressMode();
		callback.onState(progressMode, taskState.progressState, taskState.attachmentIndex,
				taskState.attachmentsCount);
		callback.onProgress(taskState.progress, taskState.progressMax);
	}

	private boolean performFinish(Key key, boolean cancel) {
		TaskState taskState = this.taskState;
		if (taskState != null && (key == null || taskState.key.equals(key))) {
			this.taskState = null;
			if (cancel) {
				taskState.task.cancel();
			}
			refreshNotification(NotificationData.Type.CANCEL, taskState);
			wakeLock.release();
			if (cancel) {
				ArrayList<Callback> callbacks = this.callbacks.get(key);
				if (callbacks != null) {
					for (Callback callback : callbacks) {
						callback.onStop(false);
					}
				}
			}
			return true;
		}
		return false;
	}

	@Override
	public void onSendPostChangeProgressState(Key key, SendPostTask.ProgressState progressState,
			int attachmentIndex, int attachmentsCount) {
		TaskState taskState = this.taskState;
		if (taskState != null && taskState.key.equals(key)) {
			taskState.progressState = progressState;
			taskState.attachmentIndex = attachmentIndex;
			taskState.attachmentsCount = attachmentsCount;
			refreshNotification(NotificationData.Type.UPDATE, taskState);
			ArrayList<Callback> callbacks = this.callbacks.get(key);
			if (callbacks != null) {
				boolean progressMode = taskState.task.isProgressMode();
				for (Callback callback : callbacks) {
					callback.onState(progressMode, progressState, attachmentIndex, attachmentsCount);
				}
			}
		}
	}

	@Override
	public void onSendPostChangeProgressValue(Key key, long progress, long progressMax) {
		TaskState taskState = this.taskState;
		if (taskState != null && taskState.key.equals(key)) {
			taskState.progress = progress;
			taskState.progressMax = progressMax;
			refreshNotification(NotificationData.Type.UPDATE, taskState);
			ArrayList<Callback> callbacks = this.callbacks.get(key);
			if (callbacks != null) {
				for (Callback callback : callbacks) {
					callback.onProgress(progress, progressMax);
				}
			}
		}
	}

	@Override
	public void onSendPostSuccess(Key key, ChanPerformer.SendPostData data,
			String chanName, String threadNumber, String postNumber) {
		if (performFinish(key, false)) {
			String targetThreadNumber = data.threadNumber != null ? data.threadNumber
					: StringUtils.nullIfEmpty(threadNumber);
			if (targetThreadNumber != null && Preferences.isFavoriteOnReply(data.optionSage)) {
				FavoritesStorage.getInstance().add(chanName, data.boardName, targetThreadNumber, null, 0);
			}
			StatisticsStorage.getInstance().incrementPostsSent(chanName, data.threadNumber == null);
			DraftsStorage draftsStorage = DraftsStorage.getInstance();
			draftsStorage.removeCaptchaDraft();
			draftsStorage.removePostDraft(chanName, data.boardName, data.threadNumber);
			if (targetThreadNumber != null) {
				String password = Preferences.getPassword(chanName);
				if (StringUtils.equals(password, data.password)) {
					password = null;
				}
				draftsStorage.store(new DraftsStorage.PostDraft(chanName, data.boardName, targetThreadNumber,
						data.name, data.email, password, data.optionSage, data.optionOriginalPoster, data.userIcon));
			}
			if (targetThreadNumber != null) {
				postNumber = StringUtils.nullIfEmpty(postNumber);
				String comment = data.comment;
				if (comment != null) {
					CommentEditor commentEditor = ChanMarkup.get(chanName).safe().obtainCommentEditor(data.boardName);
					if (commentEditor != null) {
						comment = commentEditor.removeTags(comment);
					}
				}
				NewPostData newPostData = new NewPostData(chanName, data.boardName, targetThreadNumber, postNumber,
						comment, data.threadNumber == null);
				Key arrayKey = new Key(chanName, data.boardName, targetThreadNumber);
				ArrayList<NewPostData> newPostDataList = NEW_POST_DATA_LIST.get(arrayKey);
				if (newPostDataList == null) {
					newPostDataList = new ArrayList<>(1);
					NEW_POST_DATA_LIST.put(arrayKey, newPostDataList);
				}
				newPostDataList.add(newPostData);
				if (newPostData.newThread) {
					PostingService.newThreadData = new Pair<>(new Key(chanName, data.boardName, null), newPostData);
				}
				NotificationCompat.Builder builder = new NotificationCompat.Builder(this,
						C.NOTIFICATION_CHANNEL_POSTING_COMPLETE);
				builder.setSmallIcon(android.R.drawable.stat_sys_upload_done);
				builder.setColor(notificationColor);
				if (C.API_LOLLIPOP) {
					builder.setPriority(NotificationCompat.PRIORITY_HIGH);
					builder.setVibrate(new long[0]);
				} else {
					builder.setTicker(getString(R.string.post_sent));
				}
				builder.setContentTitle(getString(R.string.post_sent));
				builder.setContentText(buildNotificationText(chanName, data.boardName, targetThreadNumber, postNumber));
				String tag = newPostData.getNotificationTag();
				Intent intent = NavigationUtils.obtainPostsIntent(this, chanName, data.boardName, targetThreadNumber,
						postNumber, 0);
				builder.setContentIntent(PendingIntent.getActivity(this, tag.hashCode(), intent,
						PendingIntent.FLAG_UPDATE_CURRENT));
				notificationManager.notify(tag, 0, builder.build());
			}
			ArrayList<Callback> callbacks = this.callbacks.get(key);
			if (callbacks != null) {
				for (Callback callback : callbacks) {
					callback.onStop(true);
				}
			}
			for (GlobalCallback globalCallback : globalCallbacks) {
				globalCallback.onPostSent();
			}
		}
	}

	@Override
	public void onSendPostFail(Key key, ChanPerformer.SendPostData data, String chanName, ErrorItem errorItem,
			ApiException.Extra extra, boolean captchaError, boolean keepCaptcha) {
		if (performFinish(key, false)) {
			ArrayList<Callback> callbacks = this.callbacks.get(key);
			if (callbacks != null) {
				for (Callback callback : callbacks) {
					callback.onStop(false);
				}
			}
			startActivity(new Intent(this, MainActivity.class).setAction(C.ACTION_POSTING)
					.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(C.EXTRA_CHAN_NAME, chanName)
					.putExtra(C.EXTRA_BOARD_NAME, data.boardName).putExtra(C.EXTRA_THREAD_NUMBER, data.threadNumber)
					.putExtra(C.EXTRA_FAIL_RESULT, new FailResult(errorItem, extra, captchaError, keepCaptcha)));
		}
	}

	public static class Receiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent != null ? intent.getAction() : null;
			boolean cancel = ACTION_CANCEL.equals(action);
			Context bindContext = context.getApplicationContext();
			if (cancel) {
				// Broadcast receivers can't bind to services
				ServiceConnection[] connection = {null};
				connection[0] = new ServiceConnection() {
					@Override
					public void onServiceConnected(ComponentName componentName, IBinder binder) {
						Binder postingBinder = (Binder) binder;
						if (cancel) {
							postingBinder.cancelCurrentSendPost();
						}
						bindContext.unbindService(connection[0]);
					}

					@Override
					public void onServiceDisconnected(ComponentName componentName) {}
				};
				bindContext.bindService(new Intent(context, PostingService.class), connection[0], BIND_AUTO_CREATE);
			}
		}
	}

	public static class FailResult implements Parcelable {
		public final ErrorItem errorItem;
		public final ApiException.Extra extra;
		public final boolean captchaError;
		public final boolean keepCaptcha;

		public FailResult(ErrorItem errorItem, ApiException.Extra extra, boolean captchaError, boolean keepCaptcha) {
			this.errorItem = errorItem;
			this.extra = extra;
			this.captchaError = captchaError;
			this.keepCaptcha = keepCaptcha;
		}

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			errorItem.writeToParcel(dest, flags);
			dest.writeParcelable(extra, flags);
			dest.writeByte((byte) (captchaError ? 1 : 0));
			dest.writeByte((byte) (keepCaptcha ? 1 : 0));
		}

		public static final Creator<FailResult> CREATOR = new Creator<FailResult>() {
			@Override
			public FailResult createFromParcel(Parcel in) {
				ErrorItem errorItem = ErrorItem.CREATOR.createFromParcel(in);
				ApiException.Extra extra = in.readParcelable(FailResult.class.getClassLoader());
				boolean captchaError = in.readByte() != 0;
				boolean keepCaptcha = in.readByte() != 0;
				return new FailResult(errorItem, extra, captchaError, keepCaptcha);
			}

			@Override
			public FailResult[] newArray(int size) {
				return new FailResult[size];
			}
		};
	}

	public static class NewPostData {
		public final String chanName;
		public final String boardName;
		public final String threadNumber;
		public final String postNumber;
		public final String comment;
		public final boolean newThread;

		public NewPostData(String chanName, String boardName, String threadNumber, String postNumber,
				String comment, boolean newThread) {
			this.chanName = chanName;
			this.boardName = boardName;
			this.threadNumber = threadNumber;
			this.postNumber = postNumber;
			this.comment = comment;
			this.newThread = newThread;
		}

		private String notificationTag;

		private String getNotificationTag() {
			if (notificationTag == null) {
				notificationTag = "posting:" + IOUtils.calculateSha256(chanName + "/" + boardName + "/" +
						threadNumber + "/" + postNumber + "/" + comment + "/" + newThread);
			}
			return notificationTag;
		}
	}

	private static final HashMap<Key, ArrayList<NewPostData>> NEW_POST_DATA_LIST = new HashMap<>();

	public static List<NewPostData> getNewPostDataList(Context context, String chanName, String boardName,
			String threadNumber) {
		ArrayList<NewPostData> newPostDataList = NEW_POST_DATA_LIST
				.remove(new Key(chanName, boardName, threadNumber));
		if (newPostDataList != null) {
			NotificationManager notificationManager = (NotificationManager) context
					.getSystemService(NOTIFICATION_SERVICE);
			for (NewPostData newPostData : newPostDataList) {
				notificationManager.cancel(newPostData.getNotificationTag(), 0);
			}
		}
		return newPostDataList;
	}

	private static Pair<Key, NewPostData> newThreadData;

	public static NewPostData obtainNewThreadData(Context context, String chanName, String boardName) {
		Pair<Key, NewPostData> newThreadData = PostingService.newThreadData;
		if (newThreadData != null && newThreadData.first.equals(new Key(chanName, boardName, null))) {
			clearNewThreadData();
			NotificationManager notificationManager = (NotificationManager) context
					.getSystemService(NOTIFICATION_SERVICE);
			notificationManager.cancel(newThreadData.second.getNotificationTag(), 0);
			return newThreadData.second;
		}
		return null;
	}

	public static void clearNewThreadData() {
		newThreadData = null;
	}
}
