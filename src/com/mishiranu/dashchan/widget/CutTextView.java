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

package com.mishiranu.dashchan.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.text.Layout;
import android.util.AttributeSet;
import android.widget.TextView;

// Allows to cut lines that doesn't fit to view's height.
public class CutTextView extends TextView
{
	public CutTextView(Context context, AttributeSet attrs)
	{
		super(context, attrs);
	}
	
	@Override
	public void draw(Canvas canvas)
	{
		int height = getHeight();
		Layout layout = getLayout();
		int maxHeight = 0;
		for (int i = 0; i < layout.getLineCount(); i++)
		{
			int bottom = layout.getLineBottom(i);
			if (bottom > height)
			{
				maxHeight = layout.getLineTop(i);
				break;
			}
		}
		if (maxHeight > 0)
		{
			canvas.save();
			canvas.clipRect(0, 0, getWidth(), maxHeight);
		}
		super.draw(canvas);
		if (maxHeight > 0) canvas.restore();
	}
}