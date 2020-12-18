package chan.http;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;

public class InetSocket implements Closeable {
	public static final class Builder {
		public interface Factory {
			Factory DEFAULT = SocketFactory.getDefault()::createSocket;

			Socket createSocket() throws IOException;
		}

		private final String host;
		private final int port;
		private final boolean resolve;

		private Factory factory = Factory.DEFAULT;
		private boolean secure;
		private boolean verifyCertificate;
		private int connectTimeout = 15000;
		private int readTimeout = 15000;

		public Builder(String host, int port, boolean resolve) {
			this.host = host;
			this.port = port;
			this.resolve = resolve;
		}

		public Builder setFactory(Factory factory) {
			this.factory = factory;
			return this;
		}

		public Builder setSecure(boolean secure, boolean verifyCertificate) {
			this.secure = secure;
			this.verifyCertificate = verifyCertificate;
			return this;
		}

		public Builder setTimeouts(int connectTimeout, int readTimeout) {
			this.connectTimeout = connectTimeout;
			this.readTimeout = readTimeout;
			return this;
		}

		public InetSocket open() throws IOException {
			return new InetSocket(this);
		}
	}

	public static class InvalidCertificateException extends IOException {}

	private final Socket socket;

	private InetSocket(Builder builder) throws IOException {
		InetSocketAddress address = builder.resolve ? new InetSocketAddress(builder.host, builder.port)
				: InetSocketAddress.createUnresolved(builder.host, builder.port);
		Socket socket = builder.factory.createSocket();
		boolean success = false;
		try {
			socket.setSoTimeout(Math.max(builder.readTimeout, 60000));
			socket.connect(address, builder.connectTimeout);
			if (builder.secure) {
				HttpClient client = HttpClient.getInstance();
				SSLSocket sslSocket = (SSLSocket) client.getSSLSocketFactory(builder.verifyCertificate)
						.createSocket(socket, builder.host, builder.port, true);
				socket = sslSocket;
				sslSocket.startHandshake();
				if (!client.getHostnameVerifier(builder.verifyCertificate)
						.verify(builder.host, sslSocket.getSession())) {
					throw new InvalidCertificateException();
				}
			}
			success = true;
		} finally {
			if (!success) {
				try {
					socket.close();
				} catch (IOException e) {
					// Ignore
				}
			}
		}
		this.socket = socket;
	}

	@Override
	public void close() throws IOException {
		socket.close();
	}

	public InputStream getInputStream() throws IOException {
		return socket.getInputStream();
	}

	public OutputStream getOutputStream() throws IOException {
		return socket.getOutputStream();
	}
}
