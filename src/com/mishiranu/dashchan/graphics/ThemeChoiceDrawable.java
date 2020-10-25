package com.mishiranu.dashchan.graphics;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

public class ThemeChoiceDrawable extends BaseDrawable {
	private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final RectF rectF = new RectF();

	private final int background;
	private final int primary;
	private final int accent;

	public ThemeChoiceDrawable(int colorBackground, int colorPrimary, int colorAccent) {
		background = colorBackground;
		primary = colorPrimary;
		accent = colorAccent;
	}

	@Override
	public void draw(@NonNull Canvas canvas) {
		Rect bounds = getBounds();
		int radius = Math.min(bounds.width(), bounds.height()) / 2;
		int cx = bounds.centerX();
		int cy = bounds.centerY();
		Paint paint = this.paint;
		if (accent != primary && accent != Color.TRANSPARENT) {
			RectF rectF = this.rectF;
			rectF.set(cx - radius, cy - radius, cx + radius, cy + radius);
			paint.setColor(primary);
			canvas.drawArc(rectF, 135f, 180f, true, paint);
			paint.setColor(accent);
			canvas.drawArc(rectF, -45f, 180f, true, paint);
		} else {
			paint.setColor(primary);
			canvas.drawCircle(cx, cy, radius * 1f, paint);
		}
		paint.setColor(background);
		canvas.drawCircle(cx, cy, radius * 0.5f, paint);
	}

	@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
	@Override
	public void getOutline(Outline outline) {
		Rect bounds = getBounds();
		int radius = (int) ((Math.min(bounds.width(), bounds.height()) / 2) * 0.95f);
		int cx = bounds.centerX();
		int cy = bounds.centerY();
		outline.setOval(cx - radius, cy - radius, cx + radius, cy + radius);
	}
}
