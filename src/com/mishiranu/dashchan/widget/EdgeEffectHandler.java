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

import java.lang.reflect.Field;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.widget.AbsListView;
import android.widget.EdgeEffect;
import com.mishiranu.dashchan.C;

public class EdgeEffectHandler
{
	public interface Shift
	{
		public int getEdgeEffectShift(boolean top);
	}
	
	private int mShiftTop = 0;
	private int mShiftBottom = 0;
	
	private class InternalShift implements Shift
	{
		@Override
		public int getEdgeEffectShift(boolean top)
		{
			return top ? mShiftTop : mShiftBottom;
		}
	}
	
	private static class ControlledEdgeEffect extends EdgeEffect
	{
		private final Shift mShift;
		private final boolean mTop;
		
		public ControlledEdgeEffect(Context context, Shift shift, boolean top)
		{
			super(context);
			mShift = shift;
			mTop = top;
		}
		
		private boolean mPullable = true;
		
		public void setPullable(boolean pullable)
		{
			mPullable = pullable;
		}
		
		@Override
		public void onPull(float deltaDistance)
		{
			if (mPullable) super.onPull(deltaDistance);
		}
		
		@TargetApi(Build.VERSION_CODES.LOLLIPOP)
		@Override
		public void onPull(float deltaDistance, float displacement)
		{
			if (mPullable) super.onPull(deltaDistance, displacement);
		}
		
		private int mWidth;
		
		@Override
		public void setSize(int width, int height)
		{
			super.setSize(width, height);
			mWidth = width;
		}
		
		@TargetApi(Build.VERSION_CODES.LOLLIPOP)
		@Override
		public int getMaxHeight()
		{
			return super.getMaxHeight() + mShift.getEdgeEffectShift(mTop);
		}
		
		private final Paint mShiftPaint = new Paint();
		
		@TargetApi(Build.VERSION_CODES.LOLLIPOP)
		@Override
		public boolean draw(Canvas canvas)
		{
			if (mPullable)
			{
				int shift = mShift.getEdgeEffectShift(mTop);
				boolean needShift = shift != 0;
				if (needShift)
				{
					if (C.API_LOLLIPOP)
					{
						int color = getColor();
						Paint paint = mShiftPaint;
						paint.setColor(color);
						canvas.drawRect(0, 0, mWidth, shift, paint);
					}
					canvas.save();
					canvas.translate(0, shift);
				}
				boolean result = super.draw(canvas);
				if (needShift) canvas.restore();
				return result;
			}
			else return false;
		}
	}
	
	private final ControlledEdgeEffect mTopEdgeEffect;
	private final ControlledEdgeEffect mBottomEdgeEffect;
	
	private EdgeEffectHandler(Context context, Shift shift)
	{
		if (shift == null) shift = new InternalShift();
		mTopEdgeEffect = new ControlledEdgeEffect(context, shift, true);
		mBottomEdgeEffect = new ControlledEdgeEffect(context, shift, false);
	}
	
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public void setColor(int color)
	{
		if (C.API_LOLLIPOP)
		{
			mTopEdgeEffect.setColor(color);
			mBottomEdgeEffect.setColor(color);
		}
	}
	
	public void setShift(boolean top, int shift)
	{
		if (top) mShiftTop = shift; else mShiftBottom = shift;
	}
	
	public void setPullable(boolean top, boolean pullable)
	{
		(top ? mTopEdgeEffect : mBottomEdgeEffect).setPullable(pullable);
	}
	
	public void finish(boolean top)
	{
		(top ? mTopEdgeEffect : mBottomEdgeEffect).finish();
	}
	
	private static final Field EDGE_GLOW_TOP_FIELD, EDGE_GLOW_BOTTOM_FIELD;
	
	static
	{
		Field edgeGlowTopField, edgeGlowBottomField;
		try
		{
			edgeGlowTopField = AbsListView.class.getDeclaredField("mEdgeGlowTop");
			edgeGlowTopField.setAccessible(true);
			edgeGlowBottomField = AbsListView.class.getDeclaredField("mEdgeGlowBottom");
			edgeGlowBottomField.setAccessible(true);
		}
		catch (Exception e)
		{
			edgeGlowTopField = null;
			edgeGlowBottomField = null;
		}
		EDGE_GLOW_TOP_FIELD = edgeGlowTopField;
		EDGE_GLOW_BOTTOM_FIELD = edgeGlowBottomField;
	}
	
	public static EdgeEffectHandler bind(AbsListView listView, Shift shift)
	{
		if (EDGE_GLOW_TOP_FIELD != null && EDGE_GLOW_BOTTOM_FIELD != null)
		{
			try
			{
				Object edgeEffect = EDGE_GLOW_TOP_FIELD.get(listView);
				if (edgeEffect != null && !(edgeEffect instanceof ControlledEdgeEffect))
				{
					EdgeEffectHandler handler = new EdgeEffectHandler(listView.getContext(), shift);
					EDGE_GLOW_TOP_FIELD.set(listView, handler.mTopEdgeEffect);
					EDGE_GLOW_BOTTOM_FIELD.set(listView, handler.mBottomEdgeEffect);
					return handler;
				}
			}
			catch (Exception e)
			{
				
			}
		}
		return null;
	}
}