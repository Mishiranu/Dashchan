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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.IDN;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLProtocolException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.util.Pair;

import chan.content.ChanLocator;
import chan.content.ChanManager;
import chan.util.CommonUtils;
import chan.util.StringUtils;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.app.MainApplication;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.net.CloudFlarePasser;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.Log;

public class HttpClient
{
	private static final int MAX_ATTEMPS_COUNT = 10;
	
	private static final HashMap<String, String> SHORT_RESPONSE_MESSAGES = new HashMap<>();
	
	private static final HostnameVerifier DEFAULT_HOSTNAME_VERIFIER = HttpsURLConnection.getDefaultHostnameVerifier();
	private static final HostnameVerifier ALLOW_ALL_HOSTNAME_VERIFIER = new AllowAllHostnameVerifier();
	
	private static final int HTTP_TEMPORARY_REDIRECT = 307;
	
	static
	{
		int poolSize = (ChanManager.getInstance().getAllChanNames().size() + 1) * 2;
		System.setProperty("http.maxConnections", Integer.toString(poolSize));
		try
		{
			// http.maxConnections may do nothing because ConnectionPool inits earlier. Android bug?
			Object connectionPool = Class.forName("com.android.okhttp.ConnectionPool").getMethod("getDefault")
					.invoke(null);
			Field maxIdleConnectionsField = connectionPool.getClass().getDeclaredField("maxIdleConnections");
			maxIdleConnectionsField.setAccessible(true);
			maxIdleConnectionsField.setInt(connectionPool, poolSize);
		}
		catch (Exception e)
		{
			
		}
		
		SHORT_RESPONSE_MESSAGES.put("Internal Server Error", "Internal Error");
		SHORT_RESPONSE_MESSAGES.put("Service Temporarily Unavailable", "Service Unavailable");
		
		if (Preferences.isUseGmsProvider())
		{
			try
			{
				// Load GmsCore_OpenSSL from Google Play Services package
				Context context = MainApplication.getInstance().createPackageContext("com.google.android.gms",
						Context.CONTEXT_IGNORE_SECURITY | Context.CONTEXT_INCLUDE_CODE);
				Class<?> providerInstallerImplClass = Class.forName("com.google.android.gms.common.security"
						+ ".ProviderInstallerImpl", false, context.getClassLoader());
				Method insertProviderMethod = providerInstallerImplClass.getMethod("insertProvider", Context.class);
				insertProviderMethod.invoke(null, context);
			}
			catch (Exception e)
			{
				
			}
		}
		
		/*
		 * MediaPlayer uses MediaHTTPConnection that uses its own CookieHandler instance.
		 * This cause some bugs in application work.
		 * 
		 * This CookieHandler doesn't allow app to store cookies when chan HttpClient used.
		 */
		CookieHandler.setDefault(new CookieHandler()
		{
			private final CookieManager mCookieManager = new CookieManager();
			
			private boolean isInternalRequest()
			{
				StackTraceElement[] elements = Thread.currentThread().getStackTrace();
				for (StackTraceElement element : elements)
				{
					if (HttpClient.class.getName().equals(element.getClassName())) return true;
				}
				return false;
			}
			
			@Override
			public void put(URI uri, Map<String, List<String>> responseHeaders) throws IOException
			{
				if (isInternalRequest()) return;
				mCookieManager.put(uri, responseHeaders);
			}
			
			@Override
			public Map<String, List<String>> get(URI uri, Map<String, List<String>> requestHeaders) throws IOException
			{
				if (isInternalRequest()) return Collections.emptyMap();
				return mCookieManager.get(uri, requestHeaders);
			}
		});
	}
	
	private static final HttpClient INSTANCE = new HttpClient();
	
	public static HttpClient getInstance()
	{
		return INSTANCE;
	}
	
	private final HashMap<String, Proxy> mProxies = new HashMap<>();
	private boolean mUseNoSSLv3SSLSocketFactory = false;
	
	private HttpClient()
	{
		for (String chanName : ChanManager.getInstance().getAllChanNames())
		{
			Proxy proxy;
			try
			{
				proxy = initProxy(chanName, false);
			}
			catch (Exception e)
			{
				// Impossible with throwIfNotValid == false
				throw new RuntimeException(e);
			}
			if (proxy != null) mProxies.put(chanName, proxy);
		}
	}
	
