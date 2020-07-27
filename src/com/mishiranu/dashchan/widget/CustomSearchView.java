package com.mishiranu.dashchan.widget;

import android.content.Context;
import android.view.CollapsibleActionView;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SearchView;

public class CustomSearchView extends LinearLayout implements CollapsibleActionView {
	public interface OnSubmitListener {
		boolean onSubmit(String query);
	}

	public interface OnChangeListener {
		void onChange(String query);
	}

	private final SearchView searchView;
	private final FrameLayout customViewLayout;

	private OnSubmitListener onSubmitListener;
	private OnChangeListener onChangeListener;

	public CustomSearchView(Context context) {
		super(context);

		setFocusable(true);
		setFocusableInTouchMode(true);
		setOrientation(LinearLayout.HORIZONTAL);
		searchView = new SearchView(context);
		searchView.setMaxWidth(Integer.MAX_VALUE);
		addView(searchView, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
		customViewLayout = new FrameLayout(context);
		addView(customViewLayout, LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
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
