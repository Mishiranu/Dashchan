package com.mishiranu.dashchan.content.service;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Pair;
import android.view.ContextThemeWrapper;
import androidx.core.app.NotificationCompat;
import chan.content.ChanLocator;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.CacheManager;
import com.mishiranu.dashchan.content.FileProvider;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.async.ReadFileTask;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.FileHolder;
import com.mishiranu.dashchan.ui.MainActivity;
import com.mishiranu.dashchan.util.AndroidUtils;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.Log;
import com.mishiranu.dashchan.util.MimeTypes;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ToastUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.util.WeakObservable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

public class DownloadService extends Service implements ReadFileTask.Callback {
	private static final Executor SINGLE_THREAD_EXECUTOR = Executors.newSingleThreadExecutor();
	private static final Handler HANDLER = new Handler(Looper.getMainLooper());

	private static final String ACTION_CANCEL = "cancel";
	private static final String ACTION_RETRY = "retry";
	private static final String ACTION_OPEN = "open";

	private static final String EXTRA_FILE = "file";

	private NotificationManager notificationManager;
	private int notificationColor;
	private PowerManager.WakeLock wakeLock;

	private Thread notificationsWorker;
	private final LinkedBlockingQueue<NotificationData> notificationsQueue = new LinkedBlockingQueue<>();
	private boolean isForegroundWorker;

	private final WeakObservable<Callback> callbacks = new WeakObservable<>();

	private ChoiceRequest choiceRequest;
	private ReplaceRequest replaceRequest;
	private final ArrayList<DirectRequest> directRequests = new ArrayList<>();

	private final LinkedHashMap<String, TaskData> queuedTasks = new LinkedHashMap<>();
	private final LinkedHashMap<String, TaskData> successTasks = new LinkedHashMap<>();
	private final LinkedHashMap<String, TaskData> errorTasks = new LinkedHashMap<>();
	private Pair<TaskData, ReadFileTask> activeTask;

	private NotificationCompat.Builder builder;

	private int progress;
	private int progressMax;
	private long lastUpdate;

	private static File getSavedDownloadRetryFile() {
		return CacheManager.getInstance().getInternalCacheFile("saved-download-retry");
	}

