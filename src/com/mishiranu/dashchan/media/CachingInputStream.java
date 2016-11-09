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

package com.mishiranu.dashchan.media;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.Arrays;

public class CachingInputStream extends InputStream {
	enum Whence {START, RELATIVE, END}

	private final Object dataBufferLock = new Object();
	private byte[] dataBuffer = new byte[1024];
	private boolean dataBufferEnd = false;
	private int dataBufferCount = 0;
	private int dataBufferIndex = 0;

	private boolean allowReadBeyondBuffer = true;
	private boolean closed = false;

	private final byte[] oneByteBuffer = new byte[1];

	@Override
	public int read() throws IOException {
		synchronized (dataBufferLock) {
			int count = read(oneByteBuffer, 0, 1);
			return count == 1 ? oneByteBuffer[0] : -1;
		}
	}

	@Override
	public int read(byte[] buffer) throws IOException {
		return read(buffer, 0, buffer.length);
	}

	@Override
	public int read(byte[] buffer, int byteOffset, int byteCount) throws IOException {
		synchronized (dataBufferLock) {
			while (dataBufferCount - dataBufferIndex < byteCount && !dataBufferEnd && !closed) {
				if (!allowReadBeyondBuffer) {
					return 0;
				}
				try {
					dataBufferLock.wait();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new InterruptedIOException("Thread was interrupted during IO operation");
				}
			}
			if (dataBufferEnd && dataBufferIndex >= dataBufferCount || closed) {
				return -1;
			}
			byteCount = Math.min(dataBufferCount - dataBufferIndex, byteCount);
			if (byteCount < 0) {
				byteCount = 0;
			} else if (byteCount > 0) {
				System.arraycopy(dataBuffer, dataBufferIndex, buffer, byteOffset, byteCount);
				dataBufferIndex += byteCount;
			}
			return byteCount;
		}
	}

	@Override
	public void close() throws IOException {
		synchronized (dataBufferLock) {
			if (!dataBufferEnd) {
				closed = true;
				dataBufferEnd = true;
				dataBufferLock.notifyAll();
			}
		}
	}

	public void setAllowReadBeyondBuffer(boolean allow) {
		synchronized (dataBufferLock) {
			allowReadBeyondBuffer = allow;
			dataBufferLock.notifyAll();
		}
	}

	public int seek(int position, Whence whence) {
		synchronized (dataBufferLock) {
			int index = dataBufferIndex;
			switch (whence) {
				case START: {
					index = Math.max(position, 0);
					break;
				}
				case RELATIVE: {
					index = Math.max(index + position, 0);
					break;
				}
				case END: {
					if (dataBufferEnd) {
						index = dataBufferCount + position;
					} else {
						return -1;
					}
					break;
				}
			}
			if (!allowReadBeyondBuffer && index >= dataBufferCount) {
				return -1;
			}
			dataBufferIndex = index;
			return index;
		}
	}

	public int getPosition() {
		synchronized (dataBufferLock) {
			return dataBufferIndex;
		}
	}

	public int getTotalCount() {
		synchronized (dataBufferLock) {
			return dataBufferEnd ? dataBufferCount : -1;
		}
	}

	private final OutputStream outputStream = new OutputStream() {
		@Override
		public void write(int oneByte) throws IOException {
			synchronized (dataBufferLock) {
				oneByteBuffer[0] = (byte) oneByte;
				write(oneByteBuffer, 0, 1);
			}
		}

		@Override
		public void write(byte[] buffer) throws IOException {
			write(buffer, 0, buffer.length);
		}

		@Override
		public void write(byte[] buffer, int offset, int count) throws IOException {
			synchronized (dataBufferLock) {
				if (dataBufferEnd || closed) {
					throw new IOException("Stream is closed");
				}
				if (count > 0) {
					int newCount = dataBufferCount + count;
					if (newCount > dataBuffer.length) {
						dataBuffer = Arrays.copyOf(dataBuffer, Math.max(dataBuffer.length * 2, newCount));
					}
					System.arraycopy(buffer, offset, dataBuffer, dataBufferCount, count);
					dataBufferCount = newCount;
					dataBufferLock.notifyAll();
				}
			}
		}

		@Override
		public void close() throws IOException {
			synchronized (dataBufferLock) {
				if (!dataBufferEnd && !closed) {
					dataBufferEnd = true;
					dataBufferLock.notifyAll();
				}
			}
		}
	};

	public OutputStream getOutputStream() {
		return outputStream;
	}

	public void writeTo(OutputStream output) throws IOException {
		output.write(dataBuffer, 0, dataBufferCount);
	}
}