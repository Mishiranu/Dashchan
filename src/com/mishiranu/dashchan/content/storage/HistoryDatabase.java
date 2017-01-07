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

import java.util.ArrayList;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;

import chan.util.StringUtils;

public class HistoryDatabase implements BaseColumns {
	static final String COLUMN_CHAN_NAME = "chan_name";
	static final String COLUMN_BOARD_NAME = "board_name";
	static final String COLUMN_THREAD_NUMBER = "thread_number";
	static final String COLUMN_TITLE = "title";
	static final String COLUMN_CREATED = "created";

	private static final String[] ALL_COLUMNS = {_ID, COLUMN_CHAN_NAME, COLUMN_BOARD_NAME, COLUMN_THREAD_NUMBER,
			COLUMN_TITLE, COLUMN_CREATED};

	private static final int HISTORY_SIZE = 200;
	private static final int HISTORY_THRESHOLD = HISTORY_SIZE + 50;

	private static final HistoryDatabase INSTANCE = new HistoryDatabase();

	public static HistoryDatabase getInstance() {
		return INSTANCE;
	}

	private final SQLiteDatabase database;

	private HistoryDatabase() {
		database = DatabaseHelper.getInstance().getWritableDatabase();
	}

	public void addHistory(final String chanName, final String boardName, final String threadNumber,
			final String threadTitle) {
		DatabaseHelper.getInstance().getExecutor().execute(() -> {
			synchronized (HistoryDatabase.this) {
				clearOldHistory(chanName);
				database.delete(DatabaseHelper.TABLE_HISTORY, buildWhere(chanName, boardName, threadNumber), null);
				long currentTime = System.currentTimeMillis();
				ContentValues values = new ContentValues();
				values.put(COLUMN_CHAN_NAME, chanName);
				values.put(COLUMN_BOARD_NAME, boardName);
				values.put(COLUMN_THREAD_NUMBER, threadNumber);
				values.put(COLUMN_TITLE, threadTitle);
				values.put(COLUMN_CREATED, currentTime);
				database.insert(DatabaseHelper.TABLE_HISTORY, null, values);
			}
		});
	}

	public void refreshTitles(final String chanName, final String boardName, final String threadNumber,
			final String threadTitle) {
		if (!StringUtils.isEmpty(threadTitle)) {
			DatabaseHelper.getInstance().getExecutor().execute(() -> {
				synchronized (HistoryDatabase.this) {
					ContentValues values = new ContentValues();
					values.put(COLUMN_TITLE, threadTitle);
					database.update(DatabaseHelper.TABLE_HISTORY, values,
							buildWhere(chanName, boardName, threadNumber), null);
				}
			});
		}
	}

	private String buildWhere(String chanName, String boardName, String threadNumber) {
		return COLUMN_CHAN_NAME + " = \"" + chanName + "\" AND " +
				COLUMN_BOARD_NAME + (boardName == null ? " IS NULL" : " = \"" + boardName + "\"") + " AND " +
				COLUMN_THREAD_NUMBER + " = \"" + threadNumber + "\"";
	}

	private String buildWhere(String chanName) {
		return COLUMN_CHAN_NAME + " = \"" + chanName + "\"";
	}

	public ArrayList<HistoryItem> getAllHistory(String chanName) {
		synchronized (this) {
			int maxCount = chanName != null ? HISTORY_SIZE : 5 * HISTORY_SIZE;
			ArrayList<HistoryItem> historyItems = new ArrayList<>();
			Cursor cursor = database.query(DatabaseHelper.TABLE_HISTORY, ALL_COLUMNS,
					chanName != null ? buildWhere(chanName) : null, null, null,
					null, COLUMN_CREATED + " desc", Integer.toString(maxCount));
			cursor.moveToFirst();
			while (!cursor.isAfterLast()) {
				HistoryItem historyItem = new HistoryItem();
				historyItem.chanName = cursor.getString(1);
				historyItem.boardName = cursor.getString(2);
				historyItem.threadNumber = cursor.getString(3);
				historyItem.title = cursor.getString(4);
				if (historyItem.threadNumber != null) {
					historyItems.add(historyItem);
				}
				historyItem.time = cursor.getLong(5);
				cursor.moveToNext();
			}
			cursor.close();
			return historyItems;
		}
	}

	public boolean remove(String chanName, String boardName, String threadNumber) {
		synchronized (this) {
			return database.delete(DatabaseHelper.TABLE_HISTORY, buildWhere(chanName, boardName,
					threadNumber), null) > 0;
		}
	}

	private void clearOldHistory(String chanName) {
		synchronized (this) {
			String where = buildWhere(chanName);
			Cursor cursor = database.rawQuery("SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_HISTORY + " WHERE " +
					where, null);
			cursor.moveToFirst();
			long count = cursor.getLong(0);
			cursor.close();
			if (count >= HISTORY_THRESHOLD) {
				cursor = database.query(DatabaseHelper.TABLE_HISTORY, ALL_COLUMNS, where, null, null, null,
						COLUMN_CREATED + " desc", HISTORY_SIZE + ", " + (HISTORY_SIZE + 1));
				long id = -1;
				if (cursor.moveToFirst()) {
					id = cursor.getLong(0);
				}
				cursor.close();
				if (id >= 0) {
					database.delete(DatabaseHelper.TABLE_HISTORY, _ID + " <= " + id + " AND " + where, null);
				}
			}
		}
	}

	public void clearAllHistory(String chanName) {
		synchronized (this) {
			database.delete(DatabaseHelper.TABLE_HISTORY, chanName != null ? buildWhere(chanName) : null, null);
		}
	}

	public static class HistoryItem {
		public String chanName;
		public String boardName;
		public String threadNumber;
		public String title;
		public long time;
	}
}