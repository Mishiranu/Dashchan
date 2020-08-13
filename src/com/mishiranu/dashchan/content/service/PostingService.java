package com.mishiranu.dashchan.content.service;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.PowerManager;
import android.view.ContextThemeWrapper;
import chan.content.ApiException;
import chan.content.ChanConfiguration;
import chan.content.ChanMarkup;
import chan.content.ChanPerformer;
import chan.text.CommentEditor;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.async.SendPostTask;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.storage.DraftsStorage;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.content.storage.StatisticsStorage;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.ui.navigator.NavigatorActivity;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.NavigationUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.util.WeakObservable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;

public class PostingService extends Service implements Runnable, SendPostTask.Callback {
	private static final String ACTION_CANCEL_PREFIX = "cancel";

	private final HashMap<String, ArrayList<Callback>> callbacks = new HashMap<>();
	private final WeakObservable<GlobalCallback> globalCallbacks = new WeakObservable<>();
	private final HashMap<Callback, String> callbackKeys = new HashMap<>();
	private final HashMap<String, TaskState> tasks = new HashMap<>();

	private NotificationManager notificationManager;
	private PowerManager.WakeLock wakeLock;

	private Thread notificationsWorker;
	private final LinkedBlockingQueue<TaskState> notificationsQueue = new LinkedBlockingQueue<>();

	private static class TaskState {
		public final String key;
		public final SendPostTask task;
		public final Notification.Builder builder;
		public final String notificationTag = UUID.randomUUID().toString();
		public final String text;

		private SendPostTask.ProgressState progressState = SendPostTask.ProgressState.CONNECTING;
		private int attachmentIndex = 0;
		private int attachmentsCount = 0;

		private int progress = 0;
		private int progressMax = 0;

		private boolean first;
		private boolean cancel;

