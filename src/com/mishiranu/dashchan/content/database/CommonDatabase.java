package com.mishiranu.dashchan.content.database;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.mishiranu.dashchan.content.MainApplication;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;

public class CommonDatabase {
	public enum Migration {FROM_8_TO_9}

	public interface Instance {
		void create(SQLiteDatabase database);
		void upgrade(SQLiteDatabase database, Migration migration);
		default void open(SQLiteDatabase database) {}
	}

	public interface QueryCallback {
		Cursor query(SQLiteDatabase db);
	}

	public interface ExecuteCallback<T> {
		T run(SQLiteDatabase database);
	}

	private static final Set<String> IGNORE_TABLES = Collections.unmodifiableSet(new HashSet<>(Arrays
			.asList("sqlite_sequence", "sqlite_master", "android_metadata")));

	private static final CommonDatabase INSTANCE = new CommonDatabase();

	public static CommonDatabase getInstance() {
		return INSTANCE;
	}

	private final Executor executor = ConcurrentUtils.newSingleThreadPool(10000, "CommonDatabase", null);

	private final HistoryDatabase historyDatabase;
	private final ThreadsDatabase threadsDatabase;
	private final PostsDatabase postsDatabase;

	private final Helper helper;

	private CommonDatabase() {
		// Rename "dashchan.db" to "common.db"
		File oldFile = MainApplication.getInstance().getDatabasePath("dashchan.db");
		if (oldFile.exists()) {
			File parent = oldFile.getParentFile();
			String oldName = oldFile.getName();
			for (File file : oldFile.getParentFile().listFiles()) {
				String name = file.getName();
				if (name.startsWith(oldName)) {
					file.renameTo(new File(parent, "common.db" + name.substring(oldName.length())));
				}
			}
		}
		historyDatabase = new HistoryDatabase(this);
		threadsDatabase = new ThreadsDatabase(this);
		postsDatabase = new PostsDatabase(this);
		helper = new Helper(Arrays.asList(historyDatabase, threadsDatabase, postsDatabase));
	}

	public HistoryDatabase getHistory() {
		return historyDatabase;
	}

	public ThreadsDatabase getThreads() {
		return threadsDatabase;
	}

	public PostsDatabase getPosts() {
		return postsDatabase;
	}

	public Cursor query(QueryCallback callback) {
		return callback.query(helper.database);
	}

	public <T> T execute(ExecuteCallback<T> callback) {
		return callback.run(helper.database);
	}

	public void enqueue(ExecuteCallback<?> callback) {
		executor.execute(() -> execute(callback));
	}

	private static void copyDatabase(SQLiteDatabase database,
			String fromPrefix, String toPrefix) throws IOException {
		String[] tablesProjection = {"name", "sql"};
		Expression.Filter tablesFilter = Expression.filter().equals("type", "table").build();
		try (Cursor cursor = database.query(fromPrefix + "sqlite_master", tablesProjection,
				tablesFilter.value, tablesFilter.args, null, null, null)) {
			while (cursor.moveToNext()) {
				String name = cursor.getString(0);
				if (!IGNORE_TABLES.contains(name)) {
					String sql = cursor.getString(1);
					int index = sql.indexOf(name);
					if (index < 0) {
						throw new IOException();
					}
					sql = sql.substring(0, index) + toPrefix + sql.substring(index);
					database.execSQL(sql);
					database.execSQL("INSERT INTO " + toPrefix + name + " SELECT * FROM " + fromPrefix + name);
				}
			}
		}
		String[] indexesProjection = {"name", "sql"};
		Expression.Filter indexesFilter = Expression.filter().equals("type", "index").build();
		try (Cursor cursor = database.query(fromPrefix + "sqlite_master", indexesProjection,
				indexesFilter.value, indexesFilter.args, null, null, null)) {
			while (cursor.moveToNext()) {
				String name = cursor.getString(0);
				String sql = cursor.getString(1);
				if (sql != null) {
					int index = sql.indexOf(name);
					if (index < 0) {
						throw new IOException();
					}
					sql = sql.substring(0, index) + toPrefix + sql.substring(index);
					database.execSQL(sql);
				}
			}
		}
	}

