package com.mishiranu.dashchan.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

public class SingleLayerLinearLayout extends LinearLayout {
	public SingleLayerLinearLayout(Context context) {
		super(context);
	}

	public SingleLayerLinearLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public SingleLayerLinearLayout(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	@Override
	public boolean hasOverlappingRendering() {
		// Makes setAlpha faster, see https://plus.google.com/+RomanNurik/posts/NSgQvbfXGQN
		// Thumbnails will become strange with alpha because background alpha and image alpha are separate now
		return false;
	}

	public interface OnTemporaryDetachListener {
		public void onTemporaryDetach(SingleLayerLinearLayout view, boolean start);
	}

	private OnTemporaryDetachListener onTemporaryDetachListener;

	public void setOnTemporaryDetachListener(OnTemporaryDetachListener listener) {
		onTemporaryDetachListener = listener;
	}

	@Override
	public void onStartTemporaryDetach() {
		super.onStartTemporaryDetach();
		if (onTemporaryDetachListener != null) {
			onTemporaryDetachListener.onTemporaryDetach(this, true);
		}
	}

	@Override
	public void onFinishTemporaryDetach() {
		super.onFinishTemporaryDetach();
		if (onTemporaryDetachListener != null) {
			onTemporaryDetachListener.onTemporaryDetach(this, false);
		}
	}
}
