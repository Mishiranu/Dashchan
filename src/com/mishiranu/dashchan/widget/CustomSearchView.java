package com.mishiranu.dashchan.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.view.CollapsibleActionView;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SearchView;
import android.widget.Toolbar;
import com.mishiranu.dashchan.C;

public class CustomSearchView extends FrameLayout implements CollapsibleActionView {
	public interface OnSubmitListener {
		boolean onSubmit(String query);
	}

	public interface OnChangeListener {
		void onChange(String query);
	}

	private final SearchView searchView;
	private final FrameLayout customViewLayout;
	private final int contentInsetEnd;

	private OnSubmitListener onSubmitListener;
	private OnChangeListener onChangeListener;

	public CustomSearchView(Context context) {
		super(context);

		setFocusable(true);
		setFocusableInTouchMode(true);
		LinearLayout layout = new LinearLayout(context);
		layout.setOrientation(LinearLayout.HORIZONTAL);
		addView(layout, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		searchView = new SearchView(context);
		searchView.setMaxWidth(Integer.MAX_VALUE);
		disableSaveInstanceState(searchView);
		layout.addView(searchView, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
		customViewLayout = new FrameLayout(context);
		layout.addView(customViewLayout, LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
		searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
			@Override
			public boolean onQueryTextSubmit(String query) {
				if (onSubmitListener == null || onSubmitListener.onSubmit(query)) {
					searchView.clearFocus();
					requestFocus();
				}
				return true;
			}

			@Override
			public boolean onQueryTextChange(String newText) {
				if (onChangeListener != null) {
					onChangeListener.onChange(newText);
				}
				return true;
			}
		});

		int contentInsetEnd = 0;
		if (C.API_LOLLIPOP) {
			TypedArray typedArray = context.obtainStyledAttributes(null,
					new int[] {android.R.attr.contentInsetEnd}, android.R.attr.actionBarStyle, 0);
			contentInsetEnd = typedArray.getDimensionPixelSize(0, 0);
			typedArray.recycle();
		}
		this.contentInsetEnd = contentInsetEnd;
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		super.onLayout(changed, left, top, right, bottom);

		// Ignore Toolbar's content inset at the end if there is only free space left
		// There should be "home" button at the start, so this side is not handled at all
		if (C.API_LOLLIPOP && contentInsetEnd > 0 && getParent() instanceof Toolbar) {
			View layout = getChildAt(0);
			FrameLayout.LayoutParams layoutParams = (LayoutParams) layout.getLayoutParams();
			Toolbar toolbar = (Toolbar) getParent();
			boolean relayout = false;
			if (getLayoutDirection() == LAYOUT_DIRECTION_RTL) {
				boolean apply = left == contentInsetEnd;
				if (apply == (layoutParams.leftMargin == 0)) {
					layoutParams.leftMargin = apply ? -contentInsetEnd : 0;
					relayout = true;
				}
			} else {
				boolean apply = toolbar.getMeasuredWidth() == right + contentInsetEnd;
				if (apply == (layoutParams.rightMargin == 0)) {
					layoutParams.rightMargin = apply ? -contentInsetEnd : 0;
					relayout = true;
				}
			}
			if (relayout) {
				layout.requestLayout();
			}
		}
	}

	private static void disableSaveInstanceState(View view) {
		view.setSaveEnabled(false);
		if (view instanceof ViewGroup) {
			ViewGroup viewGroup = (ViewGroup) view;
			int childCount = viewGroup.getChildCount();
			for (int i = 0; i < childCount; i++) {
				View child = viewGroup.getChildAt(i);
				disableSaveInstanceState(child);
			}
		}
	}

	public void setHint(CharSequence hint) {
		searchView.setQueryHint(hint);
	}

	public String getQuery() {
		return searchView.getQuery().toString();
	}

	public void setQuery(String query) {
		searchView.setQuery(query, false);
	}

	public void setOnSubmitListener(OnSubmitListener listener) {
		onSubmitListener = listener;
	}

	public void setOnChangeListener(OnChangeListener listener) {
		onChangeListener = listener;
	}

	public void setCustomView(View view) {
		customViewLayout.removeAllViews();
		if (view != null) {
			customViewLayout.addView(view, LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
		}
	}

	@Override
	public void onActionViewExpanded() {
		searchView.onActionViewExpanded();
	}

	@Override
	public void onActionViewCollapsed() {
		searchView.onActionViewCollapsed();
	}

	@Override
	public boolean dispatchKeyEventPreIme(KeyEvent event) {
		if (searchView.hasFocus() && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
			if (event.getAction() == KeyEvent.ACTION_UP) {
				searchView.clearFocus();
				requestFocus();
			}
			return true;
		} else {
			return super.dispatchKeyEventPreIme(event);
		}
	}
}
