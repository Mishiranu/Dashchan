package com.mishiranu.dashchan.widget;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.view.DisplayCutout;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import androidx.annotation.RequiresApi;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;

public class InsetsLayout extends FrameLayout {
	public static final class Insets {
		public static final Insets DEFAULT = new Insets(0, 0, 0, 0);

		public final int left;
		public final int top;
		public final int right;
		public final int bottom;

		public Insets(int left, int top, int right, int bottom) {
			this.left = left;
			this.top = top;
			this.right = right;
			this.bottom = bottom;
		}

		@RequiresApi(api = Build.VERSION_CODES.Q)
		private Insets(android.graphics.Insets insets) {
			this(insets.left, insets.top, insets.right, insets.bottom);
		}

		@Override
		public boolean equals(Object o) {
			if (o == this) {
				return true;
			}
			if (o instanceof Insets) {
				Insets insets = (Insets) o;
				return left == insets.left &&
						top == insets.top &&
						right == insets.right &&
						bottom == insets.bottom;
			}
			return false;
		}

		@Override
		public int hashCode() {
			int prime = 31;
			int result = 1;
			result = prime * result + left;
			result = prime * result + top;
			result = prime * result + right;
			result = prime * result + bottom;
			return result;
		}
	}

	public static class Apply {
		public final Insets window;
		public final boolean useGesture29;
		public final int imeBottom30;

		private Apply(Insets window, boolean useGesture29, int imeBottom30) {
			this.window = window;
			this.useGesture29 = useGesture29;
			this.imeBottom30 = imeBottom30;
		}

		public Insets get() {
			int bottom = Math.max(window.bottom, imeBottom30);
			return bottom != window.bottom ? new Insets(window.left, window.top, window.right, window.bottom) : window;
		}
	}

	public interface OnApplyInsetsListener {
		void onApplyInsets(Apply apply);
	}

	private OnApplyInsetsListener onApplyInsetsListener;

	public InsetsLayout(Context context) {
		super(context);
		super.setFitsSystemWindows(true);
		super.setClipToPadding(false);
	}

	public void setOnApplyInsetsListener(OnApplyInsetsListener listener) {
		onApplyInsetsListener = listener;
	}

	public void setOnApplyInsetsTarget(View view) {
		setOnApplyInsetsListener(view != null ? applyData -> {
			Insets insets = applyData.get();
			view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
			view.setTag(R.id.tag_insets_gesture_navigation, applyData.useGesture29);
		} : null);
	}

	@Override
	public void setFitsSystemWindows(boolean fitSystemWindows) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setClipToPadding(boolean clipToPadding) {
		throw new UnsupportedOperationException();
	}

	public static boolean isTargetGesture29(View view) {
		Object gestureNavigation = view.getTag(R.id.tag_insets_gesture_navigation);
		return gestureNavigation != null && (boolean) gestureNavigation;
	}

	private Insets lastWindow = Insets.DEFAULT;
	private Insets lastGesture29 = Insets.DEFAULT;
	private Insets lastIme30 = Insets.DEFAULT;

	private void onInsetsChangedInternal(Insets window, Insets gesture29, Insets ime30) {
		if (!lastWindow.equals(window) || !lastGesture29.equals(gesture29) || !lastIme30.equals(ime30)) {
			lastWindow = window;
			lastGesture29 = gesture29;
			lastIme30 = ime30;
			if (onApplyInsetsListener != null) {
				boolean useGesture29 = C.API_Q && (gesture29.left > window.left || gesture29.right > window.right);
				onApplyInsetsListener.onApplyInsets(new Apply(window, useGesture29, ime30.bottom));
			}
		}
	}

	private static Insets getWindowWithCutout(Insets window, WindowInsets insets) {
		if (C.API_PIE) {
			DisplayCutout cutout = insets.getDisplayCutout();
			if (cutout != null) {
				int left = cutout.getSafeInsetLeft();
				int top = cutout.getSafeInsetTop();
				int right = cutout.getSafeInsetRight();
				int bottom = cutout.getSafeInsetBottom();
				if (left > window.left || top > window.top || right > window.right || bottom > window.bottom) {
					return new Insets(Math.max(left, window.left), Math.max(top, window.top),
							Math.max(right, window.right), Math.max(bottom, window.bottom));
				}
			}
		}
		return window;
	}

	@Override
	public WindowInsets onApplyWindowInsets(WindowInsets insets) {
		try {
			return super.onApplyWindowInsets(insets);
		} finally {
			if (C.API_LOLLIPOP) {
				setPadding(0, 0, 0, 0);
				Insets window;
				Insets gesture29;
				Insets ime30;
				if (C.API_R) {
					Insets realWindow = new Insets(insets.getInsetsIgnoringVisibility
							(WindowInsets.Type.displayCutout() | WindowInsets.Type.systemBars()));
					gesture29 = new Insets(insets.getInsets(WindowInsets.Type.systemGestures()));
					ime30 = new Insets(insets.getInsets(WindowInsets.Type.ime()));
					if (ime30.bottom > realWindow.bottom) {
						// Assume keyboard can be at the bottom only
						window = new Insets(realWindow.left, realWindow.top, realWindow.right, 0);
					} else {
						window = realWindow;
					}
				} else if (C.API_Q) {
					@SuppressWarnings("deprecation")
					Insets windowDeprecated = new Insets(insets.getSystemWindowInsets());
					window = getWindowWithCutout(windowDeprecated, insets);
					@SuppressWarnings("deprecation")
					Insets gesture29Deprecated = new Insets(insets.getSystemGestureInsets());
					gesture29 = gesture29Deprecated;
					ime30 = Insets.DEFAULT;
				} else {
					@SuppressWarnings("deprecation")
					int left = insets.getSystemWindowInsetLeft();
					@SuppressWarnings("deprecation")
					int top = insets.getSystemWindowInsetTop();
					@SuppressWarnings("deprecation")
					int right = insets.getSystemWindowInsetRight();
					@SuppressWarnings("deprecation")
					int bottom = insets.getSystemWindowInsetBottom();
					window = getWindowWithCutout(new Insets(left, top, right, bottom), insets);
					gesture29 = Insets.DEFAULT;
					ime30 = Insets.DEFAULT;
				}
				onInsetsChangedInternal(window, gesture29, ime30);
			}
		}
	}

	@SuppressWarnings("deprecation")
	@Override
	protected boolean fitSystemWindows(Rect insets) {
		Insets windowInsets = !C.API_LOLLIPOP ? new Insets(insets.left, insets.top, insets.right, insets.bottom) : null;
		try {
			return super.fitSystemWindows(insets);
		} finally {
			if (!C.API_LOLLIPOP) {
				setPadding(0, 0, 0, 0);
				onInsetsChangedInternal(windowInsets, Insets.DEFAULT, Insets.DEFAULT);
			}
		}
	}
}
