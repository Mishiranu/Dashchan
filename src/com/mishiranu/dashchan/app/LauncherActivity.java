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

package com.mishiranu.dashchan.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import chan.content.ChanManager;

import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.util.NavigationUtils;

/*
 * MainActivity can't be both singleTask and launcher activity, so I use this launcher activity.
 */
public class LauncherActivity extends Activity
{
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		getIntent().setPackage(null);
		String chanName = ChanManager.getInstance().getDefaultChanName();
		if (chanName == null) startActivity(new Intent(this, PreferencesActivity.class)); else
		{
			NavigationUtils.navigateThreads(this, chanName, Preferences.getDefaultBoardName(chanName),
					false, false, false, true);
		}
		finish();
	}
}