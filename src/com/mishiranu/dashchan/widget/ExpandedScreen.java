package com.mishiranu.dashchan.widget;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.util.AnimationUtils;
import com.mishiranu.dashchan.util.FlagUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.widget.callback.ListScrollTracker;
import com.mishiranu.dashchan.widget.callback.ScrollListenerComposite;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

public class ExpandedScreen implements ListScrollTracker.OnScrollListener,
		WindowControlFrameLayout.OnApplyWindowPaddingsListener {
	private static final int LOLLIPOP_DIM_COLOR = 0x4d000000;

	private final boolean expandingEnabled;
	private final boolean fullScreenLayoutEnabled;

	private final Handler handler = new Handler();
	private final Activity activity;
	private View toolbarView;
	private View actionModeView;
	private View statusGuardView;

	private boolean drawerOverToolbarEnabled;
	private FrameLayout drawerParent;
	private AbsListView drawerListView;
	private View drawerHeader;
	private final LinkedHashMap<View, AbsListView> contentViews = new LinkedHashMap<>();
	private final LinkedHashSet<View> additionalViews = new LinkedHashSet<>();

	private ValueAnimator foregroundAnimator;
	private boolean foregroundAnimatorShow;

	private final StatusBarController statusBar;
	private final NavigationBarController navigationBar;

	private static final int STATE_SHOW = 0x00000001;
	private static final int STATE_ACTION_MODE = 0x00000002;
	private static final int STATE_LOCKED = 0x00000004;

	private final HashSet<String> lockers = new HashSet<>();
	private int stateFlags = STATE_SHOW;

	private final int slopShiftSize;
	private final int lastItemLimit;
	private int minItemsCount;

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public ExpandedScreen(Activity activity, boolean enabled) {
		this.activity = activity;
		statusBar = new StatusBarController(activity);
		navigationBar = new NavigationBarController();
		Resources resources = activity.getResources();
		Window window = activity.getWindow();
		boolean fullScreenLayoutEnabled;
		if (C.API_LOLLIPOP) {
			fullScreenLayoutEnabled = true;
		} else if (C.API_KITKAT) {
			int resId = resources.getIdentifier("config_enableTranslucentDecor", "bool", "android");
			fullScreenLayoutEnabled = resId != 0 && resources.getBoolean(resId);
		} else {
			fullScreenLayoutEnabled = false;
		}
		expandingEnabled = enabled;
		this.fullScreenLayoutEnabled = fullScreenLayoutEnabled;
		float density = ResourceUtils.obtainDensity(resources);
		slopShiftSize = (int) (6f * density);
		lastItemLimit = (int) (72f * density);
		if (fullScreenLayoutEnabled) {
			if (C.API_LOLLIPOP) {
				int statusBarColor = window.getStatusBarColor() | Color.BLACK;
				int navigationBarColor = window.getNavigationBarColor() | Color.BLACK;
				window.setStatusBarColor(Color.TRANSPARENT);
				window.setNavigationBarColor(Color.TRANSPARENT);
				contentForeground = new LollipopContentForeground(statusBarColor, navigationBarColor);
				statusBarContentForeground = new LollipopStatusBarForeground(statusBarColor);
				statusBarDrawerForeground = new LollipopDrawerForeground();
			} else {
				contentForeground = new KitKatContentForeground();
				statusBarContentForeground = null;
				statusBarDrawerForeground = null;
			}
			window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
					View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
		} else {
			contentForeground = null;
			statusBarContentForeground = null;
			statusBarDrawerForeground = null;
			if (enabled) {
				window.requestFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
			}
		}
		readConfiguration(resources.getConfiguration());
	}

	@Override
	public void onApplyWindowPaddings(WindowControlFrameLayout view, Rect rect) {
		boolean needUpdate = false;
		needUpdate |= navigationBar.onSystemWindowInsetsChanged(rect);
		needUpdate |= statusBar.onSystemWindowInsetsChanged(rect);
		if (needUpdate) {
			onConfigurationChanged(null);
		}
	}

	private static class StatusBarController {
		private final int initialActionBarHeight;
		private int height = 0;

		public StatusBarController(Activity activity) {
			// Height may be not changed when screen rotated in insets rect on 4.4 (tested on emulator)
			initialActionBarHeight = obtainActionBarHeight(activity);
		}

		public int getHeight() {
			return height;
		}

		public boolean onSystemWindowInsetsChanged(Rect insets) {
			int oldHeight = height;
			int height = insets.top;
			if (height > initialActionBarHeight) {
				height -= initialActionBarHeight; // Fix for KitKat, assuming AB height always > status bar height
			}
			this.height = height;
			return oldHeight != height;
		}
	}

	private static class NavigationBarController {
		private int right = 0;
		private int bottom = 0;

		public int getRight() {
			return right;
		}

		public int getBottom() {
			return bottom;
		}

		public boolean onSystemWindowInsetsChanged(Rect insets) {
			int right = insets.right;
			int bottom = insets.bottom;
			if (this.right != right || this.bottom != bottom) {
				this.right = right;
				this.bottom = bottom;
				return true;
			}
			return false;
		}
	}

	// The same value is hardcoded in ActionBarImpl.
	private static final int ACTION_BAR_ANIMATION_TIME = 250;

	private final ForegroundDrawable contentForeground;
	private final ForegroundDrawable statusBarContentForeground;
	private final ForegroundDrawable statusBarDrawerForeground;

	private static abstract class ForegroundDrawable extends Drawable {
		protected int alpha = 0xff;

		public final void applyAlpha(float value) {
			alpha = (int) (0xff * value);
			invalidateSelf();
		}

		@Override
		public final int getOpacity() {
			return 0;
		}

		@Override
		public final void setAlpha(int alpha) {}

		@Override
		public final void setColorFilter(ColorFilter cf) {}
	}

	private class KitKatContentForeground extends ForegroundDrawable {
		private final Paint paint = new Paint();

		@Override
		public void draw(@NonNull Canvas canvas) {
			int statusBarHeight = statusBar.getHeight();
			if (statusBarHeight > 0 && alpha != 0x00 && alpha != 0xff) {
				// Black while action bar animated
				paint.setColor(Color.BLACK);
				canvas.drawRect(0f, 0f, getBounds().width(), statusBarHeight, paint);
			}
		}
	}

	private class LollipopContentForeground extends ForegroundDrawable {
		private final Paint paint = new Paint();
		private final int statusBarColor;
		private final int navigationBarColor;

		public LollipopContentForeground(int statusBarColor, int navigationBarColor) {
			this.statusBarColor = statusBarColor;
			this.navigationBarColor = navigationBarColor;
		}

		@Override
		public void draw(@NonNull Canvas canvas) {
			int width = getBounds().width();
			int height = getBounds().height();
			Paint paint = this.paint;
			if (toolbarView == null) {
				StatusBarController statusBar = ExpandedScreen.this.statusBar;
				int statusBarHeight = statusBar.getHeight();
				if (statusBarHeight > 0) {
					paint.setColor(LOLLIPOP_DIM_COLOR);
					canvas.drawRect(0f, 0f, width, statusBarHeight, paint);
					if (alpha > 0) {
						paint.setColor(statusBarColor);
						paint.setAlpha(alpha);
						canvas.drawRect(0f, 0f, width, statusBarHeight, paint);
					}
				}
			}
			NavigationBarController navigationBar = ExpandedScreen.this.navigationBar;
			// Add instead of sub, because metrics doesn't contain navbar size
			int navigationBarRight = navigationBar.getRight();
			int navigationBarBottom = navigationBar.getBottom();
			if (navigationBarRight > 0) {
				// In landscape mode NavigationBar is always visible
				paint.setColor(navigationBarColor);
				canvas.drawRect(width - navigationBarRight, 0, width, height, paint);
			}
			if (navigationBarBottom > 0) {
				paint.setColor(LOLLIPOP_DIM_COLOR);
				canvas.drawRect(0f, height - navigationBarBottom, width, height, paint);
				if (alpha > 0) {
					paint.setColor(navigationBarColor);
					paint.setAlpha(alpha);
					canvas.drawRect(0f, height - navigationBarBottom, width, height, paint);
				}
			}
		}
	}

	private class LollipopStatusBarForeground extends ForegroundDrawable {
		private final Paint paint = new Paint();
		private final int statusBarColor;

		public LollipopStatusBarForeground(int statusBarColor) {
			this.statusBarColor = statusBarColor;
		}

		@Override
		public void draw(@NonNull Canvas canvas) {
			int width = getBounds().width();
			Paint paint = this.paint;
			StatusBarController statusBar = ExpandedScreen.this.statusBar;
			int statusBarHeight = statusBar.getHeight();
			if (statusBarHeight > 0) {
				paint.setColor(LOLLIPOP_DIM_COLOR);
				canvas.drawRect(0f, 0f, width, statusBarHeight, paint);
				if (alpha > 0) {
					paint.setColor(statusBarColor);
					paint.setAlpha(alpha);
					canvas.drawRect(0f, 0f, width, statusBarHeight, paint);
				}
			}
		}
	}

	private class LollipopDrawerForeground extends ForegroundDrawable {
		private final Paint paint = new Paint();

		@Override
		public void draw(@NonNull Canvas canvas) {
			if (drawerOverToolbarEnabled && toolbarView != null) {
				int width = getBounds().width();
				StatusBarController statusBar = ExpandedScreen.this.statusBar;
				int statusBarHeight = statusBar.getHeight();
				if (statusBarHeight > 0) {
					paint.setColor(LOLLIPOP_DIM_COLOR);
					canvas.drawRect(0f, 0f, width, statusBarHeight, paint);
				}
			}
		}
	}

	private class ForegroundAnimatorListener implements ValueAnimator.AnimatorListener,
			ValueAnimator.AnimatorUpdateListener {
		private final boolean show;

		public ForegroundAnimatorListener(boolean show) {
			this.show = show;
		}

		@Override
		public void onAnimationUpdate(ValueAnimator animation) {
			float alpha = (float) animation.getAnimatedValue();
			if (contentForeground != null) {
				contentForeground.applyAlpha(alpha);
			}
			if (statusBarContentForeground != null) {
				statusBarContentForeground.applyAlpha(alpha);
			}
			if (statusBarDrawerForeground != null) {
				statusBarDrawerForeground.applyAlpha(alpha);
			}
			if (toolbarView != null) {
				toolbarView.setAlpha(alpha);
			}
		}

		@Override
		public void onAnimationStart(Animator animation) {}

		@Override
		public void onAnimationEnd(Animator animation) {
			if (toolbarView != null && !show) {
				activity.getActionBar().hide();
			}
			foregroundAnimator = null;
		}

		@Override
		public void onAnimationCancel(Animator animation) {}

		@Override
		public void onAnimationRepeat(Animator animation) {}
	}

	private boolean lastTranslucent = false;

	@TargetApi(Build.VERSION_CODES.KITKAT)
	private void setState(int state, boolean value) {
		if (expandingEnabled) {
			boolean oldShow, newShow, oldActionMode, newActionMode;
			newShow = oldShow = checkState(STATE_SHOW);
			newActionMode = oldActionMode = checkState(STATE_ACTION_MODE);
			if (state == STATE_SHOW) {
				newShow = value;
			}
			if (state == STATE_ACTION_MODE) {
				newActionMode = value;
			}
			stateFlags = FlagUtils.set(stateFlags, state, value);
			if (fullScreenLayoutEnabled && C.API_KITKAT && !C.API_LOLLIPOP) {
				boolean wasDisplayed = oldShow || oldActionMode;
				boolean willDisplayed = newShow || newActionMode;
				if (wasDisplayed == willDisplayed) {
					return;
				}
				boolean translucent = !willDisplayed;
				if (lastTranslucent != translucent) {
					lastTranslucent = translucent;
					Window window = activity.getWindow();
					if (translucent) {
						window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
								| WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
					} else {
						window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
								| WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
					}
				}
			}
		}
	}

	private boolean checkState(int state) {
		return FlagUtils.get(stateFlags, state);
	}

	private void applyShowActionBar(boolean show) {
		ActionBar actionBar = activity.getActionBar();
		if (fullScreenLayoutEnabled) {
			boolean showing = isActionBarShowing();
			ValueAnimator foregroundAnimator = ExpandedScreen.this.foregroundAnimator;
			if (foregroundAnimator != null) {
				foregroundAnimator.cancel();
			}
			if (showing != show) {
				if (toolbarView != null) {
					actionBar.show();
				}
				foregroundAnimator = ValueAnimator.ofFloat(show ? 0f : 1f, show ? 1f : 0f);
				ForegroundAnimatorListener listener = new ForegroundAnimatorListener(show);
				foregroundAnimator.setInterpolator(AnimationUtils.ACCELERATE_DECELERATE_INTERPOLATOR);
				foregroundAnimator.setDuration(ACTION_BAR_ANIMATION_TIME);
				foregroundAnimator.addListener(listener);
				foregroundAnimator.addUpdateListener(listener);
				foregroundAnimator.start();
				ExpandedScreen.this.foregroundAnimator = foregroundAnimator;
				foregroundAnimatorShow = show;
			}
		}
		if (toolbarView == null) {
			if (show) {
				actionBar.show();
			} else {
				actionBar.hide();
			}
		}
	}

	private boolean isActionBarShowing() {
		if (!activity.getActionBar().isShowing()) {
			return false;
		}
		if (toolbarView != null && foregroundAnimator != null) {
			return foregroundAnimatorShow;
		}
		return true;
	}

	private boolean enqueuedShowState = true;
	private long lastShowStateChanged;

	private final Runnable showStateRunnable = () -> {
		if (enqueuedShowState != isActionBarShowing()) {
			boolean show = enqueuedShowState;
			setState(STATE_SHOW, show);
			applyShowActionBar(show);
			lastShowStateChanged = System.currentTimeMillis();
			updatePaddings();
		}
	};

	public void addLocker(String name) {
		lockers.add(name);
		if (!checkState(STATE_LOCKED)) {
			setLocked(true);
		}
	}

	public void removeLocker(String name) {
		lockers.remove(name);
		if (lockers.size() == 0 && checkState(STATE_LOCKED)) {
			setLocked(false);
		}
	}

	private void setLocked(boolean locked) {
		setState(STATE_LOCKED, locked);
		if (locked) {
			setShowActionBar(true, false);
		}
	}

	private void setShowActionBar(boolean show, boolean delayed) {
		// In Lollipop ActionMode isn't depending from Toolbar (android:windowActionModeOverlay == true in theme)
		if (toolbarView == null && checkState(STATE_ACTION_MODE) || checkState(STATE_LOCKED)) {
			show = true;
		}
		if (enqueuedShowState != show) {
			enqueuedShowState = show;
			handler.removeCallbacks(showStateRunnable);
			long t = System.currentTimeMillis() - lastShowStateChanged;
			if (show != isActionBarShowing()) {
				if (!delayed) {
					showStateRunnable.run();
				} else if (t >= ACTION_BAR_ANIMATION_TIME + 200) {
					handler.post(showStateRunnable);
				} else {
					handler.postDelayed(showStateRunnable, t);
				}
			}
		}
	}

	public boolean isFullScreenLayoutEnabled() {
		return fullScreenLayoutEnabled;
	}

	public void onResume() {
		onConfigurationChanged(activity.getResources().getConfiguration());
	}

	public void onConfigurationChanged(Configuration configuration) {
		if (configuration != null) {
			readConfiguration(configuration);
		}
		updatePaddings();
		handler.removeCallbacks(scrollerUpdater);
		handler.post(scrollerUpdater);
	}

	private void readConfiguration(Configuration configuration) {
		if (configuration.screenHeightDp != Configuration.SCREEN_HEIGHT_DP_UNDEFINED) {
			// Let's think that 48 dp - min list item height
			minItemsCount = configuration.screenHeightDp / 48;
		}
	}

	private final ListScrollTracker listScrollTracker = new ListScrollTracker(this);

	public void addContentView(View view) {
		AbsListView listView = null;
		if (view instanceof ExpandedFrameLayout) {
			listView = ((ExpandedFrameLayout) view).getListView();
		}
		contentViews.put(view, listView);
		if (listView != null && expandingEnabled) {
			ScrollListenerComposite.obtain(listView).add(listScrollTracker);
		}
		updatePaddings();
		setShowActionBar(true, true);
	}

	public void removeContentView(View view) {
		AbsListView listView = contentViews.remove(view);
		if (listView != null && expandingEnabled) {
			ScrollListenerComposite.obtain(listView).remove(listScrollTracker);
		}
	}

	public void setDrawerListView(FrameLayout drawerParent, AbsListView drawerListView, View drawerHeader) {
		this.drawerParent = drawerParent;
		this.drawerListView = drawerListView;
		this.drawerHeader = drawerHeader;
	}

	public void addAdditionalView(View additionalView) {
		additionalViews.add(additionalView);
	}

	public void removeAdditionalView(View additionalView) {
		additionalViews.remove(additionalView);
	}

	public void setToolbar(View toolbar, FrameLayout toolbarDrawerInterlayerLayout) {
		toolbarView = toolbar;
		toolbarDrawerInterlayerLayout.setForeground(statusBarContentForeground);
		addAdditionalView(toolbarDrawerInterlayerLayout);
	}

	public void finishInitialization() {
		if (fullScreenLayoutEnabled) {
			FrameLayout content = activity.findViewById(android.R.id.content);
			WindowControlFrameLayout frameLayout = new WindowControlFrameLayout(activity);
			content.addView(frameLayout, FrameLayout.LayoutParams.MATCH_PARENT,
					FrameLayout.LayoutParams.MATCH_PARENT);
			frameLayout.setOnApplyWindowPaddingsListener(this);
			frameLayout.setBackground(contentForeground);
			if (statusBarDrawerForeground != null && drawerParent != null) {
				drawerParent.setForeground(statusBarDrawerForeground);
			}
		}
		updatePaddings();
	}

	public void setDrawerOverToolbarEnabled(boolean drawerOverToolbarEnabled) {
		this.drawerOverToolbarEnabled = drawerOverToolbarEnabled;
		updatePaddings();
	}

	private static final int[] ATTRS_ACTION_BAR_SIZE = {android.R.attr.actionBarSize};

	private static int obtainActionBarHeight(Activity activity) {
		TypedArray typedArray = activity.obtainStyledAttributes(ATTRS_ACTION_BAR_SIZE);
		int actionHeight = typedArray.getDimensionPixelSize(0, 0);
		typedArray.recycle();
		return actionHeight;
	}

	private static final int KEEP = Integer.MAX_VALUE;

	private static void setNewPadding(View view, int left, int top, int right, int bottom) {
		int oldLeft = view.getPaddingLeft();
		int oldTop = view.getPaddingTop();
		int oldRight = view.getPaddingRight();
		int oldBottom = view.getPaddingBottom();
		if (left == KEEP) {
			left = oldLeft;
		}
		if (top == KEEP) {
			top = oldTop;
		}
		if (right == KEEP) {
			right = oldRight;
		}
		if (bottom == KEEP) {
			bottom = oldBottom;
		}
		if (oldLeft != left || oldTop != top || oldRight != right || oldBottom != bottom) {
			view.setPadding(left, top, right, bottom);
		}
	}

	public void updatePaddings() {
		if (expandingEnabled || fullScreenLayoutEnabled) {
			int actionBarHeight = obtainActionBarHeight(activity);
			int statusBarHeight = statusBar.getHeight();
			int bottomNavigationBarHeight = navigationBar.getBottom();
			// TODO Handle left padding
			int rightNavigationBarHeight = navigationBar.getRight();
			for (LinkedHashMap.Entry<View, AbsListView> entry : contentViews.entrySet()) {
				View view = entry.getKey();
				AbsListView listView = entry.getValue();
				setNewPadding(view, 0, 0, rightNavigationBarHeight, 0);
				if (listView != null) {
					setNewPadding(listView, KEEP, statusBarHeight + actionBarHeight, KEEP, bottomNavigationBarHeight);
				}
				if (view instanceof ExpandedFrameLayout) {
					ExpandedFrameLayout layout = (ExpandedFrameLayout) view;
					int childCount = layout.getChildCount();
					for (int i = 0; i < childCount; i++) {
						View child = layout.getChildAt(i);
						if (child != listView) {
							child.setPadding(0, statusBarHeight + actionBarHeight,
									rightNavigationBarHeight, bottomNavigationBarHeight);
						}
					}
				}
			}
			if (actionModeView != null) {
				((ViewGroup.MarginLayoutParams) actionModeView.getLayoutParams()).rightMargin =
						rightNavigationBarHeight;
			}
			for (View additional : additionalViews) {
				additional.setPadding(0, statusBarHeight, rightNavigationBarHeight, bottomNavigationBarHeight);
			}
			if (drawerListView != null) {
				int paddingTop = C.API_LOLLIPOP && drawerOverToolbarEnabled && toolbarView != null
						? statusBarHeight : statusBarHeight + actionBarHeight;
				if (drawerHeader != null) {
					setNewPadding(drawerHeader, KEEP, paddingTop, KEEP, KEEP);
					setNewPadding(drawerListView, KEEP, 0, KEEP, bottomNavigationBarHeight);
				} else {
					setNewPadding(drawerListView, KEEP, paddingTop, KEEP, bottomNavigationBarHeight);
				}
			}
			if (contentForeground != null) {
				contentForeground.invalidateSelf();
			}
			if (statusBarContentForeground != null) {
				statusBarContentForeground.invalidateSelf();
			}
			if (statusBarDrawerForeground != null) {
				statusBarDrawerForeground.invalidateSelf();
			}
		}
	}

	private boolean scrollingDown;

	@Override
	public void onScroll(AbsListView view, int scrollY, int totalItemCount, boolean first, boolean last) {
		if (Math.abs(scrollY) > slopShiftSize) {
			scrollingDown = scrollY > 0;
		}
		boolean hide = false;
		if (scrollingDown) {
			if (totalItemCount > minItemsCount) {
				// List can be overscrolled when it shows first item including list top padding
				// top <= 0 means that list is not overscrolled
				if (!first || view.getChildAt(0).getTop() <= 0) {
					if (last) {
						View lastView = view.getChildAt(view.getChildCount() - 1);
						if (view.getHeight() - view.getPaddingBottom() - lastView.getBottom() + lastItemLimit < 0) {
							hide = true;
						}
					} else {
						hide = true;
					}
				}
			}
		}
		setShowActionBar(!hide, true);
	}

	public void setActionModeState(boolean actionMode) {
		if (actionMode && fullScreenLayoutEnabled && actionModeView == null) {
			// ActionModeBar view has lazy initialization
			int actionModeBarId = activity.getResources().getIdentifier("action_mode_bar", "id", "android");
			actionModeView = actionModeBarId != 0 ? activity.findViewById(actionModeBarId) : null;
			updatePaddings();
		}
		if (!actionMode && fullScreenLayoutEnabled && C.API_MARSHMALLOW
				&& activity.getWindow().hasFeature(Window.FEATURE_NO_TITLE)) {
			// Fix marshmallow bug with hidden action bar and action mode overlay
			if (statusGuardView == null) {
				View decorView = activity.getWindow().getDecorView();
				try {
					Field statusGuardField = decorView.getClass().getDeclaredField("mStatusGuard");
					statusGuardField.setAccessible(true);
					statusGuardView = (View) statusGuardField.get(decorView);
				} catch (Exception e) {
					// Ignore
				}
			}
			if (statusGuardView != null) {
				statusGuardView.post(statusGuardHideRunnable);
			}
		}
		setState(STATE_ACTION_MODE, actionMode);
		if (!actionMode && checkState(STATE_LOCKED) && !isActionBarShowing()) {
			// Restore action bar
			enqueuedShowState = false;
			setShowActionBar(true, true);
		}
	}

	private final Runnable statusGuardHideRunnable = () -> statusGuardView.setVisibility(View.GONE);

	private static final Field FAST_SCROLL_FIELD;
	private static final Method UPDATE_LAYOUT_METHOD;

	static {
		Field fastScrollField;
		Method updateLayoutMethod;
		try {
			fastScrollField = AbsListView.class.getDeclaredField(C.API_LOLLIPOP ? "mFastScroll" : "mFastScroller");
			fastScrollField.setAccessible(true);
			updateLayoutMethod = fastScrollField.getType().getDeclaredMethod("updateLayout");
		} catch (Exception e) {
			fastScrollField = null;
			updateLayoutMethod = null;
		}
		FAST_SCROLL_FIELD = fastScrollField;
		UPDATE_LAYOUT_METHOD = updateLayoutMethod;
	}

	private final Runnable scrollerUpdater = () -> {
		if (UPDATE_LAYOUT_METHOD != null) {
			for (LinkedHashMap.Entry<View, AbsListView> entry : contentViews.entrySet()) {
				AbsListView listView = entry.getValue();
				if (listView != null) {
					try {
						UPDATE_LAYOUT_METHOD.invoke(FAST_SCROLL_FIELD.get(listView));
					} catch (Exception e) {
						// Ignore
					}
				}
			}
		}
	};
}
