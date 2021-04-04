package chan.http;

import android.net.Uri;
import android.util.Base64;
import android.util.Pair;
import androidx.annotation.NonNull;
import chan.annotation.Extendable;
import chan.annotation.Public;
import chan.content.ExtensionException;
import chan.util.StringUtils;
import com.mishiranu.dashchan.content.AdvancedPreferences;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.util.IOUtils;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.Socket;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Public
public final class WebSocket {
	private final Uri uri;
	private final HttpHolder holder;
	private final HttpClient client;

	private int connectTimeout = 15000;
	private int readTimeout = 15000;

	private ArrayList<Pair<String, String>> headers;
	private CookieBuilder cookieBuilder;

	private InetSocket socket;
	private InputStream inputStream;
	private OutputStream outputStream;
	private volatile boolean closed = false;

	private final LinkedBlockingQueue<ReadFrame> readQueue = new LinkedBlockingQueue<>();
	private final LinkedBlockingQueue<WriteFrame> writeQueue = new LinkedBlockingQueue<>();

	private final HashSet<Object> results = new HashSet<>();
	private volatile boolean cancelResults = false;

	private static final int OPCODE_TEXT = 1;
	private static final int OPCODE_BINARY = 2;
	private static final int OPCODE_CONNECTION_CLOSE = 8;
	private static final int OPCODE_PING = 9;
	private static final int OPCODE_PONG = 10;

	@Public
	public static class Event {
		private final ReadFrame frame;
		private final WebSocket webSocket;

		private Event(ReadFrame frame, WebSocket webSocket) {
			this.frame = frame;
			this.webSocket = webSocket;
		}

		@Public
		public byte[] getData() {
			return frame.data;
		}

		@Public
		public boolean isBinary() {
			return frame.opcode == OPCODE_BINARY;
		}

		@Public
		public void store(String key, Object object) {
			webSocket.store(key, object);
		}

		@Public
		public <T> T get(String key) {
			return webSocket.get(key);
		}

		@Public
		public void complete(Object result) {
			webSocket.complete(result);
		}

		@Public
		public void close() {
			webSocket.closeSocket();
		}
	}

	@Extendable
	public interface EventHandler {
		@Extendable
		void onEvent(Event event);
	}

	@Public
	public WebSocket(Uri uri, HttpRequest.Preset preset) {
		this.uri = uri;
		holder = preset.getHolder();
		client = HttpClient.getInstance();
		Objects.requireNonNull(holder);
		if (preset instanceof HttpRequest.TimeoutsPreset) {
			setTimeouts(((HttpRequest.TimeoutsPreset) preset).getConnectTimeout(),
					((HttpRequest.TimeoutsPreset) preset).getReadTimeout());
		}
	}

	private WebSocket addHeader(Pair<String, String> header) {
		if (header != null && header.first != null && header.second != null) {
			if (headers == null) {
				headers = new ArrayList<>();
			}
			headers.add(header);
		}
		return this;
	}

	@Public
	public WebSocket addHeader(String name, String value) {
		return addHeader(new Pair<>(name, value));
	}

	@Public
	public WebSocket addCookie(String name, String value) {
		if (value != null) {
			if (cookieBuilder == null) {
				cookieBuilder = new CookieBuilder();
			}
			cookieBuilder.append(name, value);
		}
		return this;
	}

	@Public
	public WebSocket addCookie(String cookie) {
		if (cookie != null) {
			if (cookieBuilder == null) {
				cookieBuilder = new CookieBuilder();
			}
			cookieBuilder.append(cookie);
		}
		return this;
	}

	@Public
	public WebSocket addCookie(CookieBuilder builder) {
		if (builder != null) {
			if (cookieBuilder == null) {
				cookieBuilder = new CookieBuilder();
			}
			cookieBuilder.append(builder);
		}
		return this;
	}

	@Public
	public WebSocket setTimeouts(int connectTimeout, int readTimeout) {
		if (connectTimeout >= 0) {
			this.connectTimeout = connectTimeout;
		}
		if (readTimeout >= 0) {
			this.readTimeout = readTimeout;
		}
		return this;
	}

