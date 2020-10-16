package com.mishiranu.dashchan.text.style;

import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.ParagraphStyle;
import android.text.style.UpdateAppearance;

public class ScriptSpan extends CharacterStyle implements UpdateAppearance, ParagraphStyle {
	private final boolean superscript;

	public ScriptSpan(boolean superscript) {
		this.superscript = superscript;
	}

	public boolean isSuperscript() {
		return superscript;
	}

	@Override
	public void updateDrawState(TextPaint paint) {
		float oldSize = paint.getTextSize();
		float newSize = oldSize * 3f / 4f;
		paint.setTextSize((int) (newSize + 0.5f));
		int shift = (int) (oldSize - newSize);
		if (superscript) {
			paint.baselineShift -= shift;
		}
	}
}
