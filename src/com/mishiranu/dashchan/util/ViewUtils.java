package com.mishiranu.dashchan.util;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toolbar;
import androidx.core.app.NotificationCompat;
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

	public static void applyScaleSize(View... views) {
		float scale = Preferences.getTextScale() / 100f;
		for (View view : views) {
			if (view != null) {
				if (view instanceof TextView) {
					TextView textView = (TextView) view;
					float size = textView.getTextSize() * scale;
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

	public static void applyMultipleCardHolderPadding(View view) {
		float density = ResourceUtils.obtainDensity(view);
		int leftRight = (int) (5.5f * density);
		view.setPadding(leftRight, 0, leftRight, 0);
	}

	public static void applyCardHolderPadding(View view, boolean isFirst, boolean isLast, boolean multipleLeftRight) {
		float density = ResourceUtils.obtainDensity(view);
		int leftRight = multipleLeftRight ? (int) (2.5f * density + 0.5f) : (int) (8f * density);
		int top = isFirst ? (int) (8f * density) : (int) (4f * density);
		int bottom = isLast ? (int) (7f * density) : 0;
		view.setPadding(leftRight, top, leftRight, bottom);
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public static void applyToolbarStyle(Window window, View toolbarView) {
		if (C.API_LOLLIPOP) {
			View decorView = window.getDecorView();
			if (toolbarView instanceof Toolbar) {
				Toolbar toolbar = (Toolbar) toolbarView;
				TextView subtitleTextView = null;
				try {
					Field field = toolbar.getClass().getDeclaredField("mSubtitleTextView");
					field.setAccessible(true);
					subtitleTextView = (TextView) field.get(toolbar);
					if (subtitleTextView == null) {
						// Create new TextView
						toolbar.setSubtitle("stub");
						toolbar.setSubtitle(null);
						subtitleTextView = (TextView) field.get(toolbar);
					}
				} catch (Exception e) {
					// Reflective operation, ignore exception
				}
				if (subtitleTextView != null) {
					Configuration configuration = decorView.getResources().getConfiguration();
					boolean handle = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
							&& !ResourceUtils.isTablet(configuration);
					if (handle) {
						float density = ResourceUtils.obtainDensity(toolbar);
						subtitleTextView.setIncludeFontPadding(false);
						subtitleTextView.setPadding(0, 0, 0, (int) (2f * density + 0.5f));
					}
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

	public static void dismissDialogQuietly(DialogInterface dialog) {
		try {
			dialog.dismiss();
		} catch (IllegalArgumentException e) {
			// May be detached from window manager
		}
	}

	public static void addNotificationAction(NotificationCompat.Builder builder, Context context,
			TypedArray typedArray, int resourceIndex, int titleRes, PendingIntent intent) {
		builder.addAction(typedArray.getResourceId(resourceIndex, 0), context.getString(titleRes), intent);
	}

	@Deprecated
	public static void addNotificationAction(Notification.Builder builder, Context context, TypedArray typedArray,
			int resourceIndex, int titleRes, PendingIntent intent) {
		addNotificationAction(builder, context, typedArray.getResourceId(resourceIndex, 0),
				context.getString(titleRes), intent);
	}

	@Deprecated
	public static void addNotificationAction(Notification.Builder builder, Context context, int icon,
			CharSequence title, PendingIntent intent) {
		if (C.API_MARSHMALLOW) {
			builder.addAction(new Notification.Action.Builder(Icon.createWithResource(context, icon),
					title, intent).build());
		} else {
			builder.addAction(icon, title, intent);
		}
	}

	public static <V extends View> void setBackgroundPreservePadding(V view, Drawable drawable) {
		// Setting background drawable may reset padding
		int left = view.getPaddingLeft();
		int top = view.getPaddingTop();
		int right = view.getPaddingRight();
		int bottom = view.getPaddingBottom();
		view.setBackground(drawable);
		view.setPadding(left, top, right, bottom);
	}
}
