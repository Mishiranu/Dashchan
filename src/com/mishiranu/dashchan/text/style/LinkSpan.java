package com.mishiranu.dashchan.text.style;

import android.graphics.Color;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.UpdateAppearance;
import chan.util.StringUtils;
import com.mishiranu.dashchan.content.model.PostNumber;
import com.mishiranu.dashchan.graphics.ColorScheme;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.widget.CommentTextView;

public class LinkSpan extends CharacterStyle implements UpdateAppearance, CommentTextView.ClickableSpan,
		ColorScheme.Span {
	public final String uriString;
	public final PostNumber postNumber;

	private int foregroundColor;
	private int clickedColor;

	private boolean clicked;
	private boolean hidden;

	public LinkSpan(String uriString, PostNumber postNumber) {
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

	public boolean inBoardLink() {
		// >>NUMBER links cannot refer to another board
		// This check can be useful for threads moved to another board
		return postNumber != null;
	}
}
