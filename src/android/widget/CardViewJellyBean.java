/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * 
 * ************************************************************************
 * 
 * Copyright 2014-2016 Fukurou Mishiranu
 */

package android.widget;

import android.content.Context;
import android.graphics.Rect;

public class CardViewJellyBean implements CardView.Implementation
{
	@SuppressWarnings("deprecation")
	@Override
	public void initialize(CardView cardView, Context context, int backgroundColor, float size)
	{
		RoundRectDrawableWithShadow background = new RoundRectDrawableWithShadow(context.getResources(),
				backgroundColor, size);
		cardView.setBackgroundDrawable(background);
		Rect shadowPadding = new Rect();
		background.getMaxShadowAndCornerPadding(shadowPadding);
		cardView.setMinimumHeight((int) Math.ceil(background.getMinHeight()));
		cardView.setMinimumWidth((int) Math.ceil(background.getMinWidth()));
		cardView.setPadding(shadowPadding.left, shadowPadding.top, shadowPadding.right, shadowPadding.bottom);
	}
	
	@Override
	public void measure(CardView cardView, int[] measureSpecs)
	{
		RoundRectDrawableWithShadow background = (RoundRectDrawableWithShadow) cardView.getBackground();
		int widthMode = CardView.MeasureSpec.getMode(measureSpecs[0]);
		switch (widthMode)
		{
			case CardView.MeasureSpec.EXACTLY:
			case CardView.MeasureSpec.AT_MOST:
			{
				int minWidth = (int) Math.ceil(background.getMinWidth());
				measureSpecs[0] = CardView.MeasureSpec.makeMeasureSpec(Math.max(minWidth,
						CardView.MeasureSpec.getSize(measureSpecs[0])), widthMode);
				break;
			}
			case CardView.MeasureSpec.UNSPECIFIED: break;
		}
		int heightMode = CardView.MeasureSpec.getMode(measureSpecs[1]);
		switch (heightMode)
		{
			case CardView.MeasureSpec.EXACTLY:
			case CardView.MeasureSpec.AT_MOST:
			{
				int minHeight = (int) Math.ceil(background.getMinHeight());
				measureSpecs[1] = CardView.MeasureSpec.makeMeasureSpec(Math.max(minHeight,
						CardView.MeasureSpec.getSize(measureSpecs[1])), heightMode);
				break;
			}
			case CardView.MeasureSpec.UNSPECIFIED: break;
		}
	}
	
	@Override
	public void setBackgroundColor(CardView cardView, int color)
	{
		((RoundRectDrawableWithShadow) cardView.getBackground()).setColor(color);
	}
}