package com.mishiranu.dashchan.content.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.database.sqlite.SQLiteDatabase;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;
import androidx.annotation.NonNull;
import chan.util.StringUtils;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.WeakObservable;
import java.util.Objects;

public class HistoryDatabase implements CommonDatabase.Instance {
	private interface Schema {
		interface History {
			String TABLE_NAME = "history";

			interface Columns {
				String CHAN_NAME = "chan_name";
				String BOARD_NAME = "board_name";
				String THREAD_NUMBER = "thread_number";
				String TIME = "time";
				String TITLE = "title";
			}
		}
	}

	public static class HistoryCursor extends CursorWrapper {
		public final boolean hasItems;
		public final boolean filtered;

		private final int chanNameIndex;
		private final int boardNameIndex;
		private final int threadNumberIndex;
		private final int timeIndex;
		private final int titleIndex;

		private HistoryCursor(Cursor cursor, boolean hasItems, boolean filtered) {
			super(cursor);
			this.hasItems = hasItems;
			this.filtered = filtered;
			chanNameIndex = cursor.getColumnIndex(Schema.History.Columns.CHAN_NAME);
			boardNameIndex = cursor.getColumnIndex(Schema.History.Columns.BOARD_NAME);
			threadNumberIndex = cursor.getColumnIndex(Schema.History.Columns.THREAD_NUMBER);
			timeIndex = cursor.getColumnIndex(Schema.History.Columns.TIME);
			titleIndex = cursor.getColumnIndex(Schema.History.Columns.TITLE);
		}
	}

	public static class HistoryItem {
		public String chanName;
		public String boardName;
		public String threadNumber;
		public long time;
		public String title;

		public HistoryItem update(HistoryCursor cursor) {
			chanName = cursor.getString(cursor.chanNameIndex);
			boardName = cursor.getString(cursor.boardNameIndex);
			threadNumber = cursor.getString(cursor.threadNumberIndex);
			time = cursor.getLong(cursor.timeIndex);
			title = cursor.getString(cursor.titleIndex);
			return this;
		}

		public HistoryItem copy() {
			HistoryItem historyItem = new HistoryItem();
			historyItem.chanName = chanName;
			historyItem.boardName = boardName;
			historyItem.threadNumber = threadNumber;
			historyItem.time = time;
			historyItem.title = title;
			return historyItem;
		}
	}

	private final CommonDatabase database;

	HistoryDatabase(CommonDatabase database) {
		this.database = database;
	}

	@Override
	public void create(SQLiteDatabase database) {
		database.execSQL("CREATE TABLE " + Schema.History.TABLE_NAME + " (" +
				Schema.History.Columns.CHAN_NAME + " TEXT NOT NULL, " +
				Schema.History.Columns.BOARD_NAME + " TEXT NOT NULL, " +
				Schema.History.Columns.THREAD_NUMBER + " TEXT NOT NULL, " +
				Schema.History.Columns.TIME + " INTEGER NOT NULL, " +
				Schema.History.Columns.TITLE + " TEXT, " +
				"PRIMARY KEY (" + Schema.History.Columns.CHAN_NAME + ", " +
				Schema.History.Columns.BOARD_NAME + ", " +
				Schema.History.Columns.THREAD_NUMBER + "))");
		database.execSQL("CREATE INDEX " + Schema.History.TABLE_NAME + "_order " +
				"ON " + Schema.History.TABLE_NAME + " (" +
				Schema.History.Columns.CHAN_NAME + ", " +
				Schema.History.Columns.TIME + ")");
	}

	@Override
	public void upgrade(SQLiteDatabase database, CommonDatabase.Migration migration) {
		switch (migration) {
			case FROM_8_TO_9: {
				// Change "history" table structure
				database.execSQL("ALTER TABLE history RENAME TO history_old");
				database.execSQL("CREATE TABLE history (chan_name TEXT NOT NULL, board_name TEXT NOT NULL, " +
						"thread_number TEXT NOT NULL, time INTEGER NOT NULL, title TEXT, " +
						"PRIMARY KEY (chan_name, board_name, thread_number))");
				database.execSQL("INSERT INTO history " +
						"SELECT chan_name, COALESCE(board_name, ''), thread_number, COALESCE(created, 0), title " +
						"FROM history_old WHERE chan_name IS NOT NULL AND thread_number IS NOT NULL " +
						"GROUP BY chan_name, COALESCE(board_name, ''), thread_number");
				database.execSQL("CREATE INDEX history_order ON history (chan_name, time)");
				database.execSQL("DROP TABLE history_old");
				break;
			}
			default: {
				throw new UnsupportedOperationException();
			}
		}
	}

	private final WeakObservable<Runnable> observable = new WeakObservable<>();

