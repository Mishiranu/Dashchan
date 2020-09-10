package chan.util;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.util.Pair;
import androidx.annotation.RequiresApi;
import chan.annotation.Public;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.content.CacheManager;
import com.mishiranu.dashchan.content.FileProvider;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.util.MimeTypes;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Public
public abstract class DataFile {
	public enum Target {
		CACHE(null, CacheManager.getInstance()::getMediaDirectory),
		UPDATES(null, FileProvider::getUpdatesDirectory),
		DOWNLOADS(SafFile.SafTarget.DOWNLOADS, Preferences::getDownloadDirectoryLegacy);

		private interface LegacyDirectory {
			File getLegacyDirectory();
		}

		private final SafFile.SafTarget safTarget;
		private final LegacyDirectory legacyDirectory;

		Target(SafFile.SafTarget safTarget, LegacyDirectory legacyDirectory) {
			this.safTarget = safTarget;
			this.legacyDirectory = legacyDirectory;
		}

		public boolean isExternal() {
			return safTarget != null;
		}
	}

	public static boolean isValidSegment(String segment) {
		return segment != null && !segment.isEmpty() && !segment.equals(".") && !segment.equals("..");
	}

	private static String validatePath(String path) {
		if (StringUtils.isEmpty(path)) {
			return "";
		} else {
			StringBuilder newPath = new StringBuilder();
			for (String segment : path.split("/")) {
				if (isValidSegment(segment)) {
					if (newPath.length() > 0) {
						newPath.append('/');
					}
					newPath.append(segment);
				}
			}
			return newPath.toString();
		}
	}

	public static DataFile obtain(Context context, Target target, String path) {
		if (target.safTarget != null && C.USE_SAF) {
			return new SafFile(target, validatePath(path), context.getApplicationContext());
		} else {
			return new RegularFile(target, validatePath(path));
		}
	}

	private final Target target;
	private final String path;

	protected DataFile(Target target, String path) {
		this.target = target;
		this.path = path;
	}

	public Target getTarget() {
		return target;
	}

	public String getRelativePath() {
		return path;
	}

	public abstract Pair<File, Uri> getFileOrUri();

	public abstract boolean exists();

	@Public
	public abstract String getName();

	@Public
	public abstract boolean isDirectory();

	@Public
	public abstract long getLastModified();

	@Public
	public abstract DataFile getChild(String path);

	@Public
	public abstract List<DataFile> getChildren();

	@Public
	public abstract boolean delete();

	@Public
	public abstract InputStream openInputStream() throws IOException;

	@Public
	public abstract OutputStream openOutputStream() throws IOException;

	private static class RegularFile extends DataFile {
		private final File file;

		private RegularFile(Target target, String path) {
			super(target, path);
			File directory = target.legacyDirectory.getLegacyDirectory();
			file = StringUtils.isEmpty(path) ? directory : new File(directory, path);
		}

		@Override
		public Pair<File, Uri> getFileOrUri() {
			return new Pair<>(file, null);
		}

		@Override
		public boolean exists() {
			return file.exists();
		}

		@Override
		public String getName() {
			return file.getName();
		}

		@Override
		public boolean isDirectory() {
			return file.isDirectory();
		}

		@Override
		public long getLastModified() {
			return file.lastModified();
		}

		@Override
		public DataFile getChild(String path) {
			String relativePath = getRelativePath();
			if (StringUtils.isEmpty(path)) {
				return this;
			} else if (relativePath.isEmpty()) {
				return new RegularFile(getTarget(), validatePath(path));
			} else {
				return new RegularFile(getTarget(), validatePath(relativePath + "/" + path));
			}
		}

		@Override
		public List<DataFile> getChildren() {
			String[] names = file.list();
			if (names != null) {
				ArrayList<DataFile> files = new ArrayList<>(names.length);
				for (String name : names) {
					files.add(getChild(name));
				}
				return files;
			}
			return null;
		}

