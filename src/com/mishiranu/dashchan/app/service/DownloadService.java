/*
 * Copyright 2014-2016 Fukurou Mishiranu
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mishiranu.dashchan.app.service;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

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
import android.webkit.MimeTypeMap;

import chan.util.StringUtils;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.async.ReadFileTask;
import com.mishiranu.dashchan.content.DownloadManager;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.FileHolder;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ToastUtils;
import com.mishiranu.dashchan.util.ViewUtils;

public class DownloadService extends Service implements ReadFileTask.Callback, ReadFileTask.AsyncFinishCallback,
		Runnable, MediaScannerConnection.MediaScannerConnectionClient
{
	private static Executor SINGLE_THREAD_EXECUTOR = Executors.newSingleThreadExecutor();
	
	private static String ACTION_START = "com.mishiranu.dashchan.action.START";
	private static String ACTION_SHOW_FAKE = "com.mishiranu.dashchan.action.SHOW_FAKE";
	private static String ACTION_OPEN_FILE = "com.mishiranu.dashchan.action.OPEN_FILE";
	private static String ACTION_CANCEL_DOWNLOADING = "com.mishiranu.dashchan.action.CANCEL_DOWNLOADING";
	private static String ACTION_RETRY_DOWNLOADING = "com.mishiranu.dashchan.action.RETRY_DOWNLOADING";
	
	private static String EXTRA_DIRECTORY = "com.mishiranu.dashchan.extra.DIRECTORY";
	private static String EXTRA_DOWNLOAD_ITEMS = "com.mishiranu.dashchan.extra.DOWNLOAD_ITEMS";
	
	private static String EXTRA_FILE = "com.mishiranu.dashchan.extra.FILE";
	private static String EXTRA_SUCCESS = "com.mishiranu.dashchan.extra.SUCCESS";
	
	private Context mContext;
	private NotificationManager mNotificationManager;
	private int mNotificationColor;
	private PowerManager.WakeLock mWakeLock;
	private boolean mFirstStart;
	
	private Thread mNotificationsWorker;
	private final LinkedBlockingQueue<NotificationData> mNotificationsQueue = new LinkedBlockingQueue<>();
	
	private static Intent obtainIntent(Context context, String action)
	{
		return new Intent(context, DownloadService.class).setAction(action);
	}
	
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	@Override
	public void onCreate()
	{
		super.onCreate();
		mNotificationsWorker = new Thread(this, "DownloadServiceNotificationThread");
		mNotificationsWorker.start();
		mFirstStart = true;
		mContext = new ContextThemeWrapper(this, R.style.Theme_Special_Notification);
		mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		int notificationColor = 0;
		if (C.API_LOLLIPOP)
		{
			Context themedContext = new ContextThemeWrapper(this, Preferences.getThemeResource());
			notificationColor = ResourceUtils.getColor(themedContext, android.R.attr.colorAccent);
		}
		mNotificationColor = notificationColor;
		PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
		mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DownloadServiceWakeLock");
		mWakeLock.setReferenceCounted(false);
	}
	
	@Override
	public void onDestroy()
	{
		super.onDestroy();
		mNotificationsWorker.interrupt();
		mWakeLock.release();
		DownloadManager.getInstance().notifyServiceDestroy();
		if (mReadFileTask != null) mReadFileTask.cancel();
		try
		{
			mNotificationsWorker.join();
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
		stopForeground(true);
		mNotificationManager.cancel(C.NOTIFICATION_ID_DOWNLOAD);
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		boolean firstStart = mFirstStart;
		mFirstStart = false;
		if (intent != null)
		{
			String action = intent.getAction();
			if (ACTION_START.equals(action))
			{
				File directory = new File(intent.getStringExtra(EXTRA_DIRECTORY));
				ArrayList<DownloadItem> downloadItems = intent.getParcelableArrayListExtra(EXTRA_DOWNLOAD_ITEMS);
				for (int i = 0; i < downloadItems.size(); i++)
				{
					DownloadItem downloadItem = downloadItems.get(i);
					enqueue(downloadItem.chanName, downloadItem.uri, new File(directory, downloadItem.name),
							i == downloadItems.size() - 1);
				}
			}
			else if (ACTION_SHOW_FAKE.equals(action))
			{
				File file = new File(intent.getStringExtra(EXTRA_FILE));
				boolean success = intent.getBooleanExtra(EXTRA_SUCCESS, true);
				TaskData taskData = new TaskData(null, Uri.fromFile(file), file);
				taskData.retryable = false;
				taskData.local = true;
				mErrorTasks.remove(taskData);
				mSuccessTasks.remove(taskData);
				if (success) mSuccessTasks.add(taskData); else mErrorTasks.add(taskData);
				mNotificationsQueue.add(new NotificationData(mSuccessTasks.get(mSuccessTasks.size() - 1)));
				refreshNotification(true);
			}
			else if (firstStart)
			{
				// Start caused by clicking on notification when application was closed
				stopSelf();
			}
			else if (ACTION_OPEN_FILE.equals(action))
			{
				mBuilder = null;
				refreshNotification(false);
				if (mSuccessTasks.size() > 0)
				{
					TaskData taskData = mSuccessTasks.get(mSuccessTasks.size() - 1);
					String extension = StringUtils.getFileExtension(taskData.to.getPath());
					String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
					Uri uri = taskData.to.equals(mScannedMediaFile) ? mScannedMediaUri : Uri.fromFile(taskData.to);
					try
					{
						mContext.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri,
								type).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
					}
					catch (ActivityNotFoundException e)
					{
						ToastUtils.show(this, R.string.message_unknown_address);
					}
				}
			}
			else if (ACTION_CANCEL_DOWNLOADING.equals(action))
			{
				stopSelf();
			}
			else if (ACTION_RETRY_DOWNLOADING.equals(action))
			{
				if (mReadFileTask != null) mReadFileTask.cancel();
				mSuccessTasks.clear();
				for (TaskData taskData : mErrorTasks)
				{
					if (taskData.retryable) mQueuedTasks.add(taskData);
				}
				mErrorTasks.clear();
				if (mQueuedTasks.size() > 0)
				{
					TaskData taskData = mQueuedTasks.get(0);
					start(taskData.chanName, taskData.from, taskData.to);
				}
			}
		}
		return START_NOT_STICKY;
	}
	
	@Override
	public IBinder onBind(Intent intent)
	{
		return null;
	}
	
	private static class NotificationData
	{
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
				String currentTaskFileName, int progress, int progressMax, boolean forceSetImage)
		{
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
				String currentTaskFileName, int progress, int progressMax)
		{
			this(allowHeadsUp, hasTask, queudTasksSize, successTasksSize, errorTasks, hasExternal, lastSuccessTaskData,
					currentTaskFileName, progress, progressMax, false);
		}
		
		public NotificationData(TaskData lastSuccessTaskData)
		{
			this(false, false, 0, 0, null, false, lastSuccessTaskData, null, 0, 0, true);
		}
	}
	
	@Override
	public void run()
	{
		boolean interrupted = false;
		while (true)
		{
			NotificationData notificationData = null;
			if (!interrupted)
			{
				try
				{
					notificationData = mNotificationsQueue.take();
				}
				catch (InterruptedException e)
				{
					interrupted = true;
				}
			}
			if (interrupted) notificationData = mNotificationsQueue.poll();
			if (notificationData == null) return;
			if (notificationData.forceSetImage)
			{
				if (mBuilder != null) setBuilderImage(notificationData.lastSuccessTaskData);
			}
			else refreshNotificationFromThread(notificationData);
		}
	}
	
	private final ArrayList<TaskData> mQueuedTasks = new ArrayList<>();
	private final ArrayList<TaskData> mSuccessTasks = new ArrayList<>();
	private final ArrayList<TaskData> mErrorTasks = new ArrayList<>();

	private Notification.Builder mBuilder;
	private Notification.BigTextStyle mNotificationStyle;
	private CharSequence mNotificationBigText;
	
	private volatile int mProgress, mProgressMax;
	private volatile long mLastUpdate;

	private ReadFileTask mReadFileTask;
	
	private File mScannedMediaFile;
	private Uri mScannedMediaUri;
	
	private static class TaskData
	{
		public final String chanName;
		public final Uri from;
		public final File to;
		public boolean retryable = true;
		public boolean local = false;
		public String errorInfo;
		
		public TaskData(String chanName, Uri from, File to)
		{
			this.chanName = chanName;
			this.from = from;
			this.to = to;
		}
		
		@Override
		public boolean equals(Object o)
		{
			if (o == this) return true;
			if (o instanceof TaskData) return to.equals(((TaskData) o).to);
			return false;
		}
		
		@Override
		public int hashCode()
		{
			return to.hashCode();
		}
	}
	
	private void enqueue(String chanName, Uri from, File to, boolean refreshNotification)
	{
		TaskData taskData = new TaskData(chanName, from, to);
		boolean success = mSuccessTasks.contains(taskData);
		if (!success && !mQueuedTasks.contains(taskData))
		{
			if (mErrorTasks.contains(taskData)) mErrorTasks.remove(taskData);
			mQueuedTasks.add(taskData);
			DownloadManager.getInstance().notifyFileAddedToDownloadQueue(taskData.to);
			boolean started = start(chanName, from, to);
			if (!started && refreshNotification) refreshNotification(false);
		}
		else if (success && mReadFileTask == null) refreshNotification(true);
	}
	
	private boolean start(String chanName, Uri from, File to)
	{
		if (mReadFileTask == null)
		{
			mProgressMax = 0;
			mProgress = 0;
			mLastUpdate = 0L;
			mReadFileTask = new ReadFileTask(this, chanName, from, to, true, this);
			mReadFileTask.executeOnExecutor(SINGLE_THREAD_EXECUTOR);
			return true;
		}
		return false;
	}
	
	private boolean mOldStateWithTask = false;
	
	private static final int[] ICON_ATTRS = {R.attr.notificationRefresh, R.attr.notificationCancel};
	
	private void setBuilderImage(TaskData taskData)
	{
		if (taskData.to.exists())
		{
			FileHolder fileHolder = FileHolder.obtain(taskData.to);
			DisplayMetrics metrics = mContext.getResources().getDisplayMetrics();
			int size = Math.max(metrics.widthPixels, metrics.heightPixels);
			mBuilder.setLargeIcon(fileHolder.readImageBitmap(size / 4, false, false));
		}
	}
	
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private void refreshNotificationFromThread(NotificationData notificationData)
	{
		if (mBuilder == null || (notificationData.hasTask) != mOldStateWithTask)
		{
			mOldStateWithTask = notificationData.hasTask;
			mNotificationManager.cancel(C.NOTIFICATION_ID_DOWNLOAD);
			mBuilder = new Notification.Builder(mContext);
			mBuilder.setDeleteIntent(PendingIntent.getService(mContext, 0,
					obtainIntent(this, ACTION_CANCEL_DOWNLOADING), PendingIntent.FLAG_UPDATE_CURRENT));
			mBuilder.setSmallIcon(notificationData.hasTask ? android.R.drawable.stat_sys_download
					: android.R.drawable.stat_sys_download_done);
			if (notificationData.lastSuccessTaskData != null) setBuilderImage(notificationData.lastSuccessTaskData);
			TypedArray typedArray = mContext.obtainStyledAttributes(ICON_ATTRS);
			if (notificationData.hasTask)
			{
				PendingIntent cancelIntent = PendingIntent.getService(mContext, 0,
						obtainIntent(this, ACTION_CANCEL_DOWNLOADING), PendingIntent.FLAG_UPDATE_CURRENT);
				ViewUtils.addNotificationAction(mBuilder, mContext, typedArray, 1,
						android.R.string.cancel, cancelIntent);
			}
			else if (notificationData.errorTasks.size() > 0)
			{
				boolean hasRetryable = false;
				for (TaskData taskData : notificationData.errorTasks)
				{
					if (taskData.retryable)
					{
						hasRetryable = true;
						break;
					}
				}
				if (hasRetryable)
				{
					PendingIntent retryIntent = PendingIntent.getService(mContext, 0,
							obtainIntent(this, ACTION_RETRY_DOWNLOADING), PendingIntent.FLAG_UPDATE_CURRENT);
					ViewUtils.addNotificationAction(mBuilder, mContext, typedArray, 0,
							R.string.action_retry, retryIntent);
				}
			}
			typedArray.recycle();
			mNotificationBigText = null;
			mNotificationStyle = new Notification.BigTextStyle();
			mBuilder.setStyle(mNotificationStyle);
			if (notificationData.errorTasks.size() > 0)
			{
				SpannableStringBuilder spannable = new SpannableStringBuilder();
				spannable.append("\n\n");
				StringUtils.appendSpan(spannable, mContext.getString(R.string.message_download_not_loaded),
						C.API_LOLLIPOP ? new TypefaceSpan("sans-serif-medium") : new StyleSpan(Typeface.BOLD));
				for (int i = 0; i < notificationData.errorTasks.size(); i++)
				{
					spannable.append('\n');
					TaskData taskData = notificationData.errorTasks.get(i);
					spannable.append(taskData.to.getName());
					if (taskData.errorInfo != null) spannable.append(" (").append(taskData.errorInfo).append(')');
				}
				mNotificationBigText = spannable;
			}
			if (notificationData.successTasksSize > 0)
			{
				mBuilder.setContentIntent(PendingIntent.getService(mContext, 0,
						obtainIntent(this, ACTION_OPEN_FILE), PendingIntent.FLAG_UPDATE_CURRENT));
			}
		}
		String contentTitle;
		String contentText;
		boolean headsUp;
		if (notificationData.hasTask)
		{
			int ready = notificationData.errorTasks.size() + notificationData.successTasksSize;
			int total = ready + notificationData.queudTasksSize;
			ready++;
			contentTitle = mContext.getString(R.string.message_download_count_format, ready, total);
			contentText = mContext.getString(R.string.message_download_name_format,
					notificationData.currentTaskFileName);
			headsUp = false;
			mBuilder.setProgress(notificationData.progressMax, notificationData.progress,
					notificationData.progressMax == 0 || notificationData.progress > notificationData.progressMax
					|| notificationData.progress < 0);
		}
		else
		{
			contentTitle = mContext.getString(notificationData.hasExternal ? R.string.message_download_completed
					: R.string.message_save_completed);
			contentText = mContext.getString(R.string.message_download_result_format, notificationData.successTasksSize,
					notificationData.errorTasks.size());
			headsUp = notificationData.allowHeadsUp;
			mBuilder.setOngoing(false);
		}
		mBuilder.setContentTitle(contentTitle);
		mBuilder.setContentText(contentText);
		if (mNotificationBigText != null)
		{
			SpannableStringBuilder spannable = new SpannableStringBuilder(contentText);
			spannable.append(mNotificationBigText);
			mNotificationStyle.bigText(spannable);
		}
		else mNotificationStyle.bigText(contentText);
		if (C.API_LOLLIPOP)
		{
			mBuilder.setColor(mNotificationColor);
			if (headsUp && Preferences.isNotifyDownloadComplete())
			{
				mBuilder.setPriority(Notification.PRIORITY_HIGH);
				mBuilder.setVibrate(new long[0]);
			}
			else
			{
				mBuilder.setPriority(Notification.PRIORITY_DEFAULT);
				mBuilder.setVibrate(null);
			}
		}
		else mBuilder.setTicker(headsUp ? contentTitle : null);
		Notification notification = mBuilder.build();
		if (notificationData.hasTask) startForeground(C.NOTIFICATION_ID_DOWNLOAD, notification); else
		{
			stopForeground(true);
			mNotificationManager.notify(C.NOTIFICATION_ID_DOWNLOAD, notification);
		}
	}
	
	private void refreshNotification(boolean allowHeadsUp)
	{
		boolean hasTask = mReadFileTask != null;
		TaskData lastSuccessTaskData = mSuccessTasks.size() > 0 ? mSuccessTasks.get(mSuccessTasks.size() - 1) : null;
		boolean hasExternal = false;
		for (TaskData taskData : mSuccessTasks)
		{
			if (!taskData.local)
			{
				hasExternal = true;
				break;
			}
		}
		for (TaskData taskData : mErrorTasks)
		{
			if (!taskData.local)
			{
				hasExternal = true;
				break;
			}
		}
		mNotificationsQueue.add(new NotificationData(allowHeadsUp, hasTask, mQueuedTasks.size(), mSuccessTasks.size(),
				mErrorTasks, hasExternal, lastSuccessTaskData, hasTask ? mReadFileTask.getFileName() : null,
				mProgress, mProgressMax));
		if (hasTask) mWakeLock.acquire(); else mWakeLock.acquire(15000);
	}
	
	@Override
	public void onFileExists(Uri uri, File file)
	{
		onFinishDownloading(true, uri, file, null);
	}
	
	@Override
	public void onStartDownloading(Uri uri, File file)
	{
		refreshNotification(false);
	}
	
	@Override
	public void onFinishDownloading(boolean success, Uri uri, File file, ErrorItem errorItem)
	{
		if (success) MediaScannerConnection.scanFile(mContext, new String[] {file.getAbsolutePath()}, null, this);
		boolean local = mReadFileTask.isDownloadingFromCache();
		mReadFileTask = null;
		TaskData taskData = new TaskData(null, uri, file);
		taskData.local = local;
		mQueuedTasks.remove(taskData);
		if (success)
		{
			DownloadManager.getInstance().notifyFileRemovedFromDownloadQueue(file);
			mSuccessTasks.add(taskData);
		}
		else
		{
			if (errorItem != null)
			{
				int code = errorItem.httpResponseCode;
				if (code >= 400 && code < 600) taskData.errorInfo = "HTTP " + code;
			}
			mErrorTasks.add(taskData);
		}
		if (mQueuedTasks.size() > 0)
		{
			// Update image explicitly, because task state won't be changed
			if (success) mNotificationsQueue.add(new NotificationData(taskData));
			taskData = mQueuedTasks.get(0);
			start(taskData.chanName, taskData.from, taskData.to);
		}
		else refreshNotification(true);
	}
	
	@Override
	public void onUpdateProgress(long progress, long progressMax)
	{
		mProgress = (int) progress;
		mProgressMax = (int) progressMax;
		long t = System.currentTimeMillis();
		if (t - mLastUpdate >= 1000L)
		{
			mLastUpdate = t;
			refreshNotification(false);
		}
	}
	
	@Override
	public void onFinishDownloadingInThread()
	{
		DownloadManager.getInstance().notifyFinishDownloadingInThread();
	}
	
	@Override
	public void onMediaScannerConnected()
	{
		
	}
	
	@Override
	public void onScanCompleted(String path, Uri uri)
	{
		mScannedMediaFile = new File(path);
		mScannedMediaUri = uri;
	}
	
	public static class DownloadItem implements Parcelable
	{
		public final String chanName;
		public final Uri uri;
		public final String name;
		
		public DownloadItem(String chanName, Uri uri, String name)
		{
			this.chanName = chanName;
			this.uri = uri;
			this.name = name;
		}
		
		@Override
		public int describeContents()
		{
			return 0;
		}
		
		@Override
		public void writeToParcel(Parcel dest, int flags)
		{
			dest.writeString(chanName);
			dest.writeString(uri != null ? uri.toString() : null);
			dest.writeString(name);
		}
		
		public static final Creator<DownloadItem> CREATOR = new Creator<DownloadItem>()
		{
			@Override
			public DownloadItem[] newArray(int size)
			{
				return new DownloadItem[size];
			}
			
			@Override
			public DownloadItem createFromParcel(Parcel source)
			{
				String chanName = source.readString();
				String uriString = source.readString();
				String name = source.readString();
				return new DownloadItem(chanName, uriString != null ? Uri.parse(uriString) : null, name);
			}
		};
		
		@Override
		public boolean equals(Object o)
		{
			if (o == this) return true;
			if (o instanceof DownloadItem)
			{
				DownloadItem co = (DownloadItem) o;
				return (uri == co.uri || uri != null && uri.equals(co.uri)) && StringUtils.equals(name, co.name);
			}
			return false;
		}
		
		@Override
		public int hashCode()
		{
			int prime = 31;
			int result = 1;
			result = prime * result + (uri != null ? uri.hashCode() : 0);
			result = prime * result + (name != null ? name.hashCode() : 0);
			return result;
		}
	}
	
	public static void downloadDirect(Context context, File directory, ArrayList<DownloadItem> downloadItems)
	{
		int size = downloadItems.size();
		int trimCount = 1000;
		if (size > trimCount)
		{
			for (int i = 0; i < size; i += trimCount)
			{
				int end = Math.min(i + trimCount, size);
				ArrayList<DownloadService.DownloadItem> itDownloadItems = new ArrayList<>(end - i);
				for (int j = i; j < end; j++) itDownloadItems.add(downloadItems.get(j));
				downloadDirectInternal(context, directory, itDownloadItems);
			}
		}
		else downloadDirectInternal(context, directory, downloadItems);
	}
	
	private static void downloadDirectInternal(Context context, File directory, ArrayList<DownloadItem> downloadItems)
	{
		Intent intent = obtainIntent(context, ACTION_START);
		intent.putExtra(EXTRA_DIRECTORY, directory.getAbsolutePath());
		intent.putExtra(EXTRA_DOWNLOAD_ITEMS, downloadItems);
		context.startService(intent);
	}
	
	public static void showFake(Context context, File file, boolean success)
	{
		Intent intent = obtainIntent(context, ACTION_SHOW_FAKE);
		intent.putExtra(EXTRA_FILE, file.getAbsolutePath());
		intent.putExtra(EXTRA_SUCCESS, success);
		context.startService(intent);
	}
}