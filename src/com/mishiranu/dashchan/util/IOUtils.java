package com.mishiranu.dashchan.util;

import android.content.res.Resources;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Comparator;

public class IOUtils {
	public static int bytesToInt(boolean littleEndian, int start, int count, byte... bytes) {
		int result = 0;
		for (int i = 0; i < count; i++) {
			result = result << 8 | bytes[start + (littleEndian ? count - i - 1 : i)] & 0xff;
		}
		return result;
	}

	public static byte[] intToBytes(int value, boolean littleEndian, int start, int count, byte[] bytes) {
		if (bytes == null) {
			bytes = new byte[start + count];
		}
		for (int i = 0; i < count; i++) {
			bytes[start + (littleEndian ? i : count - i - 1)] = (byte) (value & 0xff);
			value >>>= 8;
		}
		return bytes;
	}

	public static int skipExactly(InputStream input, int count) throws IOException {
		int total = 0;
		while (count - total > 0) {
			int skipped = (int) input.skip(count - total);
			if (skipped == 0) {
				int read = input.read();
				if (read == -1) {
					break;
				}
				total++;
			}
			total += skipped;
		}
		return total;
	}

	public static boolean skipExactlyCheck(InputStream input, int count) throws IOException {
		return skipExactly(input, count) == count;
	}

	public static int readExactly(InputStream input, byte[] buffer, int offset, int count) throws IOException {
		int total = 0;
		while (count - total > 0) {
			int read = input.read(buffer, offset + total, count - total);
			if (read == -1) {
				break;
			}
			total += read;
		}
		return total;
	}

	public static boolean readExactlyCheck(InputStream input, byte[] buffer, int offset, int count) throws IOException {
		return readExactly(input, buffer, offset, count) == count;
	}

	public static void copyStream(InputStream from, OutputStream to) throws IOException {
		byte[] data = new byte[8192];
		int count;
		while ((count = from.read(data)) != -1) {
			to.write(data, 0, count);
		}
	}

	public static boolean close(Closeable closeable) {
		try {
			if (closeable != null) {
				closeable.close();
			}
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	public static String readRawResourceString(Resources resources, int resId) {
		InputStream input = null;
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		try {
			input = resources.openRawResource(resId);
			IOUtils.copyStream(input, output);
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			IOUtils.close(input);
		}
		return new String(output.toByteArray());
	}

	public static boolean copyInternalFile(File from, File to) {
		FileInputStream input = null;
		FileOutputStream output = null;
		try {
			input = new FileInputStream(from);
			output = new FileOutputStream(to);
			copyStream(input, output);
			return true;
		} catch (IOException e) {
			return false;
		} finally {
			close(input);
			close(output);
		}
	}

	public static final Comparator<File> SORT_BY_DATE =
			(lhs, rhs) -> ((Long) lhs.lastModified()).compareTo(rhs.lastModified());

	public static void deleteRecursive(File file) {
		if (file.isDirectory()) {
			File[] files = file.listFiles();
			if (files != null) {
				for (File innerFile : files) {
					deleteRecursive(innerFile);
				}
			}
		}
		file.delete();
	}
}
