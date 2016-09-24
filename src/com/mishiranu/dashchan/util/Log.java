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

package com.mishiranu.dashchan.util;

import java.io.File;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Locale;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Environment;

import chan.util.CommonUtils;

@SuppressWarnings({"deprecation", "unused"})
public enum Log
{
	ELAPSED_MARK, ELAPSED_MARK_UPDATE, FILE_NAME, DISABLE_QUOTES, TYPE_WARNING, TYPE_ERROR;

	private static final String STACK_TRACE_DIVIDER = "----------------------------------------";

	private static final String TAG = "Dashchan";
	private static final int MAX_FILES_COUNT = 20;
	private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

	private static final ThreadLocal<Long> MARK_TIME = new ThreadLocal<>();
	private static final Persistent PERSISTENT = new Persistent();

	private static String sTechnicalData;
	private static PrintStream sLogOutput;

	public static class Persistent
	{
		private Persistent()
		{

		}

		public void mark()
		{
			Log.mark();
		}

		public void write(Object... data)
		{
			Log.write(data);
		}

		public void stack(Throwable t)
		{
			Log.stack(t);
		}
	}

	public static Persistent persistent()
	{
		return PERSISTENT;
	}

	@Deprecated
	public static void mark()
	{
		MARK_TIME.set(System.nanoTime());
	}

	@Deprecated
	public static void write(Object... data)
	{
		StringBuilder builder = new StringBuilder();
		Log typeFlag = null;
		Log lastFlag = null;
		boolean qouteStrings = true;
		if (data == null || data.length == 0) builder.append("no arguments");
		else for (int i = 0; i < data.length; i++)
		{
			Log flagToUse = lastFlag;
			lastFlag = null;
			if (data[i] instanceof Log)
			{
				Log flag = (Log) data[i];
				if (flag == ELAPSED_MARK || flag == ELAPSED_MARK_UPDATE)
				{
					if (builder.length() > 0) builder.append(' ');
					Long markTime = MARK_TIME.get();
					builder.append((System.nanoTime() - (markTime != null ? markTime : 0L)) / 1000 / 1000f);
					if (flag == ELAPSED_MARK_UPDATE) mark();
				}
				else if (i == 0 && (flag == TYPE_ERROR || flag == TYPE_WARNING)) typeFlag = flag;
				else if (flag == DISABLE_QUOTES) qouteStrings = false;
				lastFlag = flag;
				continue;
			}
			if (builder.length() > 0) builder.append(' ');
			if (data[i] == null) builder.append("null");
			else if (data[i] instanceof CharSequence)
			{
				String string = data[i].toString().replace("\n", "[LF]").replace("\r", "[CR]");
				if (qouteStrings) builder.append('"');
				builder.append(string);
				if (qouteStrings) builder.append('"');
			}
			else if (data[i] instanceof Boolean) builder.append((boolean) data[i] ? "true" : "false");
			else if (data[i] instanceof Character)
			{
				char c = (Character) data[i];
				if (c == '\n') builder.append("'[LF]'");
				else if (c == '\r') builder.append("'[CR]'");
				else builder.append("'").append(data[i]).append("'");
			}
			else if (data[i] instanceof Object[])
			{
				Object[] array = ((Object[]) data[i]);
				String simpleName = array.getClass().getSimpleName();
				if (simpleName.endsWith("[]")) simpleName = simpleName.substring(0, simpleName.length() - 2);
				builder.append(simpleName).append('[').append(array.length).append(']');
			}
			else if (data[i] instanceof Throwable)
			{
				Throwable t = (Throwable) data[i];
				for (int j = 0; t != null; j++)
				{
					if (j > 0) builder.append(", caused by ");
					builder.append(t.getClass().getName()).append(':').append(j);
					String message = t.getMessage();
					if (message != null) builder.append(":\"").append(message).append('"');
					t = t.getCause();
				}
			}
			else if (data[i] instanceof Bitmap)
			{
				Bitmap bitmap = (Bitmap) data[i];
				builder.append("Bitmap:");
				boolean recycled = bitmap.isRecycled();
				builder.append(recycled ? "recycled" : "alive");
				if (!recycled) builder.append(':').append(bitmap.getWidth()).append('x').append(bitmap.getHeight());
			}
			else if (data[i] instanceof File)
			{
				File file = (File) data[i];
				builder.append("File:\"").append(flagToUse == FILE_NAME ? file.getName() : file.getAbsolutePath())
						.append("\":").append(file.exists() ? "exists" : "notexists").append(':').append(file.length());
			}
			else builder.append(data[i].toString());
		}
		String message = builder.toString();
		final int max = 1024;
		for (int i = 0; i < message.length(); i += max)
		{
			String part = message.substring(i, Math.min(message.length(), i + max));
			if (typeFlag == TYPE_ERROR) android.util.Log.e(TAG, part);
			else if (typeFlag == TYPE_WARNING) android.util.Log.w(TAG, part);
			else android.util.Log.d(TAG, part);
		}
		if (sLogOutput != null)
		{
			synchronized (sLogOutput)
			{
				sLogOutput.append(TIME_FORMAT.format(System.currentTimeMillis())).append(": ").append(message);
				sLogOutput.append('\n');
			}
		}
	}

