package com.mishiranu.dashchan.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

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

	private RecyclerView recyclerView;

	@Override
	public void addView(View child, int index, ViewGroup.LayoutParams params) {
		super.addView(child, index, params);

		if (child instanceof RecyclerView) {
			recyclerView = (RecyclerView) child;
		}
	}

	public RecyclerView getRecyclerView() {
		return recyclerView;
	}
}
