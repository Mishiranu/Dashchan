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

import chan.annotation.Public;

@Public
public final class HttpRequest
{
	@Public
	public interface Preset
	{
		
	}
	
	public interface HolderPreset extends Preset
	{
		public HttpHolder getHolder();
	}
	
	public interface TimeoutsPreset extends Preset
	{
		public int getConnectTimeout();
		public int getReadTimeout();
	}
	
	public interface InputListenerPreset extends Preset
	{
		public HttpHolder.InputListener getInputListener();
	}
	
	public interface OutputListenerPreset extends Preset
	{
		public OutputListener getOutputListener();
	}
	
	public interface OutputStreamPreset extends Preset
	{
		public OutputStream getOutputStream();
	}
	
	public interface OutputListener
	{
		public void onOutputProgressChange(long progress, long progressMax);
	}
	
	@Public
	public interface RedirectHandler
	{
		@Public
		public enum Action
		{
			@Public CANCEL,
			@Public GET,
			@Public RETRANSMIT;
			
			private Uri mRedirectedUri;
			
			@Public
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
		
		@Public
		public Action onRedirectReached(int responseCode, Uri requestedUri, Uri redirectedUri, HttpHolder holder)
				throws HttpException;
		
		@Public
		public static final RedirectHandler NONE = (responseCode, requestedUri, redirectedUri, holder) -> Action.CANCEL;
		
		@Public
		public static final RedirectHandler BROWSER = (responseCode, requestedUri, redirectedUri, holder) -> Action.GET;
		
		@Public
		public static final RedirectHandler STRICT = (responseCode, requestedUri, redirectedUri, holder) ->
		{
			switch (responseCode)
			{
				case HttpURLConnection.HTTP_MOVED_PERM:
				case HttpURLConnection.HTTP_MOVED_TEMP: return Action.RETRANSMIT;
				default: return Action.GET;
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
	
	@Public
	public HttpRequest(Uri uri, HttpHolder holder, Preset preset)
	{
		if (holder == null && preset instanceof HolderPreset) holder = ((HolderPreset) preset).getHolder();
		if (holder == null) holder = new HttpHolder();
		mUri = uri;
		mHolder = holder;
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
	
	@Public
	public HttpRequest(Uri uri, HttpHolder holder)
	{
		this(uri, holder, null);
	}
	
	@Public
	public HttpRequest(Uri uri, Preset preset)
	{
		this(uri, null, preset);
	}
	
	private HttpRequest setMethod(int method, RequestEntity entity)
	{
		mRequestMethod = method;
		mRequestEntity = entity;
		return this;
	}
	
	@Public
	public HttpRequest setGetMethod()
	{
		return setMethod(REQUEST_METHOD_GET, null);
	}
	
	@Public
	public HttpRequest setHeadMethod()
	{
		return setMethod(REQUEST_METHOD_HEAD, null);
	}
	
	@Public
	public HttpRequest setPostMethod(RequestEntity entity)
	{
		return setMethod(REQUEST_METHOD_POST, entity);
	}
	
	@Public
	public HttpRequest setPutMethod(RequestEntity entity)
	{
		return setMethod(REQUEST_METHOD_PUT, entity);
	}
	
	@Public
	public HttpRequest setDeleteMethod(RequestEntity entity)
	{
		return setMethod(REQUEST_METHOD_DELETE, entity);
	}
	
	@Public
	public HttpRequest setSuccessOnly(boolean successOnly)
	{
		mSuccessOnly = successOnly;
		return this;
	}
	
	@Public
	public HttpRequest setRedirectHandler(RedirectHandler redirectHandler)
	{
		if (redirectHandler == null) throw new NullPointerException();
		mRedirectHandler = redirectHandler;
		return this;
	}
	
	@Public
	public HttpRequest setValidator(HttpValidator validator)
	{
		mValidator = validator;
		return this;
	}
	
	@Public
	public HttpRequest setKeepAlive(boolean keepAlive)
	{
		mKeepAlive = keepAlive;
		return this;
	}
	
	@Public
	public HttpRequest setTimeouts(int connectTimeout, int readTimeout)
	{
		if (connectTimeout >= 0) mConnectTimeout = connectTimeout;
		if (readTimeout >= 0) mReadTimeout = readTimeout;
		return this;
	}
	
	@Public
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
	
	@Public
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
	
	@Public
	public HttpRequest addHeader(String name, String value)
	{
		return addHeader(new Pair<>(name, value));
	}
	
	@Public
	public HttpRequest clearHeaders()
	{
		mHeaders = null;
		return this;
	}
	
	@Public
	public HttpRequest addCookie(String name, String value)
	{
		if (value != null)
		{
			if (mCookieBuilder == null) mCookieBuilder = new CookieBuilder();
			mCookieBuilder.append(name, value);
		}
		return this;
	}
	
	@Public
	public HttpRequest addCookie(String cookie)
	{
		if (cookie != null)
		{
			if (mCookieBuilder == null) mCookieBuilder = new CookieBuilder();
			mCookieBuilder.append(cookie);
		}
		return this;
	}
	
	@Public
	public HttpRequest addCookie(CookieBuilder builder)
	{
		if (builder != null)
		{
			if (mCookieBuilder == null) mCookieBuilder = new CookieBuilder();
			mCookieBuilder.append(builder);
		}
		return this;
	}
	
	@Public
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
	
	@Public
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
	
	@Public
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
	
	@Public
	public HttpResponse read() throws HttpException
	{
		HttpHolder holder = execute();
		try
		{
			if (mRequestMethod == REQUEST_METHOD_HEAD) return null;
			return holder.read();
		}
		catch (HttpException e)
		{
			mHolder.disconnect();
			throw e;
		}
	}
}