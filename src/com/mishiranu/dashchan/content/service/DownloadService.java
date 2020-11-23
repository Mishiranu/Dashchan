package com.mishiranu.dashchan.content.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.PowerManager;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.util.DisplayMetrics;
import android.util.Pair;
import androidx.core.app.NotificationCompat;
import chan.content.Chan;
import chan.util.CommonUtils;
import chan.util.DataFile;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.CacheManager;
import com.mishiranu.dashchan.content.FileProvider;
import com.mishiranu.dashchan.content.LocaleManager;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.async.ExecutorTask;
import com.mishiranu.dashchan.content.async.ReadFileTask;
import com.mishiranu.dashchan.content.database.ChanDatabase;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.FileHolder;
import com.mishiranu.dashchan.ui.MainActivity;
import com.mishiranu.dashchan.util.AndroidUtils;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.Log;
import com.mishiranu.dashchan.util.MimeTypes;
import com.mishiranu.dashchan.util.WeakObservable;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.ThemeEngine;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

public class DownloadService extends BaseService implements ReadFileTask.Callback {
	private static final Executor SINGLE_THREAD_EXECUTOR = Executors.newSingleThreadExecutor();

	private static final String ACTION_CANCEL = "cancel";
	private static final String ACTION_RETRY = "retry";
	private static final String ACTION_OPEN = "open";

	private static final String EXTRA_FILE_TARGET = "fileTarget";
	private static final String EXTRA_FILE_PATH = "filePath";
	private static final String EXTRA_ALLOW_WRITE = "allowWrite";

	private NotificationManager notificationManager;
	private int notificationColor;
	private PowerManager.WakeLock wakeLock;

	private Thread notificationsWorker;
	private final LinkedBlockingQueue<NotificationData> notificationsQueue = new LinkedBlockingQueue<>();
	private boolean isForegroundWorker;

	private final WeakObservable<Callback> callbacks = new WeakObservable<>();
	private final HashMap<String, DataFile> cachedDirectories = new HashMap<>();

	private Request primaryRequest;
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
	protected void attachBaseContext(Context newBase) {
		super.attachBaseContext(LocaleManager.getInstance().apply(newBase));
	}

