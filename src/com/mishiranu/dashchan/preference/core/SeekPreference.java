package com.mishiranu.dashchan.preference.core;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import com.mishiranu.dashchan.ui.SeekBarForm;

public class SeekPreference extends DialogPreference<Integer> {
	private static final String STATE_CURRENT_VALUE = "currentValue";

	private final SeekBarForm seekBarForm;

	public SeekPreference(Context context, String key, int defaultValue, CharSequence title,
			String valueFormat, int minValue, int maxValue, int step, float multiplier) {
		super(context, key, defaultValue, title, p -> valueFormat != null
				? String.format(valueFormat, (int) (multiplier * p.getValue())) : null);
		seekBarForm = new SeekBarForm(false);
		seekBarForm.setConfiguration(minValue, maxValue, step, multiplier);
		seekBarForm.setValueFormat(valueFormat);
	}

	@Override
	protected void extract(SharedPreferences preferences) {
		setValue(preferences.getInt(key, defaultValue));
	}

	@Override
	protected void persist(SharedPreferences preferences) {
		preferences.edit().putInt(key, getValue()).commit();
	}

	@Override
	protected AlertDialog.Builder configureDialog(Bundle savedInstanceState, AlertDialog.Builder builder) {
		int currentValue = savedInstanceState != null ? savedInstanceState.getInt(STATE_CURRENT_VALUE) : getValue();
		seekBarForm.setCurrentValue(currentValue);
		return super.configureDialog(savedInstanceState, builder)
				.setView(seekBarForm.inflate(builder.getContext()))
				.setPositiveButton(android.R.string.ok, (d, which) -> new Handler()
						.post(() -> setValue(seekBarForm.getCurrentValue())));
	}

	@Override
	protected void saveState(AlertDialog dialog, Bundle outState) {
		super.saveState(dialog, outState);
		outState.putInt(STATE_CURRENT_VALUE, seekBarForm.getCurrentValue());
	}
}
