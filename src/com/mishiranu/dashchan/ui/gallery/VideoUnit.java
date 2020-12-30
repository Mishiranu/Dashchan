package com.mishiranu.dashchan.ui.gallery;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import chan.content.Chan;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.AdvancedPreferences;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.async.ReadVideoTask;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.graphics.BaseDrawable;
import com.mishiranu.dashchan.media.VideoPlayer;
import com.mishiranu.dashchan.ui.InstanceDialog;
import com.mishiranu.dashchan.util.AnimationUtils;
import com.mishiranu.dashchan.util.AudioFocus;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.SummaryLayout;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;

public class VideoUnit {
	private final PagerInstance instance;
	private final LinearLayout controlsView;
	private final AudioFocus audioFocus;

	private int layoutConfiguration = -1;
	private LinearLayout configurationView;
	private TextView timeTextView;
	private TextView totalTimeTextView;
	private SeekBar seekBar;
	private ImageButton playPauseButton;

	private VideoPlayer player;
	private BackgroundDrawable backgroundDrawable;
	private boolean initialized;
	private boolean wasPlaying;
	private boolean pausedByTransientLossOfFocus;
	private boolean finishedPlayback;
	private boolean trackingNow;
	private boolean hideSurfaceOnInit;

	private ReadVideoCallback readVideoCallback;

	public VideoUnit(PagerInstance instance) {
		this.instance = instance;
		controlsView = new LinearLayout(instance.galleryInstance.context);
		controlsView.setOrientation(LinearLayout.VERTICAL);
		controlsView.setVisibility(View.GONE);
		audioFocus = new AudioFocus(instance.galleryInstance.context, change -> {
			switch (change) {
				case LOSS: {
					setPlaying(false, false);
					updatePlayState();
					break;
				}
				case LOSS_TRANSIENT: {
					boolean playing = player.isPlaying();
					setPlaying(false, false);
					if (playing) {
						pausedByTransientLossOfFocus = true;
					}
					updatePlayState();
					break;
				}
				case GAIN: {
					if (pausedByTransientLossOfFocus) {
						setPlaying(true, false);
					}
					updatePlayState();
					break;
				}
			}
		});
	}

