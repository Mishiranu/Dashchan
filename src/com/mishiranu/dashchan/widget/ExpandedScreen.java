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

package com.mishiranu.dashchan.widget;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedHashMap;

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
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.FrameLayout;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.util.AnimationUtils;
import com.mishiranu.dashchan.util.FlagUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.widget.callback.ListScrollTracker;
import com.mishiranu.dashchan.widget.callback.ScrollListenerComposite;

public class ExpandedScreen implements ListScrollTracker.OnScrollListener,
		WindowControlFrameLayout.OnApplyWindowPaddingsListener
{
	private static final int LOLLIPOP_DIM_COLOR = 0x4d000000;
	
	private final boolean mExpandingEnabled;
	private final boolean mFullScreenLayoutEnabled;
	
	private final Activity mActivity;
	private View mToolbarView;
	private View mActionModeView;
	private View mStatusGuardView;
	
	private AbsListView mListView;
	private boolean mDrawerOverToolbarEnabled;
	private FrameLayout mDrawerParent;
	private AbsListView mDrawerListView;
	private View mDrawerHeader;
	private LinkedHashMap<View, Boolean> mAdditionalViews;
	
	private ValueAnimator mForegroundAnimator;
	private boolean mForegroundAnimatorShow;
	
	private final StatusBarController mStatusBar;
	private final NavigationBarController mNavigationBar;
	
	private static final int STATE_SHOW = 0x00000001;
	private static final int STATE_ACTION_MODE = 0x00000002;
	private static final int STATE_LOCKED = 0x00000004;
	
	private final HashSet<String> mLockers = new HashSet<>();
	private int mStateFlags = STATE_SHOW;
	
	private final int mSlopShiftSize;
	private final int mLastItemLimit;
	private int mMinItemsCount;
	
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public ExpandedScreen(Activity activity, boolean enabled)
	{
		mActivity = activity;
		mStatusBar = new StatusBarController();
		mNavigationBar = new NavigationBarController();
		Resources resources = activity.getResources();
		Window window = activity.getWindow();
		boolean fullScreenLayoutEnabled = false;
		if (C.API_LOLLIPOP) fullScreenLayoutEnabled = true; else if (C.API_KITKAT)
		{
			fullScreenLayoutEnabled = getSystemBoolResource(resources, "config_enableTranslucentDecor", false);
		}
		mExpandingEnabled = enabled;
		mFullScreenLayoutEnabled = fullScreenLayoutEnabled;
		float density = ResourceUtils.obtainDensity(resources);
		mSlopShiftSize = (int) (6f * density);
		mLastItemLimit = (int) (72f * density);
		if (fullScreenLayoutEnabled)
		{
			if (C.API_LOLLIPOP)
			{
				int statusBarColor = window.getStatusBarColor() | Color.BLACK;
				int navigationBarColor = window.getNavigationBarColor() | Color.BLACK;
				window.setStatusBarColor(Color.TRANSPARENT);
				window.setNavigationBarColor(Color.TRANSPARENT);
				mContentForeground = new LollipopContentForeground(statusBarColor, navigationBarColor);
				mStatusBarContentForeground = new LollipopStatusBarForeground(statusBarColor);
				mStatusBarDrawerForeground = new LollipopDrawerForeground();
			}
			else
			{
				mContentForeground = new KitKatContentForeground();
				mStatusBarContentForeground = null;
				mStatusBarDrawerForeground = null;
			}
			window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
					View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
		}
		else
		{
			mContentForeground = null;
			mStatusBarContentForeground = null;
			mStatusBarDrawerForeground = null;
			if (enabled) window.requestFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
		}
		readConfiguration(resources.getConfiguration());
	}
	
	@Override
	public void onApplyWindowPaddings(WindowControlFrameLayout view, Rect rect)
	{
		boolean needUpdate = false;
		needUpdate |= mNavigationBar.onSystemWindowInsetsChanged(rect);
		needUpdate |= mStatusBar.onSystemWindowInsetsChanged(rect);
		if (needUpdate) onConfigurationChanged(null);
	}
	
	private class StatusBarController
	{
		private final int mHeight;
		private final int mInitialActionBarHeight;
		private boolean mShown = false; // Will be changed if enabled and transparent mode
		
		public StatusBarController()
		{
			Resources resources = mActivity.getResources();
			mHeight = getSystemDimenResource(resources, "status_bar_height", 0);
			// Height may be not changed when screen rotated in insets rect on 4.4 (tested on emulator)
			mInitialActionBarHeight = obtainActionBarHeight();
		}
		
		public int getHeight()
		{
			return mHeight;
		}
		
		public boolean isShown()
		{
			return mShown;
		}
		
		public boolean onSystemWindowInsetsChanged(Rect insets)
		{
			boolean oldStatusShown = mShown;
			boolean statusShown = insets.top != 0 && insets.top != mInitialActionBarHeight;
			if (!statusShown && insets.top != mHeight) statusShown = insets.top != obtainActionBarHeight();
			mShown = statusShown;
			return statusShown != oldStatusShown;
		}
	}
	
	private class NavigationBarController
	{
		private int mRight;
		private int mBottom;
		
		public int getRight()
		{
			return mRight;
		}
		
		public int getBottom()
		{
			return mBottom;
		}
		
		public boolean isShown()
		{
			return mRight > 0 || mBottom > 0;
		}
		
		public boolean onSystemWindowInsetsChanged(Rect insets)
		{
			int right = insets.right;
			int bottom = insets.bottom;
			if (mRight != right || mBottom != bottom)
			{
				mRight = right;
				mBottom = bottom;
				return true;
			}
			return false;
		}
	}
	
	public int getSystemDimenResource(Resources resources, String name, int fallbackValue)
	{
		int resId = resources.getIdentifier(name, "dimen", "android");
		return resId != 0 ? resources.getDimensionPixelSize(resId) : 0;
	}
	
	public boolean getSystemBoolResource(Resources resources, String name, boolean fallbackValue)
	{
		int resId = resources.getIdentifier(name, "bool", "android");
		return resId != 0 ? resources.getBoolean(resId) : fallbackValue;
	}
	
	/*
	 * The same value is hardcoded in ActionBarImpl.
	 */
	private static final int ACTION_BAR_ANIMATION_TIME = 250;
	
	private final ForegroundDrawable mContentForeground;
	private final ForegroundDrawable mStatusBarContentForeground;
	private final ForegroundDrawable mStatusBarDrawerForeground;
	
	private abstract class ForegroundDrawable extends Drawable
	{
		protected int mAlpha = 0xff;
		
		public final void applyAlpha(float value)
		{
			mAlpha = (int) (0xff * value);
			invalidateSelf();
		}
		
		@Override
		public final int getOpacity()
		{
			return 0;
		}
		
		@Override
		public final void setAlpha(int alpha)
		{
			
		}
		
		@Override
		public final void setColorFilter(ColorFilter cf)
		{
			
		}
	};
	
	private class KitKatContentForeground extends ForegroundDrawable
	{
		private final Paint mPaint = new Paint();
		
		@Override
		public void draw(Canvas canvas)
		{
			if (mStatusBar.isShown() && mAlpha != 0x00 && mAlpha != 0xff)
			{
				// Black while action bar animated
				mPaint.setColor(Color.BLACK);
				canvas.drawRect(0f, 0f, getBounds().width(), mStatusBar.getHeight(), mPaint);
			}
		}
	};
	
	private class LollipopContentForeground extends ForegroundDrawable
	{
		private final Paint mPaint = new Paint();
		private final int mStatusBarColor;
		private final int mNavigationBarColor;
		
		public LollipopContentForeground(int statusBarColor, int navigationBarColor)
		{
			mStatusBarColor = statusBarColor;
			mNavigationBarColor = navigationBarColor;
		}
		
		@Override
		public void draw(Canvas canvas)
		{
			int width = getBounds().width();
			int height = getBounds().height();
			Paint paint = mPaint;
			if (mToolbarView == null)
			{
				StatusBarController statusBar = mStatusBar;
				int statusBarHeight = statusBar.getHeight();
				if (statusBarHeight > 0)
				{
					paint.setColor(LOLLIPOP_DIM_COLOR);
					canvas.drawRect(0f, 0f, width, statusBarHeight, paint);
					if (statusBar.isShown())
					{
						paint.setColor(mStatusBarColor);
						paint.setAlpha(mAlpha);
						canvas.drawRect(0f, 0f, width, statusBarHeight, paint);
					}
				}
			}
			NavigationBarController navigationBar = mNavigationBar;
			// Add instead of sub, because metrics doesn't contain navbar size
			int navigationBarRight = navigationBar.getRight();
			int navigationBarBottom = navigationBar.getBottom();
			if (navigationBarRight > 0)
			{
				// In landscape mode NavigationBar is always visible
				paint.setColor(mNavigationBarColor);
				canvas.drawRect(width - navigationBarRight, 0, width, height, paint);
			}
			if (navigationBarBottom > 0)
			{
				paint.setColor(LOLLIPOP_DIM_COLOR);
				canvas.drawRect(0f, height - navigationBarBottom, width, height, paint);
				if (navigationBar.isShown())
				{
					paint.setColor(mNavigationBarColor);
					paint.setAlpha(mAlpha);
					canvas.drawRect(0f, height - navigationBarBottom, width, height, paint);
				}
			}
		}
	};
	
	private class LollipopStatusBarForeground extends ForegroundDrawable
	{
		private final Paint mPaint = new Paint();
		private final int mStatusBarColor;
		
		public LollipopStatusBarForeground(int statusBarColor)
		{
			mStatusBarColor = statusBarColor;
		}
		
		@Override
		public void draw(Canvas canvas)
		{
			int width = getBounds().width();
			Paint paint = mPaint;
			StatusBarController statusBar = mStatusBar;
			int statusBarHeight = statusBar.getHeight();
			if (statusBarHeight > 0)
			{
				paint.setColor(LOLLIPOP_DIM_COLOR);
				canvas.drawRect(0f, 0f, width, statusBarHeight, paint);
				if (statusBar.isShown())
				{
					paint.setColor(mStatusBarColor);
					paint.setAlpha(mAlpha);
					canvas.drawRect(0f, 0f, width, statusBarHeight, paint);
				}
			}
		}
	};
	
	private class LollipopDrawerForeground extends ForegroundDrawable
	{
		private final Paint mPaint = new Paint();
		
		@Override
		public void draw(Canvas canvas)
		{
			if (mDrawerOverToolbarEnabled && mToolbarView != null)
			{
				int width = getBounds().width();
				Paint paint = mPaint;
				StatusBarController statusBar = mStatusBar;
				if (statusBar.isShown())
				{
					mPaint.setColor(LOLLIPOP_DIM_COLOR);
					canvas.drawRect(0f, 0f, width, statusBar.getHeight(), paint);
				}
			}
		}
	};
	
	private class ForegroundAnimatorListener implements ValueAnimator.AnimatorListener,
			ValueAnimator.AnimatorUpdateListener
	{
		private final boolean mShow;
		
		public ForegroundAnimatorListener(boolean show)
		{
			mShow = show;
		}
		
		@Override
		public void onAnimationUpdate(ValueAnimator animation)
		{
			float alpha = (float) animation.getAnimatedValue();
			if (mContentForeground != null) mContentForeground.applyAlpha(alpha);
			if (mStatusBarContentForeground != null) mStatusBarContentForeground.applyAlpha(alpha);
			if (mStatusBarDrawerForeground != null) mStatusBarDrawerForeground.applyAlpha(alpha);
			if (mToolbarView != null) mToolbarView.setAlpha(alpha);
		}
		
		@Override
		public void onAnimationStart(Animator animation)
		{
			
		}
		
		@Override
		public void onAnimationEnd(Animator animation)
		{
			if (mToolbarView != null && !mShow) mActivity.getActionBar().hide();
			mForegroundAnimator = null;
		}
		
		@Override
		public void onAnimationCancel(Animator animation)
		{
			
		}
		
		@Override
		public void onAnimationRepeat(Animator animation)
		{
			
		}
	}
	
	private boolean mLastTranslucent = false;
	
	@TargetApi(Build.VERSION_CODES.KITKAT)
	private void setState(int state, boolean value)
	{
		if (mExpandingEnabled)
		{
			boolean oldShow, newShow, oldActionMode, newActionMode;
			newShow = oldShow = checkState(STATE_SHOW);
			newActionMode = oldActionMode = checkState(STATE_ACTION_MODE);
			if (state == STATE_SHOW) newShow = value;
			if (state == STATE_ACTION_MODE) newActionMode = value;
			mStateFlags = FlagUtils.set(mStateFlags, state, value);
			if (mFullScreenLayoutEnabled && C.API_KITKAT && !C.API_LOLLIPOP)
			{
				boolean wasDisplayed = oldShow || oldActionMode;
				boolean willDisplayed = newShow || newActionMode;
				if (wasDisplayed == willDisplayed) return;
				boolean translucent = !willDisplayed;
				if (mLastTranslucent != translucent)
				{
					mLastTranslucent = translucent;
					Window window = mActivity.getWindow();
					if (translucent)
					{
						window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
								| WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
					}
					else
					{
						window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
								| WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
					}
				}
			}
		}
	}
	
	private boolean checkState(int state)
	{
		return FlagUtils.get(mStateFlags, state);
	}
	
	private void applyShowActionBar(boolean show)
	{
		ActionBar actionBar = mActivity.getActionBar();
		if (mFullScreenLayoutEnabled)
		{
			boolean showing = isActionBarShowing();
			ValueAnimator foregroundAnimator = mForegroundAnimator;
			if (foregroundAnimator != null) foregroundAnimator.cancel();
			if (showing != show)
			{
				if (mToolbarView != null) actionBar.show();
				foregroundAnimator = ValueAnimator.ofFloat(show ? 0f : 1f, show ? 1f : 0f);
				ForegroundAnimatorListener listener = new ForegroundAnimatorListener(show);
				foregroundAnimator.setInterpolator(AnimationUtils.ACCELERATE_DECELERATE_INTERPOLATOR);
				foregroundAnimator.setDuration(ACTION_BAR_ANIMATION_TIME);
				foregroundAnimator.addListener(listener);
				foregroundAnimator.addUpdateListener(listener);
				foregroundAnimator.start();
				mForegroundAnimator = foregroundAnimator;
				mForegroundAnimatorShow = show;
			}
		}
		if (mToolbarView == null)
		{
			if (show) actionBar.show(); else actionBar.hide();
		}
	}
	
	private boolean isActionBarShowing()
	{
		if (!mActivity.getActionBar().isShowing()) return false;
		if (mToolbarView != null && mForegroundAnimator != null) return mForegroundAnimatorShow;
		return true;
	}
	
	private boolean mEnqueuedShowState = true;
	private long mLastShowStateChanged;
	
	private final Runnable mShowStateRunnable = () ->
	{
		if (mEnqueuedShowState != isActionBarShowing())
		{
			boolean show = mEnqueuedShowState;
			setState(STATE_SHOW, show);
			applyShowActionBar(show);
			mLastShowStateChanged = System.currentTimeMillis();
			updatePaddings(show);
		}
	};
	
	public void addLocker(String name)
	{
		mLockers.add(name);
		if (!checkState(STATE_LOCKED)) setLocked(true);
	}
	
	public void removeLocker(String name)
	{
		mLockers.remove(name);
		if (mLockers.size() == 0 && checkState(STATE_LOCKED)) setLocked(false);
	}
	
	private void setLocked(boolean locked)
	{
		setState(STATE_LOCKED, locked);
		if (locked) setShowActionBar(true, false);
	}
	
	private void setShowActionBar(boolean show, boolean delayed)
	{
		// In Lollipop ActionMode isn't depending from Toolbar (android:windowActionModeOverlay == true in theme)
		if (mToolbarView == null && checkState(STATE_ACTION_MODE) || checkState(STATE_LOCKED)) show = true;
		if (mEnqueuedShowState != show)
		{
			mEnqueuedShowState = show;
			mListView.removeCallbacks(mShowStateRunnable);
			long t = System.currentTimeMillis() - mLastShowStateChanged;
			if (show != isActionBarShowing())
			{
				if (!delayed) mShowStateRunnable.run();
				else if (t >= ACTION_BAR_ANIMATION_TIME + 200) mListView.post(mShowStateRunnable);
				else mListView.postDelayed(mShowStateRunnable, t);
			}
		}
	}
	
	public boolean isFullScreenLayoutEnabled()
	{
		return mFullScreenLayoutEnabled;
	}
	
	public void onResume()
	{
		onConfigurationChanged(mActivity.getResources().getConfiguration());
	}
	
	public void onConfigurationChanged(Configuration configuration)
	{
		if (configuration != null) readConfiguration(configuration);
		updatePaddings();
		if (mListView != null) mListView.post(mScrollerUpdater);
	}
	
	private void readConfiguration(Configuration configuration)
	{
		if (configuration.screenHeightDp != Configuration.SCREEN_HEIGHT_DP_UNDEFINED)
		{
			// Let's think that 48 dp - min list item height
			mMinItemsCount = configuration.screenHeightDp / 48;
		}
	}
	
	public void setContentListView(AbsListView listView, ScrollListenerComposite composite)
	{
		mListView = listView;
		if (mExpandingEnabled)
		{
			ListScrollTracker scrollTracker = new ListScrollTracker(this);
			if (composite != null) composite.add(new ListScrollTracker(this));
			else listView.setOnScrollListener(scrollTracker);
		}
	}
	
	public void setDrawerListView(FrameLayout drawerParent, AbsListView drawerListView, View drawerHeader)
	{
		mDrawerParent = drawerParent;
		mDrawerListView = drawerListView;
		mDrawerHeader = drawerHeader;
	}
	
	public void addAdditionalView(View additionalView, boolean considerActionBarHeight)
	{
		if (mAdditionalViews == null) mAdditionalViews = new LinkedHashMap<>();
		mAdditionalViews.put(additionalView, considerActionBarHeight);
	}
	
	public void removeAdditionalView(View additionalView)
	{
		if (mAdditionalViews != null) mAdditionalViews.remove(additionalView);
	}
	
	public void setToolbar(View toolbar, FrameLayout toolbarDrawerInterlayerLayout)
	{
		mToolbarView = toolbar;
		toolbarDrawerInterlayerLayout.setForeground(mStatusBarContentForeground);
		addAdditionalView(toolbarDrawerInterlayerLayout, false);
	}
	
	public void finishInitialization()
	{
		if (mFullScreenLayoutEnabled)
		{
			FrameLayout content = (FrameLayout) mActivity.findViewById(android.R.id.content);
			WindowControlFrameLayout frameLayout = new WindowControlFrameLayout(mActivity);
			content.addView(frameLayout, FrameLayout.LayoutParams.MATCH_PARENT,
					FrameLayout.LayoutParams.MATCH_PARENT);
			frameLayout.setOnApplyWindowPaddingsListener(this);
			frameLayout.setBackground(mContentForeground);
			if (mStatusBarDrawerForeground != null && mDrawerParent != null)
			{
				mDrawerParent.setForeground(mStatusBarDrawerForeground);
			}
		}
		updatePaddings(true);
	}
	
	public void setDrawerOverToolbarEnabled(boolean drawerOverToolbarEnabled)
	{
		mDrawerOverToolbarEnabled = drawerOverToolbarEnabled;
		if (mListView != null) updatePaddings();
	}
	
	private static final int[] ATTRS_ACTION_BAR_SIZE = {android.R.attr.actionBarSize};
	
	private int obtainActionBarHeight()
	{
		TypedArray typedArray = mActivity.obtainStyledAttributes(ATTRS_ACTION_BAR_SIZE);
		int actionHeight = typedArray.getDimensionPixelSize(0, 0);
		typedArray.recycle();
		return actionHeight;
	}
	
	public void updatePaddings()
	{
		updatePaddings(isActionBarShowing());
	}
	
	private void updatePaddings(boolean actionShowing)
	{
		if (mListView != null && (mExpandingEnabled || mFullScreenLayoutEnabled))
		{
			int actionBarHeight = obtainActionBarHeight();
			int statusBarHeight = mStatusBar.isShown() ? mStatusBar.getHeight() : 0;
			int bottomNavigationBarHeight = mNavigationBar.getBottom();
			int rightNavigationBarHeight = mNavigationBar.getRight();
			((View) mListView.getParent()).setPadding(0, 0, rightNavigationBarHeight, 0);
			mListView.setPadding(mListView.getPaddingLeft(), statusBarHeight + actionBarHeight,
					mListView.getPaddingRight(), bottomNavigationBarHeight);
			if (mActionModeView != null)
			{
				((ViewGroup.MarginLayoutParams) mActionModeView.getLayoutParams()).rightMargin =
						rightNavigationBarHeight;
			}
			if (mAdditionalViews != null)
			{
				for (LinkedHashMap.Entry<View, Boolean> additional : mAdditionalViews.entrySet())
				{
					additional.getKey().setPadding(0, statusBarHeight + (additional.getValue() ? actionBarHeight : 0),
							rightNavigationBarHeight, bottomNavigationBarHeight);
				}
			}
			if (mDrawerListView != null)
			{
				int paddingTop = C.API_LOLLIPOP && mDrawerOverToolbarEnabled && mToolbarView != null
						? statusBarHeight : statusBarHeight + actionBarHeight;
				if (mDrawerHeader != null)
				{
					mDrawerHeader.setPadding(mDrawerHeader.getPaddingLeft(), paddingTop,
							mDrawerHeader.getPaddingRight(), mDrawerHeader.getPaddingBottom());
					mDrawerListView.setPadding(mDrawerListView.getPaddingLeft(), 0,
							mDrawerListView.getPaddingRight(), bottomNavigationBarHeight);
				}
				else
				{
					mDrawerListView.setPadding(mDrawerListView.getPaddingLeft(), paddingTop,
							mDrawerListView.getPaddingRight(), bottomNavigationBarHeight);
				}
			}
			if (mContentForeground != null) mContentForeground.invalidateSelf();
			if (mStatusBarContentForeground != null) mStatusBarContentForeground.invalidateSelf();
			if (mStatusBarDrawerForeground != null) mStatusBarDrawerForeground.invalidateSelf();
		}
	}
	
	private boolean mScrollingDown;
	
	@Override
	public void onScroll(AbsListView view, int scrollY, int totalItemCount, boolean first, boolean last)
	{
		if (Math.abs(scrollY) > mSlopShiftSize) mScrollingDown = scrollY > 0;
		boolean hide = false;
		if (mScrollingDown)
		{
			if (totalItemCount > mMinItemsCount)
			{
				// List can be overscrolled when it shows first item including list top padding
				// top <= 0 means that list is not overscrolled
				if (!first || view.getChildAt(0).getTop() <= 0)
				{
					if (last)
					{
						View lastView = view.getChildAt(view.getChildCount() - 1);
						if (view.getHeight() - view.getPaddingBottom() - lastView.getBottom() + mLastItemLimit < 0)
						{
							hide = true;
						}
					}
					else hide = true;
				}
			}
		}
		setShowActionBar(!hide, true);
	}
	
	public void setActionModeState(boolean actionMode)
	{
		if (actionMode && mFullScreenLayoutEnabled && mActionModeView == null)
		{
			// ActionModeBar view has lazy initialization
			int actionModeBarId = mActivity.getResources().getIdentifier("action_mode_bar", "id", "android");
			mActionModeView = actionModeBarId != 0 ? mActivity.findViewById(actionModeBarId) : null;
			updatePaddings();
		}
		if (!actionMode && mFullScreenLayoutEnabled && C.API_MARSHMALLOW
				&& mActivity.getWindow().hasFeature(Window.FEATURE_NO_TITLE))
		{
			// Fix marshmallow bug with hidden action bar and action mode overlay
			if (mStatusGuardView == null)
			{
				View decorView = mActivity.getWindow().getDecorView();
				try
				{
					Field statusGuardField = decorView.getClass().getDeclaredField("mStatusGuard");
					statusGuardField.setAccessible(true);
					mStatusGuardView = (View) statusGuardField.get(decorView);
				}
				catch (Exception e)
				{
					
				}
			}
			if (mStatusGuardView != null) mStatusGuardView.post(mStatusGuardHideRunnable);
		}
		setState(STATE_ACTION_MODE, actionMode);
		if (!actionMode && checkState(STATE_LOCKED) && !isActionBarShowing())
		{
			// Restore action bar
			mEnqueuedShowState = false;
			setShowActionBar(true, true);
		}
	}
	
	private final Runnable mStatusGuardHideRunnable = () -> mStatusGuardView.setVisibility(View.GONE);
	
	private static final Field FAST_SCROLL_FIELD;
	private static final Method UPDATE_LAYOUT_METHOD;
	
	static
	{
		Field fastScrollField;
		Method updateLayoutMethod;
		try
		{
			fastScrollField = AbsListView.class.getDeclaredField(C.API_LOLLIPOP ? "mFastScroll" : "mFastScroller");
			fastScrollField.setAccessible(true);
			updateLayoutMethod = fastScrollField.getType().getDeclaredMethod("updateLayout");
		}
		catch (Exception e)
		{
			fastScrollField = null;
			updateLayoutMethod = null;
		}
		FAST_SCROLL_FIELD = fastScrollField;
		UPDATE_LAYOUT_METHOD = updateLayoutMethod;
	}
	
	private final Runnable mScrollerUpdater = () ->
	{
		if (UPDATE_LAYOUT_METHOD != null)
		{
			try
			{
				UPDATE_LAYOUT_METHOD.invoke(FAST_SCROLL_FIELD.get(mListView));
			}
			catch (Exception e)
			{
				
			}
		}
	};
}