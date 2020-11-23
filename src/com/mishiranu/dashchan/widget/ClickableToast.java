package com.mishiranu.dashchan.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
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
import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.OnLifecycleEvent;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.graphics.BaseDrawable;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.FlagUtils;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.Log;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.UUID;

public class ClickableToast implements LifecycleObserver {
	private static final int Y_OFFSET;
	private static final int LAYOUT_ID;

	private static final int TIMEOUT = 3500;

	static {
		Resources resources = Resources.getSystem();
		Y_OFFSET = resources.getDimensionPixelSize(resources.getIdentifier("toast_y_offset", "dimen", "android"));
		LAYOUT_ID = resources.getIdentifier("transient_notification", "layout", "android");
	}

	private final View container;
	private final WindowManager windowManager;

	private final PartialClickDrawable partialClickDrawable;
	private final TextView message;
	private final TextView button;

	private ViewGroup currentContainer;
	private Runnable onClickListener;
	private String showing;
	private boolean clickable;
	private boolean realClickable;
	private boolean clickableOnlyWhenRoot;

	private boolean resumed;
	private boolean focused;

	private static WeakReference<ComponentActivity> activity;

	private static View getTagView(ComponentActivity activity) {
		return activity.getWindow().getDecorView();
	}

	private static ClickableToast getToast(ComponentActivity activity) {
		return (ClickableToast) getTagView(activity).getTag(R.id.tag_clickable_toast);
	}

	private static ClickableToast getCurrentToast() {
		if (activity != null) {
			ComponentActivity activity = ClickableToast.activity.get();
			return activity != null ? getToast(activity) : null;
		}
		return null;
	}

	public static void register(@NonNull ComponentActivity activity) {
		Objects.requireNonNull(activity);
		if (ClickableToast.activity != null) {
			ComponentActivity oldActivity = ClickableToast.activity.get();
			if (oldActivity == activity) {
				return;
			}
			if (oldActivity != null) {
				getToast(oldActivity).cancelInternal();
			}
		}
		ClickableToast.activity = null;
		Lifecycle.State state = activity.getLifecycle().getCurrentState();
		if (state.isAtLeast(Lifecycle.State.INITIALIZED)) {
			if (getToast(activity) == null) {
				getTagView(activity).setTag(R.id.tag_clickable_toast, new ClickableToast(activity));
			}
			ClickableToast.activity = new WeakReference<>(activity);
		}
	}

	public static class Button {
		private final int titleResId;
		private final boolean clickableOnlyWhenRoot;
		private final Runnable callback;

		public Button(int titleResId, boolean clickableOnlyWhenRoot, Runnable callback) {
			this.titleResId = titleResId;
			this.clickableOnlyWhenRoot = clickableOnlyWhenRoot;
			this.callback = callback;
		}
	}

	public static String show(int message) {
		return show(new ErrorItem(message));
	}

	public static String show(ErrorItem errorItem) {
		return show((errorItem != null ? errorItem : new ErrorItem(ErrorItem.Type.UNKNOWN)).toString());
	}

	public static String show(CharSequence message) {
		return show(message, null, null);
	}

	public static String show(CharSequence message, String updateId, Button button) {
		if (ConcurrentUtils.isMain()) {
			ClickableToast toast = getCurrentToast();
			if (toast != null) {
				return toast.showInternal(message, updateId, button);
			} else {
				return null;
			}
		} else {
			return ConcurrentUtils.mainGet(() -> show(message, updateId, null));
		}
	}

	public static boolean isShowing(String id) {
		ClickableToast toast = getCurrentToast();
		return toast != null && id != null && id.equals(toast.showing);
	}

	public static void cancel() {
		ClickableToast toast = getCurrentToast();
		if (toast != null) {
			toast.cancelInternal();
		}
	}

