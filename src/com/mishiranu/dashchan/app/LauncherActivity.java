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

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.ContextThemeWrapper;

import chan.content.ChanManager;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.util.NavigationUtils;

/*
 * MainActivity can't be both singleTask and launcher activity, so I use this launcher activity.
 */
public class LauncherActivity extends Activity
{
	private static final int STATE_START = 0;
	private static final int STATE_PERMISSION_REQUEST = 1;
	
	private static final String EXTRA_STATE = "state";
	
	private int mState;
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		getIntent().setPackage(null);
		mState = STATE_START;
		if (savedInstanceState != null) mState = savedInstanceState.getInt(EXTRA_STATE, mState);
		switch (mState)
		{
			case STATE_START: navigatePermissionRequest(); break;
			case STATE_PERMISSION_REQUEST: break;
		}
	}
	
	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		super.onSaveInstanceState(outState);
		outState.putInt(EXTRA_STATE, mState);
	}
	
	@TargetApi(Build.VERSION_CODES.M)
	private void navigatePermissionRequest()
	{
		if (C.API_MARSHMALLOW)
		{
			if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
					!= PackageManager.PERMISSION_GRANTED)
			{
				Context context = new ContextThemeWrapper(this, Preferences.getThemeResource());
				new AlertDialog.Builder(context).setMessage(R.string.message_memory_access_permission)
						.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
				{
					@Override
					public void onClick(DialogInterface dialog, int which)
					{
						mState = STATE_PERMISSION_REQUEST;
						requestPermissions(new String[] {android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
					}
				}).setOnCancelListener(new DialogInterface.OnCancelListener()
				{
					@Override
					public void onCancel(DialogInterface dialog)
					{
						finish();
					}
				}).show();
				return;
			}
		}
		navigateMainActivity();
	}
	
	private void navigateMainActivity()
	{
		String chanName = ChanManager.getInstance().getDefaultChanName();
		if (chanName == null) startActivity(new Intent(this, PreferencesActivity.class)); else
		{
			NavigationUtils.navigateThreads(this, chanName, Preferences.getDefaultBoardName(chanName),
					false, false, false, true);
		}
		finish();
	}
	
	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
	{
		if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) navigateMainActivity();
		else finish();
	}
}