package com.mishiranu.dashchan.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.content.service.WatcherService;
import com.mishiranu.dashchan.util.ResourceUtils;

@SuppressLint("ViewConstructor")
public class WatcherView extends FrameLayout {
	public static class ColorSet {
		public final int enabledColor;
		public final int unavailableColor;
		public final int disabledColor;

		public ColorSet(int enabledColor, int unavailableColor, int disabledColor) {
			this.enabledColor = enabledColor;
			this.unavailableColor = unavailableColor;
			this.disabledColor = disabledColor;
		}
	}

	private final ProgressBar progressBar;
	private final ColorSet colorSet;

	private String text = "";
	private boolean hasNew = false;
	private int color;

	public WatcherView(Context context, ColorSet colorSet) {
		super(context);

		setBackgroundResource(ResourceUtils.getResourceId(context, android.R.attr.selectableItemBackground, 0));
		progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleSmall);
		if (C.API_LOLLIPOP) {
			progressBar.setIndeterminateTintList(ColorStateList.valueOf(Color.WHITE));
		}
		addView(progressBar, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
				FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
		this.colorSet = colorSet;
		update(WatcherService.Counter.INITIAL);
	}

	private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.LINEAR_TEXT_FLAG);
	private final RectF rectF = new RectF();
	private final Rect rect = new Rect();

	@Override
	public void draw(Canvas canvas) {
		float density = ResourceUtils.obtainDensity(this);
		if (C.API_LOLLIPOP) {
			int paddingHorizontal = (int) (8f * density);
			int paddingVertical = (int) (12f * density);
			rectF.set(paddingHorizontal, paddingVertical, getWidth() - paddingHorizontal,
					getHeight() - paddingVertical);
		} else {
			int padding = (int) (8f * density);
			rectF.set(padding, padding, getWidth() - padding, getHeight() - padding);
		}
		int cornerRadius = C.API_LOLLIPOP ? (int) density : (int) (4f * density);
		paint.setColor(color);
		canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint);
		canvas.save();
		canvas.clipRect(rectF);
		super.draw(canvas);

		if (progressBar.getVisibility() != View.VISIBLE) {
			int fontSize = C.API_LOLLIPOP ? 12 : 16;
			paint.setColor(Color.WHITE);
			if (!hasNew) {
				paint.setAlpha(0x99);
			}
			paint.setTextSize((int) (fontSize * getResources().getDisplayMetrics().scaledDensity + 0.5f));
			paint.setTextAlign(Paint.Align.CENTER);
			paint.getTextBounds(text, 0, text.length(), rect);
			canvas.drawText(text, getWidth() / 2f, (getHeight() + rect.height()) / 2f, paint);
		} else if (hasNew) {
			paint.setColor(Color.WHITE);
			canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, (int) (4f * density), paint);
		}
		canvas.restore();
	}

	public void update(WatcherService.Counter counter) {
		progressBar.setVisibility(counter.running ? View.VISIBLE : View.GONE);
		switch (counter.state) {
			case ENABLED: {
				color = colorSet.enabledColor;
				break;
			}
			case UNAVAILABLE: {
				color = colorSet.unavailableColor;
				break;
			}
			case DISABLED: {
				color = colorSet.disabledColor;
				break;
			}
			default: {
				throw new IllegalStateException();
			}
		}
		String text;
		if (counter.newCount <= 0 && counter.deleted) {
			text = "X";
		} else {
			if (Math.abs(counter.newCount) >= 1000) {
				text = (counter.newCount / 1000) + "K+";
			} else {
				text = Integer.toString(counter.newCount);
			}
			if (counter.deleted) {
				text += "X";
			} else if (counter.error) {
				text += "?";
			}
		}
		this.text = text;
		this.hasNew = counter.newCount > 0;
		invalidate();
	}
}
