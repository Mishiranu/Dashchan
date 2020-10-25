package chan.http;

import androidx.annotation.NonNull;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.SocketChannel;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

public class SSLSocketWrapper extends SSLSocket {
	public final SSLSocket wrapped;

	public SSLSocketWrapper(SSLSocket socket) {
		wrapped = socket;
	}

	public SSLSocket getRealSocket() {
		return wrapped instanceof SSLSocketWrapper ? ((SSLSocketWrapper) wrapped).getRealSocket() : wrapped;
	}

	@Override
	public void addHandshakeCompletedListener(HandshakeCompletedListener listener) {
		wrapped.addHandshakeCompletedListener(listener);
	}

	@Override
	public boolean getEnableSessionCreation() {
		return wrapped.getEnableSessionCreation();
	}

	@Override
	public String[] getEnabledCipherSuites() {
		return wrapped.getEnabledCipherSuites();
	}

	@Override
	public String[] getEnabledProtocols() {
		return wrapped.getEnabledProtocols();
	}

	@Override
	public boolean getNeedClientAuth() {
		return wrapped.getNeedClientAuth();
	}

	@Override
	public SSLSession getSession() {
		return wrapped.getSession();
	}

	@Override
	public String[] getSupportedCipherSuites() {
		return wrapped.getSupportedCipherSuites();
	}

	@Override
	public String[] getSupportedProtocols() {
		return wrapped.getSupportedProtocols();
	}

	@Override
	public boolean getUseClientMode() {
		return wrapped.getUseClientMode();
	}

	@Override
	public boolean getWantClientAuth() {
		return wrapped.getWantClientAuth();
	}

	@Override
	public void removeHandshakeCompletedListener(HandshakeCompletedListener listener) {
		wrapped.removeHandshakeCompletedListener(listener);
	}

	@Override
	public void setEnableSessionCreation(boolean flag) {
		wrapped.setEnableSessionCreation(flag);
	}

	@Override
	public void setEnabledCipherSuites(String[] suites) {
		wrapped.setEnabledCipherSuites(suites);
	}

	@Override
	public void setEnabledProtocols(String[] protocols) {
		wrapped.setEnabledProtocols(protocols);
	}

	@Override
	public void setNeedClientAuth(boolean need) {
		wrapped.setNeedClientAuth(need);
	}

	@Override
	public void setUseClientMode(boolean mode) {
		wrapped.setUseClientMode(mode);
	}

	@Override
	public void setWantClientAuth(boolean want) {
		wrapped.setWantClientAuth(want);
	}

	@Override
	public void startHandshake() throws IOException {
		wrapped.startHandshake();
	}

	@Override
	public void bind(SocketAddress localAddr) throws IOException {
		wrapped.bind(localAddr);
	}

	@Override
	public void connect(SocketAddress remoteAddr) throws IOException {
		wrapped.connect(remoteAddr);
	}

	@Override
	public void connect(SocketAddress remoteAddr, int timeout) throws IOException {
		wrapped.connect(remoteAddr, timeout);
	}

	@Override
	public synchronized void close() throws IOException {
		wrapped.close();
	}

	@SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
	@Override
	public boolean equals(Object o) {
		return wrapped.equals(o);
	}

	@Override
	public SocketChannel getChannel() {
		return wrapped.getChannel();
	}

	@Override
	public InetAddress getInetAddress() {
		return wrapped.getInetAddress();
	}

	@Override
	public InputStream getInputStream() throws IOException {
		return wrapped.getInputStream();
	}

	@Override
	public boolean getKeepAlive() throws SocketException {
		return wrapped.getKeepAlive();
	}

	@Override
	public InetAddress getLocalAddress() {
		return wrapped.getLocalAddress();
	}

	@Override
	public int getLocalPort() {
		return wrapped.getLocalPort();
	}

	@Override
	public SocketAddress getLocalSocketAddress() {
		return wrapped.getLocalSocketAddress();
	}

	@Override
	public boolean getOOBInline() throws SocketException {
		return wrapped.getOOBInline();
	}

	@Override
	public OutputStream getOutputStream() throws IOException {
		return wrapped.getOutputStream();
	}

	@Override
	public int getPort() {
		return wrapped.getPort();
	}

	@Override
	public synchronized int getReceiveBufferSize() throws SocketException {
		return wrapped.getReceiveBufferSize();
	}

	@Override
	public SocketAddress getRemoteSocketAddress() {
		return wrapped.getRemoteSocketAddress();
	}

	@Override
	public boolean getReuseAddress() throws SocketException {
		return wrapped.getReuseAddress();
	}

	@Override
	public synchronized int getSendBufferSize() throws SocketException {
		return wrapped.getSendBufferSize();
	}

	@Override
	public int getSoLinger() throws SocketException {
		return wrapped.getSoLinger();
	}

	@Override
	public synchronized int getSoTimeout() throws SocketException {
		return wrapped.getSoTimeout();
	}

	@Override
	public SSLParameters getSSLParameters() {
		return wrapped.getSSLParameters();
	}

	@Override
	public boolean getTcpNoDelay() throws SocketException {
		return wrapped.getTcpNoDelay();
	}

	@Override
	public int getTrafficClass() throws SocketException {
		return wrapped.getTrafficClass();
	}

	@Override
	public int hashCode() {
		return wrapped.hashCode();
	}

	@Override
	public boolean isBound() {
		return wrapped.isBound();
	}

	@Override
	public boolean isClosed() {
		return wrapped.isClosed();
	}

	@Override
	public boolean isConnected() {
		return wrapped.isConnected();
	}

	@Override
	public boolean isInputShutdown() {
		return wrapped.isInputShutdown();
	}

	@Override
	public boolean isOutputShutdown() {
		return wrapped.isOutputShutdown();
	}

	@Override
	public void sendUrgentData(int value) throws IOException {
		wrapped.sendUrgentData(value);
	}

	@Override
	public void setKeepAlive(boolean keepAlive) throws SocketException {
		wrapped.setKeepAlive(keepAlive);
	}

	@Override
	public void setOOBInline(boolean oobinline) throws SocketException {
		wrapped.setOOBInline(oobinline);
	}

	@Override
	public void setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
		wrapped.setPerformancePreferences(connectionTime, latency, bandwidth);
	}

	@Override
	public synchronized void setReceiveBufferSize(int size) throws SocketException {
		wrapped.setReceiveBufferSize(size);
	}

	@Override
	public void setReuseAddress(boolean reuse) throws SocketException {
		wrapped.setReuseAddress(reuse);
	}

	@Override
	public synchronized void setSendBufferSize(int size) throws SocketException {
		wrapped.setSendBufferSize(size);
	}

	@Override
	public void setSoLinger(boolean on, int timeout) throws SocketException {
		wrapped.setSoLinger(on, timeout);
	}

	@Override
	public synchronized void setSoTimeout(int timeout) throws SocketException {
		wrapped.setSoTimeout(timeout);
	}

	@Override
	public void setSSLParameters(SSLParameters p) {
		wrapped.setSSLParameters(p);
	}

	@Override
	public void setTcpNoDelay(boolean on) throws SocketException {
		wrapped.setTcpNoDelay(on);
	}

	@Override
	public void setTrafficClass(int value) throws SocketException {
		wrapped.setTrafficClass(value);
	}

	@Override
	public void shutdownInput() throws IOException {
		wrapped.shutdownInput();
	}

	@Override
	public void shutdownOutput() throws IOException {
		wrapped.shutdownOutput();
	}

	@NonNull
	@Override
	public String toString() {
		return wrapped.toString();
	}
}
