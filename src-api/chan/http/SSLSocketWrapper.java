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

public class SSLSocketWrapper extends SSLSocket
{
	private final SSLSocket mWrapped;

	public SSLSocketWrapper(SSLSocket socket)
	{
		mWrapped = socket;
	}

	@Override
	public void addHandshakeCompletedListener(HandshakeCompletedListener listener)
	{
		mWrapped.addHandshakeCompletedListener(listener);
	}

	@Override
	public boolean getEnableSessionCreation()
	{
		return mWrapped.getEnableSessionCreation();
	}

	@Override
	public String[] getEnabledCipherSuites()
	{
		return mWrapped.getEnabledCipherSuites();
	}

	@Override
	public String[] getEnabledProtocols()
	{
		return mWrapped.getEnabledProtocols();
	}

	@Override
	public boolean getNeedClientAuth()
	{
		return mWrapped.getNeedClientAuth();
	}

	@Override
	public SSLSession getSession()
	{
		return mWrapped.getSession();
	}

	@Override
	public String[] getSupportedCipherSuites()
	{
		return mWrapped.getSupportedCipherSuites();
	}

	@Override
	public String[] getSupportedProtocols()
	{
		return mWrapped.getSupportedProtocols();
	}

	@Override
	public boolean getUseClientMode()
	{
		return mWrapped.getUseClientMode();
	}

	@Override
	public boolean getWantClientAuth()
	{
		return mWrapped.getWantClientAuth();
	}

	@Override
	public void removeHandshakeCompletedListener(HandshakeCompletedListener listener)
	{
		mWrapped.removeHandshakeCompletedListener(listener);
	}

	@Override
	public void setEnableSessionCreation(boolean flag)
	{
		mWrapped.setEnableSessionCreation(flag);
	}

	@Override
	public void setEnabledCipherSuites(String[] suites)
	{
		mWrapped.setEnabledCipherSuites(suites);
	}

	@Override
	public void setEnabledProtocols(String[] protocols)
	{
		mWrapped.setEnabledProtocols(protocols);
	}

	@Override
	public void setNeedClientAuth(boolean need)
	{
		mWrapped.setNeedClientAuth(need);
	}

	@Override
	public void setUseClientMode(boolean mode)
	{
		mWrapped.setUseClientMode(mode);
	}

	@Override
	public void setWantClientAuth(boolean want)
	{
		mWrapped.setWantClientAuth(want);
	}

	@Override
	public void startHandshake() throws IOException
	{
		mWrapped.startHandshake();
	}

	@Override
	public void bind(SocketAddress localAddr) throws IOException
	{
		mWrapped.bind(localAddr);
	}

	@Override
	public void connect(SocketAddress remoteAddr) throws IOException
	{
		mWrapped.connect(remoteAddr);
	}

	@Override
	public void connect(SocketAddress remoteAddr, int timeout) throws IOException
	{
		mWrapped.connect(remoteAddr, timeout);
	}

	@Override
	public synchronized void close() throws IOException
	{
		mWrapped.close();
	}

	@SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
	@Override
	public boolean equals(Object o)
	{
		return mWrapped.equals(o);
	}

	@Override
	public SocketChannel getChannel()
	{
		return mWrapped.getChannel();
	}

	@Override
	public InetAddress getInetAddress()
	{
		return mWrapped.getInetAddress();
	}

	@Override
	public InputStream getInputStream() throws IOException
	{
		return mWrapped.getInputStream();
	}

	@Override
	public boolean getKeepAlive() throws SocketException
	{
		return mWrapped.getKeepAlive();
	}

	@Override
	public InetAddress getLocalAddress()
	{
		return mWrapped.getLocalAddress();
	}

	@Override
	public int getLocalPort()
	{
		return mWrapped.getLocalPort();
	}

	@Override
	public SocketAddress getLocalSocketAddress()
	{
		return mWrapped.getLocalSocketAddress();
	}

	@Override
	public boolean getOOBInline() throws SocketException
	{
		return mWrapped.getOOBInline();
	}

