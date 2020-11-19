package com.mishiranu.dashchan.text.style;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;
import com.mishiranu.dashchan.content.model.PostNumber;
import com.mishiranu.dashchan.graphics.ColorScheme;
import com.mishiranu.dashchan.util.FlagUtils;
import com.mishiranu.dashchan.util.ResourceUtils;

public class LinkSuffixSpan extends ReplacementSpan implements ColorScheme.Span {
	private int suffix;
	private final PostNumber postNumber;

	public static final int SUFFIX_ORIGINAL_POSTER = 0x00000001;
	public static final int SUFFIX_DIFFERENT_THREAD = 0x00000002;
	public static final int SUFFIX_USER_POST = 0x00000004;

	private int foregroundColor;

	public LinkSuffixSpan(int suffix, PostNumber postNumber) {
		this.postNumber = postNumber;
		this.suffix = suffix;
	}

	public boolean isSuffixPresent(int suffix) {
		return FlagUtils.get(this.suffix, suffix);
	}

	public void setSuffix(int suffix, boolean present) {
		this.suffix = FlagUtils.set(this.suffix, suffix, present);
	}

	public PostNumber getPostNumber() {
		return postNumber;
	}

	private String getSuffixText() {
		if (isSuffixPresent(SUFFIX_ORIGINAL_POSTER)) {
			return "OP";
		} else if (isSuffixPresent(SUFFIX_DIFFERENT_THREAD)) {
			return "DT";
		} else if (isSuffixPresent(SUFFIX_USER_POST)) {
			return "Y";
		}
		return null;
	}

	@Override
	public void applyColorScheme(ColorScheme colorScheme) {
		if (colorScheme != null) {
			foregroundColor = colorScheme.linkColor;
		}
	}

	@Override
	public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
		String suffixText = getSuffixText();
		if (suffixText == null) {
			return 0;
		}
		char after = end >= text.length() ? '\n' : text.charAt(end);
		boolean addSpace = after > ' ' && after != '.' && after != ',' && after != '!' && after != '?' && after != ')'
				&& after != ']' && after != ':' && after != ';';
		paint.setTypeface(ResourceUtils.TYPEFACE_MEDIUM);
		return (int) paint.measureText(" " + suffixText + (addSpace ? " " : ""));
	}

	@Override
	public void draw(Canvas canvas, CharSequence text, int start, int end,
			float x, int top, int y, int bottom, Paint paint) {
		String suffixText = getSuffixText();
		if (suffixText != null) {
			paint.setTypeface(ResourceUtils.TYPEFACE_MEDIUM);
			paint.setColor(foregroundColor);
			canvas.drawText(" " + suffixText, x, y, paint);
		}
	}
}
