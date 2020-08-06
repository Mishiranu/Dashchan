package com.mishiranu.dashchan.content.net;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import chan.content.ChanLocator;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.util.StringUtils;
import com.mishiranu.dashchan.content.MainApplication;
import com.mishiranu.dashchan.preference.AdvancedPreferences;
import com.mishiranu.dashchan.util.WebViewUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class RelayBlockResolver {
	private static final RelayBlockResolver INSTANCE = new RelayBlockResolver();
	private static final int WEB_VIEW_TIMEOUT = 20000;

	private static final int MESSAGE_HANDLE_WEB_VIEW = 1;
	private static final int MESSAGE_HANDLE_NEXT_WEB_VIEW = 2;

	public static RelayBlockResolver getInstance() {
		return INSTANCE;
	}

	private RelayBlockResolver() {}

	public static abstract class Client {
		private final ArrayList<CheckHolder> checkHolders = new ArrayList<>();
		private boolean started = false;

		public final void add(CheckHolder checkHolder) {
			checkHolders.add(checkHolder);
			if (started) {
				synchronized (checkHolder) {
					checkHolder.started = true;
					checkHolder.notifyAll();
				}
			}
		}

		public String getChanName() {
			return checkHolders.get(0).chanName;
		}

		public void notifyStarted() {
			started = true;
			for (CheckHolder checkHolder : checkHolders) {
				synchronized (checkHolder) {
					checkHolder.started = true;
					checkHolder.notifyAll();
				}
			}
		}

		public void notifyReady(boolean success) {
			for (CheckHolder checkHolder : checkHolders) {
				synchronized (checkHolder) {
					checkHolder.success = success;
					checkHolder.ready = true;
					checkHolder.notifyAll();
				}
			}
		}

		public boolean onPageFinished(WebView webView, String uriString) {
			return false;
		}

		public boolean isUriAllowed(Uri uri) {
			return true;
		}

		public String extractCookie(String uriString, String name) {
			String data = CookieManager.getInstance().getCookie(uriString);
			if (data != null) {
				String[] splitted = data.split(";\\s*");
				if (splitted != null) {
					for (int i = 0; i < splitted.length; i++) {
						if (!StringUtils.isEmptyOrWhitespace(splitted[i]) && splitted[i].startsWith(name + "=")) {
							return splitted[i].substring(name.length() + 1);
						}
					}
				}
			}
			return null;
		}
	}

	public interface ClientFactory {
		Client newClient();
	}

	public static class CheckHolder {
		public final String chanName;
		public final ClientFactory clientFactory;

		public volatile boolean started;
		public volatile boolean ready;
		public volatile boolean success;

		public CheckHolder(String chanName, ClientFactory clientFactory) {
			this.chanName = chanName;
			this.clientFactory = clientFactory;
		}

		public void waitReady(boolean infinite) throws InterruptedException {
			synchronized (this) {
				if (infinite) {
					while (!ready) {
						wait();
					}
				} else {
					while (!started) {
						wait();
					}
					long t = System.currentTimeMillis();
					while (!ready) {
						long dt = WEB_VIEW_TIMEOUT + t - System.currentTimeMillis();
						if (dt <= 0) {
							return;
						}
						wait(dt);
					}
				}
			}
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

	private class ResolverClient extends WebViewClient {
		@Override
		public void onPageFinished(WebView view, String url) {
			super.onPageFinished(view, url);

			if (currentClient != null && currentClient.onPageFinished(view, url)) {
				view.stopLoading();
				handleNextWebView();
			}
		}

		@SuppressWarnings("deprecation")
		@Override
		public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
			Uri uri = Uri.parse(url);
			if (currentClient != null && currentClient.isUriAllowed(uri)) {
				return super.shouldInterceptRequest(view, url);
			} else {
				return new WebResourceResponse("text/html", "UTF-8", null);
			}
		}
	}

	private WebView webView;
	private Client currentClient;

	@SuppressLint("SetJavaScriptEnabled")
	private void initWebView() {
		if (webView == null) {
			webView = new WebView(MainApplication.getInstance());
			webView.getSettings().setJavaScriptEnabled(true);
			webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
			webView.getSettings().setAppCacheEnabled(false);
			webView.setWebViewClient(new ResolverClient());
			webView.setLayoutParams(new ViewGroup.LayoutParams(480, 270));
			int measureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
			webView.measure(measureSpec, measureSpec);
			webView.layout(0, 0, webView.getLayoutParams().width, webView.getLayoutParams().height);
			webView.setInitialScale(25);
		}
	}

	private final LinkedHashMap<String, Client> clients = new LinkedHashMap<>();

	private final Handler handler = new Handler(Looper.getMainLooper(), message -> {
		switch (message.what) {
			case MESSAGE_HANDLE_WEB_VIEW: {
				initWebView();
				CheckHolder checkHolder = (CheckHolder) message.obj;
				Client client = clients.get(checkHolder.chanName);
				if (client == null) {
					client = checkHolder.clientFactory.newClient();
					clients.put(checkHolder.chanName, client);
				}
				client.add(checkHolder);
				if (!message.getTarget().hasMessages(MESSAGE_HANDLE_NEXT_WEB_VIEW)) {
					handleWebView(client);
				}
				return true;
			}
			case MESSAGE_HANDLE_NEXT_WEB_VIEW: {
				handleNextWebView();
				return true;
			}
		}
		return false;
	});

	private void handleNextWebView() {
		handler.removeMessages(MESSAGE_HANDLE_NEXT_WEB_VIEW);
		Iterator<LinkedHashMap.Entry<String, Client>> iterator = clients.entrySet().iterator();
		Client client = null;
		if (iterator.hasNext()) {
			iterator.next();
			iterator.remove();
			if (iterator.hasNext()) {
				client = iterator.next().getValue();
			}
		}
		handleWebView(client);
	}

	private void handleWebView(Client client) {
		webView.stopLoading();
		WebViewUtils.clearAll(webView);
		currentClient = client;
		if (client != null) {
			client.notifyStarted();
			String chanName = client.getChanName();
			ChanLocator locator = ChanLocator.get(chanName);
			webView.getSettings().setUserAgentString(AdvancedPreferences.getUserAgent(chanName));
			webView.loadUrl(locator.buildPath().toString());
			handler.sendEmptyMessageDelayed(MESSAGE_HANDLE_NEXT_WEB_VIEW, WEB_VIEW_TIMEOUT);
		}
	}

	public boolean runWebView(String chanName, ClientFactory clientFactory) {
		CheckHolder checkHolder = new CheckHolder(chanName, clientFactory);
		handler.obtainMessage(MESSAGE_HANDLE_WEB_VIEW, checkHolder).sendToTarget();
		try {
			checkHolder.waitReady(false);
			return checkHolder.success;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
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
