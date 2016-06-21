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

package com.mishiranu.dashchan.ui.gallery;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import chan.util.StringUtils;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.async.ReadVideoTask;
import com.mishiranu.dashchan.content.CacheManager;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.graphics.ActionIconSet;
import com.mishiranu.dashchan.media.CachingInputStream;
import com.mishiranu.dashchan.media.VideoPlayer;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.util.AnimationUtils;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.StringBlockBuilder;
import com.mishiranu.dashchan.util.ViewUtils;

public class VideoUnit implements AudioManager.OnAudioFocusChangeListener
{
	private final PagerInstance mInstance;
	private final LinearLayout mControlsView;
	private final AudioManager mAudioManager;
	
	private int mLayoutConfiguration = -1;
	private LinearLayout mConfigurationView;
	private TextView mTimeTextView;
	private TextView mTotalTimeTextView;
	private SeekBar mSeekBar;
	private ImageButton mPlayPauseButton;
	
	private VideoPlayer mPlayer;
	private BackgroundDrawable mBackgroundDrawable;
	private boolean mInitialized;
	private boolean mWasPlaying;
	private boolean mPausedByTransientLossOfFocus;
	private boolean mFinishedPlayback;
	private boolean mTrackingNow;
	private boolean mHideSurfaceOnInit;
	
	private ReadVideoTask mReadVideoTask;
	
	public VideoUnit(PagerInstance instance)
	{
		mInstance = instance;
		mControlsView = new LinearLayout(mInstance.galleryInstance.context);
		mControlsView.setOrientation(LinearLayout.VERTICAL);
		mControlsView.setVisibility(View.GONE);
		mAudioManager = (AudioManager) mInstance.galleryInstance.context.getSystemService(Activity.AUDIO_SERVICE);
	}
	
