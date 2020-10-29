package com.mishiranu.dashchan.ui.preference.core;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import chan.util.CommonUtils;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import java.util.List;

public class ListPreference extends DialogPreference<String> {
	public final List<CharSequence> entries;
	public final List<String> values;

	private static int getIndex(ListPreference preference) {
		int index = preference.values.indexOf(preference.getValue());
		if (index < 0) {
			index = preference.values.indexOf(preference.defaultValue);
		}
		return index;
	}

	public ListPreference(Context context, String key, String defaultValue, CharSequence title,
			List<CharSequence> entries, List<String> values) {
		super(context, key, defaultValue, title, p -> entries.get(getIndex((ListPreference) p)));
		if (entries.size() != values.size()) {
			throw new IllegalArgumentException();
		}
		this.entries = entries;
		this.values = values;
	}

	@Override
	protected void extract(SharedPreferences preferences) {
		String value = preferences.getString(key, defaultValue);
		if (!values.contains(value)) {
			value = defaultValue;
		}
		setValue(value);
	}

	@Override
	protected void persist(SharedPreferences preferences) {
		preferences.edit().putString(key, getValue()).commit();
	}

	@Override
	protected AlertDialog.Builder configureDialog(Bundle savedInstanceState, AlertDialog.Builder builder) {
		return super.configureDialog(savedInstanceState, builder)
				.setSingleChoiceItems(CommonUtils.toArray(entries, CharSequence.class), getIndex(this), (d, which) -> {
					d.dismiss();
					ConcurrentUtils.HANDLER.post(() -> setValue(values.get(which)));
				});
	}
}