	@Deprecated
	public static void stack(Throwable t)
	{
		if (t != null)
		{
			t.printStackTrace();
			if (sLogOutput != null)
			{
				synchronized (sLogOutput)
				{
					sLogOutput.println(STACK_TRACE_DIVIDER);
					sLogOutput.print(sTechnicalData);
					sLogOutput.println(STACK_TRACE_DIVIDER);
					t.printStackTrace(sLogOutput);
					sLogOutput.println(STACK_TRACE_DIVIDER);
				}
			}
		}
	}

	@Deprecated
	public static void sleep(long interval)
	{
		CommonUtils.sleepMaxTime(System.currentTimeMillis(), interval);
	}

	public static void init(Context context)
	{
		PackageInfo packageInfo;
		try
		{
			packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
		sTechnicalData = "Device: " + Build.MANUFACTURER + " " + Build.DEVICE + " (" + Build.MODEL + ")\n"
				+ "SDK: " + Build.VERSION.SDK_INT + " (" + Build.VERSION.RELEASE + ")\n"
				+ "Application: " + packageInfo.versionCode + " (" + packageInfo.versionName + ")\n";
		File packageDirectory = new File(Environment.getExternalStorageDirectory(),  "Android/data/"
				+ context.getPackageName());
		File logsDirectory = new File(packageDirectory, "logs");
		if (logsDirectory.exists() && logsDirectory.isDirectory())
		{
			File[] files = logsDirectory.listFiles();
			if (files != null)
			{
				boolean deleted = false;
				for (File file : files)
				{
					if (file.length() == 0)
					{
						file.delete();
						deleted = true;
					}
				}
				if (deleted) files = logsDirectory.listFiles();
				if (files != null && files.length > MAX_FILES_COUNT)
				{
					Arrays.sort(files, IOUtils.SORT_BY_DATE);
					for (int i = 0; i < files.length - MAX_FILES_COUNT + 1; i++) files[i].delete();
				}
			}
			File logFile = new File(logsDirectory, "log-" + System.currentTimeMillis() + ".txt");
			try
			{
				sLogOutput = new PrintStream(logFile);
			}
			catch (Exception e)
			{

			}
		}
		File errorsDirectory = new File(packageDirectory, "errors");
		if (errorsDirectory.exists() || errorsDirectory.mkdirs())
		{
			Thread.UncaughtExceptionHandler systemHandler = Thread.getDefaultUncaughtExceptionHandler();
			Thread.setDefaultUncaughtExceptionHandler((thread, ex) ->
			{
				try
				{
					File errorFile = new File(errorsDirectory, "error-" + System.currentTimeMillis() + ".txt");
					PrintStream stream = new PrintStream(errorFile);
					stream.print(sTechnicalData);
					stream.println(STACK_TRACE_DIVIDER);
					ex.printStackTrace(stream);
				}
				catch (Throwable t)
				{
					// It's OK to catch Throwable here, let's forget about it...
				}
				finally
				{
					systemHandler.uncaughtException(thread, ex);
				}
			});
		}
	}
}