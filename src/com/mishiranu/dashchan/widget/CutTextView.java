package com.mishiranu.dashchan.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.text.Layout;
import android.util.AttributeSet;
import android.widget.TextView;
import com.mishiranu.dashchan.text.style.OverlineSpan;

// Allows to cut lines that don't fit to view's height.
public class CutTextView extends TextView {
	public CutTextView(Context context, AttributeSet attrs) {
		super(context, attrs);
		ThemeEngine.applyStyle(this);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);

		int removeHeight = getMeasuredHeight() - getPaddingTop() - getPaddingBottom();
		Layout layout = getLayout();
		int count = layout.getLineCount();
		for (int i = 0; i < count; i++) {
			int lineHeight = layout.getLineTop(i + 1) - layout.getLineTop(i);
			if (removeHeight >= lineHeight) {
				removeHeight -= lineHeight;
			} else {
				break;
			}
		}
		if (removeHeight > 0) {
			setMeasuredDimension(getMeasuredWidth(), getMeasuredHeight() - removeHeight);
		}
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		OverlineSpan.draw(this, canvas);
	}
}
