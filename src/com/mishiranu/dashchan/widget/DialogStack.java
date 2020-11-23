package com.mishiranu.dashchan.widget;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.util.Pair;
import android.view.ActionMode;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.customview.widget.ViewDragHelper;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;

public class DialogStack<T extends DialogStack.ViewFactory<T>> implements Iterable<Pair<T, View>> {
	private static final int VISIBLE_COUNT = 10;

	private final Context context;
	private final View contentView;
	private final DragLayout rootView;
	private final int dialogAnimations;
	private final float dialogDimAmount;
	private final int dialogBackgroundResId;
	private final float dialogElevation;

	private WeakReference<ActionMode> currentActionMode;

	public DialogStack(Context context) {
		this.context = context;
		Context styledContext = new ContextThemeWrapper(context, ResourceUtils.getResourceId(context,
				android.R.attr.dialogTheme, 0));
		ThemeEngine.addWeakOnOverlayFocusListener(context, overlayFocusListener);
		InsetsLayout contentView = new InsetsLayout(context);
		this.contentView = contentView;
		rootView = new ContentView(context, DragLayout.Side.TOP, new DragLayout.Callback() {
			private DialogView getLastVisibleDialog() {
				return visibleViews.isEmpty() ? null : visibleViews.getLast().second;
			}

			@Override
			public boolean isScrolled() {
				DialogView dialogView = getLastVisibleDialog();
				return dialogView != null && dialogView.isScrolledToTop();
			}

			@Override
			public boolean onProposeShift(int shift, float acceleration) {
				DialogView dialogView = getLastVisibleDialog();
				return dialogView != null && dialogView.handleShift(shift, acceleration);
			}

			@Override
			public void onFinished() {
				clear();
			}
		});
		contentView.addView(rootView, InsetsLayout.LayoutParams.MATCH_PARENT, InsetsLayout.LayoutParams.MATCH_PARENT);
		contentView.setOnApplyInsetsTarget(rootView);
		rootView.setClipToPadding(false);
		rootView.setClipChildren(false);
		rootView.setOnClickListener(v -> {
			if (!visibleViews.isEmpty()) {
				popInternal();
			}
		});
		int[] attrs = {android.R.attr.windowAnimationStyle, android.R.attr.backgroundDimAmount,
				android.R.attr.windowBackground, C.API_LOLLIPOP ? android.R.attr.windowElevation : 0};
		TypedArray typedArray = styledContext.obtainStyledAttributes(attrs);
		try {
			dialogAnimations = typedArray.getResourceId(0, 0);
			dialogDimAmount = typedArray.getFloat(1, 0.6f);
			dialogBackgroundResId = typedArray.getResourceId(2, 0);
			dialogElevation = C.API_LOLLIPOP ? typedArray.getDimension(3, 0f) : 0;
		} finally {
			typedArray.recycle();
		}

		if (C.API_LOLLIPOP) {
			// Apply elevation to visible children only so their shadows didn't overlap each other too much
			rootView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
				int maxHeight = 0;
				ListIterator<Pair<T, DialogView>> iterator = visibleViews.listIterator(visibleViews.size());
				while (iterator.hasPrevious()) {
					Pair<T, DialogView> pair = iterator.previous();
					int height = pair.second.getHeight();
					boolean taller = height > maxHeight;
					if (taller) {
						maxHeight = height;
					}
					pair.second.setElevated(taller);
				}
			});
		}
	}

	private final KeyBackHandler keyBackHandler = C.API_MARSHMALLOW ? new MarshmallowKeyBackHandler()
			: new RegularKeyBackHandler();

	private interface KeyBackHandler {
		boolean onBackKey(KeyEvent event, boolean allowPop);
	}

	private class RegularKeyBackHandler implements KeyBackHandler {
		@Override
		public boolean onBackKey(KeyEvent event, boolean allowPop) {
			if (event.getAction() == KeyEvent.ACTION_UP) {
				if (!event.isLongPress() && allowPop) {
					popInternal();
				}
				return true;
			} else if (event.getAction() == KeyEvent.ACTION_DOWN) {
				if (event.isLongPress()) {
					clear();
				}
				return true;
			}
			return false;
		}
	}

	// https://issuetracker.google.com/37106088
	private class MarshmallowKeyBackHandler implements KeyBackHandler {
		private boolean posted = false;
		private final Runnable longPressRunnable = () -> {
			posted = false;
			clear();
		};

		@Override
		public boolean onBackKey(KeyEvent event, boolean allowPop) {
			if (event.getAction() == KeyEvent.ACTION_DOWN) {
				if (!posted) {
					rootView.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());
					posted = true;
				}
			} else if (event.getAction() == KeyEvent.ACTION_UP) {
				rootView.removeCallbacks(longPressRunnable);
				posted = false;
				if (allowPop) {
					popInternal();
				}
			}
			return false;
		}
	}

	private final LinkedList<T> hiddenViews = new LinkedList<>();
	private final LinkedList<Pair<T, DialogView>> visibleViews = new LinkedList<>();
	private Dialog dialog;

	@SuppressWarnings("FieldCanBeLocal")
	private final ThemeEngine.OnOverlayFocusListener overlayFocusListener = stack -> {
		View decorView = dialog != null ? dialog.getWindow().getDecorView() : null;
		boolean background = false;
		if (decorView != null) {
			boolean foundSelf = false;
			boolean isDimmedByOtherWindow = false;
			for (ThemeEngine.OnOverlayFocusListener.MutableItem mutableItem : stack) {
				if (!foundSelf) {
					if (mutableItem.decorView == decorView) {
						foundSelf = true;
					}
				} else {
					if (mutableItem.indirect) {
						// Ignore next windows with dim behind, e.g. gallery -> gallery dialog
						break;
					} else {
						isDimmedByOtherWindow = true;
					}
				}
			}
			background = isDimmedByOtherWindow;
		}
		switchBackground(background);
	};

	public void push(T viewFactory) {
		if (dialog == null) {
			Dialog dialog = new Dialog(context, ResourceUtils.getResourceId(context, R.attr.overlayTheme, 0)) {
				@Override
				public void onActionModeStarted(ActionMode mode) {
					currentActionMode = new WeakReference<>(mode);
					super.onActionModeStarted(mode);
				}

				@Override
				public void onActionModeFinished(ActionMode mode) {
					if (currentActionMode != null) {
						if (currentActionMode.get() == mode) {
							currentActionMode = null;
						}
					}
					super.onActionModeFinished(mode);
				}
			};
			dialog.setContentView(contentView);
			dialog.setCancelable(false);
			dialog.setOnKeyListener((d, keyCode, event) -> {
				if (keyCode == KeyEvent.KEYCODE_BACK) {
					return keyBackHandler.onBackKey(event, !visibleViews.isEmpty());
				}
				return false;
			});
			rootView.resetDrag();
			Window window = dialog.getWindow();
			WindowManager.LayoutParams layoutParams = window.getAttributes();
			layoutParams.windowAnimations = dialogAnimations;
			layoutParams.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
			layoutParams.dimAmount = dialogDimAmount;
			// For hierarchy view (layout inspector)
			layoutParams.setTitle(context.getPackageName() + "/" + getClass().getName());
			if (C.API_LOLLIPOP) {
				window.setStatusBarColor(0x00000000);
				window.setNavigationBarColor(0x00000000);
				ViewUtils.setWindowLayoutFullscreen(window);
			}
			dialog.show();
			this.dialog = dialog;
		}
		if (currentActionMode != null) {
			ActionMode mode = currentActionMode.get();
			currentActionMode = null;
			if (mode != null) {
				mode.finish();
			}
		}
		if (!visibleViews.isEmpty()) {
			visibleViews.getLast().second.setActive(false);
			if (visibleViews.size() == VISIBLE_COUNT) {
				Pair<T, DialogView> first = visibleViews.removeFirst();
				first.first.destroyView(first.second.getContent(), false);
				hiddenViews.add(first.first);
				rootView.removeView(first.second.getContainer());
			}
		}
		DialogView dialogView = addDialogView(viewFactory, rootView.getChildCount());
		dialogView.setAlpha(0f);
		dialogView.animate().alpha(1f).setDuration(100).start();
		visibleViews.add(new Pair<>(viewFactory, dialogView));
		switchBackground(false);
	}

	public void addAll(List<T> viewFactories) {
		if (!viewFactories.isEmpty()) {
			int hiddenTo = viewFactories.size() - VISIBLE_COUNT;
			if (hiddenTo > 0) {
				for (Pair<T, DialogView> pair : visibleViews) {
					pair.first.destroyView(pair.second.getContent(), false);
					hiddenViews.add(pair.first);
					rootView.removeView(pair.second.getContainer());
				}
				hiddenViews.addAll(viewFactories.subList(0, hiddenTo));
			}
			for (T viewFactory : viewFactories.subList(Math.max(0, hiddenTo), viewFactories.size())) {
				push(viewFactory);
			}
		}
	}

	public T pop() {
		return popInternal();
	}

	public void clear() {
		while (!visibleViews.isEmpty()) {
			popInternal();
		}
	}

	private void switchBackground(boolean background) {
		for (Pair<T, DialogView> pair : visibleViews) {
			pair.second.setBackground(background);
		}
	}

	private T popInternal() {
		if (hiddenViews.size() > 0) {
			int index = rootView.indexOfChild(visibleViews.getFirst().second.getContainer());
			T last = hiddenViews.removeLast();
			DialogView dialogView = addDialogView(last, index);
			dialogView.setActive(false);
			visibleViews.addFirst(new Pair<>(last, dialogView));
		}
		Pair<T, DialogView> last = visibleViews.removeLast();
		rootView.removeView(last.second.getContainer());
		if (visibleViews.isEmpty()) {
			dialog.dismiss();
			dialog = null;
			currentActionMode = null;
			ViewUtils.removeFromParent(contentView);
		} else {
			visibleViews.getLast().second.setActive(true);
		}
		last.first.destroyView(last.second.getContent(), true);
		return last.first;
	}

	private DialogView addDialogView(T viewFactory, int index) {
		DialogView dialogView = new DialogView(context, dialogBackgroundResId, dialogElevation, dialogDimAmount,
				viewFactory.createView(this), viewFactory, this::handlePopSelf);
		rootView.addView(dialogView.getContainer(), index, new SimpleLayout
				.LayoutParams(SimpleLayout.LayoutParams.MATCH_PARENT, SimpleLayout.LayoutParams.MATCH_PARENT));
		return dialogView;
	}

	private void handlePopSelf(DialogView dialogView) {
		if (!visibleViews.isEmpty() && visibleViews.getLast().second == dialogView) {
			popInternal();
		}
	}

	private static class SimpleLayout extends ViewGroup {
		public SimpleLayout(Context context) {
			super(context);
		}

		@Override
		protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
			int horizontal = getPaddingLeft() + getPaddingRight();
			int vertical = getPaddingTop() + getPaddingBottom();
			int childCount = getChildCount();
			int width = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY
					? Math.max(0, MeasureSpec.getSize(widthMeasureSpec) - horizontal) : 0;
			int height = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY
					? Math.max(0, MeasureSpec.getSize(heightMeasureSpec) - vertical) : 0;
			for (int i = 0; i < childCount; i++) {
				View child = getChildAt(i);
				LayoutParams layoutParams = child.getLayoutParams();
				child.measure(getChildMeasureSpec(widthMeasureSpec, horizontal, layoutParams.width),
						getChildMeasureSpec(heightMeasureSpec, vertical, layoutParams.height));
				width = Math.max(width, child.getMeasuredWidth());
				height = Math.max(height, child.getMeasuredHeight());
			}
			setMeasuredDimension(width + horizontal, height + vertical);
		}

		@Override
		protected void onLayout(boolean changed, int l, int t, int r, int b) {
			int left = getPaddingLeft();
			int top = getPaddingTop();
			int width = r - l - getPaddingRight() - left;
			int height = b - t - getPaddingBottom() - top;
			int childCount = getChildCount();
			for (int i = 0; i < childCount; i++) {
				View child = getChildAt(i);
				int childWidth = child.getMeasuredWidth();
				int childHeight = child.getMeasuredHeight();
				int shiftX = (width - childWidth) / 2;
				int shiftY = (height - childHeight) / 2;
				child.layout(left + shiftX, top + shiftY, left + shiftX + childWidth, top + shiftY + childHeight);
			}
		}
	}

	private static class DragLayout extends SimpleLayout implements Runnable {
		public enum Side {TOP, BOTTOM}

		public interface Callback {
			boolean isScrolled();
			boolean onProposeShift(int shift, float acceleration);
			void onFinished();
		}

		private static class LayoutData {
			public int top;
			public float multiplier;
			public int dy;
		}

		private final Callback callback;
		private final ViewDragHelper helper;

		private boolean hasWindowFocus;
		private boolean intercepted;
		private boolean accept;

		public DragLayout(Context context, Side side, Callback callback) {
			super(context);

			this.callback = callback;
			hasWindowFocus = true;
			helper = ViewDragHelper.create(this, new ViewDragHelper.Callback() {
				@Override
				public boolean tryCaptureView(@NonNull View child, int pointerId) {
					return hasWindowFocus;
				}

				@Override
				public int getViewVerticalDragRange(@NonNull View child) {
					return getHeight();
				}

				@Override
				public int clampViewPositionHorizontal(@NonNull View child, int left, int dx) {
					return child.getLeft();
				}

				@Override
				public int clampViewPositionVertical(@NonNull View child, int top, int dy) {
					int layoutTop = getChildInitialTop(child);
					boolean scrolled = intercepted || callback.isScrolled();
					switch (side) {
						case TOP: return scrolled ? Math.max(layoutTop, top) : layoutTop;
						case BOTTOM: return scrolled ? Math.min(layoutTop, top) : layoutTop;
						default: throw new IllegalStateException();
					}
				}

				@Override
				public void onViewDragStateChanged(int state) {
					if (state == ViewDragHelper.STATE_DRAGGING) {
						accept = false;
					}
				}

				@Override
				public void onViewPositionChanged(@NonNull View changedView, int left, int top, int dx, int dy) {
					int layoutTop = getChildInitialTop(changedView);
					int shift = top - layoutTop;
					int childCount = getChildCount();
					for (int i = 0; i < childCount; i++) {
						View child = getChildAt(i);
						if (child != changedView) {
							LayoutData layoutData = getLayoutData(child, false);
							if (layoutData != null) {
								int shareShift = (int) (shift * layoutData.multiplier + 0.5f);
								int childTop = child.getTop();
								int childDy = layoutData.top + shareShift - childTop;
								ViewCompat.offsetTopAndBottom(child, childDy);
							}
						}
					}
					callback.onProposeShift(Math.abs(shift), 0);
				}

				@Override
				public void onViewReleased(@NonNull View releasedChild, float xvel, float yvel) {
					int top = releasedChild.getTop();
					int layoutTop = getChildInitialTop(releasedChild);
					int shift = Math.abs(top - layoutTop);
					float velocity = top > layoutTop && yvel > 0 ? yvel : top < layoutTop && yvel < 0 ? -yvel : 0;
					float acceleration = 1f + (float) Math.sqrt(velocity / getHeight());
					if (hasWindowFocus && callback.onProposeShift(shift, acceleration)) {
						accept = true;
						int targetTop = 2 * top - layoutTop;
						int maxTop;
						int minTop;
						switch (side) {
							case TOP: {
								maxTop = getHeight();
								minTop = Math.min(targetTop, maxTop);
								break;
							}
							case BOTTOM: {
								maxTop = -getHeight();
								minTop = Math.max(targetTop, maxTop);
								break;
							}
							default: {
								throw new IllegalStateException();
							}
						}
						if (velocity > 0) {
							helper.flingCapturedView(releasedChild.getLeft(), Math.min(minTop, maxTop),
									releasedChild.getLeft(), Math.max(minTop, maxTop));
						} else {
							helper.settleCapturedViewAt(releasedChild.getLeft(), minTop);
						}
						removeCallbacks(DragLayout.this);
						postDelayed(DragLayout.this, 150);
					} else {
						helper.settleCapturedViewAt(releasedChild.getLeft(), layoutTop);
					}
					invalidate();
				}
			});
		}

		@Override
		public void onWindowFocusChanged(boolean hasWindowFocus) {
			super.onWindowFocusChanged(hasWindowFocus);
			this.hasWindowFocus = hasWindowFocus;
			if (!hasWindowFocus) {
				if (helper.getViewDragState() == ViewDragHelper.STATE_DRAGGING) {
					MotionEvent ev = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
					try {
						helper.processTouchEvent(ev);
					} finally {
						ev.recycle();
					}
				}
				helper.cancel();
			}
		}

		@Override
		public boolean onInterceptTouchEvent(MotionEvent ev) {
			if (ev.getAction() == MotionEvent.ACTION_DOWN) {
				intercepted = false;
			}
			boolean result = helper.shouldInterceptTouchEvent(ev);
			if (result) {
				if (!intercepted) {
					getParent().requestDisallowInterceptTouchEvent(true);
				}
				intercepted = true;
			}
			if (result || intercepted) {
				return result;
			}
			return super.onInterceptTouchEvent(ev);
		}

		@SuppressLint("ClickableViewAccessibility")
		@Override
		public boolean onTouchEvent(MotionEvent ev) {
			if (intercepted) {
				helper.processTouchEvent(ev);
				return true;
			}
			return super.onTouchEvent(ev);
		}

		@Override
		public void computeScroll() {
			super.computeScroll();
			if (helper.continueSettling(true)) {
				postInvalidateOnAnimation();
			} else {
				run();
			}
		}

		@Override
		public void run() {
			if (accept) {
				accept = false;
				callback.onFinished();
			}
		}

		public void resetDrag() {
			helper.abort();
		}

		private static LayoutData getLayoutData(View child, boolean create) {
			LayoutData layoutData = (LayoutData) child.getTag(R.id.tag_drag_layout_data);
			if (layoutData == null && create) {
				layoutData = new LayoutData();
				child.setTag(R.id.tag_drag_layout_data, layoutData);
			}
			return layoutData;
		}

		private int getChildInitialTop(View child) {
			LayoutData layoutData = getLayoutData(child, false);
			return layoutData != null ? layoutData.top : 0;
		}

		@Override
		protected void onLayout(boolean changed, int l, int t, int r, int b) {
			int childCount = getChildCount();
			for (int i = 0; i < childCount; i++) {
				View child = getChildAt(i);
				LayoutData layoutData = getLayoutData(child, false);
				if (layoutData != null) {
					layoutData.dy = child.getTop() - layoutData.top;
				}
			}

			super.onLayout(changed, l, t, r, b);
			for (int i = 0; i < childCount; i++) {
				View child = getChildAt(i);
				LayoutData layoutData = getLayoutData(child, true);
				layoutData.top = child.getTop();
				layoutData.multiplier = (float) (i + 1) / childCount;
				if (layoutData.dy != 0) {
					ViewCompat.offsetTopAndBottom(child, layoutData.dy);
				}
			}

			View capturedView = helper.getCapturedView();
			if (capturedView != null && indexOfChild(capturedView) < 0) {
				if (childCount > 0) {
					View child = getChildAt(childCount - 1);
					LayoutData layoutData = getLayoutData(child, false);
					helper.captureChildView(child, helper.getActivePointerId());
					if (layoutData != null) {
						callback.onProposeShift(Math.abs(layoutData.top - child.getTop()), 0);
					}
				} else {
					helper.abort();
				}
			}
		}
	}

	private static class ContentView extends DragLayout {
		public ContentView(Context context, Side side, Callback callback) {
			super(context, side, callback);
			if (C.API_LOLLIPOP) {
				setWillNotDraw(false);
			}
		}

		@Override
		public void draw(Canvas canvas) {
			super.draw(canvas);
			if (C.API_LOLLIPOP) {
				ViewUtils.drawSystemInsetsOver(this, canvas, InsetsLayout.isTargetGesture29(this));
			}
		}
	}

	private static class DialogView extends SimpleLayout {
		public interface PopSelf {
			void onRequestPopSelf(DialogView dialogView);
		}

		private static class ArrowData {
			public final Paint paint;
			public final Path path = new Path();
			public final float size;

			public final float bottomTextShift;
			public final String topText;
			public final String bottomText;
			public final float topTextWidth;
			public final float bottomTextWidth;

			public float shift;
			public float vertical;

			public ArrowData(Paint paint, float size, String topText, String bottomText) {
				this.paint = paint;
				this.size = size;
				Paint.FontMetrics metrics = new Paint.FontMetrics();
				paint.getFontMetrics(metrics);
				bottomTextShift = (metrics.descent + metrics.ascent + metrics.bottom + metrics.top) / -2f;
				this.topText = topText;
				this.bottomText = bottomText;
				topTextWidth = paint.measureText(topText);
				bottomTextWidth = paint.measureText(bottomText);
			}
		}

		private final ViewFactory<?> viewFactory;

		private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final float elevation;
		private ArrowData arrowData;

		private boolean active = false;
		private boolean background = false;
		private boolean elevated = false;

		public DialogView(Context context, int backgroundResId, float elevation, float dimAmount,
				View content, ViewFactory<?> viewFactory, PopSelf popSelf) {
			super(context);
			this.viewFactory = viewFactory;

			DragLayout container = new DragLayout(context, DragLayout.Side.BOTTOM, new DragLayout.Callback() {
				@Override
				public boolean isScrolled() {
					return isScrolledToBottom();
				}

				@Override
				public boolean onProposeShift(int shift, float acceleration) {
					return handleShift(-shift, acceleration);
				}

				@Override
				public void onFinished() {
					popSelf.onRequestPopSelf(DialogView.this);
				}
			});
			container.setClipToPadding(false);
			container.setClipChildren(false);
			container.addView(this, SimpleLayout.LayoutParams.MATCH_PARENT,
					SimpleLayout.LayoutParams.WRAP_CONTENT);
			addView(content, SimpleLayout.LayoutParams.MATCH_PARENT,
					SimpleLayout.LayoutParams.WRAP_CONTENT);
			setClickable(true);

			setBackgroundResource(backgroundResId);
			this.elevation = elevation;
			if (C.API_LOLLIPOP) {
				setBackgroundTintList(ColorStateList.valueOf(ThemeEngine.getTheme(context).card));
			}
			paint.setColor((int) (dimAmount * 0xff) << 24);
			setActive(true);
		}

		public boolean isScrolledToTop() {
			return viewFactory.isScrolledToTop(getContent());
		}

		public boolean isScrolledToBottom() {
			return viewFactory.isScrolledToBottom(getContent());
		}

		public boolean handleShift(int shift, float acceleration) {
			int height = getContainer().getHeight();
			float relativeShift = shift / (height / 8f);
			relativeShift = Math.max(-1f, Math.min(relativeShift, 1f));
			updatePath(relativeShift);
			float largeHeight = height * 2f / 3f;
			float smallHeight = height / 3f;
			// Make small views easier to close: denominator is in [2 .. 4] depending on the view height
			float denominator = (getHeight() - largeHeight) / (smallHeight - largeHeight);
			denominator = 2f * (Math.max(0f, Math.min(denominator, 1f)) + 1f);
			return acceleration * Math.abs(shift) >= height / denominator;
		}

		public View getContainer() {
			return (View) getParent();
		}

		public View getContent() {
			return getChildAt(0);
		}

		public void setActive(boolean active) {
			if (this.active != active) {
				this.active = active;
				invalidate();
			}
		}

		public void setBackground(boolean background) {
			if (this.background != background) {
				this.background = background;
				invalidate();
			}
		}

		public void setElevated(boolean elevated) {
			if (this.elevated != elevated) {
				this.elevated = elevated;
				setElevation(elevated ? elevation : 0f);
			}
		}

		@Override
		public boolean dispatchTouchEvent(MotionEvent ev) {
			return active && super.dispatchTouchEvent(ev);
		}

		private void updatePath(float shift) {
			if (shift == 0f && arrowData == null) {
				return;
			}
			ArrowData arrow;
			if (arrowData == null) {
				float density = ResourceUtils.obtainDensity(this);
				int arrowSize = (int) (40f * density + 0.5f);
				Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
				paint.setColor(0xffffffff);
				paint.setTypeface(ResourceUtils.TYPEFACE_MEDIUM);
				paint.setTextSize((int) (14f * density + 0.5f));
				paint.setStrokeWidth(2f * density);
				paint.setStrokeCap(Paint.Cap.ROUND);
				paint.setStrokeJoin(Paint.Join.ROUND);
				String topText = getContext().getString(R.string.close_all).toUpperCase(Locale.getDefault());
				String bottomText = getContext().getString(R.string.close).toUpperCase(Locale.getDefault());
				arrow = new ArrowData(paint, arrowSize, topText, bottomText);
				arrowData = arrow;
			} else {
				arrow = arrowData;
				if (arrow.shift == shift) {
					return;
				}
				arrow.path.rewind();
			}
			arrow.shift = shift;
			float absShift = Math.abs(shift);
			arrow.vertical = Math.signum(shift) * Math.max(0f, absShift - 2f / 3f) * 3f;
			arrow.path.moveTo(-absShift * arrow.size / 2f, -arrow.vertical * arrow.size / 4f);
			arrow.path.lineTo(0f, 0f);
			arrow.path.lineTo(absShift * arrow.size / 2f, -arrow.vertical * arrow.size / 4f);
			invalidate();
		}

		@Override
		public void draw(Canvas canvas) {
			super.draw(canvas);

			if (!active && !background) {
				Drawable background = getBackground();
				while (background instanceof InsetDrawable) {
					background = ((InsetDrawable) background).getDrawable();
				}
				float radius = 0f;
				if (background instanceof GradientDrawable) {
					GradientDrawable roundRectDrawable = (GradientDrawable) background;
					radius = GraphicsUtils.getCornerRadius(roundRectDrawable);
				}
				if (radius > 0f) {
					canvas.drawRoundRect(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(),
							getHeight() - getPaddingBottom(), radius, radius, paint);
				} else {
					canvas.drawRect(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(),
							getHeight() - getPaddingBottom(), paint);
				}
			}

			ArrowData arrow = this.arrowData;
			if (arrow != null && arrow.shift != 0f) {
				canvas.save();
				canvas.translate(getWidth() / 2f, arrow.shift >= 0 ? 0 : getHeight());
				arrow.paint.setStyle(Paint.Style.STROKE);
				canvas.drawPath(arrow.path, arrow.paint);
				float absVertical = Math.abs(arrow.vertical);
				if (absVertical > 0) {
					arrow.paint.setStyle(Paint.Style.FILL);
					String text = arrow.shift >= 0 ? arrow.topText : arrow.bottomText;
					float textWidth = arrow.shift >= 0 ? arrow.topTextWidth : arrow.bottomTextWidth;
					float arrowDy = -arrow.vertical * arrow.size / 4f;
					float paddingDy = arrow.shift >= 0 ? -getPaddingTop() : getPaddingBottom();
					float textDy = arrow.shift >= 0 ? 0 : arrow.bottomTextShift;
					canvas.translate(-textWidth / 2f, arrowDy + paddingDy + textDy);
					int oldAlpha = arrow.paint.getAlpha();
					arrow.paint.setAlpha((int) (0xff * absVertical));
					canvas.drawText(text, 0, 0, arrow.paint);
					arrow.paint.setAlpha(oldAlpha);
				}
				canvas.restore();
			}
		}
	}

	public interface ViewFactory<T extends ViewFactory<T>> {
		View createView(DialogStack<T> dialogStack);
		default void destroyView(View view, boolean remove) {}

		default boolean isScrolledToTop(View view) {
			return true;
		}

		default boolean isScrolledToBottom(View view) {
			return true;
		}
	}

	public Iterable<View> getVisibleViews() {
		return () -> {
			Iterator<Pair<T, DialogView>> iterator = visibleViews.iterator();
			return new Iterator<View>() {
				@Override
				public boolean hasNext() {
					return iterator.hasNext();
				}

				@Override
				public View next() {
					Pair<T, DialogView> pair = iterator.next();
					return pair.second.getContent();
				}
			};
		};
	}

	@NonNull
	@Override
	public Iterator<Pair<T, View>> iterator() {
		Iterator<T> hidden = hiddenViews.iterator();
		Iterator<Pair<T, DialogView>> visible = visibleViews.iterator();
		return new Iterator<Pair<T, View>>() {
			@Override
			public boolean hasNext() {
				return hidden.hasNext() || visible.hasNext();
			}

			@Override
			public Pair<T, View> next() {
				if (hidden.hasNext()) {
					return new Pair<>(hidden.next(), null);
				} else if (visible.hasNext()) {
					Pair<T, DialogView> pair = visible.next();
					return new Pair<>(pair.first, pair.second.getContent());
				} else {
					return null;
				}
			}
		};
	}
}
