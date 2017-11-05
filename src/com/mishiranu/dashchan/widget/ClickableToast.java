/*
 * Copyright 2014-2017 Fukurou Mishiranu
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
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import chan.util.StringUtils;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.util.FlagUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ToastUtils;
import com.mishiranu.dashchan.util.ViewUtils;

public class ClickableToast {
	private static final int Y_OFFSET;
	private static final int LAYOUT_ID;

	private static final int TIMEOUT = 3500;

	static {
		Resources resources = Resources.getSystem();
		Y_OFFSET = resources.getDimensionPixelSize(resources.getIdentifier("toast_y_offset", "dimen", "android"));
		LAYOUT_ID = resources.getIdentifier("transient_notification", "layout", "android");
	}

	public static class Holder {
		private final Context context;

		public Holder(Context context) {
			this.context = context;
		}

		private boolean hasFocus = true;
		private boolean resumed = false;

		public void onWindowFocusChanged(boolean hasFocus) {
			this.hasFocus = hasFocus;
			invalidate();
		}

		public void onResume() {
			resumed = true;
			invalidate();
		}

		public void onPause() {
			resumed = false;
			invalidate();
		}

		private void invalidate() {
			ClickableToast.invalidate(context);
		}
	}

	private final View container;
	private final WindowManager windowManager;
	private final Holder holder;

	private final PartialClickDrawable partialClickDrawable;
	private final TextView message;
	private final TextView button;

	private ViewGroup currentContainer;
	private Runnable onClickListener;
	private boolean showing, clickable, realClickable, clickableOnlyWhenRoot;

	private static final HashMap<Context, ClickableToast> TOASTS = new HashMap<>();

	private static Context obtainBaseContext(Context context) {
		while (context instanceof ContextWrapper) {
			context = ((ContextWrapper) context).getBaseContext();
		}
		return context;
	}

	public static void register(Holder holder) {
		Context context = obtainBaseContext(holder.context);
		ClickableToast clickableToast = TOASTS.get(context);
		if (clickableToast == null) {
			TOASTS.put(context, new ClickableToast(holder));
		}
	}

	public static void unregister(Holder holder) {
		ClickableToast clickableToast = TOASTS.remove(obtainBaseContext(holder.context));
		if (clickableToast != null) {
			clickableToast.cancelInternal();
		}
	}

	public static void show(Context context, int message) {
		show(context, context.getString(message));
	}

	public static void show(Context context, CharSequence message) {
		show(context, message, null, null, true);
	}

	public static void show(Context context, CharSequence message, String button, Runnable listener,
			boolean clickableOnlyWhenRoot) {
		ClickableToast clickableToast = TOASTS.get(obtainBaseContext(context));
		if (clickableToast != null) {
			clickableToast.showInternal(message, button, listener, clickableOnlyWhenRoot);
		}
	}

	public static void cancel(Context context) {
		ClickableToast clickableToast = TOASTS.get(obtainBaseContext(context));
		if (clickableToast != null) {
			clickableToast.cancelInternal();
		}
	}

	private static void invalidate(Context context) {
		ClickableToast clickableToast = TOASTS.get(obtainBaseContext(context));
		if (clickableToast != null && clickableToast.showing) {
			clickableToast.updateLayoutAndRealClickable();
		}
	}

	private Rect getViewTotalPadding(View parent, View view) {
		Rect rect = new Rect(0, 0, 0, 0);
		while (view != parent) {
			rect.left += view.getLeft();
			rect.top += view.getTop();
			int right = view.getRight();
			int bottom = view.getBottom();
			view = (View) view.getParent();
			rect.right += view.getWidth() - right;
			rect.bottom += view.getHeight() - bottom;
		}
		return rect;
	}

	private ClickableToast(Holder holder) {
		this.holder = holder;
		Context context = holder.context;
		float density = ResourceUtils.obtainDensity(context);
		int innerPadding = (int) (8f * density);
		LayoutInflater inflater = LayoutInflater.from(context);
		View toast1 = inflater.inflate(LAYOUT_ID, null);
		View toast2 = inflater.inflate(LAYOUT_ID, null);
		TextView message1 = toast1.findViewById(android.R.id.message);
		TextView message2 = toast2.findViewById(android.R.id.message);
		View backgroundSource = null;
		Drawable backgroundDrawable = toast1.getBackground();
		if (backgroundDrawable == null) {
			backgroundDrawable = message1.getBackground();
			if (backgroundDrawable == null) {
				View messageParent = (View) message1.getParent();
				if (messageParent != null) {
					backgroundDrawable = messageParent.getBackground();
					backgroundSource = messageParent;
				}
			} else {
				backgroundSource = message1;
			}
		} else {
			backgroundSource = toast1;
		}

		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < 100; i++) builder.append('W'); // Make long text
		message1.setText(builder); // Avoid minimum widths
		int measureSize = (int) (context.getResources().getConfiguration().screenWidthDp * density + 0.5f);
		toast1.measure(View.MeasureSpec.makeMeasureSpec(measureSize, View.MeasureSpec.AT_MOST),
				View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
		toast1.layout(0, 0, toast1.getMeasuredWidth(), toast1.getMeasuredHeight());
		Rect backgroundSourceTotalPadding = getViewTotalPadding(toast1, backgroundSource);
		Rect messageTotalPadding = getViewTotalPadding(toast1, message1);
		messageTotalPadding.left -= backgroundSourceTotalPadding.left;
		messageTotalPadding.top -= backgroundSourceTotalPadding.top;
		messageTotalPadding.right -= backgroundSourceTotalPadding.right;
		messageTotalPadding.bottom -= backgroundSourceTotalPadding.bottom;
		int horizontalPadding = Math.max(messageTotalPadding.left, messageTotalPadding.right) +
				Math.max(message1.getPaddingLeft(), message1.getPaddingRight());
		int verticalPadding = Math.max(messageTotalPadding.top, messageTotalPadding.bottom) +
				Math.max(message1.getPaddingTop(), message1.getPaddingBottom());

		ViewUtils.removeFromParent(message1);
		ViewUtils.removeFromParent(message2);
		LinearLayout linearLayout = new LinearLayout(context);
		linearLayout.setOrientation(LinearLayout.HORIZONTAL);
		linearLayout.setDividerDrawable(new ToastDividerDrawable(0xccffffff, (int) (density + 0.5f)));
		linearLayout.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
		linearLayout.setDividerPadding((int) (4f * density));
		linearLayout.setTag(this);
		linearLayout.addView(message1, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		linearLayout.addView(message2, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		((LinearLayout.LayoutParams) message1.getLayoutParams()).weight = 1f;
		linearLayout.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);

		partialClickDrawable = new PartialClickDrawable(backgroundDrawable);
		message1.setBackground(null);
		message2.setBackground(null);
		linearLayout.setBackground(partialClickDrawable);
		linearLayout.setOnTouchListener(partialClickDrawable);
		message1.setPadding(0, 0, 0, 0);
		message2.setPadding(innerPadding, 0, 0, 0);
		message1.setSingleLine(true);
		message2.setSingleLine(true);
		message1.setEllipsize(TextUtils.TruncateAt.END);
		message2.setEllipsize(TextUtils.TruncateAt.END);
		container = linearLayout;
		message = message1;
		button = message2;

		windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
	}

	private void showInternal(CharSequence message, String button, Runnable listener, boolean clickableOnlyWhenRoot) {
		ToastUtils.cancel();
		cancelInternal();
		this.message.setText(message);
		this.button.setText(button);
		onClickListener = listener;
		partialClickDrawable.clicked = false;
		partialClickDrawable.invalidateSelf();
		clickable = !StringUtils.isEmpty(button);
		this.clickableOnlyWhenRoot = clickableOnlyWhenRoot;
		updateLayoutAndRealClickableInternal();
		boolean added = false;
		if (C.API_LOLLIPOP) {
			// TYPE_TOAST works well only on Lollipop and higher, but can throw BadTokenException on some devices
			// noinspection deprecation
			added = addContainerToWindowManager(WindowManager.LayoutParams.TYPE_TOAST);
		}
		if (!added) {
			added = addContainerToWindowManager(WindowManager.LayoutParams.TYPE_APPLICATION);
		}
		if (added) {
			showing = true;
			container.postDelayed(cancelRunnable, TIMEOUT);
		}
	}

	private boolean addContainerToWindowManager(int type) {
		boolean success = false;
		try {
			currentContainer = new FrameLayout(container.getContext());
			currentContainer.addView(container, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
					FrameLayout.LayoutParams.WRAP_CONTENT));
			windowManager.addView(currentContainer, createLayoutParams(type));
			success = true;
		} catch (WindowManager.BadTokenException e) {
			removeCurrentContainer();
			String errorMessage = e.getMessage();
			if (errorMessage == null || !(errorMessage.contains("permission denied") ||
					errorMessage.contains("has already been added"))) {
				throw e;
			}
		} finally {
			if (!success) {
				removeCurrentContainer();
			}
		}
		return success;
	}

	private WindowManager.LayoutParams updateLayoutParams(WindowManager.LayoutParams layoutParams) {
		layoutParams.flags = FlagUtils.set(layoutParams.flags, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
				!realClickable);
		return layoutParams;
	}

	private WindowManager.LayoutParams createLayoutParams(int type) {
		WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
		layoutParams.type = type;
		layoutParams.format = PixelFormat.TRANSLUCENT;
		layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
		layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
		layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
				WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
		// For hierarchy viewer (layout inspector)
		layoutParams.setTitle(container.getContext().getPackageName() + "/" + getClass().getName());
		layoutParams.windowAnimations = android.R.style.Animation_Toast;
		layoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
		layoutParams.y = Y_OFFSET;
		try {
			Field field = WindowManager.LayoutParams.class.getField("privateFlags");
			// PRIVATE_FLAG_NO_MOVE_ANIMATION == 0x00000040
			field.set(layoutParams, field.getInt(layoutParams) | 0x00000040);
		} catch (Exception e) {
			// Reflective operation, ignore exception
		}
		return updateLayoutParams(layoutParams);
	}

	private void updateLayoutAndRealClickableInternal() {
		realClickable = clickable && (holder.hasFocus || !clickableOnlyWhenRoot) && holder.resumed;
		button.setVisibility(realClickable ? View.VISIBLE : View.GONE);
		message.setPadding(0, 0, realClickable ? button.getPaddingLeft() : 0, 0);
	}

	private void updateLayoutAndRealClickable() {
		if (!clickable) {
			return;
		}
		updateLayoutAndRealClickableInternal();
		if (currentContainer != null) {
			windowManager.updateViewLayout(currentContainer,
					updateLayoutParams((WindowManager.LayoutParams) currentContainer.getLayoutParams()));
		}
	}

	private void cancelInternal() {
		if (!showing) {
			return;
		}
		container.removeCallbacks(cancelRunnable);
		showing = false;
		clickable = false;
		realClickable = false;
		removeCurrentContainer();
	}

	private void removeCurrentContainer() {
		if (currentContainer != null) {
			if (currentContainer.getParent() != null) {
				windowManager.removeView(currentContainer);
			}
			currentContainer.removeView(container);
			currentContainer = null;
		}
	}

	private final Runnable cancelRunnable = this::cancelInternal;

	private void postCancelInternal() {
		container.post(cancelRunnable);
	}

	private class PartialClickDrawable extends Drawable implements View.OnTouchListener, Drawable.Callback {
		private final Drawable drawable;
		private final ColorFilter colorFilter = new ColorMatrixColorFilter(BRIGHTNESS_MATRIX);

		private boolean clicked = false;

		public PartialClickDrawable(Drawable drawable) {
			this.drawable = drawable;
			drawable.setCallback(this);
		}

		private View getView() {
			return getCallback() instanceof View ? ((View) getCallback()) : null;
		}

		@Override
		public boolean onTouch(View v, MotionEvent event) {
			if (!realClickable) {
				return false;
			}
			if (event.getAction() == MotionEvent.ACTION_DOWN) {
				if (event.getX() >= button.getLeft()) {
					clicked = true;
					View view = getView();
					if (view != null) {
						view.invalidate();
					}
				}
			}
			if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
				if (clicked) {
					clicked = false;
					View view = getView();
					if (view != null) {
						view.invalidate();
						if (event.getAction() == MotionEvent.ACTION_UP) {
							float x = event.getX(), y = event.getY();
							if (x >= button.getLeft() && x <= view.getWidth() && y >= 0 && y <= view.getHeight()) {
								if (onClickListener != null) {
									onClickListener.run();
								}
								postCancelInternal();
							}
						}
					}
					return true;
				}
			}
			return clicked;
		}

		@Override
		public void setBounds(int left, int top, int right, int bottom) {
			super.setBounds(left, top, right, bottom);
			drawable.setBounds(left, top, right, bottom);
		}

		@TargetApi(Build.VERSION_CODES.LOLLIPOP)
		@Override
		public Rect getDirtyBounds() {
			return drawable.getDirtyBounds();
		}

		@Override
		public void draw(Canvas canvas) {
			drawable.draw(canvas);
			if (clicked) {
				drawable.setColorFilter(colorFilter);
				canvas.save();
				Rect bounds = getBounds();
				int shift = button.getLeft();
				canvas.clipRect(bounds.left + shift, bounds.top, bounds.right, bounds.bottom);
				drawable.draw(canvas);
				canvas.restore();
				drawable.setColorFilter(null);
			}
		}

		@Override
		public int getOpacity() {
			return drawable.getOpacity();
		}

		@Override
		public void setAlpha(int alpha) {
			drawable.setAlpha(alpha);
		}

		@Override
		public void setColorFilter(ColorFilter cf) {}

		@Override
		public int getIntrinsicWidth() {
			return drawable.getIntrinsicWidth();
		}

		@Override
		public int getIntrinsicHeight() {
			return drawable.getIntrinsicHeight();
		}

		@Override
		public void invalidateDrawable(Drawable who) {
			invalidateSelf();
		}

		@Override
		public void scheduleDrawable(Drawable who, Runnable what, long when) {
			scheduleSelf(what, when);
		}

		@Override
		public void unscheduleDrawable(Drawable who, Runnable what) {
			unscheduleSelf(what);
		}
	}

	private static final float[] BRIGHTNESS_MATRIX = {
		2f, 0f, 0f, 0f, 0f,
		0f, 2f, 0f, 0f, 0f,
		0f, 0f, 2f, 0f, 0f,
		0f, 0f, 0f, 1f, 0f
	};

	private static class ToastDividerDrawable extends ColorDrawable {
		private final int width;

		public ToastDividerDrawable(int color, int width) {
			super(color);
			this.width = width;
		}

		@Override
		public int getIntrinsicWidth() {
			return width;
		}
	}
}
