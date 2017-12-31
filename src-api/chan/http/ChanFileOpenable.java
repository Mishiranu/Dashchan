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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Random;

import chan.util.StringUtils;

import com.mishiranu.dashchan.content.model.FileHolder;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.IOUtils;

public class ChanFileOpenable implements MultipartEntity.Openable {
	private static final Random RANDOM = new Random(System.currentTimeMillis());

	private final FileHolder fileHolder;
	private final String fileName;
	private final String mimeType;
	private final int imageWidth;
	private final int imageHeight;

	private final int randomBytes;
	private final ArrayList<GraphicsUtils.SkipRange> skipRanges;
	private final byte[] decodedBytes;
	private final long realSize;

	public ChanFileOpenable(FileHolder fileHolder, String fileName, boolean uniqueHash, boolean removeMetadata,
			boolean removeFileName, GraphicsUtils.Reencoding reencoding) {
		this.fileHolder = fileHolder;
		if (fileName == null) {
			fileName = fileHolder.getName();
		}
		if (removeFileName) {
			String extension = StringUtils.getFileExtension(fileName);
			long time = System.currentTimeMillis();
			if (extension != null && extension.matches("[a-z0-9]{1,10}")) {
				fileName = time + "." + extension;
			} else {
				switch (fileHolder.getImageType()) {
					case IMAGE_JPEG: {
						fileName = time + ".jpeg";
						break;
					}
					case IMAGE_PNG: {
						fileName = time + ".png";
						break;
					}
					case IMAGE_GIF: {
						fileName = time + ".gif";
						break;
					}
					case IMAGE_WEBP: {
						fileName = time + ".webp";
						break;
					}
					case IMAGE_BMP: {
						fileName = time + ".bmp";
						break;
					}
					case IMAGE_SVG: {
						fileName = time + ".svg";
						break;
					}
					default: {
						fileName = Long.toString(time);
						break;
					}
				}
			}
		}
		randomBytes = uniqueHash ? 6 : 0;
		GraphicsUtils.TransformationData transformationData = GraphicsUtils.transformImageForPosting(fileHolder,
				fileName, removeMetadata, reencoding);
		if (transformationData != null) {
			skipRanges = transformationData.skipRanges;
			decodedBytes = transformationData.decodedBytes;
			if (transformationData.newFileName != null) {
				fileName = transformationData.newFileName;
			}
			imageWidth = transformationData.newWidth > 0 ? transformationData.newWidth : fileHolder.getImageWidth();
			imageHeight = transformationData.newHeight > 0 ? transformationData.newHeight : fileHolder.getImageHeight();
		} else {
			skipRanges = null;
			decodedBytes = null;
			imageWidth = fileHolder.getImageWidth();
			imageHeight = fileHolder.getImageHeight();
		}
		this.fileName = fileName;
		mimeType = MultipartEntity.obtainMimeType(fileName);
		realSize = decodedBytes != null ? decodedBytes.length : fileHolder.getSize();
	}

	@Override
	public String getFileName() {
		return fileName;
	}

	@Override
	public String getMimeType() {
		return mimeType;
	}

	public int getImageWidth() {
		return imageWidth;
	}

	public int getImageHeight() {
		return imageHeight;
	}

	@Override
	public InputStream openInputStream() throws IOException {
		return new ChanFileInputStream();
	}

	@Override
	public long getSize() {
		long totalSkip = 0L;
		if (skipRanges != null) {
			for (GraphicsUtils.SkipRange skipRange : skipRanges) {
				totalSkip += skipRange.count;
			}
		}
		return realSize + randomBytes - totalSkip;
	}

	private class ChanFileInputStream extends InputStream {
		private final InputStream inputStream;

		private long position;
		private int skipIndex = 0;
		private int randomBytesLeft;

		public ChanFileInputStream() throws IOException {
			inputStream = decodedBytes != null ? new ByteArrayInputStream(decodedBytes) : fileHolder.openInputStream();
			randomBytesLeft = randomBytes;
		}

		private byte[] tempBuffer;

		private void ensureTempBuffer() {
			if (tempBuffer == null) {
				tempBuffer = new byte[4096];
			}
		}

		@Override
		public int read() throws IOException {
			ensureTempBuffer();
			int result = read(tempBuffer, 0, 1);
			if (result == 1) {
				return tempBuffer[0];
			}
			return -1;
		}

		@Override
		public int read(byte[] buffer) throws IOException {
			return read(buffer, 0, buffer.length);
		}

		@Override
		public int read(byte[] buffer, int byteOffset, int byteCount) throws IOException {
			int totalRead = 0;
			while (byteCount > totalRead) {
				int result = readAndSkip(buffer, byteOffset + totalRead, byteCount - totalRead);
				if (result < 0) {
					if (randomBytesLeft > 0) {
						int randomBytesCount = Math.min(byteCount - totalRead, randomBytesLeft);
						for (int i = 0; i < randomBytesCount; i++) {
							buffer[byteOffset + totalRead + i] = (byte) (RANDOM.nextInt(0x49) + 0x30);
						}
						randomBytesLeft -= randomBytesCount;
						return totalRead + randomBytesCount;
					}
					return totalRead > 0 ? totalRead : -1;
				}
				totalRead += result;
			}
			return totalRead;
		}

		private int readAndSkip(byte[] buffer, int byteOffset, int byteCount) throws IOException {
			GraphicsUtils.SkipRange skipRange = skipRanges != null && skipIndex < skipRanges.size()
					? skipRanges.get(skipIndex) : null;
			long canRead = skipRange != null ? skipRange.start - position : byteCount;
			if (canRead > 0) {
				int count = inputStream.read(buffer, byteOffset, canRead >= byteCount ? byteCount : (int) canRead);
				if (count > 0) {
					position += count;
				}
				return count;
			}
			skipIndex++;
			if (skipRange.count > 0) {
				position += skipRange.count;
				if (!IOUtils.skipExactlyCheck(inputStream, skipRange.count)) {
					throw new IOException();
				}
			}
			return 0;
		}

		@Override
		public void close() throws IOException {
			inputStream.close();
		}
	}
}
