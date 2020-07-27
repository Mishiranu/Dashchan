package com.mishiranu.dashchan.preference.core;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;

public abstract class DialogPreference<T> extends Preference<T> {
	public interface ButtonListener {
		void onClick();
	}

	private CharSequence neutralButtonText;
	private ButtonListener neutralButtonListener;

	public DialogPreference(Context context, String key, T defaultValue,
			CharSequence title, SummaryProvider<T> summaryProvider) {
		super(context, key, defaultValue, title, summaryProvider);
	}

	protected AlertDialog createDialog(Bundle savedInstanceState) {
		AlertDialog dialog = configureDialog(savedInstanceState, new AlertDialog.Builder(context)).create();
		if (neutralButtonText != null) {
			dialog.setButton(AlertDialog.BUTTON_NEUTRAL, neutralButtonText, (AlertDialog.OnClickListener) null);
			dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
					.setOnClickListener(v -> neutralButtonListener.onClick()));
		}
		return dialog;
	}

	protected AlertDialog.Builder configureDialog(Bundle savedInstanceState, AlertDialog.Builder builder) {
		return builder.setTitle(title)
				.setNegativeButton(android.R.string.cancel, null);
	}

	protected void saveState(AlertDialog dialog, Bundle outState) {}

	public void setNeutralButton(CharSequence text, ButtonListener listener) {
		neutralButtonText = text;
		neutralButtonListener = listener;
	}
}
