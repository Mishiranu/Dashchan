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

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.Preference;

import chan.content.ChanConfiguration;
import chan.content.ChanManager;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.preference.PreferencesActivity;
import com.mishiranu.dashchan.util.ResourceUtils;

public class ChansFragment extends BasePreferenceFragment
{
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		ChanManager manager = ChanManager.getInstance();
		int color = ResourceUtils.getColor(getActivity(), R.attr.drawerIconColor);
		for (String chanName : manager.getAvailableChanNames())
		{
			Preference preference = makeButton(null, ChanConfiguration.get(chanName).getTitle(), null, false);
			Drawable drawable = manager.getIcon(chanName, color);
			if (drawable != null) preference.setIcon(drawable);
			Intent intent = new Intent(getActivity(), PreferencesActivity.class);
			intent.putExtra(PreferencesActivity.EXTRA_SHOW_FRAGMENT, ChanFragment.class.getName());
			intent.putExtra(PreferencesActivity.EXTRA_NO_HEADERS, true);
			intent.putExtra(C.EXTRA_CHAN_NAME, chanName);
			preference.setIntent(intent);
		}
	}
}