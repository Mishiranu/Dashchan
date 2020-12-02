package com.mishiranu.dashchan.content;

import android.content.Context;
import android.util.Pair;
import chan.util.DataFile;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.database.CommonDatabase;
import com.mishiranu.dashchan.content.service.DownloadService;
import com.mishiranu.dashchan.content.storage.AutohideStorage;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.content.storage.StatisticsStorage;
import com.mishiranu.dashchan.content.storage.ThemesStorage;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.widget.ClickableToast;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class BackupManager {
	private static final String FILE_NAME_PREFIX = "backup-";
	private static final String FILE_NAME_SUFFIX = ".zip";

	private static final String BACKUP_VERSION_0 = "dashchan:0";
	private static final String BACKUP_VERSION_1 = "dashchan:1";

	public static class BackupFile implements Comparable<BackupFile> {
		public final DataFile file;
		public final String name;
		public final long date;

		public BackupFile(DataFile file, String name, long date) {
			this.file = file;
			this.name = name;
			this.date = date;
		}

		@Override
		public int compareTo(BackupFile o) {
			return Long.compare(o.date, date);
		}
	}

	private static class Restore {
		public final boolean test;
		public final InputStream input;
		public String version;

		public Restore(boolean test, InputStream input) {
			this.test = test;
			this.input = input;
		}
	}

	private interface Writer {
		void write(OutputStream output) throws IOException;
	}

	private interface Reader {
		void read(Restore restore) throws IOException;
	}

	private static class FileWriter implements Writer {
		private final File file;

		public FileWriter(File file) {
			this.file = file;
		}

		@Override
		public void write(OutputStream output) throws IOException {
			if (file.exists()) {
				try (FileInputStream input = new FileInputStream(file)) {
					IOUtils.copyStream(input, output);
				}
			}
		}
	}

	private static class FileReader implements Reader {
		private final File file;

		public FileReader(File file) {
			this.file = file;
		}

		@Override
		public void read(Restore restore) throws IOException {
			if (!restore.test) {
				try (FileOutputStream output = new FileOutputStream(file)) {
					IOUtils.copyStream(restore.input, output);
					output.getFD().sync();
				}
			}
		}
	}

	public enum Entry {
		VERSION(0, "version", Collections.emptyList(),
				output -> output.write((BACKUP_VERSION_1 + "\n").getBytes()), restore -> {
			restore.version = null;
			byte[] data = new byte[1024];
			int count = restore.input.read(data);
			if (count <= 0 || count == data.length) {
				throw new IOException("Invalid version file");
			}
			restore.version = new String(data).trim();
		}),
		DATABASE(R.string.database, "common.db", Collections.singletonList(BACKUP_VERSION_1),
				CommonDatabase.getInstance()::writeBackup, restore -> {
			if (!restore.test) {
				CommonDatabase.getInstance().readBackup(restore.input);
			}
		}),
		PREFERENCES_0(R.string.preferences, "com.mishiranu.dashchan_preferences.xml",
				Preferences.getFileForRestore(), Collections.singletonList(BACKUP_VERSION_0)),
		PREFERENCES_1(R.string.preferences, Preferences.getFilesForBackup(),
				Collections.singletonList(BACKUP_VERSION_1)),
		FAVORITES(R.string.favorites, FavoritesStorage.getInstance().getFilesForBackup(),
				Arrays.asList(BACKUP_VERSION_0, BACKUP_VERSION_1)),
		AUTOHIDE(R.string.autohide, AutohideStorage.getInstance().getFilesForBackup(),
				Arrays.asList(BACKUP_VERSION_0, BACKUP_VERSION_1)),
		STATISTICS(R.string.statistics, StatisticsStorage.getInstance().getFilesForBackup(),
				Arrays.asList(BACKUP_VERSION_0, BACKUP_VERSION_1)),
		THEMES(R.string.themes, ThemesStorage.getInstance().getFilesForBackup(),
				Arrays.asList(BACKUP_VERSION_0, BACKUP_VERSION_1));

		public final int titleResId;
		private final String name;
		private final Writer writer;
		private final Reader reader;
		private final Set<String> versions;

		Entry(int titleResId, Pair<File, File> backupFiles, Collection<String> versions) {
			this(titleResId, backupFiles.first.getName(), versions,
					new FileWriter(backupFiles.first), new FileReader(backupFiles.second));
		}

		Entry(int titleResId, String name, File restoreFile, Collection<String> versions) {
			this(titleResId, name, versions, null, new FileReader(restoreFile));
		}

		Entry(int titleResId, String name, Collection<String> versions, Writer writer, Reader reader) {
			this.titleResId = titleResId;
			this.name = name;
			this.writer = writer;
			this.reader = reader;
			this.versions = Collections.unmodifiableSet(new HashSet<>(versions));
		}

		private static Entry find(String name) {
			for (Entry entry : Entry.values()) {
				if (entry.name.equals(name)) {
					return entry;
				}
			}
			return null;
		}
	}

	public static List<BackupFile> getAvailableBackups(Context context) {
		DataFile root = DataFile.obtain(context, DataFile.Target.DOWNLOADS, null);
		List<DataFile> files = root.getChildren();
		List<BackupFile> backupFiles = new ArrayList<>();
		if (files != null) {
			DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(context);
			DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(context);
			for (DataFile file : files) {
				String name = file.getName();
				if (name.startsWith(FILE_NAME_PREFIX) && name.endsWith(FILE_NAME_SUFFIX)) {
					name = name.substring(FILE_NAME_PREFIX.length(), name.length() - FILE_NAME_SUFFIX.length());
					long date;
					try {
						date = Long.parseLong(name);
					} catch (NumberFormatException e) {
						date = -1;
					}
					if (date >= 0) {
						name = dateFormat.format(date) + " " + timeFormat.format(date);
						backupFiles.add(new BackupFile(file, name, date));
					}
				}
			}
		}
		Collections.sort(backupFiles);
		return backupFiles;
	}

	public static void makeBackup(DownloadService.Binder binder, Context context) {
		File backupFile = new File(context.getCacheDir(), "backup-" + UUID.randomUUID());
		boolean success = false;
		try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(backupFile))) {
			boolean hasEntries = false;
			for (Entry entry : Entry.values()) {
				if (entry.writer != null) {
					zip.putNextEntry(new ZipEntry(entry.name));
					try {
						entry.writer.write(zip);
					} finally {
						zip.closeEntry();
					}
					hasEntries = true;
				}
			}
			success = hasEntries;
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (!success) {
				backupFile.delete();
			}
		}
		FileInputStream input = null;
		if (success) {
			try {
				input = new FileInputStream(backupFile);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		// FileInputStream holds a file descriptor
		backupFile.delete();
		if (success) {
			binder.downloadStorage(input, null, null, null, null,
					FILE_NAME_PREFIX + System.currentTimeMillis() + FILE_NAME_SUFFIX, false, false);
		} else {
			ClickableToast.show(R.string.no_access);
		}
	}

	public static List<Entry> readBackupEntries(DataFile file) {
		String version = BACKUP_VERSION_0;
		HashSet<Entry> entries = new HashSet<>();
		try (ZipInputStream zip = new ZipInputStream(file.openInputStream())) {
			ZipEntry zipEntry;
			while ((zipEntry = zip.getNextEntry()) != null) {
				try {
					Entry entry = Entry.find(zipEntry.getName());
					if (entry != null) {
						Restore restore = new Restore(true, zip);
						restore.version = version;
						entry.reader.read(restore);
						version = restore.version;
						entries.add(entry);
					}
				} finally {
					zip.closeEntry();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			entries.clear();
		}
		ArrayList<Entry> result = new ArrayList<>();
		for (Entry entry : Entry.values()) {
			if (entries.contains(entry)) {
				if (entry.versions.contains(version)) {
					result.add(entry);
				}
			}
		}
		return result;
	}

	public static boolean loadBackup(DataFile file, Collection<Entry> entries) {
		boolean success = false;
		try (ZipInputStream zip = new ZipInputStream(file.openInputStream())) {
			ZipEntry zipEntry;
			while ((zipEntry = zip.getNextEntry()) != null) {
				try {
					Entry entry = Entry.find(zipEntry.getName());
					if (entry != null && entries.contains(entry)) {
						entry.reader.read(new Restore(false, zip));
						success = true;
					}
				} finally {
					zip.closeEntry();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			success = false;
		}
		return success;
	}
}