	private Proxy initProxy(String chanName, boolean throwIfNotValid) throws Exception
	{
		Proxy proxy = null;
		String[] proxyData = Preferences.getProxy(chanName);
		if (proxyData != null && proxyData[0] != null && proxyData[1] != null)
		{
			boolean socks = Preferences.VALUE_PROXY_2_SOCKS.equals(proxyData[2]);
			try
			{
				proxy = new Proxy(socks ? Proxy.Type.SOCKS : Proxy.Type.HTTP, InetSocketAddress
						.createUnresolved(proxyData[0], Integer.parseInt(proxyData[1])));
			}
			catch (Exception e)
			{
				if (throwIfNotValid) throw e;
			}
		}
		return proxy;
	}
	
	public boolean updateProxy(String chanName)
	{
		Proxy proxy;
		try
		{
			proxy = initProxy(chanName, true);
		}
		catch (Exception e)
		{
			return false;
		}
		if (proxy != null) mProxies.put(chanName, proxy); else mProxies.remove(chanName);
		return true;
	}
	
	private static class AllowAllHostnameVerifier implements HostnameVerifier
	{
		@SuppressLint("BadHostnameVerifier")
		@Override
		public boolean verify(String hostname, SSLSession session)
		{
			return true;
		}
	}
	
	static final class DisconnectedIOException extends IOException
	{
		private static final long serialVersionUID = 1L;
	}
	
	void execute(HttpRequest request) throws HttpException
	{
		String chanName = ChanManager.getInstance().getChanNameByHost(request.mUri.getAuthority());
		ChanLocator locator = ChanLocator.get(chanName);
		boolean verifyCertificate = locator.isUseHttps() && Preferences.isVerifyCertificate();
		request.mHolder.initRequest(request, mProxies.get(chanName), chanName, verifyCertificate, MAX_ATTEMPS_COUNT);
		executeInternal(request);
	}
	
	private void encodeUriBufferPart(StringBuilder uriStringBuilder, char[] chars, int i, int start, boolean ascii)
	{
		if (!ascii)
		{
			try
			{
				for (byte b : new String(chars, start, i - start).getBytes("UTF-8"))
				{
					String s = Integer.toString(b & 0xff, 16).toUpperCase(Locale.US);
					uriStringBuilder.append('%');
					uriStringBuilder.append(s);
				}
			}
			catch (Exception e)
			{
				
			}
		}
		else uriStringBuilder.append(chars, start, i - start);
	}
	
	private void encodeUriAppend(StringBuilder uriStringBuilder, String part)
	{
		char[] chars = part.toCharArray();
		boolean ascii = true;
		int start = 0;
		for (int i = 0; i < chars.length; i++)
		{
			char c = chars[i];
			boolean ita = c < 0x80;
			if (ita != ascii)
			{
				encodeUriBufferPart(uriStringBuilder, chars, i, start, ascii);
				start = i;
				ascii = ita;
			}
		}
		encodeUriBufferPart(uriStringBuilder, chars, chars.length, start, ascii);
	}
	
	private URL encodeUri(Uri uri) throws MalformedURLException
	{
		StringBuilder uriStringBuilder = new StringBuilder();
		uriStringBuilder.append(uri.getScheme()).append("://");
		String host = IDN.toASCII(uri.getHost());
		uriStringBuilder.append(host);
		int port = uri.getPort();
		if (port != -1) uriStringBuilder.append(':').append(port);
		String path = uri.getEncodedPath();
		if (!StringUtils.isEmpty(path)) encodeUriAppend(uriStringBuilder, path);
		String query = uri.getEncodedQuery();
		if (!StringUtils.isEmpty(query))
		{
			uriStringBuilder.append('?');
			encodeUriAppend(uriStringBuilder, query);
		}
		return new URL(uriStringBuilder.toString());
	}
	
