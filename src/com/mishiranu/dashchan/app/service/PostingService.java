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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.content.LocalBroadcastManager;
import android.view.ContextThemeWrapper;

import chan.content.ChanConfiguration;
import chan.content.ChanMarkup;
import chan.content.ChanPerformer;
import chan.text.CommentEditor;
import chan.util.StringUtils;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.app.PostingActivity;
import com.mishiranu.dashchan.async.SendPostTask;
import com.mishiranu.dashchan.content.StatisticsManager;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.storage.DraftsStorage;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;

public class PostingService extends Service implements Runnable, SendPostTask.Callback
{
	private static String ACTION_CANCEL_POSTING = "com.mishiranu.dashchan.action.CANCEL_POSTING";
	
	private static String EXTRA_KEY = "com.mishiranu.dashchan.extra.KEY";
	
	private final HashMap<String, ArrayList<Callback>> mCallbacks = new HashMap<>();
	private final HashMap<Callback, String> mCallbackKeys = new HashMap<>();
	private final HashMap<String, TaskState> mTasks = new HashMap<>();
	
	private NotificationManager mNotificationManager;
	private PowerManager.WakeLock mWakeLock;
	
	private Thread mNotificationsWorker;
	private final LinkedBlockingQueue<TaskState> mNotificationsQueue = new LinkedBlockingQueue<>();
	
	private static class TaskState
	{
		public final String key;
		public final SendPostTask task;
		public final Notification.Builder builder;
		public final int notificationId = ViewUtils.obtainNextNotificationId();
		public final String text;
		
		private SendPostTask.ProgressState progressState = SendPostTask.ProgressState.CONNECTING;
		private int attachmentIndex = 0;
		private int attachmentsCount = 0;
		
		private int progress = 0;
		private int progressMax = 0;
		
		private boolean first;
		private boolean cancel;
		
		public TaskState(String key, SendPostTask task, Context context, String chanName,
				ChanPerformer.SendPostData data)
		{
			this.key = key;
			this.task = task;
			builder = new Notification.Builder(context);
			text = buildNotificationText(chanName, data.boardName, data.threadNumber, null);
		}
	}
	
	public static String buildNotificationText(String chanName, String boardName, String threadNumber,
			String postNumber)
	{
		ChanConfiguration configuration = ChanConfiguration.get(chanName);
		StringBuilder builder = new StringBuilder(configuration.getTitle()).append(", ");
		builder.append(StringUtils.formatThreadTitle(chanName, boardName, threadNumber != null ? threadNumber : "?"));
		if (!StringUtils.isEmpty(postNumber)) builder.append(", #").append(postNumber);
		return builder.toString();
	}
	
	@Override
	public void onCreate()
	{
		super.onCreate();
		mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
		mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PostingWakeLock");
		mWakeLock.setReferenceCounted(false);
		mNotificationsWorker = new Thread(this, "PostingServiceNotificationThread");
		mNotificationsWorker.start();
	}
	
	@Override
	public void onDestroy()
	{
		super.onDestroy();
		mNotificationsWorker.interrupt();
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		if (intent != null && ACTION_CANCEL_POSTING.equals(intent.getAction()))
		{
			performCancel(intent.getStringExtra(EXTRA_KEY));
		}
		return START_NOT_STICKY;
	}
	
	@SuppressLint("NewApi")
	@Override
	public void run()
	{
		boolean interrupted = false;
		while (true)
		{
			TaskState taskState = null;
			if (!interrupted)
			{
				try
				{
					taskState = mNotificationsQueue.take();
				}
				catch (InterruptedException e)
				{
					interrupted = true;
				}
			}
			if (interrupted) taskState = mNotificationsQueue.poll();
			if (taskState == null) return;
			if (taskState.cancel) mNotificationManager.cancel(taskState.notificationId); else
			{
				Notification.Builder builder = taskState.builder;
				if (taskState.first)
				{
					builder.setOngoing(true);
					builder.setSmallIcon(android.R.drawable.stat_sys_upload);
					PendingIntent cancelIntent = PendingIntent.getService(this, taskState.notificationId,
							new Intent(this, PostingService.class).setAction(ACTION_CANCEL_POSTING)
							.putExtra(EXTRA_KEY, taskState.key), PendingIntent.FLAG_UPDATE_CURRENT);
					Context themedContext = new ContextThemeWrapper(this, R.style.Theme_Special_Notification);
					ViewUtils.addNotificationAction(builder, this, ResourceUtils.getResourceId(themedContext,
							R.attr.notificationCancel, 0), getString(android.R.string.cancel), cancelIntent);
					if (C.API_LOLLIPOP)
					{
						themedContext = new ContextThemeWrapper(this, Preferences.getThemeResource());
						int notificationColor = ResourceUtils.getColor(themedContext, android.R.attr.colorAccent);
						builder.setColor(notificationColor);
					}
				}
				boolean progressMode = taskState.task.isProgressMode();
				switch (taskState.progressState)
				{
					case CONNECTING:
					{
						if (progressMode) builder.setProgress(1, 0, true);
						builder.setContentTitle(getString(R.string.message_sending));
						break;
					}
					case SENDING:
					{
						if (progressMode)
						{
							builder.setProgress(taskState.progressMax, taskState.progress, taskState.progressMax <= 0);
							builder.setContentTitle(getString(R.string.message_sending_index_format,
									taskState.attachmentIndex + 1, taskState.attachmentsCount));
						}
						else builder.setContentTitle(getString(R.string.message_sending));
						break;
					}
					case PROCESSING:
					{
						if (progressMode) builder.setProgress(1, 1, false);
						builder.setContentTitle(getString(R.string.message_processing_data));
						break;
					}
				}
				builder.setContentText(taskState.text);
				mNotificationManager.notify(taskState.notificationId, builder.build());
			}
		}
	}
	
