package com.mishiranu.dashchan.content.net;

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
import chan.http.HttpClient;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpResponse;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.AdvancedPreferences;
import com.mishiranu.dashchan.content.MainApplication;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.async.ReadCaptchaTask;
import com.mishiranu.dashchan.content.service.webview.IRequestCallback;
import com.mishiranu.dashchan.content.service.webview.IWebViewService;
import com.mishiranu.dashchan.content.service.webview.WebViewExtra;
import com.mishiranu.dashchan.content.service.webview.WebViewService;
import com.mishiranu.dashchan.ui.ForegroundManager;
import com.mishiranu.dashchan.util.Log;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RelayBlockResolver {
	private static final RelayBlockResolver INSTANCE = new RelayBlockResolver();
	private static final int WEB_VIEW_TIMEOUT = 20000;

	public static RelayBlockResolver getInstance() {
		return INSTANCE;
	}

	private RelayBlockResolver() {}

	public static class Session {
		public final Chan chan;
		public final Uri uri;
		public final HttpHolder holder;

		private Session(Chan chan, Uri uri, HttpHolder holder) {
			this.chan = chan;
			this.uri = uri;
			this.holder = holder;
		}
	}

	public interface Resolver {
		boolean resolve(RelayBlockResolver resolver, Session session)
				throws CancelException, HttpException, InterruptedException;
	}

	public static final class CancelException extends Exception {}

	public interface WebViewClient<Result> {
		String getName();
		Result takeResult();

		default boolean onPageFinished(String uriString, Map<String, String> cookies, String title) {
			return true;
		}

		default boolean onLoad(Uri initialUri, Uri uri) {
			return true;
		}

		default WebViewExtra getExtra() {
			return null;
		}
	}

	public interface ResolverFactory {
		Resolver newResolver();
	}

	private static class CheckHolder {
		public final Resolver resolver;

		public boolean ready = false;
		public boolean success = false;

		private CheckHolder(Resolver resolver) {
			this.resolver = resolver;
		}
	}

	public static class Result {
		public final boolean blocked;
		public final boolean resolved;

		public Result(boolean blocked, boolean resolved) {
			this.blocked = blocked;
			this.resolved = resolved;
		}
	}

	private static class RelayBlockCaptchaReader implements ReadCaptchaTask.CaptchaReader {
		private final String apiKey;
		private final String referer;
		private final Object challengeExtra;
		private final boolean allowSolveAutomatically;

		public RelayBlockCaptchaReader(String apiKey, String referer,
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
		public final WebViewClient<?> client;
		public final Uri initialUri;
		public final String chanTitle;
		public final Runnable cancel;

		private WebViewRequestCallback(WebViewClient<?> client, Uri initialUri, String chanTitle, Runnable cancel) {
			this.client = client;
			this.initialUri = initialUri;
			this.chanTitle = chanTitle;
			this.cancel = cancel;
		}

		@Override
		public boolean onPageFinished(String uriString, String cookie, String title) {
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
			return client.onPageFinished(uriString, cookies, title);
		}

		@Override
		public boolean onLoad(String uriString) {
			return client.onLoad(initialUri, Uri.parse(uriString));
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
					(R.string.relay_block__format_sentence, client.getName() + " (" + chanTitle + ")");
			RelayBlockCaptchaReader reader = new RelayBlockCaptchaReader(apiKey, referer,
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

	public <T> T resolveWebView(Session session, WebViewClient<T> client)
			throws CancelException, InterruptedException {
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
				Uri initialUri = session.uri.buildUpon().clearQuery().encodedFragment(null).build();
				String userAgent = AdvancedPreferences.getUserAgent(session.chan.name);
				String chanTitle = session.chan.configuration.getTitle();
				HttpClient.ProxyData proxyData = HttpClient.getInstance().getProxyData(session.chan);
				boolean verifyCertificate = session.chan.locator.isUseHttps() && Preferences.isVerifyCertificate();
				WebViewRequestCallback requestCallback = new WebViewRequestCallback(client, initialUri, chanTitle,
						() -> status.cancel = true);
				String requestId = UUID.randomUUID().toString();
				Thread blockingCallThread = new Thread(() -> {
					try {
						boolean result = service.loadWithCookieResult(requestId, initialUri.toString(), userAgent,
								proxyData != null && proxyData.socks, proxyData != null ? proxyData.host : null,
								proxyData != null ? proxyData.port : 0, verifyCertificate, WEB_VIEW_TIMEOUT,
								client.getExtra(), requestCallback);
						synchronized (finished) {
							finished[0] = result;
						}
					} catch (RemoteException e) {
						Log.persistent().stack(e);
					}
				});
				blockingCallThread.start();
				try {
					blockingCallThread.join();
				} catch (InterruptedException e) {
					try {
						service.interrupt(requestId);
					} catch (RemoteException e1) {
						Log.persistent().stack(e1);
					}
					requestCallback.interrupt();
					throw e;
				}
				synchronized (finished) {
					if (!finished[0]) {
						return null;
					}
				}
				T result = client.takeResult();
				if (result != null) {
					return result;
				}
				if (status.cancel) {
					throw new CancelException();
				}
			}
			return null;
		} finally {
			context.unbindService(connection);
		}
	}

	private final HashMap<String, CheckHolder> checkHolders = new HashMap<>();
	private final HashMap<String, Long> lastCheckCancel = new HashMap<>();

	public boolean runExclusive(Chan chan, Uri uri, HttpHolder holder,
			ResolverFactory resolverFactory) throws HttpException, InterruptedException {
		CheckHolder checkHolder;
		boolean handle = false;
		synchronized (lastCheckCancel) {
			Long cancel = lastCheckCancel.get(chan.name);
			if (cancel != null && cancel + 15 * 1000 > SystemClock.elapsedRealtime()) {
				return false;
			}
		}
		synchronized (checkHolders) {
			checkHolder = checkHolders.get(chan.name);
			if (checkHolder == null) {
				checkHolder = new CheckHolder(resolverFactory.newResolver());
				checkHolders.put(chan.name, checkHolder);
				handle = true;
			}
		}

		if (handle) {
			try {
				Session session = new Session(chan, uri, holder);
				try (HttpHolder.Use ignored = holder.use()) {
					checkHolder.success = checkHolder.resolver.resolve(this, session);
				} catch (CancelException e) {
					synchronized (lastCheckCancel) {
						lastCheckCancel.put(chan.name, SystemClock.elapsedRealtime());
					}
				}
			} finally {
				synchronized (checkHolder) {
					checkHolder.ready = true;
					checkHolder.notifyAll();
				}
				synchronized (checkHolders) {
					checkHolders.remove(chan.name);
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

	public Result checkResponse(Chan chan, Uri uri, HttpHolder holder, HttpResponse response, boolean resolve)
			throws HttpException, InterruptedException {
		if (chan.locator.getChanHosts(false).contains(uri.getHost())) {
			Result result = CloudFlareResolver.getInstance()
					.checkResponse(this, chan, uri, holder, response, resolve);
			if (!result.blocked) {
				result = StormWallResolver.getInstance()
						.checkResponse(this, chan, uri, holder, response, resolve);
			}
			return result;
		}
		return new Result(false, false);
	}

	public Map<String, String> getCookies(Chan chan) {
		Map<String, String> cookies = null;
		cookies = CloudFlareResolver.getInstance().addCookies(chan, cookies);
		cookies = StormWallResolver.getInstance().addCookies(chan, cookies);
		return cookies != null ? cookies : Collections.emptyMap();
	}
}