	@TargetApi(Build.VERSION_CODES.KITKAT)
	private void executeInternal(HttpRequest request) throws HttpException
	{
		HttpURLConnection connection;
		HttpHolder holder = request.mHolder;
		try
		{
			Uri requestedUri = request.mHolder.mRequestedUri;
			if (!ChanLocator.getDefault().isWebScheme(requestedUri))
			{
				throw new HttpException(ErrorItem.TYPE_UNSUPPORTED_SCHEME, false, false);
			}
			URL url = encodeUri(requestedUri);
			connection = (HttpURLConnection) (holder.mProxy != null ? url.openConnection(holder.mProxy)
					: url.openConnection());
			if (connection instanceof HttpsURLConnection)
			{
				HttpsURLConnection secureConnection = (HttpsURLConnection) connection;
				secureConnection.setHostnameVerifier(holder.mVerifyCertificate ? DEFAULT_HOSTNAME_VERIFIER
						: ALLOW_ALL_HOSTNAME_VERIFIER);
			}
			holder.setConnection(connection);
			String chanName = holder.mChanName;
			
			connection.setUseCaches(false);
			connection.setConnectTimeout(request.mConnectTimeout);
			connection.setReadTimeout(request.mReadTimeout);
			connection.setRequestProperty("User-Agent", C.USER_AGENT);
			connection.setInstanceFollowRedirects(false);
			connection.setRequestProperty("Connection", request.mKeepAlive ? "keep-alive" : "close");
			connection.setRequestProperty("Accept-Encoding", "gzip");
			if (request.mHeaders != null)
			{
				for (Pair<String, String> header : request.mHeaders)
				{
					connection.setRequestProperty(header.first, header.second);
				}
			}
			CookieBuilder cookieBuilder = request.mCookieBuilder;
			String cloudFlareCookie = CloudFlarePasser.getCookie(chanName);
			if (cloudFlareCookie != null)
			{
				cookieBuilder = new CookieBuilder(cookieBuilder).append(CloudFlarePasser.COOKIE_CLOUDFLARE,
						cloudFlareCookie);
			}
			if (cookieBuilder != null) connection.setRequestProperty("Cookie", cookieBuilder.build());
			HttpValidator validator = request.mValidator;
			if (validator != null) validator.write(connection);
			
			boolean forceGet = holder.mForceGet;
			int method = forceGet ? HttpRequest.REQUEST_METHOD_GET : request.mRequestMethod;
			RequestEntity entity = forceGet ? null : request.mRequestEntity;
			String methodString;
			switch (method)
			{
				case HttpRequest.REQUEST_METHOD_GET: methodString = "GET"; break;
				case HttpRequest.REQUEST_METHOD_HEAD: methodString = "HEAD"; break;
				case HttpRequest.REQUEST_METHOD_POST: methodString = "POST"; break;
				case HttpRequest.REQUEST_METHOD_PUT: methodString = "PUT"; break;
				case HttpRequest.REQUEST_METHOD_DELETE: methodString = "DELETE"; break;
				default: throw new RuntimeException();
			}
			connection.setRequestMethod(methodString);
			onConnect(connection, request.mDelay);
			if (entity != null)
			{
				connection.setDoOutput(true);
				connection.setRequestProperty("Content-Type", entity.getContentType());
				long contentLength = entity.getContentLength();
				if (contentLength > 0)
				{
					if (C.API_KITKAT) connection.setFixedLengthStreamingMode(contentLength);
					else connection.setFixedLengthStreamingMode((int) contentLength);
				}
				ClientOutputStream output = new ClientOutputStream(new BufferedOutputStream(connection
						.getOutputStream(), 1024), holder, forceGet ? null : request.mOutputListener, contentLength);
				entity.write(output);
				output.flush();
				output.close();
				holder.checkDisconnected();
			}
			
			int responseCode = connection.getResponseCode();
			HttpRequest.RedirectHandler redirectHandler = request.mRedirectHandler;
			switch (responseCode)
			{
				case HttpURLConnection.HTTP_MOVED_PERM:
				case HttpURLConnection.HTTP_MOVED_TEMP:
				case HttpURLConnection.HTTP_SEE_OTHER:
				case HTTP_TEMPORARY_REDIRECT:
				{
					// Scheme changed, so I must handle redirect myself
					boolean oldHttps = connection instanceof HttpsURLConnection;
					String redirectedUriString = connection.getHeaderField("Location");
					Uri redirectedUri;
					if (!StringUtils.isEmpty(redirectedUriString))
					{
						redirectedUri = Uri.parse(redirectedUriString);
						if (redirectedUri.isRelative())
						{
							Uri.Builder builder = redirectedUri.buildUpon().scheme(requestedUri.getScheme())
									.authority(requestedUri.getAuthority());
							String path = redirectedUri.getPath();
							if (path == null || !path.startsWith("/"))
							{
								String requestedPath = requestedUri.getPath();
								if (!StringUtils.isEmpty(requestedPath))
								{
									builder.path(requestedPath);
									if (path != null) builder.appendEncodedPath(path);
								}
							}
							redirectedUri = builder.build();
						}
					}
					else redirectedUri = requestedUri;
					holder.mRedirectedUri = redirectedUri;
					HttpRequest.RedirectHandler.Action action;
					Uri overriddenRedirectedUri;
					try
					{
						synchronized (HttpRequest.RedirectHandler.class)
						{
							HttpRequest.RedirectHandler.Action.resetAll();
							action = redirectHandler.onRedirectReached(responseCode, requestedUri, redirectedUri,
									holder);
							overriddenRedirectedUri = action.getRedirectedUri();
						}
					}
					catch (HttpException e)
					{
						holder.disconnectAndClear();
						throw e;
					}
					if (overriddenRedirectedUri != null) redirectedUri = overriddenRedirectedUri;
					if (action == HttpRequest.RedirectHandler.Action.GET ||
							action == HttpRequest.RedirectHandler.Action.RETRANSMIT)
					{
						holder.disconnectAndClear();
						boolean newHttps = "https".equals(redirectedUri.getScheme());
						if (holder.mVerifyCertificate && oldHttps && !newHttps)
						{
							// Redirect from https to http is unsafe
							throw new HttpException(ErrorItem.TYPE_UNSAFE_REDIRECT, true, false);
						}
						if (action == HttpRequest.RedirectHandler.Action.GET) holder.mForceGet = true;
						holder.mRequestedUri = redirectedUri;
						holder.mRedirectedUri = null;
						if (holder.nextAttempt())
						{
							executeInternal(request);
							return;
						}
						else
						{
							holder.disconnectAndClear();
							throw new HttpException(responseCode, holder.getResponseMessage());
						}
					}
				}
			}
			if (chanName != null && request.mCheckCloudFlare && CloudFlarePasser.checkResponse(chanName, holder))
			{
				if (holder.nextAttempt())
				{
					executeInternal(request);
					return;
				}
				else
				{
					holder.disconnectAndClear();
					throw new HttpException(responseCode, holder.getResponseMessage());
				}
			}
			
			if (validator != null && responseCode == HttpURLConnection.HTTP_NOT_MODIFIED)
			{
				String responseMessage = connection.getResponseMessage();
				holder.disconnectAndClear();
				throw new HttpException(responseCode, responseMessage);
			}
			if (request.mSuccessOnly) checkResponseCode(holder);
			holder.mValidator = HttpValidator.obtain(connection);
			holder.checkDisconnected();
		}
		catch (DisconnectedIOException e)
		{
			holder.disconnectAndClear();
			throw new HttpException(0, false, false, e);
		}
		catch (IOException e)
		{
			if (isConnectionReset(e))
			{
				// Sometimes server closes the socket, but client is still trying to use it
				if (holder.nextAttempt())
				{
					Log.persistent().stack(e);
					executeInternal(request);
					return;
				}
			}
			if (e.getCause() instanceof SSLProtocolException)
			{
				String message = e.getMessage();
				if (message != null && message.contains("routines:SSL23_GET_SERVER_HELLO:sslv3"))
				{
					synchronized (this)
					{
						if (!mUseNoSSLv3SSLSocketFactory)
						{
							// Fix https://code.google.com/p/android/issues/detail?id=78187
							HttpsURLConnection.setDefaultSSLSocketFactory(new NoSSLv3SSLSocketFactory
									(HttpsURLConnection.getDefaultSSLSocketFactory()));
							mUseNoSSLv3SSLSocketFactory = true;
						}
					}
					if (holder.nextAttempt())
					{
						executeInternal(request);
						return;
					}
				}
			}
			holder.disconnectAndClear();
			checkExceptionAndThrow(e);
			throw new HttpException(ErrorItem.TYPE_DOWNLOAD, false, true, e);
		}
	}
	
