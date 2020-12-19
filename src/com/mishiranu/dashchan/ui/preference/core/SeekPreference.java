package com.mishiranu.dashchan.ui.preference.core;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Pair;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.SharedPreferences;
import com.mishiranu.dashchan.widget.ViewFactory;

public class SeekPreference extends DialogPreference<Integer> {
	private static final String STATE_ENABLED = "enabled";
	private static final String STATE_VALUE = "value";

	private final String valueFormat;
	private final Integer specialValue;
	private final int minValue;
	private final int maxValue;
	private final int step;

	public SeekPreference(Context context, String key, int defaultValue, CharSequence title, String valueFormat,
			Pair<Integer, String> specialValue, int minValue, int maxValue, int step) {
		super(context, key, defaultValue, title, p -> specialValue != null && specialValue.first.equals(p.getValue())
				? specialValue.second : valueFormat != null ? String.format(valueFormat, p.getValue()) : null);
		if (specialValue != null && specialValue.first >= minValue && specialValue.first <= maxValue) {
			throw new IllegalArgumentException();
		}
		this.valueFormat = valueFormat;
		this.specialValue = specialValue != null ? specialValue.first : null;
		this.minValue = minValue;
		this.maxValue = maxValue;
		this.step = step;
	}

	@Override
	protected void extract(SharedPreferences preferences) {
		setValue(preferences.getInt(key, defaultValue));
	}

	@Override
	protected void persist(SharedPreferences preferences) {
		preferences.edit().put(key, getValue()).close();
	}

	@Override
	protected AlertDialog.Builder configureDialog(Bundle savedInstanceState, AlertDialog.Builder builder) {
		ViewFactory.SeekLayoutHolder holder = ViewFactory.createSeekLayout(builder.getContext(),
				specialValue != null, minValue, maxValue, step, valueFormat);
		if (savedInstanceState != null) {
			holder.setEnabled(savedInstanceState.getBoolean(STATE_ENABLED));
			holder.setValue(savedInstanceState.getInt(STATE_VALUE));
		} else {
			int value = getValue();
			holder.setEnabled(specialValue == null || specialValue != value);
			holder.setValue(specialValue != null && specialValue == value ? defaultValue : value);
		}
		return super.configureDialog(savedInstanceState, builder).setView(holder.layout)
				.setPositiveButton(android.R.string.ok, (d, which) -> ConcurrentUtils.HANDLER
						.post(() -> setValue(specialValue != null && !holder.isEnabled()
								? specialValue : holder.getValue())));
	}

	@Override
	protected void saveState(AlertDialog dialog, Bundle outState) {
		super.saveState(dialog, outState);

		ViewFactory.SeekLayoutHolder holder = (ViewFactory.SeekLayoutHolder)
				dialog.findViewById(R.id.seek_layout).getTag();
		outState.putBoolean(STATE_ENABLED, holder.isEnabled());
		outState.putInt(STATE_VALUE, holder.getValue());
	}
}
