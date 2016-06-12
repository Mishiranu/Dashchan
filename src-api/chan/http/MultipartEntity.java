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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Random;

import android.webkit.MimeTypeMap;

import com.mishiranu.dashchan.content.model.FileHolder;

public class MultipartEntity implements RequestEntity
{
	private static final Random RANDOM = new Random(System.currentTimeMillis());
	
	private final ArrayList<Part> mParts = new ArrayList<>();
	private final String mBoundary;
	
	private String mCharsetName = "UTF-8";
	
	public MultipartEntity()
	{
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < 27; i++) builder.append('-');
		for (int i = 0; i < 11; i++) builder.append(RANDOM.nextInt(10));
		mBoundary = builder.toString();
	}
	
	public MultipartEntity(String... alternation)
	{
		this();
		for (int i = 0; i < alternation.length; i += 2) add(alternation[i], alternation[i + 1]);
	}
	
	public void setEncoding(String charsetName)
	{
		mCharsetName = charsetName;
	}
	
	@Override
	public void add(String name, String value)
	{
		if (value != null) mParts.add(new StringPart(name, value, mCharsetName));
	}
	
	public void add(String name, File file)
	{
		add(name, new FileHolderOpenable(FileHolder.obtain(file)), null);
	}
	
	public void add(String name, Openable openable, OpenableOutputListener listener)
	{
		mParts.add(new OpenablePart(name, openable, listener));
	}
	
	@Override
	public String getContentType()
	{
		return "multipart/form-data; boundary=" + mBoundary;
	}

	@Override
	public long getContentLength()
	{
		try
		{
			long contentLength = 0L;
			int boundaryLength = mBoundary.length();
			int dashesLength = BYTES_TWO_DASHES.length;
			int lineLength = BYTES_NEW_LINE.length;
			for (Part part : mParts)
			{
				contentLength += dashesLength + boundaryLength + lineLength;
				contentLength += 39 + part.getName().getBytes(mCharsetName).length;
				String fileName = part.getFileName();
				if (fileName != null) contentLength += 13 + fileName.getBytes(mCharsetName).length;
				contentLength += lineLength;
				String contentType = part.getContentType();
				if (contentType != null) contentLength += 14 + contentType.length() + lineLength;
				contentLength += lineLength + part.getContentLength() + lineLength;
			}
			contentLength += dashesLength + boundaryLength + dashesLength + lineLength;
			return contentLength;
		}
		catch (UnsupportedEncodingException e)
		{
			throw new RuntimeException(e);
		}
	}
	
	private static final byte[] BYTES_TWO_DASHES = {0x2d, 0x2d};
	private static final byte[] BYTES_NEW_LINE = {0x0d, 0x0a};

	@Override
	public void write(OutputStream output) throws IOException
	{
		byte[] boundary = mBoundary.getBytes("ISO-8859-1");
		for (Part part : mParts)
		{
			output.write(BYTES_TWO_DASHES);
			output.write(boundary);
			output.write(BYTES_NEW_LINE);
			output.write("Content-Disposition: form-data; name=\"".getBytes());
			output.write(part.getName().getBytes(mCharsetName));
			output.write('"');
			String fileName = part.getFileName();
			if (fileName != null)
			{
				output.write("; filename=\"".getBytes());
				output.write(fileName.getBytes(mCharsetName));
				output.write('"');
			}
			output.write(BYTES_NEW_LINE);
			String contentType = part.getContentType();
			if (contentType != null)
			{
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
	public MultipartEntity copy()
	{
		MultipartEntity entity = new MultipartEntity();
		entity.setEncoding(mCharsetName);
		entity.mParts.addAll(mParts);
		return entity;
	}
	
	private static abstract class Part
	{
		private final String mName;
		
		public Part(String name)
		{
			mName = name;
		}
		
		public String getName()
		{
			return mName;
		}
		
		public abstract String getFileName();
		public abstract String getContentType();
		public abstract long getContentLength();
		public abstract void write(OutputStream output) throws IOException;
	}
	
	private static class StringPart extends Part
	{
		private final byte[] mBytes;
		
		public StringPart(String name, String value, String charset)
		{
			super(name);
			try
			{
				mBytes = value.getBytes(charset);
			}
			catch (UnsupportedEncodingException e)
			{
				throw new RuntimeException(e);
			}
		}
		
		@Override
		public String getFileName()
		{
			return null;
		}
		
		@Override
		public String getContentType()
		{
			return null;
		}
		
		@Override
		public long getContentLength()
		{
			return mBytes.length;
		}
		
		@Override
		public void write(OutputStream output) throws IOException
		{
			output.write(mBytes);
		}
	}
	
	private static class OpenablePart extends Part
	{
		private final Openable mOpenable;
		private final OpenableOutputListener mListener;
		
		public OpenablePart(String name, Openable openable, OpenableOutputListener listener)
		{
			super(name);
			mOpenable = openable;
			mListener = listener;
		}

		@Override
		public String getFileName()
		{
			return mOpenable.getFileName();
		}
		
		@Override
		public String getContentType()
		{
			return mOpenable.getMimeType();
		}
		
		@Override
		public long getContentLength()
		{
			return mOpenable.getSize();
		}
		
		@Override
		public void write(OutputStream output) throws IOException
		{
			InputStream input = mOpenable.openInputStream();
			try
			{
				long progress = 0L;
				long progressMax = mOpenable.getSize();
				if (mListener != null) mListener.onOutputProgressChange(mOpenable, 0L, progressMax);
				byte[] buffer = new byte[4096];
				int count;
				while ((count = input.read(buffer)) > 0)
				{
					output.write(buffer, 0, count);
					progress += count;
					if (mListener != null) mListener.onOutputProgressChange(mOpenable, progress, progressMax);
				}
			}
			finally
			{
				input.close();
			}
		}
	}
	
	public static interface Openable
	{
		public String getFileName();
		public String getMimeType();
		public InputStream openInputStream() throws IOException;
		public long getSize();
	}
	
	private static class FileHolderOpenable implements Openable
	{
		private final FileHolder mFileHolder;
		private final String mFileName;
		private final String mMimeType;
		
		public FileHolderOpenable(FileHolder fileHolder)
		{
			mFileHolder = fileHolder;
			mFileName = obtainFileName(mFileHolder, false);
			mMimeType = obtainMimeType(mFileName);
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
			return mFileHolder.openInputStream();
		}
		
		@Override
		public long getSize()
		{
			return mFileHolder.getSize();
		}
	}
	
	static String obtainFileName(FileHolder fileHolder, boolean removeFileName)
	{
		String fileName = fileHolder.getName();
		if (removeFileName)
		{
			String extension = null;
			int index = fileName.lastIndexOf('.');
			if (index >= 0) extension = fileName.substring(index);
			fileName = System.currentTimeMillis() + (extension != null ? extension : "");
		}
		return fileName;
	}
	
	static String obtainMimeType(String fileName)
	{
		String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap
				.getFileExtensionFromUrl(fileName));
		if (mimeType == null) mimeType = "application/octet-stream";
		return mimeType;
	}
	
	public static interface OpenableOutputListener
	{
		public void onOutputProgressChange(Openable openable, long progress, long progressMax);
	}
}