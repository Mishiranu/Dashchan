package com.mishiranu.dashchan.content.net;

import android.net.Uri;
import android.util.Base64;
import android.util.Xml;
import chan.http.HttpClient;
import chan.http.HttpException;
import chan.http.InetSocket;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.util.IOUtils;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.InflaterInputStream;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

public class SubversionProtocol {
	private static class ChunkedOutputStream extends OutputStream {
		private final OutputStream output;

		public ChunkedOutputStream(OutputStream output) {
			this.output = output;
		}

		@Override
		public void write(int b) throws IOException {
			output.write('1');
			output.write('\r');
			output.write('\n');
			output.write(b);
			output.write('\r');
			output.write('\n');
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			if (Thread.interrupted()) {
				throw new InterruptedIOException();
			}
			if (len > 0) {
				output.write(String.format(Locale.US, "%x", len).getBytes());
				output.write('\r');
				output.write('\n');
				output.write(b, off, len);
				output.write('\r');
				output.write('\n');
			}
		}

		@Override
		public void close() throws IOException {
			output.write('0');
			output.write('\r');
			output.write('\n');
			output.write('\r');
			output.write('\n');
			output.flush();
		}
	}

	private static class ChunkedInputStream extends InputStream {
		private final InputStream input;
		private final byte[] singleByte = new byte[1];
		private long bytesLeft = -1;
		private boolean finished;

		private ChunkedInputStream(InputStream input) {
			this.input = input;
		}

		@Override
		public int read() throws IOException {
			int count = read(singleByte, 0, 1);
			return count == 1 ? singleByte[0] & 0xff : -1;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			if (Thread.interrupted()) {
				throw new InterruptedIOException();
			}
			if (finished) {
				return -1;
			}
			if (bytesLeft < 0) {
				long size = 0;
				int rb = input.read();
				while (true) {
					if (rb < 0) {
						return rb;
					}
					if (rb >= '0' && rb <= '9') {
						size = (size << 4) | (rb - '0');
					} else if (rb >= 'a' && rb <= 'f') {
						size = (size << 4) | (rb - 'a' + 10);
					} else if (rb >= 'A' && rb <= 'F') {
						size = (size << 4) | (rb - 'A' + 10);
					} else {
						rb = input.read();
						break;
					}
					rb = input.read();
				}
				if (rb == '\r') {
					rb = input.read();
				}
				if (rb != '\n') {
					throw createIllegalStateIOException();
				}
				if (size < 0) {
					throw createIllegalStateIOException();
				}
				bytesLeft = size;
				if (size == 0) {
					finished = true;
					return -1;
				}
			}
			len = len > bytesLeft ? (int) bytesLeft : len;
			int count = input.read(b, off, len);
			if (count >= 0) {
				bytesLeft -= count;
				if (bytesLeft < 0) {
					throw createIllegalStateIOException();
				} else if (bytesLeft == 0) {
					bytesLeft = -1;
					int rb = input.read();
					if (rb == '\r') {
						rb = input.read();
					}
					if (rb != '\n') {
						throw createIllegalStateIOException();
					}
				}
			}
			return count;
		}
	}

	private static class FixedInputStream extends InputStream {
		private final InputStream input;
		private final byte[] singleByte = new byte[1];
		private long bytesLeft;

		public FixedInputStream(InputStream input, long length) {
			this.input = input;
			bytesLeft = length;
		}

		@Override
		public int read() throws IOException {
			int count = read(singleByte, 0, 1);
			return count == 1 ? singleByte[0] & 0xff : -1;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			if (Thread.interrupted()) {
				throw new InterruptedIOException();
			}
			len = len > bytesLeft ? (int) bytesLeft : len;
			int count = input.read(b, off, len);
			if (count > 0) {
				bytesLeft -= count;
			}
			return count;
		}
	}

	private static IOException createIllegalStateIOException() {
		return new IOException("Illegal state");
	}

	private static class ProtocolException extends IOException {
		public ProtocolException(String message) {
			super(message);
		}
	}

	private static class Slice {
		// Internal limit, assume we won't work with large files
		public static final int LIMIT = 10000000;

		private final byte[] bytes;
		private final int offset;
		private final int length;

		private int position = 0;

		public Slice(byte[] bytes, int offset, int length) throws ProtocolException {
			if (bytes == null && (offset > 0 || length > 0) ||
					bytes != null && bytes.length < offset + length) {
				throw new ProtocolException("Range validation failed: " + (bytes != null ? bytes.length : "null") +
						"+" + offset + "/" + length);
			}
			this.bytes = bytes;
			this.offset = offset;
			this.length = length;
		}

