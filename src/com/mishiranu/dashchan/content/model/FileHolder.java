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

package com.mishiranu.dashchan.content.model;

import java.io.Closeable;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import chan.util.StringUtils;

import com.mishiranu.dashchan.media.WebViewBitmapDecoder;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.Log;

public abstract class FileHolder implements Serializable
{
	private static final long serialVersionUID = 1L;
	
	public abstract String getName();
	public abstract int getSize();
	public abstract InputStream openInputStream() throws IOException;
	public abstract Descriptor openDescriptor() throws IOException;
	public abstract Uri toUri();
	
	public interface Descriptor extends Closeable 
	{
		public FileDescriptor getFileDescriptor() throws IOException;
	}
	
	public String getExtension()
	{
		return StringUtils.getFileExtension(getName());
	}
	
	private static class ImageData
	{
		public boolean image = false;
		public boolean svg = false;
		public int width = -1;
		public int height = -1;
	}
	
	private transient ImageData mImageData;
	
	private static final XmlPullParserFactory PARSER_FACTORY;
	
	static
	{
		try
		{
			PARSER_FACTORY = XmlPullParserFactory.newInstance();
		}
		catch (XmlPullParserException e)
		{
			throw new RuntimeException(e);
		}
	}
	
	private ImageData getImageData()
	{
		synchronized (this)
		{
			if (mImageData == null)
			{
				mImageData = new ImageData();
				BitmapFactory.Options options = new BitmapFactory.Options();
				options.inJustDecodeBounds = true;
				readBitmapSimple(options);
				if (options.outWidth > 0 && options.outHeight > 0)
				{
					mImageData.image = true;
					mImageData.width = options.outWidth;
					mImageData.height = options.outHeight;
				}
				else
				{
					InputStream input = null;
					try
					{
						input = openInputStream();
						XmlPullParser parser = PARSER_FACTORY.newPullParser();
						parser.setInput(input, null);
						int type;
						OUTER: while ((type = parser.getEventType()) != XmlPullParser.END_DOCUMENT)
						{
							switch (type)
							{
								case XmlPullParser.START_TAG:
								{
									if ("svg".equals(parser.getName()))
									{
										int width, height;
										try
										{
											width = Integer.parseInt(parser.getAttributeValue(null, "width"));
											height = Integer.parseInt(parser.getAttributeValue(null, "height"));
										}
										catch (NumberFormatException | NullPointerException e)
										{
											width = -1;
											height = -1;
										}
										mImageData.image = true;
										mImageData.width = width;
										mImageData.height = height;
										mImageData.svg = true;
										break OUTER;
									}
									break;
								}
							}
							parser.next();
						}
					}
					catch (IOException | XmlPullParserException e)
					{
						
					}
					finally
					{
						IOUtils.close(input);
					}
				}
			}
			return mImageData;
		}
	}
	
	public boolean isImage()
	{
		return getImageData().image;
	}
	
	public boolean isSvg()
	{
		return getImageData().svg;
	}
	
	public int getImageWidth()
	{
		return getImageData().width;
	}
	
	public int getImageHeight()
	{
		return getImageData().height;
	}
	
	public Bitmap readImageBitmap()
	{
		return readImageBitmap(Integer.MAX_VALUE, false, false);
	}
	
	public Bitmap readImageBitmap(int maxSize, boolean mayUseRegionDecoder, boolean mayUseWebViewDecoder)
	{
		ImageData imageData = getImageData();
		if (imageData.image)
		{
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inSampleSize = calculateInSampleSize(maxSize, imageData.width, imageData.height);
			options.inDither = true;
			return readBitmapInternal(options, mayUseRegionDecoder, mayUseWebViewDecoder);
		}
		return null;
	}
	
	private Bitmap readBitmapInternal(BitmapFactory.Options options, boolean mayUseRegionDecoder,
			boolean mayUseWebViewDecoder)
	{
		ImageData imageData = getImageData();
		if (!imageData.image) return null;
		if (!imageData.svg)
		{
			Bitmap bitmap = readBitmapSimple(options);
			if (bitmap != null) return bitmap;
			if (mayUseRegionDecoder)
			{
				InputStream input = null;
				BitmapRegionDecoder decoder = null;
				try
				{
					input = openInputStream();
					decoder = BitmapRegionDecoder.newInstance(input, false);
					return decoder.decodeRegion(new Rect(0, 0, decoder.getWidth(), decoder.getHeight()), options);
				}
				catch (IOException e)
				{
					Log.persistent().stack(e);
				}
				finally
				{
					IOUtils.close(input);
					if (decoder != null) decoder.recycle();
				}
			}
		}
		if (mayUseWebViewDecoder) return WebViewBitmapDecoder.loadBitmap(this);
		return null;
	}
	
	private Bitmap readBitmapSimple(BitmapFactory.Options options)
	{
		InputStream input = null;
		try
		{
			input = openInputStream();
			return BitmapFactory.decodeStream(input, null, options);
		}
		catch (IOException e)
		{
			Log.persistent().stack(e);
			return null;
		}
		finally
		{
			IOUtils.close(input);
		}
	}
	
	public static int calculateInSampleSize(int max, int width, int height)
	{
		if (width > max || height > max)
		{
			int size = Math.max(width, height);
			int scale = (size + max - 1) / max;
			int inSampleSize = 1;
			while (scale > inSampleSize) inSampleSize *= 2;
			return inSampleSize;
		}
		return 1;
	}
	
