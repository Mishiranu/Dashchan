package com.mishiranu.dashchan.widget;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.graphics.BaseDrawable;
import com.mishiranu.dashchan.util.AnimationUtils;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.FlagUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.LinkedHashMap;

public class ExpandedScreen implements RecyclerScrollTracker.OnScrollListener {
	private final boolean expandingEnabled;
	private final boolean fullScreenLayoutEnabled;

	private final Activity activity;
	private InsetsLayout.Insets windowInsets = InsetsLayout.Insets.DEFAULT;
	private boolean useGesture29;
	private int imeBottom30;

	private final View rootView;
	private final View toolbarView;
	private final View drawerInterlayer;
	private final View drawerContent;
	private final View drawerHeader;

	private View actionModeView;
	private View statusGuardView;

	private boolean drawerOverToolbarEnabled;
	private final LinkedHashMap<View, RecyclerView> contentViews = new LinkedHashMap<>();

	private ValueAnimator foregroundAnimator;
	private boolean foregroundAnimatorShow;

	private enum State {
		SHOW, ACTION_MODE, LOCKED;

		int flag() {
			return 1 << ordinal();
		}
	}

	private final HashSet<String> lockers = new HashSet<>();
	private int stateFlags = State.SHOW.flag();

	private final int lastItemLimit;
	private final int minItemsCount;

	public interface Layout {
		default RecyclerView getRecyclerView() {
			return null;
		}

		void setVerticalInsets(int top, int bottom, boolean useGesture29);
	}

	public static class PreThemeInit {
		private final Activity activity;
		private final boolean expandingEnabled;
		private final boolean fullScreenLayoutEnabled;

		public PreThemeInit(Activity activity, boolean enabled) {
			this.activity = activity;
			expandingEnabled = enabled;
			if (C.API_LOLLIPOP) {
				fullScreenLayoutEnabled = true;
			} else if (C.API_KITKAT) {
				Resources resources = activity.getResources();
				int resId = resources.getIdentifier("config_enableTranslucentDecor", "bool", "android");
				fullScreenLayoutEnabled = resId != 0 && resources.getBoolean(resId);
			} else {
				fullScreenLayoutEnabled = false;
			}
			Window window = activity.getWindow();
			if (!fullScreenLayoutEnabled && enabled) {
				window.requestFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
			}
		}

		public Init initAfterTheme() {
			if (fullScreenLayoutEnabled) {
				ViewUtils.setWindowLayoutFullscreen(activity.getWindow());
			}
			return new Init(activity, expandingEnabled, fullScreenLayoutEnabled);
		}
	}

	public static class Init {
		private final Activity activity;
		private final boolean expandingEnabled;
		private final boolean fullScreenLayoutEnabled;

		private Init(Activity activity, boolean expandingEnabled, boolean fullScreenLayoutEnabled) {
			this.activity = activity;
			this.expandingEnabled = expandingEnabled;
			this.fullScreenLayoutEnabled = fullScreenLayoutEnabled;
		}
	}

