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

package com.mishiranu.dashchan.content.service;

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
import android.os.Build;
import android.os.PowerManager;
import android.support.v4.content.LocalBroadcastManager;
import android.view.ContextThemeWrapper;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.CacheManager;
import com.mishiranu.dashchan.content.async.ReadFileTask;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ToastUtils;
import com.mishiranu.dashchan.util.ViewUtils;

public class AudioPlayerService extends Service implements AudioManager.OnAudioFocusChangeListener,
		MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, ReadFileTask.Callback {
	private static final String ACTION_START = "com.mishiranu.dashchan.action.START";
	public static final String ACTION_CANCEL = "com.mishiranu.dashchan.action.CANCEL";
	public static final String ACTION_TOGGLE = "com.mishiranu.dashchan.action.TOGGLE";

	private static final String EXTRA_CHAN_NAME = "com.mishiranu.dashchan.extra.CHAN_NAME";
	private static final String EXTRA_FILE_NAME = "com.mishiranu.dashchan.extra.FILE_NAME";

	private AudioManager audioManager;
	private NotificationManager notificationManager;
	private int notificationColor;
	private PowerManager.WakeLock wakeLock;

	private Notification.Builder builder;
	private ReadFileTask readFileTask;
	private MediaPlayer mediaPlayer;

	private String chanName;
	private String fileName;
	private File audioFile;

	private Context context;
	private boolean pausedByTransientLossOfFocus = false;

	private static Intent obtainIntent(Context context, String action) {
		return new Intent(context, AudioPlayerService.class).setAction(action);
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	@Override
	public void onCreate() {
		super.onCreate();
		audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
		notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		int notificationColor = 0;
		if (C.API_LOLLIPOP) {
			Context themedContext = new ContextThemeWrapper(this, Preferences.getThemeResource());
			notificationColor = ResourceUtils.getColor(themedContext, android.R.attr.colorAccent);
		}
		this.notificationColor = notificationColor;
		PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
		wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AudioPlayerWakeLock");
		wakeLock.setReferenceCounted(false);
		context = new ContextThemeWrapper(this, R.style.Theme_Special_Notification);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null) {
			String action = intent.getAction();
			if (ACTION_START.equals(action)) {
				cleanup();
				CacheManager cacheManager = CacheManager.getInstance();
				if (!cacheManager.isCacheAvailable()) {
					ToastUtils.show(this, R.string.message_cache_unavailable);
					stopSelf();
					sendToActivity(ACTION_CANCEL);
				} else {
					Uri uri = intent.getData();
					chanName = intent.getStringExtra(EXTRA_CHAN_NAME);
					fileName = intent.getStringExtra(EXTRA_FILE_NAME);
					File cachedFile = cacheManager.getMediaFile(uri, true);
					if (cachedFile == null) {
						ToastUtils.show(this, R.string.message_cache_unavailable);
						cleanup();
						stopSelf();
						sendToActivity(ACTION_CANCEL);
					} else {
						wakeLock.acquire();
						if (cachedFile.exists()) {
							initAndPlayAudio(cachedFile);
						} else {
							readFileTask = new ReadFileTask(this, chanName, uri, cachedFile, true, this);
							readFileTask.executeOnExecutor(ReadFileTask.THREAD_POOL_EXECUTOR);
						}
					}
				}
			} else if (ACTION_CANCEL.equals(action)) {
				cleanup();
				stopSelf();
				sendToActivity(ACTION_CANCEL);
			} else if (ACTION_TOGGLE.equals(action)) {
				togglePlayback();
			}
		}
		return START_NOT_STICKY;
	}

	@Override
	public void onDestroy() {
		cleanup();
		super.onDestroy();
	}

	private void sendToActivity(String action) {
		LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(action));
	}

	private void cleanup() {
		if (readFileTask != null) {
			readFileTask.cancel();
			readFileTask = null;
		}
		audioManager.abandonAudioFocus(this);
		if (mediaPlayer != null) {
			mediaPlayer.stop();
			mediaPlayer.release();
			mediaPlayer = null;
		}
		wakeLock.release();
		stopForeground(true);
		sendToActivity(ACTION_CANCEL);
	}

	private void togglePlayback() {
		boolean success;
		if (mediaPlayer.isPlaying()) {
			success = pause(true);
		} else {
			success = play(true);
		}
		if (success) {
			refreshPlaybackNotification(true);
			sendToActivity(ACTION_TOGGLE);
		} else {
			ToastUtils.show(context, R.string.message_playback_error);
			cleanup();
			stopSelf();
			sendToActivity(ACTION_CANCEL);
		}
	}

	public class Binder extends android.os.Binder {
		public void togglePlayback() {
			if (mediaPlayer != null) {
				AudioPlayerService.this.togglePlayback();
			}
		}

		public void stop() {
			cleanup();
			stopSelf();
		}

		public boolean isPlaying() {
			return mediaPlayer != null ? mediaPlayer.isPlaying() : false;
		}

		public String getFileName() {
			return fileName;
		}

		public int getPosition() {
			return mediaPlayer != null ? mediaPlayer.getCurrentPosition() : -1;
		}

		public int getDuration() {
			return mediaPlayer != null ? mediaPlayer.getDuration() : -1;
		}

		public void seekTo(int msec) {
			if (mediaPlayer != null) {
				mediaPlayer.seekTo(msec);
			}
		}
	}

	@Override
	public Binder onBind(Intent intent) {
		return new Binder();
	}

	@Override
	public void onAudioFocusChange(int focusChange) {
		switch (focusChange) {
			case AudioManager.AUDIOFOCUS_LOSS: {
				pause(true);
				break;
			}
			case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT: {
				boolean playing = mediaPlayer.isPlaying();
				pause(false);
				if (playing) {
					pausedByTransientLossOfFocus = true;
				}
				break;
			}
			case AudioManager.AUDIOFOCUS_GAIN: {
				if (pausedByTransientLossOfFocus) {
					play(false);
				}
				break;
			}
		}
	}

	@Override
	public void onCompletion(MediaPlayer mp) {
		pause(true);
		mediaPlayer.stop();
		mediaPlayer.release();
		initAndPlayAudio(audioFile);
	}

	@Override
	public boolean onError(MediaPlayer mp, int what, int extra) {
		ToastUtils.show(context, R.string.message_playback_error);
		if (audioFile != null) {
			audioFile.delete();
		}
		cleanup();
		stopSelf();
		sendToActivity(ACTION_CANCEL);
		return true;
	}

	private boolean pause(boolean resetFocus) {
		if (resetFocus) {
			audioManager.abandonAudioFocus(this);
		}
		mediaPlayer.pause();
		wakeLock.acquire(15000);
		return true;
	}

	private boolean play(boolean resetFocus) {
		if (resetFocus && audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
				!= AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
			return false;
		}
		mediaPlayer.start();
		wakeLock.acquire();
		return true;
	}

	private void initAndPlayAudio(File file) {
		audioFile = file;
		pausedByTransientLossOfFocus = false;
		mediaPlayer = new MediaPlayer();
		mediaPlayer.setLooping(false);
		mediaPlayer.setOnCompletionListener(this);
		mediaPlayer.setOnErrorListener(this);
		try {
			mediaPlayer.setDataSource(file.getPath());
			mediaPlayer.prepare();
		} catch (Exception e) {
			audioFile.delete();
			CacheManager.getInstance().handleDownloadedFile(audioFile, false);
			ToastUtils.show(context, R.string.message_playback_error);
			cleanup();
			stopSelf();
			sendToActivity(ACTION_CANCEL);
			return;
		}
		play(true);
		refreshPlaybackNotification(true);
	}

	private int progress, progressMax;
	private long lastUpdate;

	private static final int[] ICON_ATTRS = {R.attr.notificationRefresh, R.attr.notificationCancel,
		R.attr.notificationPlay, R.attr.notificationPause};

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private void refreshPlaybackNotification(boolean recreate) {
		Notification.Builder builder = this.builder;
		if (builder == null || recreate) {
			builder = new Notification.Builder(this);
			builder.setSmallIcon(R.drawable.ic_audiotrack_white_24dp);
			PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
					new Intent(this, AudioPlayerActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
			builder.setContentIntent(contentIntent);
			TypedArray typedArray = context.obtainStyledAttributes(ICON_ATTRS);
			PendingIntent toggleIntent = PendingIntent.getService(context, 0,
					obtainIntent(this, ACTION_TOGGLE), PendingIntent.FLAG_UPDATE_CURRENT);
			boolean playing = mediaPlayer.isPlaying();
			ViewUtils.addNotificationAction(builder, context, typedArray, playing ? 3 : 2,
					playing ? R.string.action_pause : R.string.action_play, toggleIntent);
			PendingIntent cancelIntent = PendingIntent.getService(context, 0,
					obtainIntent(this, ACTION_CANCEL), PendingIntent.FLAG_UPDATE_CURRENT);
			ViewUtils.addNotificationAction(builder, context, typedArray, 1, R.string.action_stop, cancelIntent);
			typedArray.recycle();
			if (C.API_LOLLIPOP) {
				builder.setColor(notificationColor);
			}
			this.builder = builder;
			builder.setContentTitle(getString(R.string.message_file_playback));
			builder.setContentText(getString(R.string.message_download_name_format, fileName));
		}
		startForeground(C.NOTIFICATION_ID_AUDIO_PLAYER, builder.build());
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private void refreshDownloadingNotification(boolean recreate, boolean error, Uri uri) {
		Notification.Builder builder = this.builder;
		if (builder == null || recreate) {
			builder = new Notification.Builder(this);
			builder.setSmallIcon(error ? android.R.drawable.stat_sys_download_done
					: android.R.drawable.stat_sys_download);
			builder.setDeleteIntent(PendingIntent.getService(context, 0, obtainIntent(this, ACTION_CANCEL),
					PendingIntent.FLAG_UPDATE_CURRENT));
			TypedArray typedArray = context.obtainStyledAttributes(ICON_ATTRS);
			if (error) {
				PendingIntent retryIntent = PendingIntent.getService(context, 0, obtainIntent(this, ACTION_START)
						.setData(uri).putExtra(EXTRA_CHAN_NAME, chanName).putExtra(EXTRA_FILE_NAME, fileName),
						PendingIntent.FLAG_UPDATE_CURRENT);
				ViewUtils.addNotificationAction(builder, context, typedArray, 0,
						R.string.action_retry, retryIntent);
			} else {
				PendingIntent cancelIntent = PendingIntent.getService(context, 0, obtainIntent(this, ACTION_CANCEL),
						PendingIntent.FLAG_UPDATE_CURRENT);
				ViewUtils.addNotificationAction(builder, context, typedArray, 1,
						android.R.string.cancel, cancelIntent);
			}
			typedArray.recycle();
			if (C.API_LOLLIPOP) {
				builder.setColor(notificationColor);
			}
			this.builder = builder;
		}
		if (error) {
			builder.setContentTitle(getString(R.string.message_download_completed));
			builder.setContentText(getString(R.string.message_download_result_format, 0, 1));
			notificationManager.notify(C.NOTIFICATION_ID_AUDIO_PLAYER, builder.build());
		} else {
			builder.setContentTitle(getString(R.string.message_download_audio));
			builder.setContentText(getString(R.string.message_download_name_format, fileName));
			builder.setProgress(progressMax, progress, progressMax == 0 ||
					progress > progressMax || progress < 0);
			startForeground(C.NOTIFICATION_ID_AUDIO_PLAYER, builder.build());
		}
	}

	@Override
	public void onFileExists(Uri uri, File file) {
		readFileTask = null;
		initAndPlayAudio(file);
	}

	@Override
	public void onStartDownloading(Uri uri, File file) {
		lastUpdate = 0L;
		refreshDownloadingNotification(true, false, null);
	}

	@Override
	public void onFinishDownloading(boolean success, Uri uri, File file, ErrorItem errorItem) {
		wakeLock.acquire(15000);
		readFileTask = null;
		stopForeground(true);
		if (success) {
			initAndPlayAudio(file);
		} else {
			refreshDownloadingNotification(true, true, uri);
		}
	}

	@Override
	public void onUpdateProgress(long progress, long progressMax) {
		this.progress = (int) progress;
		this.progressMax = (int) progressMax;
		long t = System.currentTimeMillis();
		if (t - lastUpdate >= 1000L) {
			lastUpdate = t;
			refreshDownloadingNotification(false, false, null);
		}
	}

	public static void start(Context context, String chanName, Uri uri, String fileName) {
		context.startService(obtainIntent(context, ACTION_START).setData(uri).putExtra(EXTRA_CHAN_NAME, chanName)
				.putExtra(EXTRA_FILE_NAME, fileName));
	}
}