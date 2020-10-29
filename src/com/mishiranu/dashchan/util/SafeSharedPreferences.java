package com.mishiranu.dashchan.util;

import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class SafeSharedPreferences implements SharedPreferences {
	private final SharedPreferences preferences;

	public SafeSharedPreferences(SharedPreferences preferences) {
		this.preferences = preferences;
	}

	@Override
	public Map<String, ?> getAll() {
		return preferences.getAll();
	}

	@Override
	public String getString(String key, String defValue) {
		String value;
		try {
			value = preferences.getString(key, defValue);
		} catch (Exception e) {
			value = null;
		}
		return value != null ? value : defValue;
	}

	@Override
	public Set<String> getStringSet(String key, Set<String> defValues) {
		Set<String> value;
		try {
			value = preferences.getStringSet(key, defValues);
		} catch (Exception e) {
			value = null;
		}
		return value != null ? value : defValues;
	}

	@Override
	public int getInt(String key, int defValue) {
		try {
			return preferences.getInt(key, defValue);
		} catch (Exception e) {
			return defValue;
		}
	}

	@Override
	public long getLong(String key, long defValue) {
		try {
			return preferences.getLong(key, defValue);
		} catch (Exception e) {
			return defValue;
		}
	}

	@Override
	public float getFloat(String key, float defValue) {
		try {
			return preferences.getFloat(key, defValue);
		} catch (Exception e) {
			return defValue;
		}
	}

	@Override
	public boolean getBoolean(String key, boolean defValue) {
		try {
			return preferences.getBoolean(key, defValue);
		} catch (Exception e) {
			return defValue;
		}
	}

	@Override
	public boolean contains(String key) {
		return preferences.contains(key);
	}

	@Override
	public Editor edit() {
		return preferences.edit();
	}

	private final Map<OnSharedPreferenceChangeListener, OnSharedPreferenceChangeListener> listeners = new HashMap<>();

	@Override
	public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
		synchronized (listeners) {
			if (!listeners.containsKey(listener)) {
				OnSharedPreferenceChangeListener internalListener =
						(p, key) -> listener.onSharedPreferenceChanged(p == preferences ? this : p, key);
				preferences.registerOnSharedPreferenceChangeListener(internalListener);
				listeners.put(listener, internalListener);
			}
		}
	}

	@Override
	public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
		synchronized (listeners) {
			OnSharedPreferenceChangeListener internalListener = listeners.remove(listener);
			if (internalListener != null) {
				preferences.unregisterOnSharedPreferenceChangeListener(internalListener);
			}
		}
	}
}
