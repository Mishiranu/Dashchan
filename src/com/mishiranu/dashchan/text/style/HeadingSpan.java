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
import android.text.style.TypefaceSpan;

// TypefaceSpan("sans-serif-light") + RelativeSizeSpan(SCALE)
public class HeadingSpan extends TypefaceSpan
{
	private static final float SCALE = 5f / 4f;
	
	public HeadingSpan()
	{
		super("sans-serif-light");
	}
	
	@Override
	public void updateDrawState(TextPaint paint)
	{
		super.updateDrawState(paint);
		applyScale(paint);
	}
	
	@Override
	public void updateMeasureState(TextPaint paint)
	{
		super.updateMeasureState(paint);
		applyScale(paint);
	}
	
	private void applyScale(TextPaint paint)
	{
		paint.setTextSize(paint.getTextSize() * SCALE);
	}
}