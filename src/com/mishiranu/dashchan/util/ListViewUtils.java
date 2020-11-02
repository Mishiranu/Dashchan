package com.mishiranu.dashchan.util;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.view.ViewParent;
import android.widget.AdapterView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;
import com.mishiranu.dashchan.widget.ThemeEngine;

public class ListViewUtils {
	public interface DataCallback<T> {
		T getData(int position);
	}

	public interface ClickCallback<T, VH> {
		boolean onItemClick(VH holder, int position, T item, boolean longClick);
	}

	public interface SimpleCallback<T> extends ClickCallback<T, RecyclerView.ViewHolder> {
		void onItemClick(T item);
		boolean onItemLongClick(T item);

		@Override
		default boolean onItemClick(RecyclerView.ViewHolder holder, int position, T item, boolean longClick) {
			if (longClick) {
				return onItemLongClick(item);
			} else {
				onItemClick(item);
				return true;
			}
		}
	}

	private static <T, VH extends RecyclerView.ViewHolder> boolean handleClick(VH holder,
			boolean longClick, DataCallback<T> dataCallback, ClickCallback<T, VH> callback) {
		int position = holder.getAdapterPosition();
		// position can be NO_POSITION if click event is fired after notifyDataSetChanged
		return position >= 0 && callback.onItemClick(holder, position,
				dataCallback != null ? dataCallback.getData(position) : null, longClick);
	}

	public static <T, VH extends RecyclerView.ViewHolder> VH bind(VH holder, View view,
			boolean longClick, DataCallback<T> dataCallback, ClickCallback<T, VH> clickCallback) {
		view.setOnClickListener(v -> handleClick(holder, false, dataCallback, clickCallback));
		if (longClick) {
			view.setOnLongClickListener(v -> handleClick(holder, true, dataCallback, clickCallback));
		}
		return holder;
	}

	public static <T, VH extends RecyclerView.ViewHolder> VH bind(VH holder,
			boolean longClick, DataCallback<T> dataCallback, ClickCallback<T, VH> clickCallback) {
		return bind(holder, holder.itemView, longClick, dataCallback, clickCallback);
	}

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

	// Unlimited pool size for immediate scrolls to improve performance
	public static class UnlimitedRecycledViewPool extends RecyclerView.RecycledViewPool {
		@Override
		public void putRecycledView(RecyclerView.ViewHolder scrap) {
			setMaxRecycledViews(scrap.getItemViewType(), Integer.MAX_VALUE);
			super.putRecycledView(scrap);
		}
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
		if (AnimationUtils.areAnimatorsEnabled()) {
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
		} else {
			layoutManager.scrollToPositionWithOffset(position, 0);
		}
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