	HttpResponse read(HttpHolder holder, HttpHolder.InputListener listener, OutputStream output)
			throws HttpException
	{
		try
		{
			HttpURLConnection connection = holder.getConnection();
			holder.checkDisconnected();
			InputStream commonInput;
			try
			{
				commonInput = connection.getInputStream();
			}
			catch (FileNotFoundException e)
			{
				commonInput = connection.getErrorStream();
			}
			commonInput = new BufferedInputStream(commonInput, 4096);
			String encoding = connection.getContentEncoding();
			int contentLength = connection.getContentLength();
			if ("gzip".equals(encoding))
			{
				commonInput = new GZIPInputStream(commonInput);
				contentLength = -1;
			}
			ClientInputStream input = new ClientInputStream(commonInput, holder, listener, contentLength);
			ByteArrayOutputStream writeTo = output == null ? new ByteArrayOutputStream() : null;
			if (output == null) output = writeTo;
			try
			{
				IOUtils.copyStream(input, output);
			}
			finally
			{
				IOUtils.close(input);
				IOUtils.close(output);
			}
			String contentType = connection.getHeaderField("Content-Type");
			String charsetName = null;
			if (contentType != null)
			{
				int index = contentType.indexOf("charset=");
				if (index >= 0)
				{
					int end = contentType.indexOf(';', index);
					charsetName = contentType.substring(index + 8, end >= 0 ? end : contentType.length());
					try
					{
						Charset.forName(charsetName);
					}
					catch (UnsupportedCharsetException e)
					{
						charsetName = null;
					}
				}
			}
			holder.checkDisconnected();
			if (writeTo != null)
			{
				HttpResponse httpResponse = new HttpResponse(writeTo.toByteArray());
				if (charsetName != null) httpResponse.setEncoding(charsetName);
				return httpResponse;
			}
			else return null;
		}
		catch (DisconnectedIOException e)
		{
			throw new HttpException(0, false, false, e);
		}
		catch (IOException e)
		{
			checkExceptionAndThrow(e);
			throw new HttpException(ErrorItem.TYPE_DOWNLOAD, false, true, e);
		}
		finally
		{
			holder.disconnectAndClear();
		}
	}
	
