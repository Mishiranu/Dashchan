package com.mishiranu.dashchan.content.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.util.Pair;
import androidx.annotation.NonNull;
import chan.util.StringUtils;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.content.model.PostNumber;
import com.mishiranu.dashchan.util.FlagUtils;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;

public class PostsDatabase implements CommonDatabase.Instance {
	private interface Schema {
		interface Posts {
			String TABLE_NAME = "posts";
			int MAX_COUNT = 10000;
			float MAX_COUNT_FACTOR = 0.75f;

			interface Columns {
				String CHAN_NAME = "chan_name";
				String BOARD_NAME = "board_name";
				String THREAD_NUMBER = "thread_number";
				String POST_NUMBER_MAJOR = "post_number_major";
				String POST_NUMBER_MINOR = "post_number_minor";
				String TIME = "time";
				String FLAGS = "flags";
			}

			interface Flags {
				int HIDDEN = 0x00000001;
				int SHOWN = 0x00000002;
				int USER = 0x00000004;
			}
		}
	}

	public static class Flags {
		public final PostItem.HideState.Map<PostNumber> hiddenPosts;
		public final HashSet<PostNumber> userPosts;

		public Flags(PostItem.HideState.Map<PostNumber> hiddenPosts, HashSet<PostNumber> userPosts) {
			this.hiddenPosts = hiddenPosts;
			this.userPosts = userPosts;
		}
	}

	private final CommonDatabase database;

	PostsDatabase(CommonDatabase database) {
		this.database = database;
	}

	@Override
	public void create(SQLiteDatabase database) {
		database.execSQL("CREATE TABLE " + Schema.Posts.TABLE_NAME + " (" +
				Schema.Posts.Columns.CHAN_NAME + " TEXT NOT NULL, " +
				Schema.Posts.Columns.BOARD_NAME + " TEXT NOT NULL, " +
				Schema.Posts.Columns.THREAD_NUMBER + " TEXT NOT NULL, " +
				Schema.Posts.Columns.POST_NUMBER_MAJOR + " INTEGER NOT NULL, " +
				Schema.Posts.Columns.POST_NUMBER_MINOR + " INTEGER NOT NULL, " +
				Schema.Posts.Columns.TIME + " INTEGER NOT NULL, " +
				Schema.Posts.Columns.FLAGS + " INTEGER NOT NULL DEFAULT 0, " +
				"PRIMARY KEY (" + Schema.Posts.Columns.CHAN_NAME + ", " +
				Schema.Posts.Columns.BOARD_NAME + ", " +
				Schema.Posts.Columns.THREAD_NUMBER + ", " +
				Schema.Posts.Columns.POST_NUMBER_MAJOR + ", " +
				Schema.Posts.Columns.POST_NUMBER_MINOR + "))");
	}

