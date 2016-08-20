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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

public class UpdaterActivity extends StateActivity
{
	private static final String EXTRA_URI_STRINGS = "uriStrings";
	private static final String EXTRA_DOWNLOAD_IDS = "downloadIds";
	
	private static final String EXTRA_INDEX = "index";
	
	private DownloadManager mDownloadManager;
	
	private ArrayList<String> mUriStrings;
	private HashMap<String, Long> mDownloadIds;
	private int mIndex = 0;
	
	@SuppressWarnings("unchecked")
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		mDownloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
		mUriStrings = getIntent().getStringArrayListExtra(EXTRA_URI_STRINGS);
		mDownloadIds = (HashMap<String, Long>) getIntent().getSerializableExtra(EXTRA_DOWNLOAD_IDS);
		if (savedInstanceState == null) performInstallation();
		else mIndex = savedInstanceState.getInt(EXTRA_INDEX);
	}
	
	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		super.onSaveInstanceState(outState);
		outState.putInt(EXTRA_INDEX, mIndex);
	}
	
	private void performInstallation()
	{
		if (mUriStrings != null && mUriStrings.size() > mIndex)
		{
			Uri uri = Uri.parse(mUriStrings.get(mIndex));
			startActivityForResult(new Intent(Intent.ACTION_INSTALL_PACKAGE).setData(uri)
					.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true).putExtra(Intent.EXTRA_RETURN_RESULT, true), 0);
		}
		else finish();
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		if (requestCode == 0)
		{
			if (resultCode == RESULT_OK)
			{
				long id = mDownloadIds.get(mUriStrings.get(mIndex));
				mDownloadManager.remove(id);
				mIndex++;
				performInstallation();
			}
			else finish();
		}
	}
	
	private static BroadcastReceiver sUpdatesReceiver;
	
	public static void initUpdater(long clientId, Collection<Long> ids)
	{
		Context context = MainApplication.getInstance();
		if (sUpdatesReceiver != null) context.unregisterReceiver(sUpdatesReceiver);
		HashSet<Long> newIds = new HashSet<>(ids);
		if (clientId >= 0) newIds.add(clientId);
		sUpdatesReceiver = new BroadcastReceiver()
		{
			private final HashMap<String, Long> mDownloadIds = new HashMap<>();
			private final ArrayList<String> mUriStrings = new ArrayList<>();
			private String mClientUriString = null;
			
			@Override
			public void onReceive(Context context, Intent intent)
			{
				if (this != sUpdatesReceiver) return;
				long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
				if (id >= 0 && newIds.remove(id))
				{
					boolean success = false;
					DownloadManager downloadManager = (DownloadManager) context.getSystemService(DOWNLOAD_SERVICE);
					Cursor cursor = downloadManager.query(new DownloadManager.Query().setFilterById(id));
					if (cursor != null)
					{
						try
						{
							if (cursor.moveToFirst())
							{
								int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
								if (status == DownloadManager.STATUS_SUCCESSFUL)
								{
									String uriString = cursor.getString(cursor.getColumnIndexOrThrow
											(DownloadManager.COLUMN_LOCAL_URI));
									if (uriString != null)
									{
										if (id == clientId) mClientUriString = uriString;
										else mUriStrings.add(uriString);
										mDownloadIds.put(uriString, id);
										success = true;
									}
								}
							}
						}
						catch (IllegalArgumentException e)
						{
							// Thrown by getColumnIndexOrThrow
						}
						finally
						{
							cursor.close();
						}
					}
					boolean unregister = false;
					if (success)
					{
						if (newIds.isEmpty())
						{
							if (mClientUriString != null) mUriStrings.add(mClientUriString);
							context.startActivity(new Intent(context, UpdaterActivity.class)
									.putStringArrayListExtra(EXTRA_URI_STRINGS, mUriStrings)
									.putExtra(EXTRA_DOWNLOAD_IDS, mDownloadIds)
									.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
							unregister = true;
						}
					}
					else unregister = true;
					if (unregister)
					{
						MainApplication.getInstance().unregisterReceiver(sUpdatesReceiver);
						sUpdatesReceiver = null;
					}
				}
			}
		};
		context.registerReceiver(sUpdatesReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
	}
}