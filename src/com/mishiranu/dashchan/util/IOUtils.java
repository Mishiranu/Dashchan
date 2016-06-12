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

package com.mishiranu.dashchan.util;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Comparator;
import java.util.List;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.webkit.MimeTypeMap;

import chan.util.StringUtils;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.preference.Preferences;

public class IOUtils
{
	public static boolean startsWith(byte[] where, byte[] what)
	{
		if (where == null || what == null) return false;
		if (what.length > where.length) return false;
		for (int i = 0; i < what.length; i++)
		{
			if (what[i] != where[i]) return false;
		}
		return true;
	}
	
	public static final int bytesToInt(byte[] bytes, int start, int count)
	{
		int result = 0;
		for (int i = 0; i < count; i++) result = result << 8 | bytes[start + i] & 0xff;
		return result;
	}
	
	public static final void intToBytes(byte[] bytes, int start, int count, int value)
	{
		for (int i = count - 1; i >= 0; i--)
		{
			bytes[start + i] = (byte) (value & 0xff);
			value >>>= 8;
		}
	}
	
	public static void copyStream(InputStream from, OutputStream to) throws IOException
	{
		byte data[] = new byte[8192];
		int count;
		while ((count = from.read(data)) != -1) to.write(data, 0, count);
	}
	
	public static void close(Closeable closeable)
	{
		try
		{
			if (closeable != null) closeable.close();
		}
		catch (Exception e)
		{
			
		}
	}
	
	public static boolean copyInternalFile(File from, File to)
	{
		FileInputStream input = null;
		FileOutputStream output = null;
		try
		{
			input = new FileInputStream(from);
			output = new FileOutputStream(to);
			copyStream(input, output);
			return true;
		}
		catch (IOException e)
		{
			return false;
		}
		finally
		{
			close(input);
			close(output);
		}
	}
	
	public static final Comparator<File> SORT_BY_DATE = new Comparator<File>()
	{
		@Override
		public int compare(File lhs, File rhs)
		{
			return ((Long) lhs.lastModified()).compareTo(rhs.lastModified());
		}
	};
	
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private static Uri findChildDocument(ContentResolver contentResolver, Uri uri, String displayName)
	{
		String[] projection = {DocumentsContract.Document.COLUMN_DOCUMENT_ID,
				DocumentsContract.Document.COLUMN_DISPLAY_NAME};
		Uri childUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri,
				DocumentsContract.getDocumentId(uri));
		Cursor cursor = contentResolver.query(childUri, projection, null, null, null);
		try
		{
			while (cursor.moveToNext())
			{
				if (displayName.equals(cursor.getString(1)))
				{
					return DocumentsContract.buildDocumentUriUsingTree(uri, cursor.getString(0));
				}
			}
		}
		finally
		{
			cursor.close();
		}
		return null;
	}
	
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public static OutputStream openOutputStream(Context context, File file) throws IOException
	{
		if (C.API_LOLLIPOP)
		{
			File downloadDirectory = Preferences.getDownloadDirectory();
			String path = file.getAbsolutePath();
			String directoryPath = downloadDirectory.getAbsolutePath();
			if (path.startsWith(directoryPath) && !downloadDirectory.canWrite())
			{
				ContentResolver contentResolver = context.getContentResolver();
				List<UriPermission> uriPermissions = contentResolver.getPersistedUriPermissions();
				if (uriPermissions.size() > 0)
				{
					try
					{
						Uri treeUri = uriPermissions.get(0).getUri();
						Uri uri = DocumentsContract.buildDocumentUriUsingTree(treeUri,
								DocumentsContract.getTreeDocumentId(treeUri));
						String[] segments = path.substring(directoryPath.length() + 1).split("/");
						if (segments.length == 0) throw new FileNotFoundException();
						for (int i = 0; i < segments.length - 1; i++)
						{
							String displayName = segments[i];
							Uri childUri = findChildDocument(contentResolver, uri, displayName);
							if (childUri == null)
							{
								uri = DocumentsContract.createDocument(contentResolver, uri,
										DocumentsContract.Document.MIME_TYPE_DIR, displayName);
								if (uri == null) throw new FileNotFoundException();
							}
							else uri = childUri;
						}
						String displayName = segments[segments.length - 1];
						Uri childUri = findChildDocument(contentResolver, uri, displayName);
						if (childUri != null)
						{
							if (DocumentsContract.deleteDocument(contentResolver, childUri)) childUri = null;
						}
						if (childUri == null)
						{
							String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(StringUtils
									.getFileExtension(displayName));
							if (mimeType == null) mimeType = "application/octet-stream";
							uri = DocumentsContract.createDocument(contentResolver, uri, mimeType, displayName);
							if (uri == null) throw new FileNotFoundException();
						}
						else uri = childUri;
						return contentResolver.openOutputStream(uri);
					}
					catch (RuntimeException e)
					{
						throw new IOException(e);
					}
				}
			}
		}
		return new FileOutputStream(file);
	}
}