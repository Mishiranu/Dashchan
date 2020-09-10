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

		default boolean onLoad(Uri uri) {
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

		public RelayBlockCaptchaReader(String apiKey, String referer) {
			this.apiKey = apiKey;
			this.referer = referer;
		}

		@Override
		public ChanPerformer.ReadCaptchaResult onReadCaptcha(ChanPerformer.ReadCaptchaData data) {
			ChanPerformer.CaptchaData captchaData = new ChanPerformer.CaptchaData();
			captchaData.put(ChanPerformer.CaptchaData.API_KEY, apiKey);
			captchaData.put(ChanPerformer.CaptchaData.REFERER, referer);
			return new ChanPerformer.ReadCaptchaResult(ChanPerformer.CaptchaState.CAPTCHA, captchaData);
		}
	}

	private final HashMap<String, CheckHolder> checkHolders = new HashMap<>();

	public boolean runWebView(String chanName, ClientFactory clientFactory) {
		CheckHolder checkHolder;
		boolean handle = false;
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
					try {
						CheckHolder checkHolderFinal = checkHolder;
						IRequestCallback requestCallback = new IRequestCallback.Stub() {
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
								return checkHolderFinal.client.onPageFinished(uriString, cookies, title);
							}

							@Override
							public boolean onLoad(String uriString) {
								return checkHolderFinal.client.onLoad(Uri.parse(uriString));
							}

							private boolean captchaRetry = false;

							private String requireUserCaptcha(String captchaType, String apiKey, String referer) {
								boolean retry = captchaRetry;
								captchaRetry = true;
								String description = MainApplication.getInstance().getLocalizedContext().getString
										(R.string.relay_block__format_sentence, checkHolderFinal.client.getName());
								ChanPerformer.CaptchaData captchaData = ForegroundManager.getInstance()
										.requireUserCaptcha(new RelayBlockCaptchaReader(apiKey, referer),
												captchaType, null, null, null, null, description, retry);
								return captchaData != null ? captchaData.get(ChanPerformer.CaptchaData.INPUT) : null;
							}

							@Override
							public String onRecaptchaV2(String apiKey, boolean invisible, String referer) {
								String captchaType = invisible ? ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2_INVISIBLE
										: ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2;
								return requireUserCaptcha(captchaType, apiKey, referer);
							}

							@Override
							public String onHcaptcha(String apiKey, String referer) {
								return requireUserCaptcha(ChanConfiguration.CAPTCHA_TYPE_HCAPTCHA, apiKey, referer);
							}
						};
						ChanLocator locator = ChanLocator.get(chanName);
						HttpClient.ProxyData proxyData = HttpClient.getInstance().getProxyData(chanName);
						finished = service.loadWithCookieResult(locator.buildPath().toString(),
								AdvancedPreferences.getUserAgent(chanName), proxyData != null && proxyData.socks,
								proxyData != null ? proxyData.host : null, proxyData != null ? proxyData.port : 0,
								locator.isUseHttps() && Preferences.isVerifyCertificate(), WEB_VIEW_TIMEOUT,
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

	public Result checkResponse(String chanName, HttpHolder holder) throws HttpException {
		Result result = CloudFlareResolver.getInstance().checkResponse(this, chanName, holder);
		if (!result.blocked) {
			result = StormWallResolver.getInstance().checkResponse(this, chanName, holder);
		}
		return result;
	}

	public Map<String, String> getCookies(String chanName) {
		Map<String, String> cookies = null;
		cookies = CloudFlareResolver.getInstance().addCookies(chanName, cookies);
		cookies = StormWallResolver.getInstance().addCookies(chanName, cookies);
		return cookies != null ? cookies : Collections.emptyMap();
	}
}
