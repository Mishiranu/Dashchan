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
import android.util.AttributeSet;
import android.widget.LinearLayout;

public class SingleLayerLinearLayout extends LinearLayout
{
	public SingleLayerLinearLayout(Context context)
	{
		super(context);
	}
	
	public SingleLayerLinearLayout(Context context, AttributeSet attrs)
	{
		super(context, attrs);
	}
	
	public SingleLayerLinearLayout(Context context, AttributeSet attrs, int defStyleAttr)
	{
		super(context, attrs, defStyleAttr);
	}
	
	@Override
	public boolean hasOverlappingRendering()
	{
		// Makes setAlpha faster, see https://plus.google.com/+RomanNurik/posts/NSgQvbfXGQN
		// Thumbnails will become strange with alpha because background alpha and image alpha are separate now
		return false;
	}
	
	public interface OnTemporaryDetatchListener
	{
		public void onTemporaryDetatch(SingleLayerLinearLayout view, boolean start);
	}
	
	private OnTemporaryDetatchListener mOnTemporaryDetatchListener;
	
	public void setOnTemporaryDetatchListener(OnTemporaryDetatchListener listener)
	{
		mOnTemporaryDetatchListener = listener;
	}
	
	@Override
	public void onStartTemporaryDetach()
	{
		super.onStartTemporaryDetach();
		if (mOnTemporaryDetatchListener != null) mOnTemporaryDetatchListener.onTemporaryDetatch(this, true);
	}
	
	@Override
	public void onFinishTemporaryDetach()
	{
		super.onFinishTemporaryDetach();
		if (mOnTemporaryDetatchListener != null) mOnTemporaryDetatchListener.onTemporaryDetatch(this, false);
	}
}