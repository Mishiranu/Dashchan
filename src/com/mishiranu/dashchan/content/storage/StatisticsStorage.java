package com.mishiranu.dashchan.content.storage;

import chan.content.Chan;
import chan.content.ChanConfiguration;
import chan.util.StringUtils;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class StatisticsStorage extends StorageManager.Storage<Map<String, StatisticsStorage.StatisticsItem>> {
	private static final String KEY_START_TIME = "startTime";
	private static final String KEY_DATA = "data";
	private static final String KEY_CHAN_NAME = "chanName";
	private static final String KEY_THREADS_VIEWED = "threadsViewed";
	private static final String KEY_POSTS_SENT = "postsSent";
	private static final String KEY_THREADS_CREATED = "threadsCreated";

	private static final StatisticsStorage INSTANCE = new StatisticsStorage();

	public static StatisticsStorage getInstance() {
		return INSTANCE;
	}

	private final HashMap<String, StatisticsItem> statisticsItems = new HashMap<>();
	private long startTime;

	private StatisticsStorage() {
		super("statistics", 1000, 10000);
		JSONObject jsonObject = read();
		if (jsonObject != null) {
			startTime = Math.max(jsonObject.optLong(KEY_START_TIME), 0L);
			JSONArray jsonArray = jsonObject.optJSONArray(KEY_DATA);
			if (jsonArray != null) {
				for (int i = 0; i < jsonArray.length(); i++) {
					jsonObject = jsonArray.optJSONObject(i);
					if (jsonObject != null) {
						String chanName = jsonObject.optString(KEY_CHAN_NAME);
						if (!StringUtils.isEmpty(chanName)) {
							int threadsViewed = Math.max(jsonObject.optInt(KEY_THREADS_VIEWED), 0);
							int postsSent = Math.max(jsonObject.optInt(KEY_POSTS_SENT), 0);
							int threadsCreated = Math.max(jsonObject.optInt(KEY_THREADS_CREATED), 0);
							StatisticsItem statisticsItem = new StatisticsItem(threadsViewed, postsSent,
									threadsCreated);
							statisticsItems.put(chanName, statisticsItem);
						}
					}
				}
			}
		}
	}

	@Override
	public Map<String, StatisticsItem> onClone() {
		return new HashMap<>(statisticsItems);
	}

	@Override
	public JSONObject onSerialize(Map<String, StatisticsItem> statisticsItems) throws JSONException {
		JSONArray jsonArray = new JSONArray();
		for (HashMap.Entry<String, StatisticsItem> entry : statisticsItems.entrySet()) {
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
		putJson(jsonObject, KEY_START_TIME, startTime);
		return jsonObject;
	}

	public static class StatisticsItem {
		public int threadsViewed;
		public int postsSent;
		public int threadsCreated;

		private StatisticsItem(int threadsViewed, int postsSent, int threadsCreated) {
			this.threadsViewed = threadsViewed;
			this.postsSent = postsSent;
			this.threadsCreated = threadsCreated;
		}
	}

	public HashMap<String, StatisticsItem> getItems() {
		return statisticsItems;
	}

	public long getStartTime() {
		return startTime;
	}

	public void convertFromOldFormat(String statistics) {
		JSONObject jsonObject;
		try {
			jsonObject = new JSONObject(statistics);
		} catch (JSONException e) {
			return;
		}
		statisticsItems.clear();
		startTime = 0L;
		for (Iterator<String> iterator = jsonObject.keys(); iterator.hasNext();) {
			String chanName = iterator.next();
			JSONObject statisticsObject = jsonObject.optJSONObject(chanName);
			if (statisticsObject != null) {
				int threadsViewed = statisticsObject.optInt("views");
				int postsSent = statisticsObject.optInt("posts");
				int threadsCreated = statisticsObject.optInt("threads");
				statisticsItems.put(chanName, new StatisticsItem(threadsViewed, postsSent, threadsCreated));
			}
		}
		serialize();
		await(false);
	}

	private StatisticsItem obtainStatisticsItem(String chanName) {
		StatisticsItem statisticsItem = statisticsItems.get(chanName);
		if (statisticsItem == null) {
			if (statisticsItems.isEmpty()) {
				startTime = System.currentTimeMillis();
			}
			statisticsItem = new StatisticsItem(0, 0, 0);
			statisticsItems.put(chanName, statisticsItem);
		}
		return statisticsItem;
	}

	public void incrementThreadsViewed(String chanName) {
		Chan chan = Chan.get(chanName);
		ChanConfiguration.Statistics statistics = chan.configuration.safe().obtainStatistics();
		if (statistics == null || !statistics.threadsViewed) {
			return;
		}
		StatisticsItem statisticsItem = obtainStatisticsItem(chanName);
		statisticsItem.threadsViewed++;
		serialize();
	}

	public void incrementPostsSent(String chanName, boolean newThread) {
		Chan chan = Chan.get(chanName);
		ChanConfiguration.Statistics statistics = chan.configuration.safe().obtainStatistics();
		if (statistics == null || !(statistics.postsSent || statistics.threadsCreated && newThread)) {
			return;
		}
		StatisticsItem statisticsItem = obtainStatisticsItem(chanName);
		if (statistics.postsSent) {
			statisticsItem.postsSent++;
		}
		if (statistics.threadsCreated && newThread) {
			statisticsItem.threadsCreated++;
		}
		serialize();
	}

	public void clear() {
		statisticsItems.clear();
		serialize();
	}
}
