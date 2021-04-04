package chan.http;

import android.net.Uri;
import android.util.Pair;
import chan.annotation.Public;
import com.mishiranu.dashchan.content.Preferences;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Objects;

@Public
public final class HttpRequest {
	@Public
	public interface Preset {
		HttpHolder getHolder();
	}

	public interface TimeoutsPreset extends Preset {
		int getConnectTimeout();
		int getReadTimeout();
	}

	public interface OutputListenerPreset extends Preset {
		OutputListener getOutputListener();
	}

	public interface RangePreset extends Preset {
		long getRangeStart();
		long getRangeEnd();
	}

	public interface OutputListener {
		void onOutputProgressChange(long progress, long progressMax);
	}

	@Public
	public interface RedirectHandler {
		@Public
		enum Action {
			@Public CANCEL,
			@Public GET,
			@Public RETRANSMIT
		}

		@Public
		Action onRedirect(HttpResponse response) throws HttpException;

		// TODO CHAN
		// Remove this method and simplify default redirect handlers after updating
		// alterchan chaosach haibane kurisach onechanca owlchan synch tiretirech
		// Added: 18.10.20 01:50
		@Public
		Action onRedirectReached(int responseCode, Uri requestedUri, Uri redirectedUri, HttpHolder holder)
				throws HttpException;

		@Public
		RedirectHandler NONE = new RedirectHandler() {
			@Override
			public Action onRedirect(HttpResponse response) {
				return onRedirectReached(response.getResponseCode(),
						response.getRequestedUri(), response.getRedirectedUri(), null);
			}

			@Override
			public Action onRedirectReached(int responseCode, Uri requestedUri, Uri redirectedUri, HttpHolder holder) {
				return Action.CANCEL;
			}
		};

		@Public
		RedirectHandler BROWSER = new RedirectHandler() {
			@Override
			public Action onRedirect(HttpResponse response) {
				return onRedirectReached(response.getResponseCode(),
						response.getRequestedUri(), response.getRedirectedUri(), null);
			}

			@Override
			public Action onRedirectReached(int responseCode, Uri requestedUri, Uri redirectedUri, HttpHolder holder) {
				return Action.GET;
			}
		};

		@Public
		RedirectHandler STRICT = new RedirectHandler() {
			@Override
			public Action onRedirect(HttpResponse response) {
				return onRedirectReached(response.getResponseCode(),
						response.getRequestedUri(), response.getRedirectedUri(), null);
			}

			@Override
			public Action onRedirectReached(int responseCode, Uri requestedUri, Uri redirectedUri, HttpHolder holder) {
				switch (responseCode) {
					case HttpURLConnection.HTTP_MOVED_PERM:
					case HttpURLConnection.HTTP_MOVED_TEMP: {
						return Action.RETRANSMIT;
					}
					default: {
						return Action.GET;
					}
				}
			}
		};
	}

	final Uri uri;
	private final HttpHolder holder;
	private final HttpClient client;

	enum RequestMethod {GET, HEAD, POST, PUT, DELETE}

	RequestMethod requestMethod = RequestMethod.GET;
	RequestEntity requestEntity;

	boolean successOnly = true;
	RedirectHandler redirectHandler = RedirectHandler.BROWSER;
	HttpValidator validator;
	boolean keepAlive = true;

	OutputListener outputListener;
	long rangeStart = -1;
	long rangeEnd = -1;

	int connectTimeout = 15000;
	int readTimeout = 15000;
	int delay = 0;

	ArrayList<Pair<String, String>> headers;
	CookieBuilder cookieBuilder;

	// TODO CHAN
	// Remove this constructor after updating
	// allchan alphachan anonfm archiverbt chuckdfwk desustorage diochan exach fiftyfive fourplebs kropyvach kurisach
	// nulltirech owlchan ponyach ponychan princessluna randomarchive sevenchan shanachan synch tiretirech tumbach
	// uboachan wizardchan
	// Added: 26.09.20 20:10
	public HttpRequest(Uri uri, HttpHolder holder, Preset preset) {
		if (holder == null && preset != null) {
			holder = preset.getHolder();
		}
		Objects.requireNonNull(holder);
		this.uri = uri;
		this.holder = holder;
		client = HttpClient.getInstance();
		if (preset instanceof TimeoutsPreset) {
			setTimeouts(((TimeoutsPreset) preset).getConnectTimeout(), ((TimeoutsPreset) preset).getReadTimeout());
		}
		if (preset instanceof OutputListenerPreset) {
			setOutputListener(((OutputListenerPreset) preset).getOutputListener());
		}
		if (preset instanceof RangePreset) {
			RangePreset rangePreset = (RangePreset) preset;
			setRange(rangePreset.getRangeStart(), rangePreset.getRangeEnd());
		}
	}

	// TODO CHAN
	// Remove this constructor after updating
	// alphachan alterchan brchan chaosach exach fiftyfive fourplebs haibane kropyvach lainchan nulltirech onechanca
	// synch twentyseven uboachan wizardchan
	// Added: 18.10.20 18:58
	@Public
	public HttpRequest(Uri uri, HttpHolder holder) {
		this(uri, holder, null);
	}

	@Public
	public HttpRequest(Uri uri, Preset preset) {
		this(uri, null, preset);
	}

