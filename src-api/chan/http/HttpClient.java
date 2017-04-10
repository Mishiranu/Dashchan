/*
 * Copyright 2014-2017 Fukurou Mishiranu
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
import java.io.UnsupportedEncodingException;
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
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLProtocolException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

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
import com.mishiranu.dashchan.content.MainApplication;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.net.CloudFlarePasser;
import com.mishiranu.dashchan.preference.AdvancedPreferences;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.Log;

public class HttpClient {
	private static final int MAX_ATTEMPS_COUNT = 10;

	private static final HashMap<String, String> SHORT_RESPONSE_MESSAGES = new HashMap<>();

	private static final HostnameVerifier DEFAULT_HOSTNAME_VERIFIER = HttpsURLConnection.getDefaultHostnameVerifier();
	private static final HostnameVerifier UNSAFE_HOSTNAME_VERIFIER = (hostname, session) -> true;

	private static final SSLSocketFactory DEFAULT_SSL_SOCKET_FACTORY = HttpsURLConnection.getDefaultSSLSocketFactory();
	private static final SSLSocketFactory UNSAFE_SSL_SOCKET_FACTORY;

	static final int HTTP_TEMPORARY_REDIRECT = 307;

	static {
		int poolSize = (ChanManager.getInstance().getAllChanNames().size() + 1) * 2;
		System.setProperty("http.maxConnections", Integer.toString(poolSize));
		try {
			// http.maxConnections may do nothing because ConnectionPool inits earlier. Android bug?
			Object connectionPool = Class.forName("com.android.okhttp.ConnectionPool").getMethod("getDefault")
					.invoke(null);
			Field maxIdleConnectionsField = connectionPool.getClass().getDeclaredField("maxIdleConnections");
			maxIdleConnectionsField.setAccessible(true);
			maxIdleConnectionsField.setInt(connectionPool, poolSize);
		} catch (Exception e) {
			// Reflective operation, ignore exception
		}

		SHORT_RESPONSE_MESSAGES.put("Internal Server Error", "Internal Error");
		SHORT_RESPONSE_MESSAGES.put("Service Temporarily Unavailable", "Service Unavailable");

		@SuppressLint("TrustAllX509TrustManager")
		X509TrustManager trustManager = new X509TrustManager() {
			@Override
			public void checkClientTrusted(X509Certificate[] chain, String authType) {}

			@Override
			public void checkServerTrusted(X509Certificate[] chain, String authType) {}

			@Override
			public X509Certificate[] getAcceptedIssuers() {
				return null;
			}
		};
		SSLSocketFactory sslSocketFactory;
		try {
			SSLContext sslContext = SSLContext.getInstance("TLS");
			sslContext.init(null, new X509TrustManager[] {trustManager}, null);
			sslSocketFactory = sslContext.getSocketFactory();
		} catch (Exception e) {
			sslSocketFactory = DEFAULT_SSL_SOCKET_FACTORY;
		}
		UNSAFE_SSL_SOCKET_FACTORY = sslSocketFactory;

		if (Preferences.isUseGmsProvider()) {
			try {
				// Load GmsCore_OpenSSL from Google Play Services package
				Context context = MainApplication.getInstance().createPackageContext("com.google.android.gms",
						Context.CONTEXT_IGNORE_SECURITY | Context.CONTEXT_INCLUDE_CODE);
				Class<?> providerInstallerImplClass = Class.forName("com.google.android.gms.common.security"
						+ ".ProviderInstallerImpl", false, context.getClassLoader());
				Method insertProviderMethod = providerInstallerImplClass.getMethod("insertProvider", Context.class);
				insertProviderMethod.invoke(null, context);
			} catch (Exception e) {
				// Reflective operation, ignore exception
			}
		}

		/*
		 * MediaPlayer uses MediaHTTPConnection that uses its own CookieHandler instance.
		 * This cause some bugs in application work.
		 *
		 * This CookieHandler doesn't allow app to store cookies when chan HttpClient used.
		 */
		CookieHandler.setDefault(new CookieHandler() {
			private final CookieManager cookieManager = new CookieManager();

			private boolean isInternalRequest() {
				StackTraceElement[] elements = Thread.currentThread().getStackTrace();
				for (StackTraceElement element : elements) {
					if (HttpClient.class.getName().equals(element.getClassName())) {
						return true;
					}
				}
				return false;
			}

			@Override
			public void put(URI uri, Map<String, List<String>> responseHeaders) throws IOException {
				if (isInternalRequest()) {
					return;
				}
				cookieManager.put(uri, responseHeaders);
			}

			@Override
			public Map<String, List<String>> get(URI uri, Map<String, List<String>> requestHeaders) throws IOException {
				if (isInternalRequest()) {
					return Collections.emptyMap();
				}
				return cookieManager.get(uri, requestHeaders);
			}
		});
	}

	private static final HttpClient INSTANCE = new HttpClient();

	public static HttpClient getInstance() {
		return INSTANCE;
	}

	private final HashMap<String, Proxy> proxies = new HashMap<>();
	private boolean useNoSSLv3SSLSocketFactory = false;

	private HttpClient() {
		for (String chanName : ChanManager.getInstance().getAllChanNames()) {
			Proxy proxy;
			try {
				proxy = initProxy(chanName, false);
			} catch (Exception e) {
				// Impossible with throwIfNotValid == false
				throw new RuntimeException(e);
			}
			if (proxy != null) {
				proxies.put(chanName, proxy);
			}
		}
	}

	private Proxy initProxy(String chanName, boolean throwIfNotValid) throws Exception {
		Proxy proxy = null;
		String[] proxyData = Preferences.getProxy(chanName);
		if (proxyData != null && proxyData[0] != null && proxyData[1] != null) {
			boolean socks = Preferences.VALUE_PROXY_2_SOCKS.equals(proxyData[2]);
			try {
				proxy = new Proxy(socks ? Proxy.Type.SOCKS : Proxy.Type.HTTP, InetSocketAddress
						.createUnresolved(proxyData[0], Integer.parseInt(proxyData[1])));
			} catch (Exception e) {
				if (throwIfNotValid) {
					throw e;
				}
			}
		}
		return proxy;
	}

	public boolean updateProxy(String chanName) {
		Proxy proxy;
		try {
			proxy = initProxy(chanName, true);
		} catch (Exception e) {
			return false;
		}
		if (proxy != null) {
			proxies.put(chanName, proxy);
		} else {
			proxies.remove(chanName);
		}
		return true;
	}

	static final class DisconnectedIOException extends IOException {
		private static final long serialVersionUID = 1L;
	}

	HostnameVerifier getHostnameVerifier(boolean verifyCertificate) {
		return verifyCertificate ? DEFAULT_HOSTNAME_VERIFIER : UNSAFE_HOSTNAME_VERIFIER;
	}

	SSLSocketFactory getSSLSocketFactory(boolean verifyCertificate) {
		return verifyCertificate ? DEFAULT_SSL_SOCKET_FACTORY : UNSAFE_SSL_SOCKET_FACTORY;
	}

	void execute(HttpRequest request) throws HttpException {
		String chanName = ChanManager.getInstance().getChanNameByHost(request.uri.getAuthority());
		ChanLocator locator = ChanLocator.get(chanName);
		boolean verifyCertificate = locator.isUseHttps() && Preferences.isVerifyCertificate();
		request.holder.initRequest(request.uri, proxies.get(chanName), chanName, verifyCertificate, request.delay,
				MAX_ATTEMPS_COUNT);
		executeInternal(request);
	}

	private void encodeUriBufferPart(StringBuilder uriStringBuilder, char[] chars, int i, int start, boolean ascii) {
		if (!ascii) {
			try {
				for (byte b : new String(chars, start, i - start).getBytes("UTF-8")) {
					String s = Integer.toString(b & 0xff, 16).toUpperCase(Locale.US);
					uriStringBuilder.append('%');
					uriStringBuilder.append(s);
				}
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException(e);
			}
		} else {
			uriStringBuilder.append(chars, start, i - start);
		}
	}

	private void encodeUriAppend(StringBuilder uriStringBuilder, String part) {
		char[] chars = part.toCharArray();
		boolean ascii = true;
		int start = 0;
		for (int i = 0; i < chars.length; i++) {
			char c = chars[i];
			boolean ita = c < 0x80;
			if (ita != ascii) {
				encodeUriBufferPart(uriStringBuilder, chars, i, start, ascii);
				start = i;
				ascii = ita;
			}
		}
		encodeUriBufferPart(uriStringBuilder, chars, chars.length, start, ascii);
	}

	URL encodeUri(Uri uri) throws MalformedURLException {
		StringBuilder uriStringBuilder = new StringBuilder();
		uriStringBuilder.append(uri.getScheme()).append("://");
		String host = IDN.toASCII(uri.getHost());
		uriStringBuilder.append(host);
		int port = uri.getPort();
		if (port != -1) {
			uriStringBuilder.append(':').append(port);
		}
		String path = uri.getEncodedPath();
		if (!StringUtils.isEmpty(path)) {
			encodeUriAppend(uriStringBuilder, path);
		}
		String query = uri.getEncodedQuery();
		if (!StringUtils.isEmpty(query)) {
			uriStringBuilder.append('?');
			encodeUriAppend(uriStringBuilder, query);
		}
		return new URL(uriStringBuilder.toString());
	}

	@TargetApi(Build.VERSION_CODES.KITKAT)
	private void executeInternal(HttpRequest request) throws HttpException {
		HttpHolder holder = request.holder;
		holder.cleanup();
		try {
			Uri requestedUri = request.holder.requestedUri;
			if (!ChanLocator.getDefault().isWebScheme(requestedUri)) {
				throw new HttpException(ErrorItem.TYPE_UNSUPPORTED_SCHEME, false, false);
			}
			URL url = encodeUri(requestedUri);
			HttpURLConnection connection = (HttpURLConnection) (holder.proxy != null
					? url.openConnection(holder.proxy) : url.openConnection());
			if (connection instanceof HttpsURLConnection) {
				HttpsURLConnection secureConnection = (HttpsURLConnection) connection;
				secureConnection.setHostnameVerifier(getHostnameVerifier(holder.verifyCertificate));
				secureConnection.setSSLSocketFactory(getSSLSocketFactory(holder.verifyCertificate));
			}
			try {
				holder.setConnection(connection, request.inputListener, request.outputStream);
			} catch (DisconnectedIOException e) {
				connection.disconnect();
				throw e;
			}
			String chanName = holder.chanName;

			connection.setUseCaches(false);
			connection.setConnectTimeout(request.connectTimeout);
			connection.setReadTimeout(request.readTimeout);
			connection.setInstanceFollowRedirects(false);
			connection.setRequestProperty("Connection", request.keepAlive ? "keep-alive" : "close");
			boolean userAgentSet = false;
			boolean acceptEncodingSet = false;
			if (request.headers != null) {
				for (Pair<String, String> header : request.headers) {
					if ("Connection".equalsIgnoreCase(header.first)) {
						continue;
					}
					connection.setRequestProperty(header.first, header.second);
					if ("User-Agent".equalsIgnoreCase(header.first)) {
						userAgentSet = true;
					}
					if ("Accept-Encoding".equalsIgnoreCase(header.first)) {
						acceptEncodingSet = true;
					}
				}
			}
			if (!userAgentSet) {
				connection.setRequestProperty("User-Agent", AdvancedPreferences.getUserAgent(chanName));
			}
			if (!acceptEncodingSet) {
				connection.setRequestProperty("Accept-Encoding", "gzip");
			}
			CookieBuilder cookieBuilder = obtainModifiedCookieBuilder(request.cookieBuilder, chanName);
			if (cookieBuilder != null) {
				connection.setRequestProperty("Cookie", cookieBuilder.build());
			}
			HttpValidator validator = request.validator;
			if (validator != null) {
				validator.write(connection);
			}

			boolean forceGet = holder.forceGet;
			int method = forceGet ? HttpRequest.REQUEST_METHOD_GET : request.requestMethod;
			RequestEntity entity = forceGet ? null : request.requestEntity;
			String methodString;
			switch (method) {
				case HttpRequest.REQUEST_METHOD_GET: {
					methodString = "GET";
					break;
				}
				case HttpRequest.REQUEST_METHOD_HEAD: {
					methodString = "HEAD";
					break;
				}
				case HttpRequest.REQUEST_METHOD_POST: {
					methodString = "POST";
					break;
				}
				case HttpRequest.REQUEST_METHOD_PUT: {
					methodString = "PUT";
					break;
				}
				case HttpRequest.REQUEST_METHOD_DELETE: {
					methodString = "DELETE";
					break;
				}
				default: {
					throw new RuntimeException();
				}
			}
			connection.setRequestMethod(methodString);
			if (entity != null) {
				connection.setDoOutput(true);
				connection.setRequestProperty("Content-Type", entity.getContentType());
				long contentLength = entity.getContentLength();
				if (contentLength > 0) {
					if (C.API_KITKAT) {
						connection.setFixedLengthStreamingMode(contentLength);
					} else {
						connection.setFixedLengthStreamingMode((int) contentLength);
					}
				}
				ClientOutputStream output = new ClientOutputStream(new BufferedOutputStream(connection
						.getOutputStream(), 1024), holder, forceGet ? null : request.outputListener, contentLength);
				entity.write(output);
				output.flush();
				output.close();
				holder.checkDisconnected();
			}
			int responseCode = connection.getResponseCode();

			if (chanName != null && request.checkCloudFlare) {
				CloudFlarePasser.Result result = CloudFlarePasser.checkResponse(chanName, requestedUri, holder);
				if (result.success) {
					// TODO Handle possible connection replacement
					if (holder.nextAttempt()) {
						executeInternal(request);
						return;
					} else {
						holder.disconnectAndClear();
						throw new HttpException(responseCode, holder.getResponseMessage());
					}
				}
			}

			HttpRequest.RedirectHandler redirectHandler = request.redirectHandler;
			switch (responseCode) {
				case HttpURLConnection.HTTP_MOVED_PERM:
				case HttpURLConnection.HTTP_MOVED_TEMP:
				case HttpURLConnection.HTTP_SEE_OTHER:
				case HTTP_TEMPORARY_REDIRECT: {
					boolean oldHttps = connection instanceof HttpsURLConnection;
					Uri redirectedUri = obtainRedirectedUri(requestedUri, connection.getHeaderField("Location"));
					holder.redirectedUri = redirectedUri;
					HttpRequest.RedirectHandler.Action action;
					Uri overriddenRedirectedUri;
					try {
						synchronized (HttpRequest.RedirectHandler.class) {
							HttpRequest.RedirectHandler.Action.resetAll();
							action = redirectHandler.onRedirectReached(responseCode, requestedUri, redirectedUri,
									holder);
							overriddenRedirectedUri = action.getRedirectedUri();
						}
					} catch (HttpException e) {
						holder.disconnectAndClear();
						throw e;
					}
					if (overriddenRedirectedUri != null) {
						redirectedUri = overriddenRedirectedUri;
					}
					if (action == HttpRequest.RedirectHandler.Action.GET ||
							action == HttpRequest.RedirectHandler.Action.RETRANSMIT) {
						holder.disconnectAndClear();
						boolean newHttps = "https".equals(redirectedUri.getScheme());
						if (holder.verifyCertificate && oldHttps && !newHttps) {
							// Redirect from https to http is unsafe
							throw new HttpException(ErrorItem.TYPE_UNSAFE_REDIRECT, true, false);
						}
						if (action == HttpRequest.RedirectHandler.Action.GET) {
							holder.forceGet = true;
						}
						holder.requestedUri = redirectedUri;
						holder.redirectedUri = null;
						if (holder.nextAttempt()) {
							executeInternal(request);
							return;
						} else {
							holder.disconnectAndClear();
							throw new HttpException(responseCode, holder.getResponseMessage());
						}
					}
				}
			}

			if (validator != null && responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
				String responseMessage = connection.getResponseMessage();
				holder.disconnectAndClear();
				throw new HttpException(responseCode, responseMessage);
			}
			if (request.successOnly) {
				checkResponseCode(holder);
			}
			holder.validator = HttpValidator.obtain(connection);
			holder.checkDisconnectedAndSetHasUnreadBody(true);
		} catch (DisconnectedIOException e) {
			holder.disconnectAndClear();
			throw new HttpException(0, false, false, e);
		} catch (IOException e) {
			if (isConnectionReset(e)) {
				// Sometimes server closes the socket, but client is still trying to use it
				if (holder.nextAttempt()) {
					Log.persistent().stack(e);
					executeInternal(request);
					return;
				}
			}
			if (e.getCause() instanceof SSLProtocolException) {
				String message = e.getMessage();
				if (message != null && message.contains("routines:SSL23_GET_SERVER_HELLO:sslv3")) {
					synchronized (this) {
						if (!useNoSSLv3SSLSocketFactory) {
							// Fix https://code.google.com/p/android/issues/detail?id=78187
							HttpsURLConnection.setDefaultSSLSocketFactory(new NoSSLv3SSLSocketFactory
									(HttpsURLConnection.getDefaultSSLSocketFactory()));
							useNoSSLv3SSLSocketFactory = true;
						}
					}
					if (holder.nextAttempt()) {
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

	HttpResponse read(HttpHolder holder) throws HttpException {
		try {
			HttpURLConnection connection = holder.getConnection();
			holder.checkDisconnected();
			InputStream commonInput;
			try {
				commonInput = connection.getInputStream();
			} catch (FileNotFoundException e) {
				commonInput = connection.getErrorStream();
			}
			commonInput = new BufferedInputStream(commonInput, 4096);
			String encoding = connection.getContentEncoding();
			int contentLength = connection.getContentLength();
			if ("gzip".equals(encoding)) {
				commonInput = new GZIPInputStream(commonInput);
				contentLength = -1;
			}
			OutputStream output = holder.outputStream;
			ClientInputStream input = new ClientInputStream(commonInput, holder, holder.inputListener, contentLength);
			ByteArrayOutputStream writeTo = output == null ? new ByteArrayOutputStream() : null;
			if (output == null) {
				output = writeTo;
			}
			try {
				IOUtils.copyStream(input, output);
			} finally {
				IOUtils.close(input);
				IOUtils.close(output);
			}
			String contentType = connection.getHeaderField("Content-Type");
			String charsetName = null;
			if (contentType != null) {
				int index = contentType.indexOf("charset=");
				if (index >= 0) {
					int end = contentType.indexOf(';', index);
					charsetName = contentType.substring(index + 8, end >= 0 ? end : contentType.length());
					try {
						Charset.forName(charsetName);
					} catch (UnsupportedCharsetException e) {
						charsetName = null;
					}
				}
			}
			holder.checkDisconnectedAndSetHasUnreadBody(false);
			if (writeTo != null) {
				HttpResponse httpResponse = new HttpResponse(writeTo.toByteArray());
				if (charsetName != null) {
					httpResponse.setEncoding(charsetName);
				}
				return httpResponse;
			} else {
				return null;
			}
		} catch (DisconnectedIOException e) {
			throw new HttpException(0, false, false, e);
		} catch (IOException e) {
			checkExceptionAndThrow(e);
			throw new HttpException(ErrorItem.TYPE_DOWNLOAD, false, true, e);
		} finally {
			holder.disconnectAndClear();
		}
	}

	CookieBuilder obtainModifiedCookieBuilder(CookieBuilder cookieBuilder, String chanName) {
		String cloudFlareCookie = CloudFlarePasser.getCookie(chanName);
		if (cloudFlareCookie != null) {
			cookieBuilder = new CookieBuilder(cookieBuilder).append(CloudFlarePasser.COOKIE_CLOUDFLARE,
					cloudFlareCookie);
		}
		return cookieBuilder;
	}

	Uri obtainRedirectedUri(Uri requestedUri, String locationHeader) {
		Uri redirectedUri;
		if (!StringUtils.isEmpty(locationHeader)) {
			redirectedUri = Uri.parse(locationHeader);
			if (redirectedUri.isRelative()) {
				Uri.Builder builder = redirectedUri.buildUpon().scheme(requestedUri.getScheme())
						.authority(requestedUri.getAuthority());
				String redirectedPath = StringUtils.emptyIfNull(redirectedUri.getPath());
				if (!redirectedPath.isEmpty() && !redirectedPath.startsWith("/")) {
					String path = StringUtils.emptyIfNull(requestedUri.getPath());
					if (!path.endsWith("/")) {
						int index = path.lastIndexOf('/');
						if (index >= 0) {
							path = path.substring(0, index + 1);
						} else {
							path = "/";
						}
					}
					path += redirectedPath;
					builder.path(path);
				}
				redirectedUri = builder.build();
			}
		} else {
			redirectedUri = requestedUri;
		}
		return redirectedUri;
	}

	void checkResponseCode(HttpHolder holder) throws HttpException {
		int responseCode = holder.getResponseCode();
		boolean success = responseCode >= HttpURLConnection.HTTP_OK && responseCode <= HttpURLConnection.HTTP_SEE_OTHER
				|| responseCode == HTTP_TEMPORARY_REDIRECT;
		if (!success) {
			String originalMessage = holder.getResponseMessage();
			String message = SHORT_RESPONSE_MESSAGES.get(originalMessage);
			if (message == null) {
				message = originalMessage;
			}
			holder.disconnectAndClear();
			throw new HttpException(responseCode, message);
		}
	}

	void checkExceptionAndThrow(IOException exception) throws HttpException {
		Log.persistent().stack(exception);
		int errorType = getErrorTypeForException(exception);
		if (errorType != 0) {
			throw new HttpException(errorType, false, true, exception);
		}
	}

	private int getErrorTypeForException(IOException exception) {
		if (isConnectionReset(exception)) {
			return ErrorItem.TYPE_CONNECTION_RESET;
		}
		String message = exception.getMessage();
		if (message != null) {
			if (message.contains("failed to connect to") && message.contains("ETIMEDOUT")) {
				return ErrorItem.TYPE_CONNECT_TIMEOUT;
			}
			if (message.contains("SSL handshake timed out")) {
				// SocketTimeoutException
				// Throws when connection was established but SSL handshake was timed out
				return ErrorItem.TYPE_CONNECT_TIMEOUT;
			}
			if (message.matches("Hostname .+ (was )?not verified")) {
				// IOException
				// Throws when hostname not matches certificate
				return ErrorItem.TYPE_INVALID_CERTIFICATE;
			}
			if (message.contains("Could not validate certificate") ||
					message.contains("Trust anchor for certification path not found")) {
				// SSLHandshakeException
				// Throws when certificate expired or not yet valid
				return ErrorItem.TYPE_INVALID_CERTIFICATE;
			}
		}
		if (exception instanceof SSLException) {
			return ErrorItem.TYPE_SSL;
		}
		if (exception instanceof SocketTimeoutException) {
			return ErrorItem.TYPE_READ_TIMEOUT;
		}
		return 0;
	}

	private boolean isConnectionReset(IOException exception) {
		if (exception instanceof EOFException) {
			return true;
		}
		String message = exception.getMessage();
		return message != null && (message.contains("Connection reset by peer")
				|| message.contains("Connection closed by peer") || message.contains("unexpected end of stream")
				|| message.contains("Connection refused"));
	}

	public static InputStream wrapWithProgressListener(InputStream input, HttpHolder.InputListener listener,
			long contentLength) {
		return new ClientInputStream(input, new HttpHolder(), listener, contentLength);
	}

	private static class ClientInputStream extends InputStream {
		private final InputStream input;
		private final HttpHolder holder;

		private final HttpHolder.InputListener listener;
		private final long contentLength;

		private volatile long progress;

		public ClientInputStream(InputStream input, HttpHolder holder,
				HttpHolder.InputListener listener, long contentLength) {
			this.input = input;
			this.holder = holder;
			this.listener = contentLength > 0 ? listener : null;
			this.contentLength = contentLength;
			if (this.listener != null) {
				this.listener.onInputProgressChange(0, contentLength);
			}
		}

		@Override
		public int read() throws IOException {
			holder.checkDisconnected(this);
			int value = input.read();
			updateProgress(value);
			return value;
		}

		@Override
		public int read(byte[] b) throws IOException {
			return read(b, 0, b.length);
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			holder.checkDisconnected(this);
			int value = input.read(b, off, len);
			updateProgress(value);
			return value;
		}

		@Override
		public long skip(long n) throws IOException {
			holder.checkDisconnected(this);
			long value = super.skip(n);
			updateProgress(value);
			return value;
		}

		private void updateProgress(long value) {
			if (listener != null && value > 0) {
				progress += value;
				listener.onInputProgressChange(progress, contentLength);
			}
		}

		@Override
		public int available() throws IOException {
			holder.checkDisconnected(this);
			return input.available();
		}

		@Override
		public void close() throws IOException {
			input.close();
		}

		@Override
		public void mark(int readlimit) {
			input.mark(readlimit);
		}

		@Override
		public boolean markSupported() {
			return input.markSupported();
		}

		@Override
		public synchronized void reset() throws IOException {
			input.reset();
		}
	}

	private static class ClientOutputStream extends OutputStream {
		private final OutputStream output;
		private final HttpHolder holder;

		private final HttpRequest.OutputListener listener;
		private final long contentLength;

		private volatile long progress;

		public ClientOutputStream(OutputStream output, HttpHolder holder, HttpRequest.OutputListener listener,
				long contentLength) {
			this.output = output;
			this.holder = holder;
			this.listener = contentLength > 0 ? listener : null;
			this.contentLength = contentLength;
			if (this.listener != null) {
				this.listener.onOutputProgressChange(0, contentLength);
			}
		}

		@Override
		public void write(int oneByte) throws IOException {
			holder.checkDisconnected(this);
			output.write(oneByte);
			updateProgress(1);
		}

		@Override
		public void write(byte[] buffer) throws IOException {
			holder.checkDisconnected(this);
			output.write(buffer);
			updateProgress(buffer.length);
		}

		@Override
		public void write(byte[] buffer, int offset, int length) throws IOException {
			holder.checkDisconnected(this);
			output.write(buffer, offset, length);
			updateProgress(length);
		}

		private void updateProgress(long value) {
			if (listener != null && value > 0) {
				progress += value;
				listener.onOutputProgressChange(progress, contentLength);
			}
		}

		@Override
		public void close() throws IOException {
			output.close();
		}

		@Override
		public void flush() throws IOException {
			holder.checkDisconnected(this);
			output.flush();
		}
	}

	private final HashMap<Object, HttpURLConnection> singleConnections = new HashMap<>();
	private final HashMap<HttpURLConnection, Object> singleConnectionIdetifiers = new HashMap<>();

	private final HashMap<String, AtomicBoolean> delayLocks = new HashMap<>();

	// Called from HttpHolder
	void onConnect(String chanName, HttpURLConnection connection, int delay) throws DisconnectedIOException {
		if (AdvancedPreferences.isSingleConnection(chanName)) {
			synchronized (singleConnections) {
				while (singleConnections.containsKey(chanName)) {
					try {
						singleConnections.wait();
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						throw new DisconnectedIOException();
					}
				}
				singleConnections.put(chanName, connection);
				singleConnectionIdetifiers.put(connection, chanName);
			}
		}
		if (delay > 0) {
			URL url = connection.getURL();
			String key = url.getAuthority();
			AtomicBoolean delayLock;
			synchronized (delayLocks) {
				delayLock = delayLocks.get(key);
				if (delayLock == null) {
					delayLock = new AtomicBoolean(false);
					delayLocks.put(key, delayLock);
				}
			}
			synchronized (delayLock) {
				try {
					while (delayLock.get()) {
						delayLock.wait();
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
				delayLock.set(true);
				try {
					Thread.sleep(delay);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} finally {
					delayLock.set(false);
					delayLock.notifyAll();
				}
			}
		}
	}

	// Called from HttpHolder
	void onDisconnect(HttpURLConnection connection) {
		synchronized (singleConnections) {
			Object identifier = singleConnectionIdetifiers.remove(connection);
			if (identifier != null) {
				if (connection == singleConnections.get(identifier)) {
					singleConnections.remove(identifier);
					singleConnections.notifyAll();
				}
			}
		}
	}

	private static class NoSSLv3SSLSocketFactory extends SSLSocketFactory {
		private final SSLSocketFactory wrapped;

		public NoSSLv3SSLSocketFactory(SSLSocketFactory sslSocketFactory) {
			wrapped = sslSocketFactory;
		}

		private Socket wrap(Socket socket) {
			if (socket instanceof SSLSocket) {
				socket = new NoSSLv3SSLSocket((SSLSocket) socket);
			}
			return socket;
		}

		@Override
		public String[] getDefaultCipherSuites() {
			return wrapped.getDefaultCipherSuites();
		}

		@Override
		public String[] getSupportedCipherSuites() {
			return wrapped.getSupportedCipherSuites();
		}

		@Override
		public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
			return wrap(wrapped.createSocket(s, host, port, autoClose));
		}

		@Override
		public Socket createSocket(String host, int port) throws IOException {
			return wrap(wrapped.createSocket(host, port));
		}

		@Override
		public Socket createSocket(InetAddress address, int port) throws IOException {
			return wrap(wrapped.createSocket(address, port));
		}

		@Override
		public Socket createSocket(String host, int port, InetAddress localAddress, int localPort) throws IOException {
			return wrap(wrapped.createSocket(host, port, localAddress, localPort));
		}

		@Override
		public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
				throws IOException {
			return wrap(wrapped.createSocket(address, port, localAddress, localPort));
		}
	}

	private static class NoSSLv3SSLSocket extends SSLSocketWrapper {
		public NoSSLv3SSLSocket(SSLSocket socket) {
			super(socket);
			try {
				socket.getClass().getMethod("setUseSessionTickets", boolean.class).invoke(socket, true);
			} catch (Exception e) {
				// Reflective operation, ignore exception
			}
		}

		@Override
		public void setEnabledProtocols(String[] protocols) {
			if (protocols != null && protocols.length == 1 && "SSLv3".equals(protocols[0])) {
				ArrayList<String> enabledProtocols = new ArrayList<>();
				Collections.addAll(enabledProtocols, getEnabledProtocols());
				if (enabledProtocols.size() > 1) {
					enabledProtocols.remove("SSLv3");
				}
				protocols = CommonUtils.toArray(enabledProtocols, String.class);
			}
			super.setEnabledProtocols(protocols);
		}
	}
}