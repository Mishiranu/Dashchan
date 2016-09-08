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
import android.text.Layout;
import android.text.Spanned;
import android.widget.TextView;

public class OverlineSpan
{
	private static final Paint PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
	
	public static void draw(TextView textView, Canvas canvas)
	{
		Layout layout = textView.getLayout();
		if (layout != null)
		{
			CharSequence text = textView.getText();
			if (text instanceof Spanned)
			{
				Spanned spanned = (Spanned) text;
				OverlineSpan[] spans = spanned.getSpans(0, spanned.length(), OverlineSpan.class);
				if (spans != null && spans.length > 0)
				{
					int paddingTop = textView.getTotalPaddingTop();
					int paddingLeft = textView.getPaddingLeft();
					int shift = (int) (textView.getTextSize() * 8f / 9f);
					float thickness = textView.getTextSize() / 15f - 0.25f;
					int color = textView.getCurrentTextColor();
					PAINT.setColor(color);
					PAINT.setStrokeWidth(thickness);
					for (OverlineSpan span : spans)
					{
						int start = spanned.getSpanStart(span);
						int end = spanned.getSpanEnd(span);
						int lineStart = layout.getLineForOffset(start);
						int lineEnd = layout.getLineForOffset(end);
						for (int i = lineStart; i <= lineEnd; i++)
						{
							float left = i == lineStart ? layout.getPrimaryHorizontal(start) : layout.getLineLeft(i);
							float right = i == lineEnd ? layout.getPrimaryHorizontal(end) : layout.getLineRight(i);
							float top = layout.getLineBaseline(i) - shift + 0.5f;
							canvas.drawLine(paddingLeft + left, paddingTop + top, paddingLeft + right,
									paddingTop + top, PAINT);
						}
					}
				}
			}
		}
	}
}