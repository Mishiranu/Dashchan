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
import android.os.Build;
import android.util.AttributeSet;

import com.mishiranu.dashchan.util.ResourceUtils;

public class CardView extends FrameLayout
{
	private static final CardViewImpl IMPL;
	
	static
	{
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
		{
			IMPL = new CardViewApi21();
		}
		else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
		{
			IMPL = new CardViewJellybeanMr1();
		}
		else
		{
			IMPL = new CardViewEclairMr1();
		}
		IMPL.initStatic();
	}
	
	public CardView(Context context)
	{
		super(context);
		initialize(context, null, 0);
	}
	
	public CardView(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		initialize(context, attrs, 0);
	}
	
	public CardView(Context context, AttributeSet attrs, int defStyleAttr)
	{
		super(context, attrs, defStyleAttr);
		initialize(context, attrs, defStyleAttr);
	}
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		if (IMPL instanceof CardViewApi21 == false)
		{
			final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
			switch (widthMode)
			{
				case MeasureSpec.EXACTLY:
				case MeasureSpec.AT_MOST:
					final int minWidth = (int) Math.ceil(IMPL.getMinWidth(this));
					widthMeasureSpec = MeasureSpec.makeMeasureSpec(Math.max(minWidth,
							MeasureSpec.getSize(widthMeasureSpec)), widthMode);
					break;
			}
			
			final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
			switch (heightMode)
			{
				case MeasureSpec.EXACTLY:
				case MeasureSpec.AT_MOST:
					final int minHeight = (int) Math.ceil(IMPL.getMinHeight(this));
					heightMeasureSpec = MeasureSpec.makeMeasureSpec(Math.max(minHeight,
							MeasureSpec.getSize(heightMeasureSpec)), heightMode);
					break;
			}
			super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		}
		else
		{
			super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		}
	}
	
	private int mBackgroundColor;
	private boolean mInitialized;
	
	private void initialize(Context context, AttributeSet attrs, int defStyleAttr)
	{
		float density = ResourceUtils.obtainDensity(context);
		float size = 1f * density + 0.5f;
		IMPL.initialize(this, context, mBackgroundColor, size);
		mInitialized = true;
	}
	
	private void setBackgroundColorInternal(int color)
	{
		mBackgroundColor = color;
		if (mInitialized) IMPL.setBackgroundColor(this, color);
	}
	
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