	@Override
	public void onCreate() {
		super.onCreate();

		notificationsWorker = new Thread(notificationsRunnable, "DownloadServiceNotificationThread");
		notificationsWorker.start();
		notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		int notificationColor = 0;
		if (C.API_LOLLIPOP) {
			ThemeEngine.Theme theme = ThemeEngine.attachAndApply(this);
			notificationColor = theme.accent;
		}
		this.notificationColor = notificationColor;
		if (C.API_OREO) {
			notificationManager.createNotificationChannel
					(new NotificationChannel(C.NOTIFICATION_CHANNEL_DOWNLOADING,
							getString(R.string.downloads), NotificationManager.IMPORTANCE_LOW));
			notificationManager.createNotificationChannel(AndroidUtils
					.createHeadsUpNotificationChannel(C.NOTIFICATION_CHANNEL_DOWNLOADING_COMPLETE,
							getString(R.string.completed_downloads)));
		}
		PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
		wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
				getPackageName() + ":DownloadServiceWakeLock");
		wakeLock.setReferenceCounted(false);
		addOnDestroyListener(ChanDatabase.getInstance().requireCookies());
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
		if (primaryRequest != null) {
			primaryRequest.cleanup();
			primaryRequest = null;
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
			callback.onCleanup();
		}
	}

	private static String getTargetPathKey(DataFile.Target target, String path) {
		return target + ":" + path.toLowerCase(Locale.getDefault());
	}

	private static String getTargetPathKey(DataFile.Target target, String path, String name) {
		String fullPath = !StringUtils.isEmpty(path) ? path + "/" + name : name;
		return getTargetPathKey(target, fullPath);
	}

	private DataFile getDataFile(TaskData taskData) {
		String key = getTargetPathKey(taskData.target, StringUtils.emptyIfNull(taskData.path));
		DataFile file = cachedDirectories.get(key);
		if (file == null) {
			file = DataFile.obtain(this, taskData.target, taskData.path);
			if (file.exists()) {
				cachedDirectories.put(key, file);
			}
		}
		return file.getChild(taskData.name);
	}

	private boolean hasStoragePermission() {
		boolean external = primaryRequest != null;
		if (!external) {
			for (DirectRequest directRequest : directRequests) {
				if (directRequest.target.isExternal()) {
					external = true;
					break;
				}
			}
		}
		if (external && C.USE_SAF) {
			return Preferences.getDownloadUriTree(this) != null;
		} else {
			return true;
		}
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
					output = getDataFile(taskData).openOutputStream();
					IOUtils.copyStream(taskData.input, output);
					success = true;
				} catch (IOException e) {
					Log.persistent().stack(e);
				} finally {
					IOUtils.close(taskData.input);
					IOUtils.close(output);
				}
				onFinishDownloadingInternal(success, new TaskData(taskData.chanName, taskData.overwrite,
						null, taskData.target, taskData.path, taskData.name, taskData.allowWrite));
			} else {
				Chan chan = Chan.getPreferred(taskData.chanName, taskData.uri);
				ReadFileTask readFileTask = ReadFileTask.createShared(this, chan,
						taskData.uri, getDataFile(taskData), taskData.overwrite, taskData.checkSha256);
				activeTask = new Pair<>(taskData, readFileTask);
				readFileTask.execute(SINGLE_THREAD_EXECUTOR);
			}
		} else {
			cachedDirectories.clear();
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
		if (primaryRequest != null || !directRequests.isEmpty()) {
			if (!hasStoragePermission()) {
				for (Callback callback : callbacks) {
					callback.requestPermission();
				}
			} else {
				if (primaryRequest instanceof ChoiceRequest) {
					ChoiceRequest choiceRequest = (ChoiceRequest) primaryRequest;
					if (!choiceRequest.shouldHandle) {
						DirectRequest directRequest = choiceRequest.complete(null, Preferences.isDownloadDetailName(),
								Preferences.isDownloadOriginalName());
						primaryRequest = null;
						handlePrimaryDirectRequest(directRequest);
					}
				}
				for (DirectRequest directRequest : directRequests) {
					if (directRequest.input != null) {
						if (directRequest.downloadItems.size() != 1) {
							throw new IllegalStateException();
						}
						DownloadItem downloadItem = directRequest.downloadItems.get(0);
						enqueue(new TaskData(downloadItem.chanName, directRequest.overwrite, directRequest.input,
								directRequest.target, directRequest.path, downloadItem.name, directRequest.allowWrite));
					} else {
						for (DownloadItem downloadItem : directRequest.downloadItems) {
							enqueue(new TaskData(downloadItem.chanName, directRequest.overwrite,
									downloadItem.uri, downloadItem.checkSha256, directRequest.target,
									directRequest.path, downloadItem.name, directRequest.allowWrite));
						}
					}
				}
				directRequests.clear();
				startNextTask();
			}
		}
		for (Callback callback : callbacks) {
			callback.requestHandleRequest();
		}
		refreshNotification(NotificationUpdate.SYNC);
	}

	private static class PrepareTask<T> extends ExecutorTask<Void, T> {
		public interface Task<T> {
			void cleanup();
			T run() throws InterruptedException;
			void onResult(T result);
		}

		private final Task<T> task;

		public PrepareTask(Task<T> task) {
			this.task = task;
		}

		@Override
		protected T run() {
			try {
				return task.run();
			} catch (InterruptedException e) {
				return null;
			}
		}

		@Override
		protected void onComplete(T result) {
			task.onResult(result);
		}
	}

	private HashSet<String> collectActiveKeys() {
		HashSet<String> activeKeys = new HashSet<>(queuedTasks.keySet());
		if (activeTask != null) {
			activeKeys.add(activeTask.first.getKey());
		}
		return activeKeys;
	}

	private ReplaceRequest createReplaceRequest(DirectRequest directRequest,
			HashSet<String> activeKeys) throws InterruptedException {
		int queued = 0;
		int exists = 0;
		DataFile lastExistingFile = null;
		HashSet<String> keys = new HashSet<>();
		ArrayList<DownloadItem> availableItems = new ArrayList<>();
		DataFile parent = DataFile.obtain(this, directRequest.target, directRequest.path);
		HashMap<String, DataFile> children = new HashMap<>();
		List<DataFile> childrenList = parent.getChildren();
		if (childrenList != null) {
			for (DataFile file : childrenList) {
				children.put(file.getName().toLowerCase(Locale.getDefault()), file);
			}
		}
		for (DownloadService.DownloadItem downloadItem : directRequest.downloadItems) {
			String key = getTargetPathKey(directRequest.target, directRequest.path, downloadItem.name);
			if (keys.contains(key) || activeKeys.contains(key)) {
				queued++;
			} else {
				DataFile file = children.get(downloadItem.name.toLowerCase(Locale.getDefault()));
				if (file != null) {
					exists++;
					lastExistingFile = file;
				} else {
					keys.add(key);
					availableItems.add(downloadItem);
				}
			}
			if (Thread.interrupted()) {
				throw new InterruptedException();
			}
		}
		return availableItems.size() == directRequest.downloadItems.size() ? null
				: new ReplaceRequest(directRequest, availableItems, lastExistingFile, queued, exists);
	}

	private void handlePrimaryDirectRequest(DirectRequest directRequest) {
		HashSet<String> activeKeys = collectActiveKeys();
		PrepareTask<ReplaceRequest> task = new PrepareTask<>(new PrepareTask.Task<ReplaceRequest>() {
			@Override
			public void cleanup() {
				directRequest.cleanup();
			}

			@Override
			public ReplaceRequest run() throws InterruptedException {
				return createReplaceRequest(directRequest, activeKeys);
			}

			@Override
			public void onResult(ReplaceRequest result) {
				primaryRequest = result;
				if (result == null) {
					directRequests.add(directRequest);
				}
				handleRequests();
			}
		});
		task.execute(ConcurrentUtils.SEPARATE_EXECUTOR);
		primaryRequest = new PrepareRequest(task);
	}

	private DirectRequest createDirectRequestKeepAll(ReplaceRequest replaceRequest,
			HashSet<String> activeKeys) throws InterruptedException {
		HashSet<String> keys = new HashSet<>();
		DataFile.Target target = replaceRequest.directRequest.target;
		String path = replaceRequest.directRequest.path;
		List<DownloadItem> downloadItems = replaceRequest.directRequest.downloadItems;
		ArrayList<DownloadItem> finalItems = new ArrayList<>(downloadItems.size());
		DataFile parent = DataFile.obtain(this, target, path);
		HashSet<String> children = new HashSet<>();
		List<DataFile> childrenList = parent.getChildren();
		if (childrenList != null) {
			for (DataFile file : childrenList) {
				children.add(file.getName().toLowerCase(Locale.getDefault()));
			}
		}
		for (DownloadService.DownloadItem downloadItem : downloadItems) {
			if (replaceRequest.availableItems.contains(downloadItem)) {
				keys.add(getTargetPathKey(target, replaceRequest.directRequest.path, downloadItem.name));
				finalItems.add(downloadItem);
			} else {
				String extension = StringUtils.getFileExtension(downloadItem.name);
				String dotExtension = StringUtils.isEmpty(extension) ? "" : "." + extension;
				String nameWithoutExtension = downloadItem.name.substring(0,
						downloadItem.name.length() - dotExtension.length());
				String name;
				String key;
				int i = 0;
				do {
					String append = (i > 0 ? "-" + i : "") + dotExtension;
					name = nameWithoutExtension + append;
					key = getTargetPathKey(target, path, nameWithoutExtension + append);
					i++;
				} while (children.contains(name.toLowerCase(Locale.getDefault())) ||
						keys.contains(key) || activeKeys.contains(key));
				keys.add(key);
				finalItems.add(new DownloadItem(downloadItem.chanName,
						downloadItem.uri, name, downloadItem.checkSha256));
			}
			if (Thread.interrupted()) {
				throw new InterruptedException();
			}
		}
		return new DirectRequest(target, path, replaceRequest.directRequest.overwrite,
				finalItems, replaceRequest.directRequest.input, replaceRequest.directRequest.allowWrite);
	}

	private void handlePrimaryReplaceKeepAllReplace(ReplaceRequest replaceRequest) {
		HashSet<String> activeKeys = collectActiveKeys();
		PrepareTask<DirectRequest> task = new PrepareTask<>(new PrepareTask.Task<DirectRequest>() {
			@Override
			public void cleanup() {
				replaceRequest.cleanup();
			}

			@Override
			public DirectRequest run() throws InterruptedException {
				return createDirectRequestKeepAll(replaceRequest, activeKeys);
			}

			@Override
			public void onResult(DirectRequest result) {
				primaryRequest = null;
				directRequests.add(result);
				handleRequests();
			}
		});
		task.execute(ConcurrentUtils.SEPARATE_EXECUTOR);
		primaryRequest = new PrepareRequest(task);
	}

	public interface Callback {
		default void requestHandleRequest() {}
		default void requestPermission() {}
		default void onFinishDownloading(boolean success, DataFile.Target target, String path, String name) {}
		default void onCleanup() {}
	}

	public enum PermissionResult {SUCCESS, FAIL, CANCEL}

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
			if (primaryRequest == choiceRequest) {
				primaryRequest = null;
				if (directRequest != null) {
					handlePrimaryDirectRequest(directRequest);
				} else {
					choiceRequest.cleanup();
				}
				handleRequests();
			}
		}

		public void resolve(ReplaceRequest replaceRequest, ReplaceRequest.Action action) {
			if (primaryRequest == replaceRequest) {
				primaryRequest = null;
				if (action != null) {
					switch (action) {
						case REPLACE: {
							directRequests.add(replaceRequest.directRequest);
							break;
						}
						case KEEP_ALL: {
							handlePrimaryReplaceKeepAllReplace(replaceRequest);
							break;
						}
						case SKIP: {
							if (!replaceRequest.availableItems.isEmpty()) {
								directRequests.add(new DirectRequest(replaceRequest.directRequest.target,
										replaceRequest.directRequest.path, replaceRequest.directRequest.overwrite,
										replaceRequest.availableItems, replaceRequest.directRequest.input,
										replaceRequest.directRequest.allowWrite));
							} else {
								// Request with input should contain only 1 download item.
								// Cleanup the request to ensure input stream is closed.
								replaceRequest.cleanup();
							}
							break;
						}
					}
				} else {
					replaceRequest.cleanup();
				}
				handleRequests();
			}
		}

		public void cancel(PrepareRequest prepareRequest) {
			if (primaryRequest == prepareRequest) {
				primaryRequest = null;
				prepareRequest.cleanup();
				handleRequests();
			}
		}

		public Request getPrimaryRequest() {
			return hasStoragePermission() ? primaryRequest : null;
		}

		public void onPermissionResult(PermissionResult result) {
			if (result != PermissionResult.SUCCESS) {
				if (result == PermissionResult.FAIL) {
					ClickableToast.show(R.string.no_access_to_memory);
				}
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

		private void open(DataFile file, boolean allowWrite) {
			refreshNotification(NotificationUpdate.SYNC);
			String extension = StringUtils.getFileExtension(file.getName());
			String type = MimeTypes.forExtension(extension, "image/jpeg");
			if (file.exists()) {
				ScanCallback callback = uri -> {
					try {
						startActivity(new Intent(Intent.ACTION_VIEW)
								.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION |
										(allowWrite ? Intent.FLAG_GRANT_WRITE_URI_PERMISSION : 0))
								.setDataAndType(uri, type));
					} catch (ActivityNotFoundException e) {
						ClickableToast.show(R.string.unknown_address);
					}
				};
				Pair<File, Uri> fileOrUri = file.getFileOrUri();
				if (fileOrUri.first != null) {
					scanFileLegacy(fileOrUri.first, new Pair<>(type, callback));
				} else if (fileOrUri.second != null) {
					callback.onComplete(fileOrUri.second);
				}
			}
		}

		private Boolean accumulateState;

		public Accumulate accumulate() {
			if (accumulateState != null) {
				throw new IllegalStateException();
			}
			accumulateState = false;
			return () -> {
				if (accumulateState) {
					handleRequests();
				}
			};
		}

		private void handleRequestsOrAccumulate() {
			if (accumulateState != null) {
				accumulateState = true;
			} else {
				handleRequests();
			}
		}

		public void downloadDirect(DataFile.Target target, String path, boolean overwrite,
				List<DownloadItem> downloadItems) {
			directRequests.add(new DirectRequest(target, path, overwrite, downloadItems, null, false));
			handleRequestsOrAccumulate();
		}

		public void downloadDirect(DataFile.Target target, String path, String name, InputStream input) {
			directRequests.add(new DirectRequest(target, path, true,
					Collections.singletonList(new DownloadItem(null, null, name, null)), input, false));
			handleRequestsOrAccumulate();
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
				if (!StringUtils.isEmpty(requestItem.originalName)) {
					hasOriginalNames = true;
				}
			}
			boolean allowDetailName = modifyingAllowed;
			boolean allowOriginalName = modifyingAllowed && hasOriginalNames;
			primaryRequest = new UriRequest(Preferences.getDownloadSubdirMode().isEnabled(multiple), requestItems,
					allowDetailName, allowOriginalName, chanName, boardName, threadNumber, threadTitle);
			handleRequestsOrAccumulate();
		}

		public void downloadStorage(InputStream input, String chanName, String boardName, String threadNumber,
				String threadTitle, String fileName, boolean allowDialog, boolean allowWrite) {
			primaryRequest = new StreamRequest(Preferences.getDownloadSubdirMode().isEnabled(false) && allowDialog,
					allowWrite, input, fileName, chanName, boardName, threadNumber, threadTitle);
			handleRequestsOrAccumulate();
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
		public final DataFile lastSuccessFile;
		public final boolean allowWrite;
		public final String activeName;
		public final int progress;
		public final int progressMax;
		public final boolean updateImageOnly;
		public final CountDownLatch syncLatch;

		private NotificationData(Type type, boolean allowHeadsUp,
				int queuedTasks, int successTasks, int errorTasks, boolean allowRetry,
				boolean hasNotFromCache, DataFile lastSuccessFile, boolean allowWrite,
				String activeName, int progress, int progressMax, boolean updateImageOnly, CountDownLatch syncLatch) {
			this.type = type;
			this.allowHeadsUp = allowHeadsUp;
			this.queuedTasks = queuedTasks;
			this.successTasks = successTasks;
			this.errorTasks = errorTasks;
			this.allowRetry = allowRetry;
			this.hasNotFromCache = hasNotFromCache;
			this.lastSuccessFile = lastSuccessFile;
			this.allowWrite = allowWrite;
			this.activeName = activeName;
			this.progress = progress;
			this.progressMax = progressMax;
			this.updateImageOnly = updateImageOnly;
			this.syncLatch = syncLatch;
		}

		public static NotificationData updateData(Type type, boolean allowHeadsUp,
				int queuedTasks, int successTasks, int errorTasks, boolean allowRetry, boolean hasExternal,
				DataFile lastSuccessFile, boolean allowWrite, String activeName, int progress, int progressMax) {
			return new NotificationData(type, allowHeadsUp, queuedTasks, successTasks, errorTasks,
					allowRetry, hasExternal, lastSuccessFile, allowWrite,
					activeName, progress, progressMax, false, null);
		}

		public static NotificationData updateImageOnly(DataFile lastSuccessFile, boolean allowWrite) {
			return new NotificationData(null, false, 0, 0, 0, false, false,
					lastSuccessFile, allowWrite, null, 0, 0, true, null);
		}

		public static NotificationData sync(CountDownLatch syncLatch) {
			return new NotificationData(null, false, 0, 0, 0, false, false,
					null, false, null, 0, 0, false, syncLatch);
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
		public final byte[] checkSha256;
		public final DataFile.Target target;
		public final String path;
		public final String name;
		public final boolean allowWrite;

		private TaskData(String chanName, boolean finishedFromCache, boolean overwrite,
				InputStream input, Uri uri, byte[] checkSha256,
				DataFile.Target target, String path, String name, boolean allowWrite) {
			this.chanName = chanName;
			this.finishedFromCache = finishedFromCache;
			this.overwrite = overwrite;
			this.input = input;
			this.uri = uri;
			this.checkSha256 = checkSha256;
			this.target = target;
			this.path = path;
			this.name = name;
			this.allowWrite = allowWrite;
		}

		public TaskData(String chanName, boolean overwrite,
				InputStream input, DataFile.Target target, String path, String name, boolean allowWrite) {
			this(chanName, true, overwrite, input, null, null, target, path, name, allowWrite);
		}

		public TaskData(String chanName, boolean overwrite,
				Uri from, byte[] checkSha256, DataFile.Target target, String path, String name, boolean allowWrite) {
			this(chanName, false, overwrite, null, from, checkSha256, target, path, name, allowWrite);
		}

		public TaskData newFinishedFromCache(boolean finishedFromCache) {
			return this.finishedFromCache == finishedFromCache ? this : new TaskData(chanName, finishedFromCache,
					overwrite, input, uri, checkSha256, target, path, name, allowWrite);
		}

		public String getKey() {
			return getTargetPathKey(target, path, name);
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
			dest.writeByteArray(checkSha256);
			dest.writeString(target.name());
			dest.writeString(path);
			dest.writeString(name);
			dest.writeByte((byte) (allowWrite ? 1 : 0));
		}

		public static final Creator<TaskData> CREATOR = new Creator<TaskData>() {
			@Override
			public TaskData createFromParcel(Parcel source) {
				String chanName = source.readString();
				boolean finishedFromCache = source.readByte() != 0;
				boolean overwrite = source.readByte() != 0;
				Uri uri = source.readParcelable(getClass().getClassLoader());
				byte[] checkSha256 = source.createByteArray();
				DataFile.Target target = DataFile.Target.valueOf(source.readString());
				String path = source.readString();
				String name = source.readString();
				boolean allowWrite = source.readByte() != 0;
				return new TaskData(chanName, finishedFromCache, overwrite, null, uri, checkSha256,
						target, path, name, allowWrite);
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

	private void setBuilderImage(DataFile file) {
		if (file.exists()) {
			FileHolder fileHolder;
			Pair<File, Uri> fileOrUri = file.getFileOrUri();
			if (fileOrUri.first != null) {
				fileHolder = FileHolder.obtain(fileOrUri.first);
			} else if (fileOrUri.second != null) {
				fileHolder = FileHolder.obtain(this, fileOrUri.second);
			} else {
				fileHolder = null;
			}
			if (fileHolder != null) {
				DisplayMetrics metrics = getResources().getDisplayMetrics();
				int size = Math.max(metrics.widthPixels, metrics.heightPixels);
				builder.setLargeIcon(fileHolder.readImageBitmap(size / 4, false, false));
			}
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
			switch (notificationData.type) {
				case PROGRESS:
				case REQUEST: {
					builder.addAction(C.API_LOLLIPOP ? 0 : R.drawable.ic_action_cancel_dark,
							getString(android.R.string.cancel), PendingIntent.getBroadcast(this, 0,
									new Intent(this, Receiver.class).setAction(ACTION_CANCEL),
									PendingIntent.FLAG_UPDATE_CURRENT));
					break;
				}
				case RESULT: {
					if (notificationData.allowRetry) {
						builder.addAction(C.API_LOLLIPOP ? 0 : R.drawable.ic_action_refresh_dark,
								getString(R.string.retry), PendingIntent.getBroadcast(this, 0,
										new Intent(this, Receiver.class).setAction(ACTION_RETRY),
										PendingIntent.FLAG_UPDATE_CURRENT));
					}
					break;
				}
			}
			if (notificationData.type == NotificationData.Type.REQUEST) {
				builder.setContentIntent(PendingIntent.getActivity(this, 0,
						new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT));
			} else if (notificationData.lastSuccessFile != null) {
				builder.setContentIntent(PendingIntent.getBroadcast(this, 0, new Intent(this, Receiver.class)
						.putExtra(EXTRA_FILE_TARGET, notificationData.lastSuccessFile.getTarget().name())
						.putExtra(EXTRA_FILE_PATH, notificationData.lastSuccessFile.getRelativePath())
						.putExtra(EXTRA_ALLOW_WRITE, notificationData.allowWrite)
						.setAction(ACTION_OPEN), PendingIntent.FLAG_UPDATE_CURRENT));
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
				contentTitle = getString(R.string.downloading_number_of_number__format, ready, total);
				contentText = getString(R.string.file_name__format, notificationData.activeName);
				headsUp = false;
				foreground = true;
				builder.setProgress(notificationData.progressMax, notificationData.progress,
						notificationData.progressMax == 0 || notificationData.progress > notificationData.progressMax
								|| notificationData.progress < 0);
				break;
			}
			case RESULT: {
				contentTitle = getString(notificationData.hasNotFromCache
						? R.string.download_completed : R.string.save_completed);
				contentText = getString(R.string.success_number_not_loaded_number__format,
						notificationData.successTasks, notificationData.errorTasks);
				headsUp = notificationData.allowHeadsUp;
				foreground = false;
				break;
			}
			case REQUEST: {
				contentTitle = getString(R.string.pending_downloading);
				contentText = getString(R.string.confirmation_is_required);
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
		startStopForeground(foreground, foreground ? builder.build() : null);
		if (!foreground) {
			if (C.API_NOUGAT) {
				// Await notification removed so it could be dismissed by user
				for (int i = 0; i < 10; i++) {
					StatusBarNotification[] notifications = notificationManager.getActiveNotifications();
					if (notifications == null) {
						break;
					}
					boolean found = false;
					for (StatusBarNotification notification : notifications) {
						if (notification.getId() == C.NOTIFICATION_ID_DOWNLOADING) {
							found = true;
							break;
						}
					}
					if (!found) {
						break;
					}
					try {
						Thread.sleep(50);
					} catch (InterruptedException e) {
						return;
					}
				}
			}
			notificationManager.notify(C.NOTIFICATION_ID_DOWNLOADING, builder.build());
		}
	}

	private enum NotificationUpdate {NORMAL, HEADS_UP, SYNC}

	private void refreshNotification(NotificationUpdate notificationUpdate) {
		boolean hasTask = activeTask != null;
		boolean hasResults = !queuedTasks.isEmpty() || !successTasks.isEmpty() || !errorTasks.isEmpty();
		boolean hasRequests = primaryRequest != null || !directRequests.isEmpty();
		boolean needForegroundOrNotification = hasTask || hasResults || hasRequests;
		if (needForegroundOrNotification) {
			boolean allowRetry = false;
			boolean hasNotFromCache = false;
			TaskData lastSuccessFileTaskData = null;
			if (hasTask || !hasRequests) {
				for (TaskData taskData : successTasks.values()) {
					if (!taskData.finishedFromCache) {
						hasNotFromCache = true;
					}
					if (taskData.target.isExternal()) {
						lastSuccessFileTaskData = taskData;
					}
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
			DataFile lastSuccessFile = lastSuccessFileTaskData != null ? getDataFile(lastSuccessFileTaskData) : null;
			boolean allowWrite = lastSuccessFileTaskData != null && lastSuccessFileTaskData.allowWrite;
			NotificationData.Type type = hasTask ? NotificationData.Type.PROGRESS : hasRequests
					? NotificationData.Type.REQUEST : NotificationData.Type.RESULT;
			boolean allowHeadsUp = type == NotificationData.Type.RESULT &&
					notificationUpdate == NotificationUpdate.HEADS_UP;
			String activeName = hasTask ? activeTask.second.getFileName() : null;
			notificationsQueue.add(NotificationData.updateData(type, allowHeadsUp,
					queuedTasks.size(), successTasks.size(), errorTasks.size(), allowRetry, hasNotFromCache,
					lastSuccessFile, allowWrite, activeName, progress, progressMax));
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
	public void onStartDownloading() {
		progress = 0;
		progressMax = 0;
		refreshNotification(NotificationUpdate.NORMAL);
	}

	@Override
	public void onFinishDownloading(boolean success, Uri uri, DataFile file, ErrorItem errorItem) {
		TaskData taskData = activeTask.first.newFinishedFromCache(activeTask.second.isDownloadingFromCache());
		activeTask = null;
		onFinishDownloadingInternal(success, taskData);
	}

	private void onFinishDownloadingInternal(boolean success, TaskData taskData) {
		if (success) {
			File file = getDataFile(taskData).getFileOrUri().first;
			if (file != null) {
				scanFileLegacy(file, null);
			}
		}
		for (Callback callback : callbacks) {
			callback.onFinishDownloading(success, taskData.target, taskData.path, taskData.name);
		}
		if (success) {
			successTasks.put(taskData.getKey(), taskData);
		} else {
			errorTasks.put(taskData.getKey(), taskData);
		}
		if (!queuedTasks.isEmpty()) {
			if (success && taskData.target.isExternal()) {
				// Update image explicitly, because task type won't be changed
				notificationsQueue.add(NotificationData.updateImageOnly(getDataFile(taskData), taskData.allowWrite));
			}
			startNextTask();
		} else {
			cachedDirectories.clear();
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

	private void scanFileLegacy(File file, Pair<String, ScanCallback> callback) {
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
			ConcurrentUtils.HANDLER.postDelayed(() -> {
				synchronized (handled) {
					if (!handled[0]) {
						handled[0] = true;
						callback.second.onComplete(FileProvider.convertDownloadsLegacyFile(file, callback.first));
					}
				}
			}, 1000);
		} else {
			listener = null;
		}
		MediaScannerConnection.scanFile(this, fileArray, null, listener);
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
		Chan chan = Chan.getPreferred(chanName, uri);
		return chanName != null && chan.locator.safe(false).isAttachmentUri(uri);
	}

	private static String getDesiredFileName(Uri uri, String fileName, String originalName, boolean detailName,
			String chanName, String boardName, String threadNumber) {
		if (isFileNameModifyingAllowed(chanName, uri)) {
			if (!StringUtils.isEmpty(originalName) && Preferences.isDownloadOriginalName()) {
				fileName = originalName;
			}
			if (detailName) {
				fileName = getFileNameWithChanBoardThreadData(fileName, chanName, boardName, threadNumber);
			}
		}
		return fileName;
	}

	public interface Accumulate extends Closeable {
		@Override
		void close();
	}

	public interface Request {
		default void cleanup() {}
	}

	public static abstract class ChoiceRequest implements Request {
		private final boolean shouldHandle;
		public final String chanName;
		public final String boardName;
		public final String threadNumber;
		public final String threadTitle;
		public final boolean allowWrite;

		public Object state;

		protected ChoiceRequest(boolean shouldHandle, boolean allowWrite,
				String chanName, String boardName, String threadNumber, String threadTitle) {
			this.shouldHandle = shouldHandle;
			this.allowWrite = allowWrite;
			this.chanName = chanName;
			this.boardName = boardName;
			this.threadNumber = threadNumber;
			this.threadTitle = threadTitle;
		}

		public abstract boolean allowDetailName();
		public abstract boolean allowOriginalName();
		public abstract DirectRequest complete(String path, boolean detailName, boolean originalName);
	}

	public static class DirectRequest {
		public final DataFile.Target target;
		public final String path;
		public final boolean overwrite;
		public final InputStream input;
		public final List<DownloadItem> downloadItems;
		public final boolean allowWrite;

		private DirectRequest(DataFile.Target target, String path, boolean overwrite,
				List<DownloadItem> downloadItems, InputStream input, boolean allowWrite) {
			this.target = target;
			this.path = path;
			this.overwrite = overwrite;
			this.downloadItems = downloadItems;
			this.input = input;
			this.allowWrite = allowWrite;
		}

		private void cleanup() {
			if (input != null) {
				IOUtils.close(input);
			}
		}
	}

	public static class ReplaceRequest implements Request {
		public enum Action {REPLACE, KEEP_ALL, SKIP}

		private final DirectRequest directRequest;
		private final List<DownloadItem> availableItems;

		public final DataFile lastExistingFile;
		public final int queued;
		public final int exists;

		public Object state;

		private ReplaceRequest(DirectRequest directRequest, List<DownloadItem> availableItems,
				DataFile lastExistingFile, int queued, int exists) {
			this.directRequest = directRequest;
			this.availableItems = availableItems;
			this.lastExistingFile = lastExistingFile;
			this.queued = queued;
			this.exists = exists;
		}

		@Override
		public void cleanup() {
			directRequest.cleanup();
		}
	}

	public static class PrepareRequest implements Request {
		private final PrepareTask<?> task;

		protected PrepareRequest(PrepareTask<?> task) {
			this.task = task;
		}

		@Override
		public void cleanup() {
			task.task.cleanup();
			task.cancel();
		}
	}

	private static class UriRequest extends ChoiceRequest {
		public final List<RequestItem> items;
		public final boolean allowDetailName;
		public final boolean allowOriginalName;

		private UriRequest(boolean shouldHandle, List<RequestItem> items,
				boolean allowDetailName, boolean allowOriginalName,
				String chanName, String boardName, String threadNumber, String threadTitle) {
			super(shouldHandle, false, chanName, boardName, threadNumber, threadTitle);
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
						chanName, boardName, threadNumber), null));
			}
			return new DirectRequest(DataFile.Target.DOWNLOADS, path, true, downloadItems, null, allowWrite);
		}
	}

	private static class StreamRequest extends ChoiceRequest {
		public final InputStream input;
		public final String fileName;

		private StreamRequest(boolean shouldHandle, boolean allowWrite, InputStream input, String fileName,
				String chanName, String boardName, String threadNumber, String threadTitle) {
			super(shouldHandle, allowWrite, chanName, boardName, threadNumber, threadTitle);
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
			DownloadItem downloadItem = new DownloadItem(chanName, null, fileName, null);
			return new DirectRequest(DataFile.Target.DOWNLOADS, path, true,
					Collections.singletonList(downloadItem), input, allowWrite);
		}

		@Override
		public void cleanup() {
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
		public final byte[] checkSha256;

		public DownloadItem(String chanName, Uri uri, String name, byte[] checkSha256) {
			this.chanName = chanName;
			this.uri = uri;
			this.name = name;
			this.checkSha256 = checkSha256;
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
			dest.writeByteArray(checkSha256);
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
				byte[] checkSha256 = source.createByteArray();
				return new DownloadItem(chanName, uriString != null ? Uri.parse(uriString) : null, name, checkSha256);
			}
		};

		@Override
		public boolean equals(Object o) {
			if (o == this) {
				return true;
			}
			if (o instanceof DownloadItem) {
				DownloadItem co = (DownloadItem) o;
				return (CommonUtils.equals(uri, co.uri)) &&
						CommonUtils.equals(name, co.name);
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
				String targetString = intent.getStringExtra(EXTRA_FILE_TARGET);
				String path = intent.getStringExtra(EXTRA_FILE_PATH);
				boolean allowWrite = intent.getBooleanExtra(EXTRA_ALLOW_WRITE, false);
				DataFile file = targetString != null && path != null
						? DataFile.obtain(context, DataFile.Target.valueOf(targetString), path) : null;
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
							downloadBinder.open(file, allowWrite);
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
