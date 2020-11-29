package com.mishiranu.dashchan.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.widget.EdgeEffect;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.util.ViewUtils;

public class EdgeEffectHandler {
	public enum Side {TOP, BOTTOM}

	public interface Shift {
		int getEdgeEffectShift(Side side);
	}

	private static class ControlledEdgeEffect extends EdgeEffect {
		private final Shift shift;
		private final Side side;

		public ControlledEdgeEffect(Context context, Shift shift, Side side) {
			super(context);

			this.shift = shift;
			this.side = side;
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

		@Override
		public void onPull(float deltaDistance, float displacement) {
			if (pullable) {
				super.onPull(deltaDistance, displacement);
			}
		}

		@Override
		public void onAbsorb(int velocity) {
			if (pullable) {
				super.onAbsorb(velocity);
			}
		}

		private int width;

		@Override
		public void setSize(int width, int height) {
			super.setSize(width, height);
			this.width = width;
		}

		@Override
		public int getMaxHeight() {
			return super.getMaxHeight() + shift.getEdgeEffectShift(side);
		}

		private final Paint shiftPaint = new Paint();

		@Override
		public boolean draw(Canvas canvas) {
			if (pullable) {
				int shift = this.shift.getEdgeEffectShift(side);
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
			shift = side -> 0;
		}
		topEdgeEffect = new ControlledEdgeEffect(context, shift, Side.TOP);
		bottomEdgeEffect = new ControlledEdgeEffect(context, shift, Side.BOTTOM);
	}

	public void setColor(int color) {
		ViewUtils.setEdgeEffectColor(topEdgeEffect, color);
		ViewUtils.setEdgeEffectColor(bottomEdgeEffect, color);
	}

	private ControlledEdgeEffect getEdgeEffect(Side side) {
		switch (side) {
			case TOP: {
				return topEdgeEffect;
			}
			case BOTTOM: {
				return bottomEdgeEffect;
			}
			default: {
				throw new IllegalArgumentException();
			}
		}
	}

	public void setPullable(Side side, boolean pullable) {
		getEdgeEffect(side).setPullable(pullable);
	}

	public void finish(Side side) {
		getEdgeEffect(side).finish();
	}

	public static EdgeEffectHandler bind(RecyclerView recyclerView, Shift shift) {
		EdgeEffectHandler handler = new EdgeEffectHandler(recyclerView.getContext(), shift);
		recyclerView.setEdgeEffectFactory(new RecyclerView.EdgeEffectFactory() {
			@NonNull
			@Override
			protected EdgeEffect createEdgeEffect(@NonNull RecyclerView view, int direction) {
				switch (direction) {
					case DIRECTION_TOP: {
						return handler.topEdgeEffect;
					}
					case DIRECTION_BOTTOM: {
						return handler.bottomEdgeEffect;
					}
					case DIRECTION_LEFT:
					case DIRECTION_RIGHT:
					default: {
						return super.createEdgeEffect(view, direction);
					}
				}
			}
		});
		return handler;
	}
}
