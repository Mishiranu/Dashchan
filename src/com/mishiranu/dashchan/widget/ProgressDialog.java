package com.mishiranu.dashchan.widget;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.util.ResourceUtils;
import java.text.NumberFormat;

public class ProgressDialog extends AlertDialog {
	private final String progressFormat;
	private final NumberFormat numberFormat;
	private final ProgressBar progressBar;
	private final TextView message;
	private final TextView percent;
	private final TextView progress;

	@SuppressLint("RtlHardcoded")
	public ProgressDialog(Context context, String progressFormat) {
		super(context);
		setCanceledOnTouchOutside(false);

		this.progressFormat = progressFormat;
		if (progressFormat != null) {
			numberFormat = NumberFormat.getPercentInstance();
			numberFormat.setMaximumFractionDigits(0);
			// Ensure message TextView is created
			setMessage("");
		} else {
			numberFormat = null;
		}

		LinearLayout layout = new LinearLayout(context);
		float density = ResourceUtils.obtainDensity(context);
		int horizontalPadding = (int) ((C.API_LOLLIPOP ? numberFormat != null ? 24f : 20f : 16f) * density);
		int verticalPadding = (int) ((C.API_LOLLIPOP ? 18f : 16f) * density);
		layout.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
		setView(layout);

		if (progressFormat == null) {
			layout.setOrientation(LinearLayout.HORIZONTAL);
			layout.setGravity(Gravity.CENTER_VERTICAL);
			progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyle);
			layout.addView(progressBar, LinearLayout.LayoutParams.WRAP_CONTENT,
					LinearLayout.LayoutParams.WRAP_CONTENT);
			message = new TextView(context);
			layout.addView(message, LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.WRAP_CONTENT);
			if (C.API_JELLY_BEAN_MR1 && message.getLayoutDirection() == TextView.LAYOUT_DIRECTION_RTL) {
				message.setPadding(0, 0, horizontalPadding, 0);
			} else {
				message.setPadding(horizontalPadding, 0, 0, 0);
			}
			percent = null;
			progress = null;
		} else {
			layout.setOrientation(LinearLayout.VERTICAL);
			progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
			layout.addView(progressBar, LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.WRAP_CONTENT);
			message = null;
			LinearLayout inner = new LinearLayout(context);
			layout.addView(inner, LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.WRAP_CONTENT);
			inner.setOrientation(LinearLayout.HORIZONTAL);
			percent = new TextView(context);
			inner.addView(percent, LinearLayout.LayoutParams.WRAP_CONTENT,
					LinearLayout.LayoutParams.WRAP_CONTENT);
			progress = new TextView(context);
			inner.addView(progress, 0, LinearLayout.LayoutParams.WRAP_CONTENT);
			((LinearLayout.LayoutParams) progress.getLayoutParams()).weight = 1f;
			progress.setGravity(C.API_JELLY_BEAN_MR1 ? Gravity.END : Gravity.RIGHT);
		}
		updateProgress();
	}

	@Override
	public void setMessage(CharSequence message) {
		if (progressFormat == null) {
			this.message.setText(message);
		} else {
			super.setMessage(message);
		}
	}

	public void setIndeterminate(boolean indeterminate) {
		progressBar.setIndeterminate(indeterminate);
	}

	public void setValue(int value) {
		progressBar.setProgress(value);
		updateProgress();
	}

	public void setMax(int max) {
		progressBar.setMax(max);
		updateProgress();
	}

	private void updateProgress() {
		if (progressFormat != null) {
			int value = progressBar.getProgress();
			int max = progressBar.getMax();
			percent.setText(numberFormat.format((double) value / (double) max));
			progress.setText(String.format(progressFormat, value, max));
		}
	}
}