	private ClickableToast(ComponentActivity activity) {
		activity.getLifecycle().addObserver(this);
		resumed = activity.getLifecycle().getCurrentState() == Lifecycle.State.RESUMED;
		float density = ResourceUtils.obtainDensity(activity);
		int innerPadding = (int) (8f * density);
		LayoutInflater inflater = LayoutInflater.from(activity);
		View toast1 = inflater.inflate(LAYOUT_ID, null);
		View toast2 = inflater.inflate(LAYOUT_ID, null);
		TextView message1 = toast1.findViewById(android.R.id.message);
		TextView message2 = toast2.findViewById(android.R.id.message);
		Drawable backgroundDrawable = toast1.getBackground();
		if (backgroundDrawable == null) {
			View view = message1;
			while (view != null) {
				backgroundDrawable = message1.getBackground();
				if (backgroundDrawable == null) {
					break;
				}
				view = (View) view.getParent();
			}
		}

		// Make long text to avoid minimum widths
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < 100; i++) {
			builder.append('W');
		}
		message1.setText(builder);
		int measureSize = (int) (activity.getResources().getConfiguration().screenWidthDp * density + 0.5f);
		toast1.measure(View.MeasureSpec.makeMeasureSpec(measureSize, View.MeasureSpec.AT_MOST),
				View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
		int lineCount = message1.getLayout().getLineCount();
		if (lineCount >= 2) {
			builder.setLength(message1.getLayout().getLineEnd(0));
			message1.setText(builder);
			toast1.measure(View.MeasureSpec.makeMeasureSpec(measureSize, View.MeasureSpec.AT_MOST),
					View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
		}
		toast1.layout(0, 0, toast1.getMeasuredWidth(), toast1.getMeasuredHeight());
		Rect totalPadding = new Rect(message1.getPaddingLeft(), message1.getPaddingTop(),
				message1.getPaddingRight(), message1.getPaddingBottom());
		int messageMeasuredHeight = message1.getHeight();
		View measureView = message1;
		while (true) {
			View parent = (View) measureView.getParent();
			if (parent == null) {
				break;
			}
			totalPadding.left += measureView.getLeft();
			totalPadding.top += measureView.getTop();
			totalPadding.right += parent.getWidth() - measureView.getRight();
			totalPadding.bottom += parent.getHeight() - measureView.getBottom();
			measureView = parent;
		}
		message1.measure(View.MeasureSpec.makeMeasureSpec(message1.getWidth(), View.MeasureSpec.EXACTLY),
				View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
		int extraHeight = messageMeasuredHeight - message1.getMeasuredHeight();
		totalPadding.top += extraHeight / 2;
		totalPadding.bottom += extraHeight / 2;
		int horizontalPadding = Math.max(totalPadding.left, totalPadding.right);

		ViewUtils.removeFromParent(message1);
		ViewUtils.removeFromParent(message2);
		LinearLayout linearLayout = new LinearLayout(activity);
		linearLayout.setOrientation(LinearLayout.HORIZONTAL);
		linearLayout.setDividerDrawable(new ToastDividerDrawable(message1.getTextColors().getDefaultColor(),
				(int) (density + 0.5f)));
		linearLayout.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
		linearLayout.setDividerPadding((int) (4f * density));
		linearLayout.setTag(this);
		linearLayout.addView(message1, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		linearLayout.addView(message2, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		((LinearLayout.LayoutParams) message1.getLayoutParams()).weight = 1f;
		linearLayout.setPadding(horizontalPadding, totalPadding.top, horizontalPadding, totalPadding.bottom);

		partialClickDrawable = new PartialClickDrawable(activity, backgroundDrawable);
		message1.setBackground(null);
		message2.setBackground(null);
		linearLayout.setBackground(partialClickDrawable);
		linearLayout.setOnTouchListener(partialClickDrawable);
		message1.setPadding(0, 0, 0, 0);
		ViewCompat.setPaddingRelative(message2, innerPadding, 0, 0, 0);
		message1.setSingleLine(true);
		message2.setSingleLine(true);
		message1.setEllipsize(TextUtils.TruncateAt.END);
		message2.setEllipsize(TextUtils.TruncateAt.END);
		container = linearLayout;
		message = message1;
		button = message2;

		windowManager = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
		ViewGroup viewGroup = (ViewGroup) activity.getWindow().getDecorView();
		viewGroup.addView(new View(activity) {
			@Override
			public void onWindowFocusChanged(boolean hasWindowFocus) {
				super.onWindowFocusChanged(hasWindowFocus);
				focused = hasWindowFocus;
				updateAndApplyLayoutChecked();
			}
		}, 0, 0);
	}

	@SuppressWarnings("unused")
	@OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
	public void onResume() {
		resumed = true;
		updateAndApplyLayoutChecked();
	}

	@SuppressWarnings("unused")
	@OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
	public void onPause() {
		resumed = false;
		updateAndApplyLayoutChecked();
	}

	@SuppressWarnings("unused")
	@OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
	public void onDestroy(LifecycleOwner owner) {
		if (activity != null && activity.get() == owner) {
			activity = null;
		}
		cancelInternal();
	}

	private String showInternal(CharSequence message, String updateId, Button button) {
		boolean update = updateId != null && updateId.equals(showing);
		if (update) {
			ConcurrentUtils.HANDLER.removeCallbacks(cancelRunnable);
		} else {
			cancelInternal();
		}
		clickable = button != null;
		this.message.setText(message);
		if (button != null) {
			this.button.setText(button.titleResId);
		}
		onClickListener = button != null ? button.callback : null;
		partialClickDrawable.clicked = false;
		partialClickDrawable.invalidateSelf();
		clickableOnlyWhenRoot = button == null || button.clickableOnlyWhenRoot;
		updateLayout();
		if (update) {
			applyLayout();
			ConcurrentUtils.HANDLER.postDelayed(cancelRunnable, TIMEOUT);
			return updateId;
		} else if (addContainerToWindowManager()) {
			String id = UUID.randomUUID().toString();
			showing = id;
			ConcurrentUtils.HANDLER.postDelayed(cancelRunnable, TIMEOUT);
			return id;
		} else {
			return null;
		}
	}

	private boolean addContainerToWindowManager() {
		boolean added = false;
		if (C.API_OREO) {
			// TYPE_APPLICATION_OVERLAY requires SYSTEM_ALERT_WINDOW permission
			if (Settings.canDrawOverlays(container.getContext())) {
				added = addContainerToWindowManager(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
			}
		} else if (C.API_NOUGAT_MR1) {
			// TYPE_TOAST is prohibited on 7.1 when target API is > 7.1 (excuse me, wtf?)
			// TYPE_PHONE requires SYSTEM_ALERT_WINDOW permission
			if (Settings.canDrawOverlays(container.getContext())) {
				@SuppressWarnings("deprecation")
				int type = WindowManager.LayoutParams.TYPE_PHONE;
				added = addContainerToWindowManager(type);
			}
		} else if (C.API_LOLLIPOP && !IS_MIUI) {
			// TYPE_TOAST works well only on Android 5.0-7.1, but doesn't work on MIUI
			@SuppressWarnings("deprecation")
			int type = WindowManager.LayoutParams.TYPE_TOAST;
			added = addContainerToWindowManager(type);
		}
		if (!added) {
			// TYPE_APPLICATION can't even properly overlay dialogs, used as fallback option
			added = addContainerToWindowManager(WindowManager.LayoutParams.TYPE_APPLICATION);
		}
		return added;
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
		layoutParams.flags = FlagUtils.set(layoutParams.flags,
				WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, !realClickable);
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
			Log.persistent().stack(e);
		}
		return updateLayoutParams(layoutParams);
	}

	private void updateAndApplyLayoutChecked() {
		if (showing != null && clickable) {
			updateLayout();
			applyLayout();
		}
	}

	private void updateLayout() {
		realClickable = clickable && (focused || !clickableOnlyWhenRoot) && resumed;
		button.setVisibility(realClickable ? View.VISIBLE : View.GONE);
		message.setPadding(realClickable ? button.getPaddingRight() : 0, 0,
				realClickable ? button.getPaddingLeft() : 0, 0);
	}

	private void applyLayout() {
		if (currentContainer != null) {
			windowManager.updateViewLayout(currentContainer,
					updateLayoutParams((WindowManager.LayoutParams) currentContainer.getLayoutParams()));
		}
	}

	private void cancelInternal() {
		ConcurrentUtils.HANDLER.removeCallbacks(cancelRunnable);
		if (showing == null) {
			return;
		}
		showing = null;
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

	private class PartialClickDrawable extends BaseDrawable implements View.OnTouchListener, Drawable.Callback {
		private final Drawable drawable;
		private final ColorFilter colorFilter;

		private boolean clicked = false;

		public PartialClickDrawable(Context context, Drawable drawable) {
			this.drawable = drawable;
			int color = GraphicsUtils.getDrawableColor(context, drawable, Gravity.CENTER);
			boolean isLight = GraphicsUtils.isLight(color);
			float source = (Color.red(color) + Color.green(color) + Color.blue(color)) / 3f / 0xff;
			float target = source + (isLight ? -0.15f : 0.2f);
			float multiplier = target / source;
			float[] matrix = new float[20];
			for (int i = 0; i < 3; i++) {
				matrix[6 * i] = multiplier;
			}
			matrix[18] = 1f;
			colorFilter = new ColorMatrixColorFilter(matrix);
			drawable.setCallback(this);
		}

		private View getView() {
			return getCallback() instanceof View ? ((View) getCallback()) : null;
		}

		@SuppressLint("ClickableViewAccessibility")
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
								ConcurrentUtils.HANDLER.removeCallbacks(cancelRunnable);
								ConcurrentUtils.HANDLER.post(cancelRunnable);
								if (onClickListener != null) {
									onClickListener.run();
								}
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

		@NonNull
		@Override
		public Rect getDirtyBounds() {
			return drawable.getDirtyBounds();
		}

		@Override
		public void draw(@NonNull Canvas canvas) {
			drawable.draw(canvas);
			if (clicked) {
				drawable.setColorFilter(colorFilter);
				canvas.save();
				Rect bounds = getBounds();
				if (ViewCompat.getLayoutDirection(button) == ViewCompat.LAYOUT_DIRECTION_RTL) {
					int shift = button.getRight();
					canvas.clipRect(bounds.left + shift, bounds.top, bounds.left + shift, bounds.bottom);
				} else {
					int shift = button.getLeft();
					canvas.clipRect(bounds.left + shift, bounds.top, bounds.right, bounds.bottom);
				}
				drawable.draw(canvas);
				canvas.restore();
				drawable.setColorFilter(null);
			}
		}

		@SuppressWarnings("deprecation")
		@Override
		public int getOpacity() {
			return drawable.getOpacity();
		}

		@Override
		public void setAlpha(int alpha) {
			drawable.setAlpha(alpha);
		}

		@Override
		public int getIntrinsicWidth() {
			return drawable.getIntrinsicWidth();
		}

		@Override
		public int getIntrinsicHeight() {
			return drawable.getIntrinsicHeight();
		}

		@Override
		public void invalidateDrawable(@NonNull Drawable who) {
			invalidateSelf();
		}

		@Override
		public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
			scheduleSelf(what, when);
		}

		@Override
		public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
			unscheduleSelf(what);
		}
	}

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

	private static final boolean IS_MIUI;

	static {
		boolean isMiui = false;
		try {
			Method getProperty = Class.forName("android.os.SystemProperties")
					.getMethod("get", String.class, String.class);
			isMiui = !StringUtils.isEmpty((String) getProperty.invoke(null, "ro.miui.ui.version.name", ""));
		} catch (Exception e) {
			// Ignore exception
		}
		IS_MIUI = isMiui;
	}
}
