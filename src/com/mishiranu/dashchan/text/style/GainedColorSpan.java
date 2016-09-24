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

package com.mishiranu.dashchan.text.style;

import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.UpdateAppearance;

import com.mishiranu.dashchan.graphics.ColorScheme;
import com.mishiranu.dashchan.util.GraphicsUtils;

public class GainedColorSpan extends CharacterStyle implements UpdateAppearance, ColorScheme.Span
{
	private final int mForegroundColor;
	private float mColorGainFactor;

	public GainedColorSpan(int foregroundColor)
	{
		mForegroundColor = foregroundColor;
	}

	@Override
	public void applyColorScheme(ColorScheme colorScheme)
	{
		if (colorScheme != null) mColorGainFactor = colorScheme.colorGainFactor;
	}

	public int getForegroundColor()
	{
		return mForegroundColor;
	}

	@Override
	public void updateDrawState(TextPaint paint)
	{
		paint.setColor(GraphicsUtils.modifyColorGain(mForegroundColor, mColorGainFactor));
	}
}