		@Override
		public boolean delete() {
			return file.delete();
		}

		@Override
		public InputStream openInputStream() throws IOException {
			return new FileInputStream(file);
		}

		@Override
		public OutputStream openOutputStream() throws IOException {
			file.getParentFile().mkdirs();
			return new FileOutputStream(file);
		}
	}

	@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
	private static class SafFile extends DataFile {
		private enum SafTarget {
			DOWNLOADS(Preferences::getDownloadUriTree);

			private interface UriTree {
				Uri getUriTree(Context context);
			}

			private final UriTree uriTree;

			SafTarget(UriTree uriTree) {
				this.uriTree = uriTree;
			}
		}

		private enum CursorExtra {LOADING, ERROR}

		private static class Resolution {
			public final Uri documentUri;
			public final String unresolvedPath;
			public final CursorExtra cursorExtra;
			public final boolean isDirectory;
			public final long lastModified;

			private Resolution(Uri documentUri, String unresolvedPath, CursorExtra cursorExtra,
					boolean isDirectory, long lastModified) {
				this.documentUri = documentUri;
				this.unresolvedPath = unresolvedPath;
				this.cursorExtra = cursorExtra;
				this.isDirectory = isDirectory;
				this.lastModified = lastModified;
			}
		}

		private final Context context;
		private Resolution resolution;

		private SafFile(Target target, String path, Context context, Resolution resolution) {
			super(target, path);
			this.context = context;
			this.resolution = resolution;
		}

		private SafFile(Target target, String path, Context context) {
			this(target, path, context, resolveChild(context,
					getDocumentUriFromTree(context, target.safTarget), path));
		}

		private static Uri getDocumentUriFromTree(Context context, SafTarget safTarget) {
			Uri treeUri = safTarget.uriTree.getUriTree(context);
			return treeUri != null ? DocumentsContract.buildDocumentUriUsingTree(treeUri,
					DocumentsContract.getTreeDocumentId(treeUri)) : null;
		}

		private static Resolution resolveChild(Context context, Uri documentUri, String path) {
			if (StringUtils.isEmpty(path)) {
				return new Resolution(documentUri, null, null, true, 0L);
			} else if (documentUri == null) {
				return new Resolution(null, path, null, false, 0L);
			} else {
				ContentResolver contentResolver = context.getContentResolver();
				String[] segments = path.split("/");
				boolean success = true;
				CursorExtra cursorExtra = null;
				for (int i = 0; i < segments.length - 1; i++) {
					String segment = segments[i];
					Object uriOrExtra = findChildDocumentUriOrExtra(contentResolver, documentUri, segment, null);
					if (uriOrExtra instanceof Uri) {
						documentUri = (Uri) uriOrExtra;
						segments[i] = null;
					} else {
						success = false;
						cursorExtra = (CursorExtra) uriOrExtra;
						break;
					}
				}
				if (success) {
					String name = segments[segments.length - 1];
					ChildExtra childExtra = new ChildExtra();
					Object uriOrExtra = findChildDocumentUriOrExtra(contentResolver, documentUri, name, childExtra);
					if (uriOrExtra instanceof Uri) {
						return new Resolution((Uri) uriOrExtra, null, null,
								childExtra.isDirectory, childExtra.lastModified);
					} else {
						return new Resolution(documentUri, name, (CursorExtra) uriOrExtra, true, 0L);
					}
				} else {
					StringBuilder builder = new StringBuilder();
					for (String segment : segments) {
						if (segment != null) {
							if (builder.length() > 0) {
								builder.append('/');
							}
							builder.append(segment);
						}
					}
					return new Resolution(documentUri, builder.toString(), cursorExtra, true, 0L);
				}
			}
		}

		private static class ChildExtra {
			public boolean isDirectory;
			public long lastModified;
		}

