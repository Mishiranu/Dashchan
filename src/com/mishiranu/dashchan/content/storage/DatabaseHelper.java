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

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.Executor;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Process;

import chan.http.HttpValidator;
import chan.util.StringUtils;

import com.mishiranu.dashchan.content.MainApplication;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.FlagUtils;

public class DatabaseHelper extends SQLiteOpenHelper {
	private static final String DATABASE_NAME = "dashchan.db";
	private static final int DATABASE_VERSION = 8;

	static final String TABLE_HISTORY = "history";
	static final String TABLE_HIDDEN_THREADS = "hidden_threads";

	private static final String TYPE_INTEGER = "INTEGER";
	private static final String TYPE_LONG = "LONG";
	private static final String TYPE_TEXT = "TEXT";

	private final Executor mExecutor = ConcurrentUtils.newSingleThreadPool(10000, "DatabaseHelper", null,
			Process.THREAD_PRIORITY_DEFAULT);

	private static final DatabaseHelper INSTANCE = new DatabaseHelper();

	public static DatabaseHelper getInstance() {
		return INSTANCE;
	}

	private DatabaseHelper() {
		super(MainApplication.getInstance(), DATABASE_NAME, null, DATABASE_VERSION);
		getWritableDatabase(); // Perform create and upgrade here
	}

	public Executor getExecutor() {
		return mExecutor;
	}

	public static File getDatabaseFile() {
		return MainApplication.getInstance().getDatabasePath(DATABASE_NAME);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		new TableCreator(TABLE_HISTORY)
				.addColumn(HistoryDatabase.COLUMN_CHAN_NAME, TYPE_TEXT)
				.addColumn(HistoryDatabase.COLUMN_BOARD_NAME, TYPE_TEXT)
				.addColumn(HistoryDatabase.COLUMN_THREAD_NUMBER, TYPE_TEXT)
				.addColumn(HistoryDatabase.COLUMN_TITLE, TYPE_TEXT)
				.addColumn(HistoryDatabase.COLUMN_CREATED, TYPE_LONG).execute(db);
		new TableCreator(TABLE_HIDDEN_THREADS)
				.addColumn(HiddenThreadsDatabase.COLUMN_CHAN_NAME, TYPE_TEXT)
				.addColumn(HiddenThreadsDatabase.COLUMN_BOARD_NAME, TYPE_TEXT)
				.addColumn(HiddenThreadsDatabase.COLUMN_THREAD_NUMBER, TYPE_TEXT)
				.addColumn(HiddenThreadsDatabase.COLUMN_HIDDEN, TYPE_INTEGER).execute(db);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		switch (oldVersion) {
			case 5: {
				// Version upgrade from 5 to 6
				// Added watcher_validator column to favorites table
				new TableModifier(null, "favorites")
						.addColumn("chan_name", TYPE_TEXT, "chan_name")
						.addColumn("board_name", TYPE_TEXT, "board_name")
						.addColumn("thread_number", TYPE_TEXT, "thread_number")
						.addColumn("title", TYPE_TEXT, "title")
						.addColumn("flags", TYPE_INTEGER, "flags")
						.addColumn("posts_count", TYPE_INTEGER, "posts_count")
						.addColumn("new_posts_count", TYPE_INTEGER, "new_posts_count")
						.addColumn("watcher_validator", TYPE_TEXT, null).execute(db);
			}
			case 6: {
				// Version upgrade from 6 to 7
				// Renamed params column to flags in autohide table
				// Renamed regex column to value in autohide table
				new TableModifier(null, "autohide")
						.addColumn("chan_name", TYPE_TEXT, "chan_name")
						.addColumn("board_name", TYPE_TEXT, "board_name")
						.addColumn("thread_number", TYPE_TEXT, "thread_number")
						.addColumn("flags", TYPE_INTEGER, "params")
						.addColumn("value", TYPE_TEXT, "regex").execute(db);
			}
			case 7: {
				// Version upgrade from 7 to 8
				// Remove favorites and autohide tables
				FavoritesStorage favoritesStorage = FavoritesStorage.getInstance();
				String[] favoritesColumns = {"chan_name", "board_name", "thread_number",
					"title", "flags", "posts_count", "new_posts_count", "watcher_validator"};
				Cursor cursor = db.query("favorites", favoritesColumns, null, null, null, null, "_id ASC");
				if (cursor.moveToFirst()) {
					do {
						String chanName = cursor.getString(0);
						String boardName = cursor.getString(1);
						String threadNumber = cursor.getString(2);
						String title = cursor.getString(3);
						int flags = cursor.getInt(4);
						boolean modifiedTitle = FlagUtils.get(flags, 0x00000001);
						boolean watcherEnabled = FlagUtils.get(flags, 0x00000002);
						int postsCount = cursor.getInt(5);
						int newPostsCount = cursor.getInt(6);
						boolean hasNewPosts = newPostsCount > postsCount;
						HttpValidator watcherValidator = HttpValidator.fromString(cursor.getString(7));
						favoritesStorage.add(new FavoritesStorage.FavoriteItem(chanName, boardName, threadNumber, title,
								modifiedTitle, watcherEnabled, postsCount, newPostsCount, hasNewPosts,
								watcherValidator));
					} while (cursor.moveToNext());
				}
				cursor.close();
				favoritesStorage.await(false);
				AutohideStorage autohideStorage = AutohideStorage.getInstance();
				String[] autohideColumns = {"chan_name", "board_name", "thread_number", "flags", "value"};
				cursor = db.query("autohide", autohideColumns, null, null, null, null, "_id ASC");
				if (cursor.moveToFirst()) {
					do {
						int params = cursor.getInt(3);
						String chanNamesString = StringUtils.nullIfEmpty(cursor.getString(0));
						HashSet<String> chanNames = null;
						if (chanNamesString != null) {
							String[] chanNamesArray = chanNamesString.split(",");
							if (chanNamesArray.length > 0) {
								chanNames = new HashSet<>();
								Collections.addAll(chanNames, chanNamesArray);
							}
						}
						String boardName = cursor.getString(1);
						String threadNumber = cursor.getString(2);
						boolean optionOriginalPost = FlagUtils.get(params, 0x00000001);
						boolean optionSage = FlagUtils.get(params, 0x00000010);
						boolean optionSubject = FlagUtils.get(params, 0x00000002);
						boolean optionComment = FlagUtils.get(params, 0x00000004);
						boolean optionName = FlagUtils.get(params, 0x00000008);
						String value = cursor.getString(4);
						autohideStorage.add(new AutohideStorage.AutohideItem(chanNames, boardName, threadNumber,
								optionOriginalPost, optionSage, optionSubject, optionComment, optionName, value));
					} while (cursor.moveToNext());
				}
				cursor.close();
				autohideStorage.await(false);
				db.execSQL("DROP TABLE favorites");
				db.execSQL("DROP TABLE autohide");
			}
		}
	}

