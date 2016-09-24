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

import chan.util.StringUtils;

import com.mishiranu.dashchan.graphics.ColorScheme;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.widget.CommentTextView;

public class LinkSpan extends CharacterStyle implements UpdateAppearance, CommentTextView.ClickableSpan,
		ColorScheme.Span
{
	private final String mUriString;
	private final String mPostNumber;

	private int mForegroundColor, mClickedColor;

	private boolean mClicked;
	private boolean mHidden;

	public LinkSpan(String uriString, String postNumber)
	{
		mUriString = StringUtils.fixParsedUriString(uriString);
		mPostNumber = postNumber;
	}

	@Override
	public void applyColorScheme(ColorScheme colorScheme)
	{
		if (colorScheme != null)
		{
			mForegroundColor = colorScheme.linkColor;
			mClickedColor = colorScheme.clickedColor;
		}
	}

	@Override
	public void updateDrawState(TextPaint paint)
	{
		if (mHidden)
		{
			paint.setColor(mForegroundColor & 0x00ffffff | Color.argb(Color.alpha(mForegroundColor) / 2, 0, 0, 0));
			paint.setStrikeThruText(true);
		}
		else paint.setColor(mForegroundColor);
		paint.setUnderlineText(true);
		if (mClicked)
		{
			paint.bgColor = Color.alpha(paint.bgColor) == 0x00 ? mClickedColor
					: GraphicsUtils.mixColors(paint.bgColor, mClickedColor);
		}
	}

	@Override
	public void setClicked(boolean clicked)
	{
		mClicked = clicked;
	}

	public void setHidden(boolean hidden)
	{
		mHidden = hidden;
	}

	public String getUriString()
	{
		return mUriString;
	}

	public String getPostNumber()
	{
		return mPostNumber;
	}
}