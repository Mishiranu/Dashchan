/*
 * Copyright 2014-2017 Fukurou Mishiranu
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

package chan.http;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Random;

import chan.annotation.Extendable;
import chan.annotation.Public;
import chan.util.StringUtils;

import com.mishiranu.dashchan.content.model.FileHolder;
import com.mishiranu.dashchan.util.MimeTypes;

@Extendable
public class MultipartEntity implements RequestEntity {
	private static final Random RANDOM = new Random(System.currentTimeMillis());

	private final ArrayList<Part> parts = new ArrayList<>();
	private final String boundary;

	private String charsetName = "UTF-8";

	@Public
	public MultipartEntity() {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < 27; i++) {
			builder.append('-');
		}
		for (int i = 0; i < 11; i++) {
			builder.append(RANDOM.nextInt(10));
		}
		boundary = builder.toString();
	}

	@Public
	public MultipartEntity(String... alternation) {
		this();
		for (int i = 0; i < alternation.length; i += 2) {
			add(alternation[i], alternation[i + 1]);
		}
	}

	@Public
	public void setEncoding(String charsetName) {
		this.charsetName = charsetName;
	}

	@Override
	public void add(String name, String value) {
		if (value != null) {
			parts.add(new StringPart(name, value, charsetName));
		}
	}

	@Extendable
	public void add(String name, File file) {
		add(name, new FileHolderOpenable(FileHolder.obtain(file)), null);
	}

	public final void add(String name, Openable openable, OpenableOutputListener listener) {
		if (name == null) {
			throw new NullPointerException("Name is null");
		}
		parts.add(new OpenablePart(name, openable, listener));
	}

	@Override
	public String getContentType() {
		return "multipart/form-data; boundary=" + boundary;
	}

	@Override
	public long getContentLength() {
		try {
			long contentLength = 0L;
			int boundaryLength = boundary.length();
			int dashesLength = BYTES_TWO_DASHES.length;
			int lineLength = BYTES_NEW_LINE.length;
			for (Part part : parts) {
				contentLength += dashesLength + boundaryLength + lineLength;
				contentLength += 39 + part.getName().getBytes(charsetName).length;
				String fileName = part.getFileName();
				if (fileName != null) {
					contentLength += 13 + fileName.getBytes(charsetName).length;
				}
				contentLength += lineLength;
				String contentType = part.getContentType();
				if (contentType != null) {
					contentLength += 14 + contentType.length() + lineLength;
				}
				contentLength += lineLength + part.getContentLength() + lineLength;
			}
			contentLength += dashesLength + boundaryLength + dashesLength + lineLength;
			return contentLength;
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}

	private static final byte[] BYTES_TWO_DASHES = {0x2d, 0x2d};
	private static final byte[] BYTES_NEW_LINE = {0x0d, 0x0a};

	@Override
	public void write(OutputStream output) throws IOException {
		byte[] boundary = this.boundary.getBytes("ISO-8859-1");
		for (Part part : parts) {
			output.write(BYTES_TWO_DASHES);
			output.write(boundary);
			output.write(BYTES_NEW_LINE);
			output.write("Content-Disposition: form-data; name=\"".getBytes());
			output.write(part.getName().getBytes(charsetName));
			output.write('"');
			String fileName = part.getFileName();
			if (fileName != null) {
				output.write("; filename=\"".getBytes());
				output.write(fileName.getBytes(charsetName));
				output.write('"');
			}
			output.write(BYTES_NEW_LINE);
			String contentType = part.getContentType();
			if (contentType != null) {
				output.write(("Content-Type: " + contentType).getBytes("ISO-8859-1"));
				output.write(BYTES_NEW_LINE);
			}
			output.write(BYTES_NEW_LINE);
			part.write(output);
			output.write(BYTES_NEW_LINE);
		}
		output.write(BYTES_TWO_DASHES);
		output.write(boundary);
		output.write(BYTES_TWO_DASHES);
		output.write(BYTES_NEW_LINE);
		output.flush();
	}

	@Override
	public MultipartEntity copy() {
		MultipartEntity entity = new MultipartEntity();
		entity.setEncoding(charsetName);
		entity.parts.addAll(parts);
		return entity;
	}

	private static abstract class Part {
		private final String name;

		public Part(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}

		public abstract String getFileName();
		public abstract String getContentType();
		public abstract long getContentLength();
		public abstract void write(OutputStream output) throws IOException;
	}

	private static class StringPart extends Part {
		private final byte[] bytes;

		public StringPart(String name, String value, String charset) {
			super(name);
			try {
				bytes = value.getBytes(charset);
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public String getFileName() {
			return null;
		}

		@Override
		public String getContentType() {
			return null;
		}

		@Override
		public long getContentLength() {
			return bytes.length;
		}

		@Override
		public void write(OutputStream output) throws IOException {
			output.write(bytes);
		}
	}

	private static class OpenablePart extends Part {
		private final Openable openable;
		private final OpenableOutputListener listener;

		public OpenablePart(String name, Openable openable, OpenableOutputListener listener) {
			super(name);
			this.openable = openable;
			this.listener = listener;
		}

		@Override
		public String getFileName() {
			return openable.getFileName();
		}

		@Override
		public String getContentType() {
			return openable.getMimeType();
		}

		@Override
		public long getContentLength() {
			return openable.getSize();
		}

		@Override
		public void write(OutputStream output) throws IOException {
			InputStream input = openable.openInputStream();
			try {
				long progress = 0L;
				long progressMax = openable.getSize();
				if (listener != null) {
					listener.onOutputProgressChange(openable, 0L, progressMax);
				}
				byte[] buffer = new byte[4096];
				int count;
				while ((count = input.read(buffer)) > 0) {
					output.write(buffer, 0, count);
					progress += count;
					if (listener != null) {
						listener.onOutputProgressChange(openable, progress, progressMax);
					}
				}
			} finally {
				input.close();
			}
		}
	}

	public interface Openable {
		public String getFileName();
		public String getMimeType();
		public InputStream openInputStream() throws IOException;
		public long getSize();
	}

	private static class FileHolderOpenable implements Openable {
		private final FileHolder fileHolder;
		private final String fileName;
		private final String mimeType;

		public FileHolderOpenable(FileHolder fileHolder) {
			this.fileHolder = fileHolder;
			fileName = fileHolder.getName();
			mimeType = obtainMimeType(fileName);
		}

		@Override
		public String getFileName() {
			return fileName;
		}

		@Override
		public String getMimeType() {
			return mimeType;
		}

		@Override
		public InputStream openInputStream() throws IOException {
			return fileHolder.openInputStream();
		}

		@Override
		public long getSize() {
			return fileHolder.getSize();
		}
	}

	static String obtainMimeType(String fileName) {
		return MimeTypes.forExtension(StringUtils.getFileExtension(fileName), "application/octet-stream");
	}

	public interface OpenableOutputListener {
		public void onOutputProgressChange(Openable openable, long progress, long progressMax);
	}
}