		private static final String[] PROJECTION_CHILD_SIMPLE = {DocumentsContract.Document.COLUMN_DOCUMENT_ID,
				DocumentsContract.Document.COLUMN_DISPLAY_NAME};
		private static final String[] PROJECTION_CHILD_EXTRA = {DocumentsContract.Document.COLUMN_DOCUMENT_ID,
				DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE,
				DocumentsContract.Document.COLUMN_LAST_MODIFIED};

		private static Object findChildDocumentUriOrExtra(ContentResolver contentResolver,
				Uri documentUri, String displayName, ChildExtra childExtra) {
			String[] projection = childExtra != null ? PROJECTION_CHILD_EXTRA : PROJECTION_CHILD_SIMPLE;
			Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(documentUri,
					DocumentsContract.getDocumentId(documentUri));
			Cursor cursor = contentResolver.query(childrenUri, projection, null, null, null);
			if (cursor == null) {
				return null;
			}
			boolean loading = cursor.getExtras().getBoolean(DocumentsContract.EXTRA_LOADING);
			boolean error = cursor.getExtras().getBoolean(DocumentsContract.EXTRA_ERROR);
			try {
				while (cursor.moveToNext()) {
					if (displayName.toLowerCase(Locale.getDefault()).equals(StringUtils
							.emptyIfNull(cursor.getString(1)).toLowerCase(Locale.getDefault()))) {
						Uri childDocumentUri = DocumentsContract
								.buildDocumentUriUsingTree(documentUri, cursor.getString(0));
						if (childExtra != null) {
							childExtra.isDirectory = DocumentsContract.Document.MIME_TYPE_DIR
									.equals(cursor.getString(2));
							childExtra.lastModified = cursor.getLong(3);
						}
						return childDocumentUri;
					}
				}
			} finally {
				cursor.close();
			}
			return loading ? CursorExtra.LOADING : error ? CursorExtra.ERROR : null;
		}

		@Override
		public Pair<File, Uri> getFileOrUri() {
			Resolution resolution = this.resolution;
			return new Pair<>(null, exists(resolution) ? resolution.documentUri : null);
		}

		private static boolean exists(Resolution resolution) {
			return resolution.documentUri != null && resolution.unresolvedPath == null;
		}

		@Override
		public boolean exists() {
			return exists(resolution);
		}

		@Override
		public String getName() {
			String relativePath = getRelativePath();
			if (relativePath.isEmpty()) {
				Uri documentUri = getDocumentUriFromTree(context, getTarget().safTarget);
				if (documentUri == null) {
					return null;
				}
				String[] projection = {DocumentsContract.Document.COLUMN_DISPLAY_NAME};
				Cursor cursor = context.getContentResolver().query(documentUri, projection, null, null, null);
				if (cursor == null) {
					return null;
				}
				try {
					if (cursor.moveToFirst()) {
						return cursor.getString(0);
					} else {
						return null;
					}
				} finally {
					cursor.close();
				}
			} else {
				int index = relativePath.lastIndexOf('/');
				return index >= 0 ? relativePath.substring(index + 1) : relativePath;
			}
		}

		@Override
		public boolean isDirectory() {
			return resolution.isDirectory;
		}

		@Override
		public long getLastModified() {
			return resolution.lastModified;
		}

		@Override
		public DataFile getChild(String path) {
			if (StringUtils.isEmpty(path)) {
				return this;
			} else {
				String relativePath = getRelativePath();
				String fullPath = validatePath(relativePath.isEmpty() ? path : relativePath + "/" + path);
				String unresolvedPath = validatePath(resolution.unresolvedPath != null
						? resolution.unresolvedPath + "/" + path : path);
				Resolution resolution = resolveChild(context, this.resolution.documentUri, unresolvedPath);
				return new SafFile(getTarget(), fullPath, context, resolution);
			}
		}