	@Override
	public IBinder onBind(Intent intent)
	{
		return new PostingBinder();
	}
	
	private void stopSelfAndReleaseWakeLock()
	{
		stopSelf();
		mWakeLock.release();
	}
	
	private static final String makeKey(String chanName, String boardName, String threadNumber)
	{
		return chanName + '/' + boardName + '/' + threadNumber;
	}
	
	public static interface Callback
	{
		public void onSendPostStart(boolean progressMode);
		public void onSendPostChangeProgressState(boolean progressMode, SendPostTask.ProgressState progressState,
				int attachmentIndex, int attachmentsCount);
		public void onSendPostChangeProgressValue(int progress, int progressMax);
		
		public void onSendPostSuccess(Intent intent);
		public void onSendPostFail(ErrorItem errorItem, Object extra, boolean captchaError, boolean keepCaptcha);
		public void onSendPostCancel();
	}
	
	public class PostingBinder extends Binder
	{
		public void executeSendPost(String chanName, ChanPerformer.SendPostData data)
		{
			startService(new Intent(PostingService.this, PostingService.class));
			mWakeLock.acquire();
			String key = makeKey(chanName, data.boardName, data.threadNumber);
			SendPostTask task = new SendPostTask(key, chanName, PostingService.this, data);
			task.executeOnExecutor(SendPostTask.THREAD_POOL_EXECUTOR);
			TaskState taskState = new TaskState(key, task, PostingService.this, chanName, data);
			enqueueUpdateNotification(taskState, true, false);
			mTasks.put(key, taskState);
			ArrayList<Callback> callbacks = mCallbacks.get(key);
			if (callbacks != null)
			{
				for (Callback callback : callbacks) notifyInitDownloading(callback, taskState, false);
			}
		}
		
		public void cancelSendPost(String chanName, String boardName, String threadNumber)
		{
			performCancel(makeKey(chanName, boardName, threadNumber));
		}
		
		public void register(Callback callback, String chanName, String boardName, String threadNumber)
		{
			String key = makeKey(chanName, boardName, threadNumber);
			mCallbackKeys.put(callback, key);
			ArrayList<Callback> callbacks = mCallbacks.get(key);
			if (callbacks == null)
			{
				callbacks = new ArrayList<>(1);
				mCallbacks.put(key, callbacks);
			}
			callbacks.add(callback);
			TaskState taskState = mTasks.get(key);
			if (taskState != null) notifyInitDownloading(callback, taskState, true);
		}
		
		public void unregister(Callback callback)
		{
			String key = mCallbackKeys.remove(callback);
			if (key != null)
			{
				ArrayList<Callback> callbacks = mCallbacks.get(key);
				callbacks.remove(callback);
				if (callbacks.isEmpty()) mCallbacks.remove(key);
			}
		}
	}
	
	private void enqueueUpdateNotification(TaskState taskState, boolean first, boolean cancel)
	{
		taskState.first = first;
		taskState.cancel = cancel;
		mNotificationsQueue.add(taskState);
	}
	
	private void notifyInitDownloading(Callback callback, TaskState taskState, boolean notifyState)
	{
		boolean progressMode = taskState.task.isProgressMode();
		callback.onSendPostStart(progressMode);
		if (notifyState)
		{
			callback.onSendPostChangeProgressState(progressMode, taskState.progressState, taskState.attachmentIndex,
					taskState.attachmentsCount);
			callback.onSendPostChangeProgressValue(taskState.progress, taskState.progressMax);
		}
	}
	
	private void performCancel(String key)
	{
		TaskState taskState = mTasks.remove(key);
		if (taskState != null)
		{
			taskState.task.cancel();
			enqueueUpdateNotification(taskState, false, true);
			if (mTasks.isEmpty()) stopSelfAndReleaseWakeLock();
			ArrayList<Callback> callbacks = mCallbacks.get(key);
			if (callbacks != null)
			{
				for (Callback callback : callbacks) callback.onSendPostCancel();
			}
		}
	}
	
	@Override
	public void onSendPostChangeProgressState(String key, SendPostTask.ProgressState progressState,
			int attachmentIndex, int attachmentsCount)
	{
		TaskState taskState = mTasks.get(key);
		if (taskState != null)
		{
			taskState.progressState = progressState;
			taskState.attachmentIndex = attachmentIndex;
			taskState.attachmentsCount = attachmentsCount;
			enqueueUpdateNotification(taskState, false, false);
			ArrayList<Callback> callbacks = mCallbacks.get(key);
			if (callbacks != null)
			{
				boolean progressMode = taskState.task.isProgressMode();
				for (Callback callback : callbacks)
				{
					callback.onSendPostChangeProgressState(progressMode, progressState, attachmentIndex,
							attachmentsCount);
				}
			}
		}
	}
	