	private static void dropAllTables(SQLiteDatabase database) {
		String[] projection = {"name"};
		Expression.Filter filter = Expression.filter().equals("type", "table").build();
		ArrayList<String> names = new ArrayList<>();
		try (Cursor cursor = database.query("sqlite_master",
				projection, filter.value, filter.args, null, null, null)) {
			while (cursor.moveToNext()) {
				names.add(cursor.getString(0));
			}
		}
		for (String name : names) {
			if (!IGNORE_TABLES.contains(name)) {
				database.execSQL("DROP TABLE IF EXISTS " + name);
			}
		}
	}

	public boolean writeBackup(OutputStream output) throws IOException {
		File backupFile = MainApplication.getInstance().getDatabasePath(Helper.DATABASE_BACKUP_NAME);
		try {
			SQLiteDatabase database = helper.database;
			database.execSQL("ATTACH DATABASE ? AS backup", new Object[] {backupFile.getPath()});
			database.beginTransaction();
			try {
				database.execSQL("PRAGMA backup.user_version=" + Helper.DATABASE_VERSION);
				copyDatabase(database, "", "backup.");
				database.setTransactionSuccessful();
			} finally {
				database.endTransaction();
				database.execSQL("DETACH DATABASE backup");
			}
			try (FileInputStream input = new FileInputStream(backupFile)) {
				IOUtils.copyStream(input, output);
			}
			return true;
		} finally {
			for (File file : backupFile.getParentFile().listFiles()) {
				if (file.getName().startsWith(backupFile.getName())) {
					file.delete();
				}
			}
		}
	}

	public void readBackup(InputStream input) throws IOException {
		File restoreFile = MainApplication.getInstance().getDatabasePath(Helper.DATABASE_RESTORE_NAME);
		File[] files = restoreFile.getParentFile().listFiles();
		if (files != null) {
			for (File file : files) {
				if (file.getName().startsWith(restoreFile.getName())) {
					file.delete();
				}
			}
		}
		restoreFile.getParentFile().mkdirs();
		try (FileOutputStream output = new FileOutputStream(restoreFile)) {
			IOUtils.copyStream(input, output);
		}
	}

	private static class Helper extends SQLiteOpenHelper {
		private static final String DATABASE_NAME = "common.db";
		private static final String DATABASE_BACKUP_NAME = "common.backup.db";
		private static final String DATABASE_RESTORE_NAME = "common.restore.db";
		private static final int DATABASE_VERSION = 9;

		private final Collection<Instance> instances;
		private final SQLiteDatabase database;

		private Helper(Collection<Instance> instances) {
			super(MainApplication.getInstance(), DATABASE_NAME, null, DATABASE_VERSION);
			setWriteAheadLoggingEnabled(false);
			this.instances = instances;
			database = getWritableDatabase();
		}

		@Override
		public void onConfigure(SQLiteDatabase db) {
			File restoreFile = MainApplication.getInstance().getDatabasePath(DATABASE_RESTORE_NAME);
			if (restoreFile.exists()) {
				try {
					db.execSQL("ATTACH DATABASE ? AS restore", new Object[] {restoreFile.getPath()});
					db.beginTransaction();
					try {
						int version;
						try (Cursor cursor = db.rawQuery("PRAGMA restore.user_version", null)) {
							version = cursor.moveToFirst() ? cursor.getInt(0) : 0;
						}
						if (version > 0) {
							dropAllTables(db);
							db.execSQL("PRAGMA user_version=" + version);
							copyDatabase(db, "restore.", "");
							db.setTransactionSuccessful();
						}
					} finally {
						db.endTransaction();
						db.execSQL("DETACH DATABASE restore");
					}
				} catch (IOException e) {
					Log.persistent().stack(e);
				} finally {
					for (File file : restoreFile.getParentFile().listFiles()) {
						if (file.getName().startsWith(restoreFile.getName())) {
							file.delete();
						}
					}
				}
			}
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			for (Instance instance : instances) {
				instance.create(db);
			}
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			if (oldVersion <= 7) {
				dropAllTables(db);
				onCreate(db);
			} else {
				switch (oldVersion) {
					case 8: {
						for (Instance instance : instances) {
							instance.upgrade(db, Migration.FROM_8_TO_9);
						}
					}
				}
			}
		}

		@Override
		public void onOpen(SQLiteDatabase db) {
			for (Instance instance : instances) {
				instance.open(db);
			}
		}
	}
}
