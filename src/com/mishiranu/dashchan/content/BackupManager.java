package com.mishiranu.dashchan.content;

import android.content.Context;
import android.text.format.DateFormat;
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
import com.mishiranu.dashchan.util.Log;
import com.mishiranu.dashchan.util.NavigationUtils;
import com.mishiranu.dashchan.widget.ClickableToast;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class BackupManager {
	private static final Pattern NAME_PATTERN = Pattern.compile("backup-(\\d+)\\.zip");
	private static final Comparator<Pair<DataFile, String>> COMPARATOR =
			(lhs, rhs) -> rhs.second.compareTo(lhs.second);

	private interface Writer {
		boolean write(OutputStream output) throws IOException;
	}

	private interface Reader {
		void read(InputStream input) throws IOException;
	}

	private static class FileWriter implements Writer {
		private final File file;

		public FileWriter(File file) {
			this.file = file;
		}

		@Override
		public boolean write(OutputStream output) throws IOException {
			if (file.exists()) {
				try (FileInputStream input = new FileInputStream(file)) {
					IOUtils.copyStream(input, output);
					return true;
				}
			} else {
				return false;
			}
		}
	}

	private static class FileReader implements Reader {
		private final File file;

		public FileReader(File file) {
			this.file = file;
		}

		@Override
		public void read(InputStream input) throws IOException {
			try (FileOutputStream output = new FileOutputStream(file)) {
				IOUtils.copyStream(input, output);
			}
		}
	}

	private static class BackupData {
		public final String name;
		public final boolean required;
		public final Writer writer;
		public final Reader reader;

		public BackupData(boolean required, File file) {
			this(file.getName(), required, file);
		}

		public BackupData(String name, boolean required, File file) {
			this(name, required, new FileWriter(file), new FileReader(file));
		}

		public BackupData(String name, boolean required, Writer writer, Reader reader) {
			this.name = name;
			this.required = required;
			this.writer = writer;
			this.reader = reader;
		}
	}

	public static LinkedHashMap<DataFile, String> getAvailableBackups(Context context) {
		DataFile root = DataFile.obtain(context, DataFile.Target.DOWNLOADS, null);
		List<DataFile> files = root.getChildren();
		List<Pair<DataFile, String>> backupFiles = new ArrayList<>();
		if (files != null) {
			java.text.DateFormat timeFormat = DateFormat.getTimeFormat(context);
			java.text.DateFormat dateFormat = DateFormat.getDateFormat(context);
			for (DataFile file : files) {
				String name = file.getName();
				Matcher matcher = NAME_PATTERN.matcher(name);
				if (matcher.matches()) {
					long date = Long.parseLong(matcher.group(1));
					String dateString = dateFormat.format(date) + " " + timeFormat.format(date);
					backupFiles.add(new Pair<>(file, dateString));
				}
			}
		}
		Collections.sort(backupFiles, COMPARATOR);
		LinkedHashMap<DataFile, String> backups = new LinkedHashMap<>();
		for (Pair<DataFile, String> pair : backupFiles) {
			backups.put(pair.first, pair.second);
		}
		return backups;
	}

	private static final Map<String, BackupData> BACKUP_DATA_MAP;
	private static final String BACKUP_VERSION = "dashchan:1";

	private static void add(Map<String, BackupData> map, BackupData backupData) {
		map.put(backupData.name, backupData);
	}

	static {
		LinkedHashMap<String, BackupData> backupDataMap = new LinkedHashMap<>();
		add(backupDataMap, new BackupData("version", true, output -> {
			output.write((BACKUP_VERSION + "\n").getBytes());
			return true;
		}, input -> {
			byte[] data = new byte[1024];
			int count = input.read(data);
			if (count <= 0 || count == data.length) {
				throw new IOException("Invalid version file");
			}
			String version = new String(data).trim();
			if (!BACKUP_VERSION.equals(version)) {
				throw new IOException("Unsupported version");
			}
		}));
		add(backupDataMap, new BackupData("preferences.xml", true, Preferences.getPreferencesFile()));
		add(backupDataMap, new BackupData("common.db", true,
				CommonDatabase.getInstance()::writeBackup, CommonDatabase.getInstance()::readBackup));
		add(backupDataMap, new BackupData(false, FavoritesStorage.getInstance().getFile()));
		add(backupDataMap, new BackupData(false, AutohideStorage.getInstance().getFile()));
		add(backupDataMap, new BackupData(false, StatisticsStorage.getInstance().getFile()));
		add(backupDataMap, new BackupData(false, ThemesStorage.getInstance().getFile()));
		BACKUP_DATA_MAP = Collections.unmodifiableMap(backupDataMap);
	}

	public static void makeBackup(DownloadService.Binder binder, Context context) {
		File backupFile = new File(context.getCacheDir(), "backup-" + UUID.randomUUID());
		boolean success = true;
		try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(backupFile))) {
			for (BackupData backupData : BACKUP_DATA_MAP.values()) {
				boolean exists;
				try {
					zip.putNextEntry(new ZipEntry(backupData.name));
					try {
						exists = backupData.writer.write(zip);
					} finally {
						zip.closeEntry();
					}
				} catch (IOException e) {
					success = false;
					break;
				}
				if (backupData.required && !exists) {
					success = false;
					break;
				}
			}
		} catch (IOException e) {
			Log.persistent().stack(e);
			success = false;
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
				Log.persistent().stack(e);
			}
		}
		// FileInputStream holds a file descriptor
		backupFile.delete();
		if (success) {
			binder.downloadStorage(input, null, null, null, null,
					"backup-" + System.currentTimeMillis() + ".zip", false, false);
		} else {
			ClickableToast.show(R.string.no_access);
		}
	}

	public static void loadBackup(Context context, DataFile file) {
		boolean success = false;
		try (ZipInputStream zip = new ZipInputStream(file.openInputStream())) {
			ZipEntry entry;
			boolean first = true;
			while ((entry = zip.getNextEntry()) != null) {
				String name = entry.getName();
				if (first && !"version".equals(name)) {
					throw new IOException("Version file should be the first ZIP entry");
				}
				first = false;
				try {
					BackupData backupData = BACKUP_DATA_MAP.get(name);
					if (backupData != null) {
						backupData.reader.read(zip);
						success = true;
					}
				} finally {
					zip.closeEntry();
				}
			}
		} catch (IOException e) {
			Log.persistent().stack(e);
			success = false;
		}
		if (success) {
			NavigationUtils.restartApplication(context);
		} else {
			ClickableToast.show(R.string.unknown_error);
		}
	}
}
