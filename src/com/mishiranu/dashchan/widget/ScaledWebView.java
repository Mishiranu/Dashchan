package com.mishiranu.dashchan.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.webkit.WebView;

@SuppressLint("ViewConstructor")
public class ScaledWebView extends WebView {
	private float nativeScale;
	private float scale;

	public ScaledWebView(Context context, float minScale, float extraInitialScale) {
		super(context);
		int nativeScaleInt = (int) Math.max(25, Math.min(minScale * extraInitialScale * 100, 300));
		super.setInitialScale(nativeScaleInt);
		nativeScale = nativeScaleInt / 100f;
		scale = minScale;
	}

	private float getRelativeScale() {
		return scale / nativeScale;
	}

	@Override
	public void setInitialScale(int scaleInPercent) {
		throw new UnsupportedOperationException();
	}

	public void setScale(float scale) {
		if (this.scale != scale) {
			this.scale = scale;
			invalidate();
		}
	}

	public void notifyClientScaleChanged(float newScale) {
		if ((int) (1000 * nativeScale) != (int) (1000 * newScale)) {
			nativeScale = newScale;
			invalidate();
		}
	}

	@Override
	protected void onDraw(Canvas canvas) {
		float scale = getRelativeScale();
		canvas.save();
		canvas.scale(scale, scale);
		super.onDraw(canvas);
		canvas.restore();
	}

	private MotionEvent createScaledMotionEvent(MotionEvent event) {
		float scale = getRelativeScale();
		return MotionEvent.obtain(event.getDownTime(), event.getEventTime(), event.getAction(),
				event.getX() / scale, event.getY() / scale, event.getPressure(), event.getSize(),
				event.getMetaState(), event.getXPrecision(), event.getYPrecision(),
				event.getDeviceId(), event.getEdgeFlags());
	}

	@Override
	public boolean onHoverEvent(MotionEvent event) {
		MotionEvent scaledEvent = createScaledMotionEvent(event);
		try {
			return super.onHoverEvent(scaledEvent);
		} finally {
			scaledEvent.recycle();
		}
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		MotionEvent scaledEvent = createScaledMotionEvent(event);
		try {
			return super.onTouchEvent(scaledEvent);
		} finally {
			scaledEvent.recycle();
		}
	}

	@Override
	public boolean onGenericMotionEvent(MotionEvent event) {
		MotionEvent scaledEvent = createScaledMotionEvent(event);
		try {
			return super.onGenericMotionEvent(scaledEvent);
		} finally {
			scaledEvent.recycle();
		}
	}

	@Override
	public boolean onTrackballEvent(MotionEvent event) {
		MotionEvent scaledEvent = createScaledMotionEvent(event);
		try {
			return super.onTrackballEvent(scaledEvent);
		} finally {
			scaledEvent.recycle();
		}
	}
}