		@Override
		public List<DataFile> getChildren() {
			Resolution resolution = this.resolution;
			if (exists(resolution) && resolution.isDirectory) {
				Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(resolution.documentUri,
						DocumentsContract.getDocumentId(resolution.documentUri));
				Cursor cursor = context.getContentResolver().query(childrenUri,
						PROJECTION_CHILD_EXTRA, null, null, null);
				if (cursor == null) {
					return Collections.emptyList();
				}
				String relativePath = getRelativePath();
				ArrayList<DataFile> result = new ArrayList<>(cursor.getCount());
				try {
					while (cursor.moveToNext()) {
						Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(childrenUri, cursor.getString(0));
						String name = cursor.getString(1);
						boolean isDirectory = DocumentsContract.Document.MIME_TYPE_DIR
								.equals(cursor.getString(2));
						long lastModified = cursor.getLong(3);
						String path = relativePath.isEmpty() ? name : relativePath + "/" + name;
						result.add(new SafFile(getTarget(), path, context,
								new Resolution(documentUri, null, null, isDirectory, lastModified)));
					}
				} finally {
					cursor.close();
				}
				return result;
			} else {
				return null;
			}
		}

		@Override
		public boolean delete() {
			Resolution resolution = this.resolution;
			if (exists(resolution)) {
				try {
					Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(resolution.documentUri,
							DocumentsContract.getTreeDocumentId(resolution.documentUri));
					this.resolution = new Resolution(documentUri, getRelativePath(), null, false, 0L);
					return DocumentsContract.deleteDocument(context.getContentResolver(), resolution.documentUri);
				} catch (IOException e) {
					// Ignore
				}
			}
			return false;
		}

		@Override
		public InputStream openInputStream() throws IOException {
			Resolution resolution = this.resolution;
			if (!exists(resolution)) {
				throw new FileNotFoundException("File not found");
			}
			return context.getContentResolver().openInputStream(resolution.documentUri);
		}

		@Override
		public OutputStream openOutputStream() throws IOException {
			Resolution resolution = this.resolution;
			boolean mayUpdateResolution = true;
			if (resolution.documentUri == null) {
				Uri documentUri = getDocumentUriFromTree(context, getTarget().safTarget);
				if (documentUri == null) {
					throw new FileNotFoundException("No access");
				}
				resolution = resolveChild(context, documentUri, resolution.unresolvedPath);
				this.resolution = resolution;
				mayUpdateResolution = false;
			}
			if (exists(resolution)) {
				return context.getContentResolver().openOutputStream(resolution.documentUri);
			}
			if (StringUtils.isEmpty(resolution.unresolvedPath)) {
				throw new FileNotFoundException("No access");
			}
			if (mayUpdateResolution && resolution.cursorExtra != null) {
				resolution = resolveChild(context, resolution.documentUri, resolution.unresolvedPath);
				this.resolution = resolution;
			}
			if (resolution.cursorExtra != null) {
				throw new IOException("Tree is not ready: " + resolution.cursorExtra.name());
			}
			ContentResolver contentResolver = context.getContentResolver();
			String[] segments = resolution.unresolvedPath.split("/");
			Uri childDocumentUri = resolution.documentUri;
			for (int i = 0; i < segments.length - 1; i++) {
				String displayName = segments[i];
				childDocumentUri = DocumentsContract.createDocument(contentResolver, childDocumentUri,
						DocumentsContract.Document.MIME_TYPE_DIR, displayName);
				if (childDocumentUri == null) {
					throw new FileNotFoundException("Couldn't create a directory " + displayName);
				}
			}
			String name = segments[segments.length - 1];
			String mimeType = MimeTypes.forExtension(StringUtils.getFileExtension(name), "application/octet-stream");
			Uri documentUri = DocumentsContract.createDocument(contentResolver, childDocumentUri, mimeType, name);
			if (documentUri == null) {
				throw new FileNotFoundException("Couldn't create a file " + name);
			}
			this.resolution = new Resolution(documentUri, null, null, false, System.currentTimeMillis());
			return contentResolver.openOutputStream(documentUri);
		}
	}
}