	@Override
	public void onSendPostChangeProgressValue(String key, int progress, int progressMax)
	{
		TaskState taskState = mTasks.get(key);
		if (taskState != null)
		{
			taskState.progress = progress;
			taskState.progressMax = progressMax;
			enqueueUpdateNotification(taskState, false, false);
			ArrayList<Callback> callbacks = mCallbacks.get(key);
			if (callbacks != null)
			{
				for (Callback callback : callbacks) callback.onSendPostChangeProgressValue(progress, progressMax);
			}
		}
	}
	
	private boolean removeTask(String key)
	{
		TaskState taskState = mTasks.remove(key);
		if (taskState != null)
		{
			enqueueUpdateNotification(taskState, false, true);
			if (mTasks.isEmpty()) stopSelfAndReleaseWakeLock();
			return true;
		}
		return false;
	}
	
	@Override
	public void onSendPostSuccess(String key, ChanPerformer.SendPostData data,
			String chanName, String threadNumber, String postNumber)
	{
		if (removeTask(key))
		{
			Intent intent = new Intent(C.ACTION_POST_SENT);
			String targetThreadNumber = data.threadNumber != null ? data.threadNumber
					: StringUtils.nullIfEmpty(threadNumber);
			intent.putExtra(C.EXTRA_TIMESTAMP, System.currentTimeMillis());
			intent.putExtra(C.EXTRA_CHAN_NAME, chanName);
			intent.putExtra(C.EXTRA_BOARD_NAME, data.boardName);
			intent.putExtra(C.EXTRA_THREAD_NUMBER, targetThreadNumber);
			if (!StringUtils.isEmpty(postNumber)) intent.putExtra(C.EXTRA_POST_NUMBER, postNumber);
			String comment = data.comment;
			if (comment != null)
			{
				CommentEditor commentEditor = ChanMarkup.get(chanName).obtainCommentEditor(data.boardName);
				if (commentEditor != null) comment = commentEditor.removeTags(comment);
				intent.putExtra(C.EXTRA_COMMENT, comment);
			}
			if (targetThreadNumber != null && Preferences.isFavoriteOnReply())
			{
				FavoritesStorage.getInstance().add(chanName, data.boardName, targetThreadNumber, null, 0);
			}
			intent.putExtra(C.EXTRA_NEW_THREAD, data.threadNumber == null);
			StatisticsManager.getInstance().incrementPosts(chanName, data.threadNumber == null);
			DraftsStorage draftsStorage = DraftsStorage.getInstance();
			if (targetThreadNumber != null)
			{
				String password = Preferences.getPassword(chanName);
				if (StringUtils.equals(password, data.password)) password = null;
				draftsStorage.store(new DraftsStorage.ThreadDraft(chanName, data.boardName, targetThreadNumber,
						data.name, data.email, password, data.optionSage, data.optionOriginalPoster, data.userIcon));
			}
			draftsStorage.removePostDraft(chanName, data.boardName, data.threadNumber);
			draftsStorage.removeCaptchaDraft();
			ArrayList<Callback> callbacks = mCallbacks.get(key);
			if (callbacks != null)
			{
				for (Callback callback : callbacks) callback.onSendPostSuccess(intent);
			}
			LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
		}
	}
	
	@Override
	public void onSendPostFail(String key, ChanPerformer.SendPostData data, String chanName, ErrorItem errorItem,
			Object extra, boolean captchaError, boolean keepCaptcha)
	{
		if (removeTask(key))
		{
			ArrayList<Callback> callbacks = mCallbacks.get(key);
			boolean hasCallback = callbacks != null && !callbacks.isEmpty();
			if (hasCallback)
			{
				for (Callback callback : callbacks)
				{
					callback.onSendPostFail(errorItem, extra, captchaError, keepCaptcha);
				}
			}
			else
			{
				startActivity(new Intent(this, PostingActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
						.putExtra(C.EXTRA_CHAN_NAME, chanName).putExtra(C.EXTRA_BOARD_NAME, data.boardName)
						.putExtra(C.EXTRA_THREAD_NUMBER, data.threadNumber).putExtra(C.EXTRA_FAIL_RESULT,
						new FailResult(errorItem, extra, captchaError, keepCaptcha)));
			}
		}
	}
	
	public static class FailResult implements Serializable
	{
		private static final long serialVersionUID = 1L;
		
		public final ErrorItem errorItem;
		public final Object extra;
		public final boolean captchaError;
		public final boolean keepCaptcha;
		
		public FailResult(ErrorItem errorItem, Object extra, boolean captchaError, boolean keepCaptcha)
		{
			this.errorItem = errorItem;
			this.extra = extra;
			this.captchaError = captchaError;
			this.keepCaptcha = keepCaptcha;
		}
	}
}