	@Override
	protected void attachBaseContext(Context base) {
		super.attachBaseContext(new ContextThemeWrapper(base, R.style.Theme_Special_Notification));
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	@Override
	public void onCreate() {
		super.onCreate();

		notificationsWorker = new Thread(notificationsRunnable, "DownloadServiceNotificationThread");
		notificationsWorker.start();
		notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		int notificationColor = 0;
		if (C.API_LOLLIPOP) {
			Context themedContext = new ContextThemeWrapper(this, Preferences.getThemeResource());
			notificationColor = ResourceUtils.getColor(themedContext, android.R.attr.colorAccent);
		}
		this.notificationColor = notificationColor;
		if (C.API_OREO) {
			NotificationChannel channelDownloading =
					new NotificationChannel(C.NOTIFICATION_CHANNEL_DOWNLOADING,
							getString(R.string.text_downloads), NotificationManager.IMPORTANCE_LOW);
			NotificationChannel channelDownloadingComplete =
					new NotificationChannel(C.NOTIFICATION_CHANNEL_DOWNLOADING_COMPLETE,
							getString(R.string.text_completed_downloads), NotificationManager.IMPORTANCE_HIGH);
			channelDownloadingComplete.setSound(null, null);
			channelDownloadingComplete.setVibrationPattern(new long[0]);
			notificationManager.createNotificationChannel(channelDownloading);
			notificationManager.createNotificationChannel(channelDownloadingComplete);
		}
		PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
		wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
				getPackageName() + ":DownloadServiceWakeLock");
		wakeLock.setReferenceCounted(false);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		if (!errorTasks.isEmpty()) {
			// Preserve existing error tasks for "retry" notification button
			File file = getSavedDownloadRetryFile();
			file.delete();
			Parcel parcel = Parcel.obtain();
			FileOutputStream output = null;
			try {
				parcel.writeTypedList(new ArrayList<>(errorTasks.values()));
				byte[] data = parcel.marshall();
				output = new FileOutputStream(file);
				IOUtils.copyStream(new ByteArrayInputStream(data), output);
			} catch (Exception e) {
				file.delete();
			} finally {
				IOUtils.close(output);
				parcel.recycle();
			}
		}
		cleanup();
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

	private void cleanupRequests() {
		if (choiceRequest != null) {
			choiceRequest.cleanup();
			choiceRequest = null;
		}
		if (replaceRequest != null) {
			replaceRequest.directRequest.cleanup();
			replaceRequest = null;
		}
		for (DirectRequest directRequest : directRequests) {
			directRequest.cleanup();
		}
		directRequests.clear();
	}

	private void cleanup() {
		cleanupRequests();
		for (TaskData taskData : queuedTasks.values()) {
			if (taskData.input != null) {
				IOUtils.close(taskData.input);
			}
		}
		queuedTasks.clear();
		successTasks.clear();
		errorTasks.clear();
		if (activeTask != null) {
			activeTask.second.cancel();
			activeTask = null;
		}
		// Should also stop foreground and remove notification
		refreshNotification(NotificationUpdate.SYNC);
		wakeLock.release();
		for (Callback callback : callbacks) {
			callback.requestHandleRequest();
		}
	}

	private static String getTargetPathKey(Target target, String path) {
		return target + ":" + path.toLowerCase(Locale.getDefault());
	}

	private boolean hasStoragePermission() {
		return !C.API_MARSHMALLOW || checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
				== PackageManager.PERMISSION_GRANTED;
	}

	private void startNextTask() {
		if (activeTask == null && !queuedTasks.isEmpty()) {
			Iterator<TaskData> iterator = queuedTasks.values().iterator();
			TaskData taskData = iterator.next();
			iterator.remove();
			if (taskData.input != null) {
				OutputStream output = null;
				boolean success = false;
				try {
					output = IOUtils.openOutputStream(this, taskData.getFile(true));
					IOUtils.copyStream(taskData.input, output);
					success = true;
				} catch (IOException e) {
					Log.persistent().stack(e);
				} finally {
					IOUtils.close(taskData.input);
					IOUtils.close(output);
				}
				onFinishDownloadingInternal(success, new TaskData(taskData.chanName, taskData.overwrite,
						(InputStream) null, taskData.target, taskData.path));
			} else {
				ReadFileTask readFileTask = new ReadFileTask(this, taskData.chanName,
						taskData.uri, taskData.getFile(true), taskData.overwrite, this);
				activeTask = new Pair<>(taskData, readFileTask);
				readFileTask.executeOnExecutor(SINGLE_THREAD_EXECUTOR);
			}
		}
	}

	private void enqueue(TaskData taskData) {
		String key = taskData.getKey();
		if (activeTask != null && activeTask.first.getKey().equals(key)) {
			activeTask.second.cancel();
			activeTask = null;
		}
		TaskData oldTaskData = queuedTasks.remove(key);
		if (oldTaskData != null && oldTaskData.input != null) {
			IOUtils.close(oldTaskData.input);
		}
		successTasks.remove(key);
		errorTasks.remove(key);
		queuedTasks.put(key, taskData);
	}

	private void handleRequests() {
		boolean hasChoiceReplaceRequests = choiceRequest != null || replaceRequest != null;
		boolean hasDirectRequests = !directRequests.isEmpty();
		if (hasChoiceReplaceRequests || hasDirectRequests) {
			if (!hasStoragePermission()) {
				for (Callback callback : callbacks) {
					callback.requestPermission();
				}
			} else {
				if (choiceRequest != null && !choiceRequest.shouldHandle) {
					DirectRequest directRequest = choiceRequest.complete(null, Preferences.isDownloadDetailName(),
							Preferences.isDownloadOriginalName());
					choiceRequest = null;
					replaceRequest = createReplaceRequest(directRequest);
					if (replaceRequest == null) {
						directRequests.add(directRequest);
					}
				}
				for (DirectRequest directRequest : directRequests) {
					if (directRequest.input != null) {
						if (directRequest.downloadItems.size() != 1) {
							throw new IllegalStateException();
						}
						DownloadItem downloadItem = directRequest.downloadItems.get(0);
						enqueue(new TaskData(downloadItem.chanName, directRequest.overwrite, directRequest.input,
								directRequest.target, directRequest.getPath(downloadItem.name)));
					} else {
						for (DownloadItem downloadItem : directRequest.downloadItems) {
							enqueue(new TaskData(downloadItem.chanName, directRequest.overwrite, downloadItem.uri,
									directRequest.target, directRequest.getPath(downloadItem.name)));
						}
					}
				}
				directRequests.clear();
				if (choiceRequest != null || replaceRequest != null) {
					for (Callback callback : callbacks) {
						callback.requestHandleRequest();
					}
				}
				startNextTask();
			}
		}
		refreshNotification(NotificationUpdate.SYNC);
	}

	private ReplaceRequest createReplaceRequest(DirectRequest directRequest) {
		int queued = 0;
		int exists = 0;
		File lastExistingFile = null;
		HashSet<String> keys = new HashSet<>();
		ArrayList<DownloadItem> availableItems = new ArrayList<>();
		for (DownloadService.DownloadItem downloadItem : directRequest.downloadItems) {
			String path = directRequest.getPath(downloadItem.name);
			String key = getTargetPathKey(directRequest.target, path);
			File file = getTargetFile(directRequest.target, path, false);
			if (keys.contains(key) || queuedTasks.containsKey(key) ||
					activeTask != null && activeTask.first.getKey().equals(key)) {
				queued++;
			} else if (file.exists()) {
				exists++;
				lastExistingFile = file;
			} else {
				keys.add(key);
				availableItems.add(downloadItem);
			}
		}
		return availableItems.size() == directRequest.downloadItems.size() ? null
				: new ReplaceRequest(directRequest, availableItems, lastExistingFile, queued, exists);
	}

	public enum Target {DOWNLOADS}

	public interface Callback {
		void requestHandleRequest();
		void requestPermission();
	}

	public class Binder extends android.os.Binder {
		public void register(Callback callback) {
			callbacks.register(callback);
		}

		public void unregister(Callback callback) {
			callbacks.unregister(callback);
		}

		public void notifyReadyToHandleRequests() {
			handleRequests();
		}

		public void resolve(ChoiceRequest choiceRequest, DirectRequest directRequest) {
			if (DownloadService.this.choiceRequest == choiceRequest) {
				DownloadService.this.choiceRequest = null;
				if (directRequest != null) {
					replaceRequest = createReplaceRequest(directRequest);
					if (replaceRequest == null) {
						directRequests.add(directRequest);
					}
				} else {
					DownloadService.this.replaceRequest = null;
					choiceRequest.cleanup();
				}
				handleRequests();
			}
		}

		public void resolve(ReplaceRequest replaceRequest, ReplaceRequest.Action action) {
			if (DownloadService.this.replaceRequest == replaceRequest) {
				DownloadService.this.replaceRequest = null;
				if (action != null) {
					switch (action) {
						case REPLACE: {
							directRequests.add(replaceRequest.directRequest);
							break;
						}
						case KEEP_ALL: {
							HashSet<String> keys = new HashSet<>();
							Target target = replaceRequest.directRequest.target;
							String path = replaceRequest.directRequest.path;
							List<DownloadItem> downloadItems = replaceRequest.directRequest.downloadItems;
							ArrayList<DownloadItem> finalItems = new ArrayList<>(downloadItems.size());
							for (DownloadService.DownloadItem downloadItem : downloadItems) {
								if (replaceRequest.availableItems.contains(downloadItem)) {
									keys.add(getTargetPathKey(target,
											replaceRequest.directRequest.getPath(downloadItem.name)));
									finalItems.add(downloadItem);
								} else {
									String extension = StringUtils.getFileExtension(downloadItem.name);
									String dotExtension = StringUtils.isEmpty(extension) ? "" : "." + extension;
									String name = downloadItem.name.substring(0,
											downloadItem.name.length() - dotExtension.length());
									String startPath = StringUtils.isEmpty(path) ? name : path + "/" + name;
									File file;
									String key;
									int i = 0;
									do {
										String testPath = startPath + (i > 0 ? "-" + i : "") + dotExtension;
										file = getTargetFile(target, testPath, false);
										key = getTargetPathKey(target, testPath);
										i++;
									} while (file.exists() || keys.contains(key) || queuedTasks.containsKey(key) ||
											(activeTask != null && activeTask.first.getKey().equals(key)));
									keys.add(key);
									finalItems.add(new DownloadService.DownloadItem(downloadItem.chanName,
											downloadItem.uri, file.getName()));
								}
							}
							directRequests.add(new DirectRequest(target, path, replaceRequest.directRequest.overwrite,
									finalItems, replaceRequest.directRequest.input));
							break;
						}
						case SKIP: {
							if (!replaceRequest.availableItems.isEmpty()) {
								directRequests.add(new DirectRequest(replaceRequest.directRequest.target,
										replaceRequest.directRequest.path, replaceRequest.directRequest.overwrite,
										replaceRequest.availableItems, replaceRequest.directRequest.input));
							} else {
								// Request with input should contain only 1 download item.
								// Cleanup the request to ensure input stream is closed.
								replaceRequest.directRequest.cleanup();
							}
							break;
						}
					}
				} else {
					replaceRequest.directRequest.cleanup();
				}
				handleRequests();
			}
		}

		public ChoiceRequest getChoiceRequest() {
			return hasStoragePermission() ? choiceRequest : null;
		}

		public ReplaceRequest getReplaceRequest() {
			return hasStoragePermission() ? replaceRequest : null;
		}

		public void onPermissionResult(boolean granted) {
			if (!granted) {
				ToastUtils.show(DownloadService.this, R.string.message_no_access_to_memory);
				cleanupRequests();
			}
			handleRequests();
		}

		private void cancelAll() {
			cleanup();
		}

		private void retry() {
			if (activeTask == null && queuedTasks.isEmpty()) {
				successTasks.clear();
				ArrayList<TaskData> errorTasks = new ArrayList<>(DownloadService.this.errorTasks.values());
				DownloadService.this.errorTasks.clear();
				if (errorTasks.isEmpty()) {
					File file = getSavedDownloadRetryFile();
					if (file.exists()) {
						Parcel parcel = Parcel.obtain();
						FileInputStream input = null;
						ByteArrayOutputStream output = new ByteArrayOutputStream();
						try {
							input = new FileInputStream(file);
							IOUtils.copyStream(input, output);
							byte[] data = output.toByteArray();
							parcel.unmarshall(data, 0, data.length);
							parcel.setDataPosition(0);
							errorTasks.addAll(parcel.createTypedArrayList(TaskData.CREATOR));
						} catch (Exception e) {
							// Ignore exception
						} finally {
							IOUtils.close(input);
							parcel.recycle();
							file.delete();
						}
					}
				}
				for (TaskData taskData : errorTasks) {
					if (taskData.uri != null) {
						enqueue(taskData);
					}
				}
				startNextTask();
			}
		}

		private void open(File file) {
			refreshNotification(NotificationUpdate.SYNC);
			String extension = StringUtils.getFileExtension(file.getPath());
			String type = MimeTypes.forExtension(extension, "image/jpeg");
			if (file.exists()) {
				scanFile(file, new Pair<>(type, uri -> {
					try {
						startActivity(new Intent(Intent.ACTION_VIEW)
								.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION)
								.setDataAndType(uri, type));
					} catch (ActivityNotFoundException e) {
						ToastUtils.show(DownloadService.this, R.string.message_unknown_address);
					}
				}));
			}
		}

		public void downloadDirect(Target target, String path, boolean overwrite, List<DownloadItem> downloadItems) {
			directRequests.add(new DirectRequest(target, path, overwrite, downloadItems, null));
			handleRequests();
		}

		public void downloadDirect(Target target, String path, String name, InputStream input) {
			directRequests.add(new DirectRequest(target, path, true,
					Collections.singletonList(new DownloadItem(null, null, name)), input));
			handleRequests();
		}

		public void downloadStorage(Uri uri, String fileName, String originalName,
				String chanName, String boardName, String threadNumber, String threadTitle) {
			downloadStorage(new RequestItem(uri, fileName, originalName),
					chanName, boardName, threadNumber, threadTitle);
		}

		public void downloadStorage(RequestItem requestItem,
				String chanName, String boardName, String threadNumber, String threadTitle) {
			downloadStorage(Collections.singletonList(requestItem), false,
					chanName, boardName, threadNumber, threadTitle);
		}

		public void downloadStorage(List<RequestItem> requestItems, boolean multiple,
				String chanName, String boardName, String threadNumber, String threadTitle) {
			boolean modifyingAllowed = false;
			boolean hasOriginalNames = false;
			for (RequestItem requestItem : requestItems) {
				if (isFileNameModifyingAllowed(chanName, requestItem.uri)) {
					modifyingAllowed = true;
				}
				if (requestItem.originalName != null) {
					hasOriginalNames = true;
				}
			}
			boolean allowDetailName = modifyingAllowed;
			boolean allowOriginalName = modifyingAllowed && hasOriginalNames;
			choiceRequest = new UriRequest(Preferences.isDownloadSubdir(multiple), requestItems,
					allowDetailName, allowOriginalName, chanName, boardName, threadNumber, threadTitle);
			handleRequests();
		}

		public void saveStreamStorage(InputStream input, String chanName, String boardName, String threadNumber,
				String threadTitle, String fileName, boolean allowDialog) {
			choiceRequest = new StreamRequest(Preferences.isDownloadSubdir(false) && allowDialog,
					input, fileName, chanName, boardName, threadNumber, threadTitle);
			handleRequests();
		}
	}