	private static class TableCreator {
		private final ArrayList<String> mColumns = new ArrayList<>();
		private final String mTableName;

		public TableCreator(String tableName) {
			mTableName = tableName;
		}

		public TableCreator addColumn(String columnName, String columnType) {
			String columnString = columnName + " " + columnType + " NULL";
			mColumns.add(columnString);
			return this;
		}

		public void execute(SQLiteDatabase db) {
			StringBuilder builder = new StringBuilder().append("CREATE TABLE ").append(mTableName).append(" (");
			builder.append("_id INTEGER PRIMARY KEY AUTOINCREMENT");
			for (int i = 0; i < mColumns.size(); i++) {
				builder.append(", ").append(mColumns.get(i));
			}
			builder.append(')');
			db.execSQL(builder.toString());
		}
	}

	private static class TableModifier {
		private final TableCreator mTableCreator;
		private final String mFromTableName;

		private final ArrayList<String> mFrom = new ArrayList<>(), mTo = new ArrayList<>();

		public TableModifier(String newTableName, String oldTableName) {
			if (newTableName == null) {
				newTableName = oldTableName;
			}
			mTableCreator = new TableCreator(newTableName);
			mFromTableName = newTableName.equals(oldTableName) ? null : oldTableName;
		}

		public TableModifier addColumn(String columnName, String columnType, String copyFrom) {
			mTableCreator.addColumn(columnName, columnType);
			if (copyFrom != null) {
				mFrom.add(copyFrom);
				mTo.add(columnName);
			}
			return this;
		}

		public void execute(SQLiteDatabase db) {
			String table = mTableCreator.mTableName;
			String oldTable;
			if (mFromTableName == null) {
				oldTable = table + "_temp";
				db.execSQL("ALTER TABLE " + table + " RENAME TO " + oldTable);
			} else {
				oldTable = mFromTableName;
			}
			mTableCreator.execute(db);
			if (mFrom.size() > 0) {
				StringBuilder builder = new StringBuilder().append("INSERT INTO ").append(table).append(" (");
				for (int i = 0; i < mTo.size(); i++) {
					if (i > 0) {
						builder.append(", ");
					}
					builder.append(mTo.get(i));
				}
				builder.append(") SELECT ");
				for (int i = 0; i < mFrom.size(); i++) {
					if (i > 0) {
						builder.append(", ");
					}
					builder.append(mFrom.get(i));
				}
				builder.append(" FROM ").append(oldTable);
				db.execSQL(builder.toString());
			}
			db.execSQL("DROP TABLE " + oldTable);
		}
	}
}