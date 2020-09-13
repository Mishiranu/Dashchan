package com.mishiranu.dashchan.content;

import android.content.Context;
import android.text.format.DateFormat;
import android.util.Pair;
import chan.util.DataFile;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.service.DownloadService;
import com.mishiranu.dashchan.content.storage.AutohideStorage;
import com.mishiranu.dashchan.content.storage.DatabaseHelper;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.content.storage.StatisticsStorage;
import com.mishiranu.dashchan.content.storage.ThemesStorage;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.Log;
import com.mishiranu.dashchan.util.NavigationUtils;
import com.mishiranu.dashchan.util.ToastUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
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

	private static void addFileToMap(LinkedHashMap<String, Pair<File, Boolean>> files, File file, boolean mustExist) {
		files.put(file.getName(), new Pair<>(file, mustExist));
	}

	private static LinkedHashMap<String, Pair<File, Boolean>> obtainBackupFiles() {
		LinkedHashMap<String, Pair<File, Boolean>> files = new LinkedHashMap<>();
		addFileToMap(files, Preferences.getPreferencesFile(), true);
		addFileToMap(files, DatabaseHelper.getDatabaseFile(), true);
		addFileToMap(files, FavoritesStorage.getInstance().getFile(), false);
		addFileToMap(files, AutohideStorage.getInstance().getFile(), false);
		addFileToMap(files, StatisticsStorage.getInstance().getFile(), false);
		addFileToMap(files, ThemesStorage.getInstance().getFile(), false);
		return files;
	}

	public static void makeBackup(DownloadService.Binder binder, Context context) {
		File backupFile = new File(context.getCacheDir(), "backup-" + UUID.randomUUID());
		boolean success = true;
		try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(backupFile))) {
			LinkedHashMap<String, Pair<File, Boolean>> files = obtainBackupFiles();
			for (Pair<File, Boolean> pair : files.values()) {
				if (pair.first.exists()) {
					try (FileInputStream input = new FileInputStream(pair.first)) {
						zip.putNextEntry(new ZipEntry(pair.first.getName()));
						IOUtils.copyStream(input, zip);
						zip.closeEntry();
					}
				} else if (pair.second) {
					success = false;
					break;
				}
			}
		} catch (IOException e) {
			Log.persistent().write(e);
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
				Log.persistent().write(e);
			}
		}
		// FileInputStream holds a file descriptor
		backupFile.delete();
		if (success) {
			binder.downloadStorage(input, null, null, null, null,
					"backup-" + System.currentTimeMillis() + ".zip", false, false);
		} else {
			ToastUtils.show(context, R.string.no_access);
		}
	}

	public static void loadBackup(Context context, DataFile file) {
		LinkedHashMap<String, Pair<File, Boolean>> files = obtainBackupFiles();
		ZipInputStream zip = null;
		boolean success = true;
		try {
			zip = new ZipInputStream(file.openInputStream());
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				String name = entry.getName();
				Pair<File, Boolean> pair = files.get(name);
				if (pair != null) {
					OutputStream output = null;
					try {
						output = new FileOutputStream(pair.first);
						IOUtils.copyStream(zip, output);
						zip.closeEntry();
					} finally {
						IOUtils.close(output);
					}
				}
			}
		} catch (IOException e) {
			Log.persistent().stack(e);
			success = false;
		} finally {
			IOUtils.close(zip);
		}
		if (success) {
			NavigationUtils.restartApplication(context);
		} else {
			ToastUtils.show(context, R.string.no_access);
		}
	}
}
