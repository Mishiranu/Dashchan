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

package com.mishiranu.dashchan.widget;

import java.lang.reflect.Field;
import java.util.HashMap;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import chan.util.StringUtils;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.util.FlagUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ToastUtils;
import com.mishiranu.dashchan.util.ViewUtils;

public class ClickableToast
{
	private static final int Y_OFFSET;
	private static final int LAYOUT_ID;

	private static final int TIMEOUT = 3500;

	static
	{
		Resources resources = Resources.getSystem();
		Y_OFFSET = resources.getDimensionPixelSize(resources.getIdentifier("toast_y_offset", "dimen", "android"));
		LAYOUT_ID = resources.getIdentifier("transient_notification", "layout", "android");
	}

	public static class Holder
	{
		private final Context mContext;

		public Holder(Context context)
		{
			mContext = context;
		}

		private boolean mHasFocus = true;
		private boolean mResumed = false;

		public void onWindowFocusChanged(boolean hasFocus)
		{
			mHasFocus = hasFocus;
			invalidate();
		}

		public void onResume()
		{
			mResumed = true;
			invalidate();
		}

		public void onPause()
		{
			mResumed = false;
			invalidate();
		}

		private void invalidate()
		{
			ClickableToast.invalidate(mContext);
		}
	}

	private final View mContainer;
	private final WindowManager mWindowManager;
	private final Holder mHolder;

	private final PartialClickDrawable mPartialClickDrawable;
	private final TextView mMessage;
	private final TextView mButton;

	private Runnable mOnClickListener;
	private boolean mShowing, mCanClickable, mRealClickable, mClickableOnlyWhenRoot;

	private static final HashMap<Context, ClickableToast> TOASTS = new HashMap<>();

	private static Context obtainBaseContext(Context context)
	{
		while (context instanceof ContextWrapper) context = ((ContextWrapper) context).getBaseContext();
		return context;
	}

	public static void register(Holder holder)
	{
		Context context = obtainBaseContext(holder.mContext);
		ClickableToast clickableToast = TOASTS.get(context);
		if (clickableToast == null) TOASTS.put(context, new ClickableToast(holder));
	}

	public static void unregister(Holder holder)
	{
		ClickableToast clickableToast = TOASTS.remove(obtainBaseContext(holder.mContext));
		if (clickableToast != null) clickableToast.cancelInternal();
	}

	public static void show(Context context, int message)
	{
		show(context, context.getString(message));
	}

	public static void show(Context context, CharSequence message)
	{
		show(context, message, null, null, true);
	}

	public static void show(Context context, CharSequence message, String button, Runnable listener,
			boolean clickableOnlyWhenRoot)
	{
		ClickableToast clickableToast = TOASTS.get(obtainBaseContext(context));
		if (clickableToast != null) clickableToast.showInternal(message, button, listener, clickableOnlyWhenRoot);
	}

	public static void cancel(Context context)
	{
		ClickableToast clickableToast = TOASTS.get(obtainBaseContext(context));
		if (clickableToast != null) clickableToast.cancelInternal();
	}

	private static void invalidate(Context context)
	{
		ClickableToast clickableToast = TOASTS.get(obtainBaseContext(context));
		if (clickableToast != null && clickableToast.mShowing) clickableToast.updateLayoutAndRealClickable();
	}