	public void registerObserver(Runnable runnable) {
		observable.register(runnable);
	}

	public void unregisterObserver(Runnable runnable) {
		observable.unregister(runnable);
	}

	private final Runnable onChanged = () -> {
		for (Runnable runnable : observable) {
			runnable.run();
		}
	};

	public void addHistoryAsync(@NonNull String chanName, String boardName,
			@NonNull String threadNumber, String title) {
		Objects.requireNonNull(chanName);
		Objects.requireNonNull(threadNumber);
		if (Preferences.isRememberHistory()) {
			database.enqueue(database -> {
				ContentValues values = new ContentValues();
				values.put(Schema.History.Columns.CHAN_NAME, chanName);
				values.put(Schema.History.Columns.BOARD_NAME, StringUtils.emptyIfNull(boardName));
				values.put(Schema.History.Columns.THREAD_NUMBER, threadNumber);
				values.put(Schema.History.Columns.TIME, System.currentTimeMillis());
				values.put(Schema.History.Columns.TITLE, title);
				database.replace(Schema.History.TABLE_NAME, null, values);
				ConcurrentUtils.HANDLER.post(onChanged);
				return null;
			});
		}
	}

	public void updateTitleAsync(@NonNull String chanName, String boardName,
			@NonNull String threadNumber, String title) {
		Objects.requireNonNull(chanName);
		Objects.requireNonNull(threadNumber);
		if (!StringUtils.isEmpty(title)) {
			database.enqueue(database -> {
				Expression.Filter filter = Expression.filter()
						.equals(Schema.History.Columns.CHAN_NAME, chanName)
						.equals(Schema.History.Columns.BOARD_NAME, StringUtils.emptyIfNull(boardName))
						.equals(Schema.History.Columns.THREAD_NUMBER, threadNumber)
						.build();
				ContentValues values = new ContentValues();
				values.put(Schema.History.Columns.TITLE, title);
				database.update(Schema.History.TABLE_NAME, values, filter.value, filter.args);
				ConcurrentUtils.HANDLER.post(onChanged);
				return null;
			});
		}
	}

	public HistoryCursor getHistory(String chanName, String searchQuery,
			CancellationSignal signal) throws OperationCanceledException {
		int count = database.execute(database -> {
			String[] projection = {"COUNT(*)"};
			Expression.Filter.Builder filterBuilder = Expression.filter();
			if (chanName != null) {
				filterBuilder.equals(Schema.History.Columns.CHAN_NAME, chanName);
			}
			Expression.Filter filter = filterBuilder.build();
			try (Cursor cursor = database.query(false, Schema.History.TABLE_NAME,
					projection, filter.value, filter.args, null, null, null, null, signal)) {
				if (cursor.moveToFirst()) {
					return cursor.getInt(0);
				}
			}
			return 0;
		});
		String[] projection = {"rowid", "*"};
		Expression.Filter.Builder filterBuilder = Expression.filter();
		if (chanName != null) {
			filterBuilder.equals(Schema.History.Columns.CHAN_NAME, chanName);
		}
		boolean filtered = false;
		if (!StringUtils.isEmpty(searchQuery)) {
			filterBuilder.like(Schema.History.Columns.TITLE, "%" + searchQuery + "%");
			filtered = true;
		}
		Expression.Filter filter = filterBuilder.build();
		Cursor cursor = database.query(database -> database.query(false, Schema.History.TABLE_NAME, projection,
				filter.value, filter.args, null, null, Schema.History.Columns.TIME + " DESC", null, signal));
		return new HistoryCursor(cursor, count > 0, filtered);
	}

	public void remove(@NonNull String chanName, String boardName, @NonNull String threadNumber) {
		Objects.requireNonNull(chanName);
		Objects.requireNonNull(threadNumber);
		Expression.Filter filter = Expression.filter()
				.equals(Schema.History.Columns.CHAN_NAME, chanName)
				.equals(Schema.History.Columns.BOARD_NAME, StringUtils.emptyIfNull(boardName))
				.equals(Schema.History.Columns.THREAD_NUMBER, threadNumber)
				.build();
		database.execute(database -> database.delete(Schema.History.TABLE_NAME, filter.value, filter.args));
		ConcurrentUtils.HANDLER.post(onChanged);
	}

	public void clearHistory(String chanName) {
		Expression.Filter.Builder filterBuilder = Expression.filter();
		if (chanName != null) {
			filterBuilder.equals(Schema.History.Columns.CHAN_NAME, chanName);
		}
		Expression.Filter filter = filterBuilder.build();
		database.execute(database -> database.delete(Schema.History.TABLE_NAME, filter.value, filter.args));
		ConcurrentUtils.HANDLER.post(onChanged);
	}
}
