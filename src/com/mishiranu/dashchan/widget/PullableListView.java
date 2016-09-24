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

public class PullableListView extends PaddedListView implements PullableWrapper.Wrapped, AbsListView.OnScrollListener
{
	private final PullableWrapper mWrapper = new PullableWrapper(this);

	public PullableListView(Context context)
	{
		this(context, null);
	}

	public PullableListView(Context context, AttributeSet attrs)
	{
		this(context, attrs, android.R.attr.listViewStyle);
	}

	public PullableListView(Context context, AttributeSet attrs, int defStyleAttr)
	{
		super(context, attrs, defStyleAttr);
		super.setOnScrollListener(this);
		mWrapper.handleAttrs(attrs, defStyleAttr, 0);
	}

	private OnScrollListener mOnScrollListener;

	@Override
	public void setOnScrollListener(OnScrollListener l)
	{
		mOnScrollListener = l;
	}

	@Override
	public boolean onTouchEvent(MotionEvent ev)
	{
		mWrapper.onTouchEvent(ev);
		return super.onTouchEvent(ev);
	}

	@Override
	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount)
	{
		mWrapper.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
		if (mOnScrollListener != null)
		{
			mOnScrollListener.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
		}
	}

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState)
	{
		mWrapper.onScrollStateChanged(view, scrollState);
		if (mOnScrollListener != null) mOnScrollListener.onScrollStateChanged(view, scrollState);
	}

	public PullableWrapper getWrapper()
	{
		return mWrapper;
	}

	@Override
	public void draw(Canvas canvas)
	{
		mWrapper.drawBefore(canvas);
		try
		{
			super.draw(canvas);
		}
		finally
		{
			mWrapper.drawAfter(canvas);
		}
	}

	public interface OnBeforeLayoutListener
	{
		public void onBeforeLayout(View v, int left, int top, int right, int bottom);
	}

	private final ArrayList<OnBeforeLayoutListener> mBeforeLayoutListeners = new ArrayList<>();

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b)
	{
		for (OnBeforeLayoutListener listener : mBeforeLayoutListeners) listener.onBeforeLayout(this, l, t, r, b);
		super.onLayout(changed, l, t, r, b);
	}

	public void addOnBeforeLayoutListener(OnBeforeLayoutListener listener)
	{
		mBeforeLayoutListeners.add(listener);
	}

	public void removeOnBeforeLayoutListener(OnBeforeLayoutListener listener)
	{
		mBeforeLayoutListeners.remove(listener);
	}
}