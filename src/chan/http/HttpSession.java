package chan.http;

import android.net.Uri;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class HttpSession {
	private final HttpHolder holder;
	final HttpClient client;
	HttpResponse response;

	final Proxy proxy;
	final boolean verifyCertificate;
	final int delay;

	Uri requestedUri;
	Uri redirectedUri;

	int attempt;
	boolean forceGet;
	boolean executing;

	HttpURLConnection connection;
	HttpURLConnection deadConnection;
	HttpHolder.Callback callback;

	HttpSession(HttpHolder holder, HttpClient client, Proxy proxy, boolean verifyCertificate, int delay) {
		this.holder = holder;
		this.client = client;
		this.proxy = proxy;
		this.verifyCertificate = verifyCertificate;
		this.delay = delay;
	}

	private boolean isInterrupted() {
		return holder.interrupted;
	}

	void checkInterrupted() throws HttpClient.InterruptedHttpException {
		if (isInterrupted()) {
			throw new HttpClient.InterruptedHttpException();
		}
	}

	void checkThread() {
		holder.checkThread();
	}

	void checkExecuting() {
		if (executing) {
			throw new IllegalStateException("Can't perform requests during active execute process");
		}
	}

	private void setConnection(HttpURLConnection connection, HttpHolder.Callback callback)
			throws HttpClient.InterruptedHttpException {
		checkThread();
		this.connection = connection;
		this.callback = callback;
		redirectedUri = null;
		if (isInterrupted()) {
			this.connection = null;
			this.callback = null;
			throw new HttpClient.InterruptedHttpException();
		}
		if (connection != null) {
			client.onConnect(holder.chan, connection, delay);
		}
	}

	void setConnection(HttpURLConnection connection) throws HttpClient.InterruptedHttpException {
		checkThread();
		setConnection(connection, null);
	}

	void setCallback(HttpHolder.Callback callback) throws HttpClient.InterruptedHttpException {
		checkThread();
		setConnection(null, callback);
	}

	boolean nextAttempt() {
		checkThread();
		return attempt-- > 0;
	}

	void disconnectAndClear() {
		checkThread();
		HttpURLConnection connection = this.connection;
		this.connection = null;
		HttpHolder.Callback callback = this.callback;
		this.callback = null;
		if (response != null) {
			// HttpResponse will call disconnectAndClear if connection != null
			response.cleanupAndDisconnect();
		}
		if (connection != null) {
			try {
				connection.disconnect();
			} finally {
				deadConnection = connection;
				client.onDisconnect(connection);
			}
		}
		if (callback != null) {
			callback.onDisconnectRequested();
		}
	}

	void checkResponseCode() throws HttpException {
		checkThread();
		int responseCode = getResponseCode();
		boolean success = responseCode >= HttpURLConnection.HTTP_OK &&
				responseCode <= HttpURLConnection.HTTP_SEE_OTHER
				|| responseCode == HttpClient.HTTP_TEMPORARY_REDIRECT;
		if (!success) {
			String message = HttpClient.transformResponseMessage(getResponseMessage());
			disconnectAndClear();
			throw new HttpException(responseCode, message);
		}
	}

	private HttpURLConnection getConnectionForHeaders() {
		checkThread();
		HttpURLConnection connection = this.connection;
		if (connection == null) {
			connection = deadConnection;
		}
		return connection;
	}

	int getResponseCode() {
		HttpURLConnection connection = getConnectionForHeaders();
		if (connection != null) {
			try {
				return connection.getResponseCode();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		return -1;
	}

	String getResponseMessage() {
		HttpURLConnection connection = getConnectionForHeaders();
		if (connection != null) {
			try {
				return connection.getResponseMessage();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		return null;
	}

	Map<String, List<String>> getHeaderFields() {
		HttpURLConnection connection = getConnectionForHeaders();
		Map<String, List<String>> map = connection != null ? connection.getHeaderFields() : null;
		return map != null ? map : Collections.emptyMap();
	}

	String getCookieValue(String name) {
		Map<String, List<String>> headers = getHeaderFields();
		List<String> cookies = headers.get("Set-Cookie");
		if (cookies != null) {
			String start = name + "=";
			for (String cookie : cookies) {
				if (cookie.startsWith(start)) {
					int startIndex = start.length();
					int endIndex = cookie.indexOf(';');
					if (endIndex >= 0) {
						return cookie.substring(startIndex, endIndex);
					} else {
						return cookie.substring(startIndex);
					}
				}
			}
		}
		return null;
	}

	long getLength() {
		HttpURLConnection connection = getConnectionForHeaders();
		return connection != null && HttpClient.Encoding.get(connection) == HttpClient.Encoding.IDENTITY
				? connection.getContentLength() : -1;
	}
}
