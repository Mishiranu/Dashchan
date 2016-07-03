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

package chan.http;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Random;

import com.mishiranu.dashchan.content.model.FileHolder;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.IOUtils;

public class ChanFileOpenable implements MultipartEntity.Openable
{
	private static final Random RANDOM = new Random(System.currentTimeMillis());
	
	private final FileHolder mFileHolder;
	private final String mFileName;
	private final String mMimeType;
	
	private final int mRandomBytes;
	private final ArrayList<GraphicsUtils.SkipRange> mSkipRanges;
	private final byte[] mDecodedBytes;
	private final long mRealSize;
	
	public ChanFileOpenable(FileHolder fileHolder, boolean uniqueHash, boolean removeMetadata, boolean reencodeImage,
			boolean removeFileName)
	{
		mFileHolder = fileHolder;
		String fileName = MultipartEntity.obtainFileName(fileHolder, removeFileName);
		mRandomBytes = uniqueHash ? 6 : 0;
		GraphicsUtils.TransformationData transformationData = GraphicsUtils.transformImageForPosting(fileHolder,
				fileName, removeMetadata, reencodeImage);
		if (transformationData != null)
		{
			mSkipRanges = transformationData.skipRanges;
			mDecodedBytes = transformationData.decodedBytes;
			if (transformationData.newFileName != null) fileName = transformationData.newFileName;
		}
		else
		{
			mSkipRanges = null;
			mDecodedBytes = null;
		}
		mFileName = fileName;
		mMimeType = MultipartEntity.obtainMimeType(fileName);
		mRealSize = mDecodedBytes != null ? mDecodedBytes.length : fileHolder.getSize();
	}
	
	@Override
	public String getFileName()
	{
		return mFileName;
	}
	
	@Override
	public String getMimeType()
	{
		return mMimeType;
	}
	
	@Override
	public InputStream openInputStream() throws IOException
	{
		return new ChanFileInputStream();
	}
	
	@Override
	public long getSize()
	{
		long totalSkip = 0L;
		if (mSkipRanges != null)
		{
			for (GraphicsUtils.SkipRange skipRange : mSkipRanges) totalSkip += skipRange.count;
		}
		return mRealSize + mRandomBytes - totalSkip;
	}
	
	private class ChanFileInputStream extends InputStream
	{
		private final InputStream mInputStream;
		
		private long mPosition;
		private int mSkipIndex = 0;
		private int mRandomBytesLeft;
		
		public ChanFileInputStream() throws IOException
		{
			mInputStream = mDecodedBytes != null ? new ByteArrayInputStream(mDecodedBytes)
					: mFileHolder.openInputStream();
			mRandomBytesLeft = mRandomBytes;
		}
		
		private byte[] mTempBuffer;
		
		private void ensureTempBuffer()
		{
			if (mTempBuffer == null) mTempBuffer = new byte[4096];
		}
		
		@Override
		public int read() throws IOException
		{
			ensureTempBuffer();
			int result = read(mTempBuffer, 0, 1);
			if (result == 1) return mTempBuffer[0];
			return -1;
		}
		
		@Override
		public int read(byte[] buffer) throws IOException
		{
			return read(buffer, 0, buffer.length);
		}
		
		@Override
		public int read(byte[] buffer, int byteOffset, int byteCount) throws IOException
		{
			int totalRead = 0;
			while (byteCount > totalRead)
			{
				int result = readAndSkip(buffer, byteOffset + totalRead, byteCount - totalRead);
				if (result < 0)
				{
					if (mRandomBytesLeft > 0)
					{
						int randomBytesCount = Math.min(byteCount - result, mRandomBytesLeft);
						for (int i = 0; i < randomBytesCount; i++)
						{
							buffer[byteOffset + totalRead + i] = (byte) (RANDOM.nextInt(0x49) + 0x30);
						}
						mRandomBytesLeft -= randomBytesCount;
						return totalRead + randomBytesCount;
					}
					return totalRead > 0 ? totalRead : -1;
				}
				totalRead += result;
			}
			return totalRead;
		}
		
		private int readAndSkip(byte[] buffer, int byteOffset, int byteCount) throws IOException
		{
			GraphicsUtils.SkipRange skipRange = mSkipRanges != null && mSkipIndex < mSkipRanges.size()
					? mSkipRanges.get(mSkipIndex) : null;
			long canRead = skipRange != null ? skipRange.start - mPosition : byteCount;
			if (canRead > 0)
			{
				int count = mInputStream.read(buffer, byteOffset, canRead >= byteCount ? byteCount : (int) canRead);
				if (count > 0) mPosition += count;
				return count;
			}
			mSkipIndex++;
			if (skipRange.count > 0)
			{
				mPosition += skipRange.count;
				if (!IOUtils.skipExactlyCheck(mInputStream, skipRange.count)) throw new IOException();
			}
			return 0;
		}
		
		@Override
		public void close() throws IOException
		{
			mInputStream.close();
		}
	}
}