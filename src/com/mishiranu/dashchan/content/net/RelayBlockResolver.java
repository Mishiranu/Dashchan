package com.mishiranu.dashchan.content.net;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import chan.content.ChanLocator;
import chan.http.HttpClient;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.util.StringUtils;
import com.mishiranu.dashchan.content.AdvancedPreferences;
import com.mishiranu.dashchan.content.MainApplication;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.service.webview.IRequestCallback;
import com.mishiranu.dashchan.content.service.webview.IWebViewService;
import com.mishiranu.dashchan.content.service.webview.WebViewService;
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
		String getCookieName();
		void storeCookie(String chanName, String cookie);

		default boolean onPageFinished(String uriString, String title) {
			return false;
		}

		default boolean onLoad(Uri uri) {
			return true;
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
		public final HttpHolder replaceHolder;

		public Result(boolean blocked, boolean resolved, HttpHolder replaceHolder) {
			this.blocked = blocked;
			this.resolved = resolved;
			this.replaceHolder = replaceHolder;
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
					String cookie = null;
					try {
						CheckHolder checkHolderFinal = checkHolder;
						IRequestCallback requestCallback = new IRequestCallback.Stub() {
							@Override
							public boolean onPageFinished(String uriString, String title) {
								return checkHolderFinal.client.onPageFinished(uriString, title);
							}

							@Override
							public boolean onLoad(String uriString) {
								return checkHolderFinal.client.onLoad(Uri.parse(uriString));
							}
						};
						ChanLocator locator = ChanLocator.get(chanName);
						HttpClient.ProxyData proxyData = HttpClient.getInstance().getProxyData(chanName);
						boolean httpProxy = proxyData != null && !proxyData.socks;
						cookie = service.loadWithCookieResult(checkHolder.client.getCookieName(),
								locator.buildPath().toString(), AdvancedPreferences.getUserAgent(chanName),
								httpProxy ? proxyData.host : null, httpProxy ? proxyData.port : 0,
								locator.isUseHttps() && Preferences.isVerifyCertificate(), WEB_VIEW_TIMEOUT,
								requestCallback);
					} catch (RemoteException e) {
						e.printStackTrace();
					}
					checkHolder.client.storeCookie(chanName, cookie);
					checkHolder.success = !StringUtils.isEmpty(cookie);
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

	public Result checkResponse(String chanName, Uri uri, HttpHolder holder) throws HttpException {
		Result result = CloudFlareResolver.getInstance().checkResponse(this, chanName, uri, holder);
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
