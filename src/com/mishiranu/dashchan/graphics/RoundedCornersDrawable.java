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

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

public class RoundedCornersDrawable extends Drawable {
	private final Path mPath = new Path();
	private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final int mRadius;

	public RoundedCornersDrawable(int radius) {
		mRadius = radius;
	}

	public RoundedCornersDrawable(int radius, int color) {
		this(radius);
		setColor(color);
	}

	public void setColor(int color) {
		mPaint.setColor(color);
	}

	@Override
	public void setBounds(int left, int top, int right, int bottom) {
		Rect bounds = getBounds();
		if (bounds.left != left || bounds.top != top || bounds.right != right || bounds.bottom != bottom) {
			Path path = mPath;
			path.rewind();
			float radius = mRadius;
			float shift = ((float) Math.sqrt(2) - 1f) * radius * 4f / 3f;
			path.moveTo(left, top);
			path.rLineTo(radius, 0);
			path.rCubicTo(-shift, 0, -radius, radius - shift, -radius, radius);
			path.close();
			path.moveTo(right, top);
			path.rLineTo(-radius, 0);
			path.rCubicTo(shift, 0, radius, radius - shift, radius, radius);
			path.close();
			path.moveTo(left, bottom);
			path.rLineTo(radius, 0);
			path.rCubicTo(-shift, 0, -radius, shift - radius, -radius, -radius);
			path.close();
			path.moveTo(right, bottom);
			path.rLineTo(-radius, 0);
			path.rCubicTo(shift, 0, radius, shift - radius, radius, -radius);
			path.close();
		}
		super.setBounds(left, top, right, bottom);
	}

	@Override
	public void draw(Canvas canvas) {
		canvas.drawPath(mPath, mPaint);
	}

	@Override
	public int getOpacity() {
		return PixelFormat.TRANSLUCENT;
	}

	@Override
	public void setAlpha(int alpha) {}

	@Override
	public void setColorFilter(ColorFilter cf) {}
}