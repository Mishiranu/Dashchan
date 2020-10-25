package com.mishiranu.dashchan.graphics;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import androidx.annotation.NonNull;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.ResourceUtils;

public class TransparentTileDrawable extends BaseDrawable {
	private static final int COLOR_MIN = 0xe0;
	private static final int COLOR_MAX = 0xf0;

	private final Paint paint;

	public TransparentTileDrawable(Context context, boolean large) {
		paint = new Paint();
		float density = ResourceUtils.obtainDensity(context);
		Bitmap bitmap = GraphicsUtils.generateNoise(large ? 80 : 40, (int) density, COLOR_MIN << 24 | 0x00ffffff,
				COLOR_MAX << 24 | 0x00ffffff);
		paint.setShader(new BitmapShader(bitmap, BitmapShader.TileMode.REPEAT, BitmapShader.TileMode.REPEAT));
	}

	@Override
	public void draw(@NonNull Canvas canvas) {
		canvas.drawRect(getBounds(), paint);
	}

	@Override
	public int getAlpha() {
		return paint.getAlpha();
	}

	@Override
	public void setAlpha(int alpha) {
		paint.setAlpha(alpha);
	}
}
