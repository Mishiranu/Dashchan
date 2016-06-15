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

import java.net.HttpURLConnection;

import chan.annotation.Public;
import chan.util.StringUtils;

import com.mishiranu.dashchan.content.model.ErrorItem;

@Public
public final class HttpException extends Exception implements ErrorItem.Holder
{
	private static final long serialVersionUID = 1L;
	
	private final int mResponseCode;
	private final String mResponseText;
	private final int mErrorItemType;
	
	private final boolean mHttpException;
	private final boolean mSocketException;
	
	public HttpException(int errorItemType, boolean httpException, boolean socketException)
	{
		mResponseCode = 0;
		mErrorItemType = errorItemType;
		mResponseText = null;
		mHttpException = httpException;
		mSocketException = socketException;
	}
	
	public HttpException(int errorItemType, boolean httpException, boolean socketException, Throwable throwable)
	{
		super(throwable);
		mResponseCode = 0;
		mErrorItemType = errorItemType;
		mResponseText = null;
		mHttpException = httpException;
		mSocketException = socketException;
	}
	
	@Public
	public HttpException(int responseCode, String responseText)
	{
		mResponseCode = responseCode;
		mErrorItemType = 0;
		mResponseText = responseText;
		mHttpException = true;
		mSocketException = false;
	}
	
	@Public
	public int getResponseCode()
	{
		return mResponseCode;
	}
	
	@Public
	public boolean isHttpException()
	{
		return mHttpException;
	}
	
	@Public
	public boolean isSocketException()
	{
		return mSocketException;
	}
	
	@Override
	public ErrorItem getErrorItemAndHandle()
	{
		if (!StringUtils.isEmpty(mResponseText)) return new ErrorItem(mResponseCode, mResponseText);
		return new ErrorItem(mErrorItemType);
	}
	
	@Public
	public static HttpException createNotFoundException()
	{
		return new HttpException(HttpURLConnection.HTTP_NOT_FOUND, "Not Found");
	}
}