	void checkResponseCode(HttpHolder holder) throws HttpException
	{
		int responseCode = holder.getResponseCode();
		boolean success = responseCode >= HttpURLConnection.HTTP_OK && responseCode <= HttpURLConnection.HTTP_SEE_OTHER
				|| responseCode == HTTP_TEMPORARY_REDIRECT;
		if (!success)
		{
			String originalMessage = holder.getResponseMessage();
			String message = SHORT_RESPONSE_MESSAGES.get(originalMessage);
			if (message == null) message = originalMessage;
			holder.disconnectAndClear();
			throw new HttpException(responseCode, message);
		}
	}
	
	private void checkExceptionAndThrow(IOException exception) throws HttpException
	{
		Log.persistent().stack(exception);
		int errorType = getErrorTypeForException(exception);
		if (errorType != 0) throw new HttpException(errorType, false, true, exception);
	}
	
	private int getErrorTypeForException(IOException exception)
	{
		if (isConnectionReset(exception)) return ErrorItem.TYPE_CONNECTION_RESET;
		String message = exception.getMessage();
		if (message != null)
		{
			if (message.contains("failed to connect to") && message.contains("ETIMEDOUT"))
			{
				return ErrorItem.TYPE_CONNECT_TIMEOUT;
			}
			if (message.contains("SSL handshake timed out"))
			{
				// SocketTimeoutException
				// Throws when connection was established but SSL handshake was timed out
				return ErrorItem.TYPE_CONNECT_TIMEOUT;
			}
			if (message.matches("Hostname .+ (was )?not verified"))
			{
				// IOException
				// Throws when hostname not matches certificate
				return ErrorItem.TYPE_INVALID_CERTIFICATE;
			}
			if (message.contains("Could not validate certificate") ||
					message.contains("Trust anchor for certification path not found"))
			{
				// SSLHandshakeException
				// Throws when certificate expired or not yet valid
				return ErrorItem.TYPE_INVALID_CERTIFICATE;
			}
		}
		if (exception instanceof SSLException) return ErrorItem.TYPE_SSL;
		if (exception instanceof SocketTimeoutException) return ErrorItem.TYPE_READ_TIMEOUT;
		return 0;
	}
	
