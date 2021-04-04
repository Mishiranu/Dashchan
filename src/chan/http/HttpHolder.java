package chan.http;

import android.net.Uri;
import chan.annotation.Public;
import chan.content.Chan;
import com.mishiranu.dashchan.content.model.ErrorItem;
import java.io.Closeable;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

// TODO CHAN
// Remove @Public annotation after updating all extensions which use HttpHolder in ChanPerformer.
// Added: 30.03.21 18:45
@Public
public final class HttpHolder {
	public interface Use extends Closeable {
		void close();
	}

	private Thread thread;
	private HttpSession session;
	private ArrayList<HttpSession> sessions;

	final Chan chan;

	boolean mayResolveFirewallBlock = true;

	public HttpHolder(Chan chan) {
		this.chan = chan;
	}

	void checkThread() {
		synchronized (this) {
			if (thread != Thread.currentThread()) {
				throw new IllegalStateException("This action is allowed from the initial thread only");
			}
		}
	}

	public Use use() {
		// Lock for concurrent "thread" variable access
		synchronized (this) {
			if (thread != null) {
				checkThread();
				if (sessions == null) {
					sessions = new ArrayList<>();
				}
				sessions.add(session);
				if (session != null) {
					session.disconnectAndClear();
				}
				session = null;
				return () -> {
					releaseSession();
					session = sessions.remove(sessions.size() - 1);
				};
			} else {
				thread = Thread.currentThread();
				return this::releaseSession;
			}
		}
	}

	HttpSession createSession(HttpClient client, Uri uri, Proxy proxy,
			boolean verifyCertificate, int delay, int maxAttempts) {
		checkThread();
		if (session != null) {
			session.disconnectAndClear();
		}
		boolean mayCheckFirewallBlock = sessions == null || sessions.isEmpty();
		session = new HttpSession(this, client, uri, proxy,
				verifyCertificate, mayCheckFirewallBlock, delay, maxAttempts);
		return session;
	}

	private void releaseSession() {
		checkThread();
		if (session != null) {
			session.disconnectAndClear();
		}
	}

	private volatile boolean interrupted = false;

	public interface Callback {
		void onDisconnectRequested();
	}

	public void interrupt() {
		interrupted = true;
	}

	boolean isInterrupted() {
		return interrupted;
	}

	void checkInterrupted() throws HttpClient.InterruptedHttpException {
		if (isInterrupted()) {
			throw new HttpClient.InterruptedHttpException();
		}
	}

	// TODO CHAN
	// Remove this method after updating
	// alterchan chiochan chuckdfwk diochan kurisach nulltirech owlchan ponyach ponychan sevenchan shanachan taima
	// valkyria
	// Added: 18.10.20 19:08
	@Deprecated
	@Public
	public void disconnect() {
		checkThread();
		if (session != null) {
			session.disconnectAndClear();
		}
	}

	// TODO CHAN
	// Remove this method after updating
	// alterchan chiochan chuckdfwk diochan kurisach nulltirech owlchan ponyach ponychan sevenchan shanachan taima
	// valkyria
	// Added: 18.10.20 19:08
	@Deprecated
	@Public
	public HttpResponse read() throws HttpException {
		checkThread();
		if (session != null && session.response != null) {
			return session.response;
		}
		throw new HttpException(ErrorItem.Type.DOWNLOAD, false, false);
	}

	// TODO CHAN
	// Remove this method after updating
	// archiverbt chiochan chuckdfwk desustorage exach fiftyfive fourplebs horochan nulltirech onechanca ponychan
	// tiretirech
	// Added: 18.10.20 19:08
	@Deprecated
	@Public
	public void checkResponseCode() throws HttpException {
		checkThread();
		if (session != null) {
			session.checkResponseCode();
		}
	}

	// TODO CHAN
	// Remove this method after updating
	// alphachan alterchan anonfm archiverbt chiochan chuckdfwk desustorage diochan exach fiftyfive fourplebs horochan
	// kurisach nulltirech onechanca owlchan ponyach ponychan sevenchan shanachan taima tiretirech valkyria
	// Added: 18.10.20 19:08
	@Deprecated
	@Public
	public int getResponseCode() {
		checkThread();
		return session != null ? session.getResponseCode() : -1;
	}

	// TODO CHAN
	// Remove this method after updating
	// alphachan alterchan anonfm chiochan chuckdfwk diochan exach kurisach onechanca owlchan ponyach ponychan
	// Added: 18.10.20 19:08
	@Deprecated
	@Public
	public Uri getRedirectedUri() {
		checkThread();
		return session != null ? session.redirectedUri : null;
	}

	// TODO CHAN
	// Remove this method after updating
	// alterchan anonfm wizardchan
	// Added: 18.10.20 19:08
	@Deprecated
	@Public
	public Map<String, List<String>> getHeaderFields() {
		checkThread();
		return session != null ? session.getHeaderFields() : Collections.emptyMap();
	}

	// TODO CHAN
	// Remove this method after updating
	// alphachan alterchan chaosach chiochan chuckdfwk endchan exach haibane kurisach lolifox onechanca ponyach
	// Added: 18.10.20 19:08
	@Deprecated
	@Public
	public String getCookieValue(String name) {
		checkThread();
		return session != null ? session.getCookieValue(name) : null;
	}

	// TODO CHAN
	// Remove this method after updating
	// fiftyfive
	// Added: 18.10.20 19:08
	@Deprecated
	@Public
	public HttpValidator getValidator() {
		return extractValidator();
	}

	public HttpValidator extractValidator() {
		checkThread();
		return session != null && session.response != null ? session.response.getValidator() : null;
	}
}