		public void copy(int moveTo, Slice target, int count) throws ProtocolException {
			count = Math.max(0, count);
			if (moveTo >= 0) {
				if (moveTo > length) {
					throw new ProtocolException("Position validation failed: " + moveTo + "/" + length);
				}
				position = moveTo;
			}
			if (position + count > length) {
				throw new ProtocolException("Source length validation failed: " +
						position + "+" + count + "/" + length);
			}
			if (target.position + count > target.length) {
				throw new ProtocolException("Target length validation failed: " +
						position + "+" + count + "/" + length);
			}
			try {
				if (bytes != target.bytes) {
					System.arraycopy(bytes, offset + position, target.bytes, target.offset + target.position, count);
				} else {
					// Different slices may refer to the same array, System.arraycopy will misbehave
					for (int i = 0; i < count; i++) {
						target.bytes[target.offset + target.position + i] = bytes[offset + position + i];
					}
				}
			} catch (IndexOutOfBoundsException e) {
				throw new ProtocolException(e.getMessage());
			}
			position += count;
			target.position += count;
		}
	}

	private interface HttpWriter {
		void write(OutputStream output) throws IOException;
	}

	private interface HttpReader<T> {
		T read(Map<String, String> headers, InputStream input) throws IOException;
	}

	private static <T> T httpRequest(InetSocket socket, String method, String file,
			HttpWriter writer, HttpReader<T> reader) throws InterruptedException, HttpException, IOException {
		try {
			BufferedOutputStream output = new BufferedOutputStream(socket.getOutputStream());
			BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
			OutputStreamWriter outputWriter = new OutputStreamWriter(output);
			outputWriter.write(method + " " + file + " HTTP/1.1\r\n");
			outputWriter.write("Host: github.com\r\n");
			outputWriter.write("User-Agent: SVN/1.14.0 (x86_64-pc-linux-gnu) serf/1.3.9\r\n");
			outputWriter.write("Content-Type: text/xml\r\n");
			outputWriter.write("Accept-Encoding: identity\r\n");
			outputWriter.write("Transfer-Encoding: chunked\r\n");
			outputWriter.write("Connection: keep-alive\r\n");
			outputWriter.write("\r\n");
			outputWriter.flush();
			try (ChunkedOutputStream chunked = new ChunkedOutputStream(output)) {
				writer.write(chunked);
			}

			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			String statusLine = null;
			HashMap<String, String> headers = new HashMap<>();
			while (true) {
				int b = input.read();
				if (b < 0) {
					break;
				} else if (b == '\n') {
					@SuppressWarnings("CharsetObjectCanBeUsed")
					String line = new String(buffer.toByteArray(), "ISO-8859-1");
					buffer.reset();
					if (line.isEmpty()) {
						// HTTP headers finished
						break;
					}
					if (statusLine == null) {
						statusLine = line;
					} else {
						int index = line.indexOf(':');
						if (index >= 0) {
							int start = index < line.length() - 1 &&
									line.charAt(index + 1) == ' ' ? index + 2 : index + 1;
							headers.put(line.substring(0, index).toLowerCase(Locale.US), line.substring(start));
						}
					}
				} else if (b != '\r') {
					buffer.write(b);
				}
			}
			if (statusLine != null) {
				int index1 = statusLine.indexOf(' ');
				int index2 = statusLine.indexOf(' ', index1 + 1);
				if (index2 > index1 && statusLine.startsWith("HTTP/")) {
					int code;
					try {
						code = Integer.parseInt(statusLine.substring(index1 + 1, index2));
					} catch (NumberFormatException e) {
						throw new IOException(e);
					}
					String message = statusLine.substring(index2 + 1);
					if (code < 200 || code >= 300) {
						throw new HttpException(code, message);
					}
				} else {
					throw new IOException("Invalid status line");
				}
			} else {
				throw new IOException("Missing status line");
			}
			String encoding = headers.get("content-encoding");
			if (encoding != null && !"identity".equals(encoding)) {
				throw new IOException("Invalid encoding");
			}
			boolean chunked = "chunked".equals(headers.get("transfer-encoding"));
			long contentLength = -1;
			String contentLengthString = headers.get("content-length");
			if (contentLengthString != null) {
				try {
					contentLength = Long.parseLong(contentLengthString);
				} catch (NumberFormatException e) {
					// Ignore
				}
			}
			InputStream responseInput;
			if (chunked) {
				responseInput = new ChunkedInputStream(input);
			} else if (contentLength >= 0) {
				responseInput = new FixedInputStream(input, contentLength);
			} else {
				throw new IOException("Invalid response");
			}
			return reader.read(headers, responseInput);
		} catch (InterruptedIOException e) {
			throw new InterruptedException();
		}
	}

