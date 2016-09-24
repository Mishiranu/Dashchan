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

import android.graphics.Color;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.UpdateAppearance;

import com.mishiranu.dashchan.graphics.ColorScheme;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.widget.CommentTextView;

public class SpoilerSpan extends CharacterStyle implements UpdateAppearance, CommentTextView.ClickableSpan,
		ColorScheme.Span
{
	private int mBackgroundColor, mClickedColor;
	private boolean mClicked, mEnabled, mVisible;

	@Override
	public void applyColorScheme(ColorScheme colorScheme)
	{
		if (colorScheme != null)
		{
			mBackgroundColor = colorScheme.spoilerTopBackgroundColor;
			mClickedColor = colorScheme.clickedColor;
		}
	}

	public void setEnabled(boolean enabled)
	{
		mEnabled = enabled;
	}

	public void setVisible(boolean visible)
	{
		mVisible = visible;
	}

	public boolean isVisible()
	{
		return mVisible;
	}

	@Override
	public void updateDrawState(TextPaint paint)
	{
		if (!mEnabled || mVisible)
		{
			if (mEnabled && mClicked) paint.bgColor = GraphicsUtils.mixColors(mClickedColor, paint.bgColor);
		}
		else
		{
			paint.bgColor = mClicked ? GraphicsUtils.mixColors(mBackgroundColor, mClickedColor) : mBackgroundColor;
			paint.setColor(Color.TRANSPARENT);
		}
	}

	@Override
	public void setClicked(boolean clicked)
	{
		mClicked = clicked;
	}
}