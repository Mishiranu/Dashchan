package com.mishiranu.dashchan.ui.preference.core;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Pair;
import com.mishiranu.dashchan.ui.SeekBarForm;
import com.mishiranu.dashchan.util.ConcurrentUtils;

public class SeekPreference extends DialogPreference<Integer> {
	private static final String STATE_SWITCH_VALUE = "switchValue";
	private static final String STATE_CURRENT_VALUE = "currentValue";

	private final SeekBarForm seekBarForm;
	private final Integer specialValue;

	public SeekPreference(Context context, String key, int defaultValue, CharSequence title, String valueFormat,
			Pair<Integer, String> specialValue, int minValue, int maxValue, int step) {
		super(context, key, defaultValue, title, p -> specialValue != null && specialValue.first.equals(p.getValue())
				? specialValue.second : valueFormat != null ? String.format(valueFormat, p.getValue()) : null);
		if (specialValue != null && specialValue.first >= minValue && specialValue.first <= maxValue) {
			throw new IllegalArgumentException();
		}
		seekBarForm = new SeekBarForm(specialValue != null, minValue, maxValue, step, valueFormat);
		this.specialValue = specialValue != null ? specialValue.first : null;
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
		if (savedInstanceState != null) {
			seekBarForm.setSwitchValue(savedInstanceState.getBoolean(STATE_SWITCH_VALUE));
			seekBarForm.setCurrentValue(savedInstanceState.getInt(STATE_CURRENT_VALUE));
		} else {
			int value = getValue();
			seekBarForm.setSwitchValue(specialValue == null || specialValue != value);
			seekBarForm.setCurrentValue(specialValue != null && specialValue == value ? defaultValue : value);
		}

		return super.configureDialog(savedInstanceState, builder)
				.setView(seekBarForm.inflate(builder.getContext()))
				.setPositiveButton(android.R.string.ok, (d, which) -> ConcurrentUtils.HANDLER
						.post(() -> setValue(specialValue != null && !seekBarForm.getSwitchValue()
								? specialValue : seekBarForm.getCurrentValue())));
	}

	@Override
	protected void saveState(AlertDialog dialog, Bundle outState) {
		super.saveState(dialog, outState);

		outState.putBoolean(STATE_SWITCH_VALUE, seekBarForm.getSwitchValue());
		outState.putInt(STATE_CURRENT_VALUE, seekBarForm.getCurrentValue());
	}
}
