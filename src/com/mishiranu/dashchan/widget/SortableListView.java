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
public class SortableListView extends PaddedListView implements AdapterView.OnItemLongClickListener, ListAdapter {
	private final DataSetObservable dataSetObservable = new DataSetObservable();
	private final Paint activePaint;
	private final View fakeView;

	private ListAdapter wrappedAdapter;
	private OnItemLongClickListener longClickListener;
	private OnFinishedListener finishedListener;
	private OnStateChangedListener stateChangedListener;

	private int allowStart = INVALID_POSITION, allowEnd = INVALID_POSITION;
	private int startPosition = INVALID_POSITION, position = INVALID_POSITION;
	private boolean allowStartSorting;
	private float fingerY;
	private View drawView;

	public interface OnFinishedListener {
		public void onSortingFinished(SortableListView listView, int oldPosition, int newPosition);
	}

	public interface OnStateChangedListener {
		public void onSortingStateChanged(SortableListView listView, boolean sorting);
	}

	public SortableListView(Context context, Context unstyledContext) {
		super(context);
		super.setOnItemLongClickListener(this);
		setLongClickable(true);
		int activeColor = ResourceUtils.getColor(unstyledContext != null ? unstyledContext : context,
				R.attr.colorAccentSupport);
		PorterDuffColorFilter colorFilter = new PorterDuffColorFilter(activeColor, PorterDuff.Mode.SRC_ATOP);
		activePaint = new Paint();
		activePaint.setColorFilter(colorFilter);
		fakeView = new View(context);
		fakeView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, 0));
	}

	@Override
	public void setOnItemLongClickListener(OnItemLongClickListener listener) {
		longClickListener = listener;
	}

	public void setOnSortingFinishedListener(OnFinishedListener listener) {
		finishedListener = listener;
	}

	public void setOnSortingStateChangedListener(OnStateChangedListener listener) {
		stateChangedListener = listener;
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		if (startPosition != INVALID_POSITION) {
			return true;
		}
		return super.onInterceptTouchEvent(ev);
	}

	private final Runnable scroller = new Runnable() {
		@Override
		public void run() {
			int duration = 20;
			if (scrollSpeed != 0f) {
				updatePosition();
				smoothScrollBy((int) (scrollSpeed / 2f * fakeView.getHeight()), duration);
			}
			postDelayed(this, duration);
		}
	};

	private void updatePosition() {
		float y = lastY - fingerY + fakeView.getHeight() / 2;
		int position = this.position;
		for (int i = 0, count = getChildCount(); i < count; i++) {
			View view = getChildAt(i);
			int top = view.getTop();
			if (top <= y && (top + view.getHeight() > y || i + 1 == count)) {
				position = getFirstVisiblePosition() + i;
				break;
			}
		}
		if (position < allowStart) {
			position = allowStart;
		}
		if (position > allowEnd) {
			position = allowEnd;
		}
		int topBound = getHeight() / 4;
		int bottomBound = getHeight() - topBound;
		float speed = 0f;
		if (y < topBound) {
			speed = (y - topBound) / topBound;
		} else if (y > bottomBound) {
			speed = (y - bottomBound) / (getHeight() - bottomBound);
		}
		scrollSpeed = speed;
		if (position != this.position) {
			this.position = position;
			dataSetObservable.notifyChanged();
		} else {
			invalidate(0, fakeView.getTop() - fakeView.getHeight(), getWidth(),
					fakeView.getTop() + 2 * fakeView.getHeight());
		}
	}

	private float lastY;
	private float scrollSpeed;

	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		lastY = ev.getY();
		if (startPosition != INVALID_POSITION) {
			switch (ev.getAction()) {
				case MotionEvent.ACTION_MOVE: {
					updatePosition();
					break;
				}
				case MotionEvent.ACTION_UP: {
					if (position != startPosition && finishedListener != null) {
						finishedListener.onSortingFinished(this, startPosition, position);
					}
				}
				case MotionEvent.ACTION_CANCEL: {
					if (stateChangedListener != null) {
						stateChangedListener.onSortingStateChanged(this, false);
					}
					position = startPosition = INVALID_POSITION;
					drawView = null;
					dataSetObservable.notifyChanged();
					removeCallbacks(scroller);
					break;
				}
			}
			return true;
		}
		return super.onTouchEvent(ev);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		if (drawView != null) {
			float top = lastY - fingerY;
			int first = getFirstVisiblePosition();
			int last = first + getChildCount() - 1;
			if (allowStart >= first && allowStart <= last) {
				View view = getChildAt(allowStart - first);
				int startTop = view.getTop();
				if (top < startTop) {
					top = startTop;
				}
			}
			if (allowEnd >= first && allowEnd <= last) {
				View view = getChildAt(allowEnd - first);
				int endTop = view.getTop();
				if (top > endTop) {
					top = endTop;
				}
			}
			int saveCount = canvas.save();
			canvas.translate(-getLeftPaddingOffset(), top);
			canvas.saveLayer(0, 0, drawView.getWidth(), drawView.getHeight(), activePaint, Canvas.ALL_SAVE_FLAG);
			drawView.draw(canvas);
			canvas.restoreToCount(saveCount);
			updatePosition();
		}
	}

	@Override
	public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
		allowStartSorting = true;
		try {
			if (longClickListener != null) {
				return longClickListener.onItemLongClick(parent, view, position, id);
			}
			return false;
		} finally {
			allowStartSorting = false;
		}
	}

	public boolean startSorting(int start, int end, int position) {
		if (allowStartSorting && end > start && position >= start && position <= end &&
				startPosition == INVALID_POSITION) {
			int first = getFirstVisiblePosition();
			View view = getChildAt(position - first);
			fingerY = lastY - view.getTop();
			if (fingerY > view.getHeight() || fingerY < 0) {
				return false;
			}
			allowStart = start;
			allowEnd = end;
			if (stateChangedListener != null) {
				stateChangedListener.onSortingStateChanged(this, true);
			}
			int width = view.getWidth();
			int height = view.getHeight();
			drawView = wrappedAdapter.getView(position, null, this);
			drawView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
					MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
			drawView.layout(0, 0, width, height);
			this.position = startPosition = position;
			fakeView.getLayoutParams().height = height;
			dataSetObservable.notifyChanged();
			scrollSpeed = 0f;
			post(scroller);
			return true;
		}
		return false;
	}

	public boolean isSorting() {
		return startPosition != INVALID_POSITION;
	}

	@Override
	public void setAdapter(ListAdapter adapter) {
		wrappedAdapter = adapter;
		super.setAdapter(adapter != null ? this : null);
	}

	private int transformPosition(int position) {
		if (startPosition != INVALID_POSITION) {
			if (this.position > startPosition && position >= startPosition && position < this.position) {
				position++;
			} else if (this.position < startPosition && position <= startPosition && position > this.position) {
				position--;
			}
		}
		return position;
	}

	@Override
	public void registerDataSetObserver(DataSetObserver observer) {
		dataSetObservable.registerObserver(observer);
		wrappedAdapter.registerDataSetObserver(observer);
	}

	@Override
	public void unregisterDataSetObserver(DataSetObserver observer) {
		dataSetObservable.unregisterObserver(observer);
		wrappedAdapter.unregisterDataSetObserver(observer);
	}

	@Override
	public int getCount() {
		return wrappedAdapter.getCount();
	}

	@Override
	public Object getItem(int position) {
		if (position == this.position) {
			return null;
		}
		return wrappedAdapter.getItem(transformPosition(position));
	}

	@Override
	public long getItemId(int position) {
		if (position == this.position) {
			return 0L;
		}
		return wrappedAdapter.getItemId(transformPosition(position));
	}

	@Override
	public boolean hasStableIds() {
		return wrappedAdapter.hasStableIds();
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		if (position == this.position) {
			return fakeView;
		}
		return wrappedAdapter.getView(transformPosition(position), convertView, parent);
	}

	@Override
	public int getItemViewType(int position) {
		if (position == this.position) {
			return IGNORE_ITEM_VIEW_TYPE;
		}
		return wrappedAdapter.getItemViewType(transformPosition(position));
	}

	@Override
	public int getViewTypeCount() {
		return wrappedAdapter.getViewTypeCount();
	}

	@Override
	public boolean isEmpty() {
		return wrappedAdapter.isEmpty();
	}

	@Override
	public boolean areAllItemsEnabled() {
		return wrappedAdapter.areAllItemsEnabled();
	}

	@Override
	public boolean isEnabled(int position) {
		return wrappedAdapter.isEnabled(transformPosition(position));
	}
}