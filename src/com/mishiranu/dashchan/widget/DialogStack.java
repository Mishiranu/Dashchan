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

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedList;

import android.annotation.TargetApi;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.view.ActionMode;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;

public class DialogStack implements DialogInterface.OnKeyListener, View.OnTouchListener, Iterable<View> {
	private final Context context;
	private final Context styledContext;
	private final ExpandedScreen expandedScreen;
	private final FrameLayout rootView;
	private final float dimAmount;

	private Callback callback;
	private boolean background;

	private WeakReference<ActionMode> currentActionMode;

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public DialogStack(Context context, ExpandedScreen expandedScreen) {
		this.context = context;
		styledContext = new ContextThemeWrapper(context, ResourceUtils.getResourceId(context,
				android.R.attr.dialogTheme, 0));
		this.expandedScreen = expandedScreen;
		rootView = new FrameLayout(context);
		rootView.setOnTouchListener(this);
		TypedArray typedArray = styledContext.obtainStyledAttributes(new int[] {android.R.attr.backgroundDimAmount});
		dimAmount = typedArray.getFloat(0, 0.6f);
		typedArray.recycle();
	}

	@Override
	public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			return keyBackHandler.onBackKey(event, !visibileViews.isEmpty());
		}
		return false;
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

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		// Sometimes I get touch event even if dialog was closed
		if (event.getAction() == MotionEvent.ACTION_DOWN && visibileViews.size() > 0) {
			popInternal();
		}
		return false;
	}

	public void setCallback(Callback callback) {
		this.callback = callback;
	}

	public static void bindDialogToExpandedScreen(Dialog dialog, View rootView, ExpandedScreen expandedScreen) {
		ViewGroup decorView = (ViewGroup) dialog.getWindow().getDecorView();
		decorView.getChildAt(0).setFitsSystemWindows(false);
		// Fix resizing dialogs when status bar in gallery becomes hidden with expanded screen enabled
		if (expandedScreen.isFullScreenLayoutEnabled()) {
			expandedScreen.addAdditionalView(rootView, false);
			decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
					View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
			dialog.setOnDismissListener(d -> expandedScreen.removeAdditionalView(rootView));
			expandedScreen.updatePaddings();
		} else {
			rootView.setFitsSystemWindows(true);
		}
	}

	private static final int VISIBLE_COUNT = 10;

	private final LinkedList<DialogView> hiddenViews = new LinkedList<>();
	private final LinkedList<DialogView> visibileViews = new LinkedList<>();
	private Dialog dialog;

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public void push(View view) {
		if (visibileViews.isEmpty()) {
			Dialog dialog = new Dialog(C.API_LOLLIPOP ? context
					: new ContextThemeWrapper(context, R.style.Theme_Main_Hadron)) {
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
			dialog.setOnKeyListener(this);
			Window window = dialog.getWindow();
			WindowManager.LayoutParams layoutParams = window.getAttributes();
			layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
			layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
			layoutParams.flags = WindowManager.LayoutParams.FLAG_DIM_BEHIND | WindowManager.LayoutParams
					.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;
			layoutParams.dimAmount = dimAmount;
			if (C.API_LOLLIPOP) {
				layoutParams.flags |= WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
			}
			layoutParams.setTitle(context.getPackageName() + "/" + getClass().getName()); // For hierarchy view
			View decorView = window.getDecorView();
			decorView.setBackground(null);
			decorView.setPadding(0, 0, 0, 0);
			bindDialogToExpandedScreen(dialog, rootView, expandedScreen);
			dialog.show();
			this.dialog = dialog;
		} else {
			visibileViews.getLast().setActive(false);
			if (visibileViews.size() == VISIBLE_COUNT) {
				DialogView first = visibileViews.removeFirst();
				if (callback != null) {
					callback.onHide(first.getContentView());
				}
				hiddenViews.add(first);
				rootView.removeView(first);
			}
			if (currentActionMode != null) {
				ActionMode mode = currentActionMode.get();
				currentActionMode = null;
				if (mode != null) {
					mode.finish();
				}
			}
		}
		DialogView dialogView = createDialog(view);
		visibileViews.add(dialogView);
		switchBackground(false);
	}

	public View pop() {
		DialogView dialogView = popInternal();
		return dialogView.getContentView();
	}

	public void clear() {
		while (!visibileViews.isEmpty()) {
			popInternal();
		}
	}

	public void switchBackground(boolean background) {
		if (this.background != background) {
			this.background = background;
			for (DialogView dialogParentView : visibileViews) {
				dialogParentView.postInvalidate();
			}
		}
	}

	private DialogView popInternal() {
		if (hiddenViews.size() > 0) {
			int index = rootView.indexOfChild(visibileViews.getFirst());
			DialogView last = hiddenViews.removeLast();
			visibileViews.addFirst(last);
			rootView.addView(last, index);
			if (callback != null) {
				callback.onRestore(last.getContentView());
			}
		}
		DialogView dialogView = visibileViews.removeLast();
		rootView.removeView(dialogView);
		if (visibileViews.isEmpty()) {
			dialog.dismiss();
			dialog = null;
			ViewUtils.removeFromParent(rootView);
		} else {
			visibileViews.getLast().setActive(true);
		}
		if (callback != null) {
			callback.onPop(dialogView.getContentView());
		}
		return dialogView;
	}

	private static final int[] ATTRS_BACKGROUND = {android.R.attr.windowBackground};

	private DialogView createDialog(View view) {
		DialogView dialogView = new DialogView(context);
		rootView.addView(dialogView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
		TypedArray typedArray = styledContext.obtainStyledAttributes(ATTRS_BACKGROUND);
		dialogView.setBackground(typedArray.getDrawable(0));
		typedArray.recycle();
		dialogView.addView(view, FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.WRAP_CONTENT);
		dialogView.setAlpha(0f);
		dialogView.animate().alpha(1f).setDuration(100).start();
		return dialogView;
	}

	private class DialogView extends FrameLayout {
		private final Paint paint = new Paint();
		private final float shadowSize;

		private boolean active = false;

		@TargetApi(Build.VERSION_CODES.LOLLIPOP)
		public DialogView(Context context) {
			super(context);
			paint.setColor((int) (dimAmount * 0xff) << 24);
			if (C.API_LOLLIPOP) {
				int[] attrs = {android.R.attr.windowElevation};
				TypedArray typedArray = styledContext.obtainStyledAttributes(attrs);
				shadowSize = typedArray.getDimension(0, 0f);
				typedArray.recycle();
			} else {
				shadowSize = 0f;
			}
			setActive(true);
		}

		public View getContentView() {
			return getChildAt(0);
		}

		@TargetApi(Build.VERSION_CODES.LOLLIPOP)
		public void setActive(boolean active) {
			if (this.active != active) {
				this.active = active;
				if (C.API_LOLLIPOP && shadowSize > 0f) {
					setElevation(active ? shadowSize : 0f);
				}
				invalidate();
			}
		}

		@Override
		public boolean onInterceptTouchEvent(MotionEvent ev) {
			return active ? super.onInterceptTouchEvent(ev) : true;
		}

		@Override
		public boolean onTouchEvent(MotionEvent event) {
			return active ? true : super.onTouchEvent(event);
		}

		@Override
		public void draw(Canvas canvas) {
			super.draw(canvas);
			if (!active && !background) {
				canvas.drawRect(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(),
						getHeight() - getPaddingBottom(), paint);
			}
		}
	}

	public interface Callback {
		public void onPop(View view);
		public void onHide(View view);
		public void onRestore(View view);
	}

	@Override
	public Iterator<View> iterator() {
		return new ViewIterator();
	}

	private class ViewIterator implements Iterator<View> {
		private final Iterator<DialogView> hiddenIterator = hiddenViews.iterator();
		private final Iterator<DialogView> visibileIterator = visibileViews.iterator();

		@Override
		public boolean hasNext() {
			return hiddenIterator.hasNext() || visibileIterator.hasNext();
		}

		@Override
		public View next() {
			return (hiddenIterator.hasNext() ? hiddenIterator.next() : visibileIterator.next()).getContentView();
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}
}
