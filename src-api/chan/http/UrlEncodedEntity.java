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

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import chan.annotation.Extendable;
import chan.annotation.Public;

@Extendable
public class UrlEncodedEntity implements RequestEntity
{
	private final StringBuilder mBuilder = new StringBuilder();
	private byte[] mBytes;
	
	private String mCharsetName = "UTF-8";
	
	@Public
	public UrlEncodedEntity()
	{
		
	}
	
	@Public
	public UrlEncodedEntity(String... params)
	{
		for (int i = 0; i < params.length; i += 2) add(params[i], params[i + 1]);
	}
	
	@Public
	public void setEncoding(String charsetName)
	{
		mCharsetName = charsetName;
	}
	
	@Override
	public void add(String name, String value)
	{
		if (value != null)
		{
			mBytes = null;
			if (mBuilder.length() > 0) mBuilder.append('&');
			mBuilder.append(encode(name));
			mBuilder.append('=');
			mBuilder.append(encode(value));
		}
	}
	
	@Override
	public String getContentType()
	{
		return "application/x-www-form-urlencoded";
	}
	
	@Override
	public long getContentLength()
	{
		return getBytes().length;
	}
	
	@Override
	public void write(OutputStream output) throws IOException
	{
		output.write(getBytes());
		output.flush();
	}
	
	@Override
	public RequestEntity copy()
	{
		UrlEncodedEntity entity = new UrlEncodedEntity();
		entity.setEncoding(mCharsetName);
		entity.mBuilder.append(mBuilder);
		return entity;
	}
	
	private String encode(String string)
	{
		try
		{
			return URLEncoder.encode(string, mCharsetName);
		}
		catch (UnsupportedEncodingException e)
		{
			throw new RuntimeException(e);
		}
	}
	
	private byte[] getBytes()
	{
		if (mBytes == null)
		{
			try
			{
				mBytes = mBuilder.toString().getBytes("ISO-8859-1");
			}
			catch (UnsupportedEncodingException e)
			{
				throw new RuntimeException(e);
			}
		}
		return mBytes;
	}
}