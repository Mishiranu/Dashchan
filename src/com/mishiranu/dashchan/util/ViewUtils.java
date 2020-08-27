package com.mishiranu.dashchan.util;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.widget.EdgeEffect;
import android.widget.ScrollView;
import android.widget.TextView;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.content.Preferences;
import java.lang.reflect.Field;

public class ViewUtils {
	public static final DialogInterface.OnShowListener ALERT_DIALOG_MESSAGE_SELECTABLE = dialog -> {
		if (dialog instanceof AlertDialog) {
			TextView textView = ((AlertDialog) dialog).findViewById(android.R.id.message);
			if (textView != null) {
				textView.setTextIsSelectable(true);
			}
		}
	};

	public static final DialogInterface.OnShowListener ALERT_DIALOG_LONGER_TITLE = dialog -> {
		if (dialog instanceof AlertDialog) {
			View view = ((AlertDialog) dialog).getWindow().getDecorView();
			int id = view.getResources().getIdentifier("alertTitle", "id", "android");
			if (id == 0) {
				id = android.R.id.title;
			}
			View titleView = view.findViewById(id);
			if (titleView instanceof TextView) {
				TextView textView = (TextView) titleView;
				int maxLines = textView.getMaxLines();
				if (maxLines > 0 && maxLines < 4) {
					textView.setMaxLines(4);
				}
			}
		}
	};

	public static void removeFromParent(View view) {
		ViewParent viewParent = view.getParent();
		if (viewParent instanceof ViewGroup) {
			((ViewGroup) viewParent).removeView(view);
		}
	}

	public static boolean isDrawerLockable(Configuration configuration) {
		// Should always result "true" for tablets in landscape mode (+ in portrait mode on large screens).
		// Sometimes it will result "true" for screens with low DPI configuration, which is intentional.
		return configuration.screenWidthDp >= 720;
	}

