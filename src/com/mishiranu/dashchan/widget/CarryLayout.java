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

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

public class CarryLayout extends ViewGroup {
	private int mHorizontalSpacing;
	private int mVerticalSpacing;

	private static final int[] ATTRS = new int[] {android.R.attr.horizontalSpacing, android.R.attr.verticalSpacing};

	public CarryLayout(Context context) {
		super(context);
	}

	public CarryLayout(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public CarryLayout(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		TypedArray typedArray = context.obtainStyledAttributes(attrs, ATTRS, defStyleAttr, 0);
		mHorizontalSpacing = typedArray.getDimensionPixelSize(0, 0);
		mVerticalSpacing = typedArray.getDimensionPixelSize(1, 0);
		typedArray.recycle();
	}

	@SuppressWarnings("unused")
	public void setHorizontalSpacing(int horizontalSpacing) {
		mHorizontalSpacing = horizontalSpacing;
		requestLayout();
	}

	@SuppressWarnings("unused")
	public void setVerticalSpacing(int verticalSpacing) {
		mVerticalSpacing = verticalSpacing;
		requestLayout();
	}

	@SuppressWarnings("unused")
	public int getHorizontalSpacing() {
		return mHorizontalSpacing;
	}

	@SuppressWarnings("unused")
	public int getVerticalSpacing() {
		return mVerticalSpacing;
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int maxWidth = MeasureSpec.getSize(widthMeasureSpec);
		boolean widthUnspecified = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED;
		int vertialPaddings = getPaddingTop() + getPaddingBottom();
		int horizontalPaddings = getPaddingLeft() + getPaddingRight();
		maxWidth -= horizontalPaddings;
		int minWidth = 0;
		int minHeight = 0;
		int lineWidth = 0;
		int lineHeight = 0;
		int horizontalSpacing = mHorizontalSpacing;
		int verticalSpacing = mVerticalSpacing;
		int count = getChildCount();
		int childWidthMeasureSpec = widthUnspecified ? MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
				: MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST);
		for (int i = 0; i < count; i++) {
			View child = getChildAt(i);
			LayoutParams layoutParams = child.getLayoutParams();
			boolean measure = child.getVisibility() != View.GONE;
			if (measure) {
				int childHeightMeasureSpec = getChildMeasureSpec(heightMeasureSpec, vertialPaddings,
						layoutParams.height);
				child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
				int childWidth = child.getMeasuredWidth();
				if (lineWidth > 0 && childWidth + lineWidth + horizontalSpacing > maxWidth) {
					minWidth = Math.max(lineWidth, minWidth);
					minHeight += lineHeight + verticalSpacing;
					lineHeight = child.getMeasuredHeight();
					lineWidth = childWidth;
				} else {
					if (lineWidth > 0) {
						lineWidth += horizontalSpacing;
					}
					lineWidth += childWidth;
					lineHeight = Math.max(lineHeight, child.getMeasuredHeight());
				}
			}
			if (i + 1 == count) {
				minWidth = Math.max(lineWidth, minWidth);
				minHeight += lineHeight;
			}
		}
		minWidth += horizontalPaddings;
		minHeight += vertialPaddings;
		minWidth = Math.max(minWidth, getSuggestedMinimumWidth());
		minHeight = Math.max(minHeight, getSuggestedMinimumHeight());
		setMeasuredDimension(resolveSize(minWidth, widthMeasureSpec), resolveSize(minHeight, heightMeasureSpec));
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		int left = getPaddingLeft();
		int top = getPaddingTop();
		int right = r - l - getPaddingRight();
		int rleft = 0;
		int rtop = 0;
		int lineHeight = 0;
		int horizontalSpacing = mHorizontalSpacing;
		int verticalSpacing = mVerticalSpacing;
		int count = getChildCount();
		for (int i = 0; i < count; i++) {
			View child = getChildAt(i);
			if (child.getVisibility() != View.GONE) {
				int width = child.getMeasuredWidth();
				int height = child.getMeasuredHeight();
				if (rleft > 0 && rleft + width + horizontalSpacing > right - left) {
					rtop += lineHeight + verticalSpacing;
					rleft = 0;
					lineHeight = 0;
				}
				if (rleft > 0) {
					rleft += horizontalSpacing;
				}
				int cleft = left + rleft;
				int ctop = top + rtop;
				child.layout(cleft, ctop, Math.min(cleft + width, right), ctop + height);
				rleft += width;
				lineHeight = Math.max(lineHeight, height);
			}
		}
	}

	@Override
	protected LayoutParams generateDefaultLayoutParams() {
		return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
	}
}