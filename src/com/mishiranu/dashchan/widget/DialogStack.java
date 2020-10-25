package com.mishiranu.dashchan.widget;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
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
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
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

public class DialogStack<T extends DialogStack.ViewFactory<T>> implements Iterable<Pair<T, View>> {
	private static final int VISIBLE_COUNT = 10;

	private final Context context;
	private final Context styledContext;
	private final View contentView;
	private final SimpleLayout rootView;
	private final int dialogAnimations;
	private final float dialogDimAmount;

	private WeakReference<ActionMode> currentActionMode;

	public DialogStack(Context context) {
		this.context = context;
		styledContext = new ContextThemeWrapper(context, ResourceUtils.getResourceId(context,
				android.R.attr.dialogTheme, 0));
		ThemeEngine.addOnDialogCreatedListener(context, () -> switchBackground(true));
		WindowControlFrameLayout contentView = new WindowControlFrameLayout(context);
		this.contentView = contentView;
		rootView = new SimpleLayout(context);
		contentView.addView(rootView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
		contentView.setOnApplyWindowPaddingsListener((view, rect, imeRect30) -> {
			rect = new Rect(rect);
			rect.bottom = Math.max(rect.bottom, imeRect30.bottom);
			rootView.setPadding(rect.left, rect.top, rect.right, rect.bottom);
		});
		rootView.setOnClickListener(v -> {
			if (!visibleViews.isEmpty()) {
				popInternal();
			}
		});
		int[] attrs = {android.R.attr.windowAnimationStyle, android.R.attr.backgroundDimAmount};
		TypedArray typedArray = styledContext.obtainStyledAttributes(attrs);
		try {
			dialogAnimations = typedArray.getResourceId(0, 0);
			dialogDimAmount = typedArray.getFloat(1, 0.6f);
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

	public void push(T viewFactory) {
		if (dialog == null) {
			Dialog dialog = new Dialog(context, ResourceUtils.getResourceId(context, R.attr.overlayTheme, 0)) {
				@Override
				public void onWindowFocusChanged(boolean hasFocus) {
					super.onWindowFocusChanged(hasFocus);
					if (hasFocus) {
						switchBackground(false);
					}
				}

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
				first.first.destroyView(first.second.getContent());
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
					pair.first.destroyView(pair.second.getContent());
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

	public void switchBackground(boolean background) {
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
		last.first.destroyView(last.second.getContent());
		return last.first;
	}

	private DialogView addDialogView(T viewFactory, int index) {
		DialogView dialogView = new DialogView(context, styledContext, dialogDimAmount, viewFactory.createView(this));
		rootView.addView(dialogView.getContainer(), index, new SimpleLayout
				.LayoutParams(SimpleLayout.LayoutParams.MATCH_PARENT, SimpleLayout.LayoutParams.MATCH_PARENT));
		return dialogView;
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

	private static class DialogView extends SimpleLayout {
		private final Paint paint = new Paint();
		private final float shadowSize;

		private boolean active = false;
		private boolean background = false;
		private boolean elevated = false;

		public DialogView(Context context, Context styledContext, float dimAmount, View content) {
			super(context);

			SimpleLayout container = new SimpleLayout(context);
			container.addView(this, SimpleLayout.LayoutParams.MATCH_PARENT,
					SimpleLayout.LayoutParams.WRAP_CONTENT);
			addView(content, SimpleLayout.LayoutParams.MATCH_PARENT,
					SimpleLayout.LayoutParams.WRAP_CONTENT);

			int[] attrs = {android.R.attr.windowBackground, android.R.attr.windowElevation};
			TypedArray typedArray = styledContext.obtainStyledAttributes(attrs);
			setBackground(typedArray.getDrawable(0));
			shadowSize = C.API_LOLLIPOP ? typedArray.getDimension(1, 0f) : 0f;
			typedArray.recycle();
			if (C.API_LOLLIPOP) {
				setBackgroundTintList(ColorStateList.valueOf(ThemeEngine.getTheme(context).card));
			}
			paint.setColor((int) (dimAmount * 0xff) << 24);
			setActive(true);
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
				setElevation(elevated ? shadowSize : 0f);
			}
		}

		@Override
		public boolean onInterceptTouchEvent(MotionEvent ev) {
			return !active || super.onInterceptTouchEvent(ev);
		}

		@SuppressLint("ClickableViewAccessibility")
		@Override
		public boolean onTouchEvent(MotionEvent event) {
			return active || super.onTouchEvent(event);
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
		}
	}

	public interface ViewFactory<T extends ViewFactory<T>> {
		View createView(DialogStack<T> dialogStack);
		default void destroyView(View view) {}
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
