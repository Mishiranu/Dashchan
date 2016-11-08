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

import android.content.Context;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.SearchView;
import android.widget.TextView;

public class CustomSearchView extends SearchView {
	private final FrameLayout mCustomViewLayout;
	private final TextView mTextView;

	public CustomSearchView(Context context) {
		super(context);
		setMaxWidth(Integer.MAX_VALUE);
		if (getChildCount() == 1) {
			View view = getChildAt(0);
			LayoutParams layoutParams = (LayoutParams) view.getLayoutParams();
			layoutParams.width = LayoutParams.WRAP_CONTENT;
			layoutParams.weight = 1f;
		}
		mCustomViewLayout = new FrameLayout(context);
		addView(mCustomViewLayout, LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
		super.setIconifiedByDefault(true); // Hide search button, show hint image
		super.setIconified(false); // Always expanded
		super.onActionViewExpanded();
		TextView textView;
		try {
			Field field = SearchView.class.getDeclaredField("mSearchSrcTextView");
			field.setAccessible(true);
			textView = (TextView) field.get(this);
		} catch (Exception e1) {
			try {
				Field field = SearchView.class.getDeclaredField("mQueryTextView");
				field.setAccessible(true);
				textView = (TextView) field.get(this);
			} catch (Exception e2) {
				textView = null;
			}
		}
		mTextView = textView;
		View closeButton;
		try {
			Field field = SearchView.class.getDeclaredField("mCloseButton");
			field.setAccessible(true);
			closeButton = (View) field.get(this);
		} catch (Exception e) {
			closeButton = null;
		}
		if (closeButton != null) {
			closeButton.setBackground(null);
			closeButton.setOnClickListener(v -> {
				setQuery("", false);
				showKeyboard();
			});
		}
		super.setOnQueryTextListener(new OnQueryTextListener() {
			@Override
			public boolean onQueryTextSubmit(String query) {
				boolean hideKeyboard = true;
				if (mOnQueryTextListener != null) {
					hideKeyboard = mOnQueryTextListener.onQueryTextSubmit(query);
				}
				if (hideKeyboard) {
					hideKeyboard();
				}
				return true;
			}

			@Override
			public boolean onQueryTextChange(String newText) {
				if (mOnQueryTextListener != null) {
					mOnQueryTextListener.onQueryTextChange(newText);
				}
				return true;
			}
		});
	}

	private void showKeyboard() {
		if (mTextView != null) {
			mTextView.requestFocus();
			InputMethodManager inputMethodManager = (InputMethodManager) getContext()
					.getSystemService(Context.INPUT_METHOD_SERVICE);
			if (inputMethodManager != null) {
				inputMethodManager.showSoftInput(mTextView, 0);
			}
		}
	}

	private void hideKeyboard() {
		InputMethodManager inputMethodManager = (InputMethodManager) getContext()
				.getSystemService(Context.INPUT_METHOD_SERVICE);
		if (inputMethodManager != null) {
			inputMethodManager.hideSoftInputFromWindow(getWindowToken(), 0);
		}
		clearFocus();
	}

	private boolean mShowKeyboard;

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		mShowKeyboard = true;
	}

	@Override
	protected void onDetachedFromWindow() {
		hideKeyboard();
		super.onDetachedFromWindow();
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		super.onLayout(changed, left, top, right, bottom);
		if (mShowKeyboard) {
			mShowKeyboard = false;
			post(() -> post(() -> showKeyboard()));
		}
	}

	private OnQueryTextListener mOnQueryTextListener;

	@Override
	public void setOnQueryTextListener(OnQueryTextListener listener) {
		mOnQueryTextListener = listener;
	}

	@Override
	public void setIconified(boolean iconify) {
		// Block super method call
	}

	@Override
	public void setIconifiedByDefault(boolean iconified) {
		// Block super method call
	}

	@Override
	public void onActionViewExpanded() {
		// Block super method call
	}

	@Override
	public void onActionViewCollapsed() {
		// Block super method call
	}

	public void setCustomView(View view) {
		mCustomViewLayout.removeAllViews();
		if (view != null) {
			mCustomViewLayout.addView(view, LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
		}
	}
}