package com.mishiranu.dashchan.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.text.Layout;
import android.util.AttributeSet;
import android.widget.TextView;
import com.mishiranu.dashchan.text.style.OverlineSpan;

// Allows to cut lines that doesn't fit to view's height.
public class CutTextView extends TextView {
	public CutTextView(Context context, AttributeSet attrs) {
		super(context, attrs);
		ThemeEngine.applyStyle(this);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		int height = getHeight();
		Layout layout = getLayout();
		int maxHeight = 0;
		for (int i = 0; i < layout.getLineCount(); i++) {
			int bottom = layout.getLineBottom(i);
			if (bottom > height) {
				maxHeight = layout.getLineTop(i);
				break;
			}
		}
		if (maxHeight > 0) {
			canvas.save();
			canvas.clipRect(0, 0, getWidth(), maxHeight);
		}
		super.onDraw(canvas);
		OverlineSpan.draw(this, canvas);
		if (maxHeight > 0) {
			canvas.restore();
		}
	}
}
