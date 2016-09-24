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

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.DataSetObservable;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListAdapter;

import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.util.ResourceUtils;

@SuppressLint("ViewConstructor")
public class SortableListView extends PaddedListView implements AdapterView.OnItemLongClickListener, ListAdapter
{
	private final DataSetObservable mDataSetObservable = new DataSetObservable();
	private final Paint mActivePaint;
	private final View mFakeView;

	private ListAdapter mWrappedAdapter;
	private OnItemLongClickListener mLongClickListener;
	private OnFinishedListener mFinishedListener;
	private OnStateChangedListener mStateChangedListener;

	private int mAllowStart = INVALID_POSITION, mAllowEnd = INVALID_POSITION;
	private int mStartPosition = INVALID_POSITION, mPosition = INVALID_POSITION;
	private boolean mAllowStartSorting;
	private float mFingerY;
	private View mDrawView;

	public interface OnFinishedListener
	{
		public void onSortingFinished(SortableListView listView, int oldPosition, int newPosition);
	}

	public interface OnStateChangedListener
	{
		public void onSortingStateChanged(SortableListView listView, boolean sorting);
	}

	public SortableListView(Context context, Context unstyledContext)
	{
		super(context);
		super.setOnItemLongClickListener(this);
		setLongClickable(true);
		int activeColor = ResourceUtils.getColor(unstyledContext != null ? unstyledContext : context,
				R.attr.colorAccentSupport);
		PorterDuffColorFilter colorFilter = new PorterDuffColorFilter(activeColor, PorterDuff.Mode.SRC_ATOP);
		mActivePaint = new Paint();
		mActivePaint.setColorFilter(colorFilter);
		mFakeView = new View(context);
		mFakeView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, 0));
	}

	@Override
	public void setOnItemLongClickListener(OnItemLongClickListener listener)
	{
		mLongClickListener = listener;
	}

	public void setOnSortingFinishedListener(OnFinishedListener listener)
	{
		mFinishedListener = listener;
	}

	public void setOnSortingStateChangedListener(OnStateChangedListener listener)
	{
		mStateChangedListener = listener;
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev)
	{
		if (mStartPosition != INVALID_POSITION) return true;
		return super.onInterceptTouchEvent(ev);
	}

	private final Runnable mScroller = new Runnable()
	{
		@Override
		public void run()
		{
			int duration = 20;
			if (mScrollSpeed != 0f)
			{
				updatePosition();
				smoothScrollBy((int) (mScrollSpeed / 2f * mFakeView.getHeight()), duration);
			}
			postDelayed(this, duration);
		}
	};

	private void updatePosition()
	{
		float y = mLastY - mFingerY + mFakeView.getHeight() / 2;
		int position = mPosition;
		for (int i = 0, count = getChildCount(); i < count; i++)
		{
			View view = getChildAt(i);
			int top = view.getTop();
			if (top <= y && (top + view.getHeight() > y || i + 1 == count))
			{
				position = getFirstVisiblePosition() + i;
				break;
			}
		}
		if (position < mAllowStart) position = mAllowStart;
		if (position > mAllowEnd) position = mAllowEnd;
		int topBound = getHeight() / 4;
		int bottomBound = getHeight() - topBound;
		float speed = 0f;
		if (y < topBound) speed = (y - topBound) / topBound;
		else if (y > bottomBound) speed = (y - bottomBound) / (getHeight() - bottomBound);
		mScrollSpeed = speed;
		if (position != mPosition)
		{
			mPosition = position;
			mDataSetObservable.notifyChanged();
		}
		else
		{
			invalidate(0, mFakeView.getTop() - mFakeView.getHeight(), getWidth(),
					mFakeView.getTop() + 2 * mFakeView.getHeight());
		}
	}

	private float mLastY;
	private float mScrollSpeed;

	@Override
	public boolean onTouchEvent(MotionEvent ev)
	{
		mLastY = ev.getY();
		if (mStartPosition != INVALID_POSITION)
		{
			switch (ev.getAction())
			{
				case MotionEvent.ACTION_MOVE:
				{
					updatePosition();
					break;
				}
				case MotionEvent.ACTION_UP:
				{
					if (mPosition != mStartPosition && mFinishedListener != null)
					{
						mFinishedListener.onSortingFinished(this, mStartPosition, mPosition);
					}
				}
				case MotionEvent.ACTION_CANCEL:
				{
					if (mStateChangedListener != null) mStateChangedListener.onSortingStateChanged(this, false);
					mPosition = mStartPosition = INVALID_POSITION;
					mDrawView = null;
					mDataSetObservable.notifyChanged();
					removeCallbacks(mScroller);
					break;
				}
			}
			return true;
		}
		return super.onTouchEvent(ev);
	}

	@Override
	protected void onDraw(Canvas canvas)
	{
		super.onDraw(canvas);
		if (mDrawView != null)
		{
			float top = mLastY - mFingerY;
			int first = getFirstVisiblePosition();
			int last = first + getChildCount() - 1;
			if (mAllowStart >= first && mAllowStart <= last)
			{
				View view = getChildAt(mAllowStart - first);
				int startTop = view.getTop();
				if (top < startTop) top = startTop;
			}
			if (mAllowEnd >= first && mAllowEnd <= last)
			{
				View view = getChildAt(mAllowEnd - first);
				int endTop = view.getTop();
				if (top > endTop) top = endTop;
			}
			int saveCount = canvas.save();
			canvas.translate(-getLeftPaddingOffset(), top);
			canvas.saveLayer(0, 0, mDrawView.getWidth(), mDrawView.getHeight(), mActivePaint, Canvas.ALL_SAVE_FLAG);
			mDrawView.draw(canvas);
			canvas.restoreToCount(saveCount);
			updatePosition();
		}
	}

	@Override
	public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id)
	{
		mAllowStartSorting = true;
		try
		{
			if (mLongClickListener != null) return mLongClickListener.onItemLongClick(parent, view, position, id);
			return false;
		}
		finally
		{
			mAllowStartSorting = false;
		}
	}

	public boolean startSorting(int start, int end, int position)
	{
		if (mAllowStartSorting && end > start && position >= start && position <= end &&
				mStartPosition == INVALID_POSITION)
		{
			int first = getFirstVisiblePosition();
			View view = getChildAt(position - first);
			mFingerY = mLastY - view.getTop();
			if (mFingerY > view.getHeight() || mFingerY < 0) return false;
			mAllowStart = start;
			mAllowEnd = end;
			if (mStateChangedListener != null) mStateChangedListener.onSortingStateChanged(this, true);
			int width = view.getWidth();
			int height = view.getHeight();
			mDrawView = mWrappedAdapter.getView(position, null, this);
			mDrawView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
					MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
			mDrawView.layout(0, 0, width, height);
			mPosition = mStartPosition = position;
			mFakeView.getLayoutParams().height = height;
			mDataSetObservable.notifyChanged();
			mScrollSpeed = 0f;
			post(mScroller);
			return true;
		}
		return false;
	}

	public boolean isSorting()
	{
		return mStartPosition != INVALID_POSITION;
	}

	@Override
	public void setAdapter(ListAdapter adapter)
	{
		mWrappedAdapter = adapter;
		super.setAdapter(adapter != null ? this : null);
	}

	private int transformPosition(int position)
	{
		if (mStartPosition != INVALID_POSITION)
		{
			if (mPosition > mStartPosition && position >= mStartPosition && position < mPosition)
			{
				position++;
			}
			else if (mPosition < mStartPosition && position <= mStartPosition && position > mPosition)
			{
				position--;
			}
		}
		return position;
	}

	@Override
	public void registerDataSetObserver(DataSetObserver observer)
	{
		mDataSetObservable.registerObserver(observer);
		mWrappedAdapter.registerDataSetObserver(observer);
	}

	@Override
	public void unregisterDataSetObserver(DataSetObserver observer)
	{
		mDataSetObservable.unregisterObserver(observer);
		mWrappedAdapter.unregisterDataSetObserver(observer);
	}

	@Override
	public int getCount()
	{
		return mWrappedAdapter.getCount();
	}

	@Override
	public Object getItem(int position)
	{
		if (position == mPosition) return null;
		return mWrappedAdapter.getItem(transformPosition(position));
	}

	@Override
	public long getItemId(int position)
	{
		if (position == mPosition) return 0L;
		return mWrappedAdapter.getItemId(transformPosition(position));
	}

	@Override
	public boolean hasStableIds()
	{
		return mWrappedAdapter.hasStableIds();
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent)
	{
		if (position == mPosition) return mFakeView;
		return mWrappedAdapter.getView(transformPosition(position), convertView, parent);
	}

	@Override
	public int getItemViewType(int position)
	{
		if (position == mPosition) return IGNORE_ITEM_VIEW_TYPE;
		return mWrappedAdapter.getItemViewType(transformPosition(position));
	}

	@Override
	public int getViewTypeCount()
	{
		return mWrappedAdapter.getViewTypeCount();
	}

	@Override
	public boolean isEmpty()
	{
		return mWrappedAdapter.isEmpty();
	}

	@Override
	public boolean areAllItemsEnabled()
	{
		return mWrappedAdapter.areAllItemsEnabled();
	}

	@Override
	public boolean isEnabled(int position)
	{
		return mWrappedAdapter.isEnabled(transformPosition(position));
	}
}