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

import java.util.Collection;
import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.view.MenuItem;

import chan.content.ChanManager;

import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.async.ReadUpdateTask;
import com.mishiranu.dashchan.content.ForegroundManager;
import com.mishiranu.dashchan.preference.AboutFragment;
import com.mishiranu.dashchan.preference.AutohideFragment;
import com.mishiranu.dashchan.preference.ChanFragment;
import com.mishiranu.dashchan.preference.ChansFragment;
import com.mishiranu.dashchan.preference.ContentsFragment;
import com.mishiranu.dashchan.preference.FavoritesFragment;
import com.mishiranu.dashchan.preference.GeneralFragment;
import com.mishiranu.dashchan.preference.InterfaceFragment;
import com.mishiranu.dashchan.preference.UpdateFragment;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ToastUtils;

public class PreferencesActivity extends PreferenceActivity
{
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		ResourceUtils.applyPreferredTheme(this);
		super.onCreate(savedInstanceState);
		boolean root = getIntent().getExtras() == null;
		boolean hasChans = !ChanManager.getInstance().getAvailableChanNames().isEmpty();
		if (hasChans && root) setTitle(R.string.action_preferences);
		if (hasChans || !root) getActionBar().setDisplayHomeAsUpEnabled(true);
		if (!hasChans && root && savedInstanceState == null) ToastUtils.show(this, R.string.message_no_extensions);
	}
	
	@Override
	protected boolean isValidFragment(String fragmentName)
	{
		return true;
	}
	
	@Override
	public void onBuildHeaders(List<Header> target)
	{
		Collection<String> chanNames = ChanManager.getInstance().getAvailableChanNames();
		Header generalHeader = new Header();
		generalHeader.titleRes = R.string.preference_header_general;
		generalHeader.fragment = GeneralFragment.class.getName();
		target.add(generalHeader);
		if (chanNames.size() == 1)
		{
			Header chanHeader = new Header();
			chanHeader.titleRes = R.string.preference_header_forum;
			chanHeader.fragment = ChanFragment.class.getName();
			target.add(chanHeader);
		}
		else if (chanNames.size() > 1)
		{
			Header chansHeader = new Header();
			chansHeader.titleRes = R.string.preference_header_forums;
			chansHeader.fragment = ChansFragment.class.getName();
			target.add(chansHeader);
		}
		Header interfaceHeader = new Header();
		interfaceHeader.titleRes = R.string.preference_header_interface;
		interfaceHeader.fragment = InterfaceFragment.class.getName();
		target.add(interfaceHeader);
		Header contentsHeader = new Header();
		contentsHeader.titleRes = R.string.preference_header_contents;
		contentsHeader.fragment = ContentsFragment.class.getName();
		target.add(contentsHeader);
		Header favoritesHeader = new Header();
		favoritesHeader.titleRes = R.string.preference_header_favorites;
		favoritesHeader.fragment = FavoritesFragment.class.getName();
		target.add(favoritesHeader);
		Header autohideHeader = new Header();
		autohideHeader.titleRes = R.string.preference_header_autohide;
		autohideHeader.fragment = AutohideFragment.class.getName();
		target.add(autohideHeader);
		Header aboutHeader = new Header();
		aboutHeader.titleRes = R.string.preference_header_about;
		aboutHeader.fragment = AboutFragment.class.getName();
		target.add(aboutHeader);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case android.R.id.home:
			{
				finish();
				break;
			}
		}
		return super.onOptionsItemSelected(item);
	}
	
	@Override
	protected void onResume()
	{
		super.onResume();
		ForegroundManager.register(this);
	}
	
	@Override
	protected void onPause()
	{
		super.onPause();
		ForegroundManager.unregister(this);
	}
	
	public static int checkNewVersions(ReadUpdateTask.UpdateDataMap updateDataMap)
	{
		return UpdateFragment.checkNewVersions(updateDataMap);
	}
	
	public static Intent createUpdateIntent(Context context, ReadUpdateTask.UpdateDataMap updateDataMap)
	{
		return UpdateFragment.createUpdateIntent(context, updateDataMap);
	}
}