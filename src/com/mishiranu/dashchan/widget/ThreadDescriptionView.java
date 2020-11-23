package com.mishiranu.dashchan.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import androidx.core.view.ViewCompat;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.util.ResourceUtils;
import java.util.ArrayList;
import java.util.Locale;

public class ThreadDescriptionView extends View {
	private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint.FontMetrics fontMetrics = new Paint.FontMetrics();

	private final ArrayList<String> description = new ArrayList<>();
	private final ArrayList<String> shortDescription = new ArrayList<>();

	private boolean toEnd;
	private int spacing;

	public ThreadDescriptionView(Context context) {
		this(context, null);
	}

	public ThreadDescriptionView(Context context, AttributeSet attrs) {
		super(context, attrs);
		if (C.API_LOLLIPOP) {
			paint.setTypeface(ResourceUtils.TYPEFACE_MEDIUM);
		}
	}

	public void setTextColor(int color) {
		paint.setColor(color);
		invalidate();
	}

	public void setTextSizeSp(float sizeSp) {
		int size = (int) (sizeSp * getResources().getDisplayMetrics().scaledDensity + 0.5f);
		paint.setTextSize(size);
		requestLayout();
	}

	public void setToEnd(boolean toEnd) {
		this.toEnd = toEnd;
		invalidate();
	}

	public void setSpacing(int spacing) {
		this.spacing = spacing;
		invalidate();
	}

	public void clear() {
		description.clear();
		shortDescription.clear();
		invalidate();
	}

	public void append(String value) {
		if (!StringUtils.isEmpty(value)) {
			description.add(value.toUpperCase(Locale.getDefault()));
			shortDescription.clear();
		}
		invalidate();
	}

	private void prepareShortDescription() {
		if (shortDescription.isEmpty()) {
			StringBuilder builder = new StringBuilder();
			for (String value : description) {
				builder.setLength(0);
				String[] words = value.split(" ");
				for (String word : words) {
					int length = builder.length();
					try {
						Integer.parseInt(word);
						if (length > 0) {
							builder.append(' ');
						}
						builder.append(word);
					} catch (NumberFormatException e) {
						if (word.length() >= 3) {
							if (length > 0 && builder.charAt(length - 1) != '.') {
								builder.append(' ');
							}
							builder.append(word.charAt(0)).append('.');
						} else {
							if (length > 0) {
								builder.append(' ');
							}
							builder.append(word);
						}
					}
				}
				shortDescription.add(builder.toString());
			}
		}
	}

	@Override
	protected int getSuggestedMinimumHeight() {
		paint.getFontMetrics(fontMetrics);
		int textHeight = (int) Math.ceil(fontMetrics.bottom) - (int) Math.floor(fontMetrics.top);
		int minHeight = textHeight + getPaddingTop() + getPaddingBottom();
		return Math.max(minHeight, super.getSuggestedMinimumHeight());
	}

	private float[] measurements = null;

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		Paint paint = this.paint;
		Paint.FontMetrics fontMetrics = this.fontMetrics;
		paint.getFontMetrics(fontMetrics);
		int maxWidth = getWidth() - getPaddingLeft() - getPaddingRight();
		float totalWidth = 0;
		if (measurements == null || measurements.length != description.size()) {
			measurements = new float[description.size()];
		}
		float[] measurements = this.measurements;
		for (int i = 0; i < measurements.length; i++) {
			float width = paint.measureText(description.get(i));
			measurements[i] = width;
			totalWidth += width;
		}
		totalWidth += spacing * (description.size() - 1);
		ArrayList<String> description;
		boolean measure;
		if (totalWidth > maxWidth) {
			prepareShortDescription();
			description = shortDescription;
			measure = true;
		} else {
			description = this.description;
			measure = false;
		}
		int baseline = getHeight() - getPaddingBottom() - (int) Math.ceil(fontMetrics.bottom);
		float left = getPaddingLeft();
		float right = getWidth() - getPaddingRight();
		boolean rtl = ViewCompat.getLayoutDirection(this) == ViewCompat.LAYOUT_DIRECTION_RTL;
		if (rtl != toEnd) {
			for (int i = measurements.length - 1; i >= 0; i--) {
				String text = description.get(i);
				float width = measure ? paint.measureText(text) : measurements[i];
				if (right - width < left) {
					break;
				}
				canvas.drawText(description.get(i), right - width, baseline, paint);
				right -= width + spacing;
			}
		} else {
			for (int i = 0; i < measurements.length; i++) {
				String text = description.get(i);
				float width = measure ? paint.measureText(text) : measurements[i];
				if (left + width > right) {
					break;
				}
				canvas.drawText(description.get(i), left, baseline, paint);
				left += width + spacing;
			}
		}
	}
}
