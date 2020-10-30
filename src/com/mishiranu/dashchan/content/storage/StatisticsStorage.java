package com.mishiranu.dashchan.content.storage;

import chan.content.Chan;
import chan.content.ChanConfiguration;
import chan.text.JsonSerial;
import chan.text.ParseException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

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
		startRead();
	}

	@Override
	public Map<String, StatisticsItem> onClone() {
		return new HashMap<>(statisticsItems);
	}

	@Override
	public void onRead(InputStream input) throws IOException {
		try {
			JsonSerial.Reader reader = JsonSerial.reader(input);
			reader.startObject();
			while (!reader.endStruct()) {
				switch (reader.nextName()) {
					case KEY_DATA: {
						reader.startArray();
						while (!reader.endStruct()) {
							String chanName = null;
							int threadsViewed = 0;
							int postsSent = 0;
							int threadsCreated = 0;
							reader.startObject();
							while (!reader.endStruct()) {
								switch (reader.nextName()) {
									case KEY_CHAN_NAME: {
										chanName = reader.nextString();
										break;
									}
									case KEY_THREADS_VIEWED: {
										threadsViewed = reader.nextInt();
										break;
									}
									case KEY_POSTS_SENT: {
										postsSent = reader.nextInt();
										break;
									}
									case KEY_THREADS_CREATED: {
										threadsCreated = reader.nextInt();
										break;
									}
									default: {
										reader.skip();
										break;
									}
								}
							}
							statisticsItems.put(chanName, new StatisticsItem(threadsViewed,
									postsSent, threadsCreated));
						}
						break;
					}
					case KEY_START_TIME: {
						startTime = Math.max(reader.nextLong(), 0L);
						break;
					}
					default: {
						reader.skip();
						break;
					}
				}
			}
		} catch (ParseException e) {
			throw new IOException(e);
		}
	}

	@Override
	public void onWrite(Map<String, StatisticsItem> statisticsItems, OutputStream output) throws IOException {
		JsonSerial.Writer writer = JsonSerial.writer(output);
		writer.startObject();
		writer.name(KEY_DATA);
		writer.startArray();
		for (HashMap.Entry<String, StatisticsItem> entry : statisticsItems.entrySet()) {
			StatisticsItem statisticsItem = entry.getValue();
			writer.startObject();
			writer.name(KEY_CHAN_NAME);
			writer.value(entry.getKey());
			writer.name(KEY_THREADS_VIEWED);
			writer.value(statisticsItem.threadsViewed);
			writer.name(KEY_POSTS_SENT);
			writer.value(statisticsItem.postsSent);
			writer.name(KEY_THREADS_CREATED);
			writer.value(statisticsItem.threadsCreated);
			writer.endObject();
		}
		writer.endArray();
		writer.name(KEY_START_TIME);
		writer.value(startTime);
		writer.endObject();
		writer.flush();
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
