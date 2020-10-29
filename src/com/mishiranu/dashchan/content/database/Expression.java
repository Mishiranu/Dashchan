package com.mishiranu.dashchan.content.database;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.util.LongSparseArray;
import androidx.annotation.NonNull;
import chan.util.CommonUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Objects;

public class Expression {
	private Expression() {}

	public static Filter.Builder filter() {
		return new Filter.Builder(false);
	}

	public static Filter.Builder filterOr() {
		return new Filter.Builder(true);
	}

	public static class Filter {
		public final String value;
		public final String[] args;

		private Filter(String value, String[] args) {
			this.value = value;
			this.args = args;
		}

		public static class Builder {
			private final boolean or;
			private final StringBuilder builder = new StringBuilder();
			private final ArrayList<String> args = new ArrayList<>();

			private Builder(boolean or) {
				this.or = or;
			}

			private void append() {
				if (builder.length() > 0) {
					builder.append(or ? " OR " : " AND ");
				}
			}

			public Builder equals(@NonNull String name, String value) {
				Objects.requireNonNull(name);
				append();
				if (value != null) {
					builder.append(name).append(" = ?");
					args.add(value);
				} else {
					builder.append(name).append(" IS NULL");
				}
				return this;
			}

			public Builder like(@NonNull String name, @NonNull String value) {
				Objects.requireNonNull(name);
				Objects.requireNonNull(value);
				append();
				builder.append(name).append(" LIKE ?");
				args.add(value);
				return this;
			}

			public Builder in(@NonNull String name, @NonNull Collection<?> values) {
				Objects.requireNonNull(name);
				Objects.requireNonNull(values);
				append();
				if (values.isEmpty()) {
					builder.append("0");
				} else {
					builder.append(name).append(" IN (?");
					for (int i = 1; i < values.size(); i++) {
						builder.append(", ?");
					}
					builder.append(")");
					for (Object value : values) {
						Objects.requireNonNull(value);
						args.add(value.toString());
					}
				}
				return this;
			}

			public Builder raw(String name) {
				append();
				builder.append(name);
				return this;
			}

			public Builder append(Builder builder) {
				append();
				this.builder.append('(').append(builder.builder).append(')');
				args.addAll(builder.args);
				return this;
			}

			public Filter build() {
				if (builder.length() == 0) {
					return new Filter(null, null);
				} else {
					return new Filter(builder.toString(), CommonUtils.toArray(args, String.class));
				}
			}
		}
	}

	public interface CreateBatchInsertStatement {
		SQLiteStatement create(String values);
	}

	public interface BindBatchInsertArgs {
		void bind(SQLiteStatement statement, int start);
	}

	public static void batchInsert(int totalItems, int batchSize, int args,
			CreateBatchInsertStatement createBatchInsertStatement, BindBatchInsertArgs bindBatchInsertArgs) {
		int lastBatchSize = totalItems % batchSize;
		int index = 0;
		SQLiteStatement statement = null;
		while (index < totalItems) {
			if (index > 0 && index % batchSize == 0) {
				statement.execute();
			}
			boolean handleLast = index == totalItems - lastBatchSize;
			if (handleLast || index == 0) {
				int size = handleLast ? lastBatchSize : batchSize;
				statement = createBatchInsertStatement.create(buildInsertValues(size, args));
			}
			int start = args * (index % batchSize);
			bindBatchInsertArgs.bind(statement, start);
			index++;
		}
		if (statement != null) {
			statement.execute();
		}
	}

	private static String buildInsertValues(int count, int args) {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < count; i++) {
			if (i > 0) {
				builder.append(", ");
			}
			builder.append('(');
			for (int j = 0; j < args; j++) {
				if (j > 0) {
					builder.append(", ");
				}
				builder.append('?');
			}
			builder.append(')');
		}
		return builder.toString();
	}

	public interface LongIterator {
		boolean hasNext();
		long next();

		static LongIterator create(LongSparseArray<?> array) {
			return new SparseArrayLongIterator(array);
		}

		static LongIterator create(Iterator<Long> iterator) {
			return new IteratorLongIterator(iterator);
		}
	}

	private static class SparseArrayLongIterator implements LongIterator {
		private final LongSparseArray<?> array;
		private int index;

		public SparseArrayLongIterator(LongSparseArray<?> array) {
			this.array = array;
		}

		@Override
		public boolean hasNext() {
			return index < array.size();
		}

		@Override
		public long next() {
			return array.keyAt(index++);
		}
	}

	private static class IteratorLongIterator implements LongIterator {
		private final Iterator<Long> iterator;

		public IteratorLongIterator(Iterator<Long> iterator) {
			this.iterator = iterator;
		}

		@Override
		public boolean hasNext() {
			return iterator.hasNext();
		}

		@Override
		public long next() {
			return iterator.next();
		}
	}

	public static void updateById(SQLiteDatabase database, LongIterator iterator,
			String table, String idColumn, String set, Filter filter) {
		StringBuilder builder = new StringBuilder();
		int maxCount = 100;
		while (iterator.hasNext()) {
			builder.setLength(0);
			builder.append(iterator.next());
			for (int i = 1; i < maxCount && iterator.hasNext(); i++) {
				builder.append(", ");
				builder.append(iterator.next());
			}
			database.execSQL("UPDATE " + table + " SET " + set + " " +
					"WHERE " + idColumn + " IN (" + builder + ") AND " +
					(filter != null && filter.value != null ? filter.value : "1"),
					filter != null ? filter.args : null);
		}
	}

	public static class KeyLock<T> {
		public interface Callback<R, E extends Throwable> {
			R run() throws E;
		}

		private static class ReferenceCount {
			public volatile int count;
		}

		private final HashMap<T, ReferenceCount> locks = new HashMap<>();

		public <R, E extends Throwable> R lock(T key, Callback<R, E> callback) throws E {
			ReferenceCount lock;
			synchronized (locks) {
				lock = locks.get(key);
				if (lock == null) {
					lock = new ReferenceCount();
					locks.put(key, lock);
				}
				lock.count++;
			}
			try {
				synchronized (lock) {
					return callback.run();
				}
			} finally {
				synchronized (locks) {
					if (--lock.count == 0) {
						locks.remove(key);
					}
				}
			}
		}
	}
}