	public static void setTextSizeScaled(TextView textView, int sizeSp) {
		// Avoid fractional sizes (the same logic is used for sizes specified in XML)
		int sizePx = (int) (sizeSp * textView.getResources().getDisplayMetrics().scaledDensity + 0.5f);
		textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx);
	}

	public static void applyScaleSize(View... views) {
		float scale = Preferences.getTextScale() / 100f;
		for (View view : views) {
			if (view != null) {
				if (view instanceof TextView) {
					TextView textView = (TextView) view;
					int size = (int) (textView.getTextSize() * scale + 0.5f);
					textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
				}
				ViewGroup.LayoutParams params = view.getLayoutParams();
				if (params != null) {
					if (params.width > 0) {
						params.width = (int) (params.width * scale);
					}
					if (params.height > 0) {
						params.height = (int) (params.height * scale);
					}
				}
			}
		}
	}

	public static void applyScaleMarginLR(View... views) {
		float scale = Preferences.getTextScale() / 100f;
		for (View view : views) {
			if (view != null) {
				ViewGroup.LayoutParams params = view.getLayoutParams();
				if (params instanceof ViewGroup.MarginLayoutParams) {
					ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) params;
					marginParams.leftMargin = (int) (marginParams.leftMargin * scale);
					marginParams.rightMargin = (int) (marginParams.rightMargin * scale);
				}
			}
		}
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public static void makeRoundedCorners(View view, final int radius, final boolean withPaddings) {
		if (C.API_LOLLIPOP) {
			view.setClipToOutline(true);
			view.setOutlineProvider(new ViewOutlineProvider() {
				private final Rect rect = new Rect();

				@Override
				public void getOutline(View view, Outline outline) {
					Rect rect = this.rect;
					if (withPaddings) {
						rect.set(view.getPaddingLeft(), view.getPaddingTop(), view.getWidth() - view.getPaddingRight(),
								view.getHeight() - view.getPaddingBottom());
					} else {
						rect.set(0, 0, view.getWidth(), view.getHeight());
					}
					outline.setRoundRect(rect, radius);
				}
			});
		}
	}

	private static final Field FIELD_SCROLL_BAR_EDGE_GLOW_TOP;
	private static final Field FIELD_SCROLL_BAR_EDGE_GLOW_BOTTOM;

	static {
		Field scrollBarEdgeGlowTopField = null;
		Field scrollBarEdgeGlowBottomField = null;
		if (!C.API_Q) {
			try {
				scrollBarEdgeGlowTopField = ScrollView.class.getDeclaredField("mEdgeGlowTop");
				scrollBarEdgeGlowTopField.setAccessible(true);
				scrollBarEdgeGlowBottomField = ScrollView.class.getDeclaredField("mEdgeGlowBottom");
				scrollBarEdgeGlowBottomField.setAccessible(true);
			} catch (Exception e) {
				scrollBarEdgeGlowTopField = null;
				scrollBarEdgeGlowBottomField = null;
			}
		}
		FIELD_SCROLL_BAR_EDGE_GLOW_TOP = scrollBarEdgeGlowTopField;
		FIELD_SCROLL_BAR_EDGE_GLOW_BOTTOM = scrollBarEdgeGlowBottomField;
	}

	public static void setEdgeEffectColor(ScrollView scrollView, int color) {
		if (C.API_Q) {
			scrollView.setEdgeEffectColor(color);
		} else {
			EdgeEffect topEdgeEffect = null;
			EdgeEffect bottomEdgeEffect = null;
			if (FIELD_SCROLL_BAR_EDGE_GLOW_TOP != null && FIELD_SCROLL_BAR_EDGE_GLOW_BOTTOM != null) {
				try {
					topEdgeEffect = (EdgeEffect) FIELD_SCROLL_BAR_EDGE_GLOW_TOP.get(scrollView);
					bottomEdgeEffect = (EdgeEffect) FIELD_SCROLL_BAR_EDGE_GLOW_BOTTOM.get(scrollView);
				} catch (Exception e) {
					// Ignore
				}
			}
			if (topEdgeEffect != null && bottomEdgeEffect != null) {
				setEdgeEffectColor(topEdgeEffect, color);
				setEdgeEffectColor(bottomEdgeEffect, color);
			}
		}
	}

	private static final Field FIELD_EDGE_EFFECT_EDGE;
	private static final Field FIELD_EDGE_EFFECT_GLOW;

	static {
		Field edgeEffectEdgeField = null;
		Field edgeEffectGlowField = null;
		if (!C.API_LOLLIPOP) {
			try {
				edgeEffectEdgeField = EdgeEffect.class.getDeclaredField("mEdge");
				edgeEffectEdgeField.setAccessible(true);
				edgeEffectGlowField = EdgeEffect.class.getDeclaredField("mGlow");
				edgeEffectGlowField.setAccessible(true);
			} catch (Exception e) {
				edgeEffectEdgeField = null;
				edgeEffectGlowField = null;
			}
		}
		FIELD_EDGE_EFFECT_EDGE = edgeEffectEdgeField;
		FIELD_EDGE_EFFECT_GLOW = edgeEffectGlowField;
	}

	public static void setEdgeEffectColor(EdgeEffect edgeEffect, int color) {
		if (C.API_LOLLIPOP) {
			edgeEffect.setColor(color);
		} else {
			Drawable edge = null;
			Drawable glow = null;
			if (FIELD_EDGE_EFFECT_EDGE != null && FIELD_EDGE_EFFECT_GLOW != null) {
				try {
					edge = (Drawable) FIELD_EDGE_EFFECT_EDGE.get(edgeEffect);
					glow = (Drawable) FIELD_EDGE_EFFECT_GLOW.get(edgeEffect);
				} catch (Exception e) {
					// Ignore
				}
			}
			if (edge != null && glow != null) {
				edge.mutate().setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP));
				glow.mutate().setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP));
			}
		}
	}

	public static void dismissDialogQuietly(DialogInterface dialog) {
		try {
			dialog.dismiss();
		} catch (IllegalArgumentException e) {
			// May be detached from window manager
		}
	}

	public static void setSelectableItemBackground(View view) {
		setBackgroundPreservePadding(view, ResourceUtils
				.getDrawable(view.getContext(), android.R.attr.selectableItemBackground, 0));
	}

	public static void setBackgroundPreservePadding(View view, Drawable drawable) {
		// Setting background drawable may reset padding
		int left = view.getPaddingLeft();
		int top = view.getPaddingTop();
		int right = view.getPaddingRight();
		int bottom = view.getPaddingBottom();
		view.setBackground(drawable);
		view.setPadding(left, top, right, bottom);
	}

	public static void setNewPadding(View view, Integer left, Integer top, Integer right, Integer bottom) {
		int oldLeft = view.getPaddingLeft();
		int oldTop = view.getPaddingTop();
		int oldRight = view.getPaddingRight();
		int oldBottom = view.getPaddingBottom();
		int newLeft = left != null ? left : oldLeft;
		int newTop = top != null ? top : oldTop;
		int newRight = right != null ? right : oldRight;
		int newBottom = bottom != null ? bottom : oldBottom;
		if (oldLeft != newLeft || oldTop != newTop || oldRight != newRight || oldBottom != newBottom) {
			view.setPadding(newLeft, newTop, newRight, newBottom);
		}
	}

	public static void setNewMargin(View view, Integer left, Integer top, Integer right, Integer bottom) {
		ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
		boolean changed = false;
		if (left != null && layoutParams.leftMargin != left) {
			layoutParams.leftMargin = left;
			changed = true;
		}
		if (top != null && layoutParams.topMargin != top) {
			layoutParams.topMargin = top;
			changed = true;
		}
		if (right != null && layoutParams.rightMargin != right) {
			layoutParams.rightMargin = right;
			changed = true;
		}
		if (bottom != null && layoutParams.bottomMargin != bottom) {
			layoutParams.bottomMargin = bottom;
			changed = true;
		}
		if (changed) {
			view.requestLayout();
		}
	}
}