	private static final Random RANDOM = new Random(System.currentTimeMillis());
	private static final Pattern RESPONSE_CODE_PATTERN = Pattern.compile("HTTP/1.[10] (\\d+) (.*)");

	@Public
	public Connection open(EventHandler handler) throws HttpException {
		boolean success = false;
		try {
			if (socket != null) {
				throw new IllegalStateException("Web socket is open");
			}
			if (closed) {
				throw new HttpClient.InterruptedHttpException();
			}
			boolean verifyCertificate = holder.chan.locator.isUseHttps() && Preferences.isVerifyCertificate();
			HttpSession session = holder.createSession(client, uri, null, verifyCertificate, 0, 5);
			SocketResult socketResult = openSocket(session, verifyCertificate);
			socket = socketResult.socket;
			inputStream = socketResult.inputStream;
			outputStream = socketResult.outputStream;
			if (closed) {
				throw new HttpClient.InterruptedHttpException();
			}

			new Thread(() -> {
				try {
					ArrayList<ReadFrame> frames = new ArrayList<>();
					while (true) {
						ReadFrame frame = ReadFrame.read(inputStream);
						if (frame.fin) {
							if (frames.size() > 0) {
								ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
								for (ReadFrame dataFrame : frames) {
									byteArrayOutputStream.write(dataFrame.data);
								}
								byteArrayOutputStream.write(frame.data);
								frame = new ReadFrame(frames.get(0).opcode, true, byteArrayOutputStream.toByteArray());
								frames.clear();
							}

							switch (frame.opcode) {
								case OPCODE_TEXT:
								case OPCODE_BINARY: {
									readQueue.add(frame);
									break;
								}
								case OPCODE_CONNECTION_CLOSE: {
									int code = -1;
									if (frame.data.length >= 2) {
										code = IOUtils.bytesToInt(false, 0, 2, frame.data);
									}
									if (!closed) {
										connectionCloseException = true;
										handleException(new IOException("Connection closed by peer: " + code),
												false, true);
										closeSocket();
									}
									return;
								}
								case OPCODE_PING: {
									writeQueue.add(new WriteFrame(OPCODE_PONG, true, frame.data));
									break;
								}
								case OPCODE_PONG: {
									break;
								}
								default: {
									throw new IOException("Unknown opcode: " + frame.opcode);
								}
							}
						} else {
							frames.add(frame);
						}
					}
				} catch (IOException e) {
					handleException(e, true, !(e instanceof WebSocketException));
					closeSocket();
				}
			}).start();

			new Thread(() -> {
				try {
					while (true) {
						ReadFrame frame = readQueue.take();
						if (frame == END_READ_FRAME) {
							break;
						}
						handler.onEvent(new Event(frame, this));
					}
				} catch (LinkageError | RuntimeException e) {
					ExtensionException.logException(e, false);
					handleException(new IOException(), true, false);
				} catch (InterruptedException e) {
					// Ignore exception
				}
				synchronized (results) {
					cancelResults = true;
					results.notifyAll();
				}
			}).start();

			new Thread(() -> {
				try {
					byte[] buffer = new byte[8192];
					while (true) {
						WriteFrame frame = writeQueue.take();
						if (frame == END_WRITE_FRAME) {
							return;
						}
						frame.write(outputStream, buffer);
					}
				} catch (IOException e) {
					handleException(e, true, !(e instanceof WebSocketException));
					closeSocket();
				} catch (InterruptedException e) {
					// Ignore exception
				}
			}).start();

			session.setCallback(() -> {
				try {
					close();
				} catch (Exception e) {
					// Ignore exception
				}
			});

			success = true;
		} catch (IOException e) {
			handleException(e, false, true);
			checkException();
		} finally {
			if (!success) {
				closeSocket();
			}
		}
		return new Connection();
	}

	private static class SocketResult {
		public final InetSocket socket;
		public final InputStream inputStream;
		public final OutputStream outputStream;

		public SocketResult(InetSocket socket, InputStream inputStream, OutputStream outputStream) {
			this.socket = socket;
			this.inputStream = inputStream;
			this.outputStream = outputStream;
		}
	}

