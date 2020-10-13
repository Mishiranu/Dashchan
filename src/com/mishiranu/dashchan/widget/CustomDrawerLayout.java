package com.mishiranu.dashchan.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.customview.widget.ViewDragHelper;
import androidx.drawerlayout.widget.DrawerLayout;
import com.mishiranu.dashchan.util.ResourceUtils;
import java.lang.reflect.Field;

public class CustomDrawerLayout extends DrawerLayout {
	private final int touchSlop;
	private final int edgeSize;

	private boolean expandableFromAnyPoint = true;

	private float startX;
	private float startY;
	private boolean handle;
	private boolean ignoreTouch;

	public CustomDrawerLayout(Context context, AttributeSet attrs) {
		super(context, attrs);

		float density = ResourceUtils.obtainDensity(context);
		touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
		try {
			Field field = ViewDragHelper.class.getDeclaredField("EDGE_SIZE");
			field.setAccessible(true);
			edgeSize = (int) (field.getInt(null) * density);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void setExpandableFromAnyPoint(boolean expandableFromAnyPoint) {
		this.expandableFromAnyPoint = expandableFromAnyPoint;
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		int action = ev.getActionMasked();
		if (action == MotionEvent.ACTION_DOWN) {
			startX = ev.getX();
			startY = ev.getY();
			handle = !isDrawerOpen(GravityCompat.START) && !isDrawerOpen(GravityCompat.END);
			ignoreTouch = false;
		} else if (action == MotionEvent.ACTION_MOVE && handle) {
			float dx = ev.getX() - startX;
			float dy = ev.getY() - startY;
			if (Math.abs(dx) > touchSlop) {
				handle = false;
				if (expandableFromAnyPoint && Math.abs(dx) > Math.abs(dy) &&
						getDrawerLockMode(GravityCompat.START) == LOCK_MODE_UNLOCKED) {
					boolean rtl = ViewCompat.getLayoutDirection(this) == ViewCompat.LAYOUT_DIRECTION_RTL;
					boolean left = !rtl && dx > 0 && startX >= edgeSize;
					boolean right = rtl && dx < 0 && startX <= getWidth() - edgeSize;
					if (left || right) {
						startX = ev.getX();
						startY = ev.getY();
						ignoreTouch = true;
						// ViewDragHelper tracks pointer IDs
						MotionEvent.PointerProperties[] properties = {new MotionEvent.PointerProperties()};
						ev.getPointerProperties(0, properties[0]);
						MotionEvent.PointerCoords[] coordinates = {new MotionEvent.PointerCoords()};
						coordinates[0].x = right ? getWidth() : 0;
						coordinates[0].y = ev.getY();
						// Zero event time to disallow initial high velocity
						MotionEvent fakeDown = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN,
								1, properties, coordinates, 0, 0, 0, 0, 0, 0, 0, 0);
						try {
							super.onInterceptTouchEvent(fakeDown);
						} finally {
							fakeDown.recycle();
						}
					}
				}
			}
		}
		boolean result = super.onInterceptTouchEvent(ev);
		if (result && handle) {
			handle = false;
		}
		return result;
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		int action = ev.getActionMasked();
		if (ignoreTouch && Math.abs(ev.getX() - startX) > touchSlop) {
			ignoreTouch = false;
		}
		if (action == MotionEvent.ACTION_UP && ignoreTouch) {
			MotionEvent fakeCancel = MotionEvent.obtain(ev.getDownTime(), ev.getEventTime(),
					MotionEvent.ACTION_CANCEL, ev.getX(), ev.getY(), ev.getMetaState());
			try {
				return super.onTouchEvent(fakeCancel);
			} finally {
				fakeCancel.recycle();
			}
		}
		return super.onTouchEvent(ev);
	}
}
