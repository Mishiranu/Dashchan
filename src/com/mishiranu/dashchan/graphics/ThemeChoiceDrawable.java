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

package com.mishiranu.dashchan.graphics;

import android.annotation.TargetApi;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;

public class ThemeChoiceDrawable extends Drawable {
	private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final RectF mRectF = new RectF();

	private final int mBackground;
	private final int mPrimary;
	private final int mAccent;

	public ThemeChoiceDrawable(int colorBackground, int colorPrimary, int colorAccent) {
		mBackground = colorBackground;
		mPrimary = colorPrimary;
		mAccent = colorAccent;
	}

	@Override
	public void draw(Canvas canvas) {
		Rect bounds = getBounds();
		int radius = Math.min(bounds.width(), bounds.height()) / 2;
		int cx = bounds.centerX();
		int cy = bounds.centerY();
		Paint paint = mPaint;
		paint.setColor(mBackground);
		canvas.drawCircle(cx, cy, radius * 1f, paint);
		if (mAccent != mPrimary && mAccent != Color.TRANSPARENT) {
			RectF rectF = mRectF;
			applyRectRadius(rectF, cx, cy, radius * 0.8f);
			paint.setColor(mAccent);
			canvas.drawArc(rectF, -20, 130, true, paint);
			paint.setColor(mBackground);
			canvas.drawCircle(cx, cy, radius * 0.7f, paint);
			paint.setColor(mPrimary);
			canvas.drawCircle(cx, cy, radius * 0.65f, paint);
			canvas.drawArc(rectF, 114, 222, true, paint);
		} else {
			paint.setColor(mPrimary);
			canvas.drawCircle(cx, cy, radius * 0.8f, paint);
		}
	}

	private static void applyRectRadius(RectF rectF, int cx, int cy, float radius) {
		rectF.set(cx - radius, cy - radius, cx + radius, cy + radius);
	}

	@Override
	public int getOpacity() {
		return PixelFormat.TRANSLUCENT;
	}

	@Override
	public void setAlpha(int alpha) {}

	@Override
	public void setColorFilter(ColorFilter cf) {}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	@Override
	public void getOutline(Outline outline) {
		Rect bounds = getBounds();
		int radius = (int) ((Math.min(bounds.width(), bounds.height()) / 2) * 0.95f);
		int cx = bounds.centerX();
		int cy = bounds.centerY();
		outline.setOval(cx - radius, cy - radius, cx + radius, cy + radius);
	}
}