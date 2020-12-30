package com.mishiranu.dashchan.ui.gallery;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.Shape;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import chan.content.Chan;
import chan.util.StringUtils;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.CacheManager;
import com.mishiranu.dashchan.content.ImageLoader;
import com.mishiranu.dashchan.content.NetworkObserver;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.model.GalleryItem;
import com.mishiranu.dashchan.graphics.SimpleBitmapDrawable;
import com.mishiranu.dashchan.ui.DialogMenu;
import com.mishiranu.dashchan.ui.InstanceDialog;
import com.mishiranu.dashchan.ui.SearchImageDialog;
import com.mishiranu.dashchan.util.AnimationUtils;
import com.mishiranu.dashchan.util.NavigationUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.InsetsLayout;
import com.mishiranu.dashchan.widget.PhotoView;
import com.mishiranu.dashchan.widget.PhotoViewPager;
import com.mishiranu.dashchan.widget.ViewFactory;
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
		PagerUnitViewModel viewModel = new ViewModelProvider(instance.callback).get(PagerUnitViewModel.class);
		viewModel.pagerUnit = new WeakReference<>(this);
	}

	public static class PagerUnitViewModel extends ViewModel {
		private WeakReference<PagerUnit> pagerUnit;
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

	public void onApplyWindowInsets(InsetsLayout.Insets insets) {
		videoUnit.onApplyWindowInsets(insets.left, insets.right, insets.bottom);
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
		public final boolean viewMetadata;
		public final boolean searchImage;
		public final boolean navigatePost;
		public final boolean shareFile;

		public OptionsMenuCapabilities(boolean available, boolean save, boolean refresh, boolean viewMetadata,
				boolean searchImage, boolean navigatePost, boolean shareFile) {
			this.available = available;
			this.save = save;
			this.refresh = refresh;
			this.viewMetadata = viewMetadata;
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
		boolean viewMetadata = false;
		boolean searchImage = false;
		boolean navigatePost = false;
		boolean shareFile = false;
		if (holder != null) {
			available = true;
			GalleryItem galleryItem = holder.galleryItem;
			Chan chan = Chan.get(galleryInstance.chanName);
			boolean isVideo = galleryItem.isVideo(chan);
			boolean isOpenableVideo = isVideo && galleryItem.isOpenableVideo(chan);
			boolean isVideoInitialized = isOpenableVideo && videoUnit.isInitialized();
			boolean imageHasMetadata = imageUnit.hasMetadata();
			save = holder.loadState == PagerInstance.LoadState.COMPLETE ||
					isVideo && (!isOpenableVideo || holder.loadState == PagerInstance.LoadState.ERROR);
			refresh = !isVideo || isVideoInitialized || holder.loadState == PagerInstance.LoadState.ERROR;
			viewMetadata = isVideoInitialized || imageHasMetadata;
			searchImage = galleryItem.getDisplayImageUri(chan) != null;
			navigatePost = galleryItem.postNumber != null;
			shareFile = holder.loadState == PagerInstance.LoadState.COMPLETE;
		}
		return new OptionsMenuCapabilities(available, save, refresh, viewMetadata,
				searchImage, navigatePost, shareFile);
	}

	public PagerInstance.ViewHolder getCurrentHolder() {
		return pagerInstance.currentHolder;
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
		Chan chan = Chan.get(galleryInstance.chanName);
		interrupt(false);
		holder.loadState = PagerInstance.LoadState.PREVIEW_OR_LOADING;
		galleryInstance.callback.invalidateOptionsMenu();
		CacheManager cacheManager = CacheManager.getInstance();
		if (!cacheManager.isCacheAvailable()) {
			showError(holder, galleryInstance.context.getString(R.string.cache_is_unavailable));
			return;
		}
		galleryInstance.callback.modifySystemUiVisibility(GalleryInstance.Flags.LOCKED_ERROR, false);
		holder.errorHolder.layout.setVisibility(View.GONE);
		boolean thumbnailReady = holder.photoViewThumbnail;
		if (!thumbnailReady) {
			holder.recyclePhotoView();
			thumbnailReady = presetThumbnail(holder, reload);
		}
		boolean isImage = galleryItem.isImage(chan);
		boolean isVideo = galleryItem.isVideo(chan);
		boolean isOpenableVideo = isVideo && galleryItem.isOpenableVideo(chan);
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
		Uri uri = galleryItem.getFileUri(chan);
		File cachedFile = cacheManager.getMediaFile(uri, true);
		if (cachedFile == null) {
			showError(holder, galleryInstance.context.getString(R.string.cache_is_unavailable));
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
				Chan chan = Chan.get(galleryInstance.chanName);
				boolean setImage = awaitImmediate || holder.galleryItem != null && !holder.photoView.hasImage() &&
						key.equals(CacheManager.getInstance().getCachedFileKey(holder.galleryItem
								.getThumbnailUri(chan)));
				if (setImage) {
					holder.recyclePhotoView();
					holder.simpleBitmapDrawable = new SimpleBitmapDrawable(bitmap,
							holder.galleryItem.width, holder.galleryItem.height, false);
					boolean fitScreen = holder.galleryItem.isVideo(chan);
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
		Chan chan = Chan.get(galleryInstance.chanName);
		Uri uri = holder.galleryItem.getThumbnailUri(chan);
		if (uri != null && holder.galleryItem.width > 0 && holder.galleryItem.height > 0) {
			target.awaitImmediate = true;
			target.keepScale = keepScale;
			try {
				boolean allowLoad = galleryInstance.callback.isGalleryWindow() ||
						Preferences.getLoadThumbnails().isNetworkAvailable(NetworkObserver.getInstance());
				return ImageLoader.getInstance().loadImage(chan, uri, null, !allowLoad, target);
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
			galleryInstance.callback.modifySystemUiVisibility(GalleryInstance.Flags.LOCKED_ERROR, true);
			holder.photoView.clearInitialScaleAnimationData();
			holder.recyclePhotoView();
			interrupt(false);
			holder.errorHolder.layout.setVisibility(View.VISIBLE);
			holder.errorHolder.text.setText(!StringUtils.isEmpty(message) ? message
					: galleryInstance.context.getString(R.string.unknown_error));
			holder.progressBar.cancelVisibilityTransient();
			holder.loadState = PagerInstance.LoadState.ERROR;
			galleryInstance.callback.invalidateOptionsMenu();
		}
	}

	private final PhotoView.Listener photoViewListener = new PhotoView.Listener() {
		@Override
		public void onClick(PhotoView photoView, boolean image, float x, float y) {
			GalleryItem galleryItem = pagerInstance.currentHolder.galleryItem;
			Chan chan = Chan.get(galleryInstance.chanName);
			View playButton = pagerInstance.currentHolder.playButton;
			if (playButton.getVisibility() == View.VISIBLE && galleryItem.isVideo(chan)
					&& !videoUnit.isCreated()) {
				int centerX = playButton.getLeft() + playButton.getWidth() / 2;
				int centerY = playButton.getTop() + playButton.getHeight() / 2;
				int size = Math.min(playButton.getWidth(), playButton.getHeight());
				float distance = (float) Math.sqrt((centerX - x) * (centerX - x) + (centerY - y) * (centerY - y));
				if (distance <= size / 3f * 2f) {
					if (!galleryItem.isOpenableVideo(chan)) {
						NavigationUtils.handleUri(galleryInstance.callback.getWindow().getContext(),
								galleryInstance.chanName, galleryItem.getFileUri(chan),
								NavigationUtils.BrowserType.EXTERNAL);
					} else {
						loadImageVideo(false, false, 0);
					}
					return;
				}
			}
			if (image) {
				galleryInstance.callback.toggleSystemUIVisibility(GalleryInstance.Flags.LOCKED_USER);
			} else {
				galleryInstance.callback.navigateGalleryOrFinish(false);
			}
		}

		@Override
		public void onLongClick(PhotoView photoView, float x, float y) {
			displayPopupMenu(galleryInstance.callback.getChildFragmentManager());
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
			FrameLayout view = (FrameLayout) LayoutInflater.from(galleryInstance.context)
					.inflate(R.layout.list_item_gallery, parent, false);
			PagerInstance.ViewHolder holder = new PagerInstance.ViewHolder();
			holder.photoView = view.findViewById(R.id.photo_view);
			holder.surfaceParent = view.findViewById(R.id.surface_parent);
			holder.errorHolder = ViewFactory.createErrorLayout(view);
			holder.progressBar = view.findViewById(android.R.id.progress);
			holder.playButton = view.findViewById(R.id.play);
			holder.playButton.setBackground(new ShapeDrawable(new PlayShape()));
			holder.photoView.setListener(photoViewListener);
			view.addView(holder.errorHolder.layout);
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
			holder.errorHolder.layout.setVisibility(View.GONE);
			if (!active) {
				holder.progressBar.setVisible(false, true);
			}
			boolean hasValidImage = holder.galleryItem == galleryItem &&
					holder.loadState == PagerInstance.LoadState.COMPLETE &&
					!galleryItem.isVideo(Chan.get(galleryInstance.chanName));
			if (hasValidImage) {
				if (holder.animatedPngDecoder != null || holder.gifDecoder != null) {
					holder.recyclePhotoView();
					hasValidImage = false;
				} else {
					if (holder.decoderDrawable != null) {
						holder.decoderDrawable.setEnabled(active);
					}
					holder.photoView.resetScale();
				}
			}
			if (!hasValidImage) {
				holder.loadState = PagerInstance.LoadState.PREVIEW_OR_LOADING;
				holder.galleryItem = galleryItem;
				holder.mediaSummary = new PagerInstance.MediaSummary(galleryItem);
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
			if (holder.galleryItem != galleryItem || holder.loadState != PagerInstance.LoadState.COMPLETE) {
				holder.galleryItem = galleryItem;
				holder.mediaSummary = new PagerInstance.MediaSummary(galleryItem);
				loadImageVideo(false, mayShowThumbnailOnly, waitBeforeVideo);
				waitBeforeVideo = 0;
			} else {
				galleryInstance.callback.invalidateOptionsMenu();
				galleryInstance.callback.modifySystemUiVisibility(GalleryInstance.Flags.LOCKED_ERROR, false);
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
				holder.loadState = PagerInstance.LoadState.PREVIEW_OR_LOADING;
			}
		}
	}

	private static final String TAG_POPUP_MENU = PagerUnit.class.getName() + ":PopupMenu";

	private DialogMenu buildPopupMenu() {
		GalleryItem galleryItem = pagerInstance.currentHolder.galleryItem;
		OptionsMenuCapabilities capabilities = obtainOptionsMenuCapabilities();
		if (capabilities != null && capabilities.available) {
			Chan chan = Chan.get(galleryInstance.chanName);
			Context context = galleryInstance.callback.getWindow().getContext();
			DialogMenu dialogMenu = new DialogMenu(context);
			dialogMenu.setTitle(!StringUtils.isEmpty(galleryItem.originalName)
					? galleryItem.originalName : galleryItem.getFileName(chan));
			if (!galleryInstance.callback.isSystemUiVisible()) {
				if (capabilities.save) {
					dialogMenu.add(R.string.save, () -> galleryInstance.callback
							.downloadGalleryItem(galleryItem));
				}
				if (capabilities.refresh) {
					dialogMenu.add(R.string.refresh, this::refreshCurrent);
				}
			}
			if (capabilities.viewMetadata) {
				dialogMenu.add(R.string.metadata, () -> {
					if (galleryItem.isImage(chan)) {
						imageUnit.viewMetadata();
					} else if (galleryItem.isVideo(chan)) {
						videoUnit.viewMetadata();
					}
				});
			}
			if (capabilities.searchImage) {
				dialogMenu.add(R.string.search_image, () -> {
					videoUnit.forcePause();
					new SearchImageDialog(galleryInstance.chanName, galleryItem.getDisplayImageUri(chan))
							.show(galleryInstance.callback.getChildFragmentManager(), null);
				});
			}
			if (galleryInstance.callback.isAllowNavigatePostManually(true) && capabilities.navigatePost) {
				dialogMenu.add(R.string.go_to_post, () -> galleryInstance.callback
						.navigatePost(galleryItem, true, true));
			}
			dialogMenu.add(R.string.copy_link, () -> StringUtils.copyToClipboard(context,
					galleryItem.getFileUri(chan).toString()));
			dialogMenu.add(R.string.share_link, () -> {
				videoUnit.forcePause();
				NavigationUtils.shareLink(context, null, galleryItem.getFileUri(chan));
			});
			if (capabilities.shareFile) {
				dialogMenu.add(R.string.share_file, () -> {
					videoUnit.forcePause();
					Uri uri = galleryItem.getFileUri(chan);
					File file = CacheManager.getInstance().getMediaFile(uri, false);
					if (file == null) {
						ClickableToast.show(R.string.cache_is_unavailable);
					} else {
						NavigationUtils.shareFile(context, file, galleryItem.getFileName(chan));
					}
				});
			}
			return dialogMenu;
		}
		return null;
	}

	private static void displayPopupMenu(FragmentManager fragmentManager) {
		new InstanceDialog(fragmentManager, TAG_POPUP_MENU, provider -> {
			PagerUnitViewModel viewModel = new ViewModelProvider(provider.getParentFragment())
					.get(PagerUnitViewModel.class);
			PagerUnit pagerUnit = viewModel.pagerUnit.get();
			DialogMenu dialogMenu = pagerUnit.buildPopupMenu();
			if (dialogMenu != null) {
				return dialogMenu.create();
			} else {
				return provider.createDismissDialog();
			}
		});
	}

	public void invalidatePopupMenu() {
		InstanceDialog instanceDialog = (InstanceDialog) galleryInstance.callback
				.getChildFragmentManager().findFragmentByTag(TAG_POPUP_MENU);
		if (instanceDialog != null) {
			AlertDialog dialog = (AlertDialog) instanceDialog.getDialog();
			if (dialog != null) {
				DialogMenu dialogMenu = buildPopupMenu();
				if (dialogMenu != null) {
					dialogMenu.update(dialog);
				} else {
					instanceDialog.dismiss();
				}
			}
		}
	}
}
