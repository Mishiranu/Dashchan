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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceGroup;

import chan.content.ChanConfiguration;
import chan.util.StringUtils;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;

public class CookiesFragment extends BasePreferenceFragment implements Comparator<Map.Entry<String, String>> {
	private ChanConfiguration configuration;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		String chanName = getActivity().getIntent().getStringExtra(C.EXTRA_CHAN_NAME);
		if (chanName == null) {
			throw new IllegalStateException();
		}
		configuration = ChanConfiguration.get(chanName);
		getActivity().setTitle(R.string.preference_delete_cookies);
		Map<String, String> cookies = configuration.getCookiesWithDisplayName();

		ArrayList<Map.Entry<String, String>> entries = new ArrayList<>(cookies.entrySet());
		Collections.sort(entries, this);
		for (Map.Entry<String, String> entry : entries) {
			String cookie = configuration.getCookie(entry.getKey());
			Preference preference = makeButton(null, entry.getValue(), cookie, false);
			preference.setOnPreferenceClickListener(this);
			preference.setKey(entry.getKey());
			preference.setPersistent(false);
		}
	}

	@Override
	public int compare(Map.Entry<String, String> lhs, Map.Entry<String, String> rhs) {
		return StringUtils.compare(lhs.getKey(), rhs.getKey(), true);
	}

	@Override
	public boolean onPreferenceClick(Preference preference) {
		preference.setEnabled(false);
		preference.setSummary(null);
		configuration.storeCookie(preference.getKey(), null, null);
		configuration.commit();
		PreferenceGroup preferenceGroup = getParentGroup(preference);
		if (preference != null) {
			preferenceGroup.removePreference(preference);
			if (preferenceGroup.getPreferenceCount() == 0) {
				getActivity().finish();
			}
		}
		return true;
	}
}