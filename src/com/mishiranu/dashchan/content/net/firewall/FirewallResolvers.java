package com.mishiranu.dashchan.content.net.firewall;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import chan.content.Chan;
import chan.content.ChanConfiguration;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.http.CookieBuilder;
import chan.http.FirewallResolver;
import chan.http.HttpClient;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpResponse;
import chan.util.StringUtils;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.MainApplication;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.async.ReadCaptchaTask;
import com.mishiranu.dashchan.content.net.RecaptchaReader;
import com.mishiranu.dashchan.content.service.webview.IRequestCallback;
import com.mishiranu.dashchan.content.service.webview.IWebViewService;
import com.mishiranu.dashchan.content.service.webview.WebViewExtra;
import com.mishiranu.dashchan.content.service.webview.WebViewService;
import com.mishiranu.dashchan.ui.ForegroundManager;
import com.mishiranu.dashchan.util.Hasher;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FirewallResolvers extends FirewallResolver.Implementation {
	private static final int WEB_VIEW_TIMEOUT = 20000;

	private static class Key implements FirewallResolver.Exclusive.Key {
		public static class Generator {
			private ByteArrayOutputStream output;
			private OutputStreamWriter writer;

			@SuppressWarnings("CharsetObjectCanBeUsed")
			public void append(String key, String value) {
				try {
					if (output == null) {
						output = new ByteArrayOutputStream();
						writer = new OutputStreamWriter(output, "UTF-8");
					}
					writer.write(key);
					writer.write('=');
					if (value != null) {
						writer.write(value);
					}
					writer.flush();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}

			public String generate() {
				if (output != null) {
					byte[] bytes = output.toByteArray();
					if (bytes.length > 0) {
						return StringUtils.formatHex(Hasher.getInstanceSha256().calculate(bytes));
					}
				}
				return null;
			}
		}

		private final String hash;

		private Key(String hash) {
			this.hash = hash;
		}

		@Override
		public String formatKey(String value) {
			return hash != null ? value + "_" + hash : value;
		}

		@Override
		public String formatTitle(String value) {
			return hash != null ? value + " #" + hash.substring(0, Math.min(8, hash.length())) : value;
		}
	}

	private static abstract class BaseSession implements FirewallResolver.Session {
		private final Uri uri;
		private final Chan chan;
		private final FirewallResolver.Identifier identifier;

		public BaseSession(Uri uri, Chan chan, FirewallResolver.Identifier identifier) {
			this.uri = uri;
			this.chan = chan;
			this.identifier = identifier;
		}

		@Override
		public Uri getUri() {
			return uri;
		}

		@Override
		public Chan getChan() {
			return chan;
		}

		@Override
		public ChanConfiguration getChanConfiguration() {
			return chan.configuration;
		}

		@Override
		public FirewallResolver.Identifier getIdentifier() {
			return identifier;
		}

		@Override
		public FirewallResolver.Exclusive.Key getKey(FirewallResolver.Identifier.Flag... flags) {
			FirewallResolver.Identifier identifier = this.identifier;
			Key.Generator generator = new Key.Generator();
			for (FirewallResolver.Identifier.Flag flag : flags) {
				switch (flag) {
					case USER_AGENT: {
						if (!identifier.defaultUserAgent) {
							generator.append("user_agent", identifier.userAgent);
						}
						break;
					}
					default: {
						throw new IllegalArgumentException();
					}
				}
			}
			return new Key(generator.generate());
		}
	}

	private class CheckSession extends BaseSession {
		private final HttpHolder holder;
		private final boolean resolve;
		private final boolean exclusive;

		private CheckSession(Uri uri, HttpHolder holder, Chan chan,
				FirewallResolver.Identifier identifier, boolean resolve, boolean exclusive) {
			super(uri, chan, identifier);
			this.holder = holder;
			this.resolve = resolve;
			this.exclusive = exclusive;
		}

		@Override
		public HttpHolder getHolder() {
			if (exclusive) {
				return holder;
			} else {
				throw new IllegalStateException();
			}
		}

		@Override
		public boolean isResolveRequest() {
			return resolve;
		}

		@Override
		public <Result> Result resolveWebView(FirewallResolver.WebViewClient<Result> webViewClient)
				throws FirewallResolver.CancelException, InterruptedException {
			if (exclusive) {
				return FirewallResolvers.this.resolveWebView(this, webViewClient);
			} else {
				throw new IllegalStateException();
			}
		}
	}

	private static class CookieSession extends BaseSession {
		public CookieSession(Uri uri, Chan chan, FirewallResolver.Identifier identifier) {
			super(uri, chan, identifier);
		}

		@Override
		public Uri getUri() {
			throw new IllegalStateException();
		}

		@Override
		public HttpHolder getHolder() {
			throw new IllegalStateException();
		}

		@Override
		public boolean isResolveRequest() {
			return false;
		}

		@Override
		public <Result> Result resolveWebView(FirewallResolver.WebViewClient<Result> webViewClient) {
			throw new IllegalStateException();
		}
	}

	private static class CheckHolder {
		public boolean ready = false;
		public boolean success = false;
	}

	private static class FirewallResolverCaptchaReader implements ReadCaptchaTask.CaptchaReader {
		private final String apiKey;
		private final String referer;
		private final Object challengeExtra;
		private final boolean allowSolveAutomatically;

		public FirewallResolverCaptchaReader(String apiKey, String referer,
				Object challengeExtra, boolean allowSolveAutomatically) {
			this.apiKey = apiKey;
			this.referer = referer;
			this.challengeExtra = challengeExtra;
			this.allowSolveAutomatically = allowSolveAutomatically;
		}

		@Override
		public ReadCaptchaTask.RemoteResult onReadCaptcha(ChanPerformer.ReadCaptchaData data) {
			ChanPerformer.CaptchaData captchaData = new ChanPerformer.CaptchaData();
			captchaData.put(ChanPerformer.CaptchaData.API_KEY, apiKey);
			captchaData.put(ChanPerformer.CaptchaData.REFERER, referer);
			return new ReadCaptchaTask.RemoteResult(new ChanPerformer.ReadCaptchaResult
					(ChanPerformer.CaptchaState.CAPTCHA, captchaData), challengeExtra, allowSolveAutomatically);
		}
	}

	private static class WebViewRequestCallback extends IRequestCallback.Stub {
		public final FirewallResolver.WebViewClient<?> client;
		public final Uri initialUri;
		public final String chanTitle;
		public final Runnable cancel;

		private WebViewRequestCallback(FirewallResolver.WebViewClient<?> client,
				Uri initialUri, String chanTitle, Runnable cancel) {
			this.client = client;
			this.initialUri = initialUri;
			this.chanTitle = chanTitle;
			this.cancel = cancel;
		}

		@Override
		public boolean onPageFinished(String uriString, String cookie, String title) {
			Uri uri;
			try {
				uri = Uri.parse(uriString);
			} catch (Exception e) {
				uri = null;
			}
			Map<String, String> cookies;
			if (cookie != null && !cookie.isEmpty()) {
				cookies = new HashMap<>();
				String[] splitted = cookie.split(";\\s*");
				for (String pair : splitted) {
					int index = pair.indexOf('=');
					if (index >= 0) {
						String key = pair.substring(0, index);
						String value = pair.substring(index + 1);
						cookies.put(key, value);
					}
				}
			} else {
				cookies = Collections.emptyMap();
			}
			try {
				return client.onPageFinished(uri, cookies, title);
			} catch (LinkageError | RuntimeException e) {
				e.printStackTrace();
				return true;
			}
		}

		@Override
		public boolean onLoad(String uriString) {
			Uri uri;
			try {
				uri = Uri.parse(uriString);
			} catch (Exception e) {
				uri = null;
			}
			try {
				return client.onLoad(initialUri, uri);
			} catch (LinkageError | RuntimeException e) {
				e.printStackTrace();
				return false;
			}
		}

		private boolean retry = false;

		private boolean isRetry() {
			try {
				return retry;
			} finally {
				retry = true;
			}
		}

		private boolean interrupted = false;
		private Thread requireThread = null;

		public void interrupt() {
			synchronized (this) {
				interrupted = true;
				if (requireThread != null) {
					requireThread.interrupt();
				}
			}
		}

		private String requireUserCaptcha(String captchaType, String apiKey, String referer,
				Object challengeExtra, boolean allowSolveAutomatically, boolean retry) {
			String description = MainApplication.getInstance().getLocalizedContext().getString
					(R.string.firewall_block__format_sentence, client.getName() + " (" + chanTitle + ")");
			FirewallResolverCaptchaReader reader = new FirewallResolverCaptchaReader(apiKey, referer,
					challengeExtra, allowSolveAutomatically);
			ChanPerformer.CaptchaData captchaData;
			synchronized (this) {
				if (interrupted) {
					return null;
				}
				requireThread = Thread.currentThread();
			}
			try {
				captchaData = ForegroundManager.getInstance().requireUserCaptcha(reader,
						captchaType, null, null, null, null, description, retry);
			} catch (InterruptedException e) {
				return null;
			} finally {
				synchronized (this) {
					if (requireThread == Thread.currentThread()) {
						requireThread = null;
						// Clear interrupted state
						Thread.interrupted();
					}
				}
			}
			if (captchaData == null) {
				cancel.run();
			}
			return captchaData != null ? captchaData.get(ChanPerformer.CaptchaData.INPUT) : null;
		}

		@Override
		public String onRecaptchaV2(String apiKey, boolean invisible, String referer) {
			boolean retry = isRetry();
			boolean allowSolveAutomatically = !retry;
			String captchaType = invisible ? ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2_INVISIBLE
					: ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2;
			RecaptchaReader.ChallengeExtra challengeExtra;
			HttpHolder holder = new HttpHolder(Chan.getFallback());
			try (HttpHolder.Use ignored = holder.use()) {
				challengeExtra = RecaptchaReader.getInstance().getChallenge2(holder,
						apiKey, invisible, referer, Preferences.isRecaptchaJavascript(),
						true, allowSolveAutomatically);
			} catch (RecaptchaReader.CancelException | HttpException e) {
				return null;
			}
			try {
				if (challengeExtra != null && challengeExtra.response != null) {
					return challengeExtra.response;
				}
				return requireUserCaptcha(captchaType, apiKey, referer, challengeExtra,
						allowSolveAutomatically, retry);
			} finally {
				if (challengeExtra != null) {
					challengeExtra.cleanup();
				}
			}
		}

		@Override
		public String onHcaptcha(String apiKey, String referer) {
			boolean retry = isRetry();
			boolean allowSolveAutomatically = !retry;
			RecaptchaReader.ChallengeExtra challengeExtra;
			HttpHolder holder = new HttpHolder(Chan.getFallback());
			try (HttpHolder.Use ignored = holder.use()) {
				challengeExtra = RecaptchaReader.getInstance().getChallengeHcaptcha(holder,
						apiKey, referer, true, allowSolveAutomatically);
			} catch (RecaptchaReader.CancelException | HttpException e) {
				return null;
			}
			try {
				if (challengeExtra != null && challengeExtra.response != null) {
					return challengeExtra.response;
				}
				return requireUserCaptcha(ChanConfiguration.CAPTCHA_TYPE_HCAPTCHA,
						apiKey, referer, challengeExtra, allowSolveAutomatically, retry);
			} finally {
				if (challengeExtra != null) {
					challengeExtra.cleanup();
				}
			}
		}
	}

	public static abstract class WebViewClientWithExtra<Result> extends FirewallResolver.WebViewClient<Result> {
		private final WebViewExtra extra;

		public WebViewClientWithExtra(String name, WebViewExtra extra) {
			super(name);
			this.extra = extra;
		}
	}

	private <T> T resolveWebView(FirewallResolver.Session session, FirewallResolver.WebViewClient<T> client)
			throws FirewallResolver.CancelException, InterruptedException {
		Context context = MainApplication.getInstance();
		class Status {
			boolean established;
			IWebViewService service;
			boolean cancel;
		}
		Status status = new Status();
		ServiceConnection connection = new ServiceConnection() {
			@Override
			public void onServiceConnected(ComponentName componentName, IBinder binder) {
				synchronized (status) {
					status.established = true;
					status.service = IWebViewService.Stub.asInterface(binder);
					status.notifyAll();
				}
			}

			@Override
			public void onServiceDisconnected(ComponentName componentName) {
				synchronized (status) {
					status.established = true;
					status.service = null;
					status.notifyAll();
				}
			}
		};

		try {
			context.bindService(new Intent(context, WebViewService.class), connection, Context.BIND_AUTO_CREATE);
			IWebViewService service;
			synchronized (status) {
				long startTime = SystemClock.elapsedRealtime();
				long waitTime = 10000;
				while (!status.established) {
					long time = waitTime - (SystemClock.elapsedRealtime() - startTime);
					if (time <= 0) {
						break;
					}
					status.wait(time);
				}
				service = status.service;
			}
			if (service != null) {
				boolean[] finished = {false};
				Chan chan = session.getChan();
				Uri initialUri = session.getUri().buildUpon().clearQuery().encodedFragment(null).build();
				String chanTitle = chan.configuration.getTitle();
				String userAgent = session.getIdentifier().userAgent;
				HttpClient.ProxyData proxyData = HttpClient.getInstance().getProxyData(chan);
				boolean verifyCertificate = chan.locator.isUseHttps() && Preferences.isVerifyCertificate();
				WebViewRequestCallback requestCallback = new WebViewRequestCallback(client, initialUri, chanTitle,
						() -> status.cancel = true);
				String requestId = UUID.randomUUID().toString();
				Thread blockingCallThread = new Thread(() -> {
					WebViewExtra extra = client instanceof WebViewClientWithExtra
							? ((WebViewClientWithExtra<?>) client).extra : null;
					try {
						boolean result = service.loadWithCookieResult(requestId, initialUri.toString(), userAgent,
								proxyData != null && proxyData.socks, proxyData != null ? proxyData.host : null,
								proxyData != null ? proxyData.port : 0, verifyCertificate, WEB_VIEW_TIMEOUT,
								extra, requestCallback);
						synchronized (finished) {
							finished[0] = result;
						}
					} catch (RemoteException e) {
						e.printStackTrace();
					}
				});
				blockingCallThread.start();
				try {
					blockingCallThread.join();
				} catch (InterruptedException e) {
					try {
						service.interrupt(requestId);
					} catch (RemoteException e1) {
						e1.printStackTrace();
					}
					requestCallback.interrupt();
					throw e;
				}
				synchronized (finished) {
					if (!finished[0]) {
						return null;
					}
				}
				T result = client.getResult();
				if (result != null) {
					return result;
				}
				if (status.cancel) {
					throw new FirewallResolver.CancelException();
				}
			}
			return null;
		} finally {
			context.unbindService(connection);
		}
	}

	private final HashMap<FirewallResolver.Exclusive.Key, CheckHolder> checkHolders = new HashMap<>();
	private final HashMap<FirewallResolver.Exclusive.Key, Long> lastCheckCancel = new HashMap<>();

	private boolean runExclusive(CheckSession session, FirewallResolver.Exclusive.Key key,
			FirewallResolver.Exclusive exclusive) throws HttpException, InterruptedException {
		if (session.holder == null) {
			throw new IllegalStateException();
		}
		CheckHolder checkHolder;
		boolean handle = false;
		synchronized (lastCheckCancel) {
			Long cancel = lastCheckCancel.get(key);
			if (cancel != null && cancel + 15 * 1000 > SystemClock.elapsedRealtime()) {
				return false;
			}
		}
		synchronized (checkHolders) {
			checkHolder = checkHolders.get(key);
			if (checkHolder == null) {
				checkHolder = new CheckHolder();
				checkHolders.put(key, checkHolder);
				handle = true;
			}
		}

		if (handle) {
			try {
				try (HttpHolder.Use ignored = session.holder.use()) {
					CheckSession exclusiveSession = new CheckSession(session.getUri(), session.holder,
							session.getChan(), session.getIdentifier(), session.resolve, true);
					checkHolder.success = exclusive.resolve(exclusiveSession, key);
				} catch (FirewallResolver.CancelException e) {
					synchronized (lastCheckCancel) {
						lastCheckCancel.put(key, SystemClock.elapsedRealtime());
					}
				}
			} finally {
				synchronized (checkHolder) {
					checkHolder.ready = true;
					checkHolder.notifyAll();
				}
				synchronized (checkHolders) {
					checkHolders.remove(key);
				}
			}
		} else {
			synchronized (checkHolder) {
				while (!checkHolder.ready) {
					checkHolder.wait();
				}
			}
		}
		return checkHolder.success;
	}

	private final List<FirewallResolver> resolvers = Arrays
			.asList(new CloudFlareResolver(), new StormWallResolver());

	@Override
	public FirewallResolver.CheckResult checkResponse(Chan chan, Uri uri, HttpHolder holder, HttpResponse response,
			FirewallResolver.Identifier identifier, boolean resolve) throws HttpException, InterruptedException {
		if (chan.locator.getChanHosts(false).contains(uri.getHost())) {
			CheckSession session = new CheckSession(uri, holder, chan, identifier, resolve, false);
			FirewallResolver.CheckResponseResult result = null;
			for (FirewallResolver resolver : resolvers) {
				result = resolver.checkResponse(session, response);
				if (result != null) {
					break;
				}
			}
			if (result == null) {
				List<FirewallResolver> resolvers = chan.performer.getFirewallResolvers();
				for (FirewallResolver resolver : resolvers) {
					result = resolver.checkResponse(session, response);
					if (result != null) {
						break;
					}
				}
			}
			if (result != null && result.key != null && result.exclusive != null) {
				boolean resolved = resolve && runExclusive(session, result.key, result.exclusive);
				return new FirewallResolver.CheckResult(resolved, result.retransmitOnSuccess);
			}
		}
		return null;
	}

	@Override
	public CookieBuilder collectCookies(Chan chan, Uri uri, FirewallResolver.Identifier identifier, boolean safe) {
		CookieBuilder cookieBuilder = new CookieBuilder();
		CookieSession session = new CookieSession(uri, chan, identifier);
		for (FirewallResolver resolver : resolvers) {
			resolver.collectCookies(session, cookieBuilder);
		}
		List<FirewallResolver> resolvers = chan.performer.getFirewallResolvers();
		if (!resolvers.isEmpty()) {
			try {
				for (FirewallResolver resolver : resolvers) {
					resolver.collectCookies(session, cookieBuilder);
				}
			} catch (LinkageError | RuntimeException e) {
				if (safe) {
					ExtensionException.logException(e, true);
				} else {
					throw e;
				}
			}
		}
		return cookieBuilder;
	}
}
