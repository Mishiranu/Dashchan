package com.mishiranu.dashchan.content.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.PowerManager;
import android.os.SystemClock;
import androidx.core.app.NotificationCompat;
import chan.content.Chan;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.CacheManager;
import com.mishiranu.dashchan.content.LocaleManager;
import com.mishiranu.dashchan.content.async.ReadFileTask;
import com.mishiranu.dashchan.content.database.ChanDatabase;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.ui.MainActivity;
import com.mishiranu.dashchan.util.AndroidUtils;
import com.mishiranu.dashchan.util.AudioFocus;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.WeakObservable;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.ThemeEngine;
import java.io.File;

public class AudioPlayerService extends BaseService implements MediaPlayer.OnCompletionListener,
		MediaPlayer.OnErrorListener, ReadFileTask.FileCallback {
	private static final String ACTION_START = "start";
	private static final String ACTION_CANCEL = "cancel";
	private static final String ACTION_TOGGLE = "toggle";

	private static final String EXTRA_CHAN_NAME = "chanName";
	private static final String EXTRA_FILE_NAME = "fileName";

	private final WeakObservable<Callback> callbacks = new WeakObservable<>();
	private AudioFocus audioFocus;
	private NotificationManager notificationManager;
	private int notificationColor;
	private PowerManager.WakeLock wakeLock;

	private NotificationCompat.Builder builder;
	private ReadFileTask readFileTask;
	private MediaPlayer mediaPlayer;

	private String chanName;
	private String fileName;
	private File audioFile;

	private boolean pausedByTransientLossOfFocus = false;

	private static Intent obtainIntent(Context context, String action) {
		return new Intent(context, AudioPlayerService.class).setAction(action);
	}

	@Override
	protected void attachBaseContext(Context newBase) {
		super.attachBaseContext(LocaleManager.getInstance().apply(newBase));
	}

	@Override
	public void onCreate() {
		super.onCreate();

		audioFocus = new AudioFocus(this, change -> {
			switch (change) {
				case LOSS: {
					pause(true);
					break;
				}
				case LOSS_TRANSIENT: {
					boolean playing = mediaPlayer.isPlaying();
					pause(false);
					if (playing) {
						pausedByTransientLossOfFocus = true;
					}
					break;
				}
				case GAIN: {
					if (pausedByTransientLossOfFocus) {
						play(false);
					}
					break;
				}
			}
		});
		notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		int notificationColor = 0;
		if (C.API_LOLLIPOP) {
			ThemeEngine.Theme theme = ThemeEngine.attachAndApply(this);
			notificationColor = theme.accent;
		}
		this.notificationColor = notificationColor;
		if (C.API_OREO) {
			notificationManager.createNotificationChannel
					(new NotificationChannel(C.NOTIFICATION_CHANNEL_AUDIO_PLAYER,
							getString(R.string.audio_player), NotificationManager.IMPORTANCE_LOW));
		}
		PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
		wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getPackageName() + ":AudioPlayerWakeLock");
		wakeLock.setReferenceCounted(false);
		addOnDestroyListener(ChanDatabase.getInstance().requireCookies());
	}

	private void notifyToggle() {
		for (Callback callback : callbacks) {
			callback.onTogglePlayback();
		}
	}

	private void notifyCancel() {
		for (Callback callback : callbacks) {
			callback.onCancel();
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null) {
			String action = intent.getAction();
			if (ACTION_START.equals(action)) {
				cleanup(false, false);
				CacheManager cacheManager = CacheManager.getInstance();
				if (!cacheManager.isCacheAvailable()) {
					ClickableToast.show(R.string.cache_is_unavailable);
					cleanup(true, true);
				} else {
					Uri uri = intent.getData();
					chanName = intent.getStringExtra(EXTRA_CHAN_NAME);
					fileName = intent.getStringExtra(EXTRA_FILE_NAME);
					File cachedFile = cacheManager.getMediaFile(uri, true);
					if (cachedFile == null) {
						ClickableToast.show(R.string.cache_is_unavailable);
						cleanup(true, true);
					} else {
						wakeLock.acquire();
						if (cachedFile.exists()) {
							initAndPlayAudio(cachedFile);
						} else {
							Chan chan = Chan.getPreferred(chanName, uri);
							readFileTask = ReadFileTask.createCachedMediaFile(this, this, chan, uri, cachedFile);
							readFileTask.execute(ConcurrentUtils.PARALLEL_EXECUTOR);
						}
					}
				}
			} else if (ACTION_CANCEL.equals(action)) {
				cleanup(true, true);
			} else if (ACTION_TOGGLE.equals(action)) {
				togglePlayback();
			}
		}
		return START_NOT_STICKY;
	}

	@Override
	public void onDestroy() {
		cleanup(false, true);
		super.onDestroy();
	}

	private void startForeground(NotificationCompat.Builder builder) {
		startForeground(C.NOTIFICATION_ID_AUDIO_PLAYER, builder.build());
	}

	private void cleanup(boolean stopSelf, boolean notify) {
		if (readFileTask != null) {
			readFileTask.cancel();
			readFileTask = null;
		}
		audioFocus.release();
		if (mediaPlayer != null) {
			mediaPlayer.stop();
			mediaPlayer.release();
			mediaPlayer = null;
		}
		wakeLock.release();
		if (stopSelf) {
			if (C.API_OREO) {
				// Ensure service was started foreground at least once
				startForeground(getPlaybackNotification(false));
			}
			stopForeground(true);
			stopSelf();
		}
		if (notify) {
			notifyCancel();
		}
	}

	private void togglePlayback() {
		boolean success;
		if (mediaPlayer.isPlaying()) {
			success = pause(true);
		} else {
			success = play(true);
		}
		startForeground(getPlaybackNotification(true));
		if (success) {
			notifyToggle();
		} else {
			ClickableToast.show(R.string.playback_error);
			cleanup(true, true);
		}
	}

	public interface Callback {
		void onTogglePlayback();
		void onCancel();
	}

	public class Binder extends android.os.Binder {
		public void registerCallback(Callback callback) {
			callbacks.register(callback);
		}

		public void unregisterCallback(Callback callback) {
			callbacks.unregister(callback);
		}

		public void togglePlayback() {
			if (mediaPlayer != null) {
				AudioPlayerService.this.togglePlayback();
			}
		}

		public void stop() {
			cleanup(true, true);
		}

		public boolean isRunning() {
			return mediaPlayer != null;
		}

		public boolean isPlaying() {
			return mediaPlayer != null && mediaPlayer.isPlaying();
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
	public void onCompletion(MediaPlayer mp) {
		pause(true);
		mediaPlayer.stop();
		mediaPlayer.release();
		initAndPlayAudio(audioFile);
	}

	@Override
	public boolean onError(MediaPlayer mp, int what, int extra) {
		ClickableToast.show(R.string.playback_error);
		if (audioFile != null) {
			audioFile.delete();
		}
		cleanup(true, true);
		return true;
	}

	private boolean pause(boolean resetFocus) {
		if (resetFocus) {
			audioFocus.release();
		}
		mediaPlayer.pause();
		wakeLock.acquire(15000);
		return true;
	}

	private boolean play(boolean resetFocus) {
		if (resetFocus && !audioFocus.acquire()) {
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
			ClickableToast.show(R.string.playback_error);
			cleanup(true, true);
			return;
		}
		play(true);
		startForeground(getPlaybackNotification(true));
	}

	private int progress, progressMax;
	private long lastUpdate;

	private NotificationCompat.Builder getPlaybackNotification(boolean recreate) {
		NotificationCompat.Builder builder = this.builder;
		if (builder == null || recreate) {
			builder = new NotificationCompat.Builder(this, C.NOTIFICATION_CHANNEL_AUDIO_PLAYER);
			builder.setSmallIcon(R.drawable.ic_audiotrack_white_24dp);
			builder.setColor(notificationColor);
			PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
					new Intent(this, MainActivity.class).setAction(C.ACTION_PLAYER),
					PendingIntent.FLAG_UPDATE_CURRENT);
			builder.setContentIntent(contentIntent);
			PendingIntent toggleIntent = AndroidUtils.getAnyServicePendingIntent(this, 0,
					obtainIntent(this, ACTION_TOGGLE), PendingIntent.FLAG_UPDATE_CURRENT);
			boolean playing = mediaPlayer != null && mediaPlayer.isPlaying();
			builder.addAction(C.API_LOLLIPOP ? 0 : playing
							? R.drawable.ic_action_pause_dark : R.drawable.ic_action_play_dark,
					getString(playing ? R.string.pause : R.string.play), toggleIntent);
			PendingIntent cancelIntent = AndroidUtils.getAnyServicePendingIntent(this, 0,
					obtainIntent(this, ACTION_CANCEL), PendingIntent.FLAG_UPDATE_CURRENT);
			builder.addAction(C.API_LOLLIPOP ? 0 : R.drawable.ic_action_cancel_dark,
					getString(R.string.stop), cancelIntent);
			this.builder = builder;
			builder.setContentTitle(getString(R.string.audio_playback));
			builder.setContentText(getString(R.string.file_name__format, fileName));
		}
		return builder;
	}

	private NotificationCompat.Builder getDownloadingNotification(boolean recreate, boolean error, Uri uri) {
		NotificationCompat.Builder builder = this.builder;
		if (builder == null || recreate) {
			builder = new NotificationCompat.Builder(this, C.NOTIFICATION_CHANNEL_AUDIO_PLAYER);
			builder.setSmallIcon(error ? android.R.drawable.stat_sys_download_done
					: android.R.drawable.stat_sys_download);
			builder.setDeleteIntent(AndroidUtils.getAnyServicePendingIntent(this, 0,
					obtainIntent(this, ACTION_CANCEL), PendingIntent.FLAG_UPDATE_CURRENT));
			if (error) {
				PendingIntent retryIntent = AndroidUtils.getAnyServicePendingIntent(this, 0,
						obtainIntent(this, ACTION_START).setData(uri).putExtra(EXTRA_CHAN_NAME, chanName)
								.putExtra(EXTRA_FILE_NAME, fileName), PendingIntent.FLAG_UPDATE_CURRENT);
				builder.addAction(C.API_LOLLIPOP ? 0 : R.drawable.ic_action_refresh_dark,
						getString(R.string.retry), retryIntent);
			} else {
				PendingIntent cancelIntent = AndroidUtils.getAnyServicePendingIntent(this, 0,
						obtainIntent(this, ACTION_CANCEL), PendingIntent.FLAG_UPDATE_CURRENT);
				builder.addAction(C.API_LOLLIPOP ? 0 : R.drawable.ic_action_cancel_dark,
						getString(android.R.string.cancel), cancelIntent);
			}
			if (C.API_LOLLIPOP) {
				builder.setColor(notificationColor);
			}
			this.builder = builder;
		}
		if (error) {
			builder.setContentTitle(getString(R.string.download_completed));
			builder.setContentText(getString(R.string.success_number_not_loaded_number__format, 0, 1));
		} else {
			builder.setContentTitle(getString(R.string.downloading_audio));
			builder.setContentText(getString(R.string.file_name__format, fileName));
			builder.setProgress(progressMax, progress, progressMax == 0 ||
					progress > progressMax || progress < 0);
		}
		return builder;
	}

	@Override
	public void onStartDownloading() {
		lastUpdate = 0L;
		startForeground(getDownloadingNotification(true, false, null));
	}

	@Override
	public void onFinishDownloading(boolean success, Uri uri, File file, ErrorItem errorItem) {
		wakeLock.acquire(15000);
		readFileTask = null;
		if (success) {
			initAndPlayAudio(file);
		} else {
			cleanup(true, true);
			notificationManager.notify(C.NOTIFICATION_ID_AUDIO_PLAYER,
					getDownloadingNotification(true, true, uri).build());
		}
	}

	@Override
	public void onUpdateProgress(long progress, long progressMax) {
		this.progress = (int) progress;
		this.progressMax = (int) progressMax;
		long t = SystemClock.elapsedRealtime();
		if (t - lastUpdate >= 1000L) {
			lastUpdate = t;
			startForeground(getDownloadingNotification(false, false, null));
		}
	}

	public static void start(Context context, String chanName, Uri uri, String fileName) {
		AndroidUtils.startAnyService(context, obtainIntent(context, ACTION_START).setData(uri)
				.putExtra(EXTRA_CHAN_NAME, chanName).putExtra(EXTRA_FILE_NAME, fileName));
	}
}