	// Lame protocol implementation with reduced amount of code and requests sent.
	// This implementation will work on GitHub only.
	public static HashMap<String, byte[]> listGithubFiles(Uri githubUri, String path)
			throws InterruptedException, HttpException, IOException {
		// In general, "!svn/vcc/default" path should be extracted from headers or using
		// "version-controlled-configuration" PROPFIND request.
		Uri svnUri = githubUri.buildUpon().appendEncodedPath("!svn/vcc/default").build();
		Uri.Builder fileUriBuilder = githubUri.buildUpon();
		fileUriBuilder.appendPath("trunk");
		for (String segment : path.split("/")) {
			fileUriBuilder.appendPath(segment);
		}
		Uri fileUri = fileUriBuilder.build();
		URL svnUrl = HttpClient.encodeUri(svnUri);
		URL fileUrl = HttpClient.encodeUri(fileUri);
		boolean verifyCertificate = Preferences.isVerifyCertificate();
		int overridePort = githubUri.getPort();
		int port;
		boolean secure;
		if ("http".equals(svnUri.getScheme())) {
			port = overridePort > 0 ? overridePort : 80;
			secure = false;
		} else if ("https".equals(svnUri.getScheme())) {
			port = overridePort > 0 ? overridePort : 443;
			secure = true;
		} else {
			throw new IllegalArgumentException();
		}
		try (InetSocket socket = new InetSocket.Builder(svnUrl.getHost(), port, true)
				.setSecure(secure, verifyCertificate).open()) {
			return httpRequest(socket, "REPORT", svnUrl.getFile(), output -> {
				// GitHub allows requests without specifying "svn:target-revision" and "rev" in "svn:entry",
				// but this is generally not the case.
				XmlSerializer serializer = Xml.newSerializer();
				serializer.setOutput(output, "UTF-8");
				serializer.setPrefix("S", "svn:");
				serializer.startDocument("utf-8", false);
				serializer.startTag("svn:", "update-report");
				serializer.attribute(null, "send-all", "true");
				serializer.startTag("svn:", "src-path")
						.text(fileUrl.getFile())
						.endTag("svn:", "src-path");
				serializer.startTag("svn:", "depth")
						.text("immediates")
						.endTag("svn:", "depth");
				serializer.startTag("svn:", "entry")
						.attribute(null, "depth", "immediates")
						.endTag("svn:", "entry");
				serializer.endTag("svn:", "update-report");
				serializer.endDocument();
				serializer.flush();
			}, (headers, input) -> {
				try {
					XmlPullParser parser = Xml.newPullParser();
					parser.setInput(input, "UTF-8");
					parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
					int token;
					StringBuilder text = new StringBuilder();
					HashMap<String, byte[]> files = new HashMap<>();
					while ((token = parser.nextToken()) != XmlPullParser.END_DOCUMENT) {
						if (token == XmlPullParser.START_TAG) {
							if ("svn:".equals(parser.getNamespace())) {
								String name = parser.getName();
								if ("add-directory".equals(name)) {
									String fileName = parser.getAttributeValue(null, "name");
									files.put(fileName, null);
								} else if ("add-file".equals(name)) {
									String fileName = parser.getAttributeValue(null, "name");
									while ((token = parser.nextToken()) != XmlPullParser.END_DOCUMENT) {
										if (token == XmlPullParser.START_TAG &&
												"svn:".equals(parser.getNamespace()) &&
												"txdelta".equals(parser.getName())) {
											while ((token = parser.nextToken()) != XmlPullParser.END_TAG) {
												if (token == XmlPullParser.TEXT) {
													text.append(parser.getText());
												}
											}
											byte[] svnDiff;
											try {
												svnDiff = Base64.decode(text.toString(), 0);
											} catch (RuntimeException e) {
												throw new IOException(e);
											}
											files.put(fileName, svnDiff);
											text.setLength(0);
										}
										if (token == XmlPullParser.END_TAG &&
												"svn:".equals(parser.getNamespace()) &&
												"add-file".equals(parser.getName())) {
											break;
										}
									}
								}
							}
						}
					}
					return files;
				} catch (XmlPullParserException e) {
					throw new IOException(e);
				}
			});
		}
	}

