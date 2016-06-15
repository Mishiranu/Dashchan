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

import java.io.Serializable;
import java.net.HttpURLConnection;

import org.json.JSONException;
import org.json.JSONObject;

import android.os.Parcel;
import android.os.Parcelable;

import chan.annotation.Public;
import chan.util.CommonUtils;
import chan.util.StringUtils;

@Public
public final class HttpValidator implements Parcelable, Serializable
{
	private static final long serialVersionUID = 1L;
	
	private static final String KEY_ETAG = "ETag";
	private static final String KEY_LAST_MODIFIED = "LastModified";
	
	private final String mETag;
	private final String mLastModified;
	
	private HttpValidator(String eTag, String lastModified)
	{
		mETag = eTag;
		mLastModified = lastModified;
	}
	
	static HttpValidator obtain(HttpURLConnection connection)
	{
		String eTag = connection.getHeaderField("ETag");
		String lastModified = connection.getHeaderField("Last-Modified");
		if (eTag != null || lastModified != null) return new HttpValidator(eTag, lastModified);
		return null;
	}
	
	public void write(HttpURLConnection connection)
	{
		if (mETag != null) connection.setRequestProperty("If-None-Match", mETag);
		if (mLastModified != null) connection.setRequestProperty("If-Modified-Since", mLastModified);
	}
	
	@Override
	public int describeContents()
	{
		return 0;
	}
	
	@Override
	public void writeToParcel(Parcel dest, int flags)
	{
		dest.writeString(mETag);
		dest.writeString(mLastModified);
	}
	
	public static final Creator<HttpValidator> CREATOR = new Creator<HttpValidator>()
	{
		@Override
		public HttpValidator createFromParcel(Parcel source)
		{
			String eTag = source.readString();
			String lastModified = source.readString();
			return new HttpValidator(eTag, lastModified);
		}
		
		@Override
		public HttpValidator[] newArray(int size)
		{
			return new HttpValidator[size];
		}
	};
	
	public static HttpValidator fromString(String validator)
	{
		if (validator != null)
		{
			try
			{
				JSONObject jsonObject = new JSONObject(validator);
				String eTag = CommonUtils.optJsonString(jsonObject, KEY_ETAG);
				String lastModified = CommonUtils.optJsonString(jsonObject, KEY_LAST_MODIFIED);
				if (eTag != null || lastModified != null) return new HttpValidator(eTag, lastModified);
			}
			catch (JSONException e)
			{
				
			}
		}
		return null;
	}
	
	@Override
	public String toString()
	{
		JSONObject jsonObject = new JSONObject();
		try
		{
			if (!StringUtils.isEmpty(mETag)) jsonObject.put(KEY_ETAG, mETag);
			if (!StringUtils.isEmpty(mLastModified)) jsonObject.put(KEY_LAST_MODIFIED, mLastModified);
		}
		catch (JSONException e)
		{
			throw new RuntimeException(e);
		}
		return jsonObject.toString();
	}
}