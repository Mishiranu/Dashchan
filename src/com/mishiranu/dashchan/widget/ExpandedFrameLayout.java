package com.mishiranu.dashchan.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;

public class ExpandedFrameLayout extends FrameLayout {
	public ExpandedFrameLayout(@NonNull Context context) {
		super(context);
	}

	public ExpandedFrameLayout(@NonNull Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public ExpandedFrameLayout(@NonNull Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	private AbsListView listView;

	@Override
	public void addView(View child, int index, ViewGroup.LayoutParams params) {
		super.addView(child, index, params);

		if (child instanceof AbsListView) {
			listView = (AbsListView) child;
		}
	}

	public AbsListView getListView() {
		return listView;
	}
}
