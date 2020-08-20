package com.mishiranu.dashchan.widget;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class PostsLayoutManager extends LinearLayoutManager {
	public PostsLayoutManager(Context context) {
		super(context);
	}

	@Override
	public boolean requestChildRectangleOnScreen(@NonNull RecyclerView parent, @NonNull View child,
			@NonNull Rect rect, boolean immediate, boolean focusedChildVisible) {
		// Don't scroll RecyclerView when text selection starts/ends
		return child instanceof PostLinearLayout ||
				super.requestChildRectangleOnScreen(parent, child, rect, immediate, focusedChildVisible);
	}
}
