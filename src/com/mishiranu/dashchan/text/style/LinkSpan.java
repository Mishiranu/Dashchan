/*
 * Copyright 2014-2017 Fukurou Mishiranu
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
		ColorScheme.Span {
	private final String uriString;
	private final String postNumber;

	private int foregroundColor, clickedColor;

	private boolean clicked;
	private boolean hidden;

	public LinkSpan(String uriString, String postNumber) {
		this.uriString = StringUtils.fixParsedUriString(uriString);
		this.postNumber = postNumber;
	}

	@Override
	public void applyColorScheme(ColorScheme colorScheme) {
		if (colorScheme != null) {
			foregroundColor = colorScheme.linkColor;
			clickedColor = colorScheme.clickedColor;
		}
	}

	@Override
	public void updateDrawState(TextPaint paint) {
		if (paint.getColor() != Color.TRANSPARENT) {
			if (hidden) {
				paint.setColor(foregroundColor & 0x00ffffff | Color.argb(Color.alpha(foregroundColor) / 2, 0, 0, 0));
				paint.setStrikeThruText(true);
			} else {
				paint.setColor(foregroundColor);
			}
			paint.setUnderlineText(true);
			if (clicked) {
				paint.bgColor = Color.alpha(paint.bgColor) == 0x00 ? clickedColor
						: GraphicsUtils.mixColors(paint.bgColor, clickedColor);
			}
		}
	}

	@Override
	public void setClicked(boolean clicked) {
		this.clicked = clicked;
	}

	public void setHidden(boolean hidden) {
		this.hidden = hidden;
	}

	public String getUriString() {
		return uriString;
	}

	public String getPostNumber() {
		return postNumber;
	}
}