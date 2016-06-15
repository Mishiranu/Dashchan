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
import java.util.ArrayList;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.Shape;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import chan.util.StringUtils;

import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.CacheManager;
import com.mishiranu.dashchan.content.ImageLoader;
import com.mishiranu.dashchan.content.model.FileHolder;
import com.mishiranu.dashchan.content.model.GalleryItem;
import com.mishiranu.dashchan.graphics.SimpleBitmapDrawable;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.util.AnimationUtils;
import com.mishiranu.dashchan.util.NavigationUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.widget.CircularProgressBar;
import com.mishiranu.dashchan.widget.PhotoView;
import com.mishiranu.dashchan.widget.PhotoViewPager;

public class PagerUnit implements PagerInstance.Callback
{
	private final GalleryInstance mGalleryInstance;
	private final PagerInstance mPagerInstance;
	
	private final ImageUnit mImageUnit;
	private final VideoUnit mVideoUnit;
	
	private final FrameLayout mViewPagerParent;
	private final PhotoViewPager mViewPager;
	private final PagerAdapter mPagerAdapter;
	
	public PagerUnit(GalleryInstance instance)
	{
		mGalleryInstance = instance;
		mPagerInstance = new PagerInstance(instance, this);
		mImageUnit = new ImageUnit(mPagerInstance);
		mVideoUnit = new VideoUnit(mPagerInstance);
		float density = ResourceUtils.obtainDensity(instance.context);
		mViewPagerParent = new FrameLayout(instance.context);
		mPagerAdapter = new PagerAdapter(instance.galleryItems);
		mPagerAdapter.setWaitBeforeNextVideo(PhotoView.INITIAL_SCALE_TRANSITION_TIME + 100);
		mViewPager = new PhotoViewPager(instance.context, mPagerAdapter);
		mViewPager.setInnerPadding((int) (16f * density));
		mViewPager.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.MATCH_PARENT));
		mViewPagerParent.addView(mViewPager, FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.MATCH_PARENT);
		mViewPager.setCount(instance.galleryItems.size());
	}
	
	public View getView()
	{
		return mViewPagerParent;
	}
	
	public void addAndInitViews(FrameLayout frameLayout, int initialPosition)
	{
		mViewPager.setCurrentIndex(initialPosition >= 0 ? initialPosition : 0);
		mVideoUnit.addViews(frameLayout);
	}
	
	public void onViewsCreated(int[] imageViewPosition)
	{
		if (!mGalleryInstance.callback.isGalleryWindow() && imageViewPosition != null)
		{
			View view = mViewPager.getCurrentView();
			if (view != null)
			{
				int[] location = new int[2];
				view.getLocationOnScreen(location);
				PagerInstance.ViewHolder holder = (PagerInstance.ViewHolder) view.getTag();
				if (holder.photoView.hasImage())
				{
					holder.photoView.setInitialScaleAnimationData(imageViewPosition, Preferences.isCutThumbnails());
				}
			}
		}
	}
	
	private boolean mResumed = false;
	
	public void onResume()
	{
		mResumed = true;
		mVideoUnit.onResume();
	}
	
	public void onPause()
	{
		mResumed = false;
		mVideoUnit.onPause();
	}
	
	public int getCurrentIndex()
	{
		return mViewPager.getCurrentIndex();
	}
	
	public void onApplyWindowPaddings(Rect rect)
	{
		mVideoUnit.onApplyWindowPaddings(rect);
	}
	
	public void invalidateControlsVisibility()
	{
		mVideoUnit.invalidateControlsVisibility();
	}
	
	public void onBackToGallery()
	{
		mVideoUnit.showHideVideoView(false);
	}
	
	private static final float PAGER_SCALE = 0.9f;
	
	public void switchMode(boolean galleryMode, int duration)
	{
		if (galleryMode)
		{
			interrupt(true);
			mPagerInstance.leftHolder = null;
			mPagerInstance.currentHolder = null;
			mPagerInstance.rightHolder = null;
			if (duration > 0)
			{
				mViewPager.setAlpha(1f);
				mViewPager.setScaleX(1f);
				mViewPager.setScaleY(1f);
				mViewPager.animate().alpha(0f).scaleX(PAGER_SCALE).scaleY(PAGER_SCALE).setDuration(duration)
						.setListener(new AnimationUtils.VisibilityListener(mViewPager, View.GONE)).start();
			}
			else mViewPager.setVisibility(View.GONE);
		}
		else
		{
			mViewPager.setVisibility(View.VISIBLE);
			if (duration > 0)
			{
				mViewPager.setAlpha(0f);
				mViewPager.setScaleX(PAGER_SCALE);
				mViewPager.setScaleY(PAGER_SCALE);
				mViewPager.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(duration).setListener(null).start();
			}
		}
	}
	
	public void navigatePageFromList(int position, int duration)
	{
		mPagerAdapter.setWaitBeforeNextVideo(duration);
		mViewPager.setCurrentIndex(position);
	}
	
	public void onConfigurationChanged(Configuration newConfig)
	{
		mVideoUnit.onConfigurationChanged(newConfig);
	}
	
	public void refreshCurrent()
	{
		if (mPagerInstance.currentHolder != null) loadImageVideo(true, false, 0);
	}
	
	public static class OptionsMenuCapabilities
	{
		public final boolean available;
		public final boolean save;
		public final boolean refresh;
		public final boolean viewTechnicalInfo;
		public final boolean searchImage;
		public final boolean navigatePost;
		public final boolean shareFile;
		
		public OptionsMenuCapabilities(boolean available, boolean save, boolean refresh, boolean viewTechnicalInfo,
				boolean searchImage, boolean navigatePost, boolean shareFile)
		{
			this.available = available;
			this.save = save;
			this.refresh = refresh;
			this.viewTechnicalInfo = viewTechnicalInfo;
			this.searchImage = searchImage;
			this.navigatePost = navigatePost;
			this.shareFile = shareFile;
		}
	}
	
	public OptionsMenuCapabilities obtainOptionsMenuCapabilities()
	{
		PagerInstance.ViewHolder holder = mPagerInstance.currentHolder;
		boolean available = false;
		boolean save = false;
		boolean refresh = false;
		boolean viewTechnicalInfo = false;
		boolean searchImage = false;
		boolean navigatePost = false;
		boolean shareFile = false;
		if (holder != null)
		{
			available = true;
			GalleryItem galleryItem = holder.galleryItem;
			boolean fullLoaded = holder.fullLoaded;
			boolean isVideo = galleryItem.isVideo(mGalleryInstance.locator);
			boolean isOpenableVideo = isVideo && galleryItem.isOpenableVideo(mGalleryInstance.locator);
			boolean isVideoInitialized = isOpenableVideo && mVideoUnit.isInitialized();
			save = fullLoaded || isVideo && !isOpenableVideo;
			if (!save && isOpenableVideo)
			{
				File cachedFile = CacheManager.getInstance().getMediaFile(galleryItem
						.getFileUri(mGalleryInstance.locator), false);
				if (cachedFile != null && cachedFile.exists()) save = true;
			}
			refresh = !isOpenableVideo && !isVideo || isVideoInitialized;
			viewTechnicalInfo = isVideoInitialized;
			searchImage = galleryItem.getDisplayImageUri(mGalleryInstance.locator) != null;
			navigatePost = galleryItem.postNumber != null;
			shareFile = fullLoaded;
		}
		return new OptionsMenuCapabilities(available, save, refresh, viewTechnicalInfo,
				searchImage, navigatePost, shareFile);
	}
	
	public GalleryItem getCurrentGalleryItem()
	{
		return mPagerInstance.currentHolder != null ? mPagerInstance.currentHolder.galleryItem : null;
	}
	
	public void viewTechnicalInfo()
	{
		mVideoUnit.viewTechnicalInfo();
	}
	
	private void interrupt(boolean force)
	{
		mImageUnit.interrupt(force);
		mVideoUnit.interrupt();
	}
	
	public void onFinish()
	{
		interrupt(true);
		mViewPager.postDelayed(new Runnable()
		{
			@Override
			public void run()
			{
				mPagerAdapter.recycleAll();
				System.gc();
			}
		}, 200);
	}
	
	private void loadImageVideo(final boolean reload, boolean mayShowThumbnailOnly, int waitBeforeVideo)
	{
		PagerInstance.ViewHolder holder = mPagerInstance.currentHolder;
		if (holder == null) return;
		GalleryItem galleryItem = holder.galleryItem;
		interrupt(false);
		holder.fullLoaded = false;
		mGalleryInstance.callback.invalidateOptionsMenu();
		CacheManager cacheManager = CacheManager.getInstance();
		if (!cacheManager.isCacheAvailable())
		{
			showError(holder, mGalleryInstance.context.getString(R.string.message_cache_unavailable));
			return;
		}
		mGalleryInstance.callback.modifySystemUiVisibility(GalleryInstance.FLAG_LOCKED_ERROR, false);
		holder.errorView.setVisibility(View.GONE);
		boolean thumbnailReady = holder.photoViewThumbnail;
		if (!thumbnailReady)
		{
			holder.recyclePhotoView();
			thumbnailReady = presetThumbnail(holder, galleryItem, reload, false);
		}
		boolean isImage = galleryItem.isImage(mGalleryInstance.locator);
		boolean isVideo = galleryItem.isVideo(mGalleryInstance.locator);
		boolean isOpenableVideo = isVideo && galleryItem.isOpenableVideo(mGalleryInstance.locator);
		if (waitBeforeVideo > 0 && thumbnailReady && isOpenableVideo && !mayShowThumbnailOnly)
		{
			mViewPagerParent.postDelayed(new Runnable()
			{
				@Override
				public void run()
				{
					loadImageVideo(reload, false, 0);
				}
			}, waitBeforeVideo);
			return;
		}
		if (isVideo && !isOpenableVideo || isOpenableVideo && mayShowThumbnailOnly)
		{
			holder.playButton.setVisibility(View.VISIBLE);
			holder.photoView.setDrawDimForCurrentImage(true);
			return;
		}
		else
		{
			holder.playButton.setVisibility(View.GONE);
			holder.photoView.setDrawDimForCurrentImage(false);
		}
		holder.playButton.setVisibility(View.GONE);
		Uri uri = galleryItem.getFileUri(mGalleryInstance.locator);
		File cachedFile = cacheManager.getMediaFile(uri, true);
		if (cachedFile == null)
		{
			showError(holder, mGalleryInstance.context.getString(R.string.message_cache_unavailable));
		}
		else if (isImage)
		{
			mImageUnit.applyImage(uri, cachedFile, reload);
		}
		else if (isVideo)
		{
			mImageUnit.interrupt(true);
			mVideoUnit.applyVideo(uri, cachedFile, reload);
		}
	}
	
	private boolean presetThumbnail(PagerInstance.ViewHolder holder, GalleryItem galleryItem,
			boolean keepScale, boolean unbind)
	{
		if (unbind) ImageLoader.getInstance().unbind(holder.photoView);
		Uri uri = galleryItem.getThumbnailUri(mGalleryInstance.locator);
		if (uri != null && galleryItem.width > 0 && galleryItem.height > 0)
		{
			CacheManager cacheManager = CacheManager.getInstance();
			File file = cacheManager.getThumbnailFile(cacheManager.getCachedFileKey(uri));
			if (file != null && file.exists())
			{
				Bitmap bitmap = FileHolder.obtain(file).readImageBitmap();
				if (bitmap != null)
				{
					holder.recyclePhotoView();
					holder.simpleBitmapDrawable = new SimpleBitmapDrawable(bitmap, galleryItem.width,
							galleryItem.height);
					holder.photoView.setImage(holder.simpleBitmapDrawable, bitmap.hasAlpha(), false, keepScale);
					holder.photoViewThumbnail = true;
					return true;
				}
			}
		}
		return false;
	}
	
	@Override
	public void showError(PagerInstance.ViewHolder holder, String message)
	{
		if (holder == mPagerInstance.currentHolder)
		{
			mGalleryInstance.callback.modifySystemUiVisibility(GalleryInstance.FLAG_LOCKED_ERROR, true);
			holder.recyclePhotoView();
			interrupt(false);
			holder.errorView.setVisibility(View.VISIBLE);
			holder.errorText.setText(!StringUtils.isEmpty(message) ? message
					: mGalleryInstance.context.getString(R.string.message_unknown_error));
			holder.progressBar.cancelVisibilityTransient();
		}
	}
	
	private final PhotoView.Listener mPhotoViewListener = new PhotoView.Listener()
	{
		@Override
		public void onClick(PhotoView photoView, boolean image, float x, float y)
		{
			if (!mGalleryInstance.callback.isGalleryMode())
			{
				GalleryItem galleryItem = mPagerInstance.currentHolder.galleryItem;
				View playButton = mPagerInstance.currentHolder.playButton;
				if (playButton.getVisibility() == View.VISIBLE && galleryItem.isVideo(mGalleryInstance.locator)
						&& !mVideoUnit.isCreated())
				{
					int centerX = playButton.getLeft() + playButton.getWidth() / 2;
					int centerY = playButton.getTop() + playButton.getHeight() / 2;
					int size = Math.min(playButton.getWidth(), playButton.getHeight());
					float distance = (float) Math.sqrt((centerX - x) * (centerX - x) + (centerY - y) * (centerY - y));
					if (distance <= size / 3f * 2f)
					{
						if (!galleryItem.isOpenableVideo(mGalleryInstance.locator))
						{
							NavigationUtils.handleUri(mGalleryInstance.context, mGalleryInstance.chanName, galleryItem
									.getFileUri(mGalleryInstance.locator), NavigationUtils.BrowserType.EXTERNAL);
						}
						else loadImageVideo(false, false, 0);
						return;
					}
				}
				if (image) mGalleryInstance.callback.toggleSystemUIVisibility(GalleryInstance.FLAG_LOCKED_USER);
				else mGalleryInstance.callback.navigateGalleryOrFinish();
			}
		}
		
		private boolean mSwiping = false;
		
		@Override
		public void onVerticalSwipe(PhotoView photoView, float value)
		{
			boolean swiping = value != 0f;
			if (mSwiping != swiping)
			{
				mSwiping = swiping;
				mVideoUnit.handleSwipingContent(mSwiping, true);
			}
			mGalleryInstance.callback.modifyVerticalSwipeState(value);
		}
		
		@Override
		public boolean onClose(PhotoView photoView)
		{
			mGalleryInstance.callback.navigateGalleryOrFinish();
			return true;
		}
	};
	
	private static class PlayShape extends Shape
	{
		private final Path mPath = new Path();
		
		@Override
		public void draw(Canvas canvas, Paint paint)
		{
			float width = getWidth();
			float height = getHeight();
			float size = Math.min(width, height);
			int radius = (int) (size * 38f / 48f / 2f + 0.5f);
			paint.setStrokeWidth(size / 48f * 4f);
			paint.setStyle(Paint.Style.STROKE);
			paint.setColor(Color.WHITE);
			canvas.drawCircle(width / 2f, height / 2f, radius, paint);
			paint.setStyle(Paint.Style.FILL);
			Path path = mPath;
			float side = size / 48f * 16f;
			float altitude = (float) (side * Math.sqrt(3f) / 2f);
			path.moveTo(width / 2f + altitude * 2f / 3f, height / 2f);
			path.lineTo(width / 2f - altitude  / 3f, height / 2f - side / 2f);
			path.lineTo(width / 2f - altitude  / 3f, height / 2f + side / 2f);
			path.close();
			canvas.drawPath(path, paint);
			path.rewind();
		}
	}
	
	private class PagerAdapter implements PhotoViewPager.Adapter
	{
		private final LayoutInflater mInflater;
		private final ArrayList<GalleryItem> mGalleryItems;
		
		private int mWaitBeforeVideo = 0;
		
		public PagerAdapter(ArrayList<GalleryItem> galleryItems)
		{
			mInflater = LayoutInflater.from(mGalleryInstance.context);
			mGalleryItems = galleryItems;
		}
		
		public void setWaitBeforeNextVideo(int waitBeforeVideo)
		{
			mWaitBeforeVideo = waitBeforeVideo;
		}
		
		@Override
		public View onCreateView(Context context, ViewGroup parent)
		{
			View view = mInflater.inflate(R.layout.list_item_gallery, parent, false);
			PagerInstance.ViewHolder holder = new PagerInstance.ViewHolder();
			holder.photoView = (PhotoView) view.findViewById(R.id.photoView);
			holder.surfaceParent = (FrameLayout) view.findViewById(R.id.surfaceParent);
			holder.errorView = view.findViewById(R.id.error);
			holder.errorText = (TextView) view.findViewById(R.id.error_text);
			holder.progressBar = (CircularProgressBar) view.findViewById(android.R.id.progress);
			holder.playButton = view.findViewById(R.id.play);
			holder.playButton.setBackground(new ShapeDrawable(new PlayShape()));
			holder.photoView.setListener(mPhotoViewListener);
			view.setTag(holder);
			return view;
		}
		
		@Override
		public PhotoView getPhotoView(View view)
		{
			return ((PagerInstance.ViewHolder) view.getTag()).photoView;
		}
		
		private void applySideViewData(PagerInstance.ViewHolder holder, int index, boolean active)
		{
			GalleryItem galleryItem = mGalleryItems.get(index);
			holder.playButton.setVisibility(View.GONE);
			holder.errorView.setVisibility(View.GONE);
			boolean hasValidImage = holder.galleryItem == galleryItem && holder.fullLoaded &&
					!galleryItem.isVideo(mGalleryInstance.locator);
			if (hasValidImage)
			{
				if (holder.animatedPngDecoder != null || holder.gifDecoder != null)
				{
					holder.recyclePhotoView();
					holder.fullLoaded = false;
					hasValidImage = false;
				}
				else
				{
					if (holder.decoderDrawable != null) holder.decoderDrawable.setEnabled(active);
					holder.photoView.resetScale();
				}
			}
			if (!hasValidImage)
			{
				holder.fullLoaded = false;
				holder.galleryItem = galleryItem;
				boolean success = presetThumbnail(holder, galleryItem, false, true);
				if (!success)
				{
					holder.recyclePhotoView();
					if (mGalleryInstance.callback.isGalleryWindow() || Preferences.isLoadThumbnails())
					{
						Uri thumbnailUri = galleryItem.getThumbnailUri(mGalleryInstance.locator);
						if (thumbnailUri != null)
						{
							ImageLoader.getInstance().loadImage(thumbnailUri, mGalleryInstance.chanName, null,
									new LoadThumbnailCallback(holder.photoView, holder, galleryItem), false);
						}
					}
				}
			}
		}
		
		private int mPreviousIndex = -1;
		
		@Override
		public void onPositionChange(PhotoViewPager view, int index, View currentView, View leftView, View rightView,
				boolean manually)
		{
			boolean mayShowThumbnailOnly = !manually && !Preferences.isVideoPlayAfterScroll();
			PagerInstance.ViewHolder holder = (PagerInstance.ViewHolder) currentView.getTag();
			if (index < mPreviousIndex) mPagerInstance.scrollingLeft = true;
			else if (index > mPreviousIndex) mPagerInstance.scrollingLeft = false;
			mPreviousIndex = index;
			mPagerInstance.leftHolder = leftView != null ? (PagerInstance.ViewHolder) leftView.getTag() : null;
			mPagerInstance.currentHolder = holder;
			mPagerInstance.rightHolder = rightView != null ? (PagerInstance.ViewHolder) rightView.getTag() : null;
			interrupt(false);
			if (mPagerInstance.leftHolder != null) applySideViewData(mPagerInstance.leftHolder, index - 1, false);
			if (mPagerInstance.rightHolder != null) applySideViewData(mPagerInstance.rightHolder, index + 1, false);
			applySideViewData(holder, index, true);
			GalleryItem galleryItem = mGalleryItems.get(index);
			if (holder.galleryItem != galleryItem || !holder.fullLoaded)
			{
				holder.galleryItem = galleryItem;
				loadImageVideo(false, mayShowThumbnailOnly, mWaitBeforeVideo);
				mWaitBeforeVideo = 0;
			}
			else
			{
				mGalleryInstance.callback.invalidateOptionsMenu();
				mGalleryInstance.callback.modifySystemUiVisibility(GalleryInstance.FLAG_LOCKED_ERROR, false);
			}
			if (galleryItem.size <= 0)
			{
				Uri uri = galleryItem.getFileUri(mGalleryInstance.locator);
				File cachedFile = CacheManager.getInstance().getMediaFile(uri, false);
				if (cachedFile != null && cachedFile.exists()) galleryItem.size = (int) cachedFile.length();
			}
			mGalleryInstance.callback.updateTitle();
			if (galleryItem.postNumber != null && mResumed && !mGalleryInstance.callback.isGalleryMode())
			{
				mGalleryInstance.callback.navigatePost(galleryItem, false);
			}
		}
		
		@Override
		public void onSwipingStateChange(PhotoViewPager view, boolean swiping)
		{
			mVideoUnit.handleSwipingContent(swiping, false);
		}
		
		public void recycleAll()
		{
			for (int i = 0; i < mViewPager.getChildCount(); i++)
			{
				PagerInstance.ViewHolder holder = (PagerInstance.ViewHolder) mViewPager.getChildAt(i).getTag();
				holder.recyclePhotoView();
				holder.fullLoaded = false;
			}
		}
	}
	
	private class LoadThumbnailCallback extends ImageLoader.Callback<PhotoView>
	{
		private final PagerInstance.ViewHolder mHolder;
		private final GalleryItem mGalleryItem;
		
		public LoadThumbnailCallback(PhotoView view, PagerInstance.ViewHolder holder, GalleryItem galleryItem)
		{
			super(view);
			mHolder = holder;
			mGalleryItem = galleryItem;
		}
		
		@Override
		public void onSuccess(Bitmap bitmap)
		{
			if (!mHolder.photoView.hasImage() && mHolder.galleryItem == mGalleryItem)
			{
				presetThumbnail(mHolder, mGalleryItem, false, true);
			}
		}
		
		@Override
		public void onError()
		{
			
		}
	}
}