	public void addViews(FrameLayout frameLayout)
	{
		frameLayout.addView(mControlsView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
	}
	
	public void onResume()
	{
		if (mPlayer != null && mInitialized)
		{
			setPlaying(mWasPlaying, true);
			updatePlayState();
		}
		else mWasPlaying = true;
	}
	
	public void onPause()
	{
		if (mPlayer != null && mInitialized)
		{
			mWasPlaying = mPlayer.isPlaying();
			setPlaying(false, true);
		}
		else mWasPlaying = false;
	}
	
	public void onConfigurationChanged(Configuration newConfig)
	{
		if (newConfig.orientation != Configuration.ORIENTATION_UNDEFINED)
		{
			if (mLayoutConfiguration != -1) recreateVideoControls();
		}
	}
	
	public void onApplyWindowPaddings(Rect rect)
	{
		if (C.API_LOLLIPOP) mControlsView.setPadding(rect.left, 0, rect.right, rect.bottom);
	}
	
	public boolean isInitialized()
	{
		return mInitialized;
	}
	
	public boolean isCreated()
	{
		return mPlayer != null;
	}
	
	public void interrupt()
	{
		if (mReadVideoTask != null)
		{
			mReadVideoTask.cancel();
			mReadVideoTask = null;
		}
		if (mInitialized)
		{
			mAudioManager.abandonAudioFocus(this);
			mInitialized = false;
		}
		invalidateControlsVisibility();
		if (mPlayer != null)
		{
			mPlayer.free();
			mPlayer = null;
			mInstance.currentHolder.progressBar.setVisible(false, false);
		}
		if (mBackgroundDrawable != null)
		{
			mBackgroundDrawable.recycle();
			mBackgroundDrawable = null;
		}
		interruptHolder(mInstance.leftHolder);
		interruptHolder(mInstance.currentHolder);
		interruptHolder(mInstance.rightHolder);
	}
	
	private void interruptHolder(PagerInstance.ViewHolder holder)
	{
		if (holder != null) holder.surfaceParent.removeAllViews();
	}
	
	public void applyVideo(Uri uri, File file, boolean reload)
	{
		mWasPlaying = true;
		mFinishedPlayback = false;
		mHideSurfaceOnInit = false;
		final VideoPlayer workPlayer = new VideoPlayer(Preferences.isVideoSeekAnyFrame());
		workPlayer.setListener(mPlayerListener);
		mPlayer = workPlayer;
		boolean loadedFromFile = false;
		if (!reload && file.exists())
		{
			try
			{
				mPlayer.init(file);
				initializePlayer();
				mSeekBar.setSecondaryProgress(mSeekBar.getMax());
				loadedFromFile = true;
				mInstance.currentHolder.fullLoaded = true;
				mInstance.galleryInstance.callback.invalidateOptionsMenu();
			}
			catch (IOException e)
			{
				
			}
		}
		if (!loadedFromFile)
		{
			PagerInstance.ViewHolder holder = mInstance.currentHolder;
			holder.progressBar.setIndeterminate(true);
			holder.progressBar.setVisible(true, false);
			final CachingInputStream inputStream = new CachingInputStream();
			new AsyncTask<Void, Void, Boolean>()
			{
				@Override
				protected Boolean doInBackground(Void... params)
				{
					try
					{
						VideoPlayer player = mPlayer;
						if (player != null) player.init(inputStream);
						return true;
					}
					catch (IOException e)
					{
						return false;
					}
				}
				
				@Override
				protected void onPostExecute(Boolean result)
				{
					if (mPlayer != workPlayer) return;
					PagerInstance.ViewHolder holder = mInstance.currentHolder;
					holder.progressBar.setVisible(false, false);
					if (result)
					{
						initializePlayer();
						mInstance.galleryInstance.callback.invalidateOptionsMenu();
						if (mReadVideoTask == null) mSeekBar.setSecondaryProgress(mSeekBar.getMax());
					}
					else
					{
						if (mReadVideoTask != null)
						{
							if (!mReadVideoTask.isError())
							{
								mReadVideoTask.cancel();
								mReadVideoTask = null;
							}
							else return;
						}
						mInstance.callback.showError(holder, mInstance.galleryInstance.context
								.getString(R.string.message_playback_error));
					}
				}
			}.executeOnExecutor(ConcurrentUtils.SEPARATE_EXECUTOR);
			mReadVideoTask = new ReadVideoTask(mInstance.galleryInstance.chanName, uri, inputStream,
					new ReadVideoCallback(workPlayer, holder));
			mReadVideoTask.executeOnExecutor(ReadVideoTask.THREAD_POOL_EXECUTOR);
		}
	}
	
	@Override
	public void onAudioFocusChange(int focusChange)
	{
		if (!mInitialized) return;
		switch (focusChange)
		{
			case AudioManager.AUDIOFOCUS_LOSS:
			{
				setPlaying(false, false);
				updatePlayState();
				break;
			}
			case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
			{
				boolean playing = mPlayer.isPlaying();
				setPlaying(false, false);
				if (playing) mPausedByTransientLossOfFocus = true;
				updatePlayState();
				break;
			}
			case AudioManager.AUDIOFOCUS_GAIN:
			{
				if (mPausedByTransientLossOfFocus) setPlaying(true, false);
				updatePlayState();
				break;
			}
		}
	}
	
	private boolean setPlaying(boolean playing, boolean resetFocus)
	{
		if (mPlayer.isPlaying() != playing)
		{
			if (resetFocus && mPlayer.isAudioPresent())
			{
				if (playing)
				{
					if (mAudioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
							!= AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
					{
						return false;
					}
				}
				else mAudioManager.abandonAudioFocus(this);
			}
			mPlayer.setPlaying(playing);
		}
		return true;
	}
	
	private void initializePlayer()
	{
		PagerInstance.ViewHolder holder = mInstance.currentHolder;
		holder.progressBar.setVisible(false, false);
		Point dimensions = mPlayer.getDimensions();
		mBackgroundDrawable = new BackgroundDrawable();
		mBackgroundDrawable.width = dimensions.x;
		mBackgroundDrawable.height = dimensions.y;
		holder.recyclePhotoView();
		holder.photoView.setImage(mBackgroundDrawable, false, true, false);
		View videoView = mPlayer.getVideoView(mInstance.galleryInstance.context);
		holder.surfaceParent.addView(videoView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));
		recreateVideoControls();
		mPlayPauseButton.setEnabled(true);
		mSeekBar.setEnabled(true);
		mInitialized = true;
		mPausedByTransientLossOfFocus = false;
		if (mHideSurfaceOnInit) showHideVideoView(false);
		invalidateControlsVisibility();
		setPlaying(mWasPlaying, true);
		updatePlayState();
	}
	
	private void recreateVideoControls()
	{
		Context context = mInstance.galleryInstance.context;
		float density = context.getResources().getDisplayMetrics().density;
		int targetLayoutCounfiguration = ResourceUtils.isTabletOrLandscape(context.getResources()
				.getConfiguration()) ? 1 : 0;
		if (targetLayoutCounfiguration != mLayoutConfiguration)
		{
			boolean firstTimeLayout = mLayoutConfiguration < 0;
			mLayoutConfiguration = targetLayoutCounfiguration;
			boolean longLayout = targetLayoutCounfiguration == 1;
			
			mControlsView.removeAllViews();
			if (mSeekBar != null) mSeekBar.removeCallbacks(mProgressRunnable);
			mTrackingNow = false;
			
			mConfigurationView = new LinearLayout(context);
			mConfigurationView.setOrientation(LinearLayout.HORIZONTAL);
			mConfigurationView.setGravity(Gravity.RIGHT);
			mConfigurationView.setPadding((int) (8f * density), 0, (int) (8f * density), 0);
			mControlsView.addView(mConfigurationView, LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.WRAP_CONTENT);
			
			LinearLayout controls = new LinearLayout(context);
			controls.setOrientation(longLayout ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
			controls.setBackgroundColor(mInstance.galleryInstance.actionBarColor);
			controls.setPadding((int) (8f * density), longLayout ? 0 : (int) (8f * density), (int) (8f * density), 0);
			controls.setClickable(true);
			mControlsView.addView(controls, LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.WRAP_CONTENT);
			
			CharSequence oldTimeText = mTimeTextView != null ? mTimeTextView.getText() : null;
			mTimeTextView = new TextView(context, null, android.R.attr.textAppearanceListItem);
			mTimeTextView.setTextSize(14f);
			mTimeTextView.setGravity(Gravity.CENTER_HORIZONTAL);
			if (C.API_LOLLIPOP) mTimeTextView.setTypeface(GraphicsUtils.TYPEFACE_MEDIUM);
			if (oldTimeText != null) mTimeTextView.setText(oldTimeText);
			
			mTotalTimeTextView = new TextView(context, null, android.R.attr.textAppearanceListItem);
			mTotalTimeTextView.setTextSize(14f);
			mTotalTimeTextView.setGravity(Gravity.CENTER_HORIZONTAL);
			if (C.API_LOLLIPOP) mTotalTimeTextView.setTypeface(GraphicsUtils.TYPEFACE_MEDIUM);
			
			int oldSecondaryProgress = mSeekBar != null ? mSeekBar.getSecondaryProgress() : -1;
			mSeekBar = new SeekBar(context);
			mSeekBar.setOnSeekBarChangeListener(mSeekBarListener);
			if (oldSecondaryProgress >= 0) mSeekBar.setSecondaryProgress(oldSecondaryProgress);
			
			mPlayPauseButton = new ImageButton(context, null, android.R.attr.borderlessButtonStyle);
			mPlayPauseButton.setScaleType(ImageButton.ScaleType.CENTER);
			mPlayPauseButton.setOnClickListener(mPlayPauseClickListener);
			
			if (longLayout)
			{
				controls.setGravity(Gravity.CENTER_VERTICAL);
				controls.addView(mTimeTextView, (int) (48f * density), LinearLayout.LayoutParams.WRAP_CONTENT);
				controls.addView(mSeekBar, new LinearLayout.LayoutParams(0,
						LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
				controls.addView(mPlayPauseButton, (int) (80f * density), LinearLayout.LayoutParams.WRAP_CONTENT);
				controls.addView(mTotalTimeTextView, (int) (48f * density),
						LinearLayout.LayoutParams.WRAP_CONTENT);
			}
			else
			{
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
				controls1.addView(mSeekBar, LinearLayout.LayoutParams.MATCH_PARENT,
						LinearLayout.LayoutParams.WRAP_CONTENT);
				controls2.addView(mTimeTextView, new LinearLayout.LayoutParams(0,
						LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
				controls2.addView(mPlayPauseButton, (int) (80f * density), LinearLayout.LayoutParams.WRAP_CONTENT);
				controls2.addView(mTotalTimeTextView, new LinearLayout.LayoutParams(0,
						LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
			}
			if (firstTimeLayout)
			{
				AnimationUtils.measureDynamicHeight(mControlsView);
				mControlsView.setTranslationY(mControlsView.getMeasuredHeight());
				mControlsView.setAlpha(0f);
			}
		}
		if (mPlayer != null)
		{
			mConfigurationView.removeAllViews();
			ActionIconSet set = null;
			if (!mPlayer.isAudioPresent())
			{
				if (set == null) set = new ActionIconSet(context);
				ImageView imageView = new ImageView(context);
				imageView.setImageResource(set.getId(R.attr.actionVolumeOff));
				imageView.setScaleType(ImageView.ScaleType.CENTER);
				if (C.API_LOLLIPOP) imageView.setImageAlpha(0x99);
				mConfigurationView.addView(imageView, (int) (48f * density), (int) (48f * density));
			}
			mTotalTimeTextView.setText(formatVideoTime(mPlayer.getDuration()));
			mSeekBar.setMax((int) mPlayer.getDuration());
		}
		mSeekBar.removeCallbacks(mProgressRunnable);
		mSeekBar.post(mProgressRunnable);
		updatePlayState();
	}
	
	private static String formatVideoTime(long position)
	{
		position /= 1000;
		int m = (int) (position / 60 % 60);
		int s = (int) (position % 60);
		return String.format(Locale.US, "%02d:%02d", m, s);
	}
	
	private View.OnClickListener mPlayPauseClickListener = new View.OnClickListener()
	{
		@Override
		public void onClick(View v)
		{
			if (mInitialized)
			{
				if (mFinishedPlayback)
				{
					mFinishedPlayback = false;
					mPlayer.setPosition(0);
					setPlaying(true, true);
				}
				else
				{
					boolean playing = !mPlayer.isPlaying();
					setPlaying(playing, true);
				}
				updatePlayState();
			}
		}
	};
	
	private Runnable mProgressRunnable = new Runnable()
	{
		@Override
		public void run()
		{
			if (mInitialized)
			{
				int position;
				if (mTrackingNow) position = mSeekBar.getProgress(); else
				{
					position = (int) mPlayer.getPosition();
					mSeekBar.setProgress(position);
				}
				mTimeTextView.setText(formatVideoTime(position));
			}
			mSeekBar.postDelayed(this, 200);
		}
	};
	
	private final SeekBar.OnSeekBarChangeListener mSeekBarListener = new SeekBar.OnSeekBarChangeListener()
	{
		private int mNextSeekPosition;
		
		@Override
		public void onStopTrackingTouch(SeekBar seekBar)
		{
			mTrackingNow = false;
			seekBar.removeCallbacks(mProgressRunnable);
			if (mNextSeekPosition != -1)
			{
				seekBar.setProgress(mNextSeekPosition);
				mPlayer.setPosition(mNextSeekPosition);
				seekBar.postDelayed(mProgressRunnable, 250);
				if (mFinishedPlayback)
				{
					mFinishedPlayback = false;
					updatePlayState();
				}
			}
			else mProgressRunnable.run();
		}
		
		@Override
		public void onStartTrackingTouch(SeekBar seekBar)
		{
			mTrackingNow = true;
			seekBar.removeCallbacks(mProgressRunnable);
			mNextSeekPosition = -1;
		}
		
		@Override
		public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
		{
			if (fromUser) mNextSeekPosition = progress;
		}
	};
	
	private void updatePlayState()
	{
		if (mPlayer != null)
		{
			boolean playing = mPlayer.isPlaying();
			mPlayPauseButton.setImageResource(ResourceUtils.getResourceId(mInstance.galleryInstance.context,
					mFinishedPlayback ? R.attr.buttonRefresh : playing ? R.attr.buttonPause : R.attr.buttonPlay, 0));
			mInstance.galleryInstance.callback.setScreenOnFixed(!mFinishedPlayback && playing);
		}
	}
	
	public void viewTechnicalInfo()
	{
		if (mInitialized)
		{
			HashMap<String, String> technicalInfo = mPlayer.getTechnicalInfo();
			StringBlockBuilder builder = new StringBlockBuilder();
			String videoFormat = technicalInfo.get("video_format");
			String width = technicalInfo.get("width");
			String height = technicalInfo.get("height");
			String frameRate = technicalInfo.get("frame_rate");
			String pixelFormat = technicalInfo.get("pixel_format");
			String surfaceFormat = technicalInfo.get("surface_format");
			String useLibyuv = technicalInfo.get("use_libyuv");
			String audioFormat = technicalInfo.get("audio_format");
			String channels = technicalInfo.get("channels");
			String sampleRate = technicalInfo.get("sample_rate");
			String encoder = technicalInfo.get("encoder");
			String title = technicalInfo.get("title");
			if (videoFormat != null) builder.appendLine("Video: " + videoFormat);
			if (width != null && height != null) builder.appendLine("Resolution: " + width + 'x' + height);
			if (frameRate != null) builder.appendLine("Frame rate: " + frameRate);
			if (pixelFormat != null) builder.appendLine("Pixels: " + pixelFormat);
			if (surfaceFormat != null) builder.appendLine("Surface: " + surfaceFormat);
			if ("1".equals(useLibyuv)) builder.appendLine("Use libyuv: true");
			else if ("0".equals(useLibyuv)) builder.appendLine("Use libyuv: false");
			builder.appendEmptyLine();
			if (audioFormat != null) builder.appendLine("Audio: " + audioFormat);
			if (channels != null) builder.appendLine("Channels: " + channels);
			if (sampleRate != null) builder.appendLine("Sample rate: " + sampleRate + " Hz");
			builder.appendEmptyLine();
			if (encoder != null) builder.appendLine("Encoder: " + encoder);
			if (!StringUtils.isEmptyOrWhitespace(title)) builder.appendLine("Title: " + title);
			String message = builder.toString();
			if (message.length() > 0)
			{
				AlertDialog dialog = new AlertDialog.Builder(mInstance.galleryInstance.context)
						.setTitle(R.string.action_technical_info).setMessage(message)
						.setPositiveButton(android.R.string.ok, null).create();
				dialog.setOnShowListener(ViewUtils.ALERT_DIALOG_MESSAGE_SELECTABLE);
				dialog.show();
			}
		}
	}
	
	private boolean mControlsVisible = false;
	
	public void invalidateControlsVisibility()
	{
		boolean visible = mInitialized && mInstance.galleryInstance.callback.isSystemUiVisible();
		if (mLayoutConfiguration >= 0 && mControlsVisible != visible)
		{
			mControlsView.animate().cancel();
			if (visible)
			{
				mControlsView.setVisibility(View.VISIBLE);
				mControlsView.animate().alpha(1f).translationY(0f).setDuration(250).setListener(null)
						.setInterpolator(AnimationUtils.DECELERATE_INTERPOLATOR).start();
			}
			else
			{
				mControlsView.animate().alpha(0f).translationY(mControlsView.getHeight() -
						mConfigurationView.getHeight()).setDuration(350)
						.setListener(new AnimationUtils.VisibilityListener(mControlsView, View.GONE))
						.setInterpolator(AnimationUtils.ACCELERATE_DECELERATE_INTERPOLATOR).start();
			}
			mControlsVisible = visible;
		}
	}
	
	private final VideoPlayer.Listener mPlayerListener = new VideoPlayer.Listener()
	{
		@Override
		public void onComplete(VideoPlayer player)
		{
			switch (Preferences.getVideoCompletionMode())
			{
				case Preferences.VIDEO_COMPLETION_MODE_NOTHING:
				{
					mFinishedPlayback = true;
					updatePlayState();
					break;
				}
				case Preferences.VIDEO_COMPLETION_MODE_LOOP:
				{
					mPlayer.setPosition(0L);
					break;
				}
			}
		}
		
		@Override
		public void onBusyStateChange(VideoPlayer player, boolean busy)
		{
			if (mInitialized)
			{
				PagerInstance.ViewHolder holder = mInstance.currentHolder;
				if (busy) holder.progressBar.setIndeterminate(true);
				holder.progressBar.setVisible(busy, false);
			}
		}
		
		@Override
		public void onDimensionChange(VideoPlayer player)
		{
			if (mBackgroundDrawable != null)
			{
				mBackgroundDrawable.recycle();
				Point dimensions = player.getDimensions();
				mBackgroundDrawable.width = dimensions.x;
				mBackgroundDrawable.height = dimensions.y;
				mInstance.currentHolder.photoView.resetScale();
			}
		}
	};
	
	public void showHideVideoView(boolean show)
	{
		if (mInitialized)
		{
			View videoView = mPlayer.getVideoView(mInstance.galleryInstance.context);
			if (show)
			{
				mBackgroundDrawable.recycle();
				videoView.setVisibility(View.VISIBLE);
			}
			else
			{
				mBackgroundDrawable.setFrame(mPlayer.getCurrentFrame());
				videoView.setVisibility(View.GONE);
			}
		}
	}
	
	public void handleSwipingContent(boolean swiping, boolean hideSurface)
	{
		if (mInitialized)
		{
			mPlayPauseButton.setEnabled(!swiping);
			mSeekBar.setEnabled(!swiping);
			if (swiping)
			{
				mWasPlaying = mPlayer.isPlaying();
				setPlaying(false, true);
				if (hideSurface) showHideVideoView(false);
			}
			else
			{
				setPlaying(mWasPlaying, true);
				if (hideSurface) showHideVideoView(true);
				updatePlayState();
			}
		}
		else if (mPlayer != null)
		{
			mWasPlaying = !swiping;
			mHideSurfaceOnInit = hideSurface && swiping;
		}
	}
	
	private class ReadVideoCallback implements ReadVideoTask.Callback
	{
		private final VideoPlayer mWorkPlayer;
		private final PagerInstance.ViewHolder mHolder;
		
		public ReadVideoCallback(VideoPlayer player, PagerInstance.ViewHolder holder)
		{
			mWorkPlayer = player;
			mHolder = holder;
		}
		
		@Override
		public void onReadVideoProgressUpdate(long progress, long progressMax)
		{
			if (mInitialized && mWorkPlayer == mPlayer)
			{
				int max = mSeekBar.getMax();
				if (max > 0 && progressMax > 0)
				{
					int newProgress = (int) (max * progress / progressMax);
					mSeekBar.setSecondaryProgress(newProgress);
				}
			}
		}
		
		@Override
		public void onReadVideoSuccess(final CachingInputStream inputStream)
		{
			if (mWorkPlayer != mPlayer) return;
			mReadVideoTask = null;
			if (mInitialized) mSeekBar.setSecondaryProgress(mSeekBar.getMax());
			new AsyncTask<Void, Void, Boolean>()
			{
				private File mFile;
				
				@Override
				protected Boolean doInBackground(Void... params)
				{
					mFile = CacheManager.getInstance().getMediaFile(mHolder.galleryItem
							.getFileUri(mInstance.galleryInstance.locator), false);
					if (mFile == null) return false;
					boolean success;
					FileOutputStream output = null;
					try
					{
						output = new FileOutputStream(mFile);
						inputStream.writeTo(output);
						success = true;
					}
					catch (IOException e)
					{
						success = false;
						e.printStackTrace();
					}
					finally
					{
						IOUtils.close(output);
					}
					CacheManager.getInstance().handleDownloadedFile(mFile, success);
					return success;
				}
				
				@Override
				protected void onPostExecute(Boolean result)
				{
					if (result && mWorkPlayer == mPlayer)
					{
						mHolder.fullLoaded = true;
						mInstance.galleryInstance.callback.invalidateOptionsMenu();
						try
						{
							mPlayer.replaceStream(mFile);
						}
						catch (IOException e)
						{
							
						}
						if (mHolder.galleryItem.size <= 0)
						{
							mHolder.galleryItem.size = (int) mFile.length();
							mInstance.galleryInstance.callback.updateTitle();
						}
					}
				}
			}.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		}
		
		@Override
		public void onReadVideoFail(ErrorItem errorItem)
		{
			mReadVideoTask = null;
			mHolder.progressBar.setVisible(false, false);
			mInstance.callback.showError(mHolder, errorItem.toString());
			mInstance.galleryInstance.callback.invalidateOptionsMenu();
		}
	}
	
	private static class BackgroundDrawable extends Drawable
	{
		public int width;
		public int height;
		
		private Bitmap mFrame;
		private boolean mDraw = false;
		
		public void setFrame(Bitmap frame)
		{
			recycleInternal();
			mFrame = frame;
			mDraw = true;
			invalidateSelf();
		}
		
		public void recycle()
		{
			recycleInternal();
			if (mDraw)
			{
				mDraw = false;
				invalidateSelf();
			}
		}
		
		private void recycleInternal()
		{
			if (mFrame != null)
			{
				mFrame.recycle();
				mFrame = null;
			}
		}
		
		private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
		
		@Override
		public void draw(Canvas canvas)
		{
			if (mDraw)
			{
				Rect bounds = getBounds();
				mPaint.setColor(Color.BLACK);
				canvas.drawRect(bounds, mPaint);
				if (mFrame != null) canvas.drawBitmap(mFrame, null, bounds, mPaint);
			}
		}
		
		@Override
		public int getOpacity()
		{
			return PixelFormat.TRANSPARENT;
		}
		
		@Override
		public void setAlpha(int alpha)
		{
			
		}
		
		@Override
		public void setColorFilter(ColorFilter colorFilter)
		{
			
		}
		
		@Override
		public int getIntrinsicWidth()
		{
			return width;
		}
		
		@Override
		public int getIntrinsicHeight()
		{
			return height;
		}
	}
}