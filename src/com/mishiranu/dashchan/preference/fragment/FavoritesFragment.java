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

package com.mishiranu.dashchan.preference.fragment;

import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;

import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.preference.Preferences;

public class FavoritesFragment extends BasePreferenceFragment
{
	private ListPreference mFavoritesOrderPreference;
	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		mFavoritesOrderPreference = makeList(null, Preferences.KEY_FAVORITES_ORDER, Preferences.VALUES_FAVORITES_ORDER,
				Preferences.DEFAULT_FAVORITES_ORDER, R.string.preference_favorites_order,
				R.array.preference_favorites_order_choices);
		makeCheckBox(null, true, Preferences.KEY_FAVORITE_ON_REPLY, Preferences.DEFAULT_FAVORITE_ON_REPLY,
				R.string.preference_favorite_on_reply, 0);
		
		PreferenceCategory watcherCategory = makeCategory(R.string.preference_category_watcher);
		makeCheckBox(watcherCategory, true, Preferences.KEY_WATCHER_REFRESH_PERIODICALLY,
				Preferences.DEFAULT_WATCHER_REFRESH_PERIODICALLY, R.string.preference_watcher_refresh_periodically, 0);
		makeSeekBar(watcherCategory, Preferences.KEY_WATCHER_REFRESH_INTERVAL,
				Preferences.DEFAULT_WATCHER_REFRESH_INTERVAL, R.string.preference_watcher_refresh_interval,
				R.string.preference_watcher_refresh_interval_summary_format, 15, 60, 5, 1f);
		makeCheckBox(watcherCategory, true, Preferences.KEY_WATCHER_WIFI_ONLY, Preferences.DEFAULT_WATCHER_WIFI_ONLY,
				R.string.preference_watcher_wifi_only, 0);
		makeCheckBox(watcherCategory, true, Preferences.KEY_WATCHER_WATCH_INITIALLY,
				Preferences.DEFAULT_WATCHER_WATCH_INITIALLY, R.string.preference_watcher_watch_initially,
				R.string.preference_watcher_watch_initially_summary);
		
		addDependency(Preferences.KEY_WATCHER_REFRESH_INTERVAL, Preferences.KEY_WATCHER_REFRESH_PERIODICALLY, true);
		addDependency(Preferences.KEY_WATCHER_WIFI_ONLY, Preferences.KEY_WATCHER_REFRESH_PERIODICALLY, true);
	}
	
	@Override
	public void onPreferenceAfterChange(Preference preference)
	{
		super.onPreferenceAfterChange(preference);
		if (preference == mFavoritesOrderPreference)
		{
			FavoritesStorage.getInstance().sortIfNeeded();
		}
	}
}