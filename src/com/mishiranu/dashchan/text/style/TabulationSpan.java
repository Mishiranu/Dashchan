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

import com.mishiranu.dashchan.preference.AdvancedPreferences;

public class TabulationSpan extends ReplacementSpan
{
	private static final int TAB_SIZE;

	static
	{
		int tabSize = AdvancedPreferences.getTabSize();
		if (tabSize < 1 || tabSize > 8) tabSize = 8;
		TAB_SIZE = tabSize;
	}

	@Override
	public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm)
	{
		int from = 0;
		for (int i = start - 1; i >= 0; i--)
		{
			if (text.charAt(i) == '\n')
			{
				from = i + 1;
				break;
			}
		}
		int length = 0;
		for (int i = from; i < start; i++)
		{
			char c = text.charAt(i);
			if (c == '\t') length = length + TAB_SIZE - length % TAB_SIZE; else length++;
		}
		int count = TAB_SIZE - length % TAB_SIZE;
		return (int) paint.measureText("        ", 0, count);
	}

	@Override
	public void draw(Canvas canvas, CharSequence text, int start, int end,
			float x, int top, int y, int bottom, Paint paint)
	{

	}
}