		public TaskState(String key, SendPostTask task, Context context, String chanName,
				ChanPerformer.SendPostData data) {
			this.key = key;
			this.task = task;
			builder = new Notification.Builder(context);
			text = buildNotificationText(chanName, data.boardName, data.threadNumber, null);
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
	public void onCreate() {
		super.onCreate();
		notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
		wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getPackageName() + ":PostingWakeLock");
		wakeLock.setReferenceCounted(false);
		notificationsWorker = new Thread(this, "PostingServiceNotificationThread");
		notificationsWorker.start();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		wakeLock.release();
		notificationsWorker.interrupt();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		String action = intent != null ? intent.getAction() : null;
		if (action != null && action.startsWith(ACTION_CANCEL_PREFIX + ".")) {
			String key = action.substring(ACTION_CANCEL_PREFIX.length() + 1);
			performCancel(key);
		}
		return START_NOT_STICKY;
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private void setNotificationColor(Notification.Builder builder) {
		Context themedContext = new ContextThemeWrapper(this, Preferences.getThemeResource());
		builder.setColor(ResourceUtils.getColor(themedContext, android.R.attr.colorAccent));
	}

	@Override
	public void run() {
		boolean interrupted = false;
		while (true) {
			TaskState taskState = null;
			if (!interrupted) {
				try {
					taskState = notificationsQueue.take();
				} catch (InterruptedException e) {
					interrupted = true;
				}
			}
			if (interrupted) {
				taskState = notificationsQueue.poll();
			}
			if (taskState == null) {
				return;
			}
			if (taskState.cancel) {
				notificationManager.cancel(taskState.notificationTag, 0);
			} else {
				Notification.Builder builder = taskState.builder;
				if (taskState.first) {
					builder.setOngoing(true);
					builder.setSmallIcon(android.R.drawable.stat_sys_upload);
					String action = ACTION_CANCEL_PREFIX + "." + taskState.key;
					PendingIntent cancelIntent = PendingIntent.getService(this, 0,
							new Intent(this, PostingService.class).setAction(action),
							PendingIntent.FLAG_UPDATE_CURRENT);
					Context themedContext = new ContextThemeWrapper(this, R.style.Theme_Special_Notification);
					ViewUtils.addNotificationAction(builder, this, ResourceUtils.getResourceId(themedContext,
							R.attr.notificationCancel, 0), getString(android.R.string.cancel), cancelIntent);
					if (C.API_LOLLIPOP) {
						setNotificationColor(builder);
					}
				}
				boolean progressMode = taskState.task.isProgressMode();
				switch (taskState.progressState) {
					case CONNECTING: {
						if (progressMode) {
							builder.setProgress(1, 0, true);
						}
						builder.setContentTitle(getString(R.string.message_sending));
						break;
					}
					case SENDING: {
						if (progressMode) {
							builder.setProgress(taskState.progressMax, taskState.progress, taskState.progressMax <= 0);
							builder.setContentTitle(getString(R.string.message_sending_index_format,
									taskState.attachmentIndex + 1, taskState.attachmentsCount));
						} else {
							builder.setContentTitle(getString(R.string.message_sending));
						}
						break;
					}
					case PROCESSING: {
						if (progressMode) {
							builder.setProgress(1, 1, false);
						}
						builder.setContentTitle(getString(R.string.message_processing_data));
						break;
					}
				}
				builder.setContentText(taskState.text);
				notificationManager.notify(taskState.notificationTag, 0, builder.build());
			}
		}
	}

	@Override
	public Binder onBind(Intent intent) {
		return new Binder();
	}

	private void stopSelfAndReleaseWakeLock() {
		stopSelf();
		wakeLock.release();
	}

	private static String makeKey(String chanName, String boardName, String threadNumber) {
		return chanName + '/' + boardName + '/' + threadNumber;
	}

	public interface Callback {
		void onState(boolean progressMode, SendPostTask.ProgressState progressState,
				int attachmentIndex, int attachmentsCount);
		void onProgress(int progress, int progressMax);
		void onStop(boolean success);
	}

	public interface GlobalCallback {
		void onPostSent();
	}

	public class Binder extends android.os.Binder {
		public void executeSendPost(String chanName, ChanPerformer.SendPostData data) {
			String key = makeKey(chanName, data.boardName, data.threadNumber);
			startService(new Intent(PostingService.this, PostingService.class));
			wakeLock.acquire();
			SendPostTask task = new SendPostTask(key, chanName, PostingService.this, data);
			task.executeOnExecutor(SendPostTask.THREAD_POOL_EXECUTOR);
			TaskState taskState = new TaskState(key, task, PostingService.this, chanName, data);
			enqueueUpdateNotification(taskState, true, false);
			tasks.put(key, taskState);
			ArrayList<Callback> callbacks = PostingService.this.callbacks.get(key);
			if (callbacks != null) {
				for (Callback callback : callbacks) {
					notifyInitDownloading(callback, taskState);
				}
			}
		}

		public void cancelSendPost(String chanName, String boardName, String threadNumber) {
			performCancel(makeKey(chanName, boardName, threadNumber));
		}

		public void register(Callback callback, String chanName, String boardName, String threadNumber) {
			String key = makeKey(chanName, boardName, threadNumber);
			callbackKeys.put(callback, key);
			ArrayList<Callback> callbacks = PostingService.this.callbacks.get(key);
			if (callbacks == null) {
				callbacks = new ArrayList<>(1);
				PostingService.this.callbacks.put(key, callbacks);
			}
			callbacks.add(callback);
			TaskState taskState = tasks.get(key);
			if (taskState != null) {
				notifyInitDownloading(callback, taskState);
			}
		}

		public void unregister(Callback callback) {
			String key = callbackKeys.remove(callback);
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

	private void enqueueUpdateNotification(TaskState taskState, boolean first, boolean cancel) {
		taskState.first = first;
		taskState.cancel = cancel;
		notificationsQueue.add(taskState);
	}

	private void notifyInitDownloading(Callback callback, TaskState taskState) {
		boolean progressMode = taskState.task.isProgressMode();
		callback.onState(progressMode, taskState.progressState, taskState.attachmentIndex,
				taskState.attachmentsCount);
		callback.onProgress(taskState.progress, taskState.progressMax);
	}

	private void performCancel(String key) {
		TaskState taskState = tasks.remove(key);
		if (taskState != null) {
			taskState.task.cancel();
			enqueueUpdateNotification(taskState, false, true);
			if (tasks.isEmpty()) {
				stopSelfAndReleaseWakeLock();
			}
			ArrayList<Callback> callbacks = this.callbacks.get(key);
			if (callbacks != null) {
				for (Callback callback : callbacks) {
					callback.onStop(false);
				}
			}
		}
	}

	@Override
	public void onSendPostChangeProgressState(String key, SendPostTask.ProgressState progressState,
			int attachmentIndex, int attachmentsCount) {
		TaskState taskState = tasks.get(key);
		if (taskState != null) {
			taskState.progressState = progressState;
			taskState.attachmentIndex = attachmentIndex;
			taskState.attachmentsCount = attachmentsCount;
			enqueueUpdateNotification(taskState, false, false);
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
	public void onSendPostChangeProgressValue(String key, int progress, int progressMax) {
		TaskState taskState = tasks.get(key);
		if (taskState != null) {
			taskState.progress = progress;
			taskState.progressMax = progressMax;
			enqueueUpdateNotification(taskState, false, false);
			ArrayList<Callback> callbacks = this.callbacks.get(key);
			if (callbacks != null) {
				for (Callback callback : callbacks) {
					callback.onProgress(progress, progressMax);
				}
			}
		}
	}

	private boolean removeTask(String key) {
		TaskState taskState = tasks.remove(key);
		if (taskState != null) {
			enqueueUpdateNotification(taskState, false, true);
			if (tasks.isEmpty()) {
				stopSelfAndReleaseWakeLock();
			}
			return true;
		}
		return false;
	}

	@Override
	public void onSendPostSuccess(String key, ChanPerformer.SendPostData data,
			String chanName, String threadNumber, String postNumber) {
		if (removeTask(key)) {
			String targetThreadNumber = data.threadNumber != null ? data.threadNumber
					: StringUtils.nullIfEmpty(threadNumber);
			if (targetThreadNumber != null && Preferences.isFavoriteOnReply()) {
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
				String arrayKey = makeKey(chanName, data.boardName, targetThreadNumber);
				ArrayList<NewPostData> newPostDataList = NEW_POST_DATA_LIST.get(arrayKey);
				if (newPostDataList == null) {
					newPostDataList = new ArrayList<>(1);
					NEW_POST_DATA_LIST.put(arrayKey, newPostDataList);
				}
				newPostDataList.add(newPostData);
				if (newPostData.newThread) {
					PostingService.newThreadData = newPostData;
					PostingService.newThreadDataKey = makeKey(chanName, data.boardName, null);
				}
				Notification.Builder builder = new Notification.Builder(this);
				builder.setSmallIcon(android.R.drawable.stat_sys_upload_done);
				if (C.API_LOLLIPOP) {
					setNotificationColor(builder);
					builder.setPriority(Notification.PRIORITY_HIGH);
					builder.setVibrate(new long[0]);
				} else {
					builder.setTicker(getString(R.string.text_post_sent));
				}
				builder.setContentTitle(getString(R.string.text_post_sent));
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
	public void onSendPostFail(String key, ChanPerformer.SendPostData data, String chanName, ErrorItem errorItem,
			ApiException.Extra extra, boolean captchaError, boolean keepCaptcha) {
		if (removeTask(key)) {
			ArrayList<Callback> callbacks = this.callbacks.get(key);
			if (callbacks != null) {
				for (Callback callback : callbacks) {
					callback.onStop(false);
				}
			}
			startActivity(new Intent(this, NavigatorActivity.class).setAction(C.ACTION_POSTING)
					.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(C.EXTRA_CHAN_NAME, chanName)
					.putExtra(C.EXTRA_BOARD_NAME, data.boardName).putExtra(C.EXTRA_THREAD_NUMBER, data.threadNumber)
					.putExtra(C.EXTRA_FAIL_RESULT, new FailResult(errorItem, extra, captchaError, keepCaptcha)));
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
				notificationTag = C.NOTIFICATION_TAG_POSTING + "/" + IOUtils.calculateSha256(chanName +
						"/" + boardName + "/" + threadNumber + "/" + postNumber + "/" + comment + "/" + newThread);
			}
			return notificationTag;
		}
	}

	private static final HashMap<String, ArrayList<NewPostData>> NEW_POST_DATA_LIST = new HashMap<>();

	public static ArrayList<NewPostData> getNewPostDataList(Context context, String chanName, String boardName,
			String threadNumber) {
		ArrayList<NewPostData> newPostDataList = NEW_POST_DATA_LIST.remove(makeKey(chanName, boardName, threadNumber));
		if (newPostDataList != null) {
			NotificationManager notificationManager = (NotificationManager) context
					.getSystemService(NOTIFICATION_SERVICE);
			for (NewPostData newPostData : newPostDataList) {
				notificationManager.cancel(newPostData.getNotificationTag(), 0);
			}
		}
		return newPostDataList;
	}

	private static NewPostData newThreadData;
	private static String newThreadDataKey;

	public static NewPostData obtainNewThreadData(Context context, String chanName, String boardName) {
		if (makeKey(chanName, boardName, null).equals(newThreadDataKey)) {
			NewPostData newThreadData = PostingService.newThreadData;
			clearNewThreadData();
			NotificationManager notificationManager = (NotificationManager) context
					.getSystemService(NOTIFICATION_SERVICE);
			notificationManager.cancel(newThreadData.getNotificationTag(), 0 );
			return newThreadData;
		}
		return null;
	}

	public static void clearNewThreadData() {
		newThreadData = null;
		newThreadDataKey = null;
	}
}
