package com.mishiranu.dashchan.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;

public class LruCache<K, V> extends LinkedHashMap<K, V> {
	private final RemoveCallback<? super K, ? super V> callback;
	private int maxEntries;

	private boolean callRemove;
	private Entry<K, V> callRemoveEntry;

	public LruCache(int maxEntries) {
		this(maxEntries, null);
	}

	public LruCache(int maxEntries, RemoveCallback<? super K, ? super V> callback) {
		super(0, 0.75f, true);
		this.callback = callback;
		setMaxEntries(maxEntries);
	}

	public void setMaxEntries(int maxEntries) {
		this.maxEntries = maxEntries;
	}

	@Override
	public V put(K key, V value) {
		V oldValue = super.put(key, value);
		if (callback != null) {
			boolean callRemove = this.callRemove;
			Entry<K, V> callRemoveEntry = this.callRemoveEntry;
			this.callRemove = false;
			this.callRemoveEntry = null;
			if (oldValue != null) {
				callback.onRemoveEntry(key, oldValue);
			}
			if (callRemove) {
				callback.onRemoveEntry(callRemoveEntry.getKey(), callRemoveEntry.getValue());
			}
		}
		return oldValue;
	}

	@SuppressWarnings("unchecked")
	@Override
	public V remove(Object key) {
		V result = super.remove(key);
		if (result != null && callback != null) {
			callback.onRemoveEntry((K) key, result);
		}
		return result;
	}

	@Override
	public void clear() {
		ArrayList<Entry<K, V>> entries = null;
		if (callback != null && !isEmpty()) {
			entries = new ArrayList<>(entrySet());
		}
		super.clear();
		if (entries != null) {
			for (Entry<K, V> entry : entries) {
				callback.onRemoveEntry(entry.getKey(), entry.getValue());
			}
		}
	}

	@Override
	protected boolean removeEldestEntry(Entry<K, V> eldest) {
		boolean remove = size() > maxEntries;
		if (remove) {
			callRemove = true;
			callRemoveEntry = eldest;
			return true;
		}
		return false;
	}

	public interface RemoveCallback<K, V> {
		void onRemoveEntry(K key, V value);
	}
}