	@Override
	public OutputStream getOutputStream() throws IOException
	{
		return mWrapped.getOutputStream();
	}

	@Override
	public int getPort()
	{
		return mWrapped.getPort();
	}

	@Override
	public synchronized int getReceiveBufferSize() throws SocketException
	{
		return mWrapped.getReceiveBufferSize();
	}

	@Override
	public SocketAddress getRemoteSocketAddress()
	{
		return mWrapped.getRemoteSocketAddress();
	}

	@Override
	public boolean getReuseAddress() throws SocketException
	{
		return mWrapped.getReuseAddress();
	}

	@Override
	public synchronized int getSendBufferSize() throws SocketException
	{
		return mWrapped.getSendBufferSize();
	}

	@Override
	public int getSoLinger() throws SocketException
	{
		return mWrapped.getSoLinger();
	}

	@Override
	public synchronized int getSoTimeout() throws SocketException
	{
		return mWrapped.getSoTimeout();
	}

	@Override
	public SSLParameters getSSLParameters()
	{
		return mWrapped.getSSLParameters();
	}

	@Override
	public boolean getTcpNoDelay() throws SocketException
	{
		return mWrapped.getTcpNoDelay();
	}

	@Override
	public int getTrafficClass() throws SocketException
	{
		return mWrapped.getTrafficClass();
	}

	@Override
	public int hashCode()
	{
		return mWrapped.hashCode();
	}

	@Override
	public boolean isBound()
	{
		return mWrapped.isBound();
	}

	@Override
	public boolean isClosed()
	{
		return mWrapped.isClosed();
	}

	@Override
	public boolean isConnected()
	{
		return mWrapped.isConnected();
	}

	@Override
	public boolean isInputShutdown()
	{
		return mWrapped.isInputShutdown();
	}

	@Override
	public boolean isOutputShutdown()
	{
		return mWrapped.isOutputShutdown();
	}

	@Override
	public void sendUrgentData(int value) throws IOException
	{
		mWrapped.sendUrgentData(value);
	}

	@Override
	public void setKeepAlive(boolean keepAlive) throws SocketException
	{
		mWrapped.setKeepAlive(keepAlive);
	}

	@Override
	public void setOOBInline(boolean oobinline) throws SocketException
	{
		mWrapped.setOOBInline(oobinline);
	}

	@Override
	public void setPerformancePreferences(int connectionTime, int latency, int bandwidth)
	{
		mWrapped.setPerformancePreferences(connectionTime, latency, bandwidth);
	}

	@Override
	public synchronized void setReceiveBufferSize(int size) throws SocketException
	{
		mWrapped.setReceiveBufferSize(size);
	}

	@Override
	public void setReuseAddress(boolean reuse) throws SocketException
	{
		mWrapped.setReuseAddress(reuse);
	}

	@Override
	public synchronized void setSendBufferSize(int size) throws SocketException
	{
		mWrapped.setSendBufferSize(size);
	}

	@Override
	public void setSoLinger(boolean on, int timeout) throws SocketException
	{
		mWrapped.setSoLinger(on, timeout);
	}

	@Override
	public synchronized void setSoTimeout(int timeout) throws SocketException
	{
		mWrapped.setSoTimeout(timeout);
	}

	@Override
	public void setSSLParameters(SSLParameters p)
	{
		mWrapped.setSSLParameters(p);
	}

	@Override
	public void setTcpNoDelay(boolean on) throws SocketException
	{
		mWrapped.setTcpNoDelay(on);
	}

	@Override
	public void setTrafficClass(int value) throws SocketException
	{
		mWrapped.setTrafficClass(value);
	}

	@Override
	public void shutdownInput() throws IOException
	{
		mWrapped.shutdownInput();
	}

	@Override
	public void shutdownOutput() throws IOException
	{
		mWrapped.shutdownOutput();
	}

	@Override
	public String toString()
	{
		return mWrapped.toString();
	}
}