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

import java.util.LinkedHashMap;

public class LruCache<K, V> extends LinkedHashMap<K, V>
{
	private static final long serialVersionUID = 1L;
	
	private final RemoveCallback<? super K, ? super V> mCallback;
	private int mMaxEntries;
	
	public LruCache(int maxEntries)
	{
		this(null, maxEntries);
	}
	
	public LruCache(RemoveCallback<? super K, ? super V> callback, int maxEntries)
	{
		super(0, 0.75f, true);
		mCallback = callback;
		setMaxEntries(maxEntries);
	}
	
	public void setMaxEntries(int maxEntries)
	{
		mMaxEntries = maxEntries;
	}
	
	public int getMaxEntries()
	{
		return mMaxEntries;
	}
	
	@Override
	protected boolean removeEldestEntry(Entry<K, V> eldest)
	{
		boolean remove = size() > mMaxEntries;
		if (remove && mCallback != null) mCallback.onRemoveEldestEntry(eldest.getKey(), eldest.getValue());
		return remove;
	}
	
	public static interface RemoveCallback<K, V>
	{
		public void onRemoveEldestEntry(K key, V value);
	}
}