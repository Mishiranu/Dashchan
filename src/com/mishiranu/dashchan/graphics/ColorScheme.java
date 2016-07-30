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

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.text.Spanned;

import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.ResourceUtils;

public class ColorScheme
{
	private static final int[] ATTRS = {android.R.attr.windowBackground, R.attr.backgroundSpoiler,
		android.R.attr.textColorLink, R.attr.colorTextQuote, R.attr.colorTextTripcode, R.attr.colorTextCapcode,
		R.attr.colorGainFactor};
	
	public ColorScheme(Context context)
	{
		TypedArray typedArray = context.obtainStyledAttributes(ATTRS);
		windowBackgroundColor = typedArray.getColor(0, 0);
		tripcodeColor = typedArray.getColor(4, 0);
		capcodeColor = typedArray.getColor(5, 0);
		linkColor = typedArray.getColor(2, 0);
		quoteColor = typedArray.getColor(3, 0);
		spoilerBackgroundColor = typedArray.getColor(1, 0);
		spoilerTopBackgroundColor = (Math.min((spoilerBackgroundColor >>> 24) * 2, 0xff)) << 24
				| spoilerBackgroundColor & 0x00ffffff;
		colorGainFactor = typedArray.getFloat(6, 1f);
		typedArray.recycle();
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
	
	public interface Span
	{
		public void applyColorScheme(ColorScheme colorScheme);
	}
	
	public static Span[] getSpans(CharSequence text)
	{
		return text instanceof Spanned ? ((Spanned) text).getSpans(0, text.length(), Span.class) : null;
	}
	
	public void apply(CharSequence text)
	{
		apply(getSpans(text));
	}
	
	public void apply(Span[] spans)
	{
		if (spans != null)
		{
			for (Span span : spans) span.applyColorScheme(this);
		}
	}
}