	private static class FileFileHolder extends FileHolder
	{
		private static final long serialVersionUID = 1L;
		
		private final File mFile;
		
		public FileFileHolder(File file)
		{
			mFile = file;
		}
		
		@Override
		public String getName()
		{
			return mFile.getName();
		}
		
		@Override
		public int getSize()
		{
			return (int) mFile.length();
		}
		
		@Override
		public FileInputStream openInputStream() throws IOException
		{
			return new FileInputStream(mFile);
		}
		
		@Override
		public Descriptor openDescriptor() throws IOException
		{
			return new FileFileHolderDescriptor(openInputStream());
		}
		
		@Override
		public Uri toUri()
		{
			return Uri.fromFile(mFile);
		}
		
		@Override
		public boolean equals(Object o)
		{
			if (o == this) return true;
			if (o instanceof FileFileHolder) return ((FileFileHolder) o).mFile.equals(mFile);
			return false;
		}
		
		@Override
		public int hashCode()
		{
			return mFile.hashCode();
		}
		
		private static class FileFileHolderDescriptor implements Descriptor
		{
			private final FileInputStream mFileInputStream;
			
			public FileFileHolderDescriptor(FileInputStream fileInputStream)
			{
				mFileInputStream = fileInputStream;
			}
			
			@Override
			public FileDescriptor getFileDescriptor() throws IOException
			{
				return mFileInputStream.getFD();
			}
			
			@Override
			public void close() throws IOException
			{
				mFileInputStream.close();
			}
		}
	}
	
	private static class ContentFileHolder extends FileHolder
	{
		private static final long serialVersionUID = 1L;
		
		private final Context mContext;
		private final String mUriString;
		private final String mName;
		private final int mSize;
		
		public ContentFileHolder(Context context, Uri uri, String name, int size)
		{
			mContext = context.getApplicationContext();
			mUriString = uri.toString();
			mName = name;
			try
			{
				int newSize = 0;
				byte[] buffer = new byte[8192];
				InputStream input = openInputStream();
				int count;
				while ((count = input.read(buffer)) >= 0) newSize += count;
				size = newSize;
			}
			catch (IOException e)
			{
				
			}
			mSize = size;
		}
		
		@Override
		public String getName()
		{
			return mName;
		}
		
		@Override
		public int getSize()
		{
			return mSize;
		}
		
		@Override
		public InputStream openInputStream() throws IOException
		{
			try
			{
				return mContext.getContentResolver().openInputStream(toUri());
			}
			catch (SecurityException e)
			{
				throw new IOException(e);
			}
		}
		
		@Override
		public Descriptor openDescriptor() throws IOException
		{
			try
			{
				return new ContentFileHolderDescriptor(mContext.getContentResolver().openFileDescriptor(toUri(), "r"));
			}
			catch (SecurityException e)
			{
				throw new IOException(e);
			}
		}
		
		@Override
		public Uri toUri()
		{
			return Uri.parse(mUriString);
		}
		
		@Override
		public boolean equals(Object o)
		{
			if (o == this) return true;
			if (o instanceof ContentFileHolder) return ((ContentFileHolder) o).mUriString.equals(mUriString);
			return false;
		}
		
		@Override
		public int hashCode()
		{
			return mUriString.hashCode();
		}
		
		private static class ContentFileHolderDescriptor implements Descriptor
		{
			private final ParcelFileDescriptor mParcelFileDescriptor;
			
			public ContentFileHolderDescriptor(ParcelFileDescriptor parcelFileDescriptor)
			{
				mParcelFileDescriptor = parcelFileDescriptor;
			}
			
			@Override
			public FileDescriptor getFileDescriptor()
			{
				return mParcelFileDescriptor.getFileDescriptor();
			}
			
			@Override
			public void close() throws IOException
			{
				mParcelFileDescriptor.close();
			}
		}
	}
	
	private static long sFileNameStart = System.currentTimeMillis();
	
	public static FileHolder obtain(File file)
	{
		return new FileFileHolder(file);
	}
	
	public static FileHolder obtain(Context context, Uri uri)
	{
		String scheme = uri.getScheme();
		if ("file".equals(scheme))
		{
			String path = uri.getPath();
			return new FileFileHolder(new File(path));
		}
		else if ("content".equals(scheme))
		{
			Cursor cursor = null;
			try
			{
				cursor = context.getContentResolver().query(uri, null, null, null, null);
				if (cursor != null && cursor.moveToFirst())
				{
					int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
					int sizeIndex = Math.max(cursor.getColumnIndex(OpenableColumns.SIZE), 0);
					if (nameIndex >= 0 && sizeIndex >= 0)
					{
						String name = cursor.getString(nameIndex);
						int size = cursor.getInt(sizeIndex);
						if (StringUtils.isEmpty(name))
						{
							int dataIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA);
							if (dataIndex >= 0)
							{
								String data = cursor.getString(dataIndex);
								if (data != null) name = new File(data).getName();
							}
						}
						if (StringUtils.isEmpty(name))
						{
							int mimeTypeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE);
							if (mimeTypeIndex >= 0)
							{
								String mimeType = cursor.getString(mimeTypeIndex);
								if (mimeType != null)
								{
									String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
									name = ++sFileNameStart + "." + extension;
								}
							}
						}
						if (!StringUtils.isEmpty(name)) return new ContentFileHolder(context, uri, name, size);
					}
				}
			}
			catch (SecurityException e)
			{
				return null;
			}
			finally
			{
				if (cursor != null) cursor.close();
			}
		}
		return null;
	}
}