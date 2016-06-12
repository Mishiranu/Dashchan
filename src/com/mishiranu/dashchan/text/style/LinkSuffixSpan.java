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

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;

import com.mishiranu.dashchan.graphics.ColorScheme;
import com.mishiranu.dashchan.util.FlagUtils;
import com.mishiranu.dashchan.util.GraphicsUtils;

public class LinkSuffixSpan extends ReplacementSpan implements ColorScheme.Span
{
	private int mSuffix;
	private final String mPostNumber;
	
	public static final int SUFFIX_ORIGINAL_POSTER = 0x00000001;
	public static final int SUFFIX_DIFFERENT_THREAD = 0x00000002;
	public static final int SUFFIX_USER_POST = 0x00000004;
	
	private int mForegroundColor;
	
	public LinkSuffixSpan(int suffix, String postNumber)
	{
		mPostNumber = postNumber;
		mSuffix = suffix;
	}
	
	public boolean isSuffixPresent(int suffix)
	{
		return FlagUtils.get(mSuffix, suffix);
	}
	
	public void setSuffix(int suffix, boolean present)
	{
		mSuffix = FlagUtils.set(mSuffix, suffix, present);
	}
	
	public String getPostNumber()
	{
		return mPostNumber;
	}
	
	private String getSuffixText()
	{
		if (isSuffixPresent(SUFFIX_ORIGINAL_POSTER)) return "OP";
		else if (isSuffixPresent(SUFFIX_DIFFERENT_THREAD)) return "DT";
		else if (isSuffixPresent(SUFFIX_USER_POST)) return "Y";
		return null;
	}
	
	@Override
	public void applyColorScheme(ColorScheme colorScheme)
	{
		if (colorScheme != null) mForegroundColor = colorScheme.linkColor;
	}

	@Override
	public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm)
	{
		String suffixText = getSuffixText();
		if (suffixText == null) return 0;
		char after = end >= text.length() ? '\n' : text.charAt(end);
		boolean addSpace = after > ' ' && after != '.' && after != ',' && after != '!' && after != '?' && after != ')'
				&& after != ']' && after != ']' && after != ':' && after != ';';
		paint.setTypeface(GraphicsUtils.TYPEFACE_MEDIUM);
		return (int) paint.measureText(" " + suffixText + (addSpace ? " " : ""));
	}
	
	@Override
	public void draw(Canvas canvas, CharSequence text, int start, int end,
			float x, int top, int y, int bottom, Paint paint)
	{
		String suffixText = getSuffixText();
		if (suffixText != null)
		{
			paint.setTypeface(GraphicsUtils.TYPEFACE_MEDIUM);
			paint.setColor(mForegroundColor);
			canvas.drawText(" " + suffixText, x, y, paint);
		}
	}
}