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

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.res.Configuration;

import chan.content.ChanManager;
import chan.http.HttpClient;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.content.CacheManager;
import com.mishiranu.dashchan.content.storage.DatabaseHelper;
import com.mishiranu.dashchan.media.VideoPlayer;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.util.Log;

public class MainApplication extends android.app.Application
{
	private static MainApplication sInstance;
	
	public MainApplication()
	{
		sInstance = this;
	}
	
	@Override
	public void onCreate()
	{
		super.onCreate();
		Log.init(this);
		// Init
		ChanManager.getInstance();
		HttpClient.getInstance();
		DatabaseHelper.getInstance();
		CacheManager.getInstance();
		Preferences.applyLocale(this);
		if (Preferences.isUseVideoPlayer()) VideoPlayer.loadLibraries(this);
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig)
	{
		super.onConfigurationChanged(newConfig);
		if (newConfig.locale != null) Preferences.applyLocale(this);
	}
	
	public static MainApplication getInstance()
	{
		return sInstance;
	}
	
	@SuppressLint("NewApi")
	public boolean isLowRam()
	{
		if (C.API_KITKAT)
		{
			ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
			return activityManager != null && activityManager.isLowRamDevice();
		}
		else return Runtime.getRuntime().maxMemory() <= 64 * 1024 * 1024;
	}
}