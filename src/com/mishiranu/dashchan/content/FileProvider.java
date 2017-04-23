/*
 * Copyright 2017 Fukurou Mishiranu
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

package com.mishiranu.dashchan.content;

import java.io.File;
import java.io.FileNotFoundException;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import com.mishiranu.dashchan.C;

import chan.util.StringUtils;

public class FileProvider extends ContentProvider {
	private static final String AUTHORITY = "com.mishiranu.providers.dashchan";
	private static final String PATH_UPDATES = "updates";

	private static final int URI_UPDATES = 1;

	private static final UriMatcher URI_MATCHER;

	static {
		URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
		URI_MATCHER.addURI(AUTHORITY, PATH_UPDATES + "/*", URI_UPDATES);
	}

	@Override
	public boolean onCreate() {
		return true;
	}

	public static File getUpdatesDirectory(Context context) {
		String dirType = "updates";
		File directory = context.getExternalFilesDir(dirType);
		if (directory != null) {
			directory.mkdirs();
		}
		return directory;
	}

	public static File getUpdatesFile(Context context, String name) {
		File directory = getUpdatesDirectory(context);
		if (directory != null) {
			File file = new File(directory, name);
			if (file.exists()) {
				return file;
			}
		}
		return null;
	}

	public static Uri convertUpdatesUri(Context context, Uri uri) {
		if (C.API_NOUGAT && "file".equals(uri.getScheme())) {
			File fileParent = new File(uri.getPath()).getParentFile();
			File directory = getUpdatesDirectory(context);
			if (fileParent != null && fileParent.equals(directory)) {
				return new Uri.Builder().scheme("content").authority(AUTHORITY)
						.appendPath(PATH_UPDATES).appendPath(uri.getLastPathSegment()).build();
			}
		}
		return uri;
	}

	@Override
	public String getType(Uri uri) {
		switch (URI_MATCHER.match(uri)) {
			case URI_UPDATES: {
				return "application/vnd.android.package-archive";
			}
			default: {
				throw new IllegalArgumentException("Unknown URI: " + uri);
			}
		}
	}

	@Override
	public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
		switch (URI_MATCHER.match(uri)) {
			case URI_UPDATES: {
				if (!"r".equals(mode)) {
					throw new FileNotFoundException();
				}
				File file = getUpdatesFile(getContext(), uri.getLastPathSegment());
				if (file != null) {
					return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
				}
			}
			default: {
				throw new FileNotFoundException();
			}
		}
	}

	private static final String[] PROJECTION = {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};

	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		switch (URI_MATCHER.match(uri)) {
			case URI_UPDATES: {
				if (projection == null) {
					projection = PROJECTION;
				}
				OUTER: for (String column : projection) {
					for (String allowedColumn : PROJECTION) {
						if (StringUtils.equals(column, allowedColumn)) {
							continue OUTER;
						}
					}
					throw new SQLiteException("No such column: " + column);
				}
				MatrixCursor cursor = new MatrixCursor(projection);
				File file = getUpdatesFile(getContext(), uri.getLastPathSegment());
				if (file != null) {
					Object[] values = new Object[projection.length];
					for (int i = 0; i < projection.length; i++) {
						switch (projection[i]) {
							case OpenableColumns.DISPLAY_NAME: {
								values[i] = file.getName();
								break;
							}
							case OpenableColumns.SIZE: {
								values[i] = file.length();
								break;
							}
						}
					}
					cursor.addRow(values);
				}
				return cursor;
			}
			default: {
				throw new IllegalArgumentException("Unknown URI: " + uri);
			}
		}
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		throw new SQLiteException("Unsupported operation");
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		throw new SQLiteException("Unsupported operation");
	}

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		throw new SQLiteException("Unsupported operation");
	}
}