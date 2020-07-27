package com.mishiranu.dashchan.util;

import android.annotation.TargetApi;
import android.app.Activity;
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
import android.widget.TextView;
import android.widget.Toolbar;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.preference.Preferences;
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
		return configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
				&& ResourceUtils.isTablet(configuration);
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
	public static void applyToolbarStyle(Activity activity, View toolbarView) {
		if (C.API_LOLLIPOP) {
			if (toolbarView == null) {
				int id = activity.getResources().getIdentifier("action_bar", "id", "android");
				if (id != 0) {
					toolbarView = activity.findViewById(id);
				}
			}
			if (toolbarView instanceof Toolbar) {
				Context context = toolbarView.getContext();
				int[] attrs = {android.R.attr.actionBarStyle, android.R.attr.actionBarSize};
				TypedArray typedArray = context.obtainStyledAttributes(attrs);
				int actionStyle = typedArray.getResourceId(0, 0);
				int actionHeight = typedArray.getDimensionPixelSize(1, 0);
				typedArray.recycle();
				try {
					View container = (View) toolbarView.getParent();
					Field field = container.getClass().getDeclaredField("mHeight");
					field.setAccessible(true);
					field.setInt(container, actionHeight);
					View overlay = (View) container.getParent();
					field = overlay.getClass().getDeclaredField("mActionBarHeight");
					field.setAccessible(true);
					field.setInt(overlay, actionHeight);
				} catch (Exception e) {
					// Reflective operation, ignore exception
				}
				toolbarView.getLayoutParams().height = actionHeight;
				toolbarView.setMinimumHeight(actionHeight);
				int[] toolbarAttrs = {android.R.attr.titleTextStyle, android.R.attr.subtitleTextStyle};
				typedArray = context.obtainStyledAttributes(actionStyle, toolbarAttrs);
				Toolbar toolbar = (Toolbar) toolbarView;
				toolbar.setTitleTextAppearance(context, typedArray.getResourceId(0, 0));
				toolbar.setSubtitleTextAppearance(context, typedArray.getResourceId(1, 0));
				typedArray.recycle();
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
					Configuration configuration = activity.getResources().getConfiguration();
					boolean handle = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
							&& !ResourceUtils.isTablet(configuration);
					subtitleTextView.setIncludeFontPadding(!handle);
					Object tag = subtitleTextView.getTag(R.id.value);
					if (tag == null) {
						tag = subtitleTextView.getPaddingLeft() == 0 && subtitleTextView.getPaddingTop() == 0 &&
								subtitleTextView.getPaddingRight() == 0 && subtitleTextView.getPaddingBottom() == 0;
						subtitleTextView.setTag(R.id.value, tag);
					}
					if (tag instanceof Boolean && (boolean) tag) {
						float density = ResourceUtils.obtainDensity(toolbar);
						subtitleTextView.setPadding(0, 0, 0, handle ? (int) (2f * density + 0.5f) : 0);
					}
					if (handle) {
						// Override text size from text appearance set before
						subtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
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

	public static void addNotificationAction(Notification.Builder builder, Context context, TypedArray typedArray,
			int resourceIndex, int titleRes, PendingIntent intent) {
		addNotificationAction(builder, context, typedArray.getResourceId(resourceIndex, 0),
				context.getString(titleRes), intent);
	}

	@TargetApi(Build.VERSION_CODES.M)
	@SuppressWarnings("deprecation")
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
