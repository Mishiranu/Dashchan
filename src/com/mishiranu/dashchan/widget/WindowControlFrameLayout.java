package com.mishiranu.dashchan.widget;

import android.content.Context;
import android.graphics.Insets;
import android.graphics.Rect;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import com.mishiranu.dashchan.C;

public class WindowControlFrameLayout extends FrameLayout {
	public interface OnApplyWindowPaddingsListener {
		void onApplyWindowPaddings(WindowControlFrameLayout view, Rect rect, Rect imeRect30);
	}

	private OnApplyWindowPaddingsListener onApplyWindowPaddingsListener;

	public WindowControlFrameLayout(Context context) {
		super(context);
		super.setFitsSystemWindows(true);
		super.setClipToPadding(false);
	}

	public void setOnApplyWindowPaddingsListener(OnApplyWindowPaddingsListener listener) {
		onApplyWindowPaddingsListener = listener;
	}

	@Override
	public void setFitsSystemWindows(boolean fitSystemWindows) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setClipToPadding(boolean clipToPadding) {
		throw new UnsupportedOperationException();
	}

	private Rect previousRect;
	private Rect previousRectIme30;

	private void onSystemWindowInsetsChangedInternal(Rect rect, Rect imeRect30) {
		if (previousRect != null && previousRect.equals(rect) &&
				previousRectIme30 != null && previousRectIme30.equals(imeRect30)) {
			return;
		}
		previousRect = rect;
		previousRectIme30 = imeRect30;
		if (onApplyWindowPaddingsListener != null) {
			onApplyWindowPaddingsListener.onApplyWindowPaddings(this, rect, imeRect30);
		}
	}

	private static final Rect DEFAULT_RECT = new Rect();

	@Override
	public WindowInsets onApplyWindowInsets(WindowInsets insets) {
		try {
			return super.onApplyWindowInsets(insets);
		} finally {
			if (C.API_LOLLIPOP) {
				setPadding(0, 0, 0, 0);
				Rect rect;
				Rect imeRect30;
				if (C.API_R) {
					Insets systemInsets = insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars());
					rect = new Rect(systemInsets.left, systemInsets.top, systemInsets.right, systemInsets.bottom);
					Insets imeInsets = insets.getInsets(WindowInsets.Type.ime());
					imeRect30 = new Rect(imeInsets.left, imeInsets.top, imeInsets.right, imeInsets.bottom);
					if (imeRect30.bottom > rect.bottom) {
						// Assume keyboard can be at the bottom only
						rect.bottom = 0;
					}
				} else {
					@SuppressWarnings("deprecation")
					int left = insets.getSystemWindowInsetLeft();
					@SuppressWarnings("deprecation")
					int top = insets.getSystemWindowInsetTop();
					@SuppressWarnings("deprecation")
					int right = insets.getSystemWindowInsetRight();
					@SuppressWarnings("deprecation")
					int bottom = insets.getSystemWindowInsetBottom();
					rect = new Rect(left, top, right, bottom);
					imeRect30 = DEFAULT_RECT;
				}
				onSystemWindowInsetsChangedInternal(rect, imeRect30);
			}
		}
	}

	@SuppressWarnings("deprecation")
	@Override
	protected boolean fitSystemWindows(Rect insets) {
		Rect rect = !C.API_LOLLIPOP ? new Rect(insets) : null;
		try {
			return super.fitSystemWindows(insets);
		} finally {
			if (!C.API_LOLLIPOP) {
				setPadding(0, 0, 0, 0);
				onSystemWindowInsetsChangedInternal(rect, DEFAULT_RECT);
			}
		}
	}
}
