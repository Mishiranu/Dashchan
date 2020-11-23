package com.mishiranu.dashchan.util;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Insets;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.EdgeEffect;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.view.ViewCompat;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import java.lang.reflect.Field;

public class ViewUtils {
	public static final int STATUS_OVERLAY_TRANSPARENT = 0x4d000000;

	@SuppressWarnings("deprecation")
	public static final int SOFT_INPUT_ADJUST_RESIZE_COMPAT = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;

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

	public static boolean isGestureNavigationOverlap(View view, boolean checkLeft, boolean checkRight) {
		if (C.API_Q) {
			WindowInsets windowInsets = view.getRootWindowInsets();
			Insets insets;
			if (C.API_R) {
				insets = windowInsets.getInsets(WindowInsets.Type.systemGestures());
			} else {
				@SuppressWarnings("deprecation")
				Insets insetsDeprecated = windowInsets.getSystemGestureInsets();
				insets = insetsDeprecated;
			}
			if (checkLeft && insets.left > 0 || checkRight && insets.right > 0) {
				int left = view.getLeft();
				View parentView = (View) view.getParent();
				while (true) {
					left += parentView.getLeft();
					ViewParent parent = parentView.getParent();
					if (parent instanceof View) {
						parentView = (View) parent;
					} else {
						break;
					}
				}
				int right = parentView.getWidth() - left - view.getWidth();
				return checkLeft && insets.left > left || checkRight && insets.right > right;
			}
		}
		return false;
	}

	public static void setTextSizeScaled(TextView textView, int sizeSp) {
		// Avoid fractional sizes (the same logic is used for sizes specified in XML)
		int sizePx = (int) (sizeSp * textView.getResources().getDisplayMetrics().scaledDensity + 0.5f);
		textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx);
	}

	public static void applyScaleSize(float scale, View... views) {
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

	public static void applyScaleMarginLR(float scale, View... views) {
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

	public static void setNewMarginRelative(View view, Integer start, Integer top, Integer end, Integer bottom) {
		if (ViewCompat.getLayoutDirection(view) == ViewCompat.LAYOUT_DIRECTION_RTL) {
			setNewMargin(view, end, top, start, bottom);
		} else {
			setNewMargin(view, start, top, end, bottom);
		}
	}

	public static void setWindowLayoutFullscreen(Window window) {
		if (C.API_R) {
			window.setDecorFitsSystemWindows(false);
		} else {
			setWindowLayoutFullscreen21(window);
		}
	}

	@SuppressWarnings("deprecation")
	private static void setWindowLayoutFullscreen21(Window window) {
		window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
				View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
	}

	public static void drawSystemInsetsOver(View view, Canvas canvas, boolean gestureNavigation) {
		Paint paint = (Paint) view.getTag(R.id.tag_insets_draw_data);
		if (paint == null) {
			paint = new Paint(Paint.ANTI_ALIAS_FLAG);
			paint.setColor(STATUS_OVERLAY_TRANSPARENT);
			paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
			view.setTag(R.id.tag_insets_draw_data, paint);
		}
		int x = view.getScrollX();
		int y = view.getScrollY();
		boolean translate = x != 0 || y != 0;
		if (translate) {
			canvas.save();
			canvas.translate(x, y);
		}
		int left = view.getPaddingLeft();
		int top = view.getPaddingTop();
		int right = view.getPaddingRight();
		int bottom = gestureNavigation ? 0 : view.getPaddingBottom();
		int width = view.getWidth();
		int height = view.getHeight();
		// Draw system insets over dialogs
		canvas.drawRect(0, 0, width, top, paint);
		canvas.drawRect(0, height - bottom, width, height, paint);
		canvas.drawRect(0, top, left, height - bottom, paint);
		canvas.drawRect(width - right, top, width, height - bottom, paint);
		if (translate) {
			canvas.restore();
		}
	}

	private static final Field FIELD_OUTLINE_RECT;
	private static final Field FIELD_OUTLINE_RADIUS;

	static {
		Field outlineRectFieldField = null;
		Field outlineRadiusFieldField = null;
		if (C.API_LOLLIPOP && !C.API_NOUGAT) {
			try {
				outlineRectFieldField = Outline.class.getDeclaredField("mRect");
				outlineRectFieldField.setAccessible(true);
				outlineRadiusFieldField = Outline.class.getDeclaredField("mRadius");
				outlineRadiusFieldField.setAccessible(true);
			} catch (Exception e) {
				outlineRectFieldField = null;
				outlineRadiusFieldField = null;
			}
		}
		FIELD_OUTLINE_RECT = outlineRectFieldField;
		FIELD_OUTLINE_RADIUS = outlineRadiusFieldField;
	}

	public static boolean getOutlineRect(Outline outline, Rect outRect) {
		if (C.API_NOUGAT) {
			return outline.getRect(outRect);
		} else {
			Rect rect = null;
			if (FIELD_OUTLINE_RECT != null) {
				try {
					rect = (Rect) FIELD_OUTLINE_RECT.get(outline);
				} catch (Exception e) {
					// Ignore
				}
			}
			if (rect != null) {
				outRect.set(rect);
				return true;
			}
			return false;
		}
	}

	public static float getOutlineRadius(Outline outline) {
		if (C.API_NOUGAT) {
			return outline.getRadius();
		} else {
			float radius = 0f;
			if (FIELD_OUTLINE_RECT != null) {
				try {
					Object rect = FIELD_OUTLINE_RECT.get(outline);
					if (rect != null) {
						radius = FIELD_OUTLINE_RADIUS.getFloat(outline);
					}
				} catch (Exception e) {
					// Ignore
				}
			}
			return radius;
		}
	}
}