	@Override
	public Binder onBind(Intent intent) {
		return new Binder();
	}

	private static class NotificationData {
		public enum Type {
			PROGRESS(android.R.drawable.stat_sys_download),
			RESULT(android.R.drawable.stat_sys_download_done),
			REQUEST(android.R.drawable.stat_sys_warning);

			public final int iconResId;

			Type(int iconResId) {
				this.iconResId = iconResId;
			}
		}

		public final Type type;
		public final boolean allowHeadsUp;
		public final int queuedTasks;
		public final int successTasks;
		public final int errorTasks;
		public final boolean allowRetry;
		public final boolean hasNotFromCache;
		public final File lastSuccessFile;
		public final String activeName;
		public final int progress;
		public final int progressMax;
		public final boolean updateImageOnly;
		public final CountDownLatch syncLatch;

		private NotificationData(Type type, boolean allowHeadsUp, int queuedTasks, int successTasks, int errorTasks,
				boolean allowRetry, boolean hasNotFromCache, File lastSuccessFile, String activeName,
				int progress, int progressMax, boolean updateImageOnly, CountDownLatch syncLatch) {
			this.type = type;
			this.allowHeadsUp = allowHeadsUp;
			this.queuedTasks = queuedTasks;
			this.successTasks = successTasks;
			this.errorTasks = errorTasks;
			this.allowRetry = allowRetry;
			this.hasNotFromCache = hasNotFromCache;
			this.lastSuccessFile = lastSuccessFile;
			this.activeName = activeName;
			this.progress = progress;
			this.progressMax = progressMax;
			this.updateImageOnly = updateImageOnly;
			this.syncLatch = syncLatch;
		}

