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
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.PowerManager;
import android.support.v4.content.LocalBroadcastManager;
import android.view.ContextThemeWrapper;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.app.AudioPlayerActivity;
import com.mishiranu.dashchan.async.ReadFileTask;
import com.mishiranu.dashchan.content.CacheManager;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ToastUtils;
import com.mishiranu.dashchan.util.ViewUtils;

public class AudioPlayerService extends Service implements AudioManager.OnAudioFocusChangeListener,
		MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, ReadFileTask.Callback 
{
	private static String ACTION_START = "com.mishiranu.dashchan.action.START";
	public static String ACTION_CANCEL = "com.mishiranu.dashchan.action.CANCEL";
	public static String ACTION_TOGGLE = "com.mishiranu.dashchan.action.TOGGLE";

	private static String EXTRA_CHAN_NAME = "com.mishiranu.dashchan.extra.CHAN_NAME";
	private static String EXTRA_FILE_NAME = "com.mishiranu.dashchan.extra.FILE_NAME";
	
	private AudioManager mAudioManager;
	private NotificationManager mNotificationManager;
	private int mNotificationColor;
	private PowerManager.WakeLock mWakeLock;
	
	private Notification.Builder mBuilder;
	private ReadFileTask mReadFileTask;
	private MediaPlayer mMediaPlayer;
	
	private String mChanName;
	private String mFileName;
	private File mAudioFile;
	
	private Context mContext;
	private boolean mPausedByTransientLossOfFocus = false;
	
	private static Intent obtainIntent(Context context, String action)
	{
		return new Intent(context, AudioPlayerService.class).setAction(action);
	}
	
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	@Override
	public void onCreate()
	{
		super.onCreate();
		mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
		mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		int notificationColor = 0;
		if (C.API_LOLLIPOP)
		{
			Context themedContext = new ContextThemeWrapper(this, Preferences.getThemeResource());
			notificationColor = ResourceUtils.getColor(themedContext, android.R.attr.colorAccent);
		}
		mNotificationColor = notificationColor;
		PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
		mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AudioPlayerWakeLock");
		mWakeLock.setReferenceCounted(false);
		mContext = new ContextThemeWrapper(this, R.style.Theme_Special_Notification);
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		if (intent != null)
		{
			String action = intent.getAction();
			if (ACTION_START.equals(action))
			{
				cleanup();
				CacheManager cacheManager = CacheManager.getInstance();
				if (!cacheManager.isCacheAvailable())
				{
					ToastUtils.show(this, R.string.message_cache_unavailable);
					stopSelf();
					sendToActivity(ACTION_CANCEL);
				}
				else
				{
					Uri uri = intent.getData();
					mChanName = intent.getStringExtra(EXTRA_CHAN_NAME);
					mFileName = intent.getStringExtra(EXTRA_FILE_NAME);
					File cachedFile = cacheManager.getMediaFile(uri, true);
					if (cachedFile == null)
					{
						ToastUtils.show(this, R.string.message_cache_unavailable);
						cleanup();
						stopSelf();
						sendToActivity(ACTION_CANCEL);
					}
					else
					{
						mWakeLock.acquire();
						if (cachedFile.exists()) initAndPlayAudio(cachedFile); else
						{
							mReadFileTask = new ReadFileTask(this, mChanName, uri, cachedFile, true, this);
							mReadFileTask.executeOnExecutor(ReadFileTask.THREAD_POOL_EXECUTOR);
						}
					}
				}
			}
			else if (ACTION_CANCEL.equals(action))
			{
				cleanup();
				stopSelf();
				sendToActivity(ACTION_CANCEL);
			}
			else if (ACTION_TOGGLE.equals(action))
			{
				togglePlayback();
			}
		}
		return START_NOT_STICKY;
	}
	
	@Override
	public void onDestroy()
	{
		cleanup();
		super.onDestroy();
	}
	
	private void sendToActivity(String action)
	{
		LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(action));
	}
	
	private void cleanup()
	{
		if (mReadFileTask != null)
		{
			mReadFileTask.cancel();
			mReadFileTask = null;
		}
		mAudioManager.abandonAudioFocus(this);
		if (mMediaPlayer != null)
		{
			mMediaPlayer.stop();
			mMediaPlayer.release();
			mMediaPlayer = null;
		}
		mWakeLock.release();
		stopForeground(true);
		sendToActivity(ACTION_CANCEL);
	}
	
	private void togglePlayback()
	{
		boolean success = false;
		if (mMediaPlayer.isPlaying()) success = pause(true); else success = play(true);
		if (success)
		{
			refreshPlaybackNotification(true);
			sendToActivity(ACTION_TOGGLE);
		}
		else
		{
			ToastUtils.show(mContext, R.string.message_playback_error);
			cleanup();
			stopSelf();
			sendToActivity(ACTION_CANCEL);
		}
	}
	
	public class AudioPlayerBinder extends Binder
	{
		public void togglePlayback()
		{
			if (mMediaPlayer != null) AudioPlayerService.this.togglePlayback();
		}
		
		public void stop()
		{
			cleanup();
			stopSelf();
		}
		
		public boolean isPlaying()
		{
			return mMediaPlayer != null ? mMediaPlayer.isPlaying() : false;
		}
		
		public String getFileName()
		{
			return mFileName;
		}
		
		public int getPosition()
		{
			return mMediaPlayer != null ? mMediaPlayer.getCurrentPosition() : -1;
		}
		
		public int getDuration()
		{
			return mMediaPlayer != null ? mMediaPlayer.getDuration() : -1;
		}
		
		public void seekTo(int msec)
		{
			if (mMediaPlayer != null) mMediaPlayer.seekTo(msec);
		}
	}
	
	@Override
	public AudioPlayerBinder onBind(Intent intent)
	{
		return new AudioPlayerBinder();
	}
	
	@Override
	public void onAudioFocusChange(int focusChange)
	{
		switch (focusChange)
		{
			case AudioManager.AUDIOFOCUS_LOSS:
			{
				pause(true);
				break;
			}
			case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
			{
				boolean playing = mMediaPlayer.isPlaying();
				pause(false);
				if (playing) mPausedByTransientLossOfFocus = true;
				break;
			}
			case AudioManager.AUDIOFOCUS_GAIN:
			{
				if (mPausedByTransientLossOfFocus) play(false);
				break;
			}
		}
	}
	
	@Override
	public void onCompletion(MediaPlayer mp)
	{
		pause(true);
		mMediaPlayer.stop();
		mMediaPlayer.release();
		initAndPlayAudio(mAudioFile);
	}
	
	@Override
	public boolean onError(MediaPlayer mp, int what, int extra)
	{
		ToastUtils.show(mContext, R.string.message_playback_error);
		if (mAudioFile != null) mAudioFile.delete();
		cleanup();
		stopSelf();
		sendToActivity(ACTION_CANCEL);
		return true;
	}
	
	private boolean pause(boolean resetFocus)
	{
		if (resetFocus) mAudioManager.abandonAudioFocus(this);
		mMediaPlayer.pause();
		mWakeLock.acquire(15000);
		return true;
	}
	
	private boolean play(boolean resetFocus)
	{
		if (resetFocus && mAudioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
				!= AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
		{
			return false;
		}
		mMediaPlayer.start();
		mWakeLock.acquire();
		return true;
	}
	
	private void initAndPlayAudio(File file)
	{
		mAudioFile = file;
		mPausedByTransientLossOfFocus = false;
		mMediaPlayer = new MediaPlayer();
		mMediaPlayer.setLooping(false);
		mMediaPlayer.setOnCompletionListener(this);
		mMediaPlayer.setOnErrorListener(this);
		try
		{
			mMediaPlayer.setDataSource(file.getPath());
			mMediaPlayer.prepare();
		}
		catch (Exception e)
		{
			mAudioFile.delete();
			CacheManager.getInstance().handleDownloadedFile(mAudioFile, false);
			ToastUtils.show(mContext, R.string.message_playback_error);
			cleanup();
			stopSelf();
			sendToActivity(ACTION_CANCEL);
			return;
		}
		play(true);
		refreshPlaybackNotification(true);
	}
	
	private int mProgress, mProgressMax;
	private long mLastUpdate;
	
	private static final int[] ICON_ATTRS = {R.attr.notificationRefresh, R.attr.notificationCancel,
		R.attr.notificationPlay, R.attr.notificationPause};
	
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private void refreshPlaybackNotification(boolean recreate)
	{
		Notification.Builder builder = mBuilder;
		if (builder == null || recreate)
		{
			builder = new Notification.Builder(this);
			builder.setSmallIcon(R.drawable.ic_audiotrack_white_24dp);
			PendingIntent contentIntent = PendingIntent.getActivity(mContext, 0,
					new Intent(this, AudioPlayerActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
			builder.setContentIntent(contentIntent);
			TypedArray typedArray = mContext.obtainStyledAttributes(ICON_ATTRS);
			PendingIntent toggleIntent = PendingIntent.getService(mContext, 0,
					obtainIntent(this, ACTION_TOGGLE), PendingIntent.FLAG_UPDATE_CURRENT);
			boolean playing = mMediaPlayer.isPlaying();
			ViewUtils.addNotificationAction(builder, mContext, typedArray, playing ? 3 : 2,
					playing ? R.string.action_pause : R.string.action_play, toggleIntent);
			PendingIntent cancelIntent = PendingIntent.getService(mContext, 0,
					obtainIntent(this, ACTION_CANCEL), PendingIntent.FLAG_UPDATE_CURRENT);
			ViewUtils.addNotificationAction(builder, mContext, typedArray, 1, R.string.action_stop, cancelIntent);
			typedArray.recycle();
			if (C.API_LOLLIPOP) builder.setColor(mNotificationColor);
			mBuilder = builder;
			builder.setContentTitle(getString(R.string.message_file_playback));
			builder.setContentText(getString(R.string.message_download_name_format, mFileName));
		}
		startForeground(C.NOTIFICATION_ID_AUDIO_PLAYER, builder.build());
	}
	
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private void refreshDownloadingNotification(boolean recreate, boolean error, ErrorItem errorItem, Uri uri)
	{
		Notification.Builder builder = mBuilder;
		if (builder == null || recreate)
		{
			builder = new Notification.Builder(this);
			builder.setSmallIcon(error ? android.R.drawable.stat_sys_download_done
					: android.R.drawable.stat_sys_download);
			builder.setDeleteIntent(PendingIntent.getService(mContext, 0, obtainIntent(this, ACTION_CANCEL),
					PendingIntent.FLAG_UPDATE_CURRENT));
			TypedArray typedArray = mContext.obtainStyledAttributes(ICON_ATTRS);
			if (error)
			{
				PendingIntent retryIntent = PendingIntent.getService(mContext, 0, obtainIntent(this, ACTION_START)
						.setData(uri).putExtra(EXTRA_CHAN_NAME, mChanName).putExtra(EXTRA_FILE_NAME, mFileName),
						PendingIntent.FLAG_UPDATE_CURRENT);
				ViewUtils.addNotificationAction(builder, mContext, typedArray, 0,
						R.string.action_retry, retryIntent);
			}
			else
			{
				PendingIntent cancelIntent = PendingIntent.getService(mContext, 0, obtainIntent(this, ACTION_CANCEL),
						PendingIntent.FLAG_UPDATE_CURRENT);
				ViewUtils.addNotificationAction(builder, mContext, typedArray, 1,
						android.R.string.cancel, cancelIntent);
			}
			typedArray.recycle();
			if (C.API_LOLLIPOP) builder.setColor(mNotificationColor);
			mBuilder = builder;
		}
		if (error)
		{
			builder.setContentTitle(getString(R.string.message_download_completed));
			builder.setContentText(getString(R.string.message_download_result_format, 0, 1));
			mNotificationManager.notify(C.NOTIFICATION_ID_AUDIO_PLAYER, builder.build());
		}
		else
		{
			builder.setContentTitle(getString(R.string.message_download_audio));
			builder.setContentText(getString(R.string.message_download_name_format, mFileName));
			builder.setProgress(mProgressMax, mProgress, mProgressMax == 0 ||
					mProgress > mProgressMax || mProgress < 0);
			startForeground(C.NOTIFICATION_ID_AUDIO_PLAYER, builder.build());
		}
	}
	
	@Override
	public void onFileExists(Uri uri, File file)
	{
		mReadFileTask = null;
		initAndPlayAudio(file);
	}
	
	@Override
	public void onStartDownloading(Uri uri, File file)
	{
		mLastUpdate = 0L;
		refreshDownloadingNotification(true, false, null, null);
	}
	
	@Override
	public void onFinishDownloading(boolean success, Uri uri, File file, ErrorItem errorItem)
	{
		mWakeLock.acquire(15000);
		mReadFileTask = null;
		stopForeground(true);
		if (success) initAndPlayAudio(file);
		else refreshDownloadingNotification(true, true, errorItem, uri);
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
			refreshDownloadingNotification(false, false, null, null);
		}
	}
	
	public static void start(Context context, String chanName, Uri uri, String fileName)
	{
		context.startService(obtainIntent(context, ACTION_START).setData(uri).putExtra(EXTRA_CHAN_NAME, chanName)
				.putExtra(EXTRA_FILE_NAME, fileName));
	}
}