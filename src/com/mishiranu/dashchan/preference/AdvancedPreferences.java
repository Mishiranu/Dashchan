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

package com.mishiranu.dashchan.preference;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;

import org.json.JSONException;
import org.json.JSONObject;

import chan.content.ChanManager;
import chan.util.StringUtils;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.content.MainApplication;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.Log;

public class AdvancedPreferences
{
	private static final HashMap<String, String> USER_AGENTS = new HashMap<>();

	static
	{
		File file = MainApplication.getInstance().getExternalCacheDir();
		if (file != null)
		{
			file = new File(file.getParentFile(), "files/advanced.json");
			if (file.exists())
			{
				String jsonString = null;
				FileInputStream inputStream = null;
				try
				{
					inputStream = new FileInputStream(file);
					ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
					IOUtils.copyStream(inputStream, outputStream);
					jsonString = new String(outputStream.toByteArray(), "UTF-8");
				}
				catch (IOException e)
				{
					Log.persistent().stack(e);
				}
				finally
				{
					IOUtils.close(inputStream);
				}
				if (jsonString != null)
				{
					try
					{
						JSONObject jsonObject = new JSONObject(jsonString);
						JSONObject userAgentObject = jsonObject.optJSONObject("userAgent");
						if (userAgentObject != null)
						{
							for (Iterator<String> keys = userAgentObject.keys(); keys.hasNext();)
							{
								String chanName = keys.next();
								String userAgent = userAgentObject.getString(chanName);
								if (!StringUtils.isEmpty(userAgent)) USER_AGENTS.put(chanName, userAgent);
							}
						}
						else
						{
							String userAgent = jsonObject.optString("userAgent", null);
							if (!StringUtils.isEmpty(userAgent))
							{
								USER_AGENTS.put(ChanManager.EXTENSION_NAME_CLIENT, userAgent);
							}
						}
					}
					catch (JSONException e)
					{
						Log.persistent().stack(e);
					}
				}
			}
		}
	}

	public static String getUserAgent(String chanName)
	{
		String userAgent = USER_AGENTS.get(chanName);
		if (userAgent == null) userAgent = USER_AGENTS.get(ChanManager.EXTENSION_NAME_CLIENT);
		if (userAgent == null) userAgent = C.USER_AGENT;
		return userAgent;
	}
}