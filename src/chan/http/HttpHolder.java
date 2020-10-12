package chan.http;

import android.net.Uri;
import chan.annotation.Public;
import com.mishiranu.dashchan.util.IOUtils;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.util.List;
import java.util.Map;

@Public
public final class HttpHolder implements Closeable {
	Uri requestedUri;
	Proxy proxy;
	String chanName;
	boolean verifyCertificate;
	int delay;

	private int attempt;
	boolean forceGet = false;
	boolean executed = false;

	@Public
	public HttpHolder() {}

	void initRequest(Uri uri, Proxy proxy, String chanName, boolean verifyCertificate, int delay, int maxAttempts) {
		requestedUri = uri;
		this.proxy = proxy;
		this.chanName = chanName;
		this.verifyCertificate = verifyCertificate;
		this.delay = delay;
		attempt = maxAttempts;
		forceGet = false;
	}

	boolean nextAttempt() {
		return attempt-- > 0;
	}

	Uri redirectedUri;
	HttpValidator validator;
	private HttpResponse response;

	private volatile Thread requestThread;
	private volatile HttpURLConnection connection;
	private volatile HttpURLConnection deadConnection;
	private volatile Callback callback;
	private volatile boolean disconnectRequested = false;
	private volatile boolean interrupted = false;

	InputListener inputListener;
	OutputStream outputStream;

	public interface InputListener {
		void onInputProgressChange(long progress, long progressMax);
	}

	public interface Callback {
		void onDisconnectRequested();
	}

	public void interrupt() {
		interrupted = true;
		disconnect();
	}

	@Override
	public void close() {
		if (requestThread == Thread.currentThread() && executed) {
			disconnectAndClear();
			response = null;
		}
	}

	@Public
	public void disconnect() {
		disconnectRequested = true;
		if (requestThread == Thread.currentThread()) {
			disconnectAndClear();
		}
		response = null;
	}

	void setConnection(HttpURLConnection connection, Callback callback, boolean notifyClient,
			InputListener inputListener, OutputStream outputStream) throws HttpClient.DisconnectedIOException {
		disconnectRequested = false;
		requestThread = Thread.currentThread();
		this.connection = connection;
		this.callback = callback;
		this.inputListener = inputListener;
		this.outputStream = outputStream;
		redirectedUri = null;
		validator = null;
		response = null;
		if (interrupted) {
			this.connection = null;
			this.callback = null;
			this.inputListener = null;
			this.outputStream = null;
			throw new HttpClient.DisconnectedIOException();
		}
		if (notifyClient) {
			HttpClient.getInstance().onConnect(chanName, connection, delay);
		}
	}

	void setConnection(HttpURLConnection connection, InputListener inputListener, OutputStream outputStream)
			throws HttpClient.DisconnectedIOException {
		setConnection(connection, null, true, inputListener, outputStream);
	}

	void setCallback(Callback callback) throws HttpClient.DisconnectedIOException {
		setConnection(null, callback, false, null, null);
	}

	HttpURLConnection getConnection() throws HttpClient.DisconnectedIOException {
		HttpURLConnection connection = this.connection;
		if (connection == null) {
			throw new HttpClient.DisconnectedIOException();
		}
		return connection;
	}

	void checkDisconnected() throws HttpClient.DisconnectedIOException {
		checkDisconnected(null);
	}

	void checkDisconnected(Closeable closeable) throws HttpClient.DisconnectedIOException {
		if (disconnectRequested) {
			IOUtils.close(closeable);
			throw new HttpClient.DisconnectedIOException();
		}
	}

	void disconnectAndClear() {
		HttpURLConnection connection = this.connection;
		this.connection = null;
		Callback callback = this.callback;
		this.callback = null;
		inputListener = null;
		outputStream = null;
		executed = false;
		if (connection != null) {
			connection.disconnect();
			deadConnection = connection;
			HttpClient.getInstance().onDisconnect(connection);
		}
		if (callback != null) {
			callback.onDisconnectRequested();
		}
	}

	@Public
	public HttpResponse read() throws HttpException {
		return read(false);
	}

	public HttpResponse readDirect() throws HttpException {
		return read(true);
	}

	private HttpResponse read(boolean direct) throws HttpException {
		HttpResponse response = this.response;
		if (response != null) {
			return response;
		}
		response = HttpClient.getInstance().read(this, direct);
		this.response = response;
		return response;
	}

	@Public
	public void checkResponseCode() throws HttpException {
		HttpClient.getInstance().checkResponseCode(this);
	}

	private HttpURLConnection getConnectionForHeaders() {
		HttpURLConnection connection = this.connection;
		if (connection == null) {
			connection = deadConnection;
		}
		return connection;
	}

	@Public
	public int getResponseCode() {
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

	@Public
	public String getResponseMessage() {
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

	@Public
	public Uri getRedirectedUri() {
		return redirectedUri;
	}

	@Public
	public Map<String, List<String>> getHeaderFields() {
		HttpURLConnection connection = getConnectionForHeaders();
		return connection != null ? connection.getHeaderFields() : null;
	}

	@Public
	public String getCookieValue(String name) {
		Map<String, List<String>> headers = getHeaderFields();
		if (headers == null) {
			return null;
		}
		String start = name + "=";
		List<String> cookies = headers.get("Set-Cookie");
		if (cookies != null) {
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

	@Public
	public HttpValidator getValidator() {
		return validator;
	}
}
