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
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

public class LinebreakLayout extends ViewGroup {
	private int mHorizontalSpacing;
	private int mVerticalSpacing;

	private static final int[] ATTRS = new int[] {android.R.attr.horizontalSpacing, android.R.attr.verticalSpacing};

	public LinebreakLayout(Context context) {
		super(context);
	}

	public LinebreakLayout(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public LinebreakLayout(Context context, AttributeSet attrs, int defStyleAttr) {
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

	private final ArrayList<View> mPostMeasurements = new ArrayList<>();

	private static int measureChild(View child, LayoutParams layoutParams, boolean widthUnspecified,
			boolean linebreak, int maxWidth, int lineWidth, int heightMeasureSpec, int vertialPaddings,
			int matchLineHeight, int horizontalSpacing) {
		int childWidthMeasureSpec = layoutParams.width >= 0
				? MeasureSpec.makeMeasureSpec(layoutParams.width, MeasureSpec.EXACTLY)
				: widthUnspecified ? MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
				: MeasureSpec.makeMeasureSpec(Math.max(maxWidth - lineWidth, 0), linebreak
				? MeasureSpec.EXACTLY : MeasureSpec.AT_MOST);
		int childHeightMeasureSpec = matchLineHeight == 0 ? getChildMeasureSpec(heightMeasureSpec, vertialPaddings,
				layoutParams.height) : MeasureSpec.makeMeasureSpec(matchLineHeight, MeasureSpec.EXACTLY);
		child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
		if (lineWidth > 0) {
			lineWidth += horizontalSpacing;
		}
		return child.getMeasuredWidth();
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
		ArrayList<View> postMeasurements = mPostMeasurements;
		int horizontalSpacing = mHorizontalSpacing;
		int verticalSpacing = mVerticalSpacing;
		int count = getChildCount();
		for (int i = 0; i < count; i++) {
			View child = getChildAt(i);
			LayoutParams layoutParams = child.getLayoutParams();
			boolean measure = child.getVisibility() != View.GONE;
			boolean linebreak = measure && layoutParams.width == LayoutParams.MATCH_PARENT;
			if (measure) {
				if (layoutParams.height == LayoutParams.MATCH_PARENT) {
					postMeasurements.add(child);
				} else {
					lineWidth += measureChild(child, layoutParams, widthUnspecified, linebreak, maxWidth, lineWidth,
							heightMeasureSpec, vertialPaddings, 0, horizontalSpacing);
					lineHeight = Math.max(lineHeight, child.getMeasuredHeight());
				}
			}
			boolean last = i + 1 == count;
			if (linebreak || last) {
				for (int j = 0; j < postMeasurements.size(); j++) {
					child = postMeasurements.get(j);
					lineWidth += measureChild(child, layoutParams, widthUnspecified, linebreak, maxWidth, lineWidth,
							heightMeasureSpec, vertialPaddings, lineHeight, horizontalSpacing);
				}
				minWidth = Math.max(minWidth, lineWidth);
				minHeight += lineHeight;
				lineWidth = 0;
				lineHeight = 0;
				if (!last) {
					minHeight += verticalSpacing;
				}
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
		boolean newLine = true;
		int horizontalSpacing = mHorizontalSpacing;
		int verticalSpacing = mVerticalSpacing;
		int count = getChildCount();
		for (int i = 0; i < count; i++) {
			if (newLine) {
				lineHeight = 0;
				for (int j = i; j < count; j++) {
					View child = getChildAt(j);
					if (child.getVisibility() != View.GONE) {
						LayoutParams layoutParams = child.getLayoutParams();
						lineHeight = Math.max(lineHeight, child.getMeasuredHeight());
						// Line break
						if (layoutParams.width == LayoutParams.MATCH_PARENT) {
							break;
						}
					}
				}
				newLine = false;
			}
			View child = getChildAt(i);
			if (child.getVisibility() != View.GONE) {
				LayoutParams layoutParams = child.getLayoutParams();
				int width = child.getMeasuredWidth();
				int height = child.getMeasuredHeight();
				// + 1 for ceil
				int dy = (lineHeight - height + 1) / 2;
				int cleft = left + rleft;
				int ctop = top + rtop + dy;
				if (cleft < right) {
					child.layout(cleft, ctop, Math.min(cleft + width, right), ctop + height);
				}
				rleft += width + horizontalSpacing;
				// Line break
				if (layoutParams.width == LayoutParams.MATCH_PARENT) {
					rleft = 0;
					rtop += lineHeight + verticalSpacing;
					newLine = true;
				}
			}
		}
	}

	@Override
	protected LayoutParams generateDefaultLayoutParams() {
		return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
	}
}