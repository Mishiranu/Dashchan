package com.mishiranu.dashchan.ui.gallery;

import android.app.ActionBar;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Pair;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.DialogFragment;
import chan.content.Chan;
import chan.util.CommonUtils;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.MainApplication;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.model.GalleryItem;
import com.mishiranu.dashchan.content.service.DownloadService;
import com.mishiranu.dashchan.graphics.GalleryBackgroundDrawable;
import com.mishiranu.dashchan.ui.FragmentHandler;
import com.mishiranu.dashchan.util.AnimationUtils;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.FlagUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.InsetsLayout;
import com.mishiranu.dashchan.widget.ThemeEngine;
import com.mishiranu.dashchan.widget.ViewFactory;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GalleryOverlay extends DialogFragment implements GalleryDialog.Callback, GalleryInstance.Callback {
	public enum NavigatePostMode {DISABLED, MANUALLY, ENABLED}

	private static final String EXTRA_URI = "uri";
	private static final String EXTRA_CHAN_NAME = "chanName";
	private static final String EXTRA_IMAGE_INDEX = "imageIndex";
	private static final String EXTRA_THREAD_TITLE = "threadTitle";
	private static final String EXTRA_NAVIGATE_POST_MODE = "navigatePostMode";
	private static final String EXTRA_INITIAL_GALLERY_MODE = "initialGalleryMode";

	private static final String EXTRA_POSITION = "position";
	private static final String EXTRA_SELECTED = "selected";
	private static final String EXTRA_GALLERY_WINDOW = "galleryWindow";
	private static final String EXTRA_GALLERY_MODE = "galleryMode";
	private static final String EXTRA_SYSTEM_UI_VISIBILITY = "systemUiVisibility";

	private List<GalleryItem> queuedGalleryItems;
	private WeakReference<View> queuedFromView;

	private InsetsLayout rootView;
	private GalleryInstance instance;
	private PagerUnit pagerUnit;
	private ListUnit listUnit;

	private boolean galleryWindow;
	private boolean galleryMode;
	private final boolean scrollThread = Preferences.isScrollThreadGallery();

	private Pair<CharSequence, CharSequence> titleSubtitle;
	private boolean screenOnFixed = false;
	private int systemUiVisibilityFlags = GalleryInstance.Flags.LOCKED_USER;

	private static final int ACTION_BAR_COLOR = 0xaa202020;
	private static final int BACKGROUND_COLOR = 0xf0101010;

	public GalleryOverlay() {}

	public GalleryOverlay(Uri uri) {
		this(uri, null, null, 0, null, null, NavigatePostMode.DISABLED, false);
	}

	public GalleryOverlay(String chanName, List<GalleryItem> galleryItems, int imageIndex, String threadTitle,
			View fromView, NavigatePostMode navigatePostMode, boolean initialGalleryMode) {
		this(null, chanName, galleryItems, imageIndex, threadTitle, fromView,
				navigatePostMode, initialGalleryMode);
	}

	public String getChanName() {
		return requireArguments().getString(EXTRA_CHAN_NAME);
	}

	private GalleryOverlay(Uri uri, String chanName, List<GalleryItem> galleryItems, int imageIndex, String threadTitle,
			View fromView, NavigatePostMode navigatePostMode, boolean initialGalleryMode) {
		Bundle args = new Bundle();
		args.putParcelable(EXTRA_URI, uri);
		args.putString(EXTRA_CHAN_NAME, chanName);
		args.putInt(EXTRA_IMAGE_INDEX, imageIndex);
		args.putString(EXTRA_THREAD_TITLE, threadTitle);
		args.putString(EXTRA_NAVIGATE_POST_MODE, navigatePostMode.name());
		args.putBoolean(EXTRA_INITIAL_GALLERY_MODE, initialGalleryMode);
		setArguments(args);
		this.queuedGalleryItems = galleryItems;
		this.queuedFromView = fromView != null ? new WeakReference<>(fromView) : null;
	}

	private NavigatePostMode getNavigatePostMode() {
		String name = requireArguments().getString(EXTRA_NAVIGATE_POST_MODE);
		return name != null ? NavigatePostMode.valueOf(name) : NavigatePostMode.DISABLED;
	}

	private String getThreadTitle() {
		return requireArguments().getString(EXTRA_THREAD_TITLE);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
	}

	@NonNull
	@Override
	public GalleryDialog onCreateDialog(Bundle savedInstanceState) {
		return new GalleryDialog(this);
	}

	@Override
	public GalleryDialog getDialog() {
		return (GalleryDialog) super.getDialog();
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();

		if (showcaseDestroy != null) {
			showcaseDestroy.run();
			showcaseDestroy = null;
		}
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		View queuedFromView = this.queuedFromView != null ? this.queuedFromView.get() : null;
		this.queuedFromView = null;
		int[] imageViewPosition = null;
		if (queuedFromView != null) {
			int[] location = new int[2];
			queuedFromView.getLocationOnScreen(location);
			imageViewPosition = new int[] {location[0], location[1],
					queuedFromView.getWidth(), queuedFromView.getHeight()};
		}
		WindowManager.LayoutParams attributes = getWindow().getAttributes();
		attributes.windowAnimations = imageViewPosition == null
				? R.style.Animation_Gallery_Full : R.style.Animation_Gallery_Partial;
		if (C.API_PIE) {
			attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams
					.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
		}

		if (rootView == null) {
			Context context = ThemeEngine.attach(new ContextThemeWrapper
					(MainApplication.getInstance().getLocalizedContext(), R.style.Theme_Gallery));
			rootView = new InsetsLayout(context);
			rootView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
				@Override
				public void onViewAttachedToWindow(View v) {
					if (!galleryMode) {
						displayShowcase();
					}
				}

				@Override
				public void onViewDetachedFromWindow(View v) {}
			});
			rootView.setOnApplyInsetsListener(apply -> {
				InsetsLayout.Insets insets = apply.get();
				if (listUnit != null) {
					boolean invalidate = listUnit.onApplyWindowInsets(insets);
					if (invalidate) {
						postInvalidateSystemUIVisibility();
					}
				}
				if (pagerUnit != null) {
					pagerUnit.onApplyWindowInsets(insets);
				}
			});
			rootView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
					ViewGroup.LayoutParams.MATCH_PARENT));
			rootView.setBackground(new GalleryBackgroundDrawable(rootView, imageViewPosition, BACKGROUND_COLOR));
		}
		GalleryDialog dialog = getDialog();
		ViewUtils.removeFromParent(rootView);
		dialog.setContentView(rootView);
		dialog.show();
		dialog.getActionBar().setDisplayHomeAsUpEnabled(true);
		Runnable invalidateSystemUiFlags = () -> {
			if (dialog.isShowing()) {
				invalidateSystemUiFlags();
			}
		};
		dialog.setOnFocusChangeListener(hasFocus -> {
			if (pagerUnit != null) {
				// Block touch events when dialogs are opened
				pagerUnit.setHasFocus(hasFocus);
			}
			ConcurrentUtils.HANDLER.removeCallbacks(invalidateSystemUiFlags);
			if (hasFocus) {
				// Re-apply visibility flags after dialogs closed
				ConcurrentUtils.HANDLER.postDelayed(invalidateSystemUiFlags, 100);
			}
		});

		Integer newImagePosition = null;
		if (instance == null) {
			Uri uri = requireArguments().getParcelable(EXTRA_URI);
			String chanNameFromArguments = requireArguments().getString(EXTRA_CHAN_NAME);
			Chan chan = chanNameFromArguments == null && uri != null
					? Chan.getPreferred(null, uri) : Chan.get(chanNameFromArguments);
			boolean defaultLocator = chan.name == null;

			List<GalleryItem> galleryItems;
			int imagePosition;
			if (uri != null) {
				String boardName = null;
				String threadNumber = null;
				if (!defaultLocator) {
					boardName = chan.locator.safe(true).getBoardName(uri);
					threadNumber = chan.locator.safe(true).getThreadNumber(uri);
				}
				galleryItems = Collections.singletonList(new GalleryItem(uri, boardName, threadNumber));
				imagePosition = 0;
			} else {
				galleryItems = queuedGalleryItems;
				queuedGalleryItems = null;
				imagePosition = savedInstanceState != null ? savedInstanceState.getInt(EXTRA_POSITION)
						: requireArguments().getInt(EXTRA_IMAGE_INDEX);
			}
			instance = new GalleryInstance(rootView.getContext(), this, ACTION_BAR_COLOR, chan.name,
					galleryItems != null ? galleryItems : Collections.emptyList());
			if (!instance.galleryItems.isEmpty()) {
				listUnit = new ListUnit(instance);
				pagerUnit = new PagerUnit(instance);
				rootView.addView(listUnit.getRecyclerView(), InsetsLayout.LayoutParams.MATCH_PARENT,
						InsetsLayout.LayoutParams.MATCH_PARENT);
				rootView.addView(pagerUnit.getView(), InsetsLayout.LayoutParams.MATCH_PARENT,
						InsetsLayout.LayoutParams.MATCH_PARENT);
				pagerUnit.addAndInitViews(rootView, imagePosition);
			}
			newImagePosition = imagePosition;
		}

		if (instance.galleryItems.isEmpty()) {
			ViewFactory.ErrorHolder errorHolder = ViewFactory.createErrorLayout(rootView);
			errorHolder.text.setText(R.string.gallery_is_empty);
			rootView.addView(errorHolder.layout);
		} else {
			if (savedInstanceState != null && savedInstanceState.containsKey(EXTRA_GALLERY_MODE)) {
				galleryMode = savedInstanceState.getBoolean(EXTRA_GALLERY_MODE);
				galleryWindow = savedInstanceState.getBoolean(EXTRA_GALLERY_WINDOW);
				switchMode(galleryMode, false);
				modifySystemUiVisibility(GalleryInstance.Flags.LOCKED_USER,
						savedInstanceState.getBoolean(EXTRA_SYSTEM_UI_VISIBILITY));
			} else if (newImagePosition != null) {
				int imagePosition = newImagePosition;
				galleryWindow = imagePosition < 0 || requireArguments().getBoolean(EXTRA_INITIAL_GALLERY_MODE);
				if (galleryWindow && imagePosition >= 0) {
					listUnit.scrollListToPosition(imagePosition, false);
				}
				switchMode(galleryWindow, false);
			}
			if (newImagePosition != null) {
				pagerUnit.onViewsCreated(imageViewPosition);
			}
			if (!galleryMode) {
				displayShowcase();
			}
		}
		int[] selected = savedInstanceState != null ? savedInstanceState.getIntArray(EXTRA_SELECTED) : null;
		if (selected != null && galleryMode && listUnit.areItemsSelectable()) {
			listUnit.startSelectionMode(selected);
		}

		if (newImagePosition == null) {
			Configuration configuration = getResources().getConfiguration();
			if (listUnit != null) {
				listUnit.onConfigurationChanged(configuration);
			}
			if (pagerUnit != null) {
				pagerUnit.onConfigurationChanged(configuration);
			}
		}
		if (titleSubtitle != null) {
			dialog.setTitleSubtitle(titleSubtitle.first, titleSubtitle.second);
		}
		if (C.API_LOLLIPOP) {
			Window window = getWindow();
			if (window != null) {
				int color = ACTION_BAR_COLOR;
				window.setStatusBarColor(color);
				window.setNavigationBarColor(color);
				ViewUtils.setWindowLayoutFullscreen(window);
			}
		}
		setScreenOnFixed(screenOnFixed);
		invalidateSystemUiVisibility();
	}

	@Override
	public void onResume() {
		super.onResume();

		if (pagerUnit != null) {
			pagerUnit.onResume();
		}
	}

	@Override
	public void onPause() {
		super.onPause();

		if (pagerUnit != null) {
			pagerUnit.onPause();
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		if (pagerUnit != null) {
			pagerUnit.onFinish();
		}
	}

	private void invalidateListPosition() {
		listUnit.scrollListToPosition(pagerUnit.getCurrentIndex(), true);
	}

	private final Runnable returnToGalleryRunnable = () -> {
		switchMode(true, true);
		invalidateListPosition();
	};

	private boolean returnToGallery() {
		if (galleryWindow && !galleryMode) {
			pagerUnit.onBackToGallery();
			rootView.post(returnToGalleryRunnable);
			return true;
		}
		return false;
	}

	@Override
	public boolean onBackPressed() {
		if (showcaseDestroy != null) {
			showcaseDestroy.run();
			showcaseDestroy = null;
			return true;
		}
		return returnToGallery();
	}

	@Override
	public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
		menu.add(0, R.id.menu_save, 0, R.string.save)
				.setIcon(ResourceUtils.getActionBarIcon(instance.context, R.attr.iconActionSave))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		menu.add(0, R.id.menu_refresh, 0, R.string.refresh)
				.setIcon(ResourceUtils.getActionBarIcon(instance.context, R.attr.iconActionRefresh))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		menu.add(0, R.id.menu_select, 0, R.string.select)
				.setIcon(ResourceUtils.getActionBarIcon(instance.context, R.attr.iconActionSelect))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
	}

	@Override
	public void onPrepareOptionsMenu(@NonNull Menu menu) {
		for (int i = 0; i < menu.size(); i++) {
			menu.getItem(i).setVisible(false);
		}
		if (!galleryMode) {
			PagerUnit.OptionsMenuCapabilities capabilities = pagerUnit != null
					? pagerUnit.obtainOptionsMenuCapabilities() : null;
			if (capabilities != null && capabilities.available) {
				menu.findItem(R.id.menu_save).setVisible(capabilities.save);
				menu.findItem(R.id.menu_refresh).setVisible(capabilities.refresh);
			}
			if (pagerUnit != null) {
				pagerUnit.invalidatePopupMenu();
			}
		} else {
			menu.findItem(R.id.menu_select).setVisible(listUnit.areItemsSelectable());
		}
	}

	@Override
	public boolean onOptionsItemSelected(@NonNull MenuItem item) {
		PagerInstance.ViewHolder holder = pagerUnit != null ? pagerUnit.getCurrentHolder() : null;
		switch (item.getItemId()) {
			case android.R.id.home: {
				dismiss();
				break;
			}
			case R.id.menu_save: {
				downloadGalleryItem(holder.galleryItem);
				break;
			}
			case R.id.menu_refresh: {
				pagerUnit.refreshCurrent();
				break;
			}
			case R.id.menu_select: {
				listUnit.startSelectionMode(null);
				break;
			}
		}
		return true;
	}

	@Override
	public Window getWindow() {
		GalleryDialog dialog = getDialog();
		return dialog != null ? dialog.getWindow() : null;
	}

	@Override
	public void downloadGalleryItem(GalleryItem galleryItem) {
		DownloadService.Binder binder = ((FragmentHandler) requireActivity()).getDownloadBinder();
		if (binder != null) {
			galleryItem.downloadStorage(binder, Chan.get(instance.chanName), getThreadTitle());
		}
	}

	@Override
	public void downloadGalleryItems(List<GalleryItem> galleryItems) {
		String boardName = null;
		String threadNumber = null;
		Chan chan = Chan.get(instance.chanName);
		ArrayList<DownloadService.RequestItem> requestItems = new ArrayList<>();
		for (GalleryItem galleryItem : galleryItems) {
			if (requestItems.size() == 0) {
				boardName = galleryItem.boardName;
				threadNumber = galleryItem.threadNumber;
			} else if (boardName != null || threadNumber != null) {
				if (!CommonUtils.equals(boardName, galleryItem.boardName) ||
						!CommonUtils.equals(threadNumber, galleryItem.threadNumber)) {
					// Images from different threads, so don't use them to mark files and folders
					boardName = null;
					threadNumber = null;
				}
			}
			requestItems.add(new DownloadService.RequestItem(galleryItem.getFileUri(chan),
					galleryItem.getFileName(chan), galleryItem.originalName));
		}
		if (requestItems.size() > 0) {
			DownloadService.Binder binder = ((FragmentHandler) requireActivity()).getDownloadBinder();
			if (binder != null) {
				binder.downloadStorage(requestItems, true, instance.chanName,
						boardName, threadNumber, getThreadTitle());
			}
		}
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);

		if (pagerUnit != null) {
			outState.putInt(EXTRA_POSITION, pagerUnit.getCurrentIndex());
		}
		if (listUnit != null) {
			outState.putIntArray(EXTRA_SELECTED, listUnit.getSelectedPositions());
		}
		outState.putBoolean(EXTRA_GALLERY_WINDOW, galleryWindow);
		outState.putBoolean(EXTRA_GALLERY_MODE, galleryMode);
		outState.putBoolean(EXTRA_SYSTEM_UI_VISIBILITY,
				FlagUtils.get(systemUiVisibilityFlags, GalleryInstance.Flags.LOCKED_USER));
	}

	private static final int GALLERY_TRANSITION_DURATION = 150;

	private void switchMode(boolean galleryMode, boolean animated) {
		int duration = animated ? GALLERY_TRANSITION_DURATION : 0;
		pagerUnit.switchMode(galleryMode, duration);
		listUnit.switchMode(galleryMode, duration);
		if (galleryMode) {
			int count = instance.galleryItems.size();
			getDialog().setTitleSubtitle(getString(R.string.gallery), getResources()
					.getQuantityString(R.plurals.number_files__format, count, count));
			titleSubtitle = null;
		}
		modifySystemUiVisibility(GalleryInstance.Flags.LOCKED_GRID, galleryMode);
		this.galleryMode = galleryMode;
		if (galleryMode) {
			new CornerAnimator(0xa0, 0xc0);
		} else {
			int alpha = Color.alpha(ACTION_BAR_COLOR);
			new CornerAnimator(alpha, alpha);
		}
		invalidateOptionsMenu();
		if (!galleryMode) {
			displayShowcase();
		}
	}

	private class CornerAnimator implements Runnable {
		private final long startTime = SystemClock.elapsedRealtime();

		private final int fromActionBarAlpha;
		private final int toActionBarAlpha;
		private final int fromStatusBarAlpha;
		private final int toStatusBarAlpha;

		private static final int INTERVAL = 200;

		public CornerAnimator(int actionBarAlpha, int statusBarAlpha) {
			Drawable drawable = getDialog().getActionBarView().getBackground();
			fromActionBarAlpha = Color.alpha(drawable instanceof ColorDrawable
					? ((ColorDrawable) drawable).getColor() : statusBarAlpha);
			toActionBarAlpha = actionBarAlpha;
			if (C.API_LOLLIPOP) {
				fromStatusBarAlpha = Color.alpha(getWindow().getStatusBarColor());
				toStatusBarAlpha = statusBarAlpha;
			} else {
				fromStatusBarAlpha = 0x00;
				toStatusBarAlpha = 0x00;
			}
			if (fromActionBarAlpha != toActionBarAlpha || fromStatusBarAlpha != toStatusBarAlpha) {
				run();
			}
		}

		@Override
		public void run() {
			float t = Math.min((float) (SystemClock.elapsedRealtime() - startTime) / INTERVAL, 1f);
			int actionBarColorAlpha = (int) AnimationUtils.lerp(fromActionBarAlpha, toActionBarAlpha, t);
			GalleryDialog dialog = getDialog();
			if (dialog != null) {
				dialog.getActionBarView().setBackgroundColor((actionBarColorAlpha << 24)
						| (0x00ffffff & ACTION_BAR_COLOR));
				if (C.API_LOLLIPOP) {
					int statusBarColorAlpha = (int) AnimationUtils.lerp(fromStatusBarAlpha, toStatusBarAlpha, t);
					int color = (statusBarColorAlpha << 24) | (0x00ffffff & ACTION_BAR_COLOR);
					getWindow().setStatusBarColor(color);
					getWindow().setNavigationBarColor(color);
				}
				if (t < 1f) {
					rootView.postOnAnimation(this);
				}
			}
		}
	}

	@Override
	public void modifyVerticalSwipeState(boolean ignoreIfGallery, float value) {
		if (!ignoreIfGallery && !galleryWindow) {
			rootView.getBackground().setAlpha((int) (0xff * (1f - value)));
		}
	}

	@Override
	public void updateTitle() {
		PagerInstance.ViewHolder holder = pagerUnit.getCurrentHolder();
		if (holder != null && holder.galleryItem != null) {
			setTitle(holder.galleryItem, holder.mediaSummary, pagerUnit.getCurrentIndex());
		}
	}

	private void setTitle(GalleryItem galleryItem, PagerInstance.MediaSummary mediaSummary, int position) {
		String fileName = galleryItem.getFileName(Chan.get(instance.chanName));
		if (!StringUtils.isEmpty(galleryItem.originalName)) {
			fileName = galleryItem.originalName;
		}
		int count = instance.galleryItems.size();
		StringBuilder builder = new StringBuilder().append(position + 1).append('/').append(count);
		if (mediaSummary.width > 0 && mediaSummary.height > 0) {
			builder.append(", ").append(mediaSummary.width).append('×').append(mediaSummary.height);
		}
		if (mediaSummary.size > 0) {
			builder.append(", ").append(StringUtils.formatFileSize(mediaSummary.size, false));
		}
		titleSubtitle = new Pair<>(fileName, builder);
		GalleryDialog dialog = getDialog();
		if (dialog != null) {
			dialog.setTitleSubtitle(fileName, builder);
		}
	}

	@Override
	public void navigateGalleryOrFinish(boolean enableGalleryMode) {
		if (enableGalleryMode && !galleryWindow) {
			galleryWindow = true;
		}
		if (!returnToGallery()) {
			getWindow().getDecorView().post(this::dismiss);
		}
	}

	@Override
	public void navigatePageFromList(int position) {
		switchMode(false, true);
		pagerUnit.navigatePageFromList(position, GALLERY_TRANSITION_DURATION);
	}

	private boolean checkAllowNavigatePost(boolean manually) {
		NavigatePostMode navigatePostMode = getNavigatePostMode();
		return navigatePostMode == NavigatePostMode.ENABLED ||
				navigatePostMode == NavigatePostMode.MANUALLY && manually;
	}

	@Override
	public void navigatePost(GalleryItem galleryItem, boolean manually, boolean force) {
		if (checkAllowNavigatePost(manually) && (scrollThread || force)) {
			((FragmentHandler) requireActivity()).scrollToPost(instance.chanName, galleryItem.boardName,
					galleryItem.threadNumber, galleryItem.postNumber);
			if (force) {
				dismiss();
			}
		}
	}

	@Override
	public boolean isAllowNavigatePostManually(boolean fromPager) {
		// Don't allow navigate to post from pager if thread is scrolling automatically with pager
		return checkAllowNavigatePost(true) && (!(scrollThread && checkAllowNavigatePost(false)) || !fromPager);
	}

	@Override
	public void invalidateOptionsMenu() {
		GalleryDialog dialog = getDialog();
		if (dialog != null) {
			dialog.invalidateOptionsMenu();
		}
	}

	@Override
	public void setScreenOnFixed(boolean fixed) {
		screenOnFixed = fixed;
		GalleryDialog dialog = getDialog();
		if (dialog != null) {
			Window window = getWindow();
			if (fixed) {
				window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
			} else {
				window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
			}
		}
	}

	@Override
	public boolean isGalleryWindow() {
		return galleryWindow;
	}

	@Override
	public boolean isGalleryMode() {
		return galleryMode;
	}

	private void postInvalidateSystemUIVisibility() {
		rootView.post(this::invalidateSystemUiVisibility);
	}

	private void invalidateSystemUiVisibility() {
		GalleryDialog dialog = getDialog();
		if (dialog != null) {
			ActionBar actionBar = dialog.getActionBar();
			boolean visible = isSystemUiVisible();
			boolean changed = visible != actionBar.isShowing();
			if (visible) {
				actionBar.show();
			} else {
				actionBar.hide();
			}
			invalidateSystemUiFlags();
			if (pagerUnit != null) {
				pagerUnit.invalidateControlsVisibility();
				if (changed) {
					pagerUnit.invalidatePopupMenu();
				}
			}
		}
	}

	private void invalidateSystemUiFlags() {
		if (C.API_LOLLIPOP) {
			boolean visible = isSystemUiVisible();
			Window window = getWindow();
			if (C.API_R) {
				WindowInsetsController controller = window.getInsetsController();
				controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
				if (visible) {
					controller.show(WindowInsets.Type.systemBars());
				} else {
					controller.hide(WindowInsets.Type.systemBars());
				}
			} else {
				View decorView = window.getDecorView();
				@SuppressWarnings("deprecation")
				Runnable runnable = () -> {
					@SuppressWarnings("deprecation")
					int visibility = decorView.getSystemUiVisibility();
					visibility = FlagUtils.set(visibility, View.SYSTEM_UI_FLAG_FULLSCREEN |
							View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION, !visible);
					decorView.setSystemUiVisibility(visibility);
				};
				runnable.run();
			}
		}
	}

	@Override
	public boolean isSystemUiVisible() {
		return systemUiVisibilityFlags != 0;
	}

	@Override
	public void modifySystemUiVisibility(int flag, boolean value) {
		systemUiVisibilityFlags = FlagUtils.set(systemUiVisibilityFlags, flag, value);
		invalidateSystemUiVisibility();
	}

	@Override
	public void toggleSystemUIVisibility(int flag) {
		modifySystemUiVisibility(flag, !FlagUtils.get(systemUiVisibilityFlags, flag));
	}

	private Runnable showcaseDestroy;

	private void displayShowcase() {
		if (showcaseDestroy != null || !Preferences.isShowcaseGalleryEnabled() ||
				!ViewCompat.isAttachedToWindow(rootView)) {
			return;
		}

		Context context = getWindow().getContext();
		float density = ResourceUtils.obtainDensity(context);
		FrameLayout frameLayout = new FrameLayout(context);
		frameLayout.setBackgroundColor(0xf0222222);
		LinearLayout linearLayout = new LinearLayout(context);
		linearLayout.setOrientation(LinearLayout.VERTICAL);
		frameLayout.addView(linearLayout, new FrameLayout.LayoutParams((int) (304f * density),
				FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

		Button button = new Button(context, null, android.R.attr.borderlessButtonStyle);
		button.setText(R.string.got_it);
		button.setMinimumWidth(0);
		button.setMinWidth(0);

		int paddingLeft = button.getPaddingLeft();
		int paddingRight = button.getPaddingRight();
		int paddingTop = button.getPaddingTop();
		int paddingBottom = Math.max(0, (int) (24f * density) - paddingTop);

		int[] titles = {R.string.context_menu, R.string.gallery};
		int[] messages = {R.string.context_menu_description__sentence, R.string.gallery_description__sentence};

		for (int i = 0; i < titles.length; i++) {
			TextView textView1 = new TextView(context, null, android.R.attr.textAppearanceLarge);
			textView1.setText(titles[i]);
			textView1.setTypeface(ResourceUtils.TYPEFACE_LIGHT);
			textView1.setPadding(paddingLeft, paddingTop, paddingRight, (int) (4f * density));
			TextView textView2 = new TextView(context, null, android.R.attr.textAppearanceSmall);
			textView2.setText(messages[i]);
			textView2.setPadding(paddingLeft, 0, paddingRight, paddingBottom);
			linearLayout.addView(textView1, LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.WRAP_CONTENT);
			linearLayout.addView(textView2, LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.WRAP_CONTENT);
		}

		linearLayout.addView(button, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);

		WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
		layoutParams.format = PixelFormat.TRANSLUCENT;
		layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
		layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
		layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
				WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
		layoutParams.windowAnimations = R.style.Animation_Gallery_Full;
		if (C.API_LOLLIPOP) {
			layoutParams.flags |= WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
		}
		windowManager.addView(frameLayout, layoutParams);

		showcaseDestroy = () -> windowManager.removeView(frameLayout);
		button.setOnClickListener(v -> {
			Preferences.consumeShowcaseGallery();
			showcaseDestroy.run();
			showcaseDestroy = null;
		});
	}
}
