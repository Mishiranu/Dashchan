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

package com.mishiranu.dashchan.content.async;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;

import chan.content.ChanLocator;
import chan.content.ChanManager;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;
import chan.util.CommonUtils;

import com.mishiranu.dashchan.C;

public class ReadUpdateTask extends HttpHolderTask<Void, Long, Object>
{
	private final Context mContext;
	private final Callback mCallback;

	public static class UpdateDataMap implements Serializable
	{
		private static final long serialVersionUID = 1L;

		private final HashMap<String, ArrayList<ReadUpdateTask.UpdateItem>> mMap = new HashMap<>();

		public ArrayList<ReadUpdateTask.UpdateItem> get(String extensionName)
		{
			return mMap.get(extensionName);
		}

		public Iterable<String> extensionNames()
		{
			return mMap.keySet();
		}
	}

	public static class UpdateItem implements Serializable
	{
		private static final long serialVersionUID = 1L;

		public final String title;
		public final String name;
		public final int code;
		public final int minVersion;
		public final int version;
		public final long length;
		public final String source;
		public final boolean ignoreVersion;

		public UpdateItem(String title, String name, int code, int version, long length, String source,
				boolean ignoreVersion)
		{
			this.title = title;
			this.name = name;
			this.code = code;
			minVersion = -1;
			this.version = version;
			this.length = length;
			this.source = source;
			this.ignoreVersion = ignoreVersion;
		}

		public UpdateItem(String title, String name, int code, int minVersion, int maxVersion, long length,
				String source)
		{
			this.title = title;
			this.name = name;
			this.code = code;
			this.minVersion = minVersion;
			version = maxVersion;
			this.length = length;
			this.source = source;
			ignoreVersion = false;
		}
	}

	public interface Callback
	{
		public void onReadUpdateComplete(UpdateDataMap updateDataMap);
	}

	public ReadUpdateTask(Context context, Callback callback)
	{
		mContext = context.getApplicationContext();
		mCallback = callback;
	}

	public static File getDownloadDirectory(Context context)
	{
		String dirType = "updates";
		File directory = context.getExternalFilesDir(dirType);
		if (directory != null) directory.mkdirs();
		return directory;
	}

	private static Uri normalizeUri(Uri uri, Uri base)
	{
		boolean noScheme = uri.getScheme() == null;
		boolean noHost = uri.getHost() == null;
		if (noScheme || noHost)
		{
			Uri.Builder redirectUriBuilder = uri.buildUpon();
			if (noScheme) redirectUriBuilder.scheme(base.getScheme());
			if (noHost) redirectUriBuilder.authority(base.getHost());
			uri = redirectUriBuilder.build();
		}
		return uri;
	}

	@Override
	protected Object doInBackground(HttpHolder holder, Void... params)
	{
		long timeThreshold = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000; // One week left
		File directory = getDownloadDirectory(mContext);
		if (directory == null) return null;
		File[] files = directory.listFiles();
		if (files != null)
		{
			for (File file : files)
			{
				if (file.lastModified() < timeThreshold) file.delete();
			}
		}
		ChanLocator locator = ChanLocator.getDefault();
		LinkedHashMap<Uri, ArrayList<String>> targets = new LinkedHashMap<>();
		Uri appUri = locator.setScheme(Uri.parse(C.UPDATE_SOURCE_URI_STRING));
		ArrayList<String> extensionNames = new ArrayList<>();
		extensionNames.add(ChanManager.EXTENSION_NAME_CLIENT);
		targets.put(appUri, extensionNames);
		Collection<ChanManager.ExtensionItem> extensionItems = ChanManager.getInstance().getExtensionItems();
		for (ChanManager.ExtensionItem extensionItem : extensionItems)
		{
			if (extensionItem.updateUri != null)
			{
				Uri uri = locator.setScheme(extensionItem.updateUri);
				extensionNames = targets.get(uri);
				if (extensionNames == null)
				{
					extensionNames = new ArrayList<>();
					targets.put(uri, extensionNames);
				}
				extensionNames.add(extensionItem.extensionName);
			}
		}
		String appVersionName;
		int appVersionCode;
		try
		{
			PackageInfo packageInfo = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0);
			appVersionName = packageInfo.versionName;
			appVersionCode = packageInfo.versionCode;
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
		UpdateDataMap updateDataMap = new UpdateDataMap();
		ArrayList<UpdateItem> updateItems = new ArrayList<>();
		updateItems.add(new UpdateItem(null, appVersionName, appVersionCode, ChanManager.MIN_VERSION,
				ChanManager.MAX_VERSION, -1, null));
		updateDataMap.mMap.put(ChanManager.EXTENSION_NAME_CLIENT, updateItems);
		for (ChanManager.ExtensionItem extensionItem : extensionItems)
		{
			updateItems = new ArrayList<>();
			updateItems.add(new UpdateItem(null, extensionItem.packageInfo.versionName,
					extensionItem.packageInfo.versionCode, extensionItem.version, -1, null,
					extensionItem.isLibExtension));
			updateDataMap.mMap.put(extensionItem.extensionName, updateItems);
		}
		Thread thread = Thread.currentThread();
		if (thread.isInterrupted()) return null;
		for (LinkedHashMap.Entry<Uri, ArrayList<String>> target : targets.entrySet())
		{
			try
			{
				Uri uri = target.getKey();
				int redirects = 0;
				while (redirects++ < 5)
				{
					JSONObject jsonObject = new HttpRequest(uri, holder).read().getJsonObject();
					if (jsonObject != null)
					{
						String redirect = CommonUtils.optJsonString(jsonObject, "redirect");
						if (redirect != null)
						{
							uri = normalizeUri(Uri.parse(redirect), uri);
							continue;
						}
						Iterator<String> keys = jsonObject.keys();
						while (keys.hasNext())
						{
							String extensionName = keys.next();
							if (target.getValue().contains(extensionName))
							{
								updateItems = updateDataMap.get(extensionName);
								boolean ignoreVersion = updateItems.get(0).ignoreVersion;
								JSONArray jsonArray = jsonObject.getJSONArray(extensionName);
								for (int i = 0; i < jsonArray.length(); i++)
								{
									JSONObject chanObject = jsonArray.getJSONObject(i);
									int minSdk = chanObject.optInt("minSdk");
									if (minSdk > Build.VERSION.SDK_INT) continue;
									String title = CommonUtils.getJsonString(chanObject, "title");
									String name = CommonUtils.getJsonString(chanObject, "name");
									int code = chanObject.getInt("code");
									if (code < updateItems.get(0).code) continue;
									long length = chanObject.getLong("length");
									String source = CommonUtils.getJsonString(chanObject, "source");
									if (source == null) break;
									Uri sourceUri = normalizeUri(Uri.parse(source), uri);
									source = sourceUri.toString();
									UpdateItem updateItem;
									if (ChanManager.EXTENSION_NAME_CLIENT.equals(extensionName))
									{
										int minVersion = chanObject.getInt("minVersion");
										int maxVersion = chanObject.getInt("maxVersion");
										updateItem = new UpdateItem(title, name, code,
												minVersion, maxVersion, length, source);
									}
									else
									{
										int version = chanObject.optInt("version");
										updateItem = new UpdateItem(title, name, code, version, length,
												source, ignoreVersion);
									}
									updateItems.add(updateItem);
								}
							}
						}
					}
					break;
				}
			}
			catch (HttpException | JSONException e)
			{

			}
			if (thread.isInterrupted()) return null;
		}
		return updateDataMap;
	}

	@Override
	protected void onPostExecute(Object result)
	{
		mCallback.onReadUpdateComplete((UpdateDataMap) result);
	}
}