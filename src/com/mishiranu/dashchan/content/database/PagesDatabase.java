package com.mishiranu.dashchan.content.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;
import android.util.LongSparseArray;
import android.util.Pair;
import androidx.annotation.NonNull;
import chan.http.HttpValidator;
import chan.text.JsonSerial;
import chan.text.ParseException;
import chan.util.StringUtils;
import com.mishiranu.dashchan.content.HidePerformer;
import com.mishiranu.dashchan.content.MainApplication;
import com.mishiranu.dashchan.content.model.Post;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.content.model.PostNumber;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.FlagUtils;
import com.mishiranu.dashchan.util.Hasher;
import com.mishiranu.dashchan.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class PagesDatabase {
	private interface Schema {
		interface Meta {
			String TABLE_NAME = "meta";

			interface Columns {
				String CHAN_NAME = "chan_name";
				String BOARD_NAME = "board_name";
				String THREAD_NUMBER = "thread_number";
				String TIME = "time";
				String FLAGS = "flags";
				String DATA = "data";
			}

			interface Flags {
				int DELETED = 0x00000001;
				int ERROR = 0x00000002;
			}
		}

		interface Posts {
			String TABLE_NAME = "posts";
			int MAX_COUNT = 250000;
			float MAX_COUNT_FACTOR = 0.75f;

			interface Columns {
				String CHAN_NAME = "chan_name";
				String BOARD_NAME = "board_name";
				String THREAD_NUMBER = "thread_number";
				String POST_NUMBER_MAJOR = "post_number_major";
				String POST_NUMBER_MINOR = "post_number_minor";
				String FLAGS = "flags";
				String DATA = "data";
				String HASH = "hash";
			}

			interface Flags {
				int DELETED = 0x00000001;
				int MARK_NEW = 0x00000002;
				int MARK_DELETED = 0x00000004;
				int MARK_EDITED = 0x00000008;
				int MARK_REPLY = 0x00000010;
			}
		}
	}

	public static class Meta {
		public final HttpValidator validator;
		public final Uri archivedThreadUri;
		public final int uniquePosters;
		public final boolean deleted;
		public final boolean error;

		public Meta(HttpValidator validator, Uri archivedThreadUri, int uniquePosters,
				boolean deleted, boolean error) {
			this.validator = validator;
			this.archivedThreadUri = archivedThreadUri;
			this.uniquePosters = uniquePosters;
			this.deleted = deleted;
			this.error = error;
		}

		public void serialize(JsonSerial.Writer writer) throws IOException {
			writer.startObject();
			if (validator != null) {
				writer.name("validator");
				validator.serialize(writer);
			}
			if (archivedThreadUri != null) {
				writer.name("archivedThreadUri");
				writer.value(archivedThreadUri.toString());
			}
			if (uniquePosters > 0) {
				writer.name("uniquePosters");
				writer.value(uniquePosters);
			}
			writer.endObject();
		}

		public static Meta deserialize(JsonSerial.Reader reader,
				boolean deleted, boolean error) throws IOException, ParseException {
			HttpValidator validator = null;
			Uri archivedThreadUri = null;
			int uniquePosters = 0;
			reader.startObject();
			while (!reader.endStruct()) {
				switch (reader.nextName()) {
					case "validator": {
						validator = HttpValidator.deserialize(reader);
						break;
					}
					case "archivedThreadUri": {
						archivedThreadUri = Uri.parse(reader.nextString());
						break;
					}
					case "uniquePosters": {
						uniquePosters = reader.nextInt();
						break;
					}
					default: {
						reader.skip();
						break;
					}
				}
			}
			return new Meta(validator, archivedThreadUri, uniquePosters, deleted, error);
		}
	}

	public static class ThreadSummary {
		public final PostNumber originalPostNumber;
		public final PostNumber lastExistingPostNumber;
		public final boolean cyclical;

		public ThreadSummary(PostNumber originalPostNumber, PostNumber lastExistingPostNumber, boolean cyclical) {
			this.originalPostNumber = originalPostNumber;
			this.lastExistingPostNumber = lastExistingPostNumber;
			this.cyclical = cyclical;
		}
	}

	public static class WatcherState {
		public final int newCount;
		public final boolean deleted;
		public final boolean error;
		public final long time;

		public WatcherState(int newCount, boolean deleted, boolean error, long time) {
			this.newCount = newCount;
			this.deleted = deleted;
			this.error = error;
			this.time = time;
		}
	}

	public static class ThreadKey {
		public final @NonNull String chanName;
		public final @NonNull String boardName;
		public final @NonNull String threadNumber;

		public ThreadKey(@NonNull String chanName, String boardName, @NonNull String threadNumber) {
			Objects.requireNonNull(chanName);
			Objects.requireNonNull(threadNumber);
			this.chanName = chanName;
			this.boardName = StringUtils.emptyIfNull(boardName);
			this.threadNumber = threadNumber;
		}

		private Expression.Filter.Builder filterMeta() {
			return Expression.filter()
					.equals(Schema.Meta.Columns.CHAN_NAME, chanName)
					.equals(Schema.Meta.Columns.BOARD_NAME, boardName)
					.equals(Schema.Meta.Columns.THREAD_NUMBER, threadNumber);
		}

		private Expression.Filter.Builder filterPosts() {
			return Expression.filter()
					.equals(Schema.Posts.Columns.CHAN_NAME, chanName)
					.equals(Schema.Posts.Columns.BOARD_NAME, boardName)
					.equals(Schema.Posts.Columns.THREAD_NUMBER, threadNumber);
		}

		@Override
		public boolean equals(Object o) {
			if (o == this) {
				return true;
			}
			if (o instanceof ThreadKey) {
				ThreadKey threadKey = (ThreadKey) o;
				return chanName.equals(threadKey.chanName) &&
						boardName.equals(threadKey.boardName) &&
						threadNumber.equals(threadKey.threadNumber);
			}
			return false;
		}

		@Override
		public int hashCode() {
			int prime = 31;
			int result = 1;
			result = prime * result + chanName.hashCode();
			result = prime * result + boardName.hashCode();
			result = prime * result + threadNumber.hashCode();
			return result;
		}
	}

	private static class Serialized {
		public final Post post;
		public final byte[] data;
		public final byte[] hash;
		public int flags;

		private Serialized(Post post, byte[] data, byte[] hash, boolean newThread) {
			this.post = post;
			this.data = data;
			this.hash = hash;
			flags = newThread ? 0 : Schema.Posts.Flags.MARK_NEW;
		}
	}

	private static class DiffItem {
		public final byte[] hash;
		public final boolean deleted;

		private DiffItem(byte[] hash, boolean deleted) {
			this.hash = hash;
			this.deleted = deleted;
		}
	}

	private static class Extracted {
		public final byte[] data;
		public final PostNumber postNumber;
		public final boolean deleted;

		private Extracted(byte[] data, PostNumber postNumber, boolean deleted) {
			this.data = data;
			this.postNumber = postNumber;
			this.deleted = deleted;
		}
	}

	public static class Cache {
		public static class State {
			private final UUID id;
			private boolean newThread;

			private State(UUID id, boolean newThread) {
				this.id = id;
				this.newThread = newThread;
			}

			private boolean isNewThreadOnce() {
				if (newThread) {
					synchronized (this) {
						if (newThread) {
							newThread = false;
							return true;
						}
					}
				}
				return false;
			}

			@Override
			public boolean equals(Object o) {
				if (o == this) {
					return true;
				}
				if (o instanceof State) {
					return ((State) o).id.equals(id);
				}
				return false;
			}

			@Override
			public int hashCode() {
				return id.hashCode();
			}
		}

		private final Map<PostNumber, DiffItem> diffItems;
		public final PostNumber originalPostNumber;
		public final State state;

		private Cache(Map<PostNumber, DiffItem> diffItems, PostNumber originalPostNumber, State state) {
			this.diffItems = diffItems;
			this.originalPostNumber = originalPostNumber;
			this.state = state;
		}

		public boolean isEmpty() {
			return diffItems.isEmpty();
		}

		public boolean isChanged(Cache oldCache) {
			// Compare references
			return oldCache == null || oldCache.diffItems != diffItems;
		}

		public boolean isNewThreadOnce() {
			return state.isNewThreadOnce();
		}
	}

	public static class InsertResult {
		public static class Reply {
			public final PostNumber postNumber;
			public final String comment;
			public final long timestamp;

			public Reply(PostNumber postNumber, String comment, long timestamp) {
				this.postNumber = postNumber;
				this.comment = comment;
				this.timestamp = timestamp;
			}
		}

		public final Cache.State cacheState;
		public final List<Reply> replies;
		public final int newCount;

		public InsertResult(Cache.State cacheState, List<Reply> replies, int newCount) {
			this.cacheState = cacheState;
			this.replies = replies;
			this.newCount = newCount;
		}
	}

	public static class Diff {
		public final Cache cache;
		public final Collection<Post> changed;
		public final Collection<PostNumber> removed;

		public final Set<PostNumber> newPosts;
		public final Set<PostNumber> deletedPosts;
		public final Set<PostNumber> editedPosts;
		public final Set<PostNumber> replyPosts;

		public Diff(Cache cache, Collection<Post> changed, Collection<PostNumber> removed,
				Set<PostNumber> newPosts, Set<PostNumber> deletedPosts,
				Set<PostNumber> editedPosts, Set<PostNumber> replyPosts) {
			this.cache = cache;
			this.changed = changed;
			this.removed = removed;
			this.newPosts = newPosts;
			this.deletedPosts = deletedPosts;
			this.editedPosts = editedPosts;
			this.replyPosts = replyPosts;
		}
	}

	public enum Cleanup {NONE, ERASE, OLD, DELETED}

	private enum MigrationRequest {GET_META, COLLECT_DIFF_POSTS}

	private static final PagesDatabase INSTANCE = new PagesDatabase();

	public static PagesDatabase getInstance() {
		return INSTANCE;
	}

	private final Helper helper = new Helper();
	private final SQLiteDatabase database = helper.getWritableDatabase();

	private PagesDatabase() {
		File directory = getLegacyCacheDirectory();
		if (directory != null) {
			File forceMigrate = new File(directory, "migrate");
			if (forceMigrate.exists()) {
				forceMigrate.delete();
				String[] files = directory.list();
				for (String name : files) {
					if (name.startsWith("posts_")) {
						name = name.substring(6);
						int index1 = name.indexOf('_');
						int index2 = name.lastIndexOf('_');
						if (index2 > index1 && index1 >= 0) {
							String chanName = name.substring(0, index1);
							String boardName = name.substring(index1 + 1, index2);
							if ("null".equals(boardName)) {
								boardName = null;
							}
							String threadNumber = name.substring(index2 + 1);
							migratePosts(new ThreadKey(chanName, boardName, threadNumber), null);
						}
					}
				}
			}
		}

		if (MainApplication.getInstance().isMainProcess()) {
			HashSet<ThreadKey> excludeThreads = new HashSet<>();
			for (FavoritesStorage.FavoriteItem favoriteItem : FavoritesStorage.getInstance().getThreads(null)) {
				excludeThreads.add(new ThreadKey(favoriteItem.chanName,
						StringUtils.emptyIfNull(favoriteItem.boardName), favoriteItem.threadNumber));
			}
			new Thread(() -> cleanup(excludeThreads, false)).start();
		}
	}

	private static class Helper extends SQLiteOpenHelper {
		private static final String DATABASE_NAME = "pages.db";
		private static final int DATABASE_VERSION = 1;

		private Helper() {
			super(MainApplication.getInstance(), DATABASE_NAME, null, DATABASE_VERSION);
			setWriteAheadLoggingEnabled(true);
		}

		@Override
		public void onConfigure(SQLiteDatabase db) {
			db.setForeignKeyConstraintsEnabled(true);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL("CREATE TABLE " + Schema.Meta.TABLE_NAME + " (" +
					Schema.Meta.Columns.CHAN_NAME + " TEXT NOT NULL, " +
					Schema.Meta.Columns.BOARD_NAME + " TEXT NOT NULL, " +
					Schema.Meta.Columns.THREAD_NUMBER + " TEXT NOT NULL, " +
					Schema.Meta.Columns.TIME + " INTEGER NOT NULL, " +
					Schema.Meta.Columns.FLAGS + " INTEGER NOT NULL DEFAULT 0, " +
					Schema.Meta.Columns.DATA + " BLOB NOT NULL, " +
					"PRIMARY KEY (" + Schema.Meta.Columns.CHAN_NAME + ", " +
					Schema.Meta.Columns.BOARD_NAME + ", " +
					Schema.Meta.Columns.THREAD_NUMBER + "))");
			db.execSQL("CREATE INDEX " + Schema.Meta.TABLE_NAME + "_order " +
					"ON " + Schema.Meta.TABLE_NAME + " (" +
					Schema.Meta.Columns.TIME + ")");
			db.execSQL("CREATE TABLE " + Schema.Posts.TABLE_NAME + " (" +
					Schema.Posts.Columns.CHAN_NAME + " TEXT NOT NULL, " +
					Schema.Posts.Columns.BOARD_NAME + " TEXT NOT NULL, " +
					Schema.Posts.Columns.THREAD_NUMBER + " TEXT NOT NULL, " +
					Schema.Posts.Columns.POST_NUMBER_MAJOR + " INTEGER NOT NULL, " +
					Schema.Posts.Columns.POST_NUMBER_MINOR + " INTEGER NOT NULL, " +
					Schema.Posts.Columns.FLAGS + " INTEGER NOT NULL DEFAULT 0, " +
					Schema.Posts.Columns.DATA + " BLOB NOT NULL, " +
					Schema.Posts.Columns.HASH + " BLOB NOT NULL, " +
					"PRIMARY KEY (" + Schema.Posts.Columns.CHAN_NAME + ", " +
					Schema.Posts.Columns.BOARD_NAME + ", " +
					Schema.Posts.Columns.THREAD_NUMBER + ", " +
					Schema.Posts.Columns.POST_NUMBER_MAJOR + ", " +
					Schema.Posts.Columns.POST_NUMBER_MINOR + "), " +
					"FOREIGN KEY (" + Schema.Posts.Columns.CHAN_NAME + ", " +
					Schema.Posts.Columns.BOARD_NAME + ", " +
					Schema.Posts.Columns.THREAD_NUMBER + ") " +
					"REFERENCES " + Schema.Meta.TABLE_NAME + " (" +
					Schema.Meta.Columns.CHAN_NAME + ", " +
					Schema.Meta.Columns.BOARD_NAME + ", " +
					Schema.Meta.Columns.THREAD_NUMBER + ") " +
					"ON DELETE CASCADE ON UPDATE CASCADE)");
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

		@Override
		public void onOpen(SQLiteDatabase db) {}
	}

	private void cleanup(Set<ThreadKey> excludeThreads, boolean force) {
		HashSet<ThreadKey> removeThreads = null;
		boolean shouldRemove = false;
		try (Cursor cursor = database.rawQuery("SELECT " +
				"m." + Schema.Meta.Columns.CHAN_NAME + ", " +
				"m." + Schema.Meta.Columns.BOARD_NAME + ", " +
				"m." + Schema.Meta.Columns.THREAD_NUMBER + ", " +
				"COUNT(p." + Schema.Meta.Columns.CHAN_NAME + ") " +
				"FROM " + Schema.Meta.TABLE_NAME + " AS m " +
				"JOIN " + Schema.Posts.TABLE_NAME + " AS p " +
				"ON m." + Schema.Meta.Columns.CHAN_NAME + " = p." + Schema.Posts.Columns.CHAN_NAME + " AND " +
				"m." + Schema.Meta.Columns.BOARD_NAME + " = p." + Schema.Posts.Columns.BOARD_NAME + " AND " +
				"m." + Schema.Meta.Columns.THREAD_NUMBER + " = p." + Schema.Posts.Columns.THREAD_NUMBER + " " +
				"GROUP BY p." + Schema.Meta.Columns.CHAN_NAME + ", " +
				"p." + Schema.Meta.Columns.BOARD_NAME + ", " +
				"p." + Schema.Meta.Columns.THREAD_NUMBER + " " +
				"ORDER BY m." + Schema.Meta.Columns.TIME + " DESC", null)) {
			int postCount = 0;
			while (cursor.moveToNext()) {
				String chanName = cursor.getString(0);
				String boardName = cursor.getString(1);
				String threadNumber = cursor.getString(2);
				ThreadKey threadKey = new ThreadKey(chanName, boardName, threadNumber);
				if (!excludeThreads.contains(threadKey)) {
					postCount += cursor.getInt(3);
					if (postCount > Schema.Posts.MAX_COUNT * Schema.Posts.MAX_COUNT_FACTOR || force) {
						if (removeThreads == null) {
							removeThreads = new HashSet<>();
						}
						removeThreads.add(threadKey);
					}
					if (postCount > Schema.Posts.MAX_COUNT || force) {
						shouldRemove = true;
					}
				}
			}
		}
		if (removeThreads != null && !removeThreads.isEmpty() && shouldRemove) {
			database.beginTransaction();
			try {
				for (ThreadKey threadKey : removeThreads) {
					Expression.Filter filter = threadKey.filterMeta().build();
					database.delete(Schema.Meta.TABLE_NAME, filter.value, filter.args);
				}
				database.setTransactionSuccessful();
			} finally {
				database.endTransaction();
			}
			checkpoint();
		}
	}

	public void erase(Collection<ThreadKey> keepThreads) {
		HashSet<ThreadKey> mainExcludeThreads = ConcurrentUtils.mainGet(() -> {
			HashSet<ThreadKey> excludeThreads = new HashSet<>();
			for (FavoritesStorage.FavoriteItem favoriteItem : FavoritesStorage.getInstance().getThreads(null)) {
				excludeThreads.add(new ThreadKey(favoriteItem.chanName,
						StringUtils.emptyIfNull(favoriteItem.boardName), favoriteItem.threadNumber));
			}
			return excludeThreads;
		});
		if (keepThreads != null) {
			mainExcludeThreads.addAll(keepThreads);
		}
		cleanup(mainExcludeThreads, true);
	}

	public void eraseAll() {
		database.delete(Schema.Meta.TABLE_NAME, null, null);
		checkpoint();
	}

	private void checkpoint() {
		try (Cursor cursor = database.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null)) {
			cursor.moveToFirst();
		}
	}

	public long getSize() {
		File file = MainApplication.getInstance().getDatabasePath(helper.getDatabaseName());
		return file.length() + new File(file.getParentFile(), file.getName() + "-wal").length();
	}

	public Meta getMeta(@NonNull ThreadKey threadKey, boolean temporary) {
		Objects.requireNonNull(threadKey);
		String[] projection = {Schema.Meta.Columns.FLAGS, Schema.Meta.Columns.DATA};
		Expression.Filter filter = threadKey.filterMeta().build();
		Meta meta = null;
		try (Cursor cursor = database.query(Schema.Meta.TABLE_NAME,
				projection, filter.value, filter.args, null, null, null)) {
			if (cursor.moveToFirst()) {
				int flags = cursor.getInt(0);
				boolean deleted = FlagUtils.get(flags, Schema.Meta.Flags.DELETED);
				boolean error = FlagUtils.get(flags, Schema.Meta.Flags.ERROR);
				try (JsonSerial.Reader reader = JsonSerial.reader(cursor.getBlob(1))) {
					meta = Meta.deserialize(reader, deleted, error);
				} catch (IOException | ParseException e) {
					Log.persistent().stack(e);
					return null;
				}
			}
		}
		if (meta != null) {
			if (!temporary) {
				ContentValues values = new ContentValues();
				values.put(Schema.Meta.Columns.TIME, System.currentTimeMillis());
				database.update(Schema.Meta.TABLE_NAME, values, filter.value, filter.args);
			}
			return meta;
		} else if (migratePosts(threadKey, MigrationRequest.GET_META)) {
			return getMeta(threadKey, temporary);
		} else {
			return null;
		}
	}

	public void setMetaFlags(@NonNull ThreadKey threadKey, boolean deleted, boolean error) {
		Objects.requireNonNull(threadKey);
		Expression.Filter filter = threadKey.filterMeta().build();
		int clearFlags = Schema.Meta.Flags.DELETED | Schema.Meta.Flags.ERROR;
		int setFlags = (deleted ? Schema.Meta.Flags.DELETED : 0) | (error ? Schema.Meta.Flags.ERROR : 0);
		database.execSQL("UPDATE " + Schema.Meta.TABLE_NAME + " " +
				"SET " + Schema.Meta.Columns.FLAGS + " = " +
				Schema.Meta.Columns.FLAGS + " & " + ~clearFlags + " | " + setFlags + " " +
				"WHERE " + filter.value, filter.args);
	}

	public ThreadSummary getThreadSummary(@NonNull ThreadKey threadKey) {
		Objects.requireNonNull(threadKey);
		String[] lastPostProjection = {Schema.Posts.Columns.POST_NUMBER_MAJOR, Schema.Posts.Columns.POST_NUMBER_MINOR};
		Expression.Filter lastPostFilter = threadKey.filterPosts()
				.raw("NOT (" + Schema.Posts.Columns.FLAGS + " & " + Schema.Posts.Flags.DELETED + ")")
				.build();
		PostNumber lastExistingPostNumber;
		try (Cursor cursor = database.query(Schema.Posts.TABLE_NAME, lastPostProjection, lastPostFilter.value,
				lastPostFilter.args, null, null, Schema.Posts.Columns.POST_NUMBER_MAJOR + " DESC, " +
						Schema.Posts.Columns.POST_NUMBER_MINOR + " DESC", "1")) {
			lastExistingPostNumber = cursor.moveToFirst() ? new PostNumber(cursor.getInt(0), cursor.getInt(1)) : null;
		}
		String[] originalPostProjection = {Schema.Posts.Columns.POST_NUMBER_MAJOR,
				Schema.Posts.Columns.POST_NUMBER_MINOR, Schema.Posts.Columns.DATA};
		Expression.Filter originalPostFilter = threadKey.filterPosts().build();
		PostNumber originalPostNumber = null;
		boolean cyclical = false;
		try (Cursor cursor = database.query(Schema.Posts.TABLE_NAME, originalPostProjection, originalPostFilter.value,
				originalPostFilter.args, null, null, Schema.Posts.Columns.POST_NUMBER_MAJOR + " ASC, " +
						Schema.Posts.Columns.POST_NUMBER_MINOR + " ASC", "1")) {
			if (cursor.moveToFirst()) {
				originalPostNumber = new PostNumber(cursor.getInt(0), cursor.getInt(1));
				try (JsonSerial.Reader reader = JsonSerial.reader(cursor.getBlob(2))) {
					cyclical = Post.deserialize(originalPostNumber, false, reader).isCyclical();
				} catch (IOException e) {
					throw new RuntimeException(e);
				} catch (ParseException e) {
					// Ignore
				}
			}
		}
		return new ThreadSummary(originalPostNumber, lastExistingPostNumber, cyclical);
	}

	public List<PostNumber> getPostNumbers(@NonNull ThreadKey threadKey) {
		Objects.requireNonNull(threadKey);
		String[] projection = {Schema.Posts.Columns.POST_NUMBER_MAJOR, Schema.Posts.Columns.POST_NUMBER_MINOR};
		Expression.Filter filter = threadKey.filterPosts().build();
		ArrayList<PostNumber> postNumbers;
		try (Cursor cursor = database.query(Schema.Posts.TABLE_NAME, projection, filter.value, filter.args, null, null,
				Schema.Posts.Columns.POST_NUMBER_MAJOR + " ASC, " + Schema.Posts.Columns.POST_NUMBER_MINOR + " ASC")) {
			postNumbers = new ArrayList<>(cursor.getCount());
			while (cursor.moveToNext()) {
				postNumbers.add(new PostNumber(cursor.getInt(0), cursor.getInt(1)));
			}
		}
		return postNumbers != null ? postNumbers : Collections.emptyList();
	}

	public WatcherState getWatcherState(@NonNull ThreadKey threadKey) {
		Objects.requireNonNull(threadKey);
		int newCount;
		long time = 0;
		int flags = Schema.Meta.Flags.DELETED;
		Expression.Filter newPostsFilter = threadKey.filterPosts()
				.raw(Schema.Posts.Columns.FLAGS + " & " + Schema.Posts.Flags.MARK_NEW)
				.build();
		try (Cursor cursor = database.rawQuery("SELECT COUNT(*) " +
				"FROM " + Schema.Posts.TABLE_NAME + " " +
				"WHERE " + newPostsFilter.value, newPostsFilter.args)) {
			newCount = cursor.moveToFirst() ? cursor.getInt(0) : 0;
		}
		Expression.Filter metaFilter = threadKey.filterMeta().build();
		String[] metaProjection = {Schema.Meta.Columns.TIME, Schema.Meta.Columns.FLAGS};
		try (Cursor cursor = database.query(Schema.Meta.TABLE_NAME, metaProjection,
				metaFilter.value, metaFilter.args, null, null, null)) {
			if (cursor.moveToFirst()) {
				time = cursor.getLong(0);
				flags = cursor.getInt(1);
			}
		}
		return new WatcherState(newCount, FlagUtils.get(flags, Schema.Meta.Flags.DELETED),
				FlagUtils.get(flags, Schema.Meta.Flags.ERROR), time);
	}

	private final HashMap<ThreadKey, Cache.State> cacheStates = new HashMap<>();

	public Cache.State getCacheState(ThreadKey threadKey) {
		Objects.requireNonNull(threadKey);
		synchronized (cacheStates) {
			Cache.State state = cacheStates.get(threadKey);
			if (state == null) {
				state = new Cache.State(UUID.randomUUID(), false);
				cacheStates.put(threadKey, state);
			}
			return state;
		}
	}

	private void updateFlags(ThreadKey threadKey, Expression.LongIterator iterator, String transform) {
		// Use filter to properly handle reused rowid
		Expression.Filter filter = threadKey.filterPosts().build();
		Expression.updateById(database, iterator, Schema.Posts.TABLE_NAME, "rowid",
				Schema.Posts.Columns.FLAGS + " = " + Schema.Posts.Columns.FLAGS + " " + transform, filter);
	}

	private void upsertMeta(ThreadKey threadKey, long time, Meta meta) throws IOException {
		if (!database.inTransaction()) {
			throw new IllegalStateException();
		}
		Expression.Filter filter = threadKey.filterMeta().build();
		ContentValues values = new ContentValues();
		values.put(Schema.Meta.Columns.TIME, time);
		try (JsonSerial.Writer writer = JsonSerial.writer()) {
			meta.serialize(writer);
			values.put(Schema.Meta.Columns.DATA, writer.build());
		}
		if (database.update(Schema.Meta.TABLE_NAME, values, filter.value, filter.args) <= 0) {
			values.put(Schema.Meta.Columns.CHAN_NAME, threadKey.chanName);
			values.put(Schema.Meta.Columns.BOARD_NAME, threadKey.boardName);
			values.put(Schema.Meta.Columns.THREAD_NUMBER, threadKey.threadNumber);
			database.insert(Schema.Meta.TABLE_NAME, null, values);
		}
	}

	private final Expression.KeyLock<ThreadKey> insertLocks = new Expression.KeyLock<>();

	public InsertResult insertNewPosts(@NonNull ThreadKey threadKey, @NonNull List<Post> posts, @NonNull Meta meta,
			boolean temporary, boolean newThread, boolean partial) throws IOException {
		Objects.requireNonNull(threadKey);
		Objects.requireNonNull(posts);
		Objects.requireNonNull(meta);
		byte[][] dataArray = new byte[posts.size()][];
		for (int i = 0; i < posts.size(); i++) {
			try (JsonSerial.Writer writer = JsonSerial.writer()) {
				posts.get(i).serialize(writer);
				dataArray[i] = writer.build();
			}
		}
		HashMap<PostNumber, Serialized> serializedMap = new HashMap<>(dataArray.length);
		Hasher hasher = Hasher.getInstanceSha256();
		for (int i = 0; i < dataArray.length; i++) {
			Post post = posts.get(i);
			byte[] data = dataArray[i];
			byte[] hash = hasher.calculate(data);
			serializedMap.put(post.number, new Serialized(post, data, hash, newThread));
		}
		Set<PostNumber> userPosts = CommonDatabase.getInstance().getPosts()
				.getFlags(threadKey.chanName, threadKey.boardName, threadKey.threadNumber).userPosts;
		return insertLocks.lock(threadKey, () -> insertNewPostsLocked(threadKey,
				meta, temporary, newThread, partial, serializedMap, userPosts));
	}

	private InsertResult insertNewPostsLocked(ThreadKey threadKey,
			Meta meta, boolean temporary, boolean newThread, boolean partial,
			HashMap<PostNumber, Serialized> serializedMap, Set<PostNumber> userPosts) throws IOException {
		LongSparseArray<Void> deleted = null;
		LongSparseArray<Void> restored = null;
		int newCount = 0;
		String[] projection = {"rowid", Schema.Posts.Columns.POST_NUMBER_MAJOR,
				Schema.Posts.Columns.POST_NUMBER_MINOR, Schema.Posts.Columns.FLAGS, Schema.Posts.Columns.HASH};
		Expression.Filter filter = threadKey.filterPosts().build();
		try (Cursor cursor = database.query(Schema.Posts.TABLE_NAME,
				projection, filter.value, filter.args, null, null, null)) {
			while (cursor.moveToNext()) {
				long id = cursor.getLong(0);
				PostNumber postNumber = new PostNumber(cursor.getInt(1), cursor.getInt(2));
				int flags = cursor.getInt(3);
				byte[] hash = cursor.getBlob(4);
				Serialized serialized = serializedMap.get(postNumber);
				if (serialized != null) {
					if (Arrays.equals(serialized.hash, hash)) {
						serializedMap.remove(postNumber);
						serialized = null;
						if (FlagUtils.get(flags, Schema.Posts.Flags.DELETED)) {
							if (restored == null) {
								restored = new LongSparseArray<>();
							}
							restored.put(id, null);
						}
					} else {
						flags = FlagUtils.set(flags, Schema.Posts.Flags.DELETED |
								Schema.Posts.Flags.MARK_DELETED, false);
						flags = FlagUtils.set(flags, Schema.Posts.Flags.MARK_EDITED, true);
						serialized.flags = flags;
					}
				} else if (!partial && !FlagUtils.get(flags, Schema.Posts.Flags.DELETED)) {
					if (deleted == null) {
						deleted = new LongSparseArray<>();
					}
					deleted.put(id, null);
				}
				if (serialized == null && FlagUtils.get(flags, Schema.Posts.Flags.MARK_NEW)) {
					newCount++;
				}
			}
		}
		for (Serialized serialized : serializedMap.values()) {
			if (FlagUtils.get(serialized.flags, Schema.Posts.Flags.MARK_NEW)) {
				newCount++;
			}
		}

		ArrayList<InsertResult.Reply> replies = new ArrayList<>();
		database.beginTransaction();
		try {
			upsertMeta(threadKey, temporary ? 0 : System.currentTimeMillis(), meta);
			if (deleted != null) {
				updateFlags(threadKey, Expression.LongIterator.create(deleted), "| " +
						(Schema.Posts.Flags.DELETED | Schema.Posts.Flags.MARK_DELETED));
			}
			if (restored != null) {
				updateFlags(threadKey, Expression.LongIterator.create(restored), "& " +
						~(Schema.Posts.Flags.DELETED | Schema.Posts.Flags.MARK_DELETED) + " | " +
						Schema.Posts.Flags.MARK_EDITED);
			}
			if (!serializedMap.isEmpty()) {
				Iterator<Serialized> iterator = serializedMap.values().iterator();
				HashSet<PostNumber> referencesTo = userPosts.isEmpty() ? null : new HashSet<>();
				Expression.batchInsert(serializedMap.size(), 10, 8,
						values -> database.compileStatement("INSERT OR REPLACE " +
								"INTO " + Schema.Posts.TABLE_NAME + " (" +
								Schema.Posts.Columns.CHAN_NAME + ", " +
								Schema.Posts.Columns.BOARD_NAME + ", " +
								Schema.Posts.Columns.THREAD_NUMBER + ", " +
								Schema.Posts.Columns.POST_NUMBER_MAJOR + ", " +
								Schema.Posts.Columns.POST_NUMBER_MINOR + ", " +
								Schema.Posts.Columns.FLAGS + ", " +
								Schema.Posts.Columns.DATA + ", " +
								Schema.Posts.Columns.HASH + ") " +
								"VALUES " + values),
						(statement, start) -> {
							Serialized serialized = iterator.next();
							int flags = serialized.flags;
							if (referencesTo != null && FlagUtils.get(flags, Schema.Posts.Flags.MARK_NEW)) {
								referencesTo.clear();
								PostItem.collectReferences(referencesTo, serialized.post.comment);
								for (PostNumber reference : referencesTo) {
									if (userPosts.contains(reference)) {
										flags |= Schema.Posts.Flags.MARK_REPLY;
										replies.add(new InsertResult.Reply(serialized.post.number,
												serialized.post.comment, serialized.post.timestamp));
										break;
									}
								}
							}
							statement.bindString(start + 1, threadKey.chanName);
							statement.bindString(start + 2, threadKey.boardName);
							statement.bindString(start + 3, threadKey.threadNumber);
							statement.bindLong(start + 4, serialized.post.number.major);
							statement.bindLong(start + 5, serialized.post.number.minor);
							statement.bindLong(start + 6, flags);
							statement.bindBlob(start + 7, serialized.data);
							statement.bindBlob(start + 8, serialized.hash);
						});
			}
			database.setTransactionSuccessful();
		} finally {
			database.endTransaction();
		}

		Cache.State state = new Cache.State(UUID.randomUUID(), newThread);
		synchronized (cacheStates) {
			cacheStates.put(threadKey, state);
		}
		return new InsertResult(state, replies, newCount);
	}

	private final Expression.KeyLock<ThreadKey> collectLocks = new Expression.KeyLock<>();

	public Diff collectDiffPosts(@NonNull ThreadKey threadKey, Cache cache, @NonNull Cleanup cleanup,
			CancellationSignal signal) throws ParseException, OperationCanceledException {
		Objects.requireNonNull(threadKey);
		Objects.requireNonNull(cleanup);
		Diff diff = collectLocks.lock(threadKey, () -> collectDiffPostsLocked(threadKey, cache, cleanup, signal));
		if (cache == null && diff.cache.isEmpty() && migratePosts(threadKey, MigrationRequest.COLLECT_DIFF_POSTS)) {
			return collectDiffPosts(threadKey, cache, cleanup, signal);
		}
		return diff;
	}

	private Diff collectDiffPostsLocked(ThreadKey threadKey, Cache cache, Cleanup cleanup,
			CancellationSignal signal) throws ParseException, OperationCanceledException {
		switch (cleanup) {
			case NONE: {
				break;
			}
			case ERASE: {
				Expression.Filter filter = threadKey.filterMeta().build();
				database.delete(Schema.Meta.TABLE_NAME, filter.value, filter.args);
				break;
			}
			case OLD: {
				if (cache != null && !cache.diffItems.isEmpty() && cache.originalPostNumber != null) {
					PostNumber firstExistingPostNumber = null;
					for (Map.Entry<PostNumber, DiffItem> entry : cache.diffItems.entrySet()) {
						if (!entry.getValue().deleted) {
							PostNumber postNumber = entry.getKey();
							if (!postNumber.equals(cache.originalPostNumber) && (firstExistingPostNumber == null ||
									postNumber.compareTo(firstExistingPostNumber) < 0)) {
								firstExistingPostNumber = postNumber;
							}
						}
					}
					if (firstExistingPostNumber != null) {
						Expression.Filter filter = threadKey.filterPosts()
								.append(Expression.filterOr()
										.raw(Schema.Posts.Columns.POST_NUMBER_MAJOR + " < " +
												firstExistingPostNumber.major)
										.append(Expression.filter()
												.raw(Schema.Posts.Columns.POST_NUMBER_MAJOR + " = " +
														firstExistingPostNumber.major)
												.raw(Schema.Posts.Columns.POST_NUMBER_MINOR + " < " +
														firstExistingPostNumber.minor)))
								.raw(Schema.Posts.Columns.FLAGS + " & " + Schema.Posts.Flags.DELETED)
								.build();
						database.delete(Schema.Posts.TABLE_NAME, filter.value, filter.args);
					}
				}
				break;
			}
			case DELETED: {
				Expression.Filter filter = threadKey.filterPosts()
						.raw(Schema.Posts.Columns.FLAGS + " & " + Schema.Posts.Flags.DELETED)
						.build();
				database.delete(Schema.Posts.TABLE_NAME, filter.value, filter.args);
				break;
			}
			default: {
				throw new IllegalArgumentException();
			}
		}

		List<Extracted> extractedList = null;
		Map<PostNumber, DiffItem> newItems = null;
		PostNumber originalPostNumber = cache != null ? cache.originalPostNumber : null;
		Map<PostNumber, DiffItem> oldItems = cache != null ? cache.diffItems : Collections.emptyMap();
		ArrayList<PostNumber> existing = null;
		Map<PostNumber, Long> newPosts = null;
		Map<PostNumber, Long> deletedPosts = null;
		Map<PostNumber, Long> editedPosts = null;
		Map<PostNumber, Long> replyPosts = null;
		Cache.State state = getCacheState(threadKey);

		String[] projection = {"rowid", Schema.Posts.Columns.POST_NUMBER_MAJOR,
				Schema.Posts.Columns.POST_NUMBER_MINOR, Schema.Posts.Columns.FLAGS,
				Schema.Posts.Columns.DATA, Schema.Posts.Columns.HASH};
		Expression.Filter filter = threadKey.filterPosts().build();
		try (Cursor cursor = database.query(false, Schema.Posts.TABLE_NAME, projection,
				filter.value, filter.args, null, null, null, null, signal)) {
			while (cursor.moveToNext()) {
				long id = cursor.getLong(0);
				PostNumber postNumber = new PostNumber(cursor.getInt(1), cursor.getInt(2));
				if (existing == null) {
					existing = new ArrayList<>(cursor.getCount());
				}
				existing.add(postNumber);
				int flags = cursor.getInt(3);
				if (FlagUtils.get(flags, Schema.Posts.Flags.MARK_NEW)) {
					if (newPosts == null) {
						newPosts = new HashMap<>();
					}
					newPosts.put(postNumber, id);
				}
				if (FlagUtils.get(flags, Schema.Posts.Flags.MARK_DELETED)) {
					if (deletedPosts == null) {
						deletedPosts = new HashMap<>();
					}
					deletedPosts.put(postNumber, id);
				}
				if (FlagUtils.get(flags, Schema.Posts.Flags.MARK_EDITED)) {
					if (editedPosts == null) {
						editedPosts = new HashMap<>();
					}
					editedPosts.put(postNumber, id);
				}
				if (FlagUtils.get(flags, Schema.Posts.Flags.MARK_REPLY)) {
					if (replyPosts == null) {
						replyPosts = new HashMap<>();
					}
					replyPosts.put(postNumber, id);
				}
				boolean deleted = FlagUtils.get(flags, Schema.Posts.Flags.DELETED);
				byte[] hash = cursor.getBlob(5);
				DiffItem oldItem = oldItems.get(postNumber);
				if (oldItem == null || oldItem.deleted != deleted || !Arrays.equals(oldItem.hash, hash)) {
					if (originalPostNumber == null || postNumber.compareTo(originalPostNumber) < 0) {
						originalPostNumber = postNumber;
					}
					if (extractedList == null) {
						extractedList = new ArrayList<>();
					}
					if (newItems == null) {
						newItems = new HashMap<>(oldItems);
					}
					extractedList.add(new Extracted(cursor.getBlob(4), postNumber, deleted));
					newItems.put(postNumber, new DiffItem(hash, deleted));
				}
			}
		}

		List<Post> changed = null;
		if (extractedList != null) {
			@SuppressWarnings("unchecked")
			List<Post> unsafeChanged = (List<Post>) (List<?>) extractedList;
			for (int i = 0; i < extractedList.size(); i++) {
				Extracted extracted = extractedList.get(i);
				Post post;
				try (JsonSerial.Reader reader = JsonSerial.reader(extracted.data)) {
					post = Post.deserialize(extracted.postNumber, extracted.deleted, reader);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
				unsafeChanged.set(i, post);
			}
			changed = unsafeChanged;
		}
		Collection<PostNumber> removed = Collections.emptyList();
		if (existing != null) {
			int cacheSize = (newItems != null ? newItems : oldItems).size();
			if (cacheSize > existing.size()) {
				Collections.sort(existing);
				removed = new ArrayList<>(cacheSize - existing.size());
				if (newItems == null) {
					newItems = new HashMap<>(oldItems);
				}
				Iterator<PostNumber> iterator = newItems.keySet().iterator();
				while (iterator.hasNext()) {
					PostNumber postNumber = iterator.next();
					if (Collections.binarySearch(existing, postNumber) < 0) {
						iterator.remove();
						removed.add(postNumber);
					}
				}
			}
		} else if (!oldItems.isEmpty()) {
			newItems = Collections.emptyMap();
			removed = oldItems.keySet();
		}

		if (newPosts != null && !newPosts.isEmpty() || deletedPosts != null && !deletedPosts.isEmpty() ||
				editedPosts != null && !editedPosts.isEmpty() || replyPosts != null && !replyPosts.isEmpty()) {
			database.beginTransaction();
			try {
				if (newPosts != null && !newPosts.isEmpty()) {
					updateFlags(threadKey, Expression.LongIterator.create(newPosts.values().iterator()),
							"& " + ~Schema.Posts.Flags.MARK_NEW);
				}
				if (deletedPosts != null && !deletedPosts.isEmpty()) {
					updateFlags(threadKey, Expression.LongIterator.create(deletedPosts.values().iterator()),
							"& " + ~Schema.Posts.Flags.MARK_DELETED);
				}
				if (editedPosts != null && !editedPosts.isEmpty()) {
					updateFlags(threadKey, Expression.LongIterator.create(editedPosts.values().iterator()),
							"& " + ~Schema.Posts.Flags.MARK_EDITED);
				}
				if (replyPosts != null && !replyPosts.isEmpty()) {
					updateFlags(threadKey, Expression.LongIterator.create(replyPosts.values().iterator()),
							"& " + ~Schema.Posts.Flags.MARK_REPLY);
				}
				database.setTransactionSuccessful();
			} finally {
				database.endTransaction();
			}
		}

		Cache newCache = new Cache(newItems != null ? newItems : oldItems, originalPostNumber, state);
		return new Diff(newCache, changed != null ? changed : Collections.emptyList(), removed,
				newPosts != null ? newPosts.keySet() : Collections.emptySet(),
				deletedPosts != null ? deletedPosts.keySet() : Collections.emptySet(),
				editedPosts != null ? editedPosts.keySet() : Collections.emptySet(),
				replyPosts != null ? replyPosts.keySet() : Collections.emptySet());
	}

	@SuppressWarnings("unused")
	private static final class Legacy {
		public static final class HttpValidator implements Serializable {
			public String eTag;
			public String lastModified;
		}

		public static final class Posts implements Serializable {
			public Post[] mPosts;
			public HttpValidator mHttpValidator;
			public String mArchivedThreadUriString;
			public int mUniquePosters;
			public int mPostsCount;
			public int mFilesCount;
			public int mPostsWithFilesCount;
			public String[][] mLocalAutohide;
			public boolean mAutoRefreshEnabled;
			public int mAutoRefreshInterval;
		}

		public static final class Post implements Serializable {
			public interface Flags {
				int SAGE = 0x00000001;
				int STICKY = 0x00000002;
				int CLOSED = 0x00000004;
				int ARCHIVED = 0x00000008;
				int CYCLICAL = 0x00000010;
				int POSTER_WARNED = 0x00000020;
				int POSTER_BANNED = 0x00000040;
				int ORIGINAL_POSTER = 0x00000080;
				int DEFAULT_NAME = 0x00000100;
				int BUMP_LIMIT_REACHED = 0x00000200;
			}

			public interface InternalFlags {
				int HIDDEN = 0x00010000;
				int SHOWN = 0x00020000;
				int DELETED = 0x00040000;
				int USER_POST = 0x00080000;
			}

			public int mFlags;
			public String mThreadNumber;
			public String mParentPostNumber;
			public String mPostNumber;
			public long mTimestamp;
			public String mSubject;
			public String mComment;
			public String mEditedComment;
			public String mCommentMarkup;
			public String mName;
			public String mIdentifier;
			public String mTripcode;
			public String mCapcode;
			public String mEmail;
			public Object[] mAttachments;
			public Icon[] mIcons;
		}

		public static final class FileAttachment implements Serializable {
			public String mFileUriString;
			public String mThumbnailUriString;
			public String mOriginalName;
			public int mSize;
			public int mWidth;
			public int mHeight;
			public boolean mSpoiler;
		}

		public static final class EmbeddedAttachment implements Serializable {
			public enum ContentType {AUDIO, VIDEO}

			public String mFileUriString;
			public String mThumbnailUriString;
			public String mEmbeddedType;
			public ContentType mContentType;
			public boolean mCanDownload;
			public String mForcedName;
			public String mTitle;
		}

		public static final class Icon implements Serializable {
			public String mUriString;
			public String mTitle;
		}
	}

	private static class LegacyObjectInputStream extends ObjectInputStream {
		public LegacyObjectInputStream(InputStream in) throws IOException {
			super(in);
		}

		private static final Map<String, Class<?>> TRANSFORM;

		static {
			TRANSFORM = new HashMap<>();
			TRANSFORM.put("chan.http.HttpValidator", Legacy.HttpValidator.class);
			TRANSFORM.put("chan.content.model.Posts", Legacy.Posts.class);
			TRANSFORM.put("chan.content.model.Post", Legacy.Post.class);
			TRANSFORM.put("[Lchan.content.model.Post;", Legacy.Post[].class);
			TRANSFORM.put("chan.content.model.Attachment", Object.class);
			TRANSFORM.put("[Lchan.content.model.Attachment;", Object[].class);
			TRANSFORM.put("chan.content.model.FileAttachment", Legacy.FileAttachment.class);
			TRANSFORM.put("[Lchan.content.model.FileAttachment;", Legacy.FileAttachment[].class);
			TRANSFORM.put("chan.content.model.EmbeddedAttachment", Legacy.EmbeddedAttachment.class);
			TRANSFORM.put("[Lchan.content.model.EmbeddedAttachment;", Legacy.EmbeddedAttachment[].class);
			TRANSFORM.put("chan.content.model.EmbeddedAttachment$ContentType",
					Legacy.EmbeddedAttachment.ContentType.class);
			TRANSFORM.put("chan.content.model.Icon", Legacy.Icon.class);
			TRANSFORM.put("[Lchan.content.model.Icon;", Legacy.Icon[].class);
		}

		@Override
		protected ObjectStreamClass readClassDescriptor() throws ClassNotFoundException, IOException {
			ObjectStreamClass objectStreamClass = super.readClassDescriptor();
			if (objectStreamClass != null) {
				Class<?> newClass = TRANSFORM.get(objectStreamClass.getName());
				if (newClass != null) {
					objectStreamClass = ObjectStreamClass.lookup(newClass);
				}
			}
			return objectStreamClass;
		}
	}

	private File getLegacyCacheDirectory() {
		File directory = MainApplication.getInstance().getExternalCacheDir();
		directory = directory != null ? new File(directory, "pages") : null;
		return directory != null && directory.isDirectory() ? directory : null;
	}

	private final Expression.KeyLock<ThreadKey> migrateLocks = new Expression.KeyLock<>();
	private final HashMap<ThreadKey, Set<MigrationRequest>> migrated = new HashMap<>();

	private boolean migratePosts(@NonNull ThreadKey threadKey, MigrationRequest request) {
		Objects.requireNonNull(threadKey);
		synchronized (migrated) {
			Set<MigrationRequest> requests = migrated.get(threadKey);
			if (requests != null && requests.isEmpty()) {
				return false;
			}
		}
		return migrateLocks.lock(threadKey, () -> {
			synchronized (migrated) {
				Set<MigrationRequest> requests = migrated.get(threadKey);
				if (requests != null) {
					if (request != null && requests.contains(request)) {
						if (requests.size() == 1) {
							migrated.put(threadKey, Collections.emptySet());
						} else {
							HashSet<MigrationRequest> newRequests = new HashSet<>(requests);
							newRequests.remove(request);
							migrated.put(threadKey, Collections.unmodifiableSet(newRequests));
						}
						return true;
					} else {
						return false;
					}
				}
			}
			boolean success = migratePostsLocked(threadKey);
			synchronized (migrated) {
				if (success && request != null) {
					HashSet<MigrationRequest> newRequests = new HashSet<>(Arrays.asList(MigrationRequest.values()));
					newRequests.remove(request);
					migrated.put(threadKey, Collections.unmodifiableSet(newRequests));
				} else {
					migrated.put(threadKey, Collections.emptySet());
				}
			}
			return success;
		});
	}

	private File getPostsFile(File directory, String chanName, String boardName, String threadNumber) {
		String fileName = "posts_" + chanName + "_" + boardName + "_" + threadNumber;
		File postsFile = new File(directory, fileName);
		File tempFile = new File(directory, "temp_" + fileName);
		if (tempFile.exists()) {
			if ((!postsFile.exists() || !postsFile.delete()) && !tempFile.renameTo(postsFile)) {
				return null;
			}
		}
		return postsFile.exists() ? postsFile : null;
	}

	private boolean migratePostsLocked(ThreadKey threadKey) {
		File directory = getLegacyCacheDirectory();
		if (directory == null) {
			return false;
		}
		File postsFile = getPostsFile(directory, threadKey.chanName, threadKey.boardName, threadKey.threadNumber);
		if (postsFile == null) {
			postsFile = getPostsFile(directory, threadKey.chanName,
					StringUtils.nullIfEmpty(threadKey.boardName), threadKey.threadNumber);
		}
		if (postsFile == null) {
			return false;
		}

		Legacy.Posts legacyPosts;
		long time = postsFile.lastModified();
		try (LegacyObjectInputStream input = new LegacyObjectInputStream(new FileInputStream(postsFile))) {
			legacyPosts = (Legacy.Posts) input.readObject();
		} catch (Exception e) {
			Log.persistent().stack(e);
			return false;
		} finally {
			postsFile.delete();
		}
		if (legacyPosts == null || legacyPosts.mPosts == null || legacyPosts.mPosts.length == 0) {
			return false;
		}

		HttpValidator validator = legacyPosts.mHttpValidator != null ? new HttpValidator
				(legacyPosts.mHttpValidator.eTag, legacyPosts.mHttpValidator.lastModified) : null;
		Uri archivedThreadUri = legacyPosts.mArchivedThreadUriString != null
				? Uri.parse(legacyPosts.mArchivedThreadUriString) : null;
		Meta meta = new Meta(validator, archivedThreadUri, legacyPosts.mUniquePosters, false, false);
		ArrayList<Post> posts = new ArrayList<>(legacyPosts.mPosts.length);
		HashMap<PostNumber, Pair<PostItem.HideState, Boolean>> flags = new HashMap<>();
		for (Legacy.Post legacyPost : legacyPosts.mPosts) {
			Post.Builder builder = new Post.Builder();
			try {
				builder.number = PostNumber.parseOrThrow(legacyPost.mPostNumber);
			} catch (Exception e) {
				if (posts.isEmpty()) {
					return false;
				}
				continue;
			}
			builder.setSage(FlagUtils.get(legacyPost.mFlags, Legacy.Post.Flags.SAGE));
			builder.setSticky(FlagUtils.get(legacyPost.mFlags, Legacy.Post.Flags.STICKY));
			builder.setClosed(FlagUtils.get(legacyPost.mFlags, Legacy.Post.Flags.CLOSED));
			builder.setArchived(FlagUtils.get(legacyPost.mFlags, Legacy.Post.Flags.ARCHIVED));
			builder.setCyclical(FlagUtils.get(legacyPost.mFlags, Legacy.Post.Flags.CYCLICAL));
			builder.setPosterWarned(FlagUtils.get(legacyPost.mFlags, Legacy.Post.Flags.POSTER_WARNED));
			builder.setPosterBanned(FlagUtils.get(legacyPost.mFlags, Legacy.Post.Flags.POSTER_BANNED));
			builder.setOriginalPoster(FlagUtils.get(legacyPost.mFlags, Legacy.Post.Flags.ORIGINAL_POSTER));
			builder.setDefaultName(FlagUtils.get(legacyPost.mFlags, Legacy.Post.Flags.DEFAULT_NAME));
			builder.setBumpLimitReached(FlagUtils.get(legacyPost.mFlags, Legacy.Post.Flags.BUMP_LIMIT_REACHED));
			boolean hidden = FlagUtils.get(legacyPost.mFlags, Legacy.Post.InternalFlags.HIDDEN);
			boolean shown = FlagUtils.get(legacyPost.mFlags, Legacy.Post.InternalFlags.SHOWN);
			boolean deleted = FlagUtils.get(legacyPost.mFlags, Legacy.Post.InternalFlags.DELETED);
			boolean userPost = FlagUtils.get(legacyPost.mFlags, Legacy.Post.InternalFlags.USER_POST);
			if (hidden || shown || userPost) {
				PostItem.HideState hideState = hidden ? PostItem.HideState.HIDDEN
						: shown ? PostItem.HideState.SHOWN : PostItem.HideState.UNDEFINED;
				flags.put(builder.number, new Pair<>(hideState, userPost));
			}
			builder.timestamp = legacyPost.mTimestamp;
			builder.subject = legacyPost.mSubject;
			builder.comment = legacyPost.mComment;
			builder.commentMarkup = legacyPost.mCommentMarkup;
			builder.name = legacyPost.mName;
			builder.identifier = legacyPost.mIdentifier;
			builder.tripcode = legacyPost.mTripcode;
			builder.capcode = legacyPost.mCapcode;
			builder.email = legacyPost.mEmail;
			if (legacyPost.mAttachments != null && legacyPost.mAttachments.length > 0) {
				builder.attachments = new ArrayList<>(legacyPost.mAttachments.length);
				for (Object legacyAttachment : legacyPost.mAttachments) {
					if (legacyAttachment instanceof Legacy.FileAttachment) {
						Legacy.FileAttachment legacyFile = (Legacy.FileAttachment) legacyAttachment;
						Uri fileUri = StringUtils.isEmpty(legacyFile.mFileUriString) ? null
								: Uri.parse(legacyFile.mFileUriString);
						Uri thumbnailUri = StringUtils.isEmpty(legacyFile.mThumbnailUriString) ? null
								: Uri.parse(legacyFile.mThumbnailUriString);
						Post.Attachment.File file = Post.Attachment.File.createExternal(fileUri, thumbnailUri,
								legacyFile.mOriginalName, legacyFile.mSize,
								legacyFile.mWidth, legacyFile.mHeight, legacyFile.mSpoiler);
						if (file != null) {
							builder.attachments.add(file);
						}
					} else if (legacyAttachment instanceof Legacy.EmbeddedAttachment) {
						Legacy.EmbeddedAttachment legacyEmbedded = (Legacy.EmbeddedAttachment) legacyAttachment;
						Uri fileUri = StringUtils.isEmpty(legacyEmbedded.mFileUriString) ? null
								: Uri.parse(legacyEmbedded.mFileUriString);
						Uri thumbnailUri = StringUtils.isEmpty(legacyEmbedded.mThumbnailUriString) ? null
								: Uri.parse(legacyEmbedded.mThumbnailUriString);
						Post.Attachment.Embedded.ContentType contentType;
						switch (legacyEmbedded.mContentType) {
							case AUDIO: {
								contentType = Post.Attachment.Embedded.ContentType.AUDIO;
								break;
							}
							case VIDEO: {
								contentType = Post.Attachment.Embedded.ContentType.VIDEO;
								break;
							}
							default: {
								contentType = null;
								break;
							}
						}
						Post.Attachment.Embedded embedded = Post.Attachment.Embedded.createExternal(false,
								fileUri, thumbnailUri, legacyEmbedded.mEmbeddedType, contentType,
								legacyEmbedded.mCanDownload, legacyEmbedded.mForcedName);
						if (embedded != null) {
							builder.attachments.add(embedded);
						}
					}
				}
			}
			if (legacyPost.mIcons != null && legacyPost.mIcons.length > 0) {
				builder.icons = new ArrayList<>(legacyPost.mIcons.length);
				for (Legacy.Icon legacyIcon : legacyPost.mIcons) {
					if (legacyIcon != null) {
						Uri uri = StringUtils.isEmpty(legacyIcon.mUriString) ? null
								: Uri.parse(legacyIcon.mUriString);
						Post.Icon icon = Post.Icon.createExternal(uri, legacyIcon.mTitle);
						if (icon != null) {
							builder.icons.add(icon);
						}
					}
				}
			}
			posts.add(builder.build(deleted));
		}

		byte[][] data = new byte[posts.size()][];
		for (int i = 0; i < posts.size(); i++) {
			try (JsonSerial.Writer writer = JsonSerial.writer()) {
				posts.get(i).serialize(writer);
				data[i] = writer.build();
			} catch (IOException e) {
				Log.persistent().stack(e);
				return false;
			}
		}
		if (legacyPosts.mLocalAutohide != null) {
			HidePerformer hidePerformer = new HidePerformer(null);
			hidePerformer.decodeLocalFiltersLegacy(legacyPosts.mLocalAutohide);
			if (hidePerformer.hasLocalFilters()) {
				byte[] extra = null;
				try (JsonSerial.Writer writer = JsonSerial.writer()) {
					writer.startObject();
					writer.name("filters");
					hidePerformer.encodeLocalFilters(writer);
					writer.endObject();
					extra = writer.build();
				} catch (IOException e) {
					// Ignore exception
				}
				if (extra != null) {
					CommonDatabase.getInstance().getThreads().setStateExtra(false,
							threadKey.chanName, threadKey.boardName, threadKey.threadNumber, false, null, true, extra);
				}
			}
		}
		if (!flags.isEmpty()) {
			CommonDatabase.getInstance().getPosts().setFlagsMigration(threadKey.chanName,
					threadKey.boardName, threadKey.threadNumber, flags, time);
		}
		database.beginTransaction();
		try {
			try {
				upsertMeta(threadKey, time, meta);
			} catch (IOException e) {
				Log.persistent().stack(e);
				return false;
			}
			int[] index = {0};
			Hasher hasher = Hasher.getInstanceSha256();
			Expression.batchInsert(posts.size(), 10, 8,
					values -> database.compileStatement("INSERT OR REPLACE " +
							"INTO " + Schema.Posts.TABLE_NAME + " (" +
							Schema.Posts.Columns.CHAN_NAME + ", " +
							Schema.Posts.Columns.BOARD_NAME + ", " +
							Schema.Posts.Columns.THREAD_NUMBER + ", " +
							Schema.Posts.Columns.POST_NUMBER_MAJOR + ", " +
							Schema.Posts.Columns.POST_NUMBER_MINOR + ", " +
							Schema.Posts.Columns.FLAGS + ", " +
							Schema.Posts.Columns.DATA + ", " +
							Schema.Posts.Columns.HASH + ") " +
							"VALUES " + values),
					(statement, start) -> {
						int i = index[0];
						Post post = posts.get(i);
						statement.bindString(start + 1, threadKey.chanName);
						statement.bindString(start + 2, threadKey.boardName);
						statement.bindString(start + 3, threadKey.threadNumber);
						statement.bindLong(start + 4, post.number.major);
						statement.bindLong(start + 5, post.number.minor);
						statement.bindLong(start + 6, post.deleted ? Schema.Posts.Flags.DELETED : 0);
						statement.bindBlob(start + 7, data[i]);
						statement.bindBlob(start + 8, hasher.calculate(data[i]));
						index[0]++;
					});
			database.setTransactionSuccessful();
		} finally {
			database.endTransaction();
		}
		return true;
	}
}
