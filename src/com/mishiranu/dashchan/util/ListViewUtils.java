package com.mishiranu.dashchan.util;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.AdapterView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;
import com.mishiranu.dashchan.widget.ThemeEngine;

public class ListViewUtils {
	public static View getRootViewInList(View view) {
		while (view != null) {
			ViewParent parent = view.getParent();
			if (parent == null || parent instanceof AdapterView<?> || parent instanceof RecyclerView) {
				break;
			}
			view = parent instanceof View ? (View) parent : null;
		}
		return view;
	}

	@SuppressWarnings("unchecked")
	public static <T> T getViewHolder(View view, Class<T> clazz) {
		view = getRootViewInList(view);
		View parent = (View) view.getParent();
		Object holder;
		if (parent instanceof RecyclerView) {
			holder = ((RecyclerView) parent).getChildViewHolder(view);
		} else {
			holder = view.getTag();
		}
		return holder != null && clazz.isAssignableFrom(holder.getClass()) ? (T) holder : null;
	}

	public static void cancelListFling(ViewGroup viewGroup) {
		MotionEvent motionEvent;
		motionEvent = MotionEvent.obtain(0, SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, 0, 0, 0);
		viewGroup.onTouchEvent(motionEvent);
		motionEvent.recycle();
		motionEvent = MotionEvent.obtain(0, SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, 0, 0, 0);
		viewGroup.onTouchEvent(motionEvent);
		motionEvent.recycle();
	}

	private static class TopLinearSmoothScroller extends LinearSmoothScroller {
		public TopLinearSmoothScroller(Context context, int targetPosition) {
			super(context);
			setTargetPosition(targetPosition);
		}

		@Override
		protected int getVerticalSnapPreference() {
			return SNAP_TO_START;
		}
	}

	public static int getScrollJumpThreshold(Context context) {
		return context.getResources().getConfiguration().screenHeightDp / 40;
	}

	public static void smoothScrollToPosition(RecyclerView recyclerView, int position) {
		LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
		int first = layoutManager.findFirstVisibleItemPosition();
		if (first >= 0) {
			int jumpThreshold = getScrollJumpThreshold(recyclerView.getContext());
			if (position > first + jumpThreshold) {
				layoutManager.scrollToPositionWithOffset(position - jumpThreshold, 0);
			} else if (position < first - jumpThreshold) {
				layoutManager.scrollToPositionWithOffset(position + jumpThreshold, 0);
			}
		}
		layoutManager.startSmoothScroll(new TopLinearSmoothScroller(recyclerView.getContext(), position));
	}

	public static Drawable colorizeListThumbDrawable4(Context context, Drawable drawable) {
		int colorDefault = ThemeEngine.getTheme(context).accent;
		int colorPressed = GraphicsUtils.modifyColorGain(colorDefault, 4f / 3f);
		if (colorDefault != 0 && colorPressed != 0) {
			final int[] pressedState = {android.R.attr.state_pressed};
			final int[] defaultState = {};
			drawable.setState(pressedState);
			final Drawable pressedDrawable = drawable.getCurrent();
			drawable.setState(defaultState);
			final Drawable defaultDrawable = drawable.getCurrent();
			if (defaultDrawable != pressedDrawable) {
				StateListDrawable stateListDrawable = new StateListDrawable() {
					@SuppressWarnings("deprecation")
					@Override
					protected boolean onStateChange(int[] stateSet) {
						boolean result = super.onStateChange(stateSet);
						if (result) {
							setColorFilter(getCurrent() == pressedDrawable ? colorPressed
									: colorDefault, PorterDuff.Mode.SRC_IN);
						}
						return result;
					}
				};
				stateListDrawable.addState(pressedState, pressedDrawable);
				stateListDrawable.addState(defaultState, defaultDrawable);
				return stateListDrawable;
			}
		}
		return drawable;
	}
}
