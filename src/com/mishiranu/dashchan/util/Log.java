package com.mishiranu.dashchan.util;

import android.graphics.Bitmap;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import chan.util.CommonUtils;
import com.mishiranu.dashchan.BuildConfig;
import com.mishiranu.dashchan.content.MainApplication;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Locale;

public enum Log {
	FILE_NAME, DISABLE_QUOTES, TYPE_WARNING, TYPE_ERROR;

	private static final String STACK_TRACE_DIVIDER = "----------------------------------------";

	private static final String TAG = "Dashchan";
	private static final int MAX_FILES_COUNT = 20;
	private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

	private static final Persistent PERSISTENT = new Persistent();

	private static String technicalData;
	private static PrintStream logOutput;
	private static ParcelFileDescriptor pipeFd;

	public static class Persistent {
		private Persistent() {}

		public void write(Object... data) {
			Log.write(data);
		}

		public void stack(Throwable t) {
			Log.stack(t);
		}

		public int getFd() {
			ParcelFileDescriptor fd = pipeFd;
			return fd != null ? fd.getFd() : -1;
		}
	}

	public static Persistent persistent() {
		return PERSISTENT;
	}

	@Deprecated
	public static void write(Object... data) {
		StringBuilder builder = new StringBuilder();
		Log typeFlag = null;
		Log lastFlag = null;
		boolean quote = true;
		if (data == null || data.length == 0) {
			builder.append("no arguments");
		} else {
			for (int i = 0; i < data.length; i++) {
				Log flagToUse = lastFlag;
				lastFlag = null;
				if (data[i] instanceof Log) {
					Log flag = (Log) data[i];
					if (i == 0 && (flag == TYPE_ERROR || flag == TYPE_WARNING)) {
						typeFlag = flag;
					} else if (flag == DISABLE_QUOTES) {
						quote = false;
					}
					lastFlag = flag;
					continue;
				}
				if (builder.length() > 0) {
					builder.append(' ');
				}
				if (data[i] == null) {
					builder.append("null");
				} else if (data[i] instanceof CharSequence) {
					String string = data[i].toString().replace("\n", "[LF]").replace("\r", "[CR]");
					if (quote) {
						builder.append('"');
					}
					builder.append(string);
					if (quote) {
						builder.append('"');
					}
				} else if (data[i] instanceof Boolean) {
					builder.append((boolean) data[i] ? "true" : "false");
				} else if (data[i] instanceof Character) {
					char c = (Character) data[i];
					if (c == '\n') {
						builder.append("'[LF]'");
					} else if (c == '\r') {
						builder.append("'[CR]'");
					} else {
						builder.append("'").append(data[i]).append("'");
					}
				} else if (data[i] instanceof Object[]) {
					Object[] array = ((Object[]) data[i]);
					String simpleName = array.getClass().getSimpleName();
					if (simpleName.endsWith("[]")) {
						simpleName = simpleName.substring(0, simpleName.length() - 2);
					}
					builder.append(simpleName).append('[').append(array.length).append(']');
				} else if (data[i] instanceof Throwable) {
					Throwable t = (Throwable) data[i];
					for (int j = 0; t != null; j++) {
						if (j > 0) {
							builder.append(", caused by ");
						}
						builder.append(t.getClass().getName()).append(':').append(j);
						String message = t.getMessage();
						if (message != null) {
							builder.append(":\"").append(message).append('"');
						}
						t = t.getCause();
					}
				} else if (data[i] instanceof Bitmap) {
					Bitmap bitmap = (Bitmap) data[i];
					builder.append("Bitmap:");
					boolean recycled = bitmap.isRecycled();
					builder.append(recycled ? "recycled" : "alive");
					if (!recycled) {
						builder.append(':').append(bitmap.getWidth()).append('x').append(bitmap.getHeight());
					}
				} else if (data[i] instanceof File) {
					File file = (File) data[i];
					builder.append("File:\"").append(flagToUse == FILE_NAME ? file.getName() : file.getAbsolutePath())
							.append("\":").append(file.exists() ? "exists" : "notexists").append(':')
							.append(file.length());
				} else {
					builder.append(data[i].toString());
				}
			}
		}
		String message = builder.toString();
		final int max = 1024;
		for (int i = 0; i < message.length(); i += max) {
			String part = message.substring(i, Math.min(message.length(), i + max));
			if (typeFlag == TYPE_ERROR) {
				android.util.Log.e(TAG, part);
			} else if (typeFlag == TYPE_WARNING) {
				android.util.Log.w(TAG, part);
			} else {
				android.util.Log.d(TAG, part);
			}
		}
		PrintStream logOutput = Log.logOutput;
		if (logOutput != null) {
			synchronized (logOutput) {
				logOutput.append(TIME_FORMAT.format(System.currentTimeMillis())).append(": ").append(message);
				logOutput.append('\n');
			}
		}
	}

