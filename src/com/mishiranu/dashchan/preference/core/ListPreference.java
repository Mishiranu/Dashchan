package com.mishiranu.dashchan.preference.core;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import java.util.Arrays;

public class ListPreference extends DialogPreference<String> {
	public final CharSequence[] entries;
	public final String[] values;

	private static int getIndex(ListPreference preference) {
		int index = Arrays.asList(preference.values).indexOf(preference.getValue());
		if (index < 0) {
			index = Arrays.asList(preference.values).indexOf(preference.defaultValue);
		}
		return index;
	}

	public ListPreference(Context context, String key, String defaultValue, CharSequence title,
			CharSequence[] entries, String[] values) {
		super(context, key, defaultValue, title, p -> entries[getIndex((ListPreference) p)]);
		if (entries.length != values.length) {
			throw new IllegalArgumentException();
		}
		this.entries = entries;
		this.values = values;
	}

	@Override
	protected void extract(SharedPreferences preferences) {
		String value = preferences.getString(key, defaultValue);
		if (!Arrays.asList(values).contains(value)) {
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
				.setSingleChoiceItems(entries, getIndex(this), (d, which) -> {
					d.dismiss();
					new Handler().post(() -> setValue(values[which]));
				});
	}
}
