package com.mishiranu.dashchan.content;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import androidx.annotation.NonNull;
import chan.util.CommonUtils;
import com.mishiranu.dashchan.C;
import java.io.File;
import java.io.FileNotFoundException;

public class FileProvider extends ContentProvider {
	private static final String AUTHORITY = "com.mishiranu.providers.dashchan";
	private static final String PATH_UPDATES = "updates";
	private static final String PATH_DOWNLOADS = "downloads";
	private static final String PATH_SHARE = "share";

	private static final int URI_UPDATES = 1;
	private static final int URI_DOWNLOADS = 2;
	private static final int URI_SHARE = 3;

	private static final UriMatcher URI_MATCHER;

	static {
		URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
		URI_MATCHER.addURI(AUTHORITY, PATH_UPDATES + "/*", URI_UPDATES);
		URI_MATCHER.addURI(AUTHORITY, PATH_DOWNLOADS + "/*", URI_DOWNLOADS);
		URI_MATCHER.addURI(AUTHORITY, PATH_SHARE + "/*", URI_SHARE);
	}

	@Override
	public boolean onCreate() {
		return true;
	}

	public static File getUpdatesDirectory() {
		File directory = MainApplication.getInstance().getExternalCacheDir();
		if (directory == null) {
			return null;
		}
		directory = new File(directory, "updates");
		directory.mkdirs();
		return directory;
	}

	public static File getUpdatesFile(String name) {
		File directory = getUpdatesDirectory();
		if (directory != null) {
			File file = new File(directory, name);
			if (file.exists()) {
				return file;
			}
		}
		return null;
	}

	public static Uri convertUpdatesUri(Uri uri) {
		if (C.API_NOUGAT && "file".equals(uri.getScheme())) {
			File fileParent = new File(uri.getPath()).getParentFile();
			File directory = getUpdatesDirectory();
			if (fileParent != null && fileParent.equals(directory)) {
				return new Uri.Builder().scheme("content").authority(AUTHORITY)
						.appendPath(PATH_UPDATES).appendPath(uri.getLastPathSegment()).build();
			}
		}
		return uri;
	}

	private static class InternalFile {
		public final File file;
		public final String type;
		public final Uri uri;

		public InternalFile(File file, String type, Uri uri) {
			this.file = file;
			this.uri = uri;
			this.type = type;
		}
	}

	private static InternalFile downloadsFile;
	private static InternalFile shareFile;

	private static InternalFile convertFile(File directory, File file, String type, String providerPath) {
		if (C.API_NOUGAT) {
			String filePath = file.getAbsolutePath();
			String directoryPath = directory.getAbsolutePath();
			if (filePath.startsWith(directoryPath)) {
				filePath = filePath.substring(directoryPath.length());
				if (filePath.startsWith("/")) {
					filePath = filePath.substring(1);
				}
				Uri uri = new Uri.Builder().scheme("content").authority(AUTHORITY)
						.appendPath(providerPath).appendEncodedPath(filePath).build();
				return new InternalFile(file, type, uri);
			}
		}
		return null;
	}

	public static Uri convertDownloadsLegacyFile(File file, String type) {
		InternalFile internalFile = convertFile(Preferences.getDownloadDirectoryLegacy(), file, type, PATH_DOWNLOADS);
		if (internalFile != null) {
			downloadsFile = internalFile;
			return internalFile.uri;
		}
		return Uri.fromFile(file);
	}

	public static Uri convertShareFile(File directory, File file, String type) {
		InternalFile internalFile = convertFile(directory, file, type, PATH_SHARE);
		if (internalFile != null) {
			shareFile = internalFile;
			return internalFile.uri;
		}
		return Uri.fromFile(file);
	}

	@Override
	public String getType(@NonNull Uri uri) {
		switch (URI_MATCHER.match(uri)) {
			case URI_UPDATES: {
				return "application/vnd.android.package-archive";
			}
			case URI_DOWNLOADS: {
				if (downloadsFile != null) {
					return downloadsFile.type;
				}
			}
			case URI_SHARE: {
				if (shareFile != null) {
					return shareFile.type;
				}
			}
			default: {
				throw new IllegalArgumentException("Unknown URI: " + uri);
			}
		}
	}

	@Override
	public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
		switch (URI_MATCHER.match(uri)) {
			case URI_UPDATES: {
				if (!"r".equals(mode)) {
					throw new FileNotFoundException();
				}
				File file = getUpdatesFile(uri.getLastPathSegment());
				if (file != null) {
					return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
				}
			}
			case URI_DOWNLOADS: {
				if (downloadsFile != null && uri.equals(downloadsFile.uri)) {
					return ParcelFileDescriptor.open(downloadsFile.file, ParcelFileDescriptor.MODE_READ_ONLY);
				}
			}
			case URI_SHARE: {
				if (shareFile != null && uri.equals(shareFile.uri)) {
					return ParcelFileDescriptor.open(shareFile.file, ParcelFileDescriptor.MODE_READ_ONLY);
				}
			}
			default: {
				throw new FileNotFoundException();
			}
		}
	}

	private static final String[] PROJECTION = {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};

	@Override
	public Cursor query(@NonNull Uri uri, String[] projection,
			String selection, String[] selectionArgs, String sortOrder) {
		int matchResult = URI_MATCHER.match(uri);
		switch (URI_MATCHER.match(uri)) {
			case URI_UPDATES:
			case URI_DOWNLOADS:
			case URI_SHARE: {
				if (projection == null) {
					projection = PROJECTION;
				}
				OUTER: for (String column : projection) {
					for (String allowedColumn : PROJECTION) {
						if (CommonUtils.equals(column, allowedColumn)) {
							continue OUTER;
						}
					}
					throw new SQLiteException("No such column: " + column);
				}
				MatrixCursor cursor = new MatrixCursor(projection);
				File file = null;
				switch (matchResult) {
					case URI_UPDATES: {
						file = getUpdatesFile(uri.getLastPathSegment());
						break;
					}
					case URI_DOWNLOADS: {
						file = downloadsFile != null ? downloadsFile.file : null;
						break;
					}
					case URI_SHARE: {
						file = shareFile != null ? shareFile.file : null;
						break;
					}
				}
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
	public Uri insert(@NonNull Uri uri, ContentValues values) {
		throw new SQLiteException("Unsupported operation");
	}

	@Override
	public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		throw new SQLiteException("Unsupported operation");
	}

	@Override
	public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
		throw new SQLiteException("Unsupported operation");
	}
}
