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

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;

import android.net.Uri;
import android.util.Pair;

public class HttpRequest
{
	public static interface Preset
	{
		
	}
	
	public static interface TimeoutsPreset extends Preset
	{
		public int getConnectTimeout();
		public int getReadTimeout();
	}
	
	public static interface InputListenerPreset extends Preset
	{
		public HttpHolder.InputListener getInputListener();
	}
	
	public static interface OutputListenerPreset extends Preset
	{
		public OutputListener getOutputListener();
	}
	
	public static interface OutputStreamPreset extends Preset
	{
		public OutputStream getOutputStream();
	}
	
	public static interface OutputListener
	{
		public void onOutputProgressChange(long progress, long progressMax);
	}
	
	public static interface RedirectHandler
	{
		public static enum Action
		{
			CANCEL, GET, RETRANSMIT;
			
			private Uri mRedirectedUri;
		
			public Action setRedirectedUri(Uri redirectedUri)
			{
				mRedirectedUri = redirectedUri;
				return this;
			}
			
			public Uri getRedirectedUri()
			{
				return mRedirectedUri;
			}
			
			private void reset()
			{
				mRedirectedUri = null;
			}
			
			public static void resetAll()
			{
				CANCEL.reset();
				GET.reset();
				RETRANSMIT.reset();
			}
		}
		
		public Action onRedirectReached(int responseCode, Uri requestedUri, Uri redirectedUri, HttpHolder holder)
				throws HttpException;
		
		public static final RedirectHandler NONE = new RedirectHandler()
		{
			@Override
			public Action onRedirectReached(int responseCode, Uri requestedUri, Uri redirectedUri, HttpHolder holder)
			{
				return Action.CANCEL;
			}
		};
		
		public static final RedirectHandler BROWSER = new RedirectHandler()
		{
			@Override
			public Action onRedirectReached(int responseCode, Uri requestedUri, Uri redirectedUri, HttpHolder holder)
			{
				return Action.GET;
			}
		};
		
		public static final RedirectHandler STRICT = new RedirectHandler()
		{
			@Override
			public Action onRedirectReached(int responseCode, Uri requestedUri, Uri redirectedUri, HttpHolder holder)
			{
				switch (responseCode)
				{
					case HttpURLConnection.HTTP_MOVED_PERM:
					case HttpURLConnection.HTTP_MOVED_TEMP: return Action.RETRANSMIT;
					default: return Action.GET;
				}
			}
		};
	}
	
	final HttpHolder mHolder;
	final Uri mUri;
	
	static final int REQUEST_METHOD_GET = 0;
	static final int REQUEST_METHOD_HEAD = 1;
	static final int REQUEST_METHOD_POST = 2;
	static final int REQUEST_METHOD_PUT = 3;
	static final int REQUEST_METHOD_DELETE = 4;
	
	int mRequestMethod = REQUEST_METHOD_GET;
	RequestEntity mRequestEntity;
	
	boolean mSuccessOnly = true;
	RedirectHandler mRedirectHandler = RedirectHandler.BROWSER;
	HttpValidator mValidator;
	boolean mKeepAlive = true;

	HttpHolder.InputListener mInputListener;
	OutputListener mOutputListener;
	OutputStream mOutputStream;
	
	int mConnectTimeout = 15000;
	int mReadTimeout = 15000;
	int mDelay = 0;

	ArrayList<Pair<String, String>> mHeaders;
	CookieBuilder mCookieBuilder;
	
	boolean mCheckCloudFlare = true;
	
	public HttpRequest(Uri uri, HttpHolder holder)
	{
		if (holder == null) holder = new HttpHolder();
		mUri = uri;
		mHolder = holder;
	}
	
	public HttpRequest(Uri uri, HttpHolder holder, Preset preset)
	{
		this(uri, holder);
		if (preset instanceof TimeoutsPreset)
		{
			setTimeouts(((TimeoutsPreset) preset).getConnectTimeout(), ((TimeoutsPreset) preset).getReadTimeout());
		}
		if (preset instanceof OutputListenerPreset)
		{
			setOutputListener(((OutputListenerPreset) preset).getOutputListener());
		}
		if (preset instanceof InputListenerPreset)
		{
			setInputListener(((InputListenerPreset) preset).getInputListener());
		}
		if (preset instanceof OutputStreamPreset)
		{
			setOutputStream(((OutputStreamPreset) preset).getOutputStream());
		}
	}
	
	private HttpRequest setMethod(int method, RequestEntity entity)
	{
		mRequestMethod = method;
		mRequestEntity = entity;
		return this;
	}
	
	public HttpRequest setGetMethod()
	{
		return setMethod(REQUEST_METHOD_GET, null);
	}
	
