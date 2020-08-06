package com.mishiranu.dashchan.content.service;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.PowerManager;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.DisplayMetrics;
import android.view.ContextThemeWrapper;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.DownloadManager;
import com.mishiranu.dashchan.content.FileProvider;
import com.mishiranu.dashchan.content.async.ReadFileTask;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.FileHolder;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.util.MimeTypes;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ToastUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

public class DownloadService extends Service implements ReadFileTask.Callback, ReadFileTask.AsyncFinishCallback,
		Runnable, MediaScannerConnection.MediaScannerConnectionClient {
	private static final Executor SINGLE_THREAD_EXECUTOR = Executors.newSingleThreadExecutor();

	private static final String ACTION_START = "com.mishiranu.dashchan.action.START";
	private static final String ACTION_SHOW_FAKE = "com.mishiranu.dashchan.action.SHOW_FAKE";
	private static final String ACTION_OPEN_FILE = "com.mishiranu.dashchan.action.OPEN_FILE";
	private static final String ACTION_CANCEL_DOWNLOADING = "com.mishiranu.dashchan.action.CANCEL_DOWNLOADING";
	private static final String ACTION_RETRY_DOWNLOADING = "com.mishiranu.dashchan.action.RETRY_DOWNLOADING";

	private static final String EXTRA_DIRECTORY = "com.mishiranu.dashchan.extra.DIRECTORY";
	private static final String EXTRA_DOWNLOAD_ITEMS = "com.mishiranu.dashchan.extra.DOWNLOAD_ITEMS";

	private static final String EXTRA_FILE = "com.mishiranu.dashchan.extra.FILE";
	private static final String EXTRA_SUCCESS = "com.mishiranu.dashchan.extra.SUCCESS";

	private Context context;
	private NotificationManager notificationManager;
	private int notificationColor;
	private PowerManager.WakeLock wakeLock;
	private boolean firstStart;

	private Thread notificationsWorker;
	private final LinkedBlockingQueue<NotificationData> notificationsQueue = new LinkedBlockingQueue<>();
	private boolean isForegroundWorker;

	private static Intent obtainIntent(Context context, String action) {
		return new Intent(context, DownloadService.class).setAction(action);
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	@Override
	public void onCreate() {
		super.onCreate();
		notificationsWorker = new Thread(this, "DownloadServiceNotificationThread");
		notificationsWorker.start();
		firstStart = true;
		context = new ContextThemeWrapper(this, R.style.Theme_Special_Notification);
		notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		int notificationColor = 0;
		if (C.API_LOLLIPOP) {
			Context themedContext = new ContextThemeWrapper(this, Preferences.getThemeResource());
			notificationColor = ResourceUtils.getColor(themedContext, android.R.attr.colorAccent);
		}
		this.notificationColor = notificationColor;
		PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
		wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DownloadServiceWakeLock");
		wakeLock.setReferenceCounted(false);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		notificationsWorker.interrupt();
		wakeLock.release();
		DownloadManager.getInstance().notifyServiceDestroy();
		if (readFileTask != null) {
			readFileTask.cancel();
		}
		try {
			notificationsWorker.join();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		stopForeground(true);
		notificationManager.cancel(C.NOTIFICATION_ID_DOWNLOAD);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		boolean firstStart = this.firstStart;
		this.firstStart = false;
		if (intent != null) {
			String action = intent.getAction();
			if (ACTION_START.equals(action)) {
				File directory = new File(intent.getStringExtra(EXTRA_DIRECTORY));
				ArrayList<DownloadItem> downloadItems = intent.getParcelableArrayListExtra(EXTRA_DOWNLOAD_ITEMS);
				for (int i = 0; i < downloadItems.size(); i++) {
					DownloadItem downloadItem = downloadItems.get(i);
					enqueue(downloadItem.chanName, downloadItem.uri, new File(directory, downloadItem.name),
							i == downloadItems.size() - 1);
				}
			} else if (ACTION_SHOW_FAKE.equals(action)) {
				File file = new File(intent.getStringExtra(EXTRA_FILE));
				boolean success = intent.getBooleanExtra(EXTRA_SUCCESS, true);
				TaskData taskData = new TaskData(null, Uri.fromFile(file), file);
				taskData.retryable = false;
				taskData.local = true;
				errorTasks.remove(taskData);
				successTasks.remove(taskData);
				if (success) {
					scanFile(file);
					successTasks.add(taskData);
				} else {
					errorTasks.add(taskData);
				}
				notificationsQueue.add(new NotificationData(getLastSuccessTaskData()));
				refreshNotification(true);
			} else if (firstStart) {
				// Start caused by clicking on notification when application was closed
				stopSelf();
			} else if (ACTION_OPEN_FILE.equals(action)) {
				builder = null;
				refreshNotification(false);
				if (successTasks.size() > 0) {
					TaskData taskData = successTasks.get(successTasks.size() - 1);
					String extension = StringUtils.getFileExtension(taskData.to.getPath());
					String type = MimeTypes.forExtension(extension, "image/jpeg");

					Uri uri;
					int intentFlags;
					if (taskData.to.equals(scannedMediaFile)) {
						uri = scannedMediaUri;
						intentFlags = 0;
					} else {
						uri = FileProvider.convertDownloadsFile(taskData.to, type);
						intentFlags = FileProvider.getIntentFlags();
					}

					try {
						context.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, type)
								.setFlags(intentFlags | Intent.FLAG_ACTIVITY_NEW_TASK));
					} catch (ActivityNotFoundException e) {
						ToastUtils.show(this, R.string.message_unknown_address);
					}
				}
			} else if (ACTION_CANCEL_DOWNLOADING.equals(action)) {
				stopSelf();
			} else if (ACTION_RETRY_DOWNLOADING.equals(action)) {
				if (readFileTask != null) {
					readFileTask.cancel();
				}
				successTasks.clear();
				for (TaskData taskData : errorTasks) {
					if (taskData.retryable) {
						queuedTasks.add(taskData);
					}
				}
				errorTasks.clear();
				if (queuedTasks.size() > 0) {
					TaskData taskData = queuedTasks.get(0);
					start(taskData.chanName, taskData.from, taskData.to);
				}
			}
		}
		return START_NOT_STICKY;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	private static class NotificationData {
		public final boolean allowHeadsUp;
		public final boolean hasTask;
		public final int queudTasksSize;
		public final int successTasksSize;
		public final ArrayList<TaskData> errorTasks;
		public final boolean hasExternal;
		public final TaskData lastSuccessTaskData;
		public final String currentTaskFileName;
		public final int progress;
		public final int progressMax;
		public final boolean forceSetImage;

		private NotificationData(boolean allowHeadsUp, boolean hasTask, int queudTasksSize, int successTasksSize,
				ArrayList<TaskData> errorTasks, boolean hasExternal, TaskData lastSuccessTaskData,
				String currentTaskFileName, int progress, int progressMax, boolean forceSetImage) {
			this.allowHeadsUp = allowHeadsUp;
			this.hasTask = hasTask;
			this.queudTasksSize = queudTasksSize;
			this.successTasksSize = successTasksSize;
			this.errorTasks = errorTasks != null ? new ArrayList<>(errorTasks) : null;
			this.hasExternal = hasExternal;
			this.lastSuccessTaskData = lastSuccessTaskData;
			this.currentTaskFileName = currentTaskFileName;
			this.progress = progress;
			this.progressMax = progressMax;
			this.forceSetImage = forceSetImage;
		}

		public NotificationData(boolean allowHeadsUp, boolean hasTask, int queudTasksSize, int successTasksSize,
				ArrayList<TaskData> errorTasks, boolean hasExternal, TaskData lastSuccessTaskData,
				String currentTaskFileName, int progress, int progressMax) {
			this(allowHeadsUp, hasTask, queudTasksSize, successTasksSize, errorTasks, hasExternal, lastSuccessTaskData,
					currentTaskFileName, progress, progressMax, false);
		}

		public NotificationData(TaskData lastSuccessTaskData) {
			this(false, false, 0, 0, null, false, lastSuccessTaskData, null, 0, 0, true);
		}
	}

	@Override
	public void run() {
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
			if (notificationData.forceSetImage) {
				if (builder != null) {
					setBuilderImage(notificationData.lastSuccessTaskData);
				}
			} else {
				refreshNotificationFromThread(notificationData);
			}
		}
	}

	private final ArrayList<TaskData> queuedTasks = new ArrayList<>();
	private final ArrayList<TaskData> successTasks = new ArrayList<>();
	private final ArrayList<TaskData> errorTasks = new ArrayList<>();

	private Notification.Builder builder;
	private Notification.BigTextStyle notificationStyle;
	private CharSequence notificationBigText;

	private volatile int progress, progressMax;
	private volatile long lastUpdate;

	private ReadFileTask readFileTask;

	private File scannedMediaFile;
	private Uri scannedMediaUri;

	private static class TaskData {
		public final String chanName;
		public final Uri from;
		public final File to;
		public boolean retryable = true;
		public boolean local = false;
		public String errorInfo;

		public TaskData(String chanName, Uri from, File to) {
			this.chanName = chanName;
			this.from = from;
			this.to = to;
		}

		@Override
		public boolean equals(Object o) {
			if (o == this) {
				return true;
			}
			if (o instanceof TaskData) {
				return to.equals(((TaskData) o).to);
			}
			return false;
		}

		@Override
		public int hashCode() {
			return to.hashCode();
		}
	}

	private void enqueue(String chanName, Uri from, File to, boolean refreshNotification) {
		TaskData taskData = new TaskData(chanName, from, to);
		boolean success = successTasks.contains(taskData);
		if (!success && !queuedTasks.contains(taskData)) {
			if (errorTasks.contains(taskData)) {
				errorTasks.remove(taskData);
			}
			queuedTasks.add(taskData);
			DownloadManager.getInstance().notifyFileAddedToDownloadQueue(taskData.to);
			boolean started = start(chanName, from, to);
			if (!started && refreshNotification) {
				refreshNotification(false);
			}
		} else if (success && readFileTask == null) {
			refreshNotification(true);
		}
	}

	private boolean start(String chanName, Uri from, File to) {
		if (readFileTask == null) {
			progressMax = 0;
			progress = 0;
			lastUpdate = 0L;
			readFileTask = new ReadFileTask(this, chanName, from, to, true, this);
			readFileTask.executeOnExecutor(SINGLE_THREAD_EXECUTOR);
			return true;
		}
		return false;
	}

	private boolean oldStateWithTask = false;

	private static final int[] ICON_ATTRS = {R.attr.notificationRefresh, R.attr.notificationCancel};

	private void setBuilderImage(TaskData taskData) {
		if (taskData.to.exists()) {
			FileHolder fileHolder = FileHolder.obtain(taskData.to);
			DisplayMetrics metrics = context.getResources().getDisplayMetrics();
			int size = Math.max(metrics.widthPixels, metrics.heightPixels);
			builder.setLargeIcon(fileHolder.readImageBitmap(size / 4, false, false));
		}
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private void refreshNotificationFromThread(NotificationData notificationData) {
		if (builder == null || (notificationData.hasTask) != oldStateWithTask) {
			oldStateWithTask = notificationData.hasTask;
			notificationManager.cancel(C.NOTIFICATION_ID_DOWNLOAD);
			builder = new Notification.Builder(context);
			builder.setDeleteIntent(PendingIntent.getService(context, 0,
					obtainIntent(this, ACTION_CANCEL_DOWNLOADING), PendingIntent.FLAG_UPDATE_CURRENT));
			builder.setSmallIcon(notificationData.hasTask ? android.R.drawable.stat_sys_download
					: android.R.drawable.stat_sys_download_done);
			if (notificationData.lastSuccessTaskData != null) {
				setBuilderImage(notificationData.lastSuccessTaskData);
			}
			TypedArray typedArray = context.obtainStyledAttributes(ICON_ATTRS);
			if (notificationData.hasTask) {
				PendingIntent cancelIntent = PendingIntent.getService(context, 0,
						obtainIntent(this, ACTION_CANCEL_DOWNLOADING), PendingIntent.FLAG_UPDATE_CURRENT);
				ViewUtils.addNotificationAction(builder, context, typedArray, 1,
						android.R.string.cancel, cancelIntent);
			} else if (notificationData.errorTasks.size() > 0) {
				boolean hasRetryable = false;
				for (TaskData taskData : notificationData.errorTasks) {
					if (taskData.retryable) {
						hasRetryable = true;
						break;
					}
				}
				if (hasRetryable) {
					PendingIntent retryIntent = PendingIntent.getService(context, 0,
							obtainIntent(this, ACTION_RETRY_DOWNLOADING), PendingIntent.FLAG_UPDATE_CURRENT);
					ViewUtils.addNotificationAction(builder, context, typedArray, 0,
							R.string.action_retry, retryIntent);
				}
			}
			typedArray.recycle();
			notificationBigText = null;
			notificationStyle = new Notification.BigTextStyle();
			builder.setStyle(notificationStyle);
			if (notificationData.errorTasks.size() > 0) {
				SpannableStringBuilder spannable = new SpannableStringBuilder();
				spannable.append("\n\n");
				StringUtils.appendSpan(spannable, context.getString(R.string.message_download_not_loaded),
						C.API_LOLLIPOP ? new TypefaceSpan("sans-serif-medium") : new StyleSpan(Typeface.BOLD));
				for (int i = 0; i < notificationData.errorTasks.size(); i++) {
					spannable.append('\n');
					TaskData taskData = notificationData.errorTasks.get(i);
					spannable.append(taskData.to.getName());
					if (taskData.errorInfo != null) {
						spannable.append(" (").append(taskData.errorInfo).append(')');
					}
				}
				notificationBigText = spannable;
			}
			if (notificationData.successTasksSize > 0) {
				builder.setContentIntent(PendingIntent.getService(context, 0,
						obtainIntent(this, ACTION_OPEN_FILE), PendingIntent.FLAG_UPDATE_CURRENT));
			}
		}
		String contentTitle;
		String contentText;
		boolean headsUp;
		if (notificationData.hasTask) {
			int ready = notificationData.errorTasks.size() + notificationData.successTasksSize;
			int total = ready + notificationData.queudTasksSize;
			ready++;
			contentTitle = context.getString(R.string.message_download_count_format, ready, total);
			contentText = context.getString(R.string.message_download_name_format,
					notificationData.currentTaskFileName);
			headsUp = false;
			builder.setProgress(notificationData.progressMax, notificationData.progress,
					notificationData.progressMax == 0 || notificationData.progress > notificationData.progressMax
					|| notificationData.progress < 0);
		} else {
			contentTitle = context.getString(notificationData.hasExternal ? R.string.message_download_completed
					: R.string.message_save_completed);
			contentText = context.getString(R.string.message_download_result_format, notificationData.successTasksSize,
					notificationData.errorTasks.size());
			headsUp = notificationData.allowHeadsUp;
			builder.setOngoing(false);
		}
		builder.setContentTitle(contentTitle);
		builder.setContentText(contentText);
		if (notificationBigText != null) {
			SpannableStringBuilder spannable = new SpannableStringBuilder(contentText);
			spannable.append(notificationBigText);
			notificationStyle.bigText(spannable);
		} else {
			notificationStyle.bigText(contentText);
		}
		if (C.API_LOLLIPOP) {
			builder.setColor(notificationColor);
			if (headsUp && Preferences.isNotifyDownloadComplete()) {
				builder.setPriority(Notification.PRIORITY_HIGH);
				builder.setVibrate(new long[0]);
			} else {
				builder.setPriority(Notification.PRIORITY_DEFAULT);
				builder.setVibrate(null);
			}
		} else {
			builder.setTicker(headsUp ? contentTitle : null);
		}
		Notification notification = builder.build();
		if (notificationData.hasTask) {
			if (!isForegroundWorker) {
				isForegroundWorker = true;
				startForeground(C.NOTIFICATION_ID_DOWNLOAD, notification);
			} else {
				notificationManager.notify(C.NOTIFICATION_ID_DOWNLOAD, notification);
			}
		} else {
			if (isForegroundWorker) {
				isForegroundWorker = false;
				stopForeground(true);
			}
			notificationManager.notify(C.NOTIFICATION_ID_DOWNLOAD, notification);
		}
	}

	private void refreshNotification(boolean allowHeadsUp) {
		boolean hasTask = readFileTask != null;
		boolean hasExternal = false;
		for (TaskData taskData : successTasks) {
			if (!taskData.local) {
				hasExternal = true;
				break;
			}
		}
		for (TaskData taskData : errorTasks) {
			if (!taskData.local) {
				hasExternal = true;
				break;
			}
		}
		notificationsQueue.add(new NotificationData(allowHeadsUp, hasTask, queuedTasks.size(), successTasks.size(),
				errorTasks, hasExternal, getLastSuccessTaskData(), hasTask ? readFileTask.getFileName() : null,
				progress, progressMax));
		if (hasTask) {
			wakeLock.acquire();
		} else {
			wakeLock.acquire(15000);
		}
	}

	private TaskData getLastSuccessTaskData() {
		return successTasks.size() > 0 ? successTasks.get(successTasks.size() - 1) : null;
	}

	@Override
	public void onFileExists(Uri uri, File file) {
		onFinishDownloading(true, uri, file, null);
	}

	@Override
	public void onStartDownloading(Uri uri, File file) {
		refreshNotification(false);
	}

	@Override
	public void onFinishDownloading(boolean success, Uri uri, File file, ErrorItem errorItem) {
		if (success) {
			scanFile(file);
		}
		boolean local = readFileTask.isDownloadingFromCache();
		readFileTask = null;
		TaskData taskData = new TaskData(null, uri, file);
		taskData.local = local;
		queuedTasks.remove(taskData);
		if (success) {
			DownloadManager.getInstance().notifyFileRemovedFromDownloadQueue(file);
			successTasks.add(taskData);
		} else {
			if (errorItem != null) {
				int code = errorItem.httpResponseCode;
				if (code >= 400 && code < 600) {
					taskData.errorInfo = "HTTP " + code;
				}
			}
			errorTasks.add(taskData);
		}
		if (queuedTasks.size() > 0) {
			// Update image explicitly, because task state won't be changed
			if (success) {
				notificationsQueue.add(new NotificationData(taskData));
			}
			taskData = queuedTasks.get(0);
			start(taskData.chanName, taskData.from, taskData.to);
		} else {
			refreshNotification(true);
		}
	}

	@Override
	public void onUpdateProgress(long progress, long progressMax) {
		this.progress = (int) progress;
		this.progressMax = (int) progressMax;
		long t = System.currentTimeMillis();
		if (t - lastUpdate >= 1000L) {
			lastUpdate = t;
			refreshNotification(false);
		}
	}

	@Override
	public void onFinishDownloadingInThread() {
		DownloadManager.getInstance().notifyFinishDownloadingInThread();
	}

	private void scanFile(File file) {
		String[] fileArray = {file.getAbsolutePath()};
		MediaScannerConnection.scanFile(context, fileArray, null, this);
	}

	@Override
	public void onMediaScannerConnected() {}

	@Override
	public void onScanCompleted(String path, Uri uri) {
		scannedMediaFile = new File(path);
		scannedMediaUri = uri;
	}

	public static class DownloadItem implements Parcelable {
		public final String chanName;
		public final Uri uri;
		public final String name;

		public DownloadItem(String chanName, Uri uri, String name) {
			this.chanName = chanName;
			this.uri = uri;
			this.name = name;
		}

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			dest.writeString(chanName);
			dest.writeString(uri != null ? uri.toString() : null);
			dest.writeString(name);
		}

		public static final Creator<DownloadItem> CREATOR = new Creator<DownloadItem>() {
			@Override
			public DownloadItem[] newArray(int size) {
				return new DownloadItem[size];
			}

			@Override
			public DownloadItem createFromParcel(Parcel source) {
				String chanName = source.readString();
				String uriString = source.readString();
				String name = source.readString();
				return new DownloadItem(chanName, uriString != null ? Uri.parse(uriString) : null, name);
			}
		};

		@Override
		public boolean equals(Object o) {
			if (o == this) {
				return true;
			}
			if (o instanceof DownloadItem) {
				DownloadItem co = (DownloadItem) o;
				return (uri == co.uri || uri != null && uri.equals(co.uri)) && StringUtils.equals(name, co.name);
			}
			return false;
		}

		@Override
		public int hashCode() {
			int prime = 31;
			int result = 1;
			result = prime * result + (uri != null ? uri.hashCode() : 0);
			result = prime * result + (name != null ? name.hashCode() : 0);
			return result;
		}
	}

	public static void downloadDirect(Context context, File directory, ArrayList<DownloadItem> downloadItems) {
		int size = downloadItems.size();
		int trimCount = 1000;
		if (size > trimCount) {
			for (int i = 0; i < size; i += trimCount) {
				int end = Math.min(i + trimCount, size);
				ArrayList<DownloadService.DownloadItem> itDownloadItems = new ArrayList<>(end - i);
				for (int j = i; j < end; j++) {
					itDownloadItems.add(downloadItems.get(j));
				}
				downloadDirectInternal(context, directory, itDownloadItems);
			}
		} else {
			downloadDirectInternal(context, directory, downloadItems);
		}
	}

	private static void downloadDirectInternal(Context context, File directory, ArrayList<DownloadItem> downloadItems) {
		Intent intent = obtainIntent(context, ACTION_START);
		intent.putExtra(EXTRA_DIRECTORY, directory.getAbsolutePath());
		intent.putParcelableArrayListExtra(EXTRA_DOWNLOAD_ITEMS, downloadItems);
		context.startService(intent);
	}

	public static void showFake(Context context, File file, boolean success) {
		Intent intent = obtainIntent(context, ACTION_SHOW_FAKE);
		intent.putExtra(EXTRA_FILE, file.getAbsolutePath());
		intent.putExtra(EXTRA_SUCCESS, success);
		context.startService(intent);
	}
}