	private boolean isConnectionReset(IOException exception)
	{
		if (exception instanceof EOFException) return true;
		String message = exception.getMessage();
		return message != null && (message.contains("Connection reset by peer")
				|| message.contains("Connection closed by peer") || message.contains("unexpected end of stream")
				|| message.contains("Connection refused"));
	}
	
	public static InputStream wrapWithProgressListener(InputStream input, HttpHolder.InputListener listener,
			long contentLength)
	{
		return new ClientInputStream(input, new HttpHolder(), listener, contentLength);
	}
	
	private static class ClientInputStream extends InputStream
	{
		private final InputStream mInput;
		private final HttpHolder mHolder;
		
		private final HttpHolder.InputListener mListener;
		private final long mContentLength;
		
		private volatile long mProgress;
		
		public ClientInputStream(InputStream input, HttpHolder holder,
				HttpHolder.InputListener listener, long contentLength)
		{
			mInput = input;
			mHolder = holder;
			mListener = contentLength > 0 ? listener : null;
			mContentLength = contentLength;
			if (mListener != null) mListener.onInputProgressChange(0, contentLength);
		}
		
		@Override
		public int read() throws IOException
		{
			mHolder.checkDisconnected(this);
			int value = mInput.read();
			updateProgress(value);
			return value;
		}
		
		@Override
		public int read(byte[] b) throws IOException
		{
			return read(b, 0, b.length);
		}
		
		@Override
		public int read(byte[] b, int off, int len) throws IOException
		{
			mHolder.checkDisconnected(this);
			int value = mInput.read(b, off, len);
			updateProgress(value);
			return value;
		}
		
		@Override
		public long skip(long n) throws IOException
		{
			mHolder.checkDisconnected(this);
			long value = super.skip(n);
			updateProgress(value);
			return value;
		}
		
		private void updateProgress(long value)
		{
			if (mListener != null && value > 0)
			{
				mProgress += value;
				mListener.onInputProgressChange(mProgress, mContentLength);
			}
		}
		
		@Override
		public int available() throws IOException
		{
			mHolder.checkDisconnected(this);
			return mInput.available();
		}
		
		@Override
		public void close() throws IOException
		{
			mInput.close();
		}
		
		@Override
		public void mark(int readlimit)
		{
			mInput.mark(readlimit);
		}
		
		@Override
		public boolean markSupported()
		{
			return mInput.markSupported();
		}
		
		@Override
		public synchronized void reset() throws IOException
		{
			mInput.reset();
		}
	}
	
	private static class ClientOutputStream extends OutputStream
	{
		private final OutputStream mOutput;
		private final HttpHolder mHolder;
		
		private final HttpRequest.OutputListener mListener;
		private final long mContentLength;
		
		private volatile long mProgress;
		
		public ClientOutputStream(OutputStream output, HttpHolder holder, HttpRequest.OutputListener listener,
				long contentLength)
		{
			mOutput = output;
			mHolder = holder;
			mListener = contentLength > 0 ? listener : null;
			mContentLength = contentLength;
			if (mListener != null) mListener.onOutputProgressChange(0, contentLength);
		}
		
		@Override
		public void write(int oneByte) throws IOException
		{
			mHolder.checkDisconnected(this);
			mOutput.write(oneByte);
			updateProgress(1);
		}
		
		@Override
		public void write(byte[] buffer) throws IOException
		{
			mHolder.checkDisconnected(this);
			mOutput.write(buffer);
			updateProgress(buffer.length);
		}
		
