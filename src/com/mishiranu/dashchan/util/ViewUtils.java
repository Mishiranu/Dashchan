/*
 * Copyright 2014-2016 Fukurou Mishiranu
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mishiranu.dashchan.util;

import java.lang.reflect.Field;

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
import android.graphics.drawable.Icon;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.widget.CardView;
import android.widget.TextView;
import android.widget.Toolbar;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.preference.Preferences;

public class ViewUtils
{
	public static DialogInterface.OnShowListener ALERT_DIALOG_MESSAGE_SELECTABLE = new DialogInterface.OnShowListener()
	{
		@Override
		public void onShow(DialogInterface dialog)
		{
			if (dialog instanceof AlertDialog)
			{
				TextView textView = (TextView) ((AlertDialog) dialog).findViewById(android.R.id.message);
				if (textView != null) textView.setTextIsSelectable(true);
			}
		}
	};
	
	public static void removeFromParent(View view)
	{
		ViewParent viewParent = view.getParent();
		if (viewParent instanceof ViewGroup) ((ViewGroup) viewParent).removeView(view);
	}
	
	public static boolean isDrawerLockable(Configuration configuration)
	{
		return configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
				&& ResourceUtils.isTablet(configuration);
	}
	
	public static void applyScaleSize(View... views)
	{
		float scale = Preferences.getTextScale() / 100f;
		for (View view : views)
		{
			if (view != null)
			{
				if (view instanceof TextView)
				{
					TextView textView = (TextView) view;
					float size = textView.getTextSize() * scale;
					textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
				}
				ViewGroup.LayoutParams params = view.getLayoutParams();
				if (params != null)
				{
					if (params.width > 0) params.width = (int) (params.width * scale);
					if (params.height > 0) params.height = (int) (params.height * scale);
				}
			}
		}
	}
	
	public static void applyScaleMarginLR(View... views)
	{
		float scale = Preferences.getTextScale() / 100f;
		for (View view : views)
		{
			if (view != null)
			{
				ViewGroup.LayoutParams params = view.getLayoutParams();
				if (params instanceof ViewGroup.MarginLayoutParams)
				{
					ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) params;
					marginParams.leftMargin = (int) (marginParams.leftMargin * scale);
					marginParams.rightMargin = (int) (marginParams.rightMargin * scale);
				}
			}
		}
	}
	
	public static void applyMultipleCardHolderPadding(View view)
	{
		float density = ResourceUtils.obtainDensity(view);
		int leftRight = (int) (5.5f * density);
		view.setPadding(leftRight, 0, leftRight, 0);
	}
	
	public static void applyCardHolderPadding(View view, CardView cardView,
			boolean isFirst, boolean isLast, boolean multipleLeftRight)
	{
		float density = ResourceUtils.obtainDensity(view);
		int leftRight = multipleLeftRight ? (int) (2.5f * density + 0.5f) : (int) (8f * density);
		int top = isFirst ? (int) (8f * density) : (int) (4f * density);
		int bottom = isLast ? (int) (7f * density) : 0;
		view.setPadding(leftRight, top, leftRight, bottom);
	}
	
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public static void fixActionBar(Activity activity, View toolbarView)
	{
		if (C.API_LOLLIPOP)
		{
			if (toolbarView == null)
			{
				int id = activity.getResources().getIdentifier("action_bar", "id", "android");
				if (id != 0) toolbarView = activity.findViewById(id);
			}
			if (toolbarView != null && toolbarView instanceof Toolbar)
			{
				Context context = toolbarView.getContext();
				TypedArray typedArray = context.obtainStyledAttributes(new int[]
						{android.R.attr.actionBarStyle, android.R.attr.actionBarSize});
				int actionStyle = typedArray.getResourceId(0, 0);
				int actionHeight = typedArray.getDimensionPixelSize(1, 0);
				typedArray.recycle();
				try
				{
					View container = (View) toolbarView.getParent();
					Field field = container.getClass().getDeclaredField("mHeight");
					field.setAccessible(true);
					field.setInt(container, actionHeight);
					View overlay = (View) container.getParent();
					field = overlay.getClass().getDeclaredField("mActionBarHeight");
					field.setAccessible(true);
					field.setInt(overlay, actionHeight);
				}
				catch (Exception e)
				{
					
				}
				toolbarView.getLayoutParams().height = actionHeight;
				toolbarView.setMinimumHeight(actionHeight);
				typedArray = context.obtainStyledAttributes(actionStyle, new int []
						{android.R.attr.titleTextStyle, android.R.attr.subtitleTextStyle});
				Toolbar toolbar = (Toolbar) toolbarView;
				toolbar.setTitleTextAppearance(context, typedArray.getResourceId(0, 0));
				toolbar.setSubtitleTextAppearance(context, typedArray.getResourceId(1, 0));
				typedArray.recycle();
			}
		}
	}
	
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public static void makeRoundedCorners(View view, final int radius, final boolean withPaddings)
	{
		if (C.API_LOLLIPOP)
		{
			view.setClipToOutline(true);
			view.setOutlineProvider(new ViewOutlineProvider()
			{
				private final Rect mRect = new Rect();
				
				@Override
				public void getOutline(View view, Outline outline)
				{
					Rect rect = mRect;
					if (withPaddings)
					{
						rect.set(view.getPaddingLeft(), view.getPaddingTop(), view.getWidth() - view.getPaddingRight(),
								view.getHeight() - view.getPaddingBottom());
					}
					else rect.set(0, 0, view.getWidth(), view.getHeight());
					outline.setRoundRect(rect, radius);
				}
			});
		}
	}
	
	public static void dismissDialogQuietly(DialogInterface dialog)
	{
		try
		{
			dialog.dismiss();
		}
		catch (IllegalArgumentException e)
		{
			// May be detached from window manager
		}
	}
	
	public static void addNotificationAction(Notification.Builder builder, Context context, TypedArray typedArray,
			int resourceIndex, int titleRes, PendingIntent intent)
	{
		addNotificationAction(builder, context, typedArray.getResourceId(resourceIndex, 0),
				context.getString(titleRes), intent);
	}
	
	@TargetApi(Build.VERSION_CODES.M)
	@SuppressWarnings("deprecation")
	public static void addNotificationAction(Notification.Builder builder, Context context, int icon,
			CharSequence title, PendingIntent intent)
	{
		if (C.API_MARSHMALLOW)
		{
			builder.addAction(new Notification.Action.Builder(Icon.createWithResource(context, icon),
					title, intent).build());
		}
		else builder.addAction(icon, title, intent);
	}
}