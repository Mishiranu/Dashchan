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

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;

import com.mishiranu.dashchan.C;

public class HiddenThreadsDatabase implements BaseColumns {
	static final String COLUMN_CHAN_NAME = "chan_name";
	static final String COLUMN_BOARD_NAME = "board_name";
	static final String COLUMN_THREAD_NUMBER = "thread_number";
	static final String COLUMN_HIDDEN = "hidden";

	private static final String[] STATE_COLUMNS = {_ID, COLUMN_HIDDEN};

	private static final HiddenThreadsDatabase INSTANCE = new HiddenThreadsDatabase();

	public static HiddenThreadsDatabase getInstance() {
		return INSTANCE;
	}

	private final SQLiteDatabase mDatabase;

	private HiddenThreadsDatabase() {
		mDatabase = DatabaseHelper.getInstance().getWritableDatabase();
	}

	private String buildWhere(String chanName, String boardName, String threadNumber) {
		return COLUMN_CHAN_NAME + " = \"" + chanName + "\" AND " +
				COLUMN_BOARD_NAME + (boardName == null ? " IS NULL" : " = \"" + boardName + "\"") + " AND " +
				COLUMN_THREAD_NUMBER + " = \"" + threadNumber + "\"";
	}

	public void set(String chanName, String boardName, String threadNumber, boolean hidden) {
		mDatabase.delete(DatabaseHelper.TABLE_HIDDEN_THREADS, buildWhere(chanName, boardName, threadNumber), null);
		ContentValues values = new ContentValues();
		values.put(COLUMN_CHAN_NAME, chanName);
		values.put(COLUMN_BOARD_NAME, boardName);
		values.put(COLUMN_THREAD_NUMBER, threadNumber);
		values.put(COLUMN_HIDDEN, hidden);
		mDatabase.insert(DatabaseHelper.TABLE_HIDDEN_THREADS, null, values);
	}

	public int check(String chanName, String boardName, String threadNumber) {
		Cursor cursor = mDatabase.query(DatabaseHelper.TABLE_HIDDEN_THREADS, STATE_COLUMNS,
				buildWhere(chanName, boardName, threadNumber), null, null, null, null);
		try {
			if (cursor.moveToFirst()) {
				boolean hidden = cursor.getInt(1) != 0;
				return hidden ? C.HIDDEN_TRUE : C.HIDDEN_FALSE;
			}
		} finally {
			cursor.close();
		}
		return C.HIDDEN_UNKNOWN;
	}
}