package com.mishiranu.dashchan.ui.gallery;

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
import chan.util.StringUtils;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.CacheManager;
import com.mishiranu.dashchan.content.ImageLoader;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.model.GalleryItem;
import com.mishiranu.dashchan.graphics.SimpleBitmapDrawable;
import com.mishiranu.dashchan.util.AnimationUtils;
import com.mishiranu.dashchan.util.DialogMenu;
import com.mishiranu.dashchan.util.NavigationUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ToastUtils;
import com.mishiranu.dashchan.widget.PhotoView;
import com.mishiranu.dashchan.widget.PhotoViewPager;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.List;

public class PagerUnit implements PagerInstance.Callback {
	private final GalleryInstance galleryInstance;
	private final PagerInstance pagerInstance;

	private final ImageUnit imageUnit;
	private final VideoUnit videoUnit;

	private final FrameLayout viewPagerParent;
	private final PhotoViewPager viewPager;
	private final PagerAdapter pagerAdapter;

	public PagerUnit(GalleryInstance instance) {
		galleryInstance = instance;
		pagerInstance = new PagerInstance(instance, this);
		imageUnit = new ImageUnit(pagerInstance);
		videoUnit = new VideoUnit(pagerInstance);
		float density = ResourceUtils.obtainDensity(instance.context);
		viewPagerParent = new FrameLayout(instance.context);
		pagerAdapter = new PagerAdapter(instance.galleryItems);
		pagerAdapter.setWaitBeforeNextVideo(PhotoView.INITIAL_SCALE_TRANSITION_TIME + 100);
		viewPager = new PhotoViewPager(instance.context, pagerAdapter);
		viewPager.setInnerPadding((int) (16f * density));
		viewPager.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.MATCH_PARENT));
		viewPagerParent.addView(viewPager, FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.MATCH_PARENT);
		viewPager.setCount(instance.galleryItems.size());
	}

	public View getView() {
		return viewPagerParent;
	}

	public void addAndInitViews(FrameLayout frameLayout, int initialPosition) {
		videoUnit.addViews(frameLayout);
		viewPager.setCurrentIndex(Math.max(initialPosition, 0));
	}

	public void onViewsCreated(int[] imageViewPosition) {
		if (!galleryInstance.callback.isGalleryWindow() && imageViewPosition != null) {
			View view = viewPager.getCurrentView();
			if (view != null) {
				int[] location = new int[2];
				view.getLocationOnScreen(location);
				PagerInstance.ViewHolder holder = (PagerInstance.ViewHolder) view.getTag();
				if (holder.photoView.hasImage()) {
					holder.photoView.setInitialScaleAnimationData(imageViewPosition, Preferences.isCutThumbnails());
				}
			}
		}
	}

	private boolean resumed = false;

	public void onResume() {
		resumed = true;
		videoUnit.onResume();
	}

	public void onPause() {
		resumed = false;
		videoUnit.onPause();
	}

	public int getCurrentIndex() {
		return viewPager.getCurrentIndex();
	}

	public void onApplyWindowPaddings(Rect rect) {
		videoUnit.onApplyWindowPaddings(rect);
	}

	public void invalidateControlsVisibility() {
		videoUnit.invalidateControlsVisibility();
	}

	public void onBackToGallery() {
		videoUnit.showHideVideoView(false);
	}

	private static final float PAGER_SCALE = 0.9f;

	private boolean galleryMode = false;
	private boolean hasFocus = true;

	private void updateActive() {
		viewPager.setActive(!galleryMode && hasFocus);
	}

	public void setHasFocus(boolean hasFocus) {
		this.hasFocus = hasFocus;
		updateActive();
	}

	public void switchMode(boolean galleryMode, int duration) {
		this.galleryMode = galleryMode;
		updateActive();
		if (galleryMode) {
			interrupt(true);
			pagerInstance.leftHolder = null;
			pagerInstance.currentHolder = null;
			pagerInstance.rightHolder = null;
			if (duration > 0) {
				viewPager.setAlpha(1f);
				viewPager.setScaleX(1f);
				viewPager.setScaleY(1f);
				viewPager.animate().alpha(0f).scaleX(PAGER_SCALE).scaleY(PAGER_SCALE).setDuration(duration)
						.setListener(new AnimationUtils.VisibilityListener(viewPager, View.GONE)).start();
			} else {
				viewPager.setVisibility(View.GONE);
			}
		} else {
			viewPager.setVisibility(View.VISIBLE);
			if (duration > 0) {
				viewPager.setAlpha(0f);
				viewPager.setScaleX(PAGER_SCALE);
				viewPager.setScaleY(PAGER_SCALE);
				viewPager.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(duration).setListener(null).start();
			}
		}
	}

	public void navigatePageFromList(int position, int duration) {
		pagerAdapter.setWaitBeforeNextVideo(duration);
		viewPager.setCurrentIndex(position);
	}

	public void onConfigurationChanged(Configuration newConfig) {
		videoUnit.onConfigurationChanged(newConfig);
	}

	public void refreshCurrent() {
		if (pagerInstance.currentHolder != null) {
			loadImageVideo(true, false, 0);
		}
	}

	public static class OptionsMenuCapabilities {
		public final boolean available;
		public final boolean save;
		public final boolean refresh;
		public final boolean viewTechnicalInfo;
		public final boolean searchImage;
		public final boolean navigatePost;
		public final boolean shareFile;

		public OptionsMenuCapabilities(boolean available, boolean save, boolean refresh, boolean viewTechnicalInfo,
				boolean searchImage, boolean navigatePost, boolean shareFile) {
			this.available = available;
			this.save = save;
			this.refresh = refresh;
			this.viewTechnicalInfo = viewTechnicalInfo;
			this.searchImage = searchImage;
			this.navigatePost = navigatePost;
			this.shareFile = shareFile;
		}
	}

	public OptionsMenuCapabilities obtainOptionsMenuCapabilities() {
		PagerInstance.ViewHolder holder = pagerInstance.currentHolder;
		boolean available = false;
		boolean save = false;
		boolean refresh = false;
		boolean viewTechnicalInfo = false;
		boolean searchImage = false;
		boolean navigatePost = false;
		boolean shareFile = false;
		if (holder != null) {
			available = true;
			GalleryItem galleryItem = holder.galleryItem;
			boolean fullLoaded = holder.fullLoaded;
			boolean isVideo = galleryItem.isVideo(galleryInstance.locator);
			boolean isOpenableVideo = isVideo && galleryItem.isOpenableVideo(galleryInstance.locator);
			boolean isVideoInitialized = isOpenableVideo && videoUnit.isInitialized();
			boolean imageHasMetadata = imageUnit.hasMetadata();
			save = fullLoaded || isVideo && !isOpenableVideo;
			if (!save && isOpenableVideo) {
				File cachedFile = CacheManager.getInstance().getMediaFile(galleryItem
						.getFileUri(galleryInstance.locator), false);
				if (cachedFile != null && cachedFile.exists()) {
					save = true;
				}
			}
			refresh = !isOpenableVideo && !isVideo || isVideoInitialized;
			viewTechnicalInfo = isVideoInitialized || imageHasMetadata;
			searchImage = galleryItem.getDisplayImageUri(galleryInstance.locator) != null;
			navigatePost = galleryItem.postNumber != null;
			shareFile = fullLoaded;
		}
		return new OptionsMenuCapabilities(available, save, refresh, viewTechnicalInfo,
				searchImage, navigatePost, shareFile);
	}

	public GalleryItem getCurrentGalleryItem() {
		return pagerInstance.currentHolder != null ? pagerInstance.currentHolder.galleryItem : null;
	}

	private void interrupt(boolean force) {
		imageUnit.interrupt(force);
		videoUnit.interrupt();
	}

	public void onFinish() {
		PagerInstance.ViewHolder[] holders = {pagerInstance.leftHolder,
				pagerInstance.currentHolder, pagerInstance.rightHolder};
		for (PagerInstance.ViewHolder holder : holders) {
			if (holder != null && holder.thumbnailTarget != null) {
				ImageLoader.getInstance().cancel(holder.thumbnailTarget);
			}
		}
		interrupt(true);
		viewPager.postDelayed(() -> {
			pagerAdapter.recycleAll();
			System.gc();
		}, 200);
	}

	private void loadImageVideo(final boolean reload, boolean mayShowThumbnailOnly, int waitBeforeVideo) {
		PagerInstance.ViewHolder holder = pagerInstance.currentHolder;
		if (holder == null) {
			return;
		}
		GalleryItem galleryItem = holder.galleryItem;
		interrupt(false);
		holder.fullLoaded = false;
		galleryInstance.callback.invalidateOptionsMenu();
		CacheManager cacheManager = CacheManager.getInstance();
		if (!cacheManager.isCacheAvailable()) {
			showError(holder, galleryInstance.context.getString(R.string.message_cache_unavailable));
			return;
		}
		galleryInstance.callback.modifySystemUiVisibility(GalleryInstance.FLAG_LOCKED_ERROR, false);
		holder.errorView.setVisibility(View.GONE);
		boolean thumbnailReady = holder.photoViewThumbnail;
		if (!thumbnailReady) {
			holder.recyclePhotoView();
			thumbnailReady = presetThumbnail(holder, reload);
		}
		boolean isImage = galleryItem.isImage(galleryInstance.locator);
		boolean isVideo = galleryItem.isVideo(galleryInstance.locator);
		boolean isOpenableVideo = isVideo && galleryItem.isOpenableVideo(galleryInstance.locator);
		if (waitBeforeVideo > 0 && thumbnailReady && isOpenableVideo && !mayShowThumbnailOnly) {
			viewPagerParent.postDelayed(() -> loadImageVideo(reload, false, 0), waitBeforeVideo);
			return;
		}
		if (isVideo && !isOpenableVideo || isOpenableVideo && mayShowThumbnailOnly) {
			holder.playButton.setVisibility(View.VISIBLE);
			holder.photoView.setDrawDimForCurrentImage(true);
			return;
		} else {
			holder.playButton.setVisibility(View.GONE);
			holder.photoView.setDrawDimForCurrentImage(false);
		}
		holder.playButton.setVisibility(View.GONE);
		Uri uri = galleryItem.getFileUri(galleryInstance.locator);
		File cachedFile = cacheManager.getMediaFile(uri, true);
		if (cachedFile == null) {
			showError(holder, galleryInstance.context.getString(R.string.message_cache_unavailable));
		} else if (isImage) {
			imageUnit.applyImage(uri, cachedFile, reload);
		} else if (isVideo) {
			imageUnit.interrupt(true);
			videoUnit.applyVideo(uri, cachedFile, reload);
		}
	}

	private static class PageTarget extends ImageLoader.Target {
		public final WeakReference<GalleryInstance> galleryInstance;
		public final WeakReference<PagerInstance.ViewHolder> holder;

		public boolean awaitImmediate;
		public boolean keepScale;

		public PageTarget(GalleryInstance galleryInstance, PagerInstance.ViewHolder holder) {
			this.galleryInstance = new WeakReference<>(galleryInstance);
			this.holder = new WeakReference<>(holder);
		}

		@Override
		public void onResult(String key, Bitmap bitmap, boolean error, boolean instantly) {
			GalleryInstance galleryInstance = this.galleryInstance.get();
			PagerInstance.ViewHolder holder = this.holder.get();
			if (galleryInstance != null && holder != null && bitmap != null) {
				boolean setImage = awaitImmediate || holder.galleryItem != null && !holder.photoView.hasImage() &&
						key.equals(CacheManager.getInstance().getCachedFileKey(holder.galleryItem
								.getThumbnailUri(galleryInstance.locator)));
				if (setImage) {
					holder.recyclePhotoView();
					holder.simpleBitmapDrawable = new SimpleBitmapDrawable(bitmap,
							holder.galleryItem.width, holder.galleryItem.height, false);
					boolean fitScreen = holder.galleryItem.isVideo(galleryInstance.locator);
					boolean keepScale = this.keepScale && !fitScreen;
					holder.photoView.setImage(holder.simpleBitmapDrawable, bitmap.hasAlpha(), fitScreen, keepScale);
					holder.photoViewThumbnail = true;
				}
			}
		}
	}

	private boolean presetThumbnail(PagerInstance.ViewHolder holder, boolean keepScale) {
		PageTarget target = (PageTarget) holder.thumbnailTarget;
		if (target == null) {
			target = new PageTarget(galleryInstance, holder);
			holder.thumbnailTarget = target;
		}
		if (holder.galleryItem == null) {
			return false;
		}
		Uri uri = holder.galleryItem.getThumbnailUri(galleryInstance.locator);
		if (uri != null && holder.galleryItem.width > 0 && holder.galleryItem.height > 0) {
			target.awaitImmediate = true;
			target.keepScale = keepScale;
			try {
				boolean allowLoad = galleryInstance.callback.isGalleryWindow() || Preferences.isLoadThumbnails();
				return ImageLoader.getInstance().loadImage(galleryInstance.chanName, uri, null, !allowLoad, target);
			} finally {
				target.awaitImmediate = false;
				target.keepScale = false;
			}
		}
		return false;
	}

	@Override
	public void showError(PagerInstance.ViewHolder holder, String message) {
		if (holder == pagerInstance.currentHolder) {
			galleryInstance.callback.modifySystemUiVisibility(GalleryInstance.FLAG_LOCKED_ERROR, true);
			holder.photoView.clearInitialScaleAnimationData();
			holder.recyclePhotoView();
			interrupt(false);
			holder.errorView.setVisibility(View.VISIBLE);
			holder.errorText.setText(!StringUtils.isEmpty(message) ? message
					: galleryInstance.context.getString(R.string.message_unknown_error));
			holder.progressBar.cancelVisibilityTransient();
		}
	}

	private final PhotoView.Listener photoViewListener = new PhotoView.Listener() {
		@Override
		public void onClick(PhotoView photoView, boolean image, float x, float y) {
			GalleryItem galleryItem = pagerInstance.currentHolder.galleryItem;
			View playButton = pagerInstance.currentHolder.playButton;
			if (playButton.getVisibility() == View.VISIBLE && galleryItem.isVideo(galleryInstance.locator)
					&& !videoUnit.isCreated()) {
				int centerX = playButton.getLeft() + playButton.getWidth() / 2;
				int centerY = playButton.getTop() + playButton.getHeight() / 2;
				int size = Math.min(playButton.getWidth(), playButton.getHeight());
				float distance = (float) Math.sqrt((centerX - x) * (centerX - x) + (centerY - y) * (centerY - y));
				if (distance <= size / 3f * 2f) {
					if (!galleryItem.isOpenableVideo(galleryInstance.locator)) {
						NavigationUtils.handleUri(galleryInstance.callback.getWindow().getContext(),
								galleryInstance.chanName, galleryItem.getFileUri(galleryInstance.locator),
								NavigationUtils.BrowserType.EXTERNAL);
					} else {
						loadImageVideo(false, false, 0);
					}
					return;
				}
			}
			if (image) {
				galleryInstance.callback.toggleSystemUIVisibility(GalleryInstance.FLAG_LOCKED_USER);
			} else {
				galleryInstance.callback.navigateGalleryOrFinish(false);
			}
		}

		@Override
		public void onLongClick(PhotoView photoView, float x, float y) {
			displayPopupMenu();
		}

		private boolean swiping = false;

		@Override
		public void onVerticalSwipe(PhotoView photoView, boolean down, float value) {
			boolean swiping = value != 0f;
			if (this.swiping != swiping) {
				this.swiping = swiping;
				videoUnit.handleSwipingContent(swiping, true);
			}
			galleryInstance.callback.modifyVerticalSwipeState(down, value);
		}

		@Override
		public boolean onClose(PhotoView photoView, boolean down) {
			galleryInstance.callback.navigateGalleryOrFinish(down);
			return true;
		}
	};

	private static class PlayShape extends Shape {
		private final Path path = new Path();

		@Override
		public void draw(Canvas canvas, Paint paint) {
			float width = getWidth();
			float height = getHeight();
			float size = Math.min(width, height);
			int radius = (int) (size * 38f / 48f / 2f + 0.5f);
			paint.setStrokeWidth(size / 48f * 4f);
			paint.setStyle(Paint.Style.STROKE);
			paint.setColor(Color.WHITE);
			canvas.drawCircle(width / 2f, height / 2f, radius, paint);
			paint.setStyle(Paint.Style.FILL);
			Path path = this.path;
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

	private class PagerAdapter implements PhotoViewPager.Adapter {
		private final List<GalleryItem> galleryItems;

		private int waitBeforeVideo = 0;

		public PagerAdapter(List<GalleryItem> galleryItems) {
			this.galleryItems = galleryItems;
		}

		public void setWaitBeforeNextVideo(int waitBeforeVideo) {
			this.waitBeforeVideo = waitBeforeVideo;
		}

		@Override
		public View onCreateView(ViewGroup parent) {
			View view = LayoutInflater.from(galleryInstance.context).inflate(R.layout.list_item_gallery,
					parent, false);
			PagerInstance.ViewHolder holder = new PagerInstance.ViewHolder();
			holder.photoView = view.findViewById(R.id.photo_view);
			holder.surfaceParent = view.findViewById(R.id.surface_parent);
			holder.errorView = view.findViewById(R.id.error);
			holder.errorText = view.findViewById(R.id.error_text);
			holder.progressBar = view.findViewById(android.R.id.progress);
			holder.playButton = view.findViewById(R.id.play);
			holder.playButton.setBackground(new ShapeDrawable(new PlayShape()));
			holder.photoView.setListener(photoViewListener);
			view.setTag(holder);
			return view;
		}

		@Override
		public PhotoView getPhotoView(View view) {
			return ((PagerInstance.ViewHolder) view.getTag()).photoView;
		}

		private void applySideViewData(PagerInstance.ViewHolder holder, int index, boolean active) {
			GalleryItem galleryItem = galleryItems.get(index);
			holder.playButton.setVisibility(View.GONE);
			holder.errorView.setVisibility(View.GONE);
			if (!active) {
				holder.progressBar.setVisible(false, true);
			}
			boolean hasValidImage = holder.galleryItem == galleryItem && holder.fullLoaded &&
					!galleryItem.isVideo(galleryInstance.locator);
			if (hasValidImage) {
				if (holder.animatedPngDecoder != null || holder.gifDecoder != null) {
					holder.recyclePhotoView();
					holder.fullLoaded = false;
					hasValidImage = false;
				} else {
					if (holder.decoderDrawable != null) {
						holder.decoderDrawable.setEnabled(active);
					}
					holder.photoView.resetScale();
				}
			}
			if (!hasValidImage) {
				holder.fullLoaded = false;
				holder.galleryItem = galleryItem;
				boolean success = presetThumbnail(holder, false);
				if (!success) {
					holder.recyclePhotoView();
				}
			}
		}

		private int previousIndex = -1;

		@Override
		public void onPositionChange(PhotoViewPager view, int index, View centerView, View leftView, View rightView,
				boolean manually) {
			boolean mayShowThumbnailOnly = !manually && !Preferences.isVideoPlayAfterScroll();
			PagerInstance.ViewHolder holder = (PagerInstance.ViewHolder) centerView.getTag();
			if (index < previousIndex) {
				pagerInstance.scrollingLeft = true;
			} else if (index > previousIndex) {
				pagerInstance.scrollingLeft = false;
			}
			previousIndex = index;
			pagerInstance.leftHolder = leftView != null ? (PagerInstance.ViewHolder) leftView.getTag() : null;
			pagerInstance.currentHolder = holder;
			pagerInstance.rightHolder = rightView != null ? (PagerInstance.ViewHolder) rightView.getTag() : null;
			interrupt(false);
			if (pagerInstance.leftHolder != null) {
				applySideViewData(pagerInstance.leftHolder, index - 1, false);
			}
			if (pagerInstance.rightHolder != null) {
				applySideViewData(pagerInstance.rightHolder, index + 1, false);
			}
			applySideViewData(holder, index, true);
			GalleryItem galleryItem = galleryItems.get(index);
			if (holder.galleryItem != galleryItem || !holder.fullLoaded) {
				holder.galleryItem = galleryItem;
				loadImageVideo(false, mayShowThumbnailOnly, waitBeforeVideo);
				waitBeforeVideo = 0;
			} else {
				galleryInstance.callback.invalidateOptionsMenu();
				galleryInstance.callback.modifySystemUiVisibility(GalleryInstance.FLAG_LOCKED_ERROR, false);
			}
			if (galleryItem.size <= 0) {
				Uri uri = galleryItem.getFileUri(galleryInstance.locator);
				File cachedFile = CacheManager.getInstance().getMediaFile(uri, false);
				if (cachedFile != null && cachedFile.exists()) {
					galleryItem.size = (int) cachedFile.length();
				}
			}
			galleryInstance.callback.updateTitle();
			if (galleryItem.postNumber != null && resumed && !galleryInstance.callback.isGalleryMode()) {
				galleryInstance.callback.navigatePost(galleryItem, false, false);
			}
		}

		@Override
		public void onSwipingStateChange(PhotoViewPager view, boolean swiping) {
			videoUnit.handleSwipingContent(swiping, false);
		}

		public void recycleAll() {
			for (int i = 0; i < viewPager.getChildCount(); i++) {
				PagerInstance.ViewHolder holder = (PagerInstance.ViewHolder) viewPager.getChildAt(i).getTag();
				holder.recyclePhotoView();
				holder.fullLoaded = false;
			}
		}
	}

	private static final int POPUP_MENU_SAVE = 0;
	private static final int POPUP_MENU_REFRESH = 1;
	private static final int POPUP_MENU_TECHNICAL_INFO = 2;
	private static final int POPUP_MENU_SEARCH_IMAGE = 3;
	private static final int POPUP_MENU_NAVIGATE_POST = 4;
	private static final int POPUP_MENU_COPY_LINK = 5;
	private static final int POPUP_MENU_SHARE_LINK = 6;
	private static final int POPUP_MENU_SHARE_FILE = 7;

	private DialogMenu currentPopupDialogMenu;

	private void displayPopupMenu() {
		DialogMenu dialogMenu = new DialogMenu(galleryInstance.callback.getWindow().getContext(), id -> {
			GalleryItem galleryItem = pagerInstance.currentHolder.galleryItem;
			switch (id) {
				case POPUP_MENU_SAVE: {
					galleryInstance.callback.downloadGalleryItem(galleryItem);
					break;
				}
				case POPUP_MENU_REFRESH: {
					refreshCurrent();
					break;
				}
				case POPUP_MENU_TECHNICAL_INFO: {
					if (galleryItem.isImage(galleryInstance.locator)) {
						imageUnit.viewTechnicalInfo();
					} else if (galleryItem.isVideo(galleryInstance.locator)) {
						videoUnit.viewTechnicalInfo();
					}
					break;
				}
				case POPUP_MENU_SEARCH_IMAGE: {
					videoUnit.forcePause();
					NavigationUtils.searchImage(galleryInstance.callback.getWindow().getContext(),
							galleryInstance.callback.getConfigurationLock(), galleryInstance.chanName,
							galleryItem.getDisplayImageUri(galleryInstance.locator));
					break;
				}
				case POPUP_MENU_NAVIGATE_POST: {
					galleryInstance.callback.navigatePost(galleryItem, true, true);
					break;
				}
				case POPUP_MENU_COPY_LINK: {
					StringUtils.copyToClipboard(galleryInstance.context,
							galleryItem.getFileUri(galleryInstance.locator).toString());
					break;
				}
				case POPUP_MENU_SHARE_LINK: {
					videoUnit.forcePause();
					NavigationUtils.shareLink(galleryInstance.callback.getWindow().getContext(), null,
							galleryItem.getFileUri(galleryInstance.locator));
					break;
				}
				case POPUP_MENU_SHARE_FILE: {
					videoUnit.forcePause();
					Uri uri = galleryItem.getFileUri(galleryInstance.locator);
					File file = CacheManager.getInstance().getMediaFile(uri, false);
					if (file == null) {
						ToastUtils.show(galleryInstance.callback.getWindow().getContext(),
								R.string.message_cache_unavailable);
					} else {
						NavigationUtils.shareFile(galleryInstance.callback.getWindow().getContext(), file,
								galleryItem.getFileName(galleryInstance.locator));
					}
					break;
				}
			}
		});

		GalleryItem galleryItem = pagerInstance.currentHolder.galleryItem;
		OptionsMenuCapabilities capabilities = obtainOptionsMenuCapabilities();
		if (capabilities != null && capabilities.available) {
			dialogMenu.setTitle(galleryItem.originalName != null ? galleryItem.originalName
					: galleryItem.getFileName(galleryInstance.locator), true);
			if (!galleryInstance.callback.isSystemUiVisible()) {
				if (capabilities.save) {
					dialogMenu.addItem(POPUP_MENU_SAVE, R.string.action_save);
				}
				if (capabilities.refresh) {
					dialogMenu.addItem(POPUP_MENU_REFRESH, R.string.action_refresh);
				}
			}
			if (capabilities.viewTechnicalInfo) {
				dialogMenu.addItem(POPUP_MENU_TECHNICAL_INFO, R.string.action_technical_info);
			}
			if (capabilities.searchImage) {
				dialogMenu.addItem(POPUP_MENU_SEARCH_IMAGE, R.string.action_search_image);
			}
			if (galleryInstance.callback.isAllowNavigatePostManually(true) && capabilities.navigatePost) {
				dialogMenu.addItem(POPUP_MENU_NAVIGATE_POST, R.string.action_go_to_post);
			}
			dialogMenu.addItem(POPUP_MENU_COPY_LINK, R.string.action_copy_link);
			dialogMenu.addItem(POPUP_MENU_SHARE_LINK, R.string.action_share_link);
			if (capabilities.shareFile) {
				dialogMenu.addItem(POPUP_MENU_SHARE_FILE, R.string.action_share_file);
			}
			dialogMenu.setOnDismissListener(dialog -> {
				if (dialogMenu == currentPopupDialogMenu) {
					currentPopupDialogMenu = null;
				}
			});
			dialogMenu.show(galleryInstance.callback.getConfigurationLock());
			currentPopupDialogMenu = dialogMenu;
		}
	}

	public void invalidatePopupMenu() {
		if (currentPopupDialogMenu != null) {
			currentPopupDialogMenu.dismiss();
			displayPopupMenu();
		}
	}
}