	@Deprecated
	public static void stack(Throwable t) {
		if (t != null) {
			t.printStackTrace();
			PrintStream logOutput = Log.logOutput;
			if (logOutput != null) {
				synchronized (logOutput) {
					logOutput.println(STACK_TRACE_DIVIDER);
					logOutput.print(technicalData);
					logOutput.println(STACK_TRACE_DIVIDER);
					t.printStackTrace(logOutput);
					logOutput.println(STACK_TRACE_DIVIDER);
				}
			}
		}
	}

	@Deprecated
	public static void sleep(long interval) {
		CommonUtils.sleepMaxRealtime(SystemClock.elapsedRealtime(), interval);
	}

	public static void init() {
		technicalData = "Device: " + Build.MANUFACTURER + " " + Build.DEVICE + " (" + Build.MODEL + ")\n" +
				"API: " + Build.VERSION.SDK_INT + " (" + Build.VERSION.RELEASE + ")\n" +
				"Application: " + BuildConfig.VERSION_CODE + " (" + BuildConfig.VERSION_NAME + ")\n";
		File cacheDirectory = MainApplication.getInstance().getExternalCacheDir();
		if (cacheDirectory != null) {
			File packageDirectory = cacheDirectory.getParentFile();
			initDirectories(packageDirectory);
		}
		ParcelFileDescriptor[] pipe = null;
		try {
			pipe = ParcelFileDescriptor.createPipe();
		} catch (IOException e) {
			persistent().stack(e);
		}
		if (pipe != null) {
			pipeFd = pipe[1];
			ParcelFileDescriptor input = pipe[0];
			@SuppressWarnings("InfiniteLoopStatement")
			Runnable runnable = () -> {
				try (BufferedReader reader = new BufferedReader(new FileReader(input.getFileDescriptor()))) {
					while (true) {
						String line = reader.readLine();
						persistent().write(line);
					}
				} catch (IOException e) {
					persistent().stack(e);
				}
			};
			new Thread(runnable, "PipeLogger").start();
		}
	}

	private static void initDirectories(File packageDirectory) {
		File logsDirectory = new File(packageDirectory, "logs");
		if (logsDirectory.exists() && logsDirectory.isDirectory()) {
			File[] files = logsDirectory.listFiles();
			if (files != null) {
				boolean deleted = false;
				for (File file : files) {
					if (file.length() == 0) {
						file.delete();
						deleted = true;
					}
				}
				if (deleted) {
					files = logsDirectory.listFiles();
				}
				if (files != null && files.length > MAX_FILES_COUNT) {
					Arrays.sort(files, IOUtils.SORT_BY_DATE);
					for (int i = 0; i < files.length - MAX_FILES_COUNT + 1; i++) {
						files[i].delete();
					}
				}
			}
			File logFile = new File(logsDirectory, "log-" + System.currentTimeMillis() + ".txt");
			try {
				logOutput = new PrintStream(logFile);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		File errorsDirectory = new File(packageDirectory, "errors");
		if (errorsDirectory.exists() || errorsDirectory.mkdirs()) {
			Thread.UncaughtExceptionHandler systemHandler = Thread.getDefaultUncaughtExceptionHandler();
			Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
				try {
					File errorFile = new File(errorsDirectory, "error-" + System.currentTimeMillis() + ".txt");
					PrintStream stream = new PrintStream(errorFile);
					stream.print(technicalData);
					stream.println(STACK_TRACE_DIVIDER);
					ex.printStackTrace(stream);
				} catch (Throwable t) {
					// Ignore any exceptions in default exception handler
				} finally {
					systemHandler.uncaughtException(thread, ex);
				}
			});
		}
	}
}