	private ClickableToast(Holder holder)
	{
		mHolder = holder;
		Context context = holder.mContext;
		float density = ResourceUtils.obtainDensity(context);
		int innerPadding = (int) (8f * density);
		LayoutInflater inflater = LayoutInflater.from(context);
		View toast1 = inflater.inflate(LAYOUT_ID, null);
		View toast2 = inflater.inflate(LAYOUT_ID, null);
		TextView message1 = (TextView) toast1.findViewById(android.R.id.message);
		TextView message2 = (TextView) toast2.findViewById(android.R.id.message);
		ViewUtils.removeFromParent(message1);
		ViewUtils.removeFromParent(message2);
		LinearLayout linearLayout = new LinearLayout(context);
		linearLayout.setOrientation(LinearLayout.HORIZONTAL);
		linearLayout.setTag(this);
		linearLayout.setDividerDrawable(new ToastDividerDrawable(0xccffffff, (int) (density + 0.5f)));
		linearLayout.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
		linearLayout.setDividerPadding((int) (4f * density));
		linearLayout.setTag(this);
		linearLayout.addView(message1, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		linearLayout.addView(message2, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		((LinearLayout.LayoutParams) message1.getLayoutParams()).weight = 1f;
		Drawable background = toast1.getBackground();
		if (background == null)
		{
			background = message1.getBackground();
			linearLayout.setPadding(message1.getPaddingLeft(), message1.getPaddingTop(),
					message1.getPaddingRight(), message1.getPaddingBottom());
		}
		else
		{
			linearLayout.setPadding(toast1.getPaddingLeft(), toast1.getPaddingTop(),
					toast1.getPaddingRight(), toast1.getPaddingBottom());
		}
		mPartialClickDrawable = new PartialClickDrawable(background);
		message1.setBackground(null);
		message2.setBackground(null);
		linearLayout.setBackground(mPartialClickDrawable);
		linearLayout.setOnTouchListener(mPartialClickDrawable);
		message1.setPadding(0, 0, 0, 0);
		message2.setPadding(innerPadding, 0, 0, 0);
		message1.setSingleLine(true);
		message2.setSingleLine(true);
		message1.setEllipsize(TextUtils.TruncateAt.END);
		message2.setEllipsize(TextUtils.TruncateAt.END);
		mContainer = linearLayout;
		mMessage = message1;
		mButton = message2;
		mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
		layoutParams.format = PixelFormat.TRANSLUCENT;
		layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
		layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
		layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
				WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
		layoutParams.setTitle(context.getPackageName() + "/" + getClass().getName()); // For hierarchy view
		layoutParams.windowAnimations = android.R.style.Animation_Toast;
		layoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
		layoutParams.y = Y_OFFSET;
		try
		{
			Field field = WindowManager.LayoutParams.class.getField("privateFlags");
			// PRIVATE_FLAG_NO_MOVE_ANIMATION == 0x00000040
			field.set(layoutParams, field.getInt(layoutParams) | 0x00000040);
		}
		catch (Exception e)
		{

		}
		mContainer.setLayoutParams(layoutParams);
	}

	private void showInternal(CharSequence message, String button, Runnable listener, boolean clickableOnlyWhenRoot)
	{
		ToastUtils.cancel();
		cancelInternal();
		mMessage.setText(message);
		mButton.setText(button);
		mOnClickListener = listener;
		mPartialClickDrawable.mClicked = false;
		mPartialClickDrawable.invalidateSelf();
		mCanClickable = !StringUtils.isEmpty(button);
		mClickableOnlyWhenRoot = clickableOnlyWhenRoot;
		WindowManager.LayoutParams layoutParams = updateLayoutAndRealClickableInternal();
		boolean added = false;
		if (C.API_LOLLIPOP)
		{
			// TYPE_TOAST works well only on Lollipop and higher, but can throw BadTokenException on some devices
			layoutParams.type = WindowManager.LayoutParams.TYPE_TOAST;
			added = addContainerToWindowManager();
		}
		if (!added)
		{
			layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION;
			added = addContainerToWindowManager();
		}
		if (added)
		{
			mShowing = true;
			mContainer.postDelayed(mCancelRunnable, TIMEOUT);
		}
	}

	private boolean addContainerToWindowManager()
	{
		try
		{
			mWindowManager.addView(mContainer, mContainer.getLayoutParams());
			return true;
		}
		catch (WindowManager.BadTokenException e)
		{
			String errorMessage = e.getMessage();
			if (errorMessage != null && errorMessage.contains("permission denied")) return false;
			throw e;
		}
	}

	private WindowManager.LayoutParams updateLayoutAndRealClickableInternal()
	{
		mRealClickable = mCanClickable && (mHolder.mHasFocus || !mClickableOnlyWhenRoot) && mHolder.mResumed;
		mButton.setVisibility(mRealClickable ? View.VISIBLE : View.GONE);
		mMessage.setPadding(0, 0, mRealClickable ? mButton.getPaddingLeft() : 0, 0);
		WindowManager.LayoutParams layoutParams = (WindowManager.LayoutParams) mContainer.getLayoutParams();
		layoutParams.flags = FlagUtils.set(layoutParams.flags, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
				!mRealClickable);
		return layoutParams;
	}

	private void updateLayoutAndRealClickable()
	{
		if (!mCanClickable) return;
		mWindowManager.updateViewLayout(mContainer, updateLayoutAndRealClickableInternal());
	}

	private void cancelInternal()
	{
		if (!mShowing) return;
		mContainer.removeCallbacks(mCancelRunnable);
		mShowing = false;
		mCanClickable = false;
		mRealClickable = false;
		mWindowManager.removeView(mContainer);
	}

	private final Runnable mCancelRunnable = () -> cancelInternal();

	private void postCancelInternal()
	{
		mContainer.post(mCancelRunnable);
	}

	private class PartialClickDrawable extends Drawable implements View.OnTouchListener, Drawable.Callback
	{
		private final Drawable mDrawable;
		private final ColorFilter mColorFilter = new ColorMatrixColorFilter(BRIGHTNESS_MATRIX);

		private boolean mClicked = false;

		public PartialClickDrawable(Drawable drawable)
		{
			mDrawable = drawable;
			mDrawable.setCallback(this);
		}

		private View getView()
		{
			return getCallback() instanceof View ? ((View) getCallback()) : null;
		}

		@Override
		public boolean onTouch(View v, MotionEvent event)
		{
			if (!mRealClickable) return false;
			if (event.getAction() == MotionEvent.ACTION_DOWN)
			{
				if (event.getX() >= mButton.getLeft())
				{
					mClicked = true;
					View view = getView();
					if (view != null) view.invalidate();
				}
			}
			if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL)
			{
				if (mClicked)
				{
					mClicked = false;
					View view = getView();
					if (view != null)
					{
						view.invalidate();
						if (event.getAction() == MotionEvent.ACTION_UP)
						{
							float x = event.getX(), y = event.getY();
							if (x >= mButton.getLeft() && x <= view.getWidth() && y >= 0 && y <= view.getHeight())
							{
								if (mOnClickListener != null) mOnClickListener.run();
								postCancelInternal();
							}
						}
					}
					return true;
				}
			}
			return mClicked;
		}

		@Override
		public void setBounds(int left, int top, int right, int bottom)
		{
			super.setBounds(left, top, right, bottom);
			mDrawable.setBounds(left, top, right, bottom);
		}

		@TargetApi(Build.VERSION_CODES.LOLLIPOP)
		@Override
		public Rect getDirtyBounds()
		{
			return mDrawable.getDirtyBounds();
		}

		@Override
		public void draw(Canvas canvas)
		{
			mDrawable.draw(canvas);
			if (mClicked)
			{
				mDrawable.setColorFilter(mColorFilter);
				canvas.save();
				Rect bounds = getBounds();
				int shift = mButton.getLeft();
				canvas.clipRect(bounds.left + shift, bounds.top, bounds.right, bounds.bottom);
				mDrawable.draw(canvas);
				canvas.restore();
				mDrawable.setColorFilter(null);
			}
		}

		@Override
		public int getOpacity()
		{
			return mDrawable.getOpacity();
		}

		@Override
		public void setAlpha(int alpha)
		{
			mDrawable.setAlpha(alpha);
		}

		@Override
		public void setColorFilter(ColorFilter cf)
		{

		}

		@Override
		public int getIntrinsicWidth()
		{
			return mDrawable.getIntrinsicWidth();
		}

		@Override
		public int getIntrinsicHeight()
		{
			return mDrawable.getIntrinsicHeight();
		}

		@Override
		public void invalidateDrawable(Drawable who)
		{
			invalidateSelf();
		}

		@Override
		public void scheduleDrawable(Drawable who, Runnable what, long when)
		{
			scheduleSelf(what, when);
		}

		@Override
		public void unscheduleDrawable(Drawable who, Runnable what)
		{
			unscheduleSelf(what);
		}
	}

	private static final float[] BRIGHTNESS_MATRIX =
	{
		2f, 0f, 0f, 0f, 0f,
		0f, 2f, 0f, 0f, 0f,
		0f, 0f, 2f, 0f, 0f,
		0f, 0f, 0f, 1f, 0f
	};

	private static class ToastDividerDrawable extends ColorDrawable
	{
		private final int mWidth;

		public ToastDividerDrawable(int color, int width)
		{
			super(color);
			mWidth = width;
		}

		@Override
		public int getIntrinsicWidth()
		{
			return mWidth;
		}
	}
}