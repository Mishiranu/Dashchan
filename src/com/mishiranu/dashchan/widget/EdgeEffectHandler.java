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

import java.lang.reflect.Field;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.widget.AbsListView;
import android.widget.EdgeEffect;
import com.mishiranu.dashchan.C;

public class EdgeEffectHandler {
	public interface Shift {
		public int getEdgeEffectShift(boolean top);
	}

	private int shiftTop = 0;
	private int shiftBottom = 0;

	private class InternalShift implements Shift {
		@Override
		public int getEdgeEffectShift(boolean top) {
			return top ? shiftTop : shiftBottom;
		}
	}

	private static class ControlledEdgeEffect extends EdgeEffect {
		private final Shift shift;
		private final boolean top;

		public ControlledEdgeEffect(Context context, Shift shift, boolean top) {
			super(context);
			this.shift = shift;
			this.top = top;
		}

		private boolean pullable = true;

		public void setPullable(boolean pullable) {
			this.pullable = pullable;
		}

		@Override
		public void onPull(float deltaDistance) {
			if (pullable) {
				super.onPull(deltaDistance);
			}
		}

		@TargetApi(Build.VERSION_CODES.LOLLIPOP)
		@Override
		public void onPull(float deltaDistance, float displacement) {
			if (pullable) {
				super.onPull(deltaDistance, displacement);
			}
		}

		private int width;

		@Override
		public void setSize(int width, int height) {
			super.setSize(width, height);
			this.width = width;
		}

		@TargetApi(Build.VERSION_CODES.LOLLIPOP)
		@Override
		public int getMaxHeight() {
			return super.getMaxHeight() + shift.getEdgeEffectShift(top);
		}

		private final Paint shiftPaint = new Paint();

		@TargetApi(Build.VERSION_CODES.LOLLIPOP)
		@Override
		public boolean draw(Canvas canvas) {
			if (pullable) {
				int shift = this.shift.getEdgeEffectShift(top);
				boolean needShift = shift != 0;
				if (needShift) {
					if (C.API_LOLLIPOP) {
						int color = getColor();
						Paint paint = shiftPaint;
						paint.setColor(color);
						canvas.drawRect(0, 0, width, shift, paint);
					}
					canvas.save();
					canvas.translate(0, shift);
				}
				boolean result = super.draw(canvas);
				if (needShift) {
					canvas.restore();
				}
				return result;
			} else {
				return false;
			}
		}
	}

	private final ControlledEdgeEffect topEdgeEffect;
	private final ControlledEdgeEffect bottomEdgeEffect;

	private EdgeEffectHandler(Context context, Shift shift) {
		if (shift == null) {
			shift = new InternalShift();
		}
		topEdgeEffect = new ControlledEdgeEffect(context, shift, true);
		bottomEdgeEffect = new ControlledEdgeEffect(context, shift, false);
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public void setColor(int color) {
		if (C.API_LOLLIPOP) {
			topEdgeEffect.setColor(color);
			bottomEdgeEffect.setColor(color);
		}
	}

	public void setShift(boolean top, int shift) {
		if (top) {
			shiftTop = shift;
		} else {
			shiftBottom = shift;
		}
	}

	public void setPullable(boolean top, boolean pullable) {
		(top ? topEdgeEffect : bottomEdgeEffect).setPullable(pullable);
	}

	public void finish(boolean top) {
		(top ? topEdgeEffect : bottomEdgeEffect).finish();
	}

	private static final Field EDGE_GLOW_TOP_FIELD, EDGE_GLOW_BOTTOM_FIELD;

	static {
		Field edgeGlowTopField, edgeGlowBottomField;
		try {
			edgeGlowTopField = AbsListView.class.getDeclaredField("mEdgeGlowTop");
			edgeGlowTopField.setAccessible(true);
			edgeGlowBottomField = AbsListView.class.getDeclaredField("mEdgeGlowBottom");
			edgeGlowBottomField.setAccessible(true);
		} catch (Exception e) {
			edgeGlowTopField = null;
			edgeGlowBottomField = null;
		}
		EDGE_GLOW_TOP_FIELD = edgeGlowTopField;
		EDGE_GLOW_BOTTOM_FIELD = edgeGlowBottomField;
	}

	public static EdgeEffectHandler bind(AbsListView listView, Shift shift) {
		if (EDGE_GLOW_TOP_FIELD != null && EDGE_GLOW_BOTTOM_FIELD != null) {
			try {
				Object edgeEffect = EDGE_GLOW_TOP_FIELD.get(listView);
				if (edgeEffect != null && !(edgeEffect instanceof ControlledEdgeEffect)) {
					EdgeEffectHandler handler = new EdgeEffectHandler(listView.getContext(), shift);
					EDGE_GLOW_TOP_FIELD.set(listView, handler.topEdgeEffect);
					EDGE_GLOW_BOTTOM_FIELD.set(listView, handler.bottomEdgeEffect);
					return handler;
				}
			} catch (Exception e) {
				// Ugnore
			}
		}
		return null;
	}
}