	public ExpandedScreen(Init init, View rootView, View toolbarView, FrameLayout drawerInterlayer,
			FrameLayout drawerParent, View drawerContent, View drawerHeader) {
		expandingEnabled = init.expandingEnabled;
		fullScreenLayoutEnabled = init.fullScreenLayoutEnabled;
		activity = init.activity;
		Window window = activity.getWindow();
		if (fullScreenLayoutEnabled) {
			if (C.API_LOLLIPOP) {
				int statusBarColor = window.getStatusBarColor() | Color.BLACK;
				int navigationBarColor = window.getNavigationBarColor() | Color.BLACK;
				window.setStatusBarColor(Color.TRANSPARENT);
				window.setNavigationBarColor(Color.TRANSPARENT);
				contentForeground = new LollipopContentForeground(statusBarColor, navigationBarColor);
				statusBarContentForeground = new LollipopStatusBarForeground(statusBarColor);
				statusBarDrawerForeground = new LollipopDrawerForeground();
			} else if (C.API_KITKAT) {
				contentForeground = new KitKatContentForeground();
				statusBarContentForeground = null;
				statusBarDrawerForeground = null;
			} else {
				contentForeground = null;
				statusBarContentForeground = null;
				statusBarDrawerForeground = null;
			}
		} else {
			contentForeground = null;
			statusBarContentForeground = null;
			statusBarDrawerForeground = null;
		}

		Resources resources = activity.getResources();
		float density = ResourceUtils.obtainDensity(resources);
		lastItemLimit = (int) (72f * density);
		minItemsCount = resources.getConfiguration().screenHeightDp / 48;
		this.rootView = rootView;
		this.toolbarView = toolbarView;
		this.drawerInterlayer = drawerInterlayer;
		if (drawerInterlayer != null) {
			drawerInterlayer.setForeground(statusBarContentForeground);
		}
		this.drawerContent = drawerContent;
		this.drawerHeader = drawerHeader;
		if (fullScreenLayoutEnabled) {
			FrameLayout content = activity.findViewById(android.R.id.content);
			InsetsLayout insetsLayout = new InsetsLayout(activity);
			content.addView(insetsLayout, FrameLayout.LayoutParams.MATCH_PARENT,
					FrameLayout.LayoutParams.MATCH_PARENT);
			int actionBarHeight = obtainActionBarHeight(activity);
			insetsLayout.setOnApplyInsetsListener(applyData -> {
				InsetsLayout.Insets windowInsets;
				if (!C.API_LOLLIPOP && applyData.window.top > actionBarHeight) {
					// Fix for KitKat, assume action bar height > status bar height
					int top = applyData.window.top - actionBarHeight;
					windowInsets = new InsetsLayout.Insets(applyData.window.left, top,
							applyData.window.right, applyData.window.bottom);
				} else {
					windowInsets = applyData.window;
				}
				if (!this.windowInsets.equals(windowInsets) || useGesture29 != applyData.useGesture29 ||
						imeBottom30 != applyData.imeBottom30) {
					this.windowInsets = windowInsets;
					useGesture29 = applyData.useGesture29;
					imeBottom30 = applyData.imeBottom30;
					updatePaddings();
				}
			});
			insetsLayout.setBackground(contentForeground);
			if (statusBarDrawerForeground != null && drawerParent != null) {
				drawerParent.setForeground(statusBarDrawerForeground);
			}
		}
		updatePaddings();
	}

	// The same value is hardcoded in ActionBarImpl.
	private static final int ACTION_BAR_ANIMATION_TIME = 250;

	private final ForegroundDrawable contentForeground;
	private final ForegroundDrawable statusBarContentForeground;
	private final ForegroundDrawable statusBarDrawerForeground;

	private static abstract class ForegroundDrawable extends BaseDrawable {
		protected int alpha = 0xff;

		public final void applyAlpha(float value) {
			alpha = (int) (0xff * value);
			invalidateSelf();
		}
	}

	private class KitKatContentForeground extends ForegroundDrawable {
		private final Paint paint = new Paint();

