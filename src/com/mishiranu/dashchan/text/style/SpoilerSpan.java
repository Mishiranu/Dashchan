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
		ColorScheme.Span {
	private int backgroundColor, clickedColor;
	private boolean clicked, enabled, visible;

	@Override
	public void applyColorScheme(ColorScheme colorScheme) {
		if (colorScheme != null) {
			backgroundColor = colorScheme.spoilerTopBackgroundColor;
			clickedColor = colorScheme.clickedColor;
		}
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	public boolean isVisible() {
		return visible;
	}

	@Override
	public void updateDrawState(TextPaint paint) {
		if (!enabled || visible) {
			if (enabled && clicked) {
				paint.bgColor = GraphicsUtils.mixColors(clickedColor, paint.bgColor);
			}
		} else {
			paint.bgColor = clicked ? GraphicsUtils.mixColors(backgroundColor, clickedColor) : backgroundColor;
			paint.setColor(Color.TRANSPARENT);
		}
	}

	@Override
	public void setClicked(boolean clicked) {
		this.clicked = clicked;
	}
}