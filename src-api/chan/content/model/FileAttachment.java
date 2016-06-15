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

package chan.content.model;

import java.util.Locale;

import android.net.Uri;

import chan.annotation.Public;
import chan.content.ChanLocator;
import chan.util.StringUtils;

@Public
public final class FileAttachment implements Attachment
{
	private static final long serialVersionUID = 1L;

	private String mFileUriString;
	private String mThumbnailUriString;
	private String mOriginalName;
	
	private int mSize;
	private int mWidth;
	private int mHeight;
	
	private boolean mSpoiler;
	
	@Public
	public FileAttachment()
	{
		
	}
	
	private static String fixRelativeUriString(String uriString)
	{
		int index = uriString.indexOf("//");
		if (index >= 0)
		{
			index = uriString.indexOf('/', index + 2);
			if (index >= 0) index++; else return uriString;
		}
		if (index < 0) index = 0;
		if (uriString.indexOf(':', index) >= 0)
		{
			uriString = uriString.substring(0, index) + uriString.substring(index).replace(":", "%3A");
		}
		return uriString;
	}
	
	public Uri getRelativeFileUri()
	{
		return mFileUriString != null ? Uri.parse(fixRelativeUriString(mFileUriString)) : null;
	}
	
	@Public
	public Uri getFileUri(ChanLocator locator)
	{
		return locator.convert(getRelativeFileUri());
	}
	
	@Public
	public FileAttachment setFileUri(ChanLocator locator, Uri fileUri)
	{
		mFileUriString = fileUri != null ? locator.makeRelative(fileUri).toString() : null;
		return this;
	}
	
	public Uri getRelativeThumbnailUri()
	{
		return mThumbnailUriString != null ? Uri.parse(fixRelativeUriString(mThumbnailUriString)) : null;
	}
	
	@Public
	public Uri getThumbnailUri(ChanLocator locator)
	{
		return locator.convert(getRelativeThumbnailUri());
	}
	
	@Public
	public FileAttachment setThumbnailUri(ChanLocator locator, Uri thumbnailUri)
	{
		mThumbnailUriString = thumbnailUri != null ? locator.makeRelative(thumbnailUri).toString() : null;
		return this;
	}
	
	@Public
	public String getOriginalName()
	{
		return mOriginalName;
	}
	
	@Public
	public FileAttachment setOriginalName(String originalName)
	{
		mOriginalName = originalName;
		return this;
	}
	
	public String getNormalizedOriginalName(String fileName)
	{
		return getNormalizedOriginalName(fileName, StringUtils.getFileExtension(fileName));
	}
	
	public String getNormalizedOriginalName(String fileName, String extension)
	{
		String originalName = getOriginalName();
		if (!StringUtils.isEmpty(originalName))
		{
			originalName = StringUtils.escapeFile(originalName, false);
			if (!originalName.toLowerCase(Locale.US).endsWith("." + extension)) originalName += "." + extension;
			if (fileName.equals(originalName)) return null;
			return originalName;
		}
		return null;
	}
	
	@Public
	public int getSize()
	{
		return mSize;
	}
	
	@Public
	public FileAttachment setSize(int size)
	{
		mSize = size;
		return this;
	}
	
	@Public
	public int getWidth()
	{
		return mWidth;
	}
	
	@Public
	public FileAttachment setWidth(int width)
	{
		mWidth = width;
		return this;
	}
	
	@Public
	public int getHeight()
	{
		return mHeight;
	}
	
	@Public
	public FileAttachment setHeight(int height)
	{
		mHeight = height;
		return this;
	}
	
	@Public
	public boolean isSpoiler()
	{
		return mSpoiler;
	}
	
	@Public
	public FileAttachment setSpoiler(boolean spoiler)
	{
		mSpoiler = spoiler;
		return this;
	}
	
	public boolean contentEquals(FileAttachment o)
	{
		return StringUtils.equals(mFileUriString, o.mFileUriString) && StringUtils.equals(mThumbnailUriString,
				o.mThumbnailUriString) && StringUtils.equals(mOriginalName, o.mOriginalName) && mSize == o.mSize &&
				mWidth == o.mWidth && mHeight == o.mHeight && mSpoiler == o.mSpoiler;
	}
}