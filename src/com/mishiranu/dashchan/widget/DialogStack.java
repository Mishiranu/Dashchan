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
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;

public class DialogStack implements DialogInterface.OnKeyListener, View.OnTouchListener, Iterable<View>
{
	private final Context mContext;
	private final Context mStyledContext;
	private final ExpandedScreen mExpandedScreen;
	//private final Dialog mDialog;
	private final FrameLayout mRootView;
	private final float mDimAmount;

	private Callback mCallback;
	private boolean mBackground;

	private WeakReference<ActionMode> mCurrentActionMode;

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public DialogStack(Context context, ExpandedScreen expandedScreen)
	{
		mContext = context;
		mStyledContext = new ContextThemeWrapper(context, ResourceUtils.getResourceId(context,
				android.R.attr.dialogTheme, 0));
		mExpandedScreen = expandedScreen;
		mRootView = new FrameLayout(context);
		mRootView.setOnTouchListener(this);
		TypedArray typedArray = mStyledContext.obtainStyledAttributes(new int[] {android.R.attr.backgroundDimAmount});
		mDimAmount = typedArray.getFloat(0, 0.6f);
		typedArray.recycle();
	}

	@Override
	public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event)
	{
		if (keyCode == KeyEvent.KEYCODE_BACK)
		{
			if (event.getAction() == KeyEvent.ACTION_UP)
			{
				if (!event.isLongPress() && !mVisibileViews.isEmpty()) popInternal();
				return true;
			}
			else if (event.getAction() == KeyEvent.ACTION_DOWN)
			{
				if (event.isLongPress()) clear();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean onTouch(View v, MotionEvent event)
	{
		// Sometimes I get touch event even if dialog was closed
		if (event.getAction() == MotionEvent.ACTION_DOWN && mVisibileViews.size() > 0) popInternal();
		return false;
	}

	public void setCallback(Callback callback)
	{
		mCallback = callback;
	}

	public static void bindDialogToExpandedScreen(Dialog dialog, View rootView, ExpandedScreen expandedScreen)
	{
		ViewGroup decorView = (ViewGroup) dialog.getWindow().getDecorView();
		decorView.getChildAt(0).setFitsSystemWindows(false);
		// Fix resizing dialogs when status bar in gallery becomes hidden with expanded screen enabled
		if (expandedScreen.isFullScreenLayoutEnabled())
		{
			expandedScreen.addAdditionalView(rootView, false);
			decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
					View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
			dialog.setOnDismissListener(d -> expandedScreen.removeAdditionalView(rootView));
			expandedScreen.updatePaddings();
		}
		else rootView.setFitsSystemWindows(true);
	}

	private static final int VISIBLE_COUNT = 10;

	private final LinkedList<DialogView> mHiddenViews = new LinkedList<>();
	private final LinkedList<DialogView> mVisibileViews = new LinkedList<>();
	private Dialog mDialog;

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public void push(View view)
	{
		if (mVisibileViews.isEmpty())
		{
			Dialog dialog = new Dialog(C.API_LOLLIPOP ? mContext
					: new ContextThemeWrapper(mContext, R.style.Theme_Main_Hadron))
			{
				@Override
				public void onWindowFocusChanged(boolean hasFocus)
				{
					super.onWindowFocusChanged(hasFocus);
					if (hasFocus) switchBackground(false);
				}

				@Override
				public void onActionModeStarted(ActionMode mode)
				{
					mCurrentActionMode = new WeakReference<>(mode);
					super.onActionModeStarted(mode);
				}

				@Override
				public void onActionModeFinished(ActionMode mode)
				{
					if (mCurrentActionMode != null)
					{
						if (mCurrentActionMode.get() == mode) mCurrentActionMode = null;
					}
					super.onActionModeFinished(mode);
				}
			};
			dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
			dialog.setContentView(mRootView);
			dialog.setCancelable(false);
			dialog.setOnKeyListener(this);
			Window window = dialog.getWindow();
			WindowManager.LayoutParams layoutParams = window.getAttributes();
			layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
			layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
			layoutParams.flags = WindowManager.LayoutParams.FLAG_DIM_BEHIND | WindowManager.LayoutParams
					.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;
			layoutParams.dimAmount = mDimAmount;
			if (C.API_LOLLIPOP) layoutParams.flags |= WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
			layoutParams.setTitle(mContext.getPackageName() + "/" + getClass().getName()); // For hierarchy view
			View decorView = window.getDecorView();
			decorView.setBackground(null);
			decorView.setPadding(0, 0, 0, 0);
			bindDialogToExpandedScreen(dialog, mRootView, mExpandedScreen);
			dialog.show();
			mDialog = dialog;
		}
		else
		{
			mVisibileViews.getLast().setActive(false);
			if (mVisibileViews.size() == VISIBLE_COUNT)
			{
				DialogView first = mVisibileViews.removeFirst();
				if (mCallback != null) mCallback.onHide(first.getContentView());
				mHiddenViews.add(first);
				mRootView.removeView(first);
			}
			if (mCurrentActionMode != null)
			{
				ActionMode mode = mCurrentActionMode.get();
				mCurrentActionMode = null;
				if (mode != null) mode.finish();
			}
		}
		DialogView dialogView = createDialog(view);
		mVisibileViews.add(dialogView);
		switchBackground(false);
	}

	public View pop()
	{
		DialogView dialogView = popInternal();
		return dialogView.getContentView();
	}

	public void clear()
	{
		while (!mVisibileViews.isEmpty()) popInternal();
	}

	public void switchBackground(boolean background)
	{
		if (mBackground != background)
		{
			mBackground = background;
			for (DialogView dialogParentView : mVisibileViews) dialogParentView.postInvalidate();
		}
	}

	private DialogView popInternal()
	{
		if (mHiddenViews.size() > 0)
		{
			int index = mRootView.indexOfChild(mVisibileViews.getFirst());
			DialogView last = mHiddenViews.removeLast();
			mVisibileViews.addFirst(last);
			mRootView.addView(last, index);
			if (mCallback != null) mCallback.onRestore(last.getContentView());
		}
		DialogView dialogView = mVisibileViews.removeLast();
		mRootView.removeView(dialogView);
		if (mVisibileViews.isEmpty())
		{
			mDialog.dismiss();
			mDialog = null;
			ViewUtils.removeFromParent(mRootView);
		}
		else mVisibileViews.getLast().setActive(true);
		if (mCallback != null) mCallback.onPop(dialogView.getContentView());
		return dialogView;
	}

	private static final int[] ATTRS_BACKGROUND = {android.R.attr.windowBackground};

	private DialogView createDialog(View view)
	{
		DialogView dialogView = new DialogView(mContext);
		mRootView.addView(dialogView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
		TypedArray typedArray = mStyledContext.obtainStyledAttributes(ATTRS_BACKGROUND);
		dialogView.setBackground(typedArray.getDrawable(0));
		typedArray.recycle();
		dialogView.addView(view, FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.WRAP_CONTENT);
		dialogView.setAlpha(0f);
		dialogView.animate().alpha(1f).setDuration(100).start();
		return dialogView;
	}

	private class DialogView extends FrameLayout
	{
		private final Paint mPaint = new Paint();
		private final float mShadowSize;

		private boolean mActive = false;

		@TargetApi(Build.VERSION_CODES.LOLLIPOP)
		public DialogView(Context context)
		{
			super(context);
			mPaint.setColor((int) (mDimAmount * 0xff) << 24);
			if (C.API_LOLLIPOP)
			{
				int[] attrs = {android.R.attr.windowElevation};
				TypedArray typedArray = mStyledContext.obtainStyledAttributes(attrs);
				mShadowSize = typedArray.getDimension(0, 0f);
				typedArray.recycle();
			}
			else mShadowSize = 0f;
			setActive(true);
		}

		public View getContentView()
		{
			return getChildAt(0);
		}

		@TargetApi(Build.VERSION_CODES.LOLLIPOP)
		public void setActive(boolean active)
		{
			if (mActive != active)
			{
				mActive = active;
				if (C.API_LOLLIPOP && mShadowSize > 0f) setElevation(active ? mShadowSize : 0f);
				invalidate();
			}
		}

		@Override
		public boolean onInterceptTouchEvent(MotionEvent ev)
		{
			return mActive ? super.onInterceptTouchEvent(ev) : true;
		}

		@Override
		public boolean onTouchEvent(MotionEvent event)
		{
			return mActive ? true : super.onTouchEvent(event);
		}

		@Override
		public void draw(Canvas canvas)
		{
			super.draw(canvas);
			if (!mActive && !mBackground)
			{
				canvas.drawRect(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(),
						getHeight() - getPaddingBottom(), mPaint);
			}
		}
	}

	public interface Callback
	{
		public void onPop(View view);
		public void onHide(View view);
		public void onRestore(View view);
	}

	@Override
	public Iterator<View> iterator()
	{
		return new ViewIterator();
	}

	private class ViewIterator implements Iterator<View>
	{
		private final Iterator<DialogView> mHiddenIterator = mHiddenViews.iterator();
		private final Iterator<DialogView> mVisibileIterator = mVisibileViews.iterator();

		@Override
		public boolean hasNext()
		{
			return mHiddenIterator.hasNext() || mVisibileIterator.hasNext();
		}

		@Override
		public View next()
		{
			return (mHiddenIterator.hasNext() ? mHiddenIterator.next() : mVisibileIterator.next()).getContentView();
		}

		@Override
		public void remove()
		{
			throw new UnsupportedOperationException();
		}
	}
}