	public HttpRequest setHeadMethod()
	{
		return setMethod(REQUEST_METHOD_HEAD, null);
	}
	
	public HttpRequest setPostMethod(RequestEntity entity)
	{
		return setMethod(REQUEST_METHOD_POST, entity);
	}
	
	public HttpRequest setPutMethod(RequestEntity entity)
	{
		return setMethod(REQUEST_METHOD_PUT, entity);
	}
	
	public HttpRequest setDeleteMethod(RequestEntity entity)
	{
		return setMethod(REQUEST_METHOD_DELETE, entity);
	}
	
	public HttpRequest setSuccessOnly(boolean successOnly)
	{
		mSuccessOnly = successOnly;
		return this;
	}
	
	public HttpRequest setRedirectHandler(RedirectHandler redirectHandler)
	{
		if (redirectHandler == null) throw new NullPointerException();
		mRedirectHandler = redirectHandler;
		return this;
	}
	
	public HttpRequest setValidator(HttpValidator validator)
	{
		mValidator = validator;
		return this;
	}
	
	public HttpRequest setKeepAlive(boolean keepAlive)
	{
		mKeepAlive = keepAlive;
		return this;
	}
	
	public HttpRequest setTimeouts(int connectTimeout, int readTimeout)
	{
		if (connectTimeout >= 0) mConnectTimeout = connectTimeout;
		if (readTimeout >= 0) mReadTimeout = readTimeout;
		return this;
	}
	
	public HttpRequest setDelay(int delay)
	{
		mDelay = delay;
		return this;
	}
	
	public HttpRequest setInputListener(HttpHolder.InputListener listener)
	{
		mInputListener = listener;
		return this;
	}
	
	public HttpRequest setOutputListener(OutputListener listener)
	{
		mOutputListener = listener;
		return this;
	}
	
	public HttpRequest setOutputStream(OutputStream outputStream)
	{
		mOutputStream = outputStream;
		return this;
	}
	
	private HttpRequest addHeader(Pair<String, String> header)
	{
		if (header != null)
		{
			if (mHeaders == null) mHeaders = new ArrayList<>();
			mHeaders.add(header);
		}
		return this;
	}
	
	public HttpRequest addHeader(String name, String value)
	{
		return addHeader(new Pair<>(name, value));
	}
	
	public HttpRequest clearHeaders()
	{
		mHeaders = null;
		return this;
	}
	
	public HttpRequest addCookie(String name, String value)
	{
		if (value != null)
		{
			if (mCookieBuilder == null) mCookieBuilder = new CookieBuilder();
			mCookieBuilder.append(name, value);
		}
		return this;
	}
	
	public HttpRequest addCookie(String cookie)
	{
		if (cookie != null)
		{
			if (mCookieBuilder == null) mCookieBuilder = new CookieBuilder();
			mCookieBuilder.append(cookie);
		}
		return this;
	}
	
	public HttpRequest addCookie(CookieBuilder builder)
	{
		if (builder != null)
		{
			if (mCookieBuilder == null) mCookieBuilder = new CookieBuilder();
			mCookieBuilder.append(builder);
		}
		return this;
	}
	
	public HttpRequest clearCookies()
	{
		mCookieBuilder = null;
		return this;
	}
	
	public HttpRequest setCheckCloudFlare(boolean checkCloudFlare)
	{
		mCheckCloudFlare = checkCloudFlare;
		return this;
	}
	
	public HttpRequest copy()
	{
		HttpRequest request = new HttpRequest(mUri, mHolder);
		request.setMethod(mRequestMethod, mRequestEntity);
		request.setSuccessOnly(mSuccessOnly);
		request.setRedirectHandler(mRedirectHandler);
		request.setValidator(mValidator);
		request.setKeepAlive(mKeepAlive);
		request.setInputListener(mInputListener);
		request.setOutputListener(mOutputListener);
		request.setOutputStream(mOutputStream);
		request.setTimeouts(mConnectTimeout, mReadTimeout);
		request.setDelay(mDelay);
		if (mHeaders != null) request.mHeaders = new ArrayList<>(mHeaders);
		request.addCookie(mCookieBuilder);
		request.setCheckCloudFlare(mCheckCloudFlare);
		return request;
	}
	
	public HttpHolder execute() throws HttpException
	{
		try
		{
			HttpClient.getInstance().execute(this);
			return mHolder;
		}
		catch (HttpException e)
		{
			mHolder.disconnect();
			throw e;
		}
	}
	
	public HttpResponse read() throws HttpException
	{
		HttpHolder holder = execute();
		try
		{
			if (mRequestMethod == REQUEST_METHOD_HEAD) return null;
			return holder.read(mInputListener, mOutputStream);
		}
		catch (HttpException e)
		{
			mHolder.disconnect();
			throw e;
		}
	}
}