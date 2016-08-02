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

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.util.List;
import java.util.Map;

import android.net.Uri;

import chan.annotation.Public;

@Public
public final class HttpHolder
{
	Uri mRequestedUri;
	Proxy mProxy;
	String mChanName;
	boolean mVerifyCertificate;
	
	private int mAttempt;
	boolean mForceGet = false;
	
	void initRequest(HttpRequest request, Proxy proxy, String chanName, boolean verifyCertificate, int maxAttempts)
	{
		mRequestedUri = request.mUri;
		mProxy = proxy;
		mChanName = chanName;
		mVerifyCertificate = verifyCertificate;
		mAttempt = maxAttempts;
	}
	
	boolean nextAttempt()
	{
		return mAttempt-- > 0;
	}
	
	Uri mRedirectedUri;
	HttpValidator mValidator;
	private HttpResponse mResponse;
	
	private volatile Thread mRequestThread;
	private volatile HttpURLConnection mConnection;
	private volatile HttpURLConnection mDeadConnection;
	private volatile boolean mDisconnectRequested = false;
	private volatile boolean mInterrupted = false;
	
	InputListener mInputListener;
	OutputStream mOutputStream;
	
	public interface InputListener
	{
		public void onInputProgressChange(long progress, long progressMax);
	}
	
	public void interrupt()
	{
		mInterrupted = true;
		disconnect();
	}
	
	@Public
	public void disconnect()
	{
		mDisconnectRequested = true;
		if (mRequestThread == Thread.currentThread()) disconnectAndClear();
		mResponse = null;
	}
	
	void setConnection(HttpURLConnection connection, InputListener inputListener, OutputStream outputStream)
			throws HttpClient.DisconnectedIOException
	{
		mDisconnectRequested = false;
		mRequestThread = Thread.currentThread();
		mConnection = connection;
		mInputListener = inputListener;
		mOutputStream = outputStream;
		mRedirectedUri = null;
		mValidator = null;
		mResponse = null;
		if (mInterrupted) throw new HttpClient.DisconnectedIOException();
	}
	
	HttpURLConnection getConnection() throws HttpClient.DisconnectedIOException
	{
		HttpURLConnection connection = mConnection;
		if (connection == null) throw new HttpClient.DisconnectedIOException();
		return connection;
	}
	
	void checkDisconnected() throws HttpClient.DisconnectedIOException
	{
		checkDisconnected(null);
	}
	
	void checkDisconnected(Closeable closeable) throws HttpClient.DisconnectedIOException
	{
		if (mDisconnectRequested)
		{
			if (closeable != null)
			{
				try
				{
					closeable.close();
				}
				catch (IOException e)
				{
					
				}
			}
			throw new HttpClient.DisconnectedIOException();
		}
	}
	
	void disconnectAndClear()
	{
		HttpURLConnection connection = mConnection;
		mConnection = null;
		mInputListener = null;
		mOutputStream = null;
		if (connection != null)
		{
			connection.disconnect();
			mDeadConnection = connection;
			HttpClient.getInstance().onDisconnect(connection);
		}
	}
	
	@Public
	public HttpResponse read() throws HttpException
	{
		HttpResponse response = mResponse;
		if (response != null) return response;
		response = HttpClient.getInstance().read(this);
		mResponse = response;
		return mResponse;
	}
	
	@Public
	public void checkResponseCode() throws HttpException
	{
		HttpClient.getInstance().checkResponseCode(this);
	}
	
	private HttpURLConnection getConnectionForHeaders()
	{
		HttpURLConnection connection = mConnection;
		if (connection == null) connection = mDeadConnection;
		return connection;
	}
	
	@Public
	public int getResponseCode()
	{
		HttpURLConnection connection = getConnectionForHeaders();
		if (connection != null)
		{
			try
			{
				return connection.getResponseCode();
			}
			catch (IOException e)
			{
				throw new RuntimeException(e);
			}
		}
		return -1;
	}
	
	@Public
	public String getResponseMessage()
	{
		HttpURLConnection connection = getConnectionForHeaders();
		if (connection != null)
		{
			try
			{
				return connection.getResponseMessage();
			}
			catch (IOException e)
			{
				throw new RuntimeException(e);
			}
		}
		return null;
	}
	
	@Public
	public Uri getRedirectedUri()
	{
		return mRedirectedUri;
	}
	
	@Public
	public Map<String, List<String>> getHeaderFields()
	{
		HttpURLConnection connection = getConnectionForHeaders();
		return connection != null ? connection.getHeaderFields() : null;
	}
	
	@Public
	public String getCookieValue(String name)
	{
		Map<String, List<String>> headers = getHeaderFields();
		if (headers == null) return null;
		String start = name + "=";
		List<String> cookies = headers.get("Set-Cookie");
		if (cookies != null)
		{
			for (String cookie : cookies)
			{
				if (cookie.startsWith(start))
				{
					int startIndex = start.length();
					int endIndex = cookie.indexOf(';');
					if (endIndex >= 0) return cookie.substring(startIndex, endIndex);
					else return cookie.substring(startIndex); 
				}
			}
		}
		return null;
	}
	
	@Public
	public HttpValidator getValidator()
	{
		return mValidator;
	}
}