		@Override
		public void write(byte[] buffer, int offset, int length) throws IOException
		{
			mHolder.checkDisconnected(this);
			mOutput.write(buffer, offset, length);
			updateProgress(length);
		}
		
		private void updateProgress(long value)
		{
			if (mListener != null && value > 0)
			{
				mProgress += value;
				mListener.onOutputProgressChange(mProgress, mContentLength);
			}
		}
		
		@Override
		public void close() throws IOException
		{
			mOutput.close();
		}
		
		@Override
		public void flush() throws IOException
		{
			mHolder.checkDisconnected(this);
			mOutput.flush();
		}
	}
	
	/*private static class DelayLock
	{
		public HttpURLConnection active = null;
	}
	
	private final HashMap<String, DelayLock> mDelayLocks = new HashMap<>();*/
	
	private static class DelayLock
	{
		public boolean locked = false;
	}
	
	private final HashMap<String, DelayLock> mDelayLocks = new HashMap<>();
	
	void onConnect(HttpURLConnection connection, int delay)
	{
		if (delay > 0)
		{
			URL url = connection.getURL();
			String key = url.getAuthority();
			DelayLock delayLock;
			synchronized (mDelayLocks)
			{
				delayLock = mDelayLocks.get(key);
				if (delayLock == null)
				{
					delayLock = new DelayLock();
					mDelayLocks.put(key, delayLock);
				}
			}
			synchronized (delayLock)
			{
				try
				{
					while (delayLock.locked) delayLock.wait();
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
					return;
				}
				delayLock.locked = true;
				try
				{
					Thread.sleep(delay);
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
				}
				finally
				{
					delayLock.locked = false;
					delayLock.notifyAll();
				}
			}
		}
	}
	
	void onDisconnect(HttpURLConnection connection)
	{
		
	}
	
	private static class NoSSLv3SSLSocketFactory extends SSLSocketFactory
	{
		private final SSLSocketFactory mWrapped;
		
		public NoSSLv3SSLSocketFactory(SSLSocketFactory sslSocketFactory)
		{
			mWrapped = sslSocketFactory;
		}
		
		private Socket wrap(Socket socket)
		{
			if (socket instanceof SSLSocket) socket = new NoSSLv3SSLSocket((SSLSocket) socket);
			return socket;
		}
		
		@Override
		public String[] getDefaultCipherSuites()
		{
			return mWrapped.getDefaultCipherSuites();
		}
		
		@Override
		public String[] getSupportedCipherSuites()
		{
			return mWrapped.getSupportedCipherSuites();
		}
		
		@Override
		public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException
		{
			return wrap(mWrapped.createSocket(s, host, port, autoClose));
		}
		
		@Override
		public Socket createSocket(String host, int port) throws IOException
		{
			return wrap(mWrapped.createSocket(host, port));
		}
		
		@Override
		public Socket createSocket(InetAddress address, int port) throws IOException
		{
			return wrap(mWrapped.createSocket(address, port));
		}
		
		@Override
		public Socket createSocket(String host, int port, InetAddress localAddress, int localPort) throws IOException
		{
			return wrap(mWrapped.createSocket(host, port, localAddress, localPort));
		}
		
		@Override
		public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
				throws IOException
		{
			return wrap(mWrapped.createSocket(address, port, localAddress, localPort));
		}
	}
	
	private static class NoSSLv3SSLSocket extends SSLSocketWrapper
	{
		public NoSSLv3SSLSocket(SSLSocket socket)
		{
			super(socket);
			try
			{
				socket.getClass().getMethod("setUseSessionTickets", boolean.class).invoke(socket, true);
			}
			catch (Exception e)
			{
				
			}
		}
		
		@Override
		public void setEnabledProtocols(String[] protocols)
		{
			if (protocols != null && protocols.length == 1 && "SSLv3".equals(protocols[0]))
			{
				ArrayList<String> enabledProtocols = new ArrayList<>();
				Collections.addAll(enabledProtocols, getEnabledProtocols());
				if (enabledProtocols.size() > 1) enabledProtocols.remove("SSLv3");
				protocols = CommonUtils.toArray(enabledProtocols, String.class);
			}
			super.setEnabledProtocols(protocols);
		}
	}
}