		public static NotificationData updateData(Type type, boolean allowHeadsUp,
				int queuedTasks, int successTasks, int errorTasks,
				boolean allowRetry, boolean hasExternal, File lastSuccessFile,
				String activeName, int progress, int progressMax) {
			return new NotificationData(type, allowHeadsUp, queuedTasks, successTasks, errorTasks,
					allowRetry, hasExternal, lastSuccessFile, activeName, progress, progressMax, false, null);
		}

		public static NotificationData updateImageOnly(File lastSuccessFile) {
			return new NotificationData(null, false, 0, 0, 0, false, false, lastSuccessFile, null, 0, 0, true, null);
		}

		public static NotificationData sync(CountDownLatch syncLatch) {
			return new NotificationData(null, false, 0, 0, 0, false, false, null, null, 0, 0, false, syncLatch);
		}
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
			if (notificationData.syncLatch != null) {
				notificationData.syncLatch.countDown();
			} else if (notificationData.updateImageOnly) {
				if (builder != null) {
					setBuilderImage(notificationData.lastSuccessFile);
				}
			} else {
				refreshNotificationFromThread(notificationData);
			}
		}
	};

	private static class TaskData implements Parcelable {
		public final String chanName;
		public final boolean finishedFromCache;
		public final boolean overwrite;
		public final InputStream input;
		public final Uri uri;
		public final Target target;
		public final String path;

		private TaskData(String chanName, boolean finishedFromCache, boolean overwrite,
				InputStream input, Uri uri, Target target, String path) {
			this.chanName = chanName;
			this.finishedFromCache = finishedFromCache;
			this.overwrite = overwrite;
			this.input = input;
			this.uri = uri;
			this.target = target;
			this.path = path;
		}

		public TaskData(String chanName, boolean overwrite, InputStream input, Target target, String path) {
			this(chanName, true, overwrite, input, null, target, path);
		}

		public TaskData(String chanName, boolean overwrite, Uri from, Target target, String path) {
			this(chanName, false, overwrite, null, from, target, path);
		}

		public TaskData newFinishedFromCache(boolean finishedFromCache) {
			return this.finishedFromCache == finishedFromCache ? this
					: new TaskData(chanName, finishedFromCache, overwrite, input, uri, target, path);
		}

		public String getKey() {
			return getTargetPathKey(target, path);
		}

		public File getFile(boolean mkdirs) {
			return getTargetFile(target, path, mkdirs);
		}

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			dest.writeString(chanName);
			dest.writeByte((byte) (finishedFromCache ? 1 : 0));
			dest.writeByte((byte) (overwrite ? 1 : 0));
			dest.writeParcelable(uri, flags);
			dest.writeString(target.name());
			dest.writeString(path);
		}

		public static final Creator<TaskData> CREATOR = new Creator<TaskData>() {
			@Override
			public TaskData createFromParcel(Parcel in) {
				String chanName = in.readString();
				boolean finishedFromCache = in.readByte() != 0;
				boolean overwrite = in.readByte() != 0;
				Uri uri = in.readParcelable(getClass().getClassLoader());
				Target target = Target.valueOf(in.readString());
				String path = in.readString();
				return new TaskData(chanName, finishedFromCache, overwrite, null, uri, target, path);
			}

			@Override
			public TaskData[] newArray(int size) {
				return new TaskData[size];
			}
		};
	}

	private void startStopForeground(boolean foreground, Notification notification) {
		synchronized (this) {
			if (foreground) {
				if (!isForegroundWorker) {
					isForegroundWorker = true;
					AndroidUtils.startAnyService(this, new Intent(this, DownloadService.class));
				}
				startForeground(C.NOTIFICATION_ID_DOWNLOADING, notification);
			} else {
				if (isForegroundWorker) {
					isForegroundWorker = false;
					stopForeground(true);
					stopSelf();
				}
			}
		}
	}

	private NotificationData.Type oldNotificationDataType;

	private static final int[] ICON_ATTRS = {R.attr.notificationRefresh, R.attr.notificationCancel};

	private void setBuilderImage(File file) {
		if (file.exists()) {
			FileHolder fileHolder = FileHolder.obtain(file);
			DisplayMetrics metrics = getResources().getDisplayMetrics();
			int size = Math.max(metrics.widthPixels, metrics.heightPixels);
			builder.setLargeIcon(fileHolder.readImageBitmap(size / 4, false, false));
		}
	}

	private void refreshNotificationFromThread(NotificationData notificationData) {
		if (builder == null || notificationData.type != oldNotificationDataType) {
			oldNotificationDataType = notificationData.type;
			notificationManager.cancel(C.NOTIFICATION_ID_DOWNLOADING);
			builder = new NotificationCompat.Builder(this, C.NOTIFICATION_CHANNEL_DOWNLOADING);
			builder.setDeleteIntent(PendingIntent.getBroadcast(this, 0, new Intent(this, Receiver.class)
					.setAction(ACTION_CANCEL), PendingIntent.FLAG_UPDATE_CURRENT));
			builder.setSmallIcon(notificationData.type.iconResId);
			builder.setColor(notificationColor);
			if (notificationData.lastSuccessFile != null) {
				setBuilderImage(notificationData.lastSuccessFile);
			}
			TypedArray typedArray = obtainStyledAttributes(ICON_ATTRS);
			switch (notificationData.type) {
				case PROGRESS:
				case REQUEST: {
					ViewUtils.addNotificationAction(builder, this, typedArray, 1, android.R.string.cancel,
							PendingIntent.getBroadcast(this, 0, new Intent(this, Receiver.class)
									.setAction(ACTION_CANCEL), PendingIntent.FLAG_UPDATE_CURRENT));
					break;
				}
				case RESULT: {
					if (notificationData.allowRetry) {
						ViewUtils.addNotificationAction(builder, this, typedArray, 0, R.string.action_retry,
								PendingIntent.getBroadcast(this, 0, new Intent(this, Receiver.class)
										.setAction(ACTION_RETRY), PendingIntent.FLAG_UPDATE_CURRENT));
					}
					break;
				}
			}
			typedArray.recycle();
			if (notificationData.type == NotificationData.Type.REQUEST) {
				builder.setContentIntent(PendingIntent.getActivity(this, 0,
						new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT));
			} else if (notificationData.lastSuccessFile != null) {
				builder.setContentIntent(PendingIntent.getBroadcast(this, 0, new Intent(this, Receiver.class)
						.setAction(ACTION_OPEN).putExtra(EXTRA_FILE, notificationData.lastSuccessFile.getPath()),
						PendingIntent.FLAG_UPDATE_CURRENT));
			}
		}
		String contentTitle;
		String contentText;
		boolean headsUp;
		boolean foreground;
		switch (notificationData.type) {
			case PROGRESS: {
				int ready = notificationData.errorTasks + notificationData.successTasks;
				int total = ready + notificationData.queuedTasks + 1;
				ready++;
				contentTitle = getString(R.string.message_download_count_format, ready, total);
				contentText = getString(R.string.message_download_name_format, notificationData.activeName);
				headsUp = false;
				foreground = true;
				builder.setProgress(notificationData.progressMax, notificationData.progress,
						notificationData.progressMax == 0 || notificationData.progress > notificationData.progressMax
								|| notificationData.progress < 0);
				break;
			}
			case RESULT: {
				contentTitle = getString(notificationData.hasNotFromCache
						? R.string.message_download_completed : R.string.message_save_completed);
				contentText = getString(R.string.message_download_result_format,
						notificationData.successTasks, notificationData.errorTasks);
				headsUp = notificationData.allowHeadsUp;
				foreground = false;
				break;
			}
			case REQUEST: {
				contentTitle = getString(R.string.message_download_pending);
				contentText = getString(R.string.message_download_confirmation);
				headsUp = false;
				foreground = true;
				break;
			}
			default: {
				throw new IllegalStateException();
			}
		}
		builder.setContentTitle(contentTitle);
		builder.setContentText(contentText);
		if (C.API_LOLLIPOP) {
			if (headsUp && Preferences.isNotifyDownloadComplete()) {
				builder.setChannelId(C.NOTIFICATION_CHANNEL_DOWNLOADING_COMPLETE);
				builder.setPriority(NotificationCompat.PRIORITY_HIGH);
				builder.setVibrate(new long[0]);
			} else {
				builder.setChannelId(C.NOTIFICATION_CHANNEL_DOWNLOADING);
				builder.setPriority(NotificationCompat.PRIORITY_DEFAULT);
				builder.setVibrate(null);
			}
		} else {
			builder.setTicker(headsUp ? contentTitle : null);
		}
		builder.setOngoing(foreground);
		builder.setAutoCancel(false);
		if (foreground) {
			startStopForeground(true, builder.build());
		} else {
			startStopForeground(false, null);
			notificationManager.notify(C.NOTIFICATION_ID_DOWNLOADING, builder.build());
		}
	}

	private enum NotificationUpdate {NORMAL, HEADS_UP, SYNC}

	private void refreshNotification(NotificationUpdate notificationUpdate) {
		boolean hasTask = activeTask != null;
		boolean hasResults = !queuedTasks.isEmpty() || !successTasks.isEmpty() || !errorTasks.isEmpty();
		boolean hasRequests = choiceRequest != null || replaceRequest != null || !directRequests.isEmpty();
		boolean needForegroundOrNotification = hasTask || hasResults || hasRequests;
		if (needForegroundOrNotification) {
			boolean allowRetry = false;
			boolean hasNotFromCache = false;
			File lastSuccessFile = null;
			if (hasTask || !hasRequests) {
				for (TaskData taskData : successTasks.values()) {
					if (!taskData.finishedFromCache) {
						hasNotFromCache = true;
					}
					lastSuccessFile = taskData.getFile(false);
				}
				for (TaskData taskData : errorTasks.values()) {
					if (!taskData.finishedFromCache) {
						hasNotFromCache = true;
					}
					if (taskData.uri != null) {
						allowRetry = true;
					}
				}
			}
			NotificationData.Type type = hasTask ? NotificationData.Type.PROGRESS : hasRequests
					? NotificationData.Type.REQUEST : NotificationData.Type.RESULT;
			boolean allowHeadsUp = type == NotificationData.Type.RESULT &&
					notificationUpdate == NotificationUpdate.HEADS_UP;
			String activeName = hasTask ? activeTask.second.getFileName() : null;
			notificationsQueue.add(NotificationData.updateData(type, allowHeadsUp,
					queuedTasks.size(), successTasks.size(), errorTasks.size(), allowRetry, hasNotFromCache,
					lastSuccessFile, activeName, progress, progressMax));
		}
		if (hasTask) {
			wakeLock.acquire();
		} else {
			wakeLock.acquire(15000);
		}
		if (notificationUpdate == NotificationUpdate.SYNC || !needForegroundOrNotification) {
			CountDownLatch syncLatch = new CountDownLatch(1);
			notificationsQueue.add(NotificationData.sync(syncLatch));
			try {
				syncLatch.await();
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
		if (!needForegroundOrNotification) {
			startStopForeground(false, null);
		}
	}

	@Override
	public void onFileExists(Uri uri, File file) {
		Pair<TaskData, ReadFileTask> activeTask = this.activeTask;
		HANDLER.post(() -> {
			if (activeTask == this.activeTask) {
				progress = 0;
				progressMax = 0;
				refreshNotification(NotificationUpdate.NORMAL);
				onFinishDownloading(true, uri, file, null);
			}
		});
	}

	@Override
	public void onStartDownloading(Uri uri, File file) {
		progress = 0;
		progressMax = 0;
		refreshNotification(NotificationUpdate.NORMAL);
	}

	@Override
	public void onFinishDownloading(boolean success, Uri uri, File file, ErrorItem errorItem) {
		TaskData taskData = activeTask.first.newFinishedFromCache(activeTask.second.isDownloadingFromCache());
		activeTask = null;
		onFinishDownloadingInternal(success, taskData);
	}

	private void onFinishDownloadingInternal(boolean success, TaskData taskData) {
		if (success) {
			scanFile(taskData.getFile(false), null);
		}
		if (success) {
			successTasks.put(taskData.getKey(), taskData);
		} else {
			errorTasks.put(taskData.getKey(), taskData);
		}
		if (!queuedTasks.isEmpty()) {
			if (success) {
				// Update image explicitly, because task type won't be changed
				notificationsQueue.add(NotificationData.updateImageOnly(taskData.getFile(false)));
			}
			startNextTask();
		} else {
			refreshNotification(NotificationUpdate.HEADS_UP);
		}
	}

	@Override
	public void onUpdateProgress(long progress, long progressMax) {
		this.progress = (int) (progress / 1000);
		this.progressMax = (int) (progressMax / 1000);
		long t = SystemClock.elapsedRealtime();
		if (t - lastUpdate >= 500L) {
			lastUpdate = t;
			refreshNotification(NotificationUpdate.NORMAL);
		}
	}

	private interface ScanCallback {
		void onComplete(Uri uri);
	}

	private void scanFile(File file, Pair<String, ScanCallback> callback) {
		String[] fileArray = {file.getAbsolutePath()};
		MediaScannerConnection.OnScanCompletedListener listener;
		if (callback != null) {
			boolean[] handled = {false};
			listener = (f, uri) -> {
				synchronized (handled) {
					if (!handled[0]) {
						handled[0] = true;
						callback.second.onComplete(uri);
					}
				}
			};
			HANDLER.postDelayed(() -> {
				synchronized (handled) {
					if (!handled[0]) {
						handled[0] = true;
						callback.second.onComplete(FileProvider.convertDownloadsFile(file, callback.first));
					}
				}
			}, 1000);
		} else {
			listener = null;
		}
		MediaScannerConnection.scanFile(this, fileArray, null, listener);
	}

	private static File getTargetFile(Target target, String path, boolean mkdirs) {
		File directory;
		switch (target) {
			case DOWNLOADS: {
				directory = Preferences.getDownloadDirectory();
				break;
			}
			default: {
				throw new IllegalArgumentException();
			}
		}
		File file = new File(directory, path);
		if (mkdirs) {
			File parent = file.getParentFile();
			if (parent != null) {
				parent.mkdirs();
			}
		}
		return file;
	}

	private static String getFileNameWithChanBoardThreadData(String fileName,
			String chanName, String boardName, String threadNumber) {
		String extension = StringUtils.getFileExtension(fileName);
		fileName = fileName.substring(0, fileName.length() - extension.length() - 1);
		StringBuilder builder = new StringBuilder();
		builder.append(fileName);
		if (chanName != null) {
			builder.append('-').append(chanName);
		}
		if (boardName != null) {
			builder.append('-').append(boardName);
		}
		if (threadNumber != null) {
			builder.append('-').append(threadNumber);
		}
		return builder.append('.').append(extension).toString();
	}

	private static boolean isFileNameModifyingAllowed(String chanName, Uri uri) {
		return chanName != null && ChanLocator.get(chanName).safe(false).isAttachmentUri(uri);
	}

	private static String getDesiredFileName(Uri uri, String fileName, String originalName, boolean detailName,
			String chanName, String boardName, String threadNumber) {
		if (isFileNameModifyingAllowed(chanName, uri)) {
			if (originalName != null && Preferences.isDownloadOriginalName()) {
				fileName = originalName;
			}
			if (detailName) {
				fileName = getFileNameWithChanBoardThreadData(fileName, chanName, boardName, threadNumber);
			}
		}
		return fileName;
	}

	public static abstract class ChoiceRequest {
		private final boolean shouldHandle;
		public final String chanName;
		public final String boardName;
		public final String threadNumber;
		public final String threadTitle;

		protected ChoiceRequest(boolean shouldHandle,
				String chanName, String boardName, String threadNumber, String threadTitle) {
			this.shouldHandle = shouldHandle;
			this.chanName = chanName;
			this.boardName = boardName;
			this.threadNumber = threadNumber;
			this.threadTitle = threadTitle;
		}

		public abstract boolean allowDetailName();
		public abstract boolean allowOriginalName();
		public abstract DirectRequest complete(String path, boolean detailName, boolean originalName);

		protected void cleanup() {}
	}

	public static class DirectRequest {
		public final Target target;
		public final String path;
		public final boolean overwrite;
		public final InputStream input;
		public final List<DownloadItem> downloadItems;

		private DirectRequest(Target target, String path, boolean overwrite,
				List<DownloadItem> downloadItems, InputStream input) {
			this.target = target;
			this.path = path;
			this.overwrite = overwrite;
			this.downloadItems = downloadItems;
			this.input = input;
		}

		private String getPath(String name) {
			return StringUtils.isEmpty(path) ? name : path + "/" + name;
		}

		private void cleanup() {
			if (input != null) {
				IOUtils.close(input);
			}
		}
	}

	public static class ReplaceRequest {
		public enum Action {REPLACE, KEEP_ALL, SKIP}

		private final DirectRequest directRequest;
		private final List<DownloadItem> availableItems;

		public final File lastExistingFile;
		public final int queued;
		public final int exists;

		private ReplaceRequest(DirectRequest directRequest, List<DownloadItem> availableItems,
				File lastExistingFile, int queued, int exists) {
			this.directRequest = directRequest;
			this.availableItems = availableItems;
			this.lastExistingFile = lastExistingFile;
			this.queued = queued;
			this.exists = exists;
		}
	}

	private static class UriRequest extends ChoiceRequest {
		public final List<RequestItem> items;
		public final boolean allowDetailName;
		public final boolean allowOriginalName;

		private UriRequest(boolean shouldHandle, List<RequestItem> items,
				boolean allowDetailName, boolean allowOriginalName,
				String chanName, String boardName, String threadNumber, String threadTitle) {
			super(shouldHandle, chanName, boardName, threadNumber, threadTitle);
			this.items = items;
			this.allowDetailName = allowDetailName;
			this.allowOriginalName = allowOriginalName;
		}

		@Override
		public boolean allowDetailName() {
			return allowDetailName;
		}

		@Override
		public boolean allowOriginalName() {
			return allowOriginalName;
		}

		@Override
		public DirectRequest complete(String path, boolean detailName, boolean originalName) {
			List<DownloadItem> downloadItems = new ArrayList<>(items.size());
			for (RequestItem requestItem : items) {
				downloadItems.add(new DownloadItem(chanName, requestItem.uri, getDesiredFileName(requestItem.uri,
						requestItem.fileName, originalName ? requestItem.originalName : null, detailName,
						chanName, boardName, threadNumber)));
			}
			return new DirectRequest(Target.DOWNLOADS, path, true, downloadItems, null);
		}
	}

	private static class StreamRequest extends ChoiceRequest {
		public final InputStream input;
		public final String fileName;

		private StreamRequest(boolean shouldHandle, InputStream input, String fileName,
				String chanName, String boardName, String threadNumber, String threadTitle) {
			super(shouldHandle, chanName, boardName, threadNumber, threadTitle);
			this.input = input;
			this.fileName = fileName;
		}

		@Override
		public boolean allowDetailName() {
			return true;
		}

		@Override
		public boolean allowOriginalName() {
			return false;
		}

		@Override
		public DirectRequest complete(String path, boolean detailName, boolean originalName) {
			String fileName = detailName ? getFileNameWithChanBoardThreadData(this.fileName,
					chanName, boardName, threadNumber) : this.fileName;
			DownloadItem downloadItem = new DownloadService.DownloadItem(chanName, null, fileName);
			return new DirectRequest(Target.DOWNLOADS, path, true, Collections.singletonList(downloadItem), input);
		}

		@Override
		protected void cleanup() {
			if (input != null) {
				IOUtils.close(input);
			}
		}
	}

	public static class RequestItem {
		public final Uri uri;
		public final String fileName;
		public final String originalName;

		public RequestItem(Uri uri, String fileName, String originalName) {
			this.uri = uri;
			this.fileName = fileName;
			this.originalName = originalName;
		}
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

		@SuppressWarnings("EqualsReplaceableByObjectsCall")
		@Override
		public boolean equals(Object o) {
			if (o == this) {
				return true;
			}
			if (o instanceof DownloadItem) {
				DownloadItem co = (DownloadItem) o;
				return (uri == co.uri || uri != null && uri.equals(co.uri)) &&
						StringUtils.equals(name, co.name);
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

	public static class Receiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent != null ? intent.getAction() : null;
			boolean cancel = ACTION_CANCEL.equals(action);
			boolean retry = ACTION_RETRY.equals(action);
			boolean open = ACTION_OPEN.equals(action);
			Context bindContext = context.getApplicationContext();
			if (cancel || retry || open) {
				File file = open ? new File(intent.getStringExtra(EXTRA_FILE)) : null;
				// Broadcast receivers can't bind to services
				ServiceConnection[] connection = {null};
				connection[0] = new ServiceConnection() {
					@Override
					public void onServiceConnected(ComponentName componentName, IBinder binder) {
						Binder downloadBinder = (Binder) binder;
						if (cancel) {
							downloadBinder.cancelAll();
						} else if (retry) {
							downloadBinder.retry();
						} else if (open) {
							downloadBinder.open(file);
						}
						bindContext.unbindService(connection[0]);
					}

					@Override
					public void onServiceDisconnected(ComponentName componentName) {}
				};
				bindContext.bindService(new Intent(context, DownloadService.class), connection[0], BIND_AUTO_CREATE);
			}
		}
	}
}
