package com.mishiranu.dashchan.util;

import android.annotation.SuppressLint;
import android.content.Context;
import java.io.Closeable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class SharedPreferences {
	public static class Editor implements Closeable {
		private final SharedPreferences preferences;
		private final android.content.SharedPreferences.Editor editor;

		@SuppressLint("CommitPrefEdits")
		private Editor(SharedPreferences preferences) {
			this.preferences = preferences;
			editor = preferences.shared.edit();
		}

		public Editor put(String key, String value) {
			if (value != null) {
				editor.putString(key, value);
			} else {
				editor.remove(key);
			}
			return this;
		}

		public Editor put(String key, Set<String> values) {
			if (values != null) {
				editor.putStringSet(key, values);
			} else {
				editor.remove(key);
			}
			return this;
		}

		public Editor put(String key, int value) {
			editor.putInt(key, value);
			return this;
		}

		public Editor put(String key, long value) {
			editor.putLong(key, value);
			return this;
		}

		public Editor put(String key, float value) {
			editor.putFloat(key, value);
			return this;
		}

		public Editor put(String key, boolean value) {
			editor.putBoolean(key, value);
			return this;
		}

		public Editor remove(String key) {
			editor.remove(key);
			return this;
		}

		private boolean commitInternal() {
			try {
				preferences.shouldUpdateMap = true;
				return editor.commit();
			} finally {
				preferences.updateMapIfNeeded();
			}
		}

		@Override
		public void close() {
			if (ConcurrentUtils.isMain()) {
				commitInternal();
			} else {
				// Uninterruptible process
				ConcurrentUtils.mainGet(this::commitInternal);
			}
		}
	}

	public interface Listener {
		void onChanged(String key);
	}

	private final android.content.SharedPreferences shared;
	private Map<String, ?> map = Collections.emptyMap();
	private boolean shouldUpdateMap = true;

	public SharedPreferences(Context context, String name) {
		this.shared = context.getSharedPreferences(name, Context.MODE_PRIVATE);
		updateMapIfNeeded();
	}

	private void updateMapIfNeeded() {
		if (shouldUpdateMap) {
			// SharedPreferences.getAll() returns a new HashMap instance
			map = Collections.unmodifiableMap(shared.getAll());
			shouldUpdateMap = false;
		}
	}

	public Map<String, ?> getAll() {
		return map;
	}

	public String getString(String key, String defValue) {
		Object value = map.get(key);
		return value instanceof String ? (String) value : defValue;
	}

	@SuppressWarnings("unchecked")
	public Set<String> getStringSet(String key, Set<String> defValues) {
		Object value = map.get(key);
		return value instanceof Set ? (Set<String>) value : defValues;
	}

	public int getInt(String key, int defValue) {
		Object value = map.get(key);
		return value instanceof Number ? ((Number) value).intValue() : defValue;
	}

	public long getLong(String key, long defValue) {
		Object value = map.get(key);
		return value instanceof Number ? ((Number) value).longValue() : defValue;
	}

	public float getFloat(String key, float defValue) {
		Object value = map.get(key);
		return value instanceof Number ? ((Number) value).floatValue() : defValue;
	}

	public boolean getBoolean(String key, boolean defValue) {
		Object value = map.get(key);
		return value instanceof Boolean ? (boolean) value : defValue;
	}

	public boolean contains(String key) {
		return map.containsKey(key);
	}

	public Editor edit() {
		return new Editor(this);
	}

	private final Map<Listener, android.content.SharedPreferences
			.OnSharedPreferenceChangeListener> listeners = new HashMap<>();

	public void register(Listener listener) {
		synchronized (listeners) {
			if (!listeners.containsKey(listener)) {
				android.content.SharedPreferences.OnSharedPreferenceChangeListener internalListener = (p, key) -> {
					updateMapIfNeeded();
					listener.onChanged(key);
				};
				shared.registerOnSharedPreferenceChangeListener(internalListener);
				listeners.put(listener, internalListener);
			}
		}
	}

	public void unregister(Listener listener) {
		synchronized (listeners) {
			android.content.SharedPreferences.OnSharedPreferenceChangeListener
					internalListener = listeners.remove(listener);
			if (internalListener != null) {
				shared.unregisterOnSharedPreferenceChangeListener(internalListener);
			}
		}
	}
}
