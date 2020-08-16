package com.mishiranu.dashchan.ui.gallery;

import android.app.ActionBar;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Pair;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.DialogFragment;
import chan.content.ChanLocator;
import chan.content.ChanManager;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.MainApplication;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.model.AttachmentItem;
import com.mishiranu.dashchan.content.model.GalleryItem;
import com.mishiranu.dashchan.content.service.DownloadService;
import com.mishiranu.dashchan.graphics.ActionIconSet;
import com.mishiranu.dashchan.graphics.GalleryBackgroundDrawable;
import com.mishiranu.dashchan.ui.ActivityHandler;
import com.mishiranu.dashchan.ui.FragmentHandler;
import com.mishiranu.dashchan.util.AnimationUtils;
import com.mishiranu.dashchan.util.ConfigurationLock;
import com.mishiranu.dashchan.util.FlagUtils;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.WindowControlFrameLayout;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GalleryOverlay extends DialogFragment implements ActivityHandler, GalleryInstance.Callback,
		WindowControlFrameLayout.OnApplyWindowPaddingsListener {
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
	private static final String EXTRA_SYSTEM_UI_VISIBILITY = "systemUIVisibility";

	private List<GalleryItem> queuedGalleryItems;
	private WeakReference<View> queuedFromView;

	private WindowControlFrameLayout rootView;
	private GalleryInstance instance;
	private PagerUnit pagerUnit;
	private ListUnit listUnit;

	private boolean galleryWindow;
	private boolean galleryMode;
	private final boolean scrollThread = Preferences.isScrollThreadGallery();

	private Pair<CharSequence, CharSequence> titleSubtitle;
	private boolean screenOnFixed = false;
	private int systemUiVisibilityFlags = GalleryInstance.FLAG_LOCKED_USER;

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
			showcaseDestroy.destroy(false);
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
		getWindow().getAttributes().windowAnimations = imageViewPosition == null
				? R.style.Animation_Gallery_Full : R.style.Animation_Gallery_Partial;

		if (rootView == null) {
			Context context = new ContextThemeWrapper(MainApplication.getInstance(), R.style.Theme_Gallery);
			rootView = new WindowControlFrameLayout(context) {
				@Override
				protected void onAttachedToWindow() {
					super.onAttachedToWindow();
					if (!galleryMode) {
						displayShowcase();
					}
				}
			};
			rootView.setOnApplyWindowPaddingsListener(this);
			rootView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
					ViewGroup.LayoutParams.MATCH_PARENT));
			rootView.setBackground(new GalleryBackgroundDrawable(rootView, imageViewPosition, BACKGROUND_COLOR));
		}
		GalleryDialog dialog = getDialog();
		ViewUtils.removeFromParent(rootView);
		dialog.setContentView(rootView);
		dialog.show();
		dialog.getActionBar().setDisplayHomeAsUpEnabled(true);
		dialog.setOnFocusChangeListener(hasFocus -> {
			if (pagerUnit != null) {
				// Block touch events when dialogs are opened
				pagerUnit.setHasFocus(hasFocus);
			}
		});

		Integer newImagePosition = null;
		if (instance == null) {
			Uri uri = requireArguments().getParcelable(EXTRA_URI);
			String chanName = requireArguments().getString(EXTRA_CHAN_NAME);
			if (uri != null && chanName == null) {
				chanName = ChanManager.getInstance().getChanNameByHost(uri.getAuthority());
			}
			ChanLocator locator = chanName != null ? ChanLocator.get(chanName) : null;
			boolean defaultLocator = false;
			if (locator == null) {
				locator = ChanLocator.getDefault();
				defaultLocator = true;
			}

			List<GalleryItem> galleryItems;
			int imagePosition;
			if (uri != null) {
				String boardName = null;
				String threadNumber = null;
				if (!defaultLocator) {
					boardName = locator.safe(true).getBoardName(uri);
					threadNumber = locator.safe(true).getThreadNumber(uri);
				}
				galleryItems = Collections.singletonList(new GalleryItem(uri, boardName, threadNumber));
				imagePosition = 0;
			} else {
				galleryItems = queuedGalleryItems;
				queuedGalleryItems = null;
				imagePosition = savedInstanceState != null ? savedInstanceState.getInt(EXTRA_POSITION)
						: requireArguments().getInt(EXTRA_IMAGE_INDEX);
			}
			instance = new GalleryInstance(rootView.getContext(), this, ACTION_BAR_COLOR, chanName, locator,
					galleryItems != null ? galleryItems : Collections.emptyList());
			if (!instance.galleryItems.isEmpty()) {
				listUnit = new ListUnit(instance);
				pagerUnit = new PagerUnit(instance);
				rootView.addView(listUnit.getListView(), FrameLayout.LayoutParams.MATCH_PARENT,
						FrameLayout.LayoutParams.MATCH_PARENT);
				rootView.addView(pagerUnit.getView(), FrameLayout.LayoutParams.MATCH_PARENT,
						FrameLayout.LayoutParams.MATCH_PARENT);
				pagerUnit.addAndInitViews(rootView, imagePosition);
			}
			newImagePosition = imagePosition;
		}

		if (instance.galleryItems.isEmpty()) {
			View errorView = getLayoutInflater().inflate(R.layout.widget_error, rootView, false);
			TextView textView = errorView.findViewById(R.id.error_text);
			textView.setText(R.string.message_empty_gallery);
			rootView.addView(errorView);
		} else {
			if (savedInstanceState != null && savedInstanceState.containsKey(EXTRA_GALLERY_MODE)) {
				galleryMode = savedInstanceState.getBoolean(EXTRA_GALLERY_MODE);
				galleryWindow = savedInstanceState.getBoolean(EXTRA_GALLERY_WINDOW);
				switchMode(galleryMode, false);
				modifySystemUiVisibility(GalleryInstance.FLAG_LOCKED_USER,
						savedInstanceState.getBoolean(EXTRA_SYSTEM_UI_VISIBILITY));
			} else if (newImagePosition != null) {
				int imagePosition = newImagePosition;
				galleryWindow = imagePosition < 0 || requireArguments().getBoolean(EXTRA_INITIAL_GALLERY_MODE);
				if (galleryWindow && imagePosition >= 0) {
					listUnit.setListSelection(imagePosition, false);
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
			dialog.setTitle(titleSubtitle.first);
			dialog.getActionBar().setSubtitle(titleSubtitle.second);
		}
		setScreenOnFixed(screenOnFixed);
		applyStatusNavigationTranslucency();
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
		listUnit.setListSelection(pagerUnit.getCurrentIndex(), true);
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
			showcaseDestroy.destroy(true);
			return true;
		}
		return returnToGallery();
	}

	private static final int OPTIONS_MENU_SAVE = 0;
	private static final int OPTIONS_MENU_REFRESH = 1;
	private static final int OPTIONS_MENU_SELECT = 2;

	@Override
	public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
		ActionIconSet set = new ActionIconSet(getContext());
		menu.add(0, OPTIONS_MENU_SAVE, 0, R.string.action_save).setIcon(set.getId(R.attr.actionSave))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		menu.add(0, OPTIONS_MENU_REFRESH, 0, R.string.action_refresh).setIcon(set.getId(R.attr.actionRefresh))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		menu.add(0, OPTIONS_MENU_SELECT, 0, R.string.action_select).setIcon(set.getId(R.attr.actionSelect))
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
				menu.findItem(OPTIONS_MENU_SAVE).setVisible(capabilities.save);
				menu.findItem(OPTIONS_MENU_REFRESH).setVisible(capabilities.refresh);
			}
			if (pagerUnit != null) {
				pagerUnit.invalidatePopupMenu();
			}
		} else {
			menu.findItem(OPTIONS_MENU_SELECT).setVisible(listUnit.areItemsSelectable());
		}
	}

	@Override
	public boolean onOptionsItemSelected(@NonNull MenuItem item) {
		GalleryItem galleryItem = pagerUnit != null ? pagerUnit.getCurrentGalleryItem() : null;
		switch (item.getItemId()) {
			case android.R.id.home: {
				dismiss();
				break;
			}
			case OPTIONS_MENU_SAVE: {
				downloadGalleryItem(galleryItem);
				break;
			}
			case OPTIONS_MENU_REFRESH: {
				pagerUnit.refreshCurrent();
				break;
			}
			case OPTIONS_MENU_SELECT: {
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
	public ConfigurationLock getConfigurationLock() {
		return ((FragmentHandler) requireActivity()).getConfigurationLock();
	}

	@Override
	public void downloadGalleryItem(GalleryItem galleryItem) {
		DownloadService.Binder binder = ((FragmentHandler) requireActivity()).getDownloadBinder();
		if (binder != null) {
			galleryItem.downloadStorage(binder, instance.locator, getThreadTitle());
		}
	}

	@Override
	public void downloadGalleryItems(List<GalleryItem> galleryItems) {
		String boardName = null;
		String threadNumber = null;
		ArrayList<DownloadService.RequestItem> requestItems = new ArrayList<>();
		for (GalleryItem galleryItem : galleryItems) {
			if (requestItems.size() == 0) {
				boardName = galleryItem.boardName;
				threadNumber = galleryItem.threadNumber;
			} else if (boardName != null || threadNumber != null) {
				if (!StringUtils.equals(boardName, galleryItem.boardName) ||
						!StringUtils.equals(threadNumber, galleryItem.threadNumber)) {
					// Images from different threads, so don't use them to mark files and folders
					boardName = null;
					threadNumber = null;
				}
			}
			requestItems.add(new DownloadService.RequestItem(galleryItem.getFileUri(instance.locator),
					galleryItem.getFileName(instance.locator), galleryItem.originalName));
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
				FlagUtils.get(systemUiVisibilityFlags, GalleryInstance.FLAG_LOCKED_USER));
	}

	private static final int GALLERY_TRANSITION_DURATION = 150;

	private void switchMode(boolean galleryMode, boolean animated) {
		int duration = animated ? GALLERY_TRANSITION_DURATION : 0;
		pagerUnit.switchMode(galleryMode, duration);
		listUnit.switchMode(galleryMode, duration);
		if (galleryMode) {
			int count = instance.galleryItems.size();
			getDialog().setTitle(R.string.action_gallery);
			getDialog().getActionBar().setSubtitle(getResources()
					.getQuantityString(R.plurals.text_several_files_count_format, count, count));
			titleSubtitle = null;
		}
		modifySystemUiVisibility(GalleryInstance.FLAG_LOCKED_GRID, galleryMode);
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
		private final long startTime = System.currentTimeMillis();

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
			float t = Math.min((float) (System.currentTimeMillis() - startTime) / INTERVAL, 1f);
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
		GalleryItem galleryItem = pagerUnit.getCurrentGalleryItem();
		if (galleryItem != null) {
			setTitle(galleryItem, pagerUnit.getCurrentIndex(), galleryItem.size);
		}
	}

	private void setTitle(GalleryItem galleryItem, int position, int size) {
		String fileName = galleryItem.getFileName(instance.locator);
		String originalName = galleryItem.originalName;
		if (originalName != null) {
			fileName = originalName;
		}
		int count = instance.galleryItems.size();
		StringBuilder builder = new StringBuilder().append(position + 1).append('/').append(count);
		if (size > 0) {
			builder.append(", ").append(AttachmentItem.formatSize(size));
		}
		titleSubtitle = new Pair<>(fileName, builder);
		GalleryDialog dialog = getDialog();
		if (dialog != null) {
			dialog.setTitle(fileName);
			dialog.getActionBar().setSubtitle(builder);
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

	private void applyStatusNavigationTranslucency() {
		if (C.API_LOLLIPOP) {
			Window window = getWindow();
			if (window != null) {
				int color = ACTION_BAR_COLOR;
				window.setStatusBarColor(color);
				window.setNavigationBarColor(color);
				window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
						| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
			}
		}
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
			if (C.API_LOLLIPOP) {
				View decorView = getWindow().getDecorView();
				int visibility = decorView.getSystemUiVisibility();
				visibility = FlagUtils.set(visibility, View.SYSTEM_UI_FLAG_FULLSCREEN |
						View.SYSTEM_UI_FLAG_IMMERSIVE | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION, !visible);
				decorView.setSystemUiVisibility(visibility);
			}
			if (pagerUnit != null) {
				pagerUnit.invalidateControlsVisibility();
				if (changed) {
					pagerUnit.invalidatePopupMenu();
				}
			}
		}
	}

	private void postInvalidateSystemUIVisibility() {
		rootView.post(this::invalidateSystemUiVisibility);
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

	@Override
	public void onApplyWindowPaddings(WindowControlFrameLayout view, Rect rect) {
		if (listUnit != null) {
			boolean invalidate = listUnit.onApplyWindowPaddings(rect);
			if (invalidate) {
				postInvalidateSystemUIVisibility();
			}
		}
		if (pagerUnit != null) {
			pagerUnit.onApplyWindowPaddings(rect);
		}
	}

	private interface ShowcaseDestroy {
		void destroy(boolean consume);
	}

	private ShowcaseDestroy showcaseDestroy;

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
		button.setText(R.string.action_got_it);
		button.setMinimumWidth(0);
		button.setMinWidth(0);

		int paddingLeft = button.getPaddingLeft();
		int paddingRight = button.getPaddingRight();
		int paddingTop = button.getPaddingTop();
		int paddingBottom = Math.max(0, (int) (24f * density) - paddingTop);

		int[] messages = {R.string.message_showcase_context_menu, R.string.message_showcase_gallery};

		for (int resId : messages) {
			String[] message = getString(resId).split("\n");
			TextView textView1 = new TextView(context, null, android.R.attr.textAppearanceLarge);
			textView1.setText(message.length > 0 ? message[0] : null);
			textView1.setTypeface(GraphicsUtils.TYPEFACE_LIGHT);
			textView1.setPadding(paddingLeft, paddingTop, paddingRight, (int) (4f * density));
			TextView textView2 = new TextView(context, null, android.R.attr.textAppearanceSmall);
			textView2.setText(message.length > 1 ? message[1] : null);
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

		showcaseDestroy = consume -> {
			showcaseDestroy = null;
			if (consume) {
				Preferences.consumeShowcaseGallery();
			}
			windowManager.removeView(frameLayout);
		};

		button.setOnClickListener(v -> showcaseDestroy.destroy(true));
	}
}
