/*
 * Copyright 2014-2016 Fukurou Mishiranu
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import android.content.Context;
import android.text.format.DateFormat;

import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.storage.AutohideStorage;
import com.mishiranu.dashchan.content.storage.DatabaseHelper;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.Log;
import com.mishiranu.dashchan.util.NavigationUtils;
import com.mishiranu.dashchan.util.ToastUtils;

public class BackupManager
{
	private static final Pattern NAME_PATTERN = Pattern.compile("backup-(\\d+)\\.zip");
	
	private static final Comparator<File> COMPARATOR = new Comparator<File>()
	{
		@Override
		public int compare(File lhs, File rhs)
		{
			return rhs.getName().compareTo(lhs.getName());
		}
	};
	
	public static LinkedHashMap<File, String> getAvailableBackups(Context context)
	{
		LinkedHashMap<File, String> backups = new LinkedHashMap<>();
		File[] files = Preferences.getDownloadDirectory().listFiles();
		if (files != null)
		{
			Arrays.sort(files, COMPARATOR);
			java.text.DateFormat timeFormat = DateFormat.getTimeFormat(context);
			java.text.DateFormat dateFormat = DateFormat.getDateFormat(context);
			for (File file : files)
			{
				Matcher matcher = NAME_PATTERN.matcher(file.getName());
				if (matcher.matches())
				{
					long date = Long.parseLong(matcher.group(1));
					String dateString = dateFormat.format(date) + " " + timeFormat.format(date);
					backups.put(file, dateString);
				}
			}
		}
		return backups;
	}
	
	private static void addFileToMap(LinkedHashMap<String, File> files, File file)
	{
		files.put(file.getName(), file);
	}
	
	private static LinkedHashMap<String, File> obtainBackupFiles(Context context)
	{
		LinkedHashMap<String, File> files = new LinkedHashMap<>();
		addFileToMap(files, Preferences.getPreferencesFile());
		addFileToMap(files, DatabaseHelper.getDatabaseFile());
		addFileToMap(files, FavoritesStorage.getInstance().getFile());
		addFileToMap(files, AutohideStorage.getInstance().getFile());
		return files;
	}
	
	public static void makeBackup(Context context)
	{
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ZipOutputStream zip = new ZipOutputStream(output);
		LinkedHashMap<String, File> files = obtainBackupFiles(context);
		boolean success = true;
		for (File file : files.values())
		{
			if (file.exists() && file.canRead())
			{
				FileInputStream input = null;
				try
				{
					zip.putNextEntry(new ZipEntry(file.getName()));
					input = new FileInputStream(file);
					IOUtils.copyStream(input, zip);
					zip.closeEntry();
				}
				catch (IOException e)
				{
					success = false;
					break;
				}
				finally
				{
					IOUtils.close(input);
				}
			}
			else
			{
				success = false;
				break;
			}
		}
		if (success)
		{
			IOUtils.close(zip);
			ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray());
			DownloadManager.getInstance().saveStreamStorage(context, input, null, null, null, null,
					"backup-" + System.currentTimeMillis() + ".zip", true);
		}
		else ToastUtils.show(context, R.string.message_no_access);
	}
	
	public static void loadBackup(Context context, File file)
	{
		LinkedHashMap<String, File> files = obtainBackupFiles(context);
		ZipInputStream zip = null;
		boolean success = true;
		try
		{
			zip = new ZipInputStream(new FileInputStream(file));
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null)
			{
				String name = entry.getName();
				File target = files.get(name);
				if (target != null)
				{
					OutputStream output = null;
					try
					{
						output = new FileOutputStream(target);
						IOUtils.copyStream(zip, output);
						zip.closeEntry();
					}
					finally
					{
						IOUtils.close(output);
					}
				}
			}
		}
		catch (IOException e)
		{
			Log.persistent().stack(e);
			success = false;
		}
		finally
		{
			IOUtils.close(zip);
		}
		if (success) NavigationUtils.restartApplication(context);
		else ToastUtils.show(context, R.string.message_no_access);
	}
}