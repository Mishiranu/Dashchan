/*
 * Copyright 2014-2016 Fukurou Mishiranu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mishiranu.dashchan.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;

public class LruCache<K, V> extends LinkedHashMap<K, V> {
	private static final long serialVersionUID = 1L;

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
		public void onRemoveEntry(K key, V value);
	}
}