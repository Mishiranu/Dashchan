package com.mishiranu.dashchan.content.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import chan.util.StringUtils;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.util.FlagUtils;
import java.util.List;
import java.util.Objects;

public class ThreadsDatabase implements CommonDatabase.Instance {
	private interface Schema {
		interface Threads {
			String TABLE_NAME = "threads";
			int MAX_COUNT = 2000;
			float MAX_COUNT_FACTOR = 0.75f;

			interface Columns {
				String CHAN_NAME = "chan_name";
				String BOARD_NAME = "board_name";
				String THREAD_NUMBER = "thread_number";
				String TIME = "time";
				String FLAGS = "flags";
				String STATE = "state";
				String EXTRA = "extra";
			}

			interface Flags {
				int HIDDEN = 0x00000001;
				int SHOWN = 0x00000002;
			}
		}
	}

	public static class StateExtra {
		private static final StateExtra EMPTY = new StateExtra(null, null);

		public final byte[] state;
		public final byte[] extra;

		public StateExtra(byte[] state, byte[] extra) {
			this.state = state;
			this.extra = extra;
		}
	}

	private final CommonDatabase database;

	ThreadsDatabase(CommonDatabase database) {
		this.database = database;
	}

	@Override
	public void create(SQLiteDatabase database) {
		database.execSQL("CREATE TABLE " + Schema.Threads.TABLE_NAME + " (" +
				Schema.Threads.Columns.CHAN_NAME + " TEXT NOT NULL, " +
				Schema.Threads.Columns.BOARD_NAME + " TEXT NOT NULL, " +
				Schema.Threads.Columns.THREAD_NUMBER + " TEXT NOT NULL, " +
				Schema.Threads.Columns.TIME + " INTEGER NOT NULL, " +
				Schema.Threads.Columns.FLAGS + " INTEGER NOT NULL DEFAULT 0, " +
				Schema.Threads.Columns.STATE + " BLOB, " +
				Schema.Threads.Columns.EXTRA + " BLOB, " +
				"PRIMARY KEY (" + Schema.Threads.Columns.CHAN_NAME + ", " +
				Schema.Threads.Columns.BOARD_NAME + ", " +
				Schema.Threads.Columns.THREAD_NUMBER + "))");
	}

	@Override
	public void upgrade(SQLiteDatabase database, CommonDatabase.Migration migration) {
		switch (migration) {
			case FROM_8_TO_9: {
				// Change "hidden_threads" table structure and rename to "threads"
				database.execSQL("CREATE TABLE threads (chan_name TEXT NOT NULL, board_name TEXT NOT NULL, " +
						"thread_number TEXT NOT NULL, time INTEGER NOT NULL, flags INTEGER NOT NULL DEFAULT 0, " +
						"state BLOB, extra BLOB, PRIMARY KEY (chan_name, board_name, thread_number))");
				long time = System.currentTimeMillis();
				database.execSQL("INSERT INTO threads " +
						"SELECT chan_name, COALESCE(board_name, ''), thread_number, " + time + ", " +
						"CASE COALESCE(hidden, 0) WHEN 0 THEN 2 else 1 END, NULL, NULL " +
						"FROM hidden_threads WHERE chan_name IS NOT NULL AND thread_number IS NOT NULL " +
						"GROUP BY chan_name, COALESCE(board_name, ''), thread_number");
				database.execSQL("DROP TABLE hidden_threads");
				break;
			}
			default: {
				throw new UnsupportedOperationException();
			}
		}
	}

	@Override
	public void open(SQLiteDatabase database) {
		boolean clean;
		try (Cursor cursor = database.rawQuery("SELECT COUNT(*) FROM " + Schema.Threads.TABLE_NAME, null)) {
			clean = cursor.moveToFirst() && cursor.getInt(0) > Schema.Threads.MAX_COUNT;
		}
		if (clean) {
			Long time;
			String[] projection = {Schema.Threads.Columns.TIME};
			try (Cursor cursor = database.query(Schema.Threads.TABLE_NAME,
					projection, null, null, null, null, Schema.Threads.Columns.TIME + " DESC",
					(int) (Schema.Threads.MAX_COUNT_FACTOR * Schema.Threads.MAX_COUNT) + ", 1")) {
				time = cursor.moveToFirst() ? cursor.getLong(0) : null;
			}
			if (time != null) {
				database.delete(Schema.Threads.TABLE_NAME, Schema.Threads.Columns.TIME + " <= " + time, null);
			}
		}
	}

	private void upsert(SQLiteDatabase database, @NonNull String chanName, String boardName,
			@NonNull String threadNumber, ContentValues values) {
		Objects.requireNonNull(chanName);
		Objects.requireNonNull(threadNumber);
		database.beginTransaction();
		try {
			Expression.Filter filter = Expression.filter()
					.equals(Schema.Threads.Columns.CHAN_NAME, chanName)
					.equals(Schema.Threads.Columns.BOARD_NAME, StringUtils.emptyIfNull(boardName))
					.equals(Schema.Threads.Columns.THREAD_NUMBER, threadNumber)
					.build();
			if (database.update(Schema.Threads.TABLE_NAME, values, filter.value, filter.args) <= 0) {
				ContentValues newValues = new ContentValues();
				newValues.put(Schema.Threads.Columns.CHAN_NAME, chanName);
				newValues.put(Schema.Threads.Columns.BOARD_NAME, StringUtils.emptyIfNull(boardName));
				newValues.put(Schema.Threads.Columns.THREAD_NUMBER, threadNumber);
				newValues.putAll(values);
				database.insert(Schema.Threads.TABLE_NAME, null, newValues);
			}
			database.setTransactionSuccessful();
		} finally {
			database.endTransaction();
		}
	}

