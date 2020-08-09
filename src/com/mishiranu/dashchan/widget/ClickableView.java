package com.mishiranu.dashchan.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import java.lang.reflect.Method;

// Shows long click transitions. Can work as ListView parent without click listeners.
public class ClickableView extends FrameLayout implements View.OnClickListener, View.OnLongClickListener {
	private View.OnClickListener onClickListener;
	private View.OnLongClickListener onLongClickListener;

	private boolean callClickOnUp = false;

	public ClickableView(Context context) {
		super(context);
		init();
	}

	public ClickableView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	private void init() {
		setBackgroundResource(ResourceUtils.getResourceId(getContext(), android.R.attr.selectableItemBackground, 0));
		super.setOnClickListener(this);
		super.setOnLongClickListener(this);
	}

	private boolean isTap = false;

	private final Runnable tapRunnable = () -> {
		isTap = true;
		Drawable drawable = getBackground();
		if (drawable != null) {
			drawable.setState(ResourceUtils.PRESSED_STATE);
			drawable = drawable.getCurrent();
			if (drawable instanceof TransitionDrawable) {
				((TransitionDrawable) drawable).startTransition(ViewConfiguration.getLongPressTimeout());
			}
		}
	};

	private final Runnable clickRunnable = () -> onClickListener.onClick(ClickableView.this);

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (!isEnabled()) {
			return false;
		}
		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN: {
				callClickOnUp = false;
				isTap = false;
				Drawable drawable = getBackground();
				boolean listParent = getListParent() != null;
				if ((onLongClickListener != null || listParent) && drawable != null) {
					if (!(listParent && C.API_LOLLIPOP && drawable instanceof RippleDrawable)) {
						postDelayed(tapRunnable, ViewConfiguration.getTapTimeout());
					}
				}
				break;
			}
			case MotionEvent.ACTION_UP: {
				if (callClickOnUp) {
					post(clickRunnable);
					callClickOnUp = false;
				}
			}
			case MotionEvent.ACTION_CANCEL: {
				removeCallbacks(tapRunnable);
				Drawable drawable = getBackground();
				if (isTap && !C.API_LOLLIPOP && drawable != null) {
					int[] lastState = drawable.getState();
					drawable.setState(ResourceUtils.PRESSED_STATE);
					Drawable current = drawable.getCurrent();
					if (current instanceof TransitionDrawable) {
						((TransitionDrawable) current).resetTransition();
					}
					drawable.setState(lastState);
				}
				break;
			}
		}
		return super.onTouchEvent(event);
	}

	@Override
	public void setOnClickListener(OnClickListener listener) {
		onClickListener = listener;
	}

	@Override
	public void setOnLongClickListener(OnLongClickListener listener) {
		onLongClickListener = listener;
	}

	private AbsListView getListParent() {
		if (onClickListener == null && onLongClickListener == null) {
			View view = ListViewUtils.getRootViewInList(this);
			return view != null && view.getParent() instanceof AbsListView ? (AbsListView) view.getParent() : null;
		}
		return null;
	}

	private int getListPosition(AbsListView listView) {
		View view = ListViewUtils.getRootViewInList(this);
		if (view != null) {
			return listView.getPositionForView(view);
		} else {
			return AbsListView.INVALID_POSITION;
		}
	}

	private long lastClick;

	@Override
	public void onClick(View v) {
		long t = System.currentTimeMillis();
		if (t - lastClick < 200L) {
			return;
		}
		lastClick = t;
		if (onClickListener != null) {
			onClickListener.onClick(v);
		} else {
			AbsListView listView = getListParent();
			if (listView != null) {
				View view = ListViewUtils.getRootViewInList(this);
				if (view != null) {
					int position = getListPosition(listView);
					if (position >= 0) {
						listView.performItemClick(this, position, listView.getItemIdAtPosition(position));
					}
				}
			}
		}
	}

	@Override
	public boolean onLongClick(View v) {
		if (onLongClickListener != null) {
			return onLongClickListener.onLongClick(v);
		} else {
			AbsListView listView = getListParent();
			if (listView != null) {
				View view = ListViewUtils.getRootViewInList(this);
				if (view != null) {
					int position = getListPosition(listView);
					if (position >= 0) {
						try {
							return (boolean) PERFORM_LONG_PRESS.invoke(listView, this, position,
									listView.getItemIdAtPosition(position));
						} catch (Exception e) {
							// Reflective operation, ignore exception
						}
					}
				}
			} else if (onClickListener != null) {
				callClickOnUp = true;
				return true;
			}
		}
		return false;
	}

	private static final Method PERFORM_LONG_PRESS;

	static {
		try {
			PERFORM_LONG_PRESS = AbsListView.class.getDeclaredMethod("performLongPress",
					View.class, int.class, long.class);
			PERFORM_LONG_PRESS.setAccessible(true);
		} catch (Exception e) {
			throw new RuntimeException();
		}
	}
}