	private HttpRequest setMethod(RequestMethod method, RequestEntity entity) {
		requestMethod = method;
		requestEntity = entity;
		return this;
	}

	@Public
	public HttpRequest setGetMethod() {
		return setMethod(RequestMethod.GET, null);
	}

	@Public
	public HttpRequest setHeadMethod() {
		return setMethod(RequestMethod.HEAD, null);
	}

	@Public
	public HttpRequest setPostMethod(RequestEntity entity) {
		return setMethod(RequestMethod.POST, entity);
	}

	@Public
	public HttpRequest setPutMethod(RequestEntity entity) {
		return setMethod(RequestMethod.PUT, entity);
	}

	@Public
	public HttpRequest setDeleteMethod(RequestEntity entity) {
		return setMethod(RequestMethod.DELETE, entity);
	}

	@Public
	public HttpRequest setSuccessOnly(boolean successOnly) {
		this.successOnly = successOnly;
		return this;
	}

	@Public
	public HttpRequest setRedirectHandler(RedirectHandler redirectHandler) {
		if (redirectHandler == null) {
			throw new NullPointerException();
		}
		this.redirectHandler = redirectHandler;
		return this;
	}

	@Public
	public HttpRequest setValidator(HttpValidator validator) {
		this.validator = validator;
		return this;
	}

	@Public
	public HttpRequest setKeepAlive(boolean keepAlive) {
		this.keepAlive = keepAlive;
		return this;
	}

	@Public
	public HttpRequest setTimeouts(int connectTimeout, int readTimeout) {
		if (connectTimeout >= 0) {
			this.connectTimeout = connectTimeout;
		}
		if (readTimeout >= 0) {
			this.readTimeout = readTimeout;
		}
		return this;
	}

	@Public
	public HttpRequest setDelay(int delay) {
		this.delay = delay;
		return this;
	}

	public HttpRequest setOutputListener(OutputListener listener) {
		outputListener = listener;
		return this;
	}

	public HttpRequest setRange(long start, long end) {
		this.rangeStart = start;
		this.rangeEnd = end;
		return this;
	}

	private HttpRequest addHeader(Pair<String, String> header) {
		if (header != null && header.first != null && header.second != null) {
			if (headers == null) {
				headers = new ArrayList<>();
			}
			headers.add(header);
		}
		return this;
	}

	@Public
	public HttpRequest addHeader(String name, String value) {
		return addHeader(new Pair<>(name, value));
	}

	@Public
	public HttpRequest clearHeaders() {
		headers = null;
		return this;
	}

	@Public
	public HttpRequest addCookie(String name, String value) {
		if (name != null && value != null) {
			if (cookieBuilder == null) {
				cookieBuilder = new CookieBuilder();
			}
			cookieBuilder.append(name, value);
		}
		return this;
	}

	@Public
	public HttpRequest addCookie(String cookie) {
		if (cookie != null) {
			if (cookieBuilder == null) {
				cookieBuilder = new CookieBuilder();
			}
			cookieBuilder.append(cookie);
		}
		return this;
	}

	@Public
	public HttpRequest addCookie(CookieBuilder builder) {
		if (builder != null) {
			if (cookieBuilder == null) {
				cookieBuilder = new CookieBuilder();
			}
			cookieBuilder.append(builder);
		}
		return this;
	}

	@Public
	public HttpRequest clearCookies() {
		cookieBuilder = null;
		return this;
	}

	@Public
	public HttpRequest copy() {
		HttpRequest request = new HttpRequest(uri, holder);
		request.setMethod(requestMethod, requestEntity);
		request.setSuccessOnly(successOnly);
		request.setRedirectHandler(redirectHandler);
		request.setValidator(validator);
		request.setKeepAlive(keepAlive);
		request.setOutputListener(outputListener);
		request.setTimeouts(connectTimeout, readTimeout);
		request.setDelay(delay);
		if (headers != null) {
			request.headers = new ArrayList<>(headers);
		}
		request.addCookie(cookieBuilder);
		return request;
	}

	@Public
	public HttpResponse perform() throws HttpException {
		boolean verifyCertificate = holder.chan.locator.isUseHttps() && Preferences.isVerifyCertificate();
		HttpSession session = holder.createSession(client, uri, client.getProxy(holder.chan),
				verifyCertificate, delay, 10);
		return client.execute(session, this);
	}

	// TODO CHAN
	// Remove this method after updating
	// alterchan chiochan chuckdfwk diochan kurisach nulltirech owlchan ponyach ponychan sevenchan shanachan taima
	// valkyria
	// Added: 18.10.20 18:58
	@Deprecated
	@Public
	public HttpHolder execute() throws HttpException {
		perform();
		return holder;
	}

	// TODO CHAN
	// Remove this method after updating
	// allchan alphachan alterchan anonfm archiverbt brchan chaosach chiochan chuckdfwk dangeru desustorage diochan
	// endchan exach fiftyfive fourplebs haibane horochan kropyvach kurisach lainchan lolifox nulldvachin nulltirech
	// onechanca owlchan ponyach ponychan princessluna randomarchive sevenchan shanachan synch taima tiretirech tumbach
	// twentyseven uboachan valkyria wizardchan
	// Added: 18.10.20 18:58
	@Deprecated
	@Public
	public HttpResponse read() throws HttpException {
		return perform();
	}
}