	public void setFlagsAsync(@NonNull String chanName, String boardName, @NonNull String threadNumber,
			PostItem.HideState hideState) {
		Objects.requireNonNull(chanName);
		Objects.requireNonNull(threadNumber);
		database.enqueue(database -> {
			int flags = 0;
			if (hideState == PostItem.HideState.HIDDEN) {
				flags |= Schema.Threads.Flags.HIDDEN;
			} else if (hideState == PostItem.HideState.SHOWN) {
				flags |= Schema.Threads.Flags.SHOWN;
			}
			ContentValues values = new ContentValues();
			values.put(Schema.Threads.Columns.TIME, System.currentTimeMillis());
			values.put(Schema.Threads.Columns.FLAGS, flags);
			upsert(database, chanName, boardName, threadNumber, values);
			return null;
		});
	}

	private void getFlags(@NonNull String chanName, String boardName,
			@NonNull List<String> threadNumbers, PostItem.HideState.Map<String> hiddenThreads) {
		Objects.requireNonNull(chanName);
		Objects.requireNonNull(threadNumbers);
		boolean update = false;
		String[] projection = {Schema.Threads.Columns.THREAD_NUMBER, Schema.Threads.Columns.FLAGS};
		Expression.Filter filter = Expression.filter()
				.equals(Schema.Threads.Columns.CHAN_NAME, chanName)
				.equals(Schema.Threads.Columns.BOARD_NAME, StringUtils.emptyIfNull(boardName))
				.in(Schema.Threads.Columns.THREAD_NUMBER, threadNumbers)
				.raw(Schema.Threads.Columns.FLAGS)
				.build();
		try (Cursor cursor = database.query(database -> database
				.query(Schema.Threads.TABLE_NAME, projection, filter.value, filter.args, null, null, null))) {
			while (cursor.moveToNext()) {
				String threadNumber = cursor.getString(0);
				int flags = cursor.getInt(1);
				if (FlagUtils.get(flags, Schema.Threads.Flags.HIDDEN)) {
					update = true;
					hiddenThreads.set(threadNumber, PostItem.HideState.HIDDEN);
				} else if (FlagUtils.get(flags, Schema.Threads.Flags.SHOWN)) {
					update = true;
					hiddenThreads.set(threadNumber, PostItem.HideState.SHOWN);
				}
			}
		}
		if (update) {
			database.execute(database -> {
				ContentValues values = new ContentValues();
				values.put(Schema.Threads.Columns.TIME, System.currentTimeMillis());
				database.update(Schema.Threads.TABLE_NAME, values, filter.value, filter.args);
				return null;
			});
		}
	}

	public PostItem.HideState.Map<String> getFlags(@NonNull String chanName, String boardName,
			@NonNull List<String> threadNumbers) {
		return database.execute(database -> {
			database.beginTransaction();
			try {
				PostItem.HideState.Map<String> hiddenThreads = new PostItem.HideState.Map<>();
				int maxCount = 50;
				for (int i = 0; i < threadNumbers.size(); i += maxCount) {
					List<String> subList = threadNumbers.subList(i, Math.min(i + maxCount, threadNumbers.size()));
					getFlags(chanName, boardName, subList, hiddenThreads);
				}
				database.setTransactionSuccessful();
				return hiddenThreads;
			} finally {
				database.endTransaction();
			}
		});
	}

	public void setStateExtra(boolean async, @NonNull String chanName, String boardName, @NonNull String threadNumber,
			boolean hasState, byte[] state, boolean hasExtra, byte[] extra) {
		Objects.requireNonNull(chanName);
		Objects.requireNonNull(threadNumber);
		CommonDatabase.ExecuteCallback<Void> callback = database -> {
			ContentValues values = new ContentValues();
			values.put(Schema.Threads.Columns.TIME, System.currentTimeMillis());
			if (hasState) {
				values.put(Schema.Threads.Columns.STATE, state);
			}
			if (hasExtra) {
				values.put(Schema.Threads.Columns.EXTRA, extra);
			}
			upsert(database, chanName, boardName, threadNumber, values);
			return null;
		};
		if (async) {
			database.enqueue(callback);
		} else {
			database.execute(callback);
		}
	}

	public StateExtra getStateExtra(@NonNull String chanName, String boardName, @NonNull String threadNumber) {
		Objects.requireNonNull(chanName);
		Objects.requireNonNull(threadNumber);
		String[] projection = {Schema.Threads.Columns.STATE, Schema.Threads.Columns.EXTRA};
		Expression.Filter filter = Expression.filter()
				.equals(Schema.Threads.Columns.CHAN_NAME, chanName)
				.equals(Schema.Threads.Columns.BOARD_NAME, StringUtils.emptyIfNull(boardName))
				.equals(Schema.Threads.Columns.THREAD_NUMBER, threadNumber)
				.build();
		StateExtra stateExtra = null;
		try (Cursor cursor = database.query(database -> database
				.query(Schema.Threads.TABLE_NAME, projection, filter.value, filter.args, null, null, null))) {
			if (cursor.moveToFirst()) {
				stateExtra = new StateExtra(cursor.getBlob(0), cursor.getBlob(1));
			}
		}
		if (stateExtra != null) {
			database.execute(database -> {
				ContentValues values = new ContentValues();
				values.put(Schema.Threads.Columns.TIME, System.currentTimeMillis());
				database.update(Schema.Threads.TABLE_NAME, values, filter.value, filter.args);
				return null;
			});
			return stateExtra;
		}
		return StateExtra.EMPTY;
	}
}
