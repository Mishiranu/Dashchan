package com.mishiranu.dashchan.util;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Build;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import androidx.fragment.app.Fragment;
import com.mishiranu.dashchan.C;

public class ResourceUtils {
	public static float obtainDensity(View view) {
		return obtainDensity(view.getResources());
	}

	public static float obtainDensity(Fragment fragment) {
		return obtainDensity(fragment.getResources());
	}

	public static float obtainDensity(Context context) {
		return obtainDensity(context.getResources());
	}

	public static float obtainDensity(Resources resources) {
		return resources.getDisplayMetrics().density;
	}

	public static boolean isTablet(Configuration configuration) {
		return configuration.smallestScreenWidthDp >= 600;
	}

	public static boolean isTabletLarge(Configuration configuration) {
		return configuration.smallestScreenWidthDp >= 720;
	}

	public static boolean isTabletOrLandscape(Configuration configuration) {
		return configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || isTablet(configuration);
	}

	public static int getColor(Context context, int attr) {
		TypedArray typedArray = context.obtainStyledAttributes(new int[] {attr});
		try {
			return typedArray.getColor(0, 0);
		} finally {
			typedArray.recycle();
		}
	}

	public static ColorStateList getColorStateList(Context context, int attr) {
		TypedArray typedArray = context.obtainStyledAttributes(new int[] {attr});
		try {
			return typedArray.getColorStateList(0);
		} finally {
			typedArray.recycle();
		}
	}

	public static int getResourceId(Context context, int attr, int notFound) {
		try {
			int resId;
			TypedArray typedArray = context.obtainStyledAttributes(new int[] {attr});
			resId = typedArray.getResourceId(0, 0);
			typedArray.recycle();
			if (resId != 0) {
				return resId;
			}
		} catch (Exception e) {
			// Ignore exception
		}
		return notFound;
	}

	public static int getResourceId(Context context, int defStyleAttr, int attr, int notFound) {
		try {
			int resId;
			TypedArray typedArray;
			typedArray = context.obtainStyledAttributes(null, new int[] {attr}, defStyleAttr, 0);
			resId = typedArray.getResourceId(0, 0);
			typedArray.recycle();
			if (resId != 0) {
				return resId;
			}
		} catch (Exception e) {
			// Ignore exception
		}
		return notFound;
	}

	@SuppressWarnings("deprecation")
	public static Drawable getDrawable(Context context, int resId) {
		return C.API_LOLLIPOP ? context.getDrawable(resId) : context.getResources().getDrawable(resId);
	}

	public static Drawable getDrawable(Context context, int attr, int notFound) {
		int resId = getResourceId(context, attr, notFound);
		return resId != 0 ? getDrawable(context, resId) : null;
	}

	public static Drawable getActionBarIcon(Context context, int attr) {
		Drawable drawable = getDrawable(context, attr, 0);
		if (C.API_LOLLIPOP) {
			drawable.mutate();
			drawable.setTint(getColor(context, android.R.attr.textColorPrimary));
		}
		return drawable;
	}

	public static final int[] PRESSED_STATE = {android.R.attr.state_window_focused, android.R.attr.state_enabled,
		android.R.attr.state_pressed};

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	@SuppressWarnings("deprecation")
	public static int getSystemSelectorColor(Context context) {
		if (C.API_LOLLIPOP) {
			return getColor(context, android.R.attr.colorControlHighlight);
		} else {
			int resId = getResourceId(context, android.R.attr.listChoiceBackgroundIndicator,
					android.R.drawable.list_selector_background);
			Drawable drawable = context.getResources().getDrawable(resId);
			drawable.setState(PRESSED_STATE);
			return GraphicsUtils.getDrawableColor(context, drawable, Gravity.CENTER);
		}
	}

	@TargetApi(Build.VERSION_CODES.KITKAT)
	public static int getDialogBackground(Context context) {
		context = new ContextThemeWrapper(context, getResourceId(context, android.R.attr.dialogTheme, 0));
		TypedArray typedArray = context.obtainStyledAttributes(new int[] {android.R.attr.windowBackground});
		Drawable drawable = typedArray.getDrawable(0);
		typedArray.recycle();
		if (C.API_KITKAT && drawable instanceof InsetDrawable) {
			drawable = ((InsetDrawable) drawable).getDrawable();
		}
		return drawable != null ? GraphicsUtils.getDrawableColor(context, drawable, Gravity.CENTER) : 0;
	}

	public enum DialogLayout {SIMPLE, SINGLE_CHOICE, MULTI_CHOICE}

	public static int obtainAlertDialogLayoutResId(Context context, DialogLayout dialogLayout) {
		int resId;
		String layoutName;
		switch (dialogLayout) {
			case SIMPLE: {
				resId = android.R.layout.select_dialog_item;
				layoutName = "listItemLayout";
				break;
			}
			case SINGLE_CHOICE: {
				resId = android.R.layout.select_dialog_singlechoice;
				layoutName = "singleChoiceItemLayout";
				break;
			}
			case MULTI_CHOICE: {
				resId = android.R.layout.select_dialog_multichoice;
				layoutName = "multiChoiceItemLayout";
				break;
			}
			default: {
				throw new RuntimeException();
			}
		}
		int layoutAttr = context.getResources().getIdentifier(layoutName, "attr", "android");
		if (layoutAttr != 0) {
			TypedArray typedArray = context.obtainStyledAttributes(null, new int[] {layoutAttr},
					android.R.attr.alertDialogStyle, 0);
			resId = typedArray.getResourceId(0, resId);
			typedArray.recycle();
		}
		return resId;
	}
}
