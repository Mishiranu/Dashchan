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

package com.mishiranu.dashchan.content;

import java.util.HashMap;
import java.util.Iterator;

import org.json.JSONException;
import org.json.JSONObject;

import chan.content.ChanConfiguration;

import com.mishiranu.dashchan.preference.Preferences;

public class StatisticsManager
{
	private static final StatisticsManager INSTANCE = new StatisticsManager();

	public static StatisticsManager getInstance()
	{
		return INSTANCE;
	}

	private static final String KEY_VIEWS = "views";
	private static final String KEY_POSTS = "posts";
	private static final String KEY_THREADS = "threads";

	private JSONObject mStatistics;

	public static class StatisticsItem
	{
		public final int threadsViewed;
		public final int postsSent;
		public final int threadsCreated;

		private StatisticsItem(ChanConfiguration.Statistics statistics, int views, int posts, int threads)
		{
			this.threadsViewed = statistics != null && statistics.threadsViewed ? views : -1;
			this.postsSent = statistics != null && statistics.postsSent ? posts : -1;
			this.threadsCreated = statistics != null && statistics.threadsCreated ? threads : -1;
		}
	}

	private StatisticsManager()
	{
		JSONObject jsonObject = Preferences.getStatistics();
		if (jsonObject == null) jsonObject = new JSONObject();
		mStatistics = jsonObject;
	}

	private JSONObject getObjectForChanName(String chanName)
	{
		JSONObject jsonObject = mStatistics.optJSONObject(chanName);
		if (jsonObject == null)
		{
			jsonObject = new JSONObject();
			try
			{
				mStatistics.put(chanName, jsonObject);
			}
			catch (JSONException e)
			{
				throw new RuntimeException(e);
			}
		}
		return jsonObject;
	}

	public void incrementViews(String chanName)
	{
		ChanConfiguration.Statistics statistics = ChanConfiguration.get(chanName).safe().obtainStatistics();
		if (statistics == null || !statistics.threadsViewed) return;
		JSONObject jsonObject = getObjectForChanName(chanName);
		int views = jsonObject.optInt(KEY_VIEWS) + 1;
		try
		{
			jsonObject.put(KEY_VIEWS, views);
		}
		catch (JSONException e)
		{
			throw new RuntimeException(e);
		}
		Preferences.setStatistics(mStatistics);
	}

	public void incrementPosts(String chanName, boolean newThread)
	{
		ChanConfiguration.Statistics statistics = ChanConfiguration.get(chanName).safe().obtainStatistics();
		if (statistics == null) return;
		JSONObject jsonObject = getObjectForChanName(chanName);
		int posts = jsonObject.optInt(KEY_POSTS) + 1;
		int threads = newThread ? jsonObject.optInt(KEY_THREADS) + 1 : -1;
		try
		{
			if (statistics.postsSent) jsonObject.put(KEY_POSTS, posts);
			if (statistics.threadsCreated && newThread) jsonObject.put(KEY_THREADS, threads);
		}
		catch (JSONException e)
		{
			throw new RuntimeException(e);
		}
		Preferences.setStatistics(mStatistics);
	}

	public HashMap<String, StatisticsItem> getItems()
	{
		HashMap<String, StatisticsItem> statisticsItems = new HashMap<>();
		Iterator<String> keys = mStatistics.keys();
		while (keys.hasNext())
		{
			try
			{
				String chanName = keys.next();
				ChanConfiguration.Statistics statistics = ChanConfiguration.get(chanName).safe().obtainStatistics();
				JSONObject jsonObject = mStatistics.getJSONObject(chanName);
				statisticsItems.put(chanName, new StatisticsItem(statistics, jsonObject.optInt(KEY_VIEWS),
						jsonObject.optInt(KEY_POSTS), jsonObject.optInt(KEY_THREADS)));
			}
			catch (JSONException e)
			{
				throw new RuntimeException(e);
			}
		}
		return statisticsItems;
	}

	public void clear()
	{
		mStatistics = new JSONObject();
		Preferences.setStatistics(mStatistics);
	}
}