	public void addViews(FrameLayout frameLayout) {
		frameLayout.addView(controlsView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
	}

	public void onResume() {
		if (player != null && initialized) {
			setPlaying(wasPlaying, true);
			updatePlayState();
		} else {
			wasPlaying = true;
		}
	}

	public void onPause() {
		if (player != null && initialized) {
			wasPlaying = player.isPlaying();
			setPlaying(false, true);
		} else {
			wasPlaying = false;
		}
	}

	public void onConfigurationChanged(Configuration newConfig) {
		if (newConfig.orientation != Configuration.ORIENTATION_UNDEFINED) {
			if (layoutConfiguration != -1) {
				recreateVideoControls();
			}
		}
	}

	public void onApplyWindowInsets(int left, int right, int bottom) {
		if (C.API_LOLLIPOP) {
			controlsView.setPadding(left, 0, right, bottom);
		}
	}

	public boolean isInitialized() {
		return initialized;
	}

	public boolean isCreated() {
		return player != null;
	}

	public void interrupt() {
		if (readVideoCallback != null) {
			readVideoCallback.cancel();
			readVideoCallback = null;
		}
		if (initialized) {
			audioFocus.release();
			initialized = false;
		}
		invalidateControlsVisibility();
		if (player != null) {
			player.destroy();
			player = null;
			instance.currentHolder.progressBar.setVisible(false, false);
		}
		if (backgroundDrawable != null) {
			backgroundDrawable.recycle();
			backgroundDrawable = null;
		}
		interruptHolder(instance.leftHolder);
		interruptHolder(instance.currentHolder);
		interruptHolder(instance.rightHolder);
	}

	private void interruptHolder(PagerInstance.ViewHolder holder) {
		if (holder != null) {
			holder.surfaceParent.removeAllViews();
		}
	}

	public void forcePause() {
		if (initialized) {
			wasPlaying = false;
			setPlaying(false, true);
		}
	}

	public void applyVideo(Uri uri, File file, boolean reload) {
		wasPlaying = true;
		finishedPlayback = false;
		hideSurfaceOnInit = false;
		boolean seekAnyFrame = Preferences.isVideoSeekAnyFrame();
		VideoPlayer player = new VideoPlayer(playerListener, seekAnyFrame);
		boolean loadedFromFile = false;
		if (!reload && file.exists()) {
			try {
				player.init(file, null);
				loadedFromFile = true;
			} catch (IOException e) {
				// Player was consumed, create a new one and try to download a new video file
				player = new VideoPlayer(playerListener, seekAnyFrame);
			}
		}
		this.player = player;
		if (loadedFromFile) {
			initializePlayer();
			seekBar.setSecondaryProgress(seekBar.getMax());
			if (instance.currentHolder.mediaSummary.updateSize(file.length())) {
				instance.galleryInstance.callback.updateTitle();
			}
			instance.currentHolder.loadState = PagerInstance.LoadState.COMPLETE;
			instance.galleryInstance.callback.invalidateOptionsMenu();
		} else {
			instance.currentHolder.progressBar.setIndeterminate(true);
			instance.currentHolder.progressBar.setVisible(true, false);
			readVideoCallback = new ReadVideoCallback(player, instance.currentHolder,
					instance.galleryInstance.chanName, uri);
		}
	}

	private boolean setPlaying(boolean playing, boolean resetFocus) {
		if (player.isPlaying() != playing) {
			if (resetFocus && player.isAudioPresent()) {
				if (playing) {
					if (!audioFocus.acquire()) {
						return false;
					}
				} else {
					audioFocus.release();
				}
			}
			player.setPlaying(playing);
			pausedByTransientLossOfFocus = false;
		}
		return true;
	}

	private void initializePlayer() {
		PagerInstance.ViewHolder holder = instance.currentHolder;
		holder.progressBar.setVisible(false, false);
		Point dimensions = player.getDimensions();
		if (holder.mediaSummary.updateDimensions(dimensions.x, dimensions.y)) {
			instance.galleryInstance.callback.updateTitle();
		}
		backgroundDrawable = new BackgroundDrawable();
		backgroundDrawable.width = dimensions.x;
		backgroundDrawable.height = dimensions.y;
		holder.recyclePhotoView();
		holder.photoView.setImage(backgroundDrawable, false, true, false);
		View videoView = player.getVideoView(instance.galleryInstance.context);
		holder.surfaceParent.addView(videoView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));
		recreateVideoControls();
		playPauseButton.setEnabled(true);
		seekBar.setEnabled(true);
		initialized = true;
		pausedByTransientLossOfFocus = false;
		if (hideSurfaceOnInit) {
			showHideVideoView(false);
		}
		invalidateControlsVisibility();
		setPlaying(wasPlaying, true);
		updatePlayState();
	}

