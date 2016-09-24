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

public class CachingInputStream extends InputStream
{
	enum Whence {START, RELATIVE, END}

	private final Object mDataBufferLock = new Object();
	private byte[] mDataBuffer = new byte[1024];
	private boolean mDataBufferEnd = false;
	private int mDataBufferCount = 0;
	private int mDataBufferIndex = 0;

	private boolean mAllowReadBeyondBuffer = true;
	private boolean mClosed = false;

	private final byte[] mOneByteBuffer = new byte[1];

	@Override
	public int read() throws IOException
	{
		synchronized (mDataBufferLock)
		{
			int count = read(mOneByteBuffer, 0, 1);
			return count == 1 ? mOneByteBuffer[0] : -1;
		}
	}

	@Override
	public int read(byte[] buffer) throws IOException
	{
		return read(buffer, 0, buffer.length);
	}

	@Override
	public int read(byte[] buffer, int byteOffset, int byteCount) throws IOException
	{
		synchronized (mDataBufferLock)
		{
			while (mDataBufferCount - mDataBufferIndex < byteCount && !mDataBufferEnd && !mClosed)
			{
				if (!mAllowReadBeyondBuffer) return 0;
				try
				{
					mDataBufferLock.wait();
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
					throw new InterruptedIOException("Thread was interrupted during IO operation");
				}
			}
			if (mDataBufferEnd && mDataBufferIndex >= mDataBufferCount || mClosed) return -1;
			byteCount = Math.min(mDataBufferCount - mDataBufferIndex, byteCount);
			if (byteCount < 0) byteCount = 0; else if (byteCount > 0)
			{
				System.arraycopy(mDataBuffer, mDataBufferIndex, buffer, byteOffset, byteCount);
				mDataBufferIndex += byteCount;
			}
			return byteCount;
		}
	}

	@Override
	public void close() throws IOException
	{
		synchronized (mDataBufferLock)
		{
			if (!mDataBufferEnd)
			{
				mClosed = true;
				mDataBufferEnd = true;
				mDataBufferLock.notifyAll();
			}
		}
	}

	public void setAllowReadBeyondBuffer(boolean allow)
	{
		synchronized (mDataBufferLock)
		{
			mAllowReadBeyondBuffer = allow;
			mDataBufferLock.notifyAll();
		}
	}

	public int seek(int position, Whence whence)
	{
		synchronized (mDataBufferLock)
		{
			int index = mDataBufferIndex;
			switch (whence)
			{
				case START:
				{
					index = Math.max(position, 0);
					break;
				}
				case RELATIVE:
				{
					index = Math.max(index + position, 0);
					break;
				}
				case END:
				{
					if (mDataBufferEnd) index = mDataBufferCount + position; else return -1;
					break;
				}
			}
			if (!mAllowReadBeyondBuffer && index >= mDataBufferCount) return -1;
			mDataBufferIndex = index;
			return index;
		}
	}

	public int getPosition()
	{
		synchronized (mDataBufferLock)
		{
			return mDataBufferIndex;
		}
	}

	public int getTotalCount()
	{
		synchronized (mDataBufferLock)
		{
			return mDataBufferEnd ? mDataBufferCount : -1;
		}
	}

	private final OutputStream mOutputStream = new OutputStream()
	{
		@Override
		public void write(int oneByte) throws IOException
		{
			synchronized (mDataBufferLock)
			{
				mOneByteBuffer[0] = (byte) oneByte;
				write(mOneByteBuffer, 0, 1);
			}
		}

		@Override
		public void write(byte[] buffer) throws IOException
		{
			write(buffer, 0, buffer.length);
		}

		@Override
		public void write(byte[] buffer, int offset, int count) throws IOException
		{
			synchronized (mDataBufferLock)
			{
				if (mDataBufferEnd || mClosed) throw new IOException("Stream is closed");
				if (count > 0)
				{
					int newCount = mDataBufferCount + count;
					if (newCount > mDataBuffer.length)
					{
						mDataBuffer = Arrays.copyOf(mDataBuffer, Math.max(mDataBuffer.length * 2, newCount));
					}
					System.arraycopy(buffer, offset, mDataBuffer, mDataBufferCount, count);
					mDataBufferCount = newCount;
					mDataBufferLock.notifyAll();
				}
			}
		}

		@Override
		public void close() throws IOException
		{
			synchronized (mDataBufferLock)
			{
				if (!mDataBufferEnd && !mClosed)
				{
					mDataBufferEnd = true;
					mDataBufferLock.notifyAll();
				}
			}
		}
	};

	public OutputStream getOutputStream()
	{
		return mOutputStream;
	}

	public void writeTo(OutputStream output) throws IOException
	{
		output.write(mDataBuffer, 0, mDataBufferCount);
	}
}