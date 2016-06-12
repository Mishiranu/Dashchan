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

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.view.View;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class CardViewApi21 implements CardViewImpl
{
	@SuppressWarnings("deprecation")
	@Override
	public void initialize(CardView cardView, Context context, int backgroundColor, float size)
	{
		final RoundRectDrawable backgroundDrawable = new RoundRectDrawable(backgroundColor, size);
		cardView.setBackgroundDrawable(backgroundDrawable);
		View view = (View) cardView;
		view.setClipToOutline(true);
		view.setElevation(size);
		((RoundRectDrawable) (cardView.getBackground())).setPadding(size);
		updatePadding(cardView);
	}
	
	@Override
	public void initStatic()
	{
		
	}
	
	private float getMaxElevation(CardView cardView)
	{
		return ((RoundRectDrawable) (cardView.getBackground())).getPadding();
	}
	
	@Override
	public float getMinWidth(CardView cardView)
	{
		return getRadius(cardView) * 2;
	}
	
	@Override
	public float getMinHeight(CardView cardView)
	{
		return getRadius(cardView) * 2;
	}
	
	private float getRadius(CardView cardView)
	{
		return ((RoundRectDrawable) (cardView.getBackground())).getRadius();
	}
	
	private void updatePadding(CardView cardView)
	{
		float elevation = getMaxElevation(cardView);
		final float radius = getRadius(cardView);
		int hPadding = (int) Math.ceil(RoundRectDrawableWithShadow.calculateHorizontalPadding(elevation, radius));
		int vPadding = (int) Math.ceil(RoundRectDrawableWithShadow.calculateVerticalPadding(elevation, radius));
		cardView.setPadding(hPadding, vPadding, hPadding, vPadding);
	}
	
	@Override
	public void setBackgroundColor(CardView cardView, int color)
	{
		((RoundRectDrawable) (cardView.getBackground())).setColor(color);
	}
}