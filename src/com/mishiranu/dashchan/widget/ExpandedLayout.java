package com.mishiranu.dashchan.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.widget.FrameLayout;
import androidx.recyclerview.widget.RecyclerView;
import com.mishiranu.dashchan.util.ViewUtils;

@SuppressLint("ViewConstructor")
public class ExpandedLayout extends FrameLayout implements ExpandedScreen.Layout {
	private final boolean self;
	private int top;
	private int bottom;
	private boolean useGesture29;
	private int extraTop;
	private RecyclerView recyclerView;

	public ExpandedLayout(Context context, boolean self) {
		super(context);
		this.self = self;
	}

	public void setRecyclerView(RecyclerView recyclerView) {
		this.recyclerView = recyclerView;
	}

	@Override
	public RecyclerView getRecyclerView() {
		return recyclerView;
	}

	@Override
	public void setVerticalInsets(int top, int bottom, boolean useGesture29) {
		if (this.top != top || this.bottom != bottom || this.useGesture29 != useGesture29) {
			this.top = top;
			this.bottom = bottom;
			this.useGesture29 = useGesture29;
			applyPadding();
		}
	}

	public void setExtraTop(int extraTop) {
		if (this.extraTop != extraTop) {
			this.extraTop = extraTop;
			applyPadding();
		}
	}

	private void applyPadding() {
		int childTop;
		int childBottom;
		if (!self) {
			childTop = top;
			childBottom = bottom;
		} else if (useGesture29) {
			childTop = 0;
			childBottom = bottom;
		} else {
			childTop = 0;
			childBottom = 0;
		}
		ViewUtils.setNewPadding(this, null, top + extraTop - childTop, null, bottom - childBottom);
		int childCount = getChildCount();
		for (int i = 0; i < childCount; i++) {
			ViewUtils.setNewPadding(getChildAt(i), null, childTop, null, childBottom);
		}
	}
}
