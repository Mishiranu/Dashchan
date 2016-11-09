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

import java.util.ArrayList;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AbsListView;

public class PullableListView extends PaddedListView implements PullableWrapper.Wrapped, AbsListView.OnScrollListener {
	private final PullableWrapper wrapper = new PullableWrapper(this);

	public PullableListView(Context context) {
		this(context, null);
	}

	public PullableListView(Context context, AttributeSet attrs) {
		this(context, attrs, android.R.attr.listViewStyle);
	}

	public PullableListView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		super.setOnScrollListener(this);
		wrapper.handleAttrs(attrs, defStyleAttr, 0);
	}

	private OnScrollListener onScrollListener;

	@Override
	public void setOnScrollListener(OnScrollListener listener) {
		onScrollListener = listener;
	}

	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		wrapper.onTouchEvent(ev);
		return super.onTouchEvent(ev);
	}

	@Override
	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
		wrapper.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
		if (onScrollListener != null) {
			onScrollListener.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
		}
	}

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {
		wrapper.onScrollStateChanged(view, scrollState);
		if (onScrollListener != null) {
			onScrollListener.onScrollStateChanged(view, scrollState);
		}
	}

	public PullableWrapper getWrapper() {
		return wrapper;
	}

	@Override
	public void draw(Canvas canvas) {
		wrapper.drawBefore(canvas);
		try {
			super.draw(canvas);
		} finally {
			wrapper.drawAfter(canvas);
		}
	}

	public interface OnBeforeLayoutListener {
		public void onBeforeLayout(View v, int left, int top, int right, int bottom);
	}

	private final ArrayList<OnBeforeLayoutListener> beforeLayoutListeners = new ArrayList<>();

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		for (OnBeforeLayoutListener listener : beforeLayoutListeners) {
			listener.onBeforeLayout(this, l, t, r, b);
		}
		super.onLayout(changed, l, t, r, b);
	}

	public void addOnBeforeLayoutListener(OnBeforeLayoutListener listener) {
		beforeLayoutListeners.add(listener);
	}

	public void removeOnBeforeLayoutListener(OnBeforeLayoutListener listener) {
		beforeLayoutListeners.remove(listener);
	}
}