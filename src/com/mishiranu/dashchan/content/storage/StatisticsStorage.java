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

package com.mishiranu.dashchan.content.storage;

import java.util.HashMap;
import java.util.Iterator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import chan.content.ChanConfiguration;
import chan.util.StringUtils;

public class StatisticsStorage extends StorageManager.Storage
{
	private static final String KEY_START_TIME = "startTime";
	private static final String KEY_DATA = "data";
	private static final String KEY_CHAN_NAME = "chanName";
	private static final String KEY_THREADS_VIEWED = "threadsViewed";
	private static final String KEY_POSTS_SENT = "postsSent";
	private static final String KEY_THREADS_CREATED = "threadsCreated";

	private static final StatisticsStorage INSTANCE = new StatisticsStorage();

	public static StatisticsStorage getInstance()
	{
		return INSTANCE;
	}

	private final HashMap<String, StatisticsItem> mStatisticsItems = new HashMap<>();
	private long mStartTime;

	private StatisticsStorage()
	{
		super("statistics", 1000, 10000);
		JSONObject jsonObject = read();
		if (jsonObject != null)
		{
			mStartTime = Math.max(jsonObject.optLong(KEY_START_TIME), 0L);
			JSONArray jsonArray = jsonObject.optJSONArray(KEY_DATA);
			if (jsonArray != null)
			{
				for (int i = 0; i < jsonArray.length(); i++)
				{
					jsonObject = jsonArray.optJSONObject(i);
					if (jsonObject != null)
					{
						String chanName = jsonObject.optString(KEY_CHAN_NAME);
						if (!StringUtils.isEmpty(chanName))
						{
							int threadsViewed = Math.max(jsonObject.optInt(KEY_THREADS_VIEWED), 0);
							int postsSent = Math.max(jsonObject.optInt(KEY_POSTS_SENT), 0);
							int threadsCreated = Math.max(jsonObject.optInt(KEY_THREADS_CREATED), 0);
							StatisticsItem statisticsItem = new StatisticsItem(threadsViewed, postsSent,
									threadsCreated);
							mStatisticsItems.put(chanName, statisticsItem);
						}
					}
				}
			}
		}
	}

	@Override
	public Object onClone()
	{
		return new HashMap<>(mStatisticsItems);
	}

	@Override
	public JSONObject onSerialize(Object data) throws JSONException
	{
		@SuppressWarnings("unchecked")
		HashMap<String, StatisticsItem> statisticsItems = (HashMap<String, StatisticsItem>) data;
		JSONArray jsonArray = new JSONArray();
		for (HashMap.Entry<String, StatisticsItem> entry : statisticsItems.entrySet())
		{
			StatisticsItem statisticsItem = entry.getValue();
			JSONObject jsonObject = new JSONObject();
			putJson(jsonObject, KEY_CHAN_NAME, entry.getKey());
			putJson(jsonObject, KEY_THREADS_VIEWED, statisticsItem.threadsViewed);
			putJson(jsonObject, KEY_POSTS_SENT, statisticsItem.postsSent);
			putJson(jsonObject, KEY_THREADS_CREATED, statisticsItem.threadsCreated);
			jsonArray.put(jsonObject);
		}
		JSONObject jsonObject = new JSONObject();
		jsonObject.put(KEY_DATA, jsonArray);
		putJson(jsonObject, KEY_START_TIME, mStartTime);
		return jsonObject;
	}

	public static class StatisticsItem
	{
		public int threadsViewed;
		public int postsSent;
		public int threadsCreated;

		private StatisticsItem(int threadsViewed, int postsSent, int threadsCreated)
		{
			this.threadsViewed = threadsViewed;
			this.postsSent = postsSent;
			this.threadsCreated = threadsCreated;
		}
	}

	public HashMap<String, StatisticsItem> getItems()
	{
		return mStatisticsItems;
	}

	public long getStartTime()
	{
		return mStartTime;
	}

	public void convertFromOldFormat(String statistics)
	{
		JSONObject jsonObject;
		try
		{
			jsonObject = new JSONObject(statistics);
		}
		catch (JSONException e)
		{
			return;
		}
		mStatisticsItems.clear();
		mStartTime = 0L;
		for (Iterator<String> iterator = jsonObject.keys(); iterator.hasNext();)
		{
			String chanName = iterator.next();
			JSONObject statisticsObject = jsonObject.optJSONObject(chanName);
			if (statisticsObject != null)
			{
				int threadsViewed = statisticsObject.optInt("views");
				int postsSent = statisticsObject.optInt("posts");
				int threadsCreated = statisticsObject.optInt("threads");
				mStatisticsItems.put(chanName, new StatisticsItem(threadsViewed, postsSent, threadsCreated));
			}
		}
		serialize();
		await(false);
	}

	private StatisticsItem obtainStatisticsItem(String chanName)
	{
		StatisticsItem statisticsItem = mStatisticsItems.get(chanName);
		if (statisticsItem == null)
		{
			if (mStatisticsItems.isEmpty()) mStartTime = System.currentTimeMillis();
			statisticsItem = new StatisticsItem(0, 0, 0);
			mStatisticsItems.put(chanName, statisticsItem);
		}
		return statisticsItem;
	}

	public void incrementThreadsViewed(String chanName)
	{
		ChanConfiguration.Statistics statistics = ChanConfiguration.get(chanName).safe().obtainStatistics();
		if (statistics == null || !statistics.threadsViewed) return;
		StatisticsItem statisticsItem = obtainStatisticsItem(chanName);
		statisticsItem.threadsViewed++;
		serialize();
	}

	public void incrementPostsSent(String chanName, boolean newThread)
	{
		ChanConfiguration.Statistics statistics = ChanConfiguration.get(chanName).safe().obtainStatistics();
		if (statistics == null || !(statistics.postsSent || statistics.threadsCreated && newThread)) return;
		StatisticsItem statisticsItem = obtainStatisticsItem(chanName);
		if (statistics.postsSent) statisticsItem.postsSent++;
		if (statistics.threadsCreated && newThread) statisticsItem.threadsCreated++;
		serialize();
	}

	public void clear()
	{
		mStatisticsItems.clear();
		serialize();
	}
}