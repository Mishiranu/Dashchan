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
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.util.ResourceUtils;

public class CardView extends FrameLayout
{
	public static interface Implementation
	{
		public void initialize(CardView cardView, Context context, int backgroundColor, float size);
		public void measure(CardView cardView, int[] measureSpecs);
		public void setBackgroundColor(CardView cardView, int color);
	}
	
	private static final Implementation IMPLEMENTATION = C.API_LOLLIPOP
			? new CardViewLollipop() : new CardViewJellyBean();
	
	private final boolean mInitialized;
	
	public CardView(Context context)
	{
		this(context, null);
	}
	
	public CardView(Context context, AttributeSet attrs)
	{
		this(context, attrs, 0);
	}
	
	public CardView(Context context, AttributeSet attrs, int defStyleAttr)
	{
		super(context, attrs, defStyleAttr);
		float density = ResourceUtils.obtainDensity(context);
		float size = 1f * density + 0.5f;
		IMPLEMENTATION.initialize(this, context, mBackgroundColor, size);
		mInitialized = true;
	}
	
	private final int[] mMeasureSpecs = new int[2];
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		mMeasureSpecs[0] = widthMeasureSpec;
		mMeasureSpecs[1] = heightMeasureSpec;
		IMPLEMENTATION.measure(this, mMeasureSpecs);
		super.onMeasure(mMeasureSpecs[0], mMeasureSpecs[1]);
	}
	
	private int mBackgroundColor;
	
	private void setBackgroundColorInternal(int color)
	{
		mBackgroundColor = color;
		if (mInitialized) IMPLEMENTATION.setBackgroundColor(this, color);
	}
	
	@SuppressWarnings("deprecation")
	@Override
	@Deprecated
	public void setBackgroundDrawable(Drawable background)
	{
		if (background instanceof ColorDrawable)
		{
			int color = ((ColorDrawable) background).getColor();
			setBackgroundColorInternal(color);
			return;
		}
		super.setBackgroundDrawable(background);
	}
	
	@Override
	public void setBackgroundColor(int color)
	{
		setBackgroundColorInternal(color);
	}
	
	public int getBackgroundColor()
	{
		return mBackgroundColor;
	}
}