	@SuppressWarnings("ConditionalBreakInInfiniteLoop")
	private SocketResult openSocket(HttpSession session, boolean verifyCertificate)
			throws HttpException, IOException {
		InetSocket socket = null;
		try {
			Uri uri = session.getCurrentRequestedUri();
			URL url = HttpClient.encodeUri(uri);
			String scheme = uri.getScheme();
			boolean secure;
			int port = url.getPort();
			if ("https".equals(scheme) || "wss".equals(scheme)) {
				secure = true;
				if (port == -1) {
					port = 443;
				}
			} else if ("http".equals(scheme) || "ws".equals(scheme)) {
				secure = false;
				if (port == -1) {
					port = 80;
				}
			} else {
				throw new HttpException(ErrorItem.Type.UNSUPPORTED_SCHEME, false, false);
			}

			boolean resolve;
			Proxy proxy = client.getProxy(holder.chan);
			InetSocket.Builder.Factory factory;
			if (proxy != null && proxy.type() == Proxy.Type.HTTP) {
				// TODO Add support for HTTP proxy
				throw new HttpException(ErrorItem.Type.DOWNLOAD, false, false);
			} if (proxy != null && proxy.type() == Proxy.Type.SOCKS) {
				resolve = false;
				factory = () -> new Socket(proxy);
			} else {
				resolve = true;
				factory = InetSocket.Builder.Factory.DEFAULT;
			}
			try {
				socket = new InetSocket.Builder(url.getHost(), port, resolve)
						.setFactory(factory).setSecure(secure, verifyCertificate)
						.setTimeouts(connectTimeout, readTimeout).open();
			} catch (InetSocket.InvalidCertificateException e) {
				throw new HttpException(ErrorItem.Type.INVALID_CERTIFICATE, false, false, e);
			}

			byte[] webSocketKey = new byte[16];
			for (int i = 0; i < webSocketKey.length; i++) {
				webSocketKey[i] = (byte) RANDOM.nextInt(256);
			}
			String webSocketKeyEncoded = Base64.encodeToString(webSocketKey, Base64.NO_WRAP);

			InputStream inputStream = new BufferedInputStream(socket.getInputStream());
			OutputStream outputStream = new BufferedOutputStream(socket.getOutputStream());
			StringBuilder requestBuilder = new StringBuilder();
			requestBuilder.append("GET ").append(url.getFile()).append(" HTTP/1.1\r\n");
			String userAgent = null;
			boolean addHost = true;
			boolean addOrigin = true;
			boolean addUserAgent = true;
			if (headers != null) {
				for (Pair<String, String> header : headers) {
					switch (header.first.toLowerCase(Locale.US)) {
						case "host": {
							addHost = false;
							break;
						}
						case "origin": {
							addOrigin = false;
							break;
						}
						case "user-agent": {
							userAgent = header.second;
							addUserAgent = false;
							break;
						}
						case "connection":
						case "upgrade":
						case "sec-websocket-version":
						case "sec-websocket-key":
						case "sec-websocket-extensions":
						case "sec-websocket-protocol": {
							// Ignore headers
							continue;
						}
					}
					requestBuilder.append(header.first).append(": ").append(header.second.replaceAll("[\r\n]", ""))
							.append("\r\n");
				}
			}
			boolean appendPort = !(port == 80 && !secure || port == 443 && secure);
			if (addHost) {
				requestBuilder.append("Host: ").append(url.getHost());
				if (appendPort) {
					requestBuilder.append(':').append(port);
				}
				requestBuilder.append("\r\n");
			}
			if (addOrigin) {
				requestBuilder.append("Origin: ").append(scheme.replace("ws", "http"))
						.append("://").append(url.getHost());
				if (appendPort) {
					requestBuilder.append(':').append(port);
				}
				requestBuilder.append("\r\n");
			}
			requestBuilder.append("Connection: Upgrade\r\n");
			requestBuilder.append("Upgrade: websocket\r\n");
			requestBuilder.append("Sec-WebSocket-Version: 13\r\n");
			requestBuilder.append("Sec-WebSocket-Key: ").append(webSocketKeyEncoded).append("\r\n");
			requestBuilder.append("Sec-WebSocket-Protocol: chat, superchat\r\n");
			if (addUserAgent) {
				userAgent = AdvancedPreferences.getUserAgent(holder.chan.name);
				requestBuilder.append("User-Agent: ").append(userAgent.replaceAll("[\r\n]", "")).append("\r\n");
			}
			FirewallResolver.Identifier resolverIdentifier = new FirewallResolver
					.Identifier(userAgent, addUserAgent);
			CookieBuilder cookieBuilder = client.obtainModifiedCookieBuilder(this.cookieBuilder,
					holder.chan, uri, resolverIdentifier);
			if (cookieBuilder != null) {
				requestBuilder.append("Cookie: ").append(cookieBuilder.build().replaceAll("[\r\n]", ""))
						.append("\r\n");
			}
			requestBuilder.append("\r\n");
			@SuppressWarnings("CharsetObjectCanBeUsed")
			byte[] bytes = requestBuilder.toString().getBytes("ISO-8859-1");
			outputStream.write(bytes);
			outputStream.flush();

			ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
			int endCount = 0;
			while (true) {
				int b = inputStream.read();
				if (b == -1) {
					throw new HttpException(ErrorItem.Type.CONNECTION_RESET, false, true);
				}
				byteArrayOutputStream.write(b);
				switch (b) {
					case '\r': {
						if (endCount == 0 || endCount == 2) {
							endCount++;
						}
						break;
					}
					case '\n': {
						if (endCount == 1 || endCount == 3) {
							endCount++;
						}
						break;
					}
					default: {
						endCount = 0;
						break;
					}
				}
				if (endCount == 4) {
					break;
				}
			}

			@SuppressWarnings("CharsetObjectCanBeUsed")
			String[] responseHeaders = new String(byteArrayOutputStream.toByteArray(), "ISO-8859-1").split("\r\n");
			Matcher matcher = RESPONSE_CODE_PATTERN.matcher(responseHeaders[0]);
			if (matcher.matches()) {
				int responseCode = Integer.parseInt(matcher.group(1));
				switch (responseCode) {
					case HttpURLConnection.HTTP_MOVED_PERM:
					case HttpURLConnection.HTTP_MOVED_TEMP:
					case HttpURLConnection.HTTP_SEE_OTHER:
					case HttpClient.HTTP_TEMPORARY_REDIRECT: {
						if (session.nextAttempt()) {
							for (String header : responseHeaders) {
								if (header.toLowerCase(Locale.US).startsWith("location:")) {
									Uri redirectedUri = client.obtainRedirectedUri(uri,
											header.substring(header.indexOf(':') + 1).trim());
									if (redirectedUri == null) {
										throw new HttpException(ErrorItem.Type.DOWNLOAD, false, false);
									}
									IOUtils.close(socket);
									socket = null;
									scheme = redirectedUri.getScheme();
									boolean newSecure = "https".equals(scheme) || "wss".equals(scheme);
									if (session.verifyCertificate && secure && !newSecure) {
										// Redirect from https/wss to http/ws is unsafe
										throw new HttpException(ErrorItem.Type.UNSAFE_REDIRECT, true, false);
									}
									session.setNextRequestedUri(redirectedUri);
									return openSocket(session, verifyCertificate);
								}
							}
						}
						break;
					}
				}
				String responseText = matcher.group(2);
				if (responseCode != 101) {
					throw new HttpException(responseCode, responseText);
				}
			} else {
				throw new HttpException(ErrorItem.Type.INVALID_RESPONSE, false, false);
			}

			String checkKeyEncoded;
			try {
				MessageDigest digest = MessageDigest.getInstance("SHA-1");
				@SuppressWarnings("CharsetObjectCanBeUsed")
				byte[] result = digest.digest((webSocketKeyEncoded + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
						.getBytes("ISO-8859-1"));
				checkKeyEncoded = Base64.encodeToString(result, Base64.NO_WRAP);
			} catch (NoSuchAlgorithmException e) {
				throw new RuntimeException(e);
			}
			boolean verified = false;
			for (String header : responseHeaders) {
				if (header.toLowerCase(Locale.US).startsWith("sec-websocket-accept:")) {
					String value = header.substring(header.indexOf(':') + 1).trim();
					if (value.equals(checkKeyEncoded)) {
						verified = true;
						break;
					}
				}
			}
			if (!verified) {
				throw new HttpException(0, "Not verified");
			}

			try {
				return new SocketResult(socket, inputStream, outputStream);
			} finally {
				socket = null;
			}
		} finally {
			IOUtils.close(socket);
		}
	}

	private void closeSocket() {
		InetSocket socket = this.socket;
		closed = true;
		this.socket = null;
		if (socket != null) {
			writeQueue.add(END_WRITE_FRAME);
			readQueue.add(END_READ_FRAME);
			IOUtils.close(inputStream);
			IOUtils.close(outputStream);
			IOUtils.close(socket);
		}
	}

	private volatile boolean logException = false;
	private volatile boolean connectionCloseException = false;
	private volatile IOException exception;

	private void handleException(IOException exception, boolean checkSocket, boolean logException) {
		if ((!checkSocket || socket != null) && this.exception == null) {
			this.logException = logException;
			this.exception = exception;
		}
	}

	private void checkException() throws HttpException {
		boolean handled = true;
		try {
			holder.checkThread();
			IOException exception = this.exception;
			if (exception != null) {
				if (logException) {
					// Check and log only if exception occurred when socket was open
					throw HttpClient.transformIOException(exception);
				}
				throw new HttpException(ErrorItem.Type.DOWNLOAD, false, true, exception);
			}
			try {
				holder.checkInterrupted();
			} catch (HttpClient.InterruptedHttpException e) {
				throw new HttpException(null, false, false, e);
			}
			handled = false;
		} finally {
			if (handled) {
				closeSocket();
			}
		}
	}

	@Public
	public static class ComplexBinaryBuilder {
		private final Connection connection;
		private final ArrayList<InputStream> writeData = new ArrayList<>();
		private int length;

		private ComplexBinaryBuilder(Connection connection) {
			this.connection = connection;
		}

		@Public
		public ComplexBinaryBuilder bytes(byte... bytes) {
			if (bytes != null && bytes.length > 0) {
				writeData.add(new SimpleByteArrayInputStream(bytes));
				length += bytes.length;
			}
			return this;
		}

		@Public
		public ComplexBinaryBuilder bytes(int... bytes) {
			if (bytes != null && bytes.length > 0) {
				byte[] next = new byte[bytes.length];
				for (int i = 0; i < bytes.length; i++) {
					next[i] = (byte) bytes[i];
				}
				return bytes(next);
			}
			return this;
		}

		@Public
		public ComplexBinaryBuilder string(String string) {
			if (!StringUtils.isEmpty(string)) {
				bytes(string.getBytes());
			}
			return this;
		}

		@Public
		public ComplexBinaryBuilder stream(InputStream inputStream, int count) {
			if (inputStream != null && count > 0) {
				writeData.add(new LimitedInputStream(inputStream, count));
				length += count;
			}
			return this;
		}

		@Public
		public ComplexBinaryBuilder wrap(Wrapper wrapper) {
			return wrapper.apply(this);
		}

		@Public
		public Connection send() throws HttpException {
			return connection.sendBuiltComplexBinary(this);
		}

		@Extendable
		public interface Wrapper {
			@Extendable
			ComplexBinaryBuilder apply(ComplexBinaryBuilder builder);
		}
	}

	@Public
	public class Connection {
		@SuppressWarnings("CharsetObjectCanBeUsed")
		@Public
		public Connection sendText(String text) throws HttpException {
			checkException();
			try {
				writeQueue.add(new WriteFrame(OPCODE_TEXT, true, text != null ? text.getBytes("UTF-8") : new byte[0]));
			} catch (UnsupportedEncodingException e) {
				checkException();
				throw new RuntimeException(e);
			}
			return this;
		}

		@Public
		public Connection sendBinary(byte[] data) throws HttpException {
			checkException();
			writeQueue.add(new WriteFrame(OPCODE_BINARY, true, data));
			return this;
		}

		@Public
		public ComplexBinaryBuilder sendComplexBinary() throws HttpException {
			checkException();
			return new ComplexBinaryBuilder(this);
		}

		private Connection sendBuiltComplexBinary(ComplexBinaryBuilder builder) throws HttpException {
			checkException();
			writeQueue.add(new WriteFrame(OPCODE_BINARY, true, builder.writeData, builder.length));
			return this;
		}

		@Public
		public Connection await(Object... results) throws HttpException {
			if (results == null || results.length == 0) {
				return this;
			}
			synchronized (WebSocket.this.results) {
				try {
					OUTER: while (!cancelResults) {
						for (Object result : results) {
							if (WebSocket.this.results.remove(result)) {
								break OUTER;
							}
						}
						WebSocket.this.results.wait();
					}
				} catch (InterruptedException e) {
					throw new HttpException(null, false, false, e);
				}
			}
			try {
				checkException();
			}
			catch (HttpException e) {
				if (!connectionCloseException) {
					throw e;
				}
			}
			return this;
		}

		@Public
		public Connection store(String key, Object data) {
			WebSocket.this.store(key, data);
			return this;
		}

		@Public
		public <T> T get(String key) {
			return WebSocket.this.get(key);
		}

		@Public
		public Result close() throws HttpException {
			checkException();
			closeSocket();
			return new Result();
		}
	}

	@Public
	public class Result {
		@Public
		public <T> T get(String key) {
			return WebSocket.this.get(key);
		}
	}

	private WebSocket close() throws HttpException {
		checkException();
		closeSocket();
		return this;
	}

	private void complete(Object result) {
		synchronized (results) {
			results.add(result);
			results.notifyAll();
		}
	}

	private static final class WebSocketException extends IOException {}

	private static void checkReadByte(int readByte) throws WebSocketException {
		if (readByte == -1) {
			throw new WebSocketException();
		}
	}

	private static final ReadFrame END_READ_FRAME = new ReadFrame(0, true, null);
	private static final WriteFrame END_WRITE_FRAME = new WriteFrame(0, true, null);

	private static class ReadFrame {
		public final int opcode;
		public final boolean fin;
		public final byte[] data;

		public ReadFrame(int opcode, boolean fin, byte[] data) {
			this.opcode = opcode;
			this.fin = fin;
			this.data = data;
		}

		public static ReadFrame read(InputStream inputStream) throws IOException {
			int opcodeData = inputStream.read();
			checkReadByte(opcodeData);
			int length = inputStream.read();
			checkReadByte(length);
			boolean fin = (opcodeData & 0x80) == 0x80;
			int opcode = opcodeData & 0x0f;
			boolean masked = (length & 0x80) == 0x80;

			length = length & 0x7f;
			if (length >= 126) {
				byte[] data = new byte[length == 126 ? 2 : 8];
				if (!IOUtils.readExactlyCheck(inputStream, data, 0, data.length)) {
					checkReadByte(-1);
				}
				if (data.length == 8 && (data[0] != 0 || data[1] != 0 || data[2] != 0 || data[3] != 0 ||
						(data[4] & 0x80) == 0x80)) {
					// Too large frame
					checkReadByte(-1);
				}
				length = IOUtils.bytesToInt(false, 0, data.length, data);
			}

			byte[] mask = null;
			if (masked) {
				mask = new byte[4];
				if (!IOUtils.readExactlyCheck(inputStream, mask, 0, mask.length)) {
					checkReadByte(-1);
				}
			}

			if (length < 0) {
				checkReadByte(-1);
			}

			byte[] data = new byte[length];
			if (!IOUtils.readExactlyCheck(inputStream, data, 0, data.length)) {
				checkReadByte(-1);
			}

			if (mask != null) {
				for (int i = 0; i < data.length; i++) {
					data[i] ^= mask[i % mask.length];
				}
			}

			return new ReadFrame(opcode, fin, data);
		}
	}

	private static class WriteFrame {
		public final int opcode;
		public final boolean fin;
		public final List<InputStream> inputStreams;
		public final int length;

		public WriteFrame(int opcode, boolean fin, byte[] data) {
			this(opcode, fin, data != null ? Collections.singletonList(new SimpleByteArrayInputStream(data))
					: Collections.emptyList(), data != null ? data.length : 0);
		}

		public WriteFrame(int opcode, boolean fin, List<InputStream> inputStream, int length) {
			this.opcode = opcode;
			this.fin = fin;
			this.inputStreams = inputStream;
			this.length = length;
		}

		public void write(OutputStream outputStream, byte[] buffer) throws IOException {
			outputStream.write(0x80 | opcode);

			if (length >= 126) {
				if (length >= 0x10000) {
					outputStream.write(127 | 0x80);
					outputStream.write(IOUtils.intToBytes(length, false, 0, 8, null));
				} else {
					outputStream.write(126 | 0x80);
					outputStream.write(IOUtils.intToBytes(length, false, 0, 2, null));
				}
			} else {
				outputStream.write(length | 0x80);
			}

			byte[] mask = new byte[4];
			for (int i = 0; i < mask.length; i++) {
				mask[i] = (byte) RANDOM.nextInt(256);
			}
			outputStream.write(mask);

			int streamIndex = 0;
			int index = 0;
			while (index < length && streamIndex < inputStreams.size()) {
				InputStream inputStream = inputStreams.get(streamIndex);
				int count = inputStream.read(buffer);
				if (count < 0) {
					streamIndex++;
					continue;
				}
				for (int i = 0; i < count; i++) {
					buffer[i] ^= mask[(index + i) % mask.length];
				}
				outputStream.write(buffer, 0, count);
				index += count;
			}

			outputStream.flush();
		}
	}

	private static class SimpleByteArrayInputStream extends InputStream {
		private final byte[] array;

		private int position = 0;

		public SimpleByteArrayInputStream(byte[] array) {
			this.array = array;
		}

		@Override
		public int read() {
			return position < array.length ? array[position++] & 0xff : -1;
		}

		@Override
		public int read(@NonNull byte[] buffer) {
			return read(buffer, 0, buffer.length);
		}

		@Override
		public int read(@NonNull byte[] buffer, int byteOffset, int byteCount) {
			int left = array.length - position;
			if (left > 0) {
				byteCount = Math.min(byteCount, left);
				System.arraycopy(array, position, buffer, byteOffset, byteCount);
				position += byteCount;
				return byteCount;
			} else {
				return -1;
			}
		}
	}

	private static class LimitedInputStream extends InputStream {
		private final InputStream inputStream;
		private final int count;

		private int position;

		private LimitedInputStream(InputStream inputStream, int count) {
			this.inputStream = inputStream;
			this.count = count;
		}

		@Override
		public int read() throws IOException {
			if (position < count) {
				int result = inputStream.read();
				if (result >= 0) {
					position++;
				}
				return result;
			} else {
				return -1;
			}
		}

		@Override
		public int read(@NonNull byte[] buffer) throws IOException {
			return read(buffer, 0, buffer.length);
		}

		@Override
		public int read(@NonNull byte[] buffer, int byteOffset, int byteCount) throws IOException {
			int left = count - position;
			if (left > 0) {
				byteCount = Math.min(byteCount, left);
				byteCount = inputStream.read(buffer, byteOffset, byteCount);
				if (byteCount > 0) {
					position += byteCount;
				}
				return byteCount;
			} else {
				return -1;
			}
		}

		@Override
		public void close() throws IOException {
			inputStream.close();
		}
	}

	private final HashMap<String, Object> storedData = new HashMap<>();

	private WebSocket store(String key, Object data) {
		synchronized (storedData) {
			storedData.put(key, data);
		}
		return this;
	}

	@SuppressWarnings("unchecked")
	private <T> T get(String key) {
		synchronized (storedData) {
			return (T) storedData.get(key);
		}
	}
}