		@Override
		public void draw(@NonNull Canvas canvas) {
			int statusBarHeight = windowInsets.top;
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
				int statusBarHeight = windowInsets.top;
				if (statusBarHeight > 0) {
					paint.setColor(ViewUtils.STATUS_OVERLAY_TRANSPARENT);
					canvas.drawRect(0f, 0f, width, statusBarHeight, paint);
					if (alpha > 0) {
						paint.setColor(statusBarColor);
						paint.setAlpha(alpha);
						canvas.drawRect(0f, 0f, width, statusBarHeight, paint);
					}
				}
			}
			int navigationBarLeft = windowInsets.left;
			int navigationBarRight = windowInsets.right;
			int navigationBarBottom = windowInsets.bottom;
			if (navigationBarLeft > 0) {
				paint.setColor(navigationBarColor);
				canvas.drawRect(0, 0, navigationBarLeft, height, paint);
			}
			if (navigationBarRight > 0) {
				paint.setColor(navigationBarColor);
				canvas.drawRect(width - navigationBarRight, 0, width, height, paint);
			}
			if (navigationBarBottom > 0 && !useGesture29) {
				paint.setColor(ViewUtils.STATUS_OVERLAY_TRANSPARENT);
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
			int statusBarHeight = windowInsets.top;
			if (statusBarHeight > 0) {
				paint.setColor(ViewUtils.STATUS_OVERLAY_TRANSPARENT);
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
				int statusBarHeight = windowInsets.top;
				if (statusBarHeight > 0) {
					paint.setColor(ViewUtils.STATUS_OVERLAY_TRANSPARENT);
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

	private boolean lastTranslucent19 = false;

	private void setState(State state, boolean value) {
		if (expandingEnabled) {
			boolean oldShow = checkState(State.SHOW);
			boolean newShow = state == State.SHOW ? value : oldShow;
			boolean oldActionMode = checkState(State.ACTION_MODE);
			boolean newActionMode = state == State.ACTION_MODE ? value : oldActionMode;
			stateFlags = FlagUtils.set(stateFlags, state.flag(), value);
			if (fullScreenLayoutEnabled && C.API_KITKAT && !C.API_LOLLIPOP) {
				boolean wasDisplayed = oldShow || oldActionMode;
				boolean willDisplayed = newShow || newActionMode;
				if (wasDisplayed == willDisplayed) {
					return;
				}
				setTranslucent19Only(!willDisplayed);
			}
		}
	}

	@SuppressWarnings("deprecation")
	private void setTranslucent19Only(boolean translucent) {
		if (lastTranslucent19 != translucent) {
			lastTranslucent19 = translucent;
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

	private boolean checkState(State state) {
		return FlagUtils.get(stateFlags, state.flag());
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
			setState(State.SHOW, show);
			applyShowActionBar(show);
			lastShowStateChanged = SystemClock.elapsedRealtime();
			updatePaddings();
		}
	};

	public void addLocker(String name) {
		lockers.add(name);
		if (!checkState(State.LOCKED)) {
			setLocked(true);
		}
	}

	public void removeLocker(String name) {
		lockers.remove(name);
		if (lockers.size() == 0 && checkState(State.LOCKED)) {
			setLocked(false);
		}
	}

	private void setLocked(boolean locked) {
		setState(State.LOCKED, locked);
		if (locked) {
			setShowActionBar(true, false);
		}
	}

	private void setShowActionBar(boolean show, boolean delayed) {
		if (!show) {
			show = checkState(State.LOCKED) || checkState(State.ACTION_MODE) &&
					!activity.getWindow().hasFeature(Window.FEATURE_ACTION_MODE_OVERLAY);
		}
		if (enqueuedShowState != show) {
			enqueuedShowState = show;
			ConcurrentUtils.HANDLER.removeCallbacks(showStateRunnable);
			long t = SystemClock.elapsedRealtime() - lastShowStateChanged;
			if (show != isActionBarShowing()) {
				if (!delayed) {
					showStateRunnable.run();
				} else if (t >= ACTION_BAR_ANIMATION_TIME + 200) {
					ConcurrentUtils.HANDLER.post(showStateRunnable);
				} else {
					ConcurrentUtils.HANDLER.postDelayed(showStateRunnable, t);
				}
			}
		}
	}

	private final RecyclerScrollTracker recyclerScrollTracker = new RecyclerScrollTracker(this);

	public void addContentView(View view) {
		RecyclerView recyclerView = null;
		if (view instanceof Layout) {
			recyclerView = ((Layout) view).getRecyclerView();
		}
		contentViews.put(view, recyclerView);
		if (recyclerView != null && expandingEnabled) {
			recyclerScrollTracker.attach(recyclerView);
		}
		updatePaddings();
		setShowActionBar(true, true);
	}

	public void removeContentView(View view) {
		RecyclerView recyclerView = contentViews.remove(view);
		if (recyclerView != null && expandingEnabled) {
			recyclerScrollTracker.detach(recyclerView);
		}
	}

	public void setDrawerOverToolbarEnabled(boolean drawerOverToolbarEnabled) {
		this.drawerOverToolbarEnabled = drawerOverToolbarEnabled;
		updatePaddings();
	}

	private static final int[] ATTRS_ACTION_BAR_SIZE = {android.R.attr.actionBarSize};

	private static int obtainActionBarHeight(Context context) {
		TypedArray typedArray = context.obtainStyledAttributes(ATTRS_ACTION_BAR_SIZE);
		int actionHeight = typedArray.getDimensionPixelSize(0, 0);
		typedArray.recycle();
		return actionHeight;
	}

	public void updatePaddings() {
		if (expandingEnabled || fullScreenLayoutEnabled) {
			int actionBarHeight = obtainActionBarHeight(activity);
			int statusBarHeight = windowInsets.top;
			int leftNavigationBarHeight = windowInsets.left;
			int rightNavigationBarHeight = windowInsets.right;
			int bottomNavigationBarHeight = windowInsets.bottom;
			boolean useGesture29 = this.useGesture29;
			int bottomImeHeight = imeBottom30;
			if (rootView != null) {
				ViewUtils.setNewMargin(rootView, leftNavigationBarHeight, 0,
						rightNavigationBarHeight, bottomImeHeight);
			}
			if (drawerInterlayer != null) {
				ViewUtils.setNewPadding(drawerInterlayer, null, statusBarHeight, null, bottomNavigationBarHeight);
			}
			for (View view : contentViews.keySet()) {
				if (view instanceof Layout) {
					((Layout) view).setVerticalInsets(statusBarHeight + actionBarHeight,
							bottomNavigationBarHeight, useGesture29);
				} else {
					ViewUtils.setNewMargin(view, null, statusBarHeight + actionBarHeight,
							null, bottomNavigationBarHeight);
				}
			}
			if (actionModeView != null) {
				ViewUtils.setNewMargin(actionModeView, leftNavigationBarHeight, null, rightNavigationBarHeight, null);
			}
			if (drawerContent != null) {
				int paddingTop = C.API_LOLLIPOP && drawerOverToolbarEnabled && toolbarView != null
						? statusBarHeight : statusBarHeight + actionBarHeight;
				if (drawerHeader != null) {
					ViewUtils.setNewPadding(drawerHeader, null, paddingTop, null, null);
					ViewUtils.setNewPadding(drawerContent, null, 0, null, bottomNavigationBarHeight);
				} else {
					ViewUtils.setNewPadding(drawerContent, null, paddingTop, null, bottomNavigationBarHeight);
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

	@Override
	public void onScroll(ViewGroup view, boolean scrollingDown, int totalItemCount, boolean first, boolean last) {
		boolean show = true;
		if (scrollingDown) {
			int childCount = view.getChildCount();
			if (childCount > 0 && totalItemCount > minItemsCount) {
				if (!first || view.getChildAt(0).getTop() <= 0) {
					// List is scrolled above the top edge of the screen
					if (last) {
						View lastView = view.getChildAt(childCount - 1);
						if (view.getHeight() - view.getPaddingBottom() - lastView.getBottom() + lastItemLimit < 0) {
							show = false;
						}
					} else {
						show = false;
					}
				}
			}
		}
		setShowActionBar(show, true);
	}

	public void setActionModeState(boolean actionMode) {
		if (actionMode && fullScreenLayoutEnabled && actionModeView == null) {
			// ActionModeBar view has lazy initialization
			int actionModeBarId = activity.getResources().getIdentifier("action_mode_bar", "id", "android");
			actionModeView = actionModeBarId != 0 ? activity.findViewById(actionModeBarId) : null;
			updatePaddings();
		}
		if (!actionMode && fullScreenLayoutEnabled && C.API_MARSHMALLOW && !C.API_Q
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
		setState(State.ACTION_MODE, actionMode);
		if (!actionMode && checkState(State.LOCKED) && !isActionBarShowing()) {
			// Restore action bar
			enqueuedShowState = false;
			setShowActionBar(true, true);
		}
	}

	private final Runnable statusGuardHideRunnable = () -> statusGuardView.setVisibility(View.GONE);
}
