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

package com.mishiranu.dashchan.util;

import java.lang.reflect.Field;

import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;

import com.mishiranu.dashchan.R;

public class ListViewUtils {
	public static View getRootViewInList(View view) {
		while (view != null) {
			ViewParent viewParent = view.getParent();
			if (!(viewParent instanceof View)) {
				return null;
			}
			View parent = (View) viewParent;
			if (parent instanceof AdapterView<?>) {
				break;
			}
			view = parent;
		}
		return view;
	}

	@SuppressWarnings("unchecked")
	public static <T> T getViewHolder(View view, Class<T> clazz) {
		while (view != null) {
			Object tag = view.getTag();
			if (tag != null && clazz.isAssignableFrom(tag.getClass())) {
				return (T) tag;
			}
			ViewParent viewParent = view.getParent();
			if (!(viewParent instanceof View)) {
				return null;
			}
			view = (View) viewParent;
		}
		return null;
	}

	public static void cancelListFling(ListView listView) {
		MotionEvent motionEvent;
		motionEvent = MotionEvent.obtain(0, SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, 0, 0, 0);
		listView.onTouchEvent(motionEvent);
		motionEvent.recycle();
		motionEvent = MotionEvent.obtain(0, SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, 0, 0, 0);
		listView.onTouchEvent(motionEvent);
		motionEvent.recycle();
	}

	public static void colorizeListThumb4(AbsListView listView) {
		final int colorDefault = ResourceUtils.getColor(listView.getContext(), R.attr.colorAccentSupport);
		final int colorPressed = GraphicsUtils.modifyColorGain(colorDefault, 4f / 3f);
		if (colorDefault != 0) try {
			Field fastScrollerField = AbsListView.class.getDeclaredField("mFastScroller");
			fastScrollerField.setAccessible(true);
			Object fastScroller = fastScrollerField.get(listView);
			ImageView thumbImage;
			Field thumbDrawableField;
			Drawable drawable;
			try {
				Field thumbImageField = fastScroller.getClass().getDeclaredField("mThumbImage");
				thumbImageField.setAccessible(true);
				thumbImage = (ImageView) thumbImageField.get(fastScroller);
				drawable = thumbImage.getDrawable();
				thumbDrawableField = null;
			} catch (Exception e) {
				thumbDrawableField = fastScroller.getClass().getDeclaredField("mThumbDrawable");
				thumbDrawableField.setAccessible(true);
				drawable = (Drawable) thumbDrawableField.get(fastScroller);
				thumbImage = null;
			}
			final int[] pressedState = {android.R.attr.state_pressed}, defaultState = {};
			drawable.setState(pressedState);
			final Drawable pressedDrawable = drawable.getCurrent();
			drawable.setState(defaultState);
			final Drawable defaultDrawable = drawable.getCurrent();
			if (defaultDrawable != pressedDrawable) {
				StateListDrawable stateListDrawable = new StateListDrawable() {
					@Override
					protected boolean onStateChange(int[] stateSet) {
						boolean result = super.onStateChange(stateSet);
						if (result) {
							setColorFilter(getCurrent() == pressedDrawable ? colorPressed
									: colorDefault, PorterDuff.Mode.SRC_IN);
						}
						return result;
					}
				};
				stateListDrawable.addState(pressedState, pressedDrawable);
				stateListDrawable.addState(defaultState, defaultDrawable);
				drawable = stateListDrawable;
				if (thumbImage != null) {
					thumbImage.setImageDrawable(drawable);
				} else {
					thumbDrawableField.set(fastScroller, drawable);
				}
			}
		} catch (Exception e) {
			// Reflective operation, ignore exception
		}
	}
}