	@Override
	public void upgrade(SQLiteDatabase database, CommonDatabase.Migration migration) {
		switch (migration) {
			case FROM_8_TO_9: {
				// Add "posts" table
				database.execSQL("CREATE TABLE posts (chan_name TEXT NOT NULL, " +
						"board_name TEXT NOT NULL, thread_number TEXT NOT NULL, " +
						"post_number_major INTEGER NOT NULL, post_number_minor INTEGER NOT NULL, " +
						"time INTEGER NOT NULL, flags INTEGER NOT NULL DEFAULT 0, " +
						"PRIMARY KEY (chan_name, board_name, thread_number, " +
						"post_number_major, post_number_minor))");
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
		try (Cursor cursor = database.rawQuery("SELECT COUNT(*) FROM " + Schema.Posts.TABLE_NAME, null)) {
			clean = cursor.moveToFirst() && cursor.getInt(0) > Schema.Posts.MAX_COUNT;
		}
		if (clean) {
			Long time;
			String[] projection = {Schema.Posts.Columns.TIME};
			try (Cursor cursor = database.query(Schema.Posts.TABLE_NAME,
					projection, null, null, null, null, Schema.Posts.Columns.TIME + " DESC",
					(int) (Schema.Posts.MAX_COUNT_FACTOR * Schema.Posts.MAX_COUNT) + ", 1")) {
				time = cursor.moveToFirst() ? cursor.getLong(0) : null;
			}
			if (time != null) {
				database.delete(Schema.Posts.TABLE_NAME, Schema.Posts.Columns.TIME + " <= " + time, null);
			}
		}
	}

	void setFlagsMigration(@NonNull String chanName, String boardName, @NonNull String threadNumber,
			@NonNull Map<PostNumber, Pair<PostItem.HideState, Boolean>> flagsMap, long time) {
		Objects.requireNonNull(chanName);
		Objects.requireNonNull(threadNumber);
		Objects.requireNonNull(flagsMap);
		database.execute(database -> {
			database.beginTransaction();
			try {
				SQLiteStatement statement = database.compileStatement("INSERT OR REPLACE " +
						"INTO " + Schema.Posts.TABLE_NAME + " (" +
						Schema.Posts.Columns.CHAN_NAME + ", " +
						Schema.Posts.Columns.BOARD_NAME + ", " +
						Schema.Posts.Columns.THREAD_NUMBER + ", " +
						Schema.Posts.Columns.POST_NUMBER_MAJOR + ", " +
						Schema.Posts.Columns.POST_NUMBER_MINOR + ", " +
						Schema.Posts.Columns.TIME + ", " +
						Schema.Posts.Columns.FLAGS + ") " +
						"VALUES (?, ?, ?, ?, ?, ?, ?)");
				statement.bindString(1, chanName);
				statement.bindString(2, StringUtils.emptyIfNull(boardName));
				statement.bindString(3, threadNumber);
				statement.bindLong(6, time);
				for (Map.Entry<PostNumber, Pair<PostItem.HideState, Boolean>> entry : flagsMap.entrySet()) {
					PostNumber postNumber = entry.getKey();
					PostItem.HideState hideState = entry.getValue().first;
					boolean userPost = entry.getValue().second;
					int flags = 0;
					if (hideState == PostItem.HideState.HIDDEN) {
						flags |= Schema.Posts.Flags.HIDDEN;
					} else if (hideState == PostItem.HideState.SHOWN) {
						flags |= Schema.Posts.Flags.SHOWN;
					}
					if (userPost) {
						flags |= Schema.Posts.Flags.USER;
					}
					if (flags != 0) {
						statement.bindLong(4, postNumber.major);
						statement.bindLong(5, postNumber.minor);
						statement.bindLong(7, flags);
						statement.execute();
					}
				}
				database.setTransactionSuccessful();
			} finally {
				database.endTransaction();
			}
			return null;
		});
	}

	public void setFlags(boolean async, @NonNull String chanName, String boardName, @NonNull String threadNumber,
			@NonNull PostNumber postNumber, PostItem.HideState hideState, boolean userPost) {
		Objects.requireNonNull(chanName);
		Objects.requireNonNull(threadNumber);
		Objects.requireNonNull(postNumber);
		CommonDatabase.ExecuteCallback<Void> callback = database -> {
			int flags = 0;
			if (hideState == PostItem.HideState.HIDDEN) {
				flags |= Schema.Posts.Flags.HIDDEN;
			} else if (hideState == PostItem.HideState.SHOWN) {
				flags |= Schema.Posts.Flags.SHOWN;
			}
			if (userPost) {
				flags |= Schema.Posts.Flags.USER;
			}
			if (flags != 0) {
				ContentValues values = new ContentValues();
				values.put(Schema.Posts.Columns.CHAN_NAME, chanName);
				values.put(Schema.Posts.Columns.BOARD_NAME, StringUtils.emptyIfNull(boardName));
				values.put(Schema.Posts.Columns.THREAD_NUMBER, threadNumber);
				values.put(Schema.Posts.Columns.POST_NUMBER_MAJOR, postNumber.major);
				values.put(Schema.Posts.Columns.POST_NUMBER_MINOR, postNumber.minor);
				values.put(Schema.Posts.Columns.TIME, System.currentTimeMillis());
				values.put(Schema.Posts.Columns.FLAGS, flags);
				database.replace(Schema.Posts.TABLE_NAME, null, values);
			} else {
				Expression.Filter filter = Expression.filter()
						.equals(Schema.Posts.Columns.CHAN_NAME, chanName)
						.equals(Schema.Posts.Columns.BOARD_NAME, StringUtils.emptyIfNull(boardName))
						.equals(Schema.Posts.Columns.THREAD_NUMBER, threadNumber)
						.raw(Schema.Posts.Columns.POST_NUMBER_MAJOR + " = " + postNumber.major)
						.raw(Schema.Posts.Columns.POST_NUMBER_MINOR + " = " + postNumber.minor)
						.build();
				database.delete(Schema.Posts.TABLE_NAME, filter.value, filter.args);
			}
			return null;
		};
		if (async) {
			database.enqueue(callback);
		} else {
			database.execute(callback);
		}
	}

	public Flags getFlags(@NonNull String chanName, String boardName, @NonNull String threadNumber) {
		Objects.requireNonNull(chanName);
		Objects.requireNonNull(threadNumber);
		String[] projection = {Schema.Posts.Columns.POST_NUMBER_MAJOR, Schema.Posts.Columns.POST_NUMBER_MINOR,
				Schema.Posts.Columns.FLAGS};
		Expression.Filter filter = Expression.filter()
				.equals(Schema.Posts.Columns.CHAN_NAME, chanName)
				.equals(Schema.Posts.Columns.BOARD_NAME, StringUtils.emptyIfNull(boardName))
				.equals(Schema.Posts.Columns.THREAD_NUMBER, threadNumber)
				.raw(Schema.Posts.Columns.FLAGS)
				.build();
		PostItem.HideState.Map<PostNumber> hiddenPosts = new PostItem.HideState.Map<>();
		HashSet<PostNumber> userPosts = new HashSet<>();
		try (Cursor cursor = database.query(database -> database
				.query(Schema.Posts.TABLE_NAME, projection, filter.value, filter.args, null, null, null))) {
			while (cursor.moveToNext()) {
				PostNumber postNumber = new PostNumber(cursor.getInt(0), cursor.getInt(1));
				int flags = cursor.getInt(2);
				if (FlagUtils.get(flags, Schema.Posts.Flags.HIDDEN)) {
					hiddenPosts.set(postNumber, PostItem.HideState.HIDDEN);
				} else if (FlagUtils.get(flags, Schema.Posts.Flags.SHOWN)) {
					hiddenPosts.set(postNumber, PostItem.HideState.SHOWN);
				}
				if (FlagUtils.get(flags, Schema.Posts.Flags.USER)) {
					userPosts.add(postNumber);
				}
			}
		}
		if (hiddenPosts.size() > 0 || !userPosts.isEmpty()) {
			database.execute(database -> {
				ContentValues values = new ContentValues();
				values.put(Schema.Posts.Columns.TIME, System.currentTimeMillis());
				database.update(Schema.Posts.TABLE_NAME, values, filter.value, filter.args);
				return null;
			});
		}
		return new Flags(hiddenPosts, userPosts);
	}
}
