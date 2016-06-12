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
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ListView;

public class PaddedListView extends ListView implements EdgeEffectHandler.Shift
{
	private EdgeEffectHandler mEdgeEffectHandler;
	private EdgeEffectHandler.Shift mShift;
	
	public PaddedListView(Context context)
	{
		super(context);
	}
	
	public PaddedListView(Context context, AttributeSet attrs)
	{
		super(context, attrs);
	}
	
	public PaddedListView(Context context, AttributeSet attrs, int defStyleAttr)
	{
		super(context, attrs, defStyleAttr);
	}
	
	protected final void onDrawVerticalScrollBar(Canvas canvas, Drawable scrollBar, int l, int t, int r, int b)
	{
		if (b - t == getHeight())
		{
			t += getEdgeEffectShift(true);
			b -= getEdgeEffectShift(false);
		}
		scrollBar.setBounds(l, t, r, b);
		scrollBar.draw(canvas);
	}
	
	public void setEdgeEffectShift(EdgeEffectHandler.Shift shift)
	{
		mShift = shift;
	}
	
	public EdgeEffectHandler getEdgeEffectHandler()
	{
		return mEdgeEffectHandler;
	}
	
	@Override
	public void setOverScrollMode(int mode)
	{
		super.setOverScrollMode(mode);
		if (mode == View.OVER_SCROLL_NEVER) mEdgeEffectHandler = null; else
		{
			EdgeEffectHandler edgeEffectHandler = EdgeEffectHandler.bind(this, this);
			if (edgeEffectHandler != null) mEdgeEffectHandler = edgeEffectHandler;
		}
	}
	
	@Override
	public int getEdgeEffectShift(boolean top)
	{
		return mShift != null ? mShift.getEdgeEffectShift(top) : obtainEdgeEffectShift(top);
	}
	
	public final int obtainEdgeEffectShift(boolean top)
	{
		return  top ? -getTopPaddingOffset() : getBottomPaddingOffset();
	}
}