	private void recreateVideoControls() {
		Context context = instance.galleryInstance.context;
		float density = ResourceUtils.obtainDensity(context);
		int targetLayoutCounfiguration = ResourceUtils.isTabletOrLandscape(context.getResources()
				.getConfiguration()) ? 1 : 0;
		if (targetLayoutCounfiguration != layoutConfiguration) {
			boolean firstTimeLayout = layoutConfiguration < 0;
			layoutConfiguration = targetLayoutCounfiguration;
			boolean longLayout = targetLayoutCounfiguration == 1;

			controlsView.removeAllViews();
			if (seekBar != null) {
				seekBar.removeCallbacks(progressRunnable);
			}
			trackingNow = false;

			configurationView = new LinearLayout(context);
			configurationView.setOrientation(LinearLayout.HORIZONTAL);
			configurationView.setGravity(Gravity.END);
			configurationView.setPadding((int) (8f * density), 0, (int) (8f * density), 0);
			controlsView.addView(configurationView, LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.WRAP_CONTENT);

			LinearLayout controls = new LinearLayout(context);
			controls.setOrientation(longLayout ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
			controls.setBackgroundColor(instance.galleryInstance.actionBarColor);
			controls.setPadding((int) (8f * density), longLayout ? 0 : (int) (8f * density), (int) (8f * density), 0);
			controls.setClickable(true);
			controlsView.addView(controls, LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.WRAP_CONTENT);

			CharSequence oldTimeText = timeTextView != null ? timeTextView.getText() : null;
			timeTextView = new TextView(context, null, android.R.attr.textAppearanceListItem);
			ViewUtils.setTextSizeScaled(timeTextView, 14);
			timeTextView.setGravity(Gravity.CENTER_HORIZONTAL);
			if (C.API_LOLLIPOP) {
				timeTextView.setTypeface(ResourceUtils.TYPEFACE_MEDIUM);
			}
			if (oldTimeText != null) {
				timeTextView.setText(oldTimeText);
			}

			totalTimeTextView = new TextView(context, null, android.R.attr.textAppearanceListItem);
			ViewUtils.setTextSizeScaled(totalTimeTextView, 14);
			totalTimeTextView.setGravity(Gravity.CENTER_HORIZONTAL);
			if (C.API_LOLLIPOP) {
				totalTimeTextView.setTypeface(ResourceUtils.TYPEFACE_MEDIUM);
			}

			int oldSecondaryProgress = seekBar != null ? seekBar.getSecondaryProgress() : -1;
			seekBar = new SeekBar(context);
			seekBar.setOnSeekBarChangeListener(seekBarListener);
			if (oldSecondaryProgress >= 0) {
				seekBar.setSecondaryProgress(oldSecondaryProgress);
			}

			playPauseButton = new ImageButton(context, null, android.R.attr.borderlessButtonStyle);
			playPauseButton.setScaleType(ImageButton.ScaleType.CENTER);
			playPauseButton.setOnClickListener(playPauseClickListener);

			if (longLayout) {
				controls.setGravity(Gravity.CENTER_VERTICAL);
				controls.addView(timeTextView, (int) (48f * density), LinearLayout.LayoutParams.WRAP_CONTENT);
				controls.addView(seekBar, new LinearLayout.LayoutParams(0,
						LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
				controls.addView(playPauseButton, (int) (80f * density), LinearLayout.LayoutParams.WRAP_CONTENT);
				controls.addView(totalTimeTextView, (int) (48f * density),
						LinearLayout.LayoutParams.WRAP_CONTENT);
			} else {
				LinearLayout controls1 = new LinearLayout(context);
				controls1.setOrientation(LinearLayout.HORIZONTAL);
				controls1.setGravity(Gravity.CENTER_VERTICAL);
				controls1.setPadding(0, (int) (8f * density), 0, (int) (8f * density));
				LinearLayout controls2 = new LinearLayout(context);
				controls2.setOrientation(LinearLayout.HORIZONTAL);
				controls2.setGravity(Gravity.CENTER_VERTICAL);
				controls.addView(controls1, LinearLayout.LayoutParams.MATCH_PARENT,
						LinearLayout.LayoutParams.WRAP_CONTENT);
				controls.addView(controls2, LinearLayout.LayoutParams.MATCH_PARENT,
						LinearLayout.LayoutParams.WRAP_CONTENT);
				controls1.addView(seekBar, LinearLayout.LayoutParams.MATCH_PARENT,
						LinearLayout.LayoutParams.WRAP_CONTENT);
				controls2.addView(timeTextView, new LinearLayout.LayoutParams(0,
						LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
				controls2.addView(playPauseButton, (int) (80f * density), LinearLayout.LayoutParams.WRAP_CONTENT);
				controls2.addView(totalTimeTextView, new LinearLayout.LayoutParams(0,
						LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
			}
			if (firstTimeLayout) {
				AnimationUtils.measureDynamicHeight(controlsView);
				controlsView.setTranslationY(controlsView.getMeasuredHeight());
				controlsView.setAlpha(0f);
			}
		}
		if (player != null) {
			configurationView.removeAllViews();
			if (!player.isAudioPresent()) {
				ImageView imageView = new ImageView(context);
				imageView.setImageDrawable(ResourceUtils.getDrawable(context, R.attr.iconActionVolumeOff, 0));
				imageView.setScaleType(ImageView.ScaleType.CENTER);
				if (C.API_LOLLIPOP) {
					imageView.setImageAlpha(0x99);
				}
				configurationView.addView(imageView, (int) (48f * density), (int) (48f * density));
			}
			long duration = player.getDuration();
			totalTimeTextView.setText(formatVideoTime(duration));
			seekBar.setMax((int) duration);
		}
		seekBar.removeCallbacks(progressRunnable);
		seekBar.post(progressRunnable);
		updatePlayState();
	}

	private static String formatVideoTime(long position) {
		position /= 1000;
		int m = (int) (position / 60 % 60);
		int s = (int) (position % 60);
		return String.format(Locale.US, "%02d:%02d", m, s);
	}

	private final View.OnClickListener playPauseClickListener = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			if (initialized) {
				if (finishedPlayback) {
					finishedPlayback = false;
					player.setPosition(0);
					setPlaying(true, true);
				} else {
					boolean playing = !player.isPlaying();
					setPlaying(playing, true);
				}
				updatePlayState();
			}
		}
	};

	private final Runnable progressRunnable = new Runnable() {
		@Override
		public void run() {
			if (initialized) {
				int position;
				if (trackingNow) {
					position = seekBar.getProgress();
				} else {
					position = (int) player.getPosition();
					seekBar.setProgress(position);
				}
				timeTextView.setText(formatVideoTime(position));
			}
			seekBar.postDelayed(this, 200);
		}
	};

	private final SeekBar.OnSeekBarChangeListener seekBarListener = new SeekBar.OnSeekBarChangeListener() {
		private int nextSeekPosition;

		@Override
		public void onStopTrackingTouch(SeekBar seekBar) {
			trackingNow = false;
			seekBar.removeCallbacks(progressRunnable);
			if (nextSeekPosition != -1) {
				seekBar.setProgress(nextSeekPosition);
				player.setPosition(nextSeekPosition);
				seekBar.postDelayed(progressRunnable, 250);
				if (finishedPlayback) {
					finishedPlayback = false;
					updatePlayState();
				}
			} else {
				progressRunnable.run();
			}
		}

		@Override
		public void onStartTrackingTouch(SeekBar seekBar) {
			trackingNow = true;
			seekBar.removeCallbacks(progressRunnable);
			nextSeekPosition = -1;
		}

		@Override
		public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
			if (fromUser) {
				nextSeekPosition = progress;
			}
		}
	};

	private void updatePlayState() {
		if (player != null) {
			boolean playing = player.isPlaying();
			playPauseButton.setImageResource(ResourceUtils
					.getResourceId(instance.galleryInstance.context, finishedPlayback ? R.attr.iconButtonRefresh
							: playing ? R.attr.iconButtonPause : R.attr.iconButtonPlay, 0));
			instance.galleryInstance.callback.setScreenOnFixed(!finishedPlayback && playing);
		}
	}

	public void viewMetadata() {
		if (initialized) {
			Map<String, String> metadata = player.getMetadata();
			showMetadata(instance.galleryInstance.callback.getChildFragmentManager(), metadata);
		}
	}

	private static void showMetadata(FragmentManager fragmentManager, Map<String, String> metadata) {
		new InstanceDialog(fragmentManager, null, provider -> {
			Context context = GalleryInstance.getCallback(provider).getWindow().getContext();
			AlertDialog dialog = new AlertDialog.Builder(context)
					.setTitle(R.string.metadata)
					.setPositiveButton(android.R.string.ok, null)
					.create();
			SummaryLayout layout = new SummaryLayout(dialog);
			String videoFormat = metadata.get("video_format");
			String width = metadata.get("width");
			String height = metadata.get("height");
			String frameRate = metadata.get("frame_rate");
			String pixelFormat = metadata.get("pixel_format");
			String surfaceFormat = metadata.get("surface_format");
			String frameConversion = metadata.get("frame_conversion");
			String audioFormat = metadata.get("audio_format");
			String channels = metadata.get("channels");
			String sampleRate = metadata.get("sample_rate");
			String encoder = metadata.get("encoder");
			String title = metadata.get("title");
			if (videoFormat != null) {
				layout.add("Video", videoFormat);
			}
			if (width != null && height != null) {
				layout.add("Resolution", width + '×' + height);
			}
			if (frameRate != null) {
				layout.add("Frame rate", StringUtils.stripTrailingZeros(frameRate) + " FPS");
			}
			if (pixelFormat != null) {
				layout.add("Pixels", pixelFormat);
			}
			if (surfaceFormat != null) {
				layout.add("Surface", surfaceFormat);
			}
			if (frameConversion != null) {
				layout.add("Frame conversion", frameConversion);
			}
			layout.addDivider();
			if (audioFormat != null) {
				layout.add("Audio", audioFormat);
			}
			if (channels != null) {
				layout.add("Channels", channels);
			}
			if (sampleRate != null) {
				layout.add("Sample rate", sampleRate + " Hz");
			}
			layout.addDivider();
			if (encoder != null) {
				layout.add("Encoder", encoder);
			}
			if (!StringUtils.isEmptyOrWhitespace(title)) {
				layout.add("Title", title);
			}
			return dialog;
		});
	}

	private boolean controlsVisible = false;

	public void invalidateControlsVisibility() {
		boolean visible = initialized && instance.galleryInstance.callback.isSystemUiVisible();
		if (layoutConfiguration >= 0 && controlsVisible != visible) {
			controlsView.animate().cancel();
			if (visible) {
				controlsView.setVisibility(View.VISIBLE);
				controlsView.animate().alpha(1f).translationY(0f).setDuration(250).setListener(null)
						.setInterpolator(AnimationUtils.DECELERATE_INTERPOLATOR).start();
			} else {
				controlsView.animate().alpha(0f).translationY(controlsView.getHeight() -
						configurationView.getHeight()).setDuration(350)
						.setListener(new AnimationUtils.VisibilityListener(controlsView, View.GONE))
						.setInterpolator(AnimationUtils.ACCELERATE_DECELERATE_INTERPOLATOR).start();
			}
			controlsVisible = visible;
		}
	}

	private final VideoPlayer.Listener playerListener = new VideoPlayer.Listener() {
		@Override
		public void onComplete(VideoPlayer player) {
			switch (Preferences.getVideoCompletionMode()) {
				case NOTHING: {
					finishedPlayback = true;
					updatePlayState();
					break;
				}
				case LOOP: {
					player.setPosition(0L);
					break;
				}
				default: {
					throw new IllegalStateException();
				}
			}
		}

		@Override
		public void onBusyStateChange(VideoPlayer player, boolean busy) {
			if (initialized) {
				PagerInstance.ViewHolder holder = instance.currentHolder;
				if (busy) {
					holder.progressBar.setIndeterminate(true);
				}
				holder.progressBar.setVisible(busy, false);
			}
		}

		@Override
		public void onDimensionChange(VideoPlayer player) {
			if (backgroundDrawable != null) {
				backgroundDrawable.recycle();
				Point dimensions = player.getDimensions();
				backgroundDrawable.width = dimensions.x;
				backgroundDrawable.height = dimensions.y;
				instance.currentHolder.photoView.resetScale();
			}
		}
	};

	public void showHideVideoView(boolean show) {
		if (initialized) {
			View videoView = player.getVideoView(instance.galleryInstance.context);
			if (show) {
				backgroundDrawable.recycle();
				videoView.setVisibility(View.VISIBLE);
			} else {
				backgroundDrawable.setFrame(player.getCurrentFrame());
				videoView.setVisibility(View.GONE);
			}
		}
	}

	public void handleSwipingContent(boolean swiping, boolean hideSurface) {
		if (initialized) {
			playPauseButton.setEnabled(!swiping);
			seekBar.setEnabled(!swiping);
			if (swiping) {
				wasPlaying = player.isPlaying();
				setPlaying(false, true);
				if (hideSurface) {
					showHideVideoView(false);
				}
			} else {
				setPlaying(wasPlaying, true);
				if (hideSurface) {
					showHideVideoView(true);
				}
				updatePlayState();
			}
		} else if (player != null) {
			wasPlaying = !swiping;
			hideSurfaceOnInit = hideSurface && swiping;
		}
	}

	private class ReadVideoCallback implements ReadVideoTask.Callback, VideoPlayer.RangeCallback {
		private final VideoPlayer workPlayer;
		private final PagerInstance.ViewHolder holder;
		private final String chanName;
		private final Uri uri;

		private ReadVideoTask downloadTask;
		private ReadVideoTask rangeTask;
		private boolean allowRangeRequests;

		public ReadVideoCallback(VideoPlayer player, PagerInstance.ViewHolder holder, String chanName, Uri uri) {
			this.workPlayer = player;
			this.holder = holder;
			this.chanName = chanName;
			this.uri = uri;
			allowRangeRequests = !AdvancedPreferences.isSingleConnection(chanName);
			Chan chan = Chan.getPreferred(chanName, uri);
			downloadTask = new ReadVideoTask(this, chan, uri, 0);
			downloadTask.execute(ConcurrentUtils.PARALLEL_EXECUTOR);
		}

		public void cancel() {
			if (downloadTask != null) {
				downloadTask.cancel();
				downloadTask = null;
			}
			if (rangeTask != null) {
				rangeTask.cancel();
				rangeTask = null;
			}
		}

		@Override
		public void onReadVideoInit(File partialFile) {
			if (workPlayer == player) {
				new Thread(() -> {
					boolean success;
					try {
						workPlayer.init(partialFile, ReadVideoCallback.this);
						success = true;
					} catch (VideoPlayer.InitializationException e) {
						e.printStackTrace();
						success = false;
					} catch (IOException e) {
						success = false;
					}
					boolean successFinal = success;
					ConcurrentUtils.HANDLER.post(() -> {
						if (workPlayer == player) {
							holder.progressBar.setVisible(false, false);
							if (successFinal) {
								initializePlayer();
								if (downloadTask == null) {
									seekBar.setSecondaryProgress(seekBar.getMax());
									holder.loadState = PagerInstance.LoadState.COMPLETE;
								}
								instance.galleryInstance.callback.invalidateOptionsMenu();
							} else {
								if (downloadTask != null) {
									if (!downloadTask.isError()) {
										downloadTask.cancel();
										downloadTask = null;
									} else {
										return;
									}
								}
								if (rangeTask != null) {
									rangeTask.cancel();
									rangeTask = null;
								}
								instance.callback.showError(holder, instance.galleryInstance.context
										.getString(R.string.playback_error));
							}
						}
					});
				}).start();
			}
		}

		@Override
		public void onReadVideoProgressUpdate(long progress, long progressMax) {
			if (workPlayer == player) {
				workPlayer.setDownloadRange(progress, progressMax);
				if (instance.currentHolder.mediaSummary.updateSize(progressMax)) {
					instance.galleryInstance.callback.updateTitle();
				}
				if (initialized) {
					int max = seekBar.getMax();
					if (max > 0 && progressMax > 0) {
						int newProgress = (int) (max * progress / progressMax);
						seekBar.setSecondaryProgress(newProgress);
					}
				}
			}
		}

		@Override
		public void onReadVideoRangeUpdate(long start, long end) {
			if (workPlayer == player) {
				workPlayer.setPartRange(start, end);
			}
		}

		@Override
		public void onReadVideoSuccess(boolean partial, File file) {
			if (workPlayer == player) {
				if (partial) {
					rangeTask = null;
				} else {
					downloadTask = null;
					long length = file.length();
					workPlayer.setDownloadRange(length, length);
					if (instance.currentHolder.mediaSummary.updateSize(length)) {
						instance.galleryInstance.callback.updateTitle();
					}
					if (initialized) {
						seekBar.setSecondaryProgress(seekBar.getMax());
						holder.loadState = PagerInstance.LoadState.COMPLETE;
						instance.galleryInstance.callback.invalidateOptionsMenu();
					}
				}
			}
		}

		@Override
		public void onReadVideoFail(boolean partial, ErrorItem errorItem, boolean disallowRangeRequests) {
			if (workPlayer == player) {
				if (partial) {
					rangeTask = null;
					if (disallowRangeRequests) {
						allowRangeRequests = false;
					}
				} else {
					holder.progressBar.setVisible(false, false);
					instance.callback.showError(holder, errorItem.toString());
				}
			}
		}

		@Override
		public void requestPartFromPosition(long start) {
			if (rangeTask != null) {
				rangeTask.cancel();
				rangeTask = null;
			}
			if (allowRangeRequests && start > 0) {
				Chan chan = Chan.getPreferred(chanName, uri);
				rangeTask = new ReadVideoTask(this, chan, uri, start);
				rangeTask.execute(ConcurrentUtils.PARALLEL_EXECUTOR);
			}
		}
	}

	private static class BackgroundDrawable extends BaseDrawable {
		public int width;
		public int height;

		private Bitmap frame;
		private boolean draw = false;

		public void setFrame(Bitmap frame) {
			recycleInternal();
			this.frame = frame;
			draw = true;
			invalidateSelf();
		}

		public void recycle() {
			recycleInternal();
			if (draw) {
				draw = false;
				invalidateSelf();
			}
		}

		private void recycleInternal() {
			if (frame != null) {
				frame.recycle();
				frame = null;
			}
		}

		private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);

		@Override
		public void draw(@NonNull Canvas canvas) {
			if (draw) {
				Rect bounds = getBounds();
				paint.setColor(Color.BLACK);
				canvas.drawRect(bounds, paint);
				if (frame != null) {
					canvas.drawBitmap(frame, null, bounds, paint);
				}
			}
		}

		@Override
		public int getOpacity() {
			return PixelFormat.TRANSPARENT;
		}

		@Override
		public int getIntrinsicWidth() {
			return width;
		}

		@Override
		public int getIntrinsicHeight() {
			return height;
		}
	}
}
