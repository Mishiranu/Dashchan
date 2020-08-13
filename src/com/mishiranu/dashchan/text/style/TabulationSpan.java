package com.mishiranu.dashchan.text.style;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;
import androidx.annotation.NonNull;
import com.mishiranu.dashchan.content.AdvancedPreferences;

public class TabulationSpan extends ReplacementSpan {
	private static final int TAB_SIZE;

	static {
		int tabSize = AdvancedPreferences.getTabSize();
		if (tabSize < 1 || tabSize > 8) {
			tabSize = 8;
		}
		TAB_SIZE = tabSize;
	}

	@Override
	public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
		int from = 0;
		for (int i = start - 1; i >= 0; i--) {
			if (text.charAt(i) == '\n') {
				from = i + 1;
				break;
			}
		}
		int length = 0;
		for (int i = from; i < start; i++) {
			char c = text.charAt(i);
			if (c == '\t') {
				length = length + TAB_SIZE - length % TAB_SIZE;
			} else {
				length++;
			}
		}
		int count = TAB_SIZE - length % TAB_SIZE;
		return (int) paint.measureText("        ", 0, count);
	}

	@Override
	public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end,
			float x, int top, int y, int bottom, @NonNull Paint paint) {}
}
