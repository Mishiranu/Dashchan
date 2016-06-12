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

import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.widget.EditText;

import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.util.ResourceUtils;

public class ErrorEditTextSetter
{
	private final EditText mEditText;
	private boolean mError = false;
	
	private Drawable mBackgroundNormal;
	private Drawable mBackgroundError;
	
	public ErrorEditTextSetter(EditText editText)
	{
		mEditText = editText;
	}
	
	public void setError(boolean error)
	{
		if (mError != error)
		{
			mError = error;
			if (mBackgroundNormal == null)
			{
				mBackgroundNormal = ResourceUtils.getDrawable(mEditText.getContext(),
						android.R.attr.editTextBackground, 0);
				mBackgroundError = ResourceUtils.getDrawable(mEditText.getContext(),
						android.R.attr.editTextBackground, 0);
				mBackgroundError.mutate().setColorFilter(ResourceUtils.getColor(mEditText.getContext(),
						R.attr.colorTextError), PorterDuff.Mode.SRC_IN);
			}
			mEditText.setBackground(error ? mBackgroundError : mBackgroundNormal);
		}
	}
}