package com.mishiranu.dashchan.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;

public class PostLinearLayout extends LinearLayout {
	public PostLinearLayout(Context context) {
		super(context);
	}

	public PostLinearLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public PostLinearLayout(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	@Override
	public boolean hasOverlappingRendering() {
		// Makes setAlpha faster, see https://plus.google.com/+RomanNurik/posts/NSgQvbfXGQN
		// Thumbnails will become strange with alpha because background alpha and image alpha are separate now
		return false;
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		View focusedView = findFocus();
		if (focusedView instanceof CommentTextView && ((CommentTextView) focusedView).isSelectionMode()) {
			// Don't draw selection background
			return false;
		}
		return super.onTouchEvent(event);
	}

	private Drawable secondaryBackground;

	public void setSecondaryBackgroundColor(int color) {
		if (secondaryBackground instanceof ColorDrawable) {
			((ColorDrawable) secondaryBackground.mutate()).setColor(color);
		} else {
			setSecondaryBackground(new ColorDrawable(color));
		}
	}

	public void setSecondaryBackground(Drawable drawable) {
		if (secondaryBackground != drawable) {
			if (secondaryBackground != null) {
				secondaryBackground.setCallback(null);
				unscheduleDrawable(secondaryBackground);
			}
			secondaryBackground = drawable;
			if (drawable != null) {
				drawable.setCallback(this);
			}
			invalidate();
		}
	}

	@Override
	protected boolean verifyDrawable(@NonNull Drawable who) {
		return super.verifyDrawable(who) || who == secondaryBackground;
	}

	@Override
	public void draw(Canvas canvas) {
		super.draw(canvas);

		Drawable secondaryBackground = this.secondaryBackground;
		if (secondaryBackground != null) {
			secondaryBackground.setBounds(0, 0, getWidth(), getHeight());
			secondaryBackground.draw(canvas);
		}
	}
}
