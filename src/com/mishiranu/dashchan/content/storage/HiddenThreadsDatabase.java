package com.mishiranu.dashchan.content.storage;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;
import com.mishiranu.dashchan.content.model.PostItem;

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

	private final SQLiteDatabase database;

	private HiddenThreadsDatabase() {
		database = DatabaseHelper.getInstance().getWritableDatabase();
	}

	private String buildWhere(String chanName, String boardName, String threadNumber) {
		return COLUMN_CHAN_NAME + " = \"" + chanName + "\" AND " +
				COLUMN_BOARD_NAME + (boardName == null ? " IS NULL" : " = \"" + boardName + "\"") + " AND " +
				COLUMN_THREAD_NUMBER + " = \"" + threadNumber + "\"";
	}

	public void set(String chanName, String boardName, String threadNumber, boolean hidden) {
		database.delete(DatabaseHelper.TABLE_HIDDEN_THREADS, buildWhere(chanName, boardName, threadNumber), null);
		ContentValues values = new ContentValues();
		values.put(COLUMN_CHAN_NAME, chanName);
		values.put(COLUMN_BOARD_NAME, boardName);
		values.put(COLUMN_THREAD_NUMBER, threadNumber);
		values.put(COLUMN_HIDDEN, hidden);
		database.insert(DatabaseHelper.TABLE_HIDDEN_THREADS, null, values);
	}

	public PostItem.HideState check(String chanName, String boardName, String threadNumber) {
		Cursor cursor = database.query(DatabaseHelper.TABLE_HIDDEN_THREADS, STATE_COLUMNS,
				buildWhere(chanName, boardName, threadNumber), null, null, null, null);
		try {
			if (cursor.moveToFirst()) {
				boolean hidden = cursor.getInt(1) != 0;
				return hidden ? PostItem.HideState.HIDDEN : PostItem.HideState.SHOWN;
			}
		} finally {
			cursor.close();
		}
		return PostItem.HideState.UNDEFINED;
	}
}
