package com.mishiranu.dashchan.content.net;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.content.ChanPerformer;
import chan.http.HttpClient;
import chan.http.HttpException;
import chan.http.HttpHolder;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class RelayBlockResolver {
	private static final RelayBlockResolver INSTANCE = new RelayBlockResolver();
	private static final int WEB_VIEW_TIMEOUT = 20000;

	public static RelayBlockResolver getInstance() {
		return INSTANCE;
	}

	private RelayBlockResolver() {}

	public interface Client {
		String getName();
		boolean handleResult(String chanName);

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

	public interface ClientFactory {
		Client newClient();
	}

	private static class CheckHolder {
		public final Client client;

		public boolean ready = false;
		public boolean success = false;

		private CheckHolder(Client client) {
			this.client = client;
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

		public RelayBlockCaptchaReader(String apiKey, String referer, Object challengeExtra) {
			this.apiKey = apiKey;
			this.referer = referer;
			this.challengeExtra = challengeExtra;
		}

		@Override
		public ReadCaptchaTask.Result onReadCaptcha(ChanPerformer.ReadCaptchaData data) {
			ChanPerformer.CaptchaData captchaData = new ChanPerformer.CaptchaData();
			captchaData.put(ChanPerformer.CaptchaData.API_KEY, apiKey);
			captchaData.put(ChanPerformer.CaptchaData.REFERER, referer);
			return new ReadCaptchaTask.Result(new ChanPerformer.ReadCaptchaResult
					(ChanPerformer.CaptchaState.CAPTCHA, captchaData), challengeExtra);
		}
	}

	private static class RequestCallback extends IRequestCallback.Stub {
		public final CheckHolder checkHolder;
		public final Uri initialUri;
		public final Runnable cancel;

		private RequestCallback(CheckHolder checkHolder, Uri initialUri, Runnable cancel) {
			this.checkHolder = checkHolder;
			this.initialUri = initialUri;
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
			return checkHolder.client.onPageFinished(uriString, cookies, title);
		}

		@Override
		public boolean onLoad(String uriString) {
			return checkHolder.client.onLoad(initialUri, Uri.parse(uriString));
		}

		private boolean captchaRetry = false;

		private String requireUserCaptcha(String captchaType, String apiKey, String referer,
				Object challengeExtra) {
			boolean retry = captchaRetry;
			captchaRetry = true;
			String description = MainApplication.getInstance().getLocalizedContext().getString
					(R.string.relay_block__format_sentence, checkHolder.client.getName());
			RelayBlockCaptchaReader reader = new RelayBlockCaptchaReader(apiKey, referer, challengeExtra);
			ChanPerformer.CaptchaData captchaData = ForegroundManager.getInstance().requireUserCaptcha(reader,
					captchaType, null, null, null, null, description, retry);
			if (captchaData == null) {
				cancel.run();
			}
			return captchaData != null ? captchaData.get(ChanPerformer.CaptchaData.INPUT) : null;
		}

		@Override
		public String onRecaptchaV2(String apiKey, boolean invisible, String referer) {
			String captchaType = invisible ? ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2_INVISIBLE
					: ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2;
			RecaptchaReader.ChallengeExtra challengeExtra;
			try (HttpHolder holder = new HttpHolder()) {
				challengeExtra = RecaptchaReader.getInstance().getChallenge2(holder, apiKey, invisible, referer,
						Preferences.isRecaptchaJavascript(), true);
			} catch (RecaptchaReader.CancelException | HttpException e) {
				return null;
			}
			try {
				if (challengeExtra != null && challengeExtra.response != null) {
					return challengeExtra.response;
				}
				return requireUserCaptcha(captchaType, apiKey, referer, challengeExtra);
			} finally {
				if (challengeExtra != null) {
					challengeExtra.cleanup();
				}
			}
		}

		@Override
		public String onHcaptcha(String apiKey, String referer) {
			RecaptchaReader.ChallengeExtra challengeExtra;
			try {
				challengeExtra = RecaptchaReader.getInstance().getChallengeHcaptcha(apiKey, referer, true);
			} catch (RecaptchaReader.CancelException | HttpException e) {
				return null;
			}
			try {
				if (challengeExtra != null && challengeExtra.response != null) {
					return challengeExtra.response;
				}
				return requireUserCaptcha(ChanConfiguration.CAPTCHA_TYPE_HCAPTCHA,
						apiKey, referer, challengeExtra);
			} finally {
				if (challengeExtra != null) {
					challengeExtra.cleanup();
				}
			}
		}
	}

	private final HashMap<String, CheckHolder> checkHolders = new HashMap<>();
	private final HashMap<String, Long> lastCheckCancel = new HashMap<>();

	public boolean runWebView(String chanName, Uri uri, ClientFactory clientFactory) {
		CheckHolder checkHolder;
		boolean handle = false;
		synchronized (lastCheckCancel) {
			Long cancel = lastCheckCancel.get(chanName);
			if (cancel != null && cancel + 15 * 1000 > SystemClock.elapsedRealtime()) {
				return false;
			}
		}
		synchronized (checkHolders) {
			checkHolder = checkHolders.get(chanName);
			if (checkHolder == null) {
				checkHolder = new CheckHolder(clientFactory.newClient());
				checkHolders.put(chanName, checkHolder);
				handle = true;
			}
		}

		if (handle) {
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
						try {
							status.wait(time);
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}
					}
					service = status.service;
				}
				if (service != null) {
					boolean finished = false;
					Uri initialUri = uri.buildUpon().clearQuery().encodedFragment(null).build();
					try {
						ChanLocator locator = ChanLocator.get(chanName);
						String userAgent = AdvancedPreferences.getUserAgent(chanName);
						HttpClient.ProxyData proxyData = HttpClient.getInstance().getProxyData(chanName);
						boolean verifyCertificate = locator.isUseHttps() && Preferences.isVerifyCertificate();
						IRequestCallback requestCallback = new RequestCallback(checkHolder,
								initialUri, () -> status.cancel = true);
						finished = service.loadWithCookieResult(initialUri.toString(), userAgent,
								proxyData != null && proxyData.socks, proxyData != null ? proxyData.host : null,
								proxyData != null ? proxyData.port : 0, verifyCertificate, WEB_VIEW_TIMEOUT,
								checkHolder.client.getExtra(), requestCallback);
					} catch (RemoteException e) {
						e.printStackTrace();
					}
					checkHolder.success = finished && checkHolder.client.handleResult(chanName);
				}
			} finally {
				context.unbindService(connection);
			}

			synchronized (checkHolder) {
				checkHolder.ready = true;
				checkHolder.notifyAll();
			}
			if (status.cancel) {
				synchronized (lastCheckCancel) {
					lastCheckCancel.put(chanName, SystemClock.elapsedRealtime());
				}
			}
			synchronized (checkHolders) {
				checkHolders.remove(chanName);
			}
		} else {
			synchronized (checkHolder) {
				while (!checkHolder.ready) {
					try {
						checkHolder.wait();
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						break;
					}
				}
			}
		}
		return checkHolder.success;
	}

	public Result checkResponse(String chanName, Uri uri, HttpHolder holder) throws HttpException {
		if (ChanLocator.get(chanName).getChanHosts(false).contains(uri.getHost())) {
			Result result = CloudFlareResolver.getInstance().checkResponse(this, chanName, uri, holder);
			if (!result.blocked) {
				result = StormWallResolver.getInstance().checkResponse(this, chanName, uri, holder);
			}
			return result;
		}
		return new Result(false, false);
	}

	public Map<String, String> getCookies(String chanName) {
		Map<String, String> cookies = null;
		cookies = CloudFlareResolver.getInstance().addCookies(chanName, cookies);
		cookies = StormWallResolver.getInstance().addCookies(chanName, cookies);
		return cookies != null ? cookies : Collections.emptyMap();
	}
}