	private static byte[] read(InputStream input) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		IOUtils.copyStream(input, output);
		return output.toByteArray();
	}

	private static int checkLength(int length, int maxLength) throws IOException {
		if (length > maxLength || length < 0) {
			throw new ProtocolException("Invalid length: " + length);
		}
		return length;
	}

	// https://svn.apache.org/repos/asf/subversion/trunk/notes/svndiff
	public static byte[] applyDiff(byte[] source, byte[] svnDiff) throws IllegalArgumentException {
		if (svnDiff != null) {
			ByteArrayInputStream input = new ByteArrayInputStream(svnDiff);
			try {
				if (input.read() != 'S' || input.read() != 'V' || input.read() != 'N') {
					throw new ProtocolException("Invalid header");
				}
				int version = input.read();
				if (version < 0 || version > 2) {
					throw new ProtocolException("Unsupported version: " + version);
				}
				while (input.available() > 0) {
					source = processWindow(input, source, version);
				}
				return source;
			} catch (IOException e) {
				throw new IllegalArgumentException("Decode failed", e);
			}
		} else {
			return null;
		}
	}

	private static int readDiffInt(InputStream input) throws IOException {
		int result = 0;
		while (true) {
			int b = input.read();
			if (b < 0) {
				throw new ProtocolException("Unexpected end of array");
			}
			if (b < 0x80) {
				result = (result << 7) | b;
				if (result < 0) {
					throw new ProtocolException("Invalid result");
				}
				return result;
			} else {
				result = (result << 7) | (b & 0x7f);
			}
		}
	}

	private static byte[] decodeDiffBytes(InputStream input, int length, int version) throws IOException {
		byte[] original = new byte[length];
		int count = input.read(original);
		if (count != length) {
			throw new ProtocolException("Failed to read bytes: " + count + "/" + length);
		}
		if (version == 0) {
			return original;
		} else {
			ByteArrayInputStream bytesInput = new ByteArrayInputStream(original);
			int realLength = readDiffInt(bytesInput);
			int intLength = original.length - bytesInput.available();
			if (realLength == length - intLength) {
				return read(bytesInput);
			} else {
				if (version == 2) {
					throw new ProtocolException("LZ4 is not supported");
				} else {
					return read(new InflaterInputStream(bytesInput));
				}
			}
		}
	}

	private static byte[] processWindow(ByteArrayInputStream input, byte[] source, int version) throws IOException {
		int sourceViewOffset = checkLength(readDiffInt(input), Slice.LIMIT);
		int sourceViewLength = checkLength(readDiffInt(input), Slice.LIMIT);
		int targetViewLength = checkLength(readDiffInt(input), Slice.LIMIT);
		int instructionsRawLength = checkLength(readDiffInt(input), Slice.LIMIT);
		int newDataRawLength = checkLength(readDiffInt(input), Slice.LIMIT);
		byte[] instructions = decodeDiffBytes(input, instructionsRawLength, version);
		byte[] newData = decodeDiffBytes(input, newDataRawLength, version);
		Slice sourceView = new Slice(source, sourceViewOffset, sourceViewLength);
		byte[] target = new byte[targetViewLength];
		Slice targetViewInput = new Slice(target, 0, targetViewLength);
		Slice targetViewOutput = new Slice(target, 0, targetViewLength);
		Slice newDataView = new Slice(newData, 0, newData.length);
		ByteArrayInputStream instructionInput = new ByteArrayInputStream(instructions);
		while (instructionInput.available() > 0) {
			processDiffInstruction(instructionInput, sourceView, targetViewInput, targetViewOutput, newDataView);
		}
		// All necessary sanity checks were done in Slice constructors
		int resultLength = (source != null ? source.length : 0) - sourceViewLength + targetViewLength;
		byte[] result = new byte[resultLength];
		if (source != null && sourceViewOffset > 0) {
			System.arraycopy(source, 0, result, 0, sourceViewOffset);
		}
		System.arraycopy(target, 0, result, sourceViewOffset, targetViewLength);
		int lengthWithoutTail = sourceViewOffset + sourceViewLength;
		if (source != null && sourceView.length > lengthWithoutTail) {
			System.arraycopy(source, lengthWithoutTail, result, sourceViewOffset + targetViewLength,
					source.length - lengthWithoutTail);
		}
		return result;
	}

	private static void processDiffInstruction(InputStream input, Slice sourceView,
			Slice targetViewInput, Slice targetViewOutput, Slice newDataView) throws IOException {
		int b = input.read();
		if (b < 0) {
			throw new ProtocolException("Unexpected end of array");
		}
		int opcode = b >> 6;
		if (opcode < 0 || opcode > 2) {
			throw new ProtocolException("Invalid opcode: " + opcode);
		}
		int length = b & 0x3f;
		if (length == 0) {
			length = checkLength(readDiffInt(input), Slice.LIMIT);
		}
		if (opcode == 0) {
			// Copy from source view
			int offset = checkLength(readDiffInt(input), Slice.LIMIT);
			sourceView.copy(offset, targetViewOutput, length);
		} else if (opcode == 1) {
			// Copy from target view
			int offset = checkLength(readDiffInt(input), Slice.LIMIT);
			targetViewInput.copy(offset, targetViewOutput, length);
		} else if (opcode == 2) {
			// Copy from new data view
			newDataView.copy(-1, targetViewOutput, length);
		} else {
			throw new IllegalStateException();
		}
	}
}
