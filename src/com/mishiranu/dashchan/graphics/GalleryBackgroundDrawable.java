package com.mishiranu.dashchan.graphics;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import androidx.annotation.NonNull;
import com.mishiranu.dashchan.util.AnimationUtils;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.ResourceUtils;

public class GalleryBackgroundDrawable extends BaseDrawable {
	private final View view;
	private final float centerX;
	private final float centerY;

	private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final int[] location = new int[2];

	private ValueAnimator animator;
	private int alpha = 0xff;

	public GalleryBackgroundDrawable(View view, int[] imageViewPosition, int color) {
		if (imageViewPosition != null) {
			this.view = view;
			centerX = imageViewPosition[0] + imageViewPosition[2] / 2f;
			centerY = imageViewPosition[1] + imageViewPosition[3] / 2f;
		} else {
			this.view = null;
			centerX = -1;
			centerY = -1;
		}
		float density = ResourceUtils.obtainDensity(view);
		int colorFrom = Math.max(Color.alpha(color) - 5, 0x00) << 24 | 0x00ffffff & color;
		int colorTo = Math.min(Color.alpha(color) + 5, 0xff) << 24 | 0x00ffffff & color;
		Bitmap bitmap = GraphicsUtils.generateNoise(80, (int) density, colorFrom, colorTo);
		paint.setShader(new BitmapShader(bitmap, BitmapShader.TileMode.REPEAT, BitmapShader.TileMode.REPEAT));
	}

	@Override
	public void draw(@NonNull Canvas canvas) {
		Paint paint = this.paint;
		Rect bounds = getBounds();
		float t;
		if (view != null) {
			if (animator == null) {
				animator = ValueAnimator.ofFloat(0f, 1f);
				animator.setInterpolator(AnimationUtils.ACCELERATE_INTERPOLATOR);
				animator.setDuration(300);
				animator.start();
				view.getLocationOnScreen(location);
			}
			t = (float) animator.getAnimatedValue();
		} else {
			t = 1f;
		}
		if (t >= 1f) {
			paint.setAlpha(alpha);
			canvas.drawRect(bounds, paint);
		} else {
			int width = bounds.width();
			int height = bounds.height();
			float cx = AnimationUtils.lerp(centerX - location[0], bounds.left + width / 2f, t / 2f);
			float cy = AnimationUtils.lerp(centerY - location[1], bounds.top + height / 2f, t / 2f);
			float radius = AnimationUtils.lerp(0f, (float) Math.sqrt(width * width + height * height), t);
			paint.setAlpha(((int) (0xff * t)));
			canvas.drawRect(bounds, paint);
			paint.setAlpha(((int) (0xff * (1f - t) / 2f)));
			canvas.drawCircle(cx, cy, radius, paint);
			invalidateSelf();
		}
	}

	@Override
	public int getAlpha() {
		return alpha;
	}

	@Override
	public void setAlpha(int alpha) {
		if (this.alpha != alpha) {
			this.alpha = alpha;
			invalidateSelf();
		}
	}
}
