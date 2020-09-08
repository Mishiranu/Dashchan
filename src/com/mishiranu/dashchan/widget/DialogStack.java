package com.mishiranu.dashchan.widget;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.util.Pair;
import android.view.ActionMode;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
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

public class DialogStack<T extends DialogStack.ViewFactory<T>> implements Iterable<Pair<T, View>> {
	private final Context context;
	private final Context styledContext;
	private final FrameLayout rootView;
	private final float dimAmount;

	private WeakReference<ActionMode> currentActionMode;

	public DialogStack(Context context) {
		this.context = context;
		styledContext = new ContextThemeWrapper(context, ResourceUtils.getResourceId(context,
				android.R.attr.dialogTheme, 0));
		ThemeEngine.addOnDialogCreatedListener(context, () -> switchBackground(true));
		rootView = new FrameLayout(context);
		rootView.setOnClickListener(v -> {
			if (!visibleViews.isEmpty()) {
				popInternal();
			}
		});
		rootView.setFitsSystemWindows(true);
		TypedArray typedArray = styledContext.obtainStyledAttributes(new int[] {android.R.attr.backgroundDimAmount});
		dimAmount = typedArray.getFloat(0, 0.6f);
		typedArray.recycle();
	}

	private final KeyBackHandler keyBackHandler = C.API_MARSHMALLOW ? new MarshmallowKeyBackHandler()
			: new RegularKeyBackHandler();

	private interface KeyBackHandler {
		public boolean onBackKey(KeyEvent event, boolean allowPop);
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

	private static final int VISIBLE_COUNT = 10;

	private final LinkedList<T> hiddenViews = new LinkedList<>();
	private final LinkedList<Pair<T, DialogView>> visibleViews = new LinkedList<>();
	private Dialog dialog;

	public void push(T viewFactory) {
		if (dialog == null) {
			Dialog dialog = new Dialog(C.API_LOLLIPOP ? context
					: new ContextThemeWrapper(context, R.style.Theme_Gallery)) {
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
			dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
			dialog.setContentView(rootView);
			dialog.setCancelable(false);
			dialog.setOnKeyListener((d, keyCode, event) -> {
				if (keyCode == KeyEvent.KEYCODE_BACK) {
					return keyBackHandler.onBackKey(event, !visibleViews.isEmpty());
				}
				return false;
			});
			Window window = dialog.getWindow();
			WindowManager.LayoutParams layoutParams = window.getAttributes();
			layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
			layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
			// Activity theme might be opaque
			layoutParams.format = PixelFormat.TRANSLUCENT;
			layoutParams.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
			layoutParams.dimAmount = dimAmount;
			if (C.API_LOLLIPOP) {
				layoutParams.flags |= WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
			}
			layoutParams.setTitle(context.getPackageName() + "/" + getClass().getName()); // For hierarchy view
			View decorView = window.getDecorView();
			decorView.setBackground(null);
			decorView.setPadding(0, 0, 0, 0);
			decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
					View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
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
				first.first.destroyView(first.second.getChildAt(0));
				hiddenViews.add(first.first);
				rootView.removeView(first.second);
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
					pair.first.destroyView(pair.second.getChildAt(0));
					hiddenViews.add(pair.first);
					rootView.removeView(pair.second);
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
			int index = rootView.indexOfChild(visibleViews.getFirst().second);
			T last = hiddenViews.removeLast();
			DialogView dialogView = addDialogView(last, index);
			dialogView.setActive(false);
			visibleViews.addFirst(new Pair<>(last, dialogView));
		}
		Pair<T, DialogView> last = visibleViews.removeLast();
		rootView.removeView(last.second);
		if (visibleViews.isEmpty()) {
			dialog.dismiss();
			dialog = null;
			currentActionMode = null;
			ViewUtils.removeFromParent(rootView);
		} else {
			visibleViews.getLast().second.setActive(true);
		}
		last.first.destroyView(last.second.getChildAt(0));
		return last.first;
	}

	private DialogView addDialogView(T viewFactory, int index) {
		DialogView dialogView = new DialogView(context, styledContext, dimAmount);
		dialogView.addView(viewFactory.createView(this), FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.WRAP_CONTENT);
		rootView.addView(dialogView, index, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
		return dialogView;
	}

	private static class DialogView extends FrameLayout {
		private final Paint paint = new Paint();
		private final float shadowSize;

		private boolean active = false;
		private boolean background = false;

		public DialogView(Context context, Context styledContext, float dimAmount) {
			super(context);

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

		public void setActive(boolean active) {
			if (this.active != active) {
				this.active = active;
				if (C.API_LOLLIPOP && shadowSize > 0f) {
					setElevation(active ? shadowSize : 0f);
				}
				invalidate();
			}
		}

		public void setBackground(boolean background) {
			if (this.background != background) {
				this.background = background;
				invalidate();
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
					return pair.second.getChildAt(0);
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
					return new Pair<>(pair.first, pair.second.getChildAt(0));
				} else {
					return null;
				}
			}
		};
	}
}
