/*
 * Copyright 2016-2017 Fukurou Mishiranu
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

package com.mishiranu.dashchan.preference;

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

import com.mishiranu.dashchan.content.MainApplication;
import com.mishiranu.dashchan.ui.StateActivity;

public class UpdaterActivity extends StateActivity {
	private static final String EXTRA_URI_STRINGS = "uriStrings";
	private static final String EXTRA_DOWNLOAD_IDS = "downloadIds";

	private static final String EXTRA_INDEX = "index";

	private DownloadManager downloadManager;

	private ArrayList<String> uriStrings;
	private HashMap<String, Long> downloadIds;
	private int index = 0;

	@SuppressWarnings("unchecked")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
		uriStrings = getIntent().getStringArrayListExtra(EXTRA_URI_STRINGS);
		downloadIds = (HashMap<String, Long>) getIntent().getSerializableExtra(EXTRA_DOWNLOAD_IDS);
		if (savedInstanceState == null) {
			performInstallation();
		} else {
			index = savedInstanceState.getInt(EXTRA_INDEX);
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt(EXTRA_INDEX, index);
	}

	private void performInstallation() {
		if (uriStrings != null && uriStrings.size() > index) {
			Uri uri = Uri.parse(uriStrings.get(index));
			startActivityForResult(new Intent(Intent.ACTION_INSTALL_PACKAGE).setData(uri)
					.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true).putExtra(Intent.EXTRA_RETURN_RESULT, true), 0);
		} else {
			finish();
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == 0) {
			if (resultCode == RESULT_OK) {
				long id = downloadIds.get(uriStrings.get(index));
				downloadManager.remove(id);
				index++;
				performInstallation();
			} else {
				finish();
			}
		}
	}

	private static BroadcastReceiver updatesReceiver;

	public static void initUpdater(long clientId, Collection<Long> ids) {
		Context context = MainApplication.getInstance();
		if (updatesReceiver != null) {
			context.unregisterReceiver(updatesReceiver);
		}
		HashSet<Long> newIds = new HashSet<>(ids);
		if (clientId >= 0) {
			newIds.add(clientId);
		}
		updatesReceiver = new BroadcastReceiver() {
			private final HashMap<String, Long> downloadIds = new HashMap<>();
			private final ArrayList<String> uriStrings = new ArrayList<>();
			private String clientUriString = null;

			@Override
			public void onReceive(Context context, Intent intent) {
				if (this != updatesReceiver) {
					return;
				}
				long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
				if (id >= 0 && newIds.remove(id)) {
					boolean success = false;
					DownloadManager downloadManager = (DownloadManager) context.getSystemService(DOWNLOAD_SERVICE);
					Cursor cursor = downloadManager.query(new DownloadManager.Query().setFilterById(id));
					if (cursor != null) {
						try {
							if (cursor.moveToFirst()) {
								int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
								if (status == DownloadManager.STATUS_SUCCESSFUL) {
									String uriString = cursor.getString(cursor.getColumnIndexOrThrow
											(DownloadManager.COLUMN_LOCAL_URI));
									if (uriString != null) {
										if (id == clientId) {
											clientUriString = uriString;
										} else {
											uriStrings.add(uriString);
										}
										downloadIds.put(uriString, id);
										success = true;
									}
								}
							}
						} catch (IllegalArgumentException e) {
							// Thrown by getColumnIndexOrThrow
						} finally {
							cursor.close();
						}
					}
					boolean unregister = false;
					if (success) {
						if (newIds.isEmpty()) {
							if (clientUriString != null) {
								uriStrings.add(clientUriString);
							}
							context.startActivity(new Intent(context, UpdaterActivity.class)
									.putStringArrayListExtra(EXTRA_URI_STRINGS, uriStrings)
									.putExtra(EXTRA_DOWNLOAD_IDS, downloadIds)
									.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
							unregister = true;
						}
					} else {
						unregister = true;
					}
					if (unregister) {
						MainApplication.getInstance().unregisterReceiver(updatesReceiver);
						updatesReceiver = null;
					}
				}
			}
		};
		context.registerReceiver(updatesReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
	}
}