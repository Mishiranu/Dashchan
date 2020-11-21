package com.mishiranu.dashchan.content.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;
import android.util.Pair;
import android.util.Xml;
import androidx.annotation.NonNull;
import chan.content.model.Board;
import chan.content.model.BoardCategory;
import chan.util.CommonUtils;
import chan.util.StringUtils;
import com.mishiranu.dashchan.content.MainApplication;
import com.mishiranu.dashchan.util.FlagUtils;
import com.mishiranu.dashchan.util.LruCache;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class ChanDatabase {
	private interface Schema {
		interface Boards {
			String TABLE_NAME = "boards";

			interface Columns {
				String CHAN_NAME = "chan_name";
				String BOARD_NAME = "board_name";
				String CATEGORY = "category";
			}
		}

		interface Data {
			String TABLE_NAME = "data";

			interface Columns {
				String CHAN_NAME = "chan_name";
				String BOARD_NAME = "board_name";
				String NAME = "name";
				String VALUE = "value";
			}
		}

		interface Cookies {
			String TABLE_NAME = "cookies";

			interface Columns {
				String CHAN_NAME = "chan_name";
				String NAME = "name";
				String VALUE = "value";
				String TITLE = "title";
				String FLAGS = "flags";
			}

			interface Flags {
				int DELETED = 0x00000001;
				int BLOCKED = 0x00000002;
				int DELETE_ON_EXIT = 0x00000004;
			}
		}

		interface Extra {
			interface Columns {
				String EXTRA1 = "extra1";
				String EXTRA2 = "extra2";
			}
		}
	}

	public interface BoardExtraFallbackProvider {
		String getExtra(String boardName);
	}

	public static class BoardCursor extends CursorWrapper {
		public final boolean hasItems;
		public final boolean filtered;

		private final BoardExtraFallbackProvider provider1;
		private final BoardExtraFallbackProvider provider2;
		private final int boardNameIndex;
		private final int categoryIndex;
		private final int extra1Index;
		private final int extra2Index;

		private BoardCursor(Cursor cursor, boolean hasItems, boolean filtered,
				BoardExtraFallbackProvider provider1, BoardExtraFallbackProvider provider2) {
			super(cursor);
			this.hasItems = hasItems;
			this.filtered = filtered;
			this.provider1 = provider1;
			this.provider2 = provider2;
			boardNameIndex = cursor.getColumnIndex(Schema.Boards.Columns.BOARD_NAME);
			categoryIndex = cursor.getColumnIndex(Schema.Boards.Columns.CATEGORY);
			extra1Index = cursor.getColumnIndex(Schema.Extra.Columns.EXTRA1);
			extra2Index = cursor.getColumnIndex(Schema.Extra.Columns.EXTRA2);
		}
	}

	public static class BoardItem {
		public String boardName;
		public String category;
		public String extra1;
		public String extra2;

		public BoardItem update(BoardCursor cursor) {
			boardName = cursor.getString(cursor.boardNameIndex);
			category = cursor.categoryIndex >= 0 ? cursor.getString(cursor.categoryIndex) : null;
			extra1 = cursor.extra1Index >= 0 ? cursor.getString(cursor.extra1Index) : null;
			extra2 = cursor.extra2Index >= 0 ? cursor.getString(cursor.extra2Index) : null;
			if (StringUtils.isEmpty(extra1) && cursor.provider1 != null) {
				extra1 = cursor.provider1.getExtra(boardName);
			}
			if (StringUtils.isEmpty(extra2) && cursor.provider2 != null) {
				extra2 = cursor.provider2.getExtra(boardName);
			}
			return this;
		}

		public BoardItem copy() {
			BoardItem boardItem = new BoardItem();
			boardItem.boardName = boardName;
			boardItem.category = category;
			boardItem.extra1 = extra1;
			boardItem.extra2 = extra2;
			return boardItem;
		}
	}

	public static class DataKey {
		public final String boardName;
		public final String name;

		public DataKey(String boardName, String name) {
			this.boardName = StringUtils.emptyIfNull(boardName);
			this.name = StringUtils.emptyIfNull(name);
		}

		@Override
		public boolean equals(Object o) {
			if (o == this) {
				return true;
			}
			if (o instanceof DataKey) {
				DataKey dataKey = (DataKey) o;
				return boardName.equals(dataKey.boardName) && name.equals(dataKey.name);
			}
			return false;
		}

		@Override
		public int hashCode() {
			int prime = 31;
			int result = 1;
			result = prime * result + boardName.hashCode();
			result = prime * result + name.hashCode();
			return result;
		}
	}

	public static class CookieCursor extends CursorWrapper {
		private final int nameIndex;
		private final int valueIndex;
		private final int titleIndex;
		private final int flagsIndex;

		private CookieCursor(Cursor cursor) {
			super(cursor);
			nameIndex = cursor.getColumnIndex(Schema.Cookies.Columns.NAME);
			valueIndex = cursor.getColumnIndex(Schema.Cookies.Columns.VALUE);
			titleIndex = cursor.getColumnIndex(Schema.Cookies.Columns.TITLE);
			flagsIndex = cursor.getColumnIndex(Schema.Cookies.Columns.FLAGS);
		}
	}

	public static class CookieItem {
		public String name;
		public String value;
		public String title;
		public boolean blocked;
		public boolean deleteOnExit;

		public CookieItem update(CookieCursor cursor) {
			name = cursor.getString(cursor.nameIndex);
			value = cursor.getString(cursor.valueIndex);
			title = cursor.getString(cursor.titleIndex);
			blocked = FlagUtils.get(cursor.getInt(cursor.flagsIndex), Schema.Cookies.Flags.BLOCKED);
			deleteOnExit = FlagUtils.get(cursor.getInt(cursor.flagsIndex), Schema.Cookies.Flags.DELETE_ON_EXIT);
			return this;
		}

		public CookieItem copy() {
			CookieItem cookieItem = new CookieItem();
			cookieItem.name = name;
			cookieItem.value = value;
			cookieItem.title = title;
			cookieItem.blocked = blocked;
			cookieItem.deleteOnExit = deleteOnExit;
			return cookieItem;
		}
	}

	private static final ChanDatabase INSTANCE = new ChanDatabase();

	public static ChanDatabase getInstance() {
		return INSTANCE;
	}

	private final Helper helper = new Helper();
	private final SQLiteDatabase database = helper.getWritableDatabase();
	private final boolean supportsCte;

	private ChanDatabase() {
		// Since SQLite 3.8.3
		boolean supportsCte;
		try (Cursor ignored = database.rawQuery("WITH x AS (SELECT 1) SELECT 1", null)) {
			supportsCte = true;
		} catch (Exception e) {
			supportsCte = false;
		}
		this.supportsCte = supportsCte;

		// Migrate old cookies
		File[] sharedPrefs = new File(MainApplication.getInstance()
				.getCacheDir().getParentFile(), "shared_prefs").listFiles();
		if (sharedPrefs != null) {
			for (File file : sharedPrefs) {
				String fileName = file.getName();
				if (fileName.startsWith("chan.") && fileName.endsWith(".xml")) {
					String chanName = file.getName().substring(5, fileName.length() - 4);
					String cookiesJson = null;
					try (FileInputStream input = new FileInputStream(file)) {
						XmlPullParser parser = Xml.newPullParser();
						parser.setInput(input, "UTF-8");
						int eventType;
						do {
							eventType = parser.next();
							if (eventType == XmlPullParser.START_TAG && "string".equals(parser.getName())) {
								int count = parser.getAttributeCount();
								String name = null;
								for (int i = 0; i < count; i++) {
									if ("name".equals(parser.getAttributeName(i))) {
										name = parser.getAttributeValue(i);
										break;
									}
								}
								if ("cookies".equals(name)) {
									eventType = parser.next();
									if (eventType == XmlPullParser.TEXT) {
										cookiesJson = parser.getText();
										break;
									}
								}
							}
						} while (eventType != XmlPullParser.END_DOCUMENT);
					} catch (IOException | XmlPullParserException e) {
						// Ignore
					}
					JSONObject cookiesObject = null;
					if (cookiesJson != null) {
						try {
							cookiesObject = new JSONObject(cookiesJson);
						} catch (JSONException e) {
							// Ignore exception
						}
					}
					if (cookiesObject != null) {
						Iterator<String> iterator = cookiesObject.keys();
						while (iterator.hasNext()) {
							String name = iterator.next();
							JSONObject jsonObject = cookiesObject.optJSONObject(name);
							if (jsonObject != null) {
								String value = jsonObject.optString("value");
								if (!StringUtils.isEmpty(value)) {
									String title = jsonObject.optString("displayName");
									boolean blocked = jsonObject.optBoolean("blocked");
									setCookie(chanName, name, value, title);
									setCookieState(chanName, name, blocked, null);
								}
							}
						}
					}
					file.delete();
				}
			}
		}
		deleteCookiesOnExit();
	}

	private static class Helper extends SQLiteOpenHelper {
		private static final String DATABASE_NAME = "chan.db";
		private static final int DATABASE_VERSION = 1;

		private Helper() {
			super(MainApplication.getInstance(), DATABASE_NAME, null, DATABASE_VERSION);
			setWriteAheadLoggingEnabled(true);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL("CREATE TABLE " + Schema.Boards.TABLE_NAME + " (" +
					Schema.Boards.Columns.CHAN_NAME + " TEXT NOT NULL, " +
					Schema.Boards.Columns.BOARD_NAME + " TEXT NOT NULL, " +
					Schema.Boards.Columns.CATEGORY + " TEXT NOT NULL, " +
					"PRIMARY KEY (" + Schema.Boards.Columns.CHAN_NAME + ", " +
					Schema.Boards.Columns.BOARD_NAME + "))");
			db.execSQL("CREATE TABLE " + Schema.Data.TABLE_NAME + " (" +
					Schema.Data.Columns.CHAN_NAME + " TEXT NOT NULL, " +
					Schema.Data.Columns.BOARD_NAME + " TEXT NOT NULL, " +
					Schema.Data.Columns.NAME + " TEXT NOT NULL, " +
					Schema.Data.Columns.VALUE + " TEXT, " +
					"PRIMARY KEY (" + Schema.Data.Columns.CHAN_NAME + ", " +
					Schema.Data.Columns.BOARD_NAME + ", " +
					Schema.Data.Columns.NAME + "))");
			db.execSQL("CREATE TABLE " + Schema.Cookies.TABLE_NAME + " (" +
					Schema.Cookies.Columns.CHAN_NAME + " TEXT NOT NULL, " +
					Schema.Cookies.Columns.NAME + " TEXT NOT NULL, " +
					Schema.Cookies.Columns.VALUE + " TEXT NOT NULL, " +
					Schema.Cookies.Columns.TITLE + " TEXT NOT NULL, " +
					Schema.Cookies.Columns.FLAGS + " INTEGER NOT NULL DEFAULT 0, " +
					"PRIMARY KEY (" + Schema.Cookies.Columns.CHAN_NAME + ", " +
					Schema.Cookies.Columns.NAME + "))");
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}
	}

	public boolean setBoards(@NonNull String chanName, BoardCategory[] boardCategories) {
		Objects.requireNonNull(chanName);
		ArrayList<Pair<String, String>> boardsList = new ArrayList<>();
		if (boardCategories != null) {
			for (BoardCategory boardCategory : boardCategories) {
				if (boardCategory != null) {
					String category = StringUtils.emptyIfNull(boardCategory.getTitle());
					Board[] boards = boardCategory.getBoards();
					if (boards != null) {
						for (Board board : boards) {
							if (board != null) {
								String boardName = board.getBoardName();
								if (!StringUtils.isEmpty(boardName)) {
									boardsList.add(new Pair<>(boardName, category));
								}
							}
						}
					}
				}
			}
		}
		database.beginTransaction();
		try {
			Expression.Filter filter = Expression.filter()
					.equals(Schema.Boards.Columns.CHAN_NAME, chanName)
					.build();
			database.delete(Schema.Boards.TABLE_NAME, filter.value, filter.args);
			Iterator<Pair<String, String>> iterator = boardsList.iterator();
			Expression.batchInsert(boardsList.size(), 100, 3,
					values -> database.compileStatement("INSERT OR REPLACE " +
							"INTO " + Schema.Boards.TABLE_NAME + " (" +
							Schema.Boards.Columns.CHAN_NAME + ", " +
							Schema.Boards.Columns.BOARD_NAME + ", " +
							Schema.Boards.Columns.CATEGORY + ") " +
							"VALUES " + values),
					(statement, start) -> {
						Pair<String, String> pair = iterator.next();
						statement.bindString(start + 1, chanName);
						statement.bindString(start + 2, pair.first);
						statement.bindString(start + 3, pair.second);
					});
			database.setTransactionSuccessful();
			return !boardsList.isEmpty();
		} finally {
			database.endTransaction();
		}
	}

	public BoardCursor getBoards(@NonNull String chanName, String searchQuery, @NonNull String extraName,
			BoardExtraFallbackProvider provider, CancellationSignal signal) throws OperationCanceledException {
		Objects.requireNonNull(chanName);
		Objects.requireNonNull(extraName);
		String[] projection = {"COUNT(*)"};
		Expression.Filter countFilter = Expression.filter()
				.equals(Schema.Boards.Columns.CHAN_NAME, chanName)
				.build();
		int count;
		try (Cursor cursor = database.query(false, Schema.Boards.TABLE_NAME,
				projection, countFilter.value, countFilter.args, null, null, null, null, signal)) {
			count = cursor.moveToFirst() ? cursor.getInt(0) : 0;
		}
		Expression.Filter.Builder filterBuilder = Expression.filter()
				.equals("b." + Schema.Boards.Columns.CHAN_NAME, chanName);
		boolean filtered = false;
		if (!StringUtils.isEmpty(searchQuery)) {
			filterBuilder.append(Expression.filterOr()
					.like("b." + Schema.Boards.Columns.BOARD_NAME, "%" + searchQuery + "%")
					.like("d." + Schema.Data.Columns.VALUE, "%" + searchQuery + "%"));
			filtered = true;
		}
		Expression.Filter filter = filterBuilder.build();
		String[] args = new String[1 + filter.args.length];
		args[0] = extraName;
		System.arraycopy(filter.args, 0, args, 1, filter.args.length);
		Cursor cursor = database.rawQuery("SELECT b.rowid, " +
				"b." + Schema.Boards.Columns.BOARD_NAME + ", " +
				"b." + Schema.Boards.Columns.CATEGORY + ", " +
				"d." + Schema.Data.Columns.VALUE + " AS " + Schema.Extra.Columns.EXTRA1 + " " +
				"FROM " + Schema.Boards.TABLE_NAME + " AS b " +
				"LEFT JOIN " + Schema.Data.TABLE_NAME + " AS d " +
				"ON b." + Schema.Boards.Columns.CHAN_NAME + " = d." + Schema.Data.Columns.CHAN_NAME + " AND " +
				"b." + Schema.Boards.Columns.BOARD_NAME + " = d." + Schema.Data.Columns.BOARD_NAME + " AND " +
				"d." + Schema.Data.Columns.NAME + " = ? " +
				"WHERE " + filter.value + " " +
				"ORDER BY b.rowid ASC", args, signal);
		return new BoardCursor(cursor, count > 0, filtered, provider, null);
	}

	private Cursor getBoards(@NonNull String chanName, @NonNull List<String> boardNames, Expression.Filter filter,
			@NonNull String extra1Name, @NonNull String extra2Name, CancellationSignal signal) {
		String[] args = new String[boardNames.size() + 4 + (filter.args != null ? filter.args.length : 0)];
		StringBuilder valuesBuilder = new StringBuilder();
		for (int i = 0; i < boardNames.size(); i++) {
			boolean first = valuesBuilder.length() == 0;
			if (supportsCte) {
				if (first) {
					valuesBuilder.append("VALUES");
				} else {
					valuesBuilder.append(',');
				}
				valuesBuilder.append(" (?)");
			} else {
				if (!first) {
					valuesBuilder.append(" UNION ALL ");
				}
				valuesBuilder.append("SELECT ?");
				if (first) {
					valuesBuilder.append(" AS ").append(Schema.Boards.Columns.BOARD_NAME);
				}
			}
			args[i] = boardNames.get(i);
		}
		args[boardNames.size()] = chanName;
		args[boardNames.size() + 1] = extra1Name;
		args[boardNames.size() + 2] = chanName;
		args[boardNames.size() + 3] = extra2Name;
		if (filter.args != null) {
			System.arraycopy(filter.args, 0, args, boardNames.size() + 4, filter.args.length);
		}
		Cursor cursor = database.rawQuery((supportsCte ? "WITH " + Schema.Boards.TABLE_NAME + " (" +
				Schema.Boards.Columns.BOARD_NAME + ") AS (" + valuesBuilder + ") " : "") +
				"SELECT b." + Schema.Boards.Columns.BOARD_NAME + ", " +
				"d1." + Schema.Data.Columns.VALUE + " AS " + Schema.Extra.Columns.EXTRA1 + ", " +
				"d2." + Schema.Data.Columns.VALUE + " AS " + Schema.Extra.Columns.EXTRA2 + " " +
				"FROM " + (supportsCte ? Schema.Boards.TABLE_NAME : "(" + valuesBuilder + ")") + " AS b " +
				"LEFT JOIN " + Schema.Data.TABLE_NAME + " AS d1 " +
				"ON d1." + Schema.Data.Columns.CHAN_NAME + " = ? AND " +
				"b." + Schema.Boards.Columns.BOARD_NAME + " = d1." + Schema.Data.Columns.BOARD_NAME + " AND " +
				"d1." + Schema.Data.Columns.NAME + " = ? " +
				"LEFT JOIN " + Schema.Data.TABLE_NAME + " AS d2 " +
				"ON d2." + Schema.Data.Columns.CHAN_NAME + " = ? AND " +
				"b." + Schema.Boards.Columns.BOARD_NAME + " = d2." + Schema.Data.Columns.BOARD_NAME + " AND " +
				"d2." + Schema.Data.Columns.NAME + " = ? " +
				"WHERE " + (filter.value != null ? filter.value : "1"), args, signal);
		if (cursor.getCount() > 0) {
			return cursor;
		} else {
			cursor.close();
			return null;
		}
	}

	public BoardCursor getBoards(@NonNull String chanName, @NonNull List<String> boardNames,
			String searchQuery, @NonNull String extra1Name, @NonNull String extra2Name,
			BoardExtraFallbackProvider provider1, BoardExtraFallbackProvider provider2,
			CancellationSignal signal) throws OperationCanceledException {
		Expression.Filter.Builder filterBuilder = Expression.filterOr();
		boolean filtered = false;
		if (!StringUtils.isEmpty(searchQuery)) {
			filterBuilder.like("b." + Schema.Boards.Columns.BOARD_NAME, "%" + searchQuery + "%");
			filterBuilder.like("d1." + Schema.Data.Columns.VALUE, "%" + searchQuery + "%");
			filtered = true;
		}
		Expression.Filter filter = filterBuilder.build();
		int limit = 500;
		ArrayList<Cursor> cursors = new ArrayList<>();
		boolean success = false;
		try {
			for (int i = 0; i < boardNames.size(); i += limit) {
				Cursor cursor = getBoards(chanName, boardNames.subList(i, Math.min(i + limit, boardNames.size())),
						filter, extra1Name, extra2Name, signal);
				if (cursor != null) {
					cursors.add(cursor);
				}
			}
			success = true;
		} finally {
			if (!success) {
				for (Cursor cursor : cursors) {
					cursor.close();
				}
			}
		}
		return new BoardCursor(cursors.isEmpty() ? new MatrixCursor(new String[0], 0) :
				new MergeCursor(CommonUtils.toArray(cursors, Cursor.class)),
				!boardNames.isEmpty(), filtered, provider1, provider2);
	}

	private final HashMap<String, LruCache<DataKey, String>> dataCacheMap = new HashMap<>();

	public void setData(@NonNull String chanName, Map<DataKey, Object> map) {
		Objects.requireNonNull(chanName);
		if (map != null && !map.isEmpty()) {
			Expression.Filter filter = Expression.filter()
					.equals(Schema.Data.Columns.CHAN_NAME, chanName)
					.equals(Schema.Data.Columns.BOARD_NAME, "")
					.equals(Schema.Data.Columns.NAME, "")
					.build();
			database.beginTransaction();
			try {
				int totalReplace = 0;
				for (Object value : map.values()) {
					if (value != null) {
						totalReplace++;
					}
				}
				Iterator<Map.Entry<DataKey, Object>> replaceIterator = map.entrySet().iterator();
				Expression.batchInsert(totalReplace, 100, 4,
						values -> database.compileStatement("INSERT OR REPLACE " +
								"INTO " + Schema.Data.TABLE_NAME + " (" +
								Schema.Data.Columns.CHAN_NAME + ", " +
								Schema.Data.Columns.BOARD_NAME + ", " +
								Schema.Data.Columns.NAME + ", " +
								Schema.Data.Columns.VALUE + ") " +
								"VALUES " + values),
						(statement, start) -> {
							Map.Entry<DataKey, Object> entry;
							do {
								entry = replaceIterator.next();
							} while (entry.getValue() == null);
							DataKey dataKey = entry.getKey();
							Object value = entry.getValue();
							statement.bindString(start + 1, chanName);
							statement.bindString(start + 2, dataKey.boardName);
							statement.bindString(start + 3, dataKey.name);
							if (value instanceof Boolean) {
								statement.bindLong(start + 4, (boolean) value ? 1 : 0);
							} else if (value instanceof Integer) {
								statement.bindLong(start + 4, (int) value);
							} else {
								statement.bindString(start + 4, value.toString());
							}
						});
				for (Map.Entry<DataKey, Object> entry : map.entrySet()) {
					if (entry.getValue() == null) {
						DataKey dataKey = entry.getKey();
						filter.args[1] = dataKey.boardName;
						filter.args[2] = dataKey.name;
						database.delete(Schema.Data.TABLE_NAME, filter.value, filter.args);
					}
				}
				database.setTransactionSuccessful();
			} finally {
				database.endTransaction();
				synchronized (dataCacheMap) {
					LruCache<DataKey, String> dataCache = dataCacheMap.get(chanName);
					if (dataCache != null) {
						dataCache.keySet().removeAll(map.keySet());
					}
				}
			}
		}
	}

	public String getData(@NonNull String chanName, @NonNull String boardName, @NonNull String name) {
		Objects.requireNonNull(chanName);
		Objects.requireNonNull(boardName);
		Objects.requireNonNull(name);
		DataKey dataKey = new DataKey(boardName, name);
		synchronized (dataCacheMap) {
			LruCache<DataKey, String> dataCache = dataCacheMap.get(chanName);
			if (dataCache != null && dataCache.containsKey(dataKey)) {
				return dataCache.get(dataKey);
			}
		}
		Expression.Filter filter = Expression.filter()
				.equals(Schema.Data.Columns.CHAN_NAME, chanName)
				.equals(Schema.Data.Columns.BOARD_NAME, boardName)
				.equals(Schema.Data.Columns.NAME, name)
				.build();
		String value;
		String[] projection = {Schema.Data.Columns.VALUE};
		try (Cursor cursor = database.query(Schema.Data.TABLE_NAME, projection,
				filter.value, filter.args, null, null, null)) {
			value = cursor.moveToFirst() ? cursor.getString(0) : null;
		}
		synchronized (dataCacheMap) {
			LruCache<DataKey, String> dataCache = dataCacheMap.get(chanName);
			if (dataCache == null) {
				dataCache = new LruCache<>(MainApplication.getInstance().isLowRam() ? 20 : 50);
				dataCacheMap.put(chanName, dataCache);
			}
			dataCache.put(dataKey, value);
		}
		return value;
	}

	public void setCookie(@NonNull String chanName, @NonNull String name, String value, String title) {
		Objects.requireNonNull(chanName);
		Objects.requireNonNull(name);
		Expression.Filter filter = Expression.filter()
				.equals(Schema.Cookies.Columns.CHAN_NAME, chanName)
				.equals(Schema.Cookies.Columns.NAME, name)
				.build();
		database.beginTransaction();
		try {
			if (value != null) {
				String[] args = new String[filter.args.length + (title != null ? 2 : 1)];
				args[0] = value;
				if (title != null) {
					args[1] = title;
				}
				System.arraycopy(filter.args, 0, args, args.length - filter.args.length, filter.args.length);
				database.execSQL("UPDATE " + Schema.Cookies.TABLE_NAME + " " +
						"SET " + Schema.Cookies.Columns.VALUE + " = ?, " +
						(title != null ? Schema.Cookies.Columns.TITLE + " = ?, " : "") +
						Schema.Cookies.Columns.FLAGS + " = " +
						Schema.Cookies.Columns.FLAGS + " & " + ~Schema.Cookies.Flags.DELETED + " " +
						"WHERE " + filter.value, args);
				boolean updated;
				try (Cursor cursor = database.rawQuery("SELECT CHANGES()", null)) {
					updated = cursor.moveToFirst() && cursor.getInt(0) > 0;
				}
				if (!updated) {
					ContentValues values = new ContentValues();
					values.put(Schema.Cookies.Columns.CHAN_NAME, chanName);
					values.put(Schema.Cookies.Columns.NAME, name);
					values.put(Schema.Cookies.Columns.VALUE, value);
					values.put(Schema.Cookies.Columns.TITLE, StringUtils.emptyIfNull(title));
					database.insert(Schema.Cookies.TABLE_NAME, null, values);
				}
			} else {
				database.execSQL("UPDATE " + Schema.Cookies.TABLE_NAME + " " +
						"SET " + Schema.Cookies.Columns.VALUE + " = '', " +
						Schema.Cookies.Columns.FLAGS + " = " +
						Schema.Cookies.Columns.FLAGS + " | " + Schema.Cookies.Flags.DELETED + " " +
						"WHERE " + filter.value, filter.args);
				database.delete(Schema.Cookies.TABLE_NAME, "(" + filter.value + ") AND " +
						Schema.Cookies.Columns.FLAGS + " = " + Schema.Cookies.Flags.DELETED, filter.args);
			}
			database.setTransactionSuccessful();
		} finally {
			database.endTransaction();
		}
	}

	public void setCookieState(@NonNull String chanName, @NonNull String name, Boolean blocked, Boolean deleteOnExit) {
		Objects.requireNonNull(chanName);
		Objects.requireNonNull(name);
		if (blocked == null && deleteOnExit == null) {
			return;
		}
		Expression.Filter filter = Expression.filter()
				.equals(Schema.Cookies.Columns.CHAN_NAME, chanName)
				.equals(Schema.Cookies.Columns.NAME, name)
				.build();
		database.beginTransaction();
		try {
			int clearFlags = (blocked != null ? Schema.Cookies.Flags.BLOCKED : 0) |
					(deleteOnExit != null ? Schema.Cookies.Flags.DELETE_ON_EXIT : 0);
			int setFlags = (blocked != null && blocked ? Schema.Cookies.Flags.BLOCKED : 0) |
					(deleteOnExit != null && deleteOnExit ? Schema.Cookies.Flags.DELETE_ON_EXIT : 0);
			database.execSQL("UPDATE " + Schema.Cookies.TABLE_NAME + " " +
					"SET " + Schema.Cookies.Columns.FLAGS + " = " +
					Schema.Cookies.Columns.FLAGS + " & " + ~clearFlags + " | " + setFlags + " " +
					"WHERE " + filter.value, filter.args);
			if (setFlags == 0) {
				boolean delete;
				String[] projection = {Schema.Cookies.Columns.FLAGS};
				try (Cursor cursor = database.query(Schema.Cookies.TABLE_NAME, projection,
						filter.value, filter.args, null, null, null)) {
					delete = cursor.moveToFirst() && cursor.getInt(0) == Schema.Cookies.Flags.DELETED;
				}
				if (delete) {
					database.delete(Schema.Cookies.TABLE_NAME, filter.value, filter.args);
				}
			}
			database.setTransactionSuccessful();
		} finally {
			database.endTransaction();
		}
	}

	private void deleteCookiesOnExit() {
		database.execSQL("UPDATE " + Schema.Cookies.TABLE_NAME + " " +
				"SET " + Schema.Cookies.Columns.VALUE + " = '', " +
				Schema.Cookies.Columns.FLAGS + " = " +
				Schema.Cookies.Columns.FLAGS + " | " + Schema.Cookies.Flags.DELETED + " " +
				"WHERE " + Schema.Cookies.Columns.FLAGS + " & " + Schema.Cookies.Flags.DELETE_ON_EXIT);
	}

	private final AtomicInteger requireCookiesReferenceCount = new AtomicInteger(0);

	public Runnable requireCookies() {
		requireCookiesReferenceCount.incrementAndGet();
		return () -> {
			if (requireCookiesReferenceCount.decrementAndGet() == 0) {
				deleteCookiesOnExit();
			}
		};
	}

	public String getCookieChecked(@NonNull String chanName, @NonNull String name) {
		Objects.requireNonNull(chanName);
		Objects.requireNonNull(name);
		Expression.Filter filter = Expression.filter()
				.equals(Schema.Cookies.Columns.CHAN_NAME, chanName)
				.equals(Schema.Cookies.Columns.NAME, name)
				.raw("NOT (" + Schema.Cookies.Columns.FLAGS + " & " + Schema.Cookies.Flags.DELETED + ")")
				.raw("NOT (" + Schema.Cookies.Columns.FLAGS + " & " + Schema.Cookies.Flags.BLOCKED + ")")
				.build();
		String[] projection = {Schema.Cookies.Columns.VALUE};
		try (Cursor cursor = database.query(Schema.Cookies.TABLE_NAME, projection,
				filter.value, filter.args, null, null, null)) {
			if (cursor.moveToFirst()) {
				return cursor.getString(0);
			}
		}
		return null;
	}

	public boolean hasCookies(@NonNull String chanName) {
		String[] projection = {"1"};
		Expression.Filter filter = Expression.filter()
				.equals(Schema.Cookies.Columns.CHAN_NAME, chanName)
				.build();
		try (Cursor cursor = database.query(Schema.Cookies.TABLE_NAME, projection,
				filter.value, filter.args, null, null, null, "1")) {
			return cursor.moveToFirst();
		}
	}

	public CookieCursor getCookies(@NonNull String chanName) {
		String[] projection = {"rowid", "*"};
		Expression.Filter filter = Expression.filter()
				.equals(Schema.Cookies.Columns.CHAN_NAME, chanName)
				.build();
		return new CookieCursor(database.query(Schema.Cookies.TABLE_NAME, projection,
				filter.value, filter.args, null, null, null));
	}
}
