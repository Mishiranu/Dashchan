package com.mishiranu.dashchan.graphics;

import android.content.Context;
import android.graphics.Color;
import android.text.Spanned;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.widget.ThemeEngine;

public class ColorScheme {
	public ColorScheme(Context context, ThemeEngine.Theme theme) {
		this(context, theme.window, theme.spoiler, theme.link, theme.quote, theme.tripcode, theme.capcode,
				theme.colorGainFactor);
	}

	private ColorScheme(Context context, int windowBackgroundColor, int spoilerBackgroundColor,
			int linkColor, int quoteColor, int tripcodeColor, int capcodeColor, float colorGainFactor) {
		this.windowBackgroundColor = windowBackgroundColor;
		this.tripcodeColor = tripcodeColor;
		this.capcodeColor = capcodeColor;
		this.linkColor = linkColor;
		this.quoteColor = quoteColor;
		this.spoilerBackgroundColor = spoilerBackgroundColor;
		spoilerTopBackgroundColor = (Math.min((spoilerBackgroundColor >>> 24) * 2, 0xff)) << 24
				| spoilerBackgroundColor & 0x00ffffff;
		this.colorGainFactor = colorGainFactor;
		dialogBackgroundColor = ResourceUtils.getDialogBackground(context);
		clickedColor = ResourceUtils.getSystemSelectorColor(context);
		highlightTextColor = (Color.BLACK | linkColor) & 0x80ffffff;
		highlightBackgroundColor = GraphicsUtils.isLight(windowBackgroundColor) ? 0x1e000000 : 0x1effffff;
	}

	public final int windowBackgroundColor;
	public final int dialogBackgroundColor;

	public final int spoilerBackgroundColor;
	public final int spoilerTopBackgroundColor;

	public final int linkColor;
	public final int quoteColor;
	public final int clickedColor;

	public final int tripcodeColor;
	public final int capcodeColor;

	public final int highlightTextColor;
	public final int highlightBackgroundColor;

	public final float colorGainFactor;

	public interface Span {
		void applyColorScheme(ColorScheme colorScheme);
	}

	public static Span[] getSpans(CharSequence text) {
		return text instanceof Spanned ? ((Spanned) text).getSpans(0, text.length(), Span.class) : null;
	}

	public void apply(CharSequence text) {
		apply(getSpans(text));
	}

	public void apply(Span[] spans) {
		if (spans != null) {
			for (Span span : spans) {
				span.applyColorScheme(this);
			}
		}
	}
}
