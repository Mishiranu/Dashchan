package com.mishiranu.dashchan.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.util.Log;
import chan.util.StringUtils;
import com.mishiranu.dashchan.BuildConfig;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.UUID;

public class Logger {
	private static final String DIVIDER;
	private static final String LOGGER_MESSAGE = UUID.randomUUID().toString();
	private static final int MAX_FILES_COUNT = 20;

	static {
		int count = 40;
		StringBuilder divider = new StringBuilder(count);
		for (int i = 0; i < count; i++) {
			divider.append('-');
		}
		DIVIDER = divider.toString();
	}

	public enum Type {DEBUG, ERROR}

	public static void write(Type type, String tag, Object... data) {
		StringBuilder builder = new StringBuilder();
		if (data == null || data.length == 0) {
			builder.append("no arguments");
		} else {
			for (int i = 0; i < data.length; i++) {
				if (builder.length() > 0) {
					builder.append(' ');
				}
				if (data[i] == null) {
					builder.append("null");
				} else if (data[i] instanceof CharSequence) {
					builder.append(data[i].toString().replace("\n", "[LF]").replace("\r", "[CR]"));
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
					builder.append("File:\"").append(file.getAbsolutePath())
							.append("\":").append(file.exists() ? "exists" : "notexists").append(':')
							.append(file.length());
				} else {
					builder.append(data[i].toString());
				}
			}
		}
		final int max = 1024;
		for (int i = 0; i < builder.length(); i += max) {
			String part = builder.substring(i, Math.min(builder.length(), i + max));
			switch (type) {
				case DEBUG: {
					Log.d(tag, part);
					break;
				}
				case ERROR: {
					Log.e(tag, part);
					break;
				}
				default: {
					throw new IllegalArgumentException();
				}
			}
		}
	}

	private interface Writer {
		void write(String string) throws IOException;
	}

	private static void writeTechnicalData(Writer writer) throws IOException {
		writer.write("Device: ");
		writer.write(StringUtils.emptyIfNull(Build.MANUFACTURER));
		writer.write(" ");
		writer.write(StringUtils.emptyIfNull(Build.DEVICE));
		writer.write(" (");
		writer.write(StringUtils.emptyIfNull(Build.MODEL));
		writer.write(")\n");
		writer.write("API: ");
		writer.write(Integer.toString(Build.VERSION.SDK_INT));
		writer.write(" (Android ");
		writer.write(StringUtils.emptyIfNull(Build.VERSION.RELEASE));
		writer.write(")\n");
		writer.write("Application: ");
		writer.write(Integer.toString(BuildConfig.VERSION_CODE));
		writer.write(" (");
		writer.write(BuildConfig.VERSION_NAME);
		writer.write(")\n");
	}

	public static void init(Context context) {
		File cacheDirectory = context.getExternalCacheDir();
		if (cacheDirectory != null) {
			File packageDirectory = cacheDirectory.getParentFile();
			initDirectories(packageDirectory);
		}
	}

	private static void initDirectories(File packageDirectory) {
		File logsDirectory = new File(packageDirectory, "logs");
		LogcatThread logcatThread = null;
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
			logcatThread = new LogcatThread(logFile);
		}

		File errorsDirectory = new File(packageDirectory, "errors");
		if (errorsDirectory.exists() || errorsDirectory.mkdirs()) {
			LogcatThread finalLogcatThread = logcatThread;
			Thread.UncaughtExceptionHandler systemHandler = Thread.getDefaultUncaughtExceptionHandler();
			Thread.setDefaultUncaughtExceptionHandler((thread, e) -> {
				if (finalLogcatThread != null) {
					finalLogcatThread.stop();
				}
				try {
					File errorFile = new File(errorsDirectory, "error-" + System.currentTimeMillis() + ".txt");
					PrintStream stream = new PrintStream(errorFile);
					writeTechnicalData(stream::print);
					stream.println(DIVIDER);
					e.printStackTrace(stream);
				} catch (Throwable t) {
					// Ignore any exceptions in default exception handler
				} finally {
					systemHandler.uncaughtException(thread, e);
				}
			});
		}
	}

	private static class LogcatThread implements Runnable {
		private final File file;
		private final Thread thread;
		private final String pidFilter;
		private final String startMessage = UUID.randomUUID().toString();
		private final String stopMessage = UUID.randomUUID().toString();
		private boolean started = false;

		public LogcatThread(File file) {
			this.file = file;
			pidFilter = " " + android.os.Process.myPid() + " ";
			Log.d("Logger", LOGGER_MESSAGE + startMessage);
			thread = new Thread(this, "Logger");
			thread.start();
		}

		public void stop() {
			Log.d("Logger", LOGGER_MESSAGE + stopMessage);
			boolean interrupted = false;
			while (true) {
				try {
					thread.join();
					break;
				} catch (InterruptedException e) {
					interrupted = true;
				}
			}
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
		}

		@Override
		public void run() {
			Process process = null;
			try {
				process = new ProcessBuilder().command("logcat", "-v", "threadtime")
						.redirectErrorStream(true).start();
				IOUtils.close(process.getOutputStream());
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
						FileWriter writer = new FileWriter(file)) {
					char[] buffer = new char[8 * 1024];
					while (true) {
						if (!handleInput(writer, buffer, reader.read(buffer))) {
							break;
						}
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (process != null) {
					process.destroy();
				}
			}
		}

		private final StringBuilder builder = new StringBuilder();

		private boolean handleInput(FileWriter writer, char[] input, int count) throws IOException {
			if (count < 0) {
				return false;
			}
			StringBuilder builder = this.builder;
			for (int i = 0; i < count; i++) {
				char c = input[i];
				if (c == '\n') {
					if (builder.indexOf(stopMessage) >= 0) {
						return false;
					}
					if (started) {
						if (handleLine(writer)) {
							writer.write('\n');
							writer.flush();
						}
					} else if (builder.indexOf(startMessage) >= 0) {
						started = true;
						writeTechnicalData(writer::write);
						writer.write(DIVIDER);
						writer.write('\n');
						writer.flush();
					}
					builder.setLength(0);
				} else {
					builder.append(c);
				}
			}
			return true;
		}

		private final char[] buffer = new char[8 * 1024];

		private boolean handleLine(FileWriter writer) throws IOException {
			StringBuilder builder = this.builder;
			if (builder.indexOf(LOGGER_MESSAGE) >= 0) {
				return false;
			}
			int pidIndex = builder.indexOf(pidFilter);
			if (pidIndex < 0) {
				return false;
			}
			int colonIndex = builder.indexOf(": ");
			if (colonIndex < pidIndex) {
				return false;
			}
			char[] buffer = this.buffer;
			int length = builder.length();
			for (int i = 0; i < length; i += buffer.length) {
				int end = Math.min(i + buffer.length, length);
				builder.getChars(i, end, buffer, 0);
				writer.write(buffer, 0, end - i);
			}
			return length > 0;
		}
	}
}
