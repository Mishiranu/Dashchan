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

package com.mishiranu.dashchan.content.net;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.webkit.CookieManager;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.content.ChanPerformer;
import chan.content.InvalidResponseException;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;
import chan.http.UrlEncodedEntity;
import chan.util.StringUtils;

import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.MainApplication;
import com.mishiranu.dashchan.content.async.ReadCaptchaTask;
import com.mishiranu.dashchan.preference.AdvancedPreferences;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.ui.ForegroundManager;
import com.mishiranu.dashchan.util.WebViewUtils;

public class CloudFlarePasser implements Handler.Callback {
	private static final CloudFlarePasser INSTANCE = new CloudFlarePasser();
	private static final int WEB_VIEW_TIMEOUT = 20000;

	private static final Pattern PATTERN_FORBIDDEN = Pattern.compile("<form class=\\\"challenge-form\\\" " +
			"id=\\\"challenge-form\\\" action=\\\"(.*?)\\\" method=\\\"(.*?)\\\"");
	private static final Pattern PATTERN_UNAVAILABLE = Pattern.compile("<span " +
			"data-translate=\\\"checking_browser\\\">Checking your browser before accessing</span>");

	private static final Pattern ALLOWED_LINKS = Pattern.compile("/?(|cdn-cgi/l/.*)");
	private static final Pattern PATTERN_CAPTCHA = Pattern.compile("data-sitekey=\"(.*?)\"");

	public static final String COOKIE_CLOUDFLARE = "cf_clearance";

	private CloudFlarePasser() {}

	private final Handler handler = new Handler(Looper.getMainLooper(), this);

	private static class CheckHolder {
		public final String chanName;

		public volatile boolean started;
		public volatile boolean ready;
		public volatile boolean success;

		public CheckHolder(String chanName) {
			this.chanName = chanName;
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

	private final LinkedHashMap<String, CloudFlareClient> clientHandlers = new LinkedHashMap<>();

	private static final int MESSAGE_CHECK_JAVASCRIPT = 1;
	private static final int MESSAGE_HANDLE_NEXT_JAVASCRIPT = 2;

	@Override
	public boolean handleMessage(Message msg) {
		switch (msg.what) {
			case MESSAGE_CHECK_JAVASCRIPT: {
				initWebView();
				CheckHolder checkHolder = (CheckHolder) msg.obj;
				CloudFlareClient client = clientHandlers.get(checkHolder.chanName);
				if (client == null) {
					client = new CloudFlareClient(checkHolder.chanName, checkHolder);
					clientHandlers.put(checkHolder.chanName, client);
				} else {
					client.add(checkHolder);
				}
				if (!handler.hasMessages(MESSAGE_HANDLE_NEXT_JAVASCRIPT)) {
					handleJavaScript(client);
					handler.sendEmptyMessageDelayed(MESSAGE_HANDLE_NEXT_JAVASCRIPT, WEB_VIEW_TIMEOUT);
				}
				return true;
			}
			case MESSAGE_HANDLE_NEXT_JAVASCRIPT: {
				handleNextJavaScript();
				return true;
			}
		}
		return false;
	}

	private void handleNextJavaScript() {
		handler.removeMessages(MESSAGE_HANDLE_NEXT_JAVASCRIPT);
		Iterator<LinkedHashMap.Entry<String, CloudFlareClient>> iterator = clientHandlers.entrySet().iterator();
		CloudFlareClient client = null;
		if (iterator.hasNext()) {
			iterator.next();
			iterator.remove();
			if (iterator.hasNext()) {
				client = iterator.next().getValue();
			}
		}
		if (client != null) {
			handleJavaScript(client);
			handler.sendEmptyMessageDelayed(MESSAGE_HANDLE_NEXT_JAVASCRIPT, WEB_VIEW_TIMEOUT);
		}
	}

	private void handleJavaScript(CloudFlareClient client) {
		String chanName = client.chanName;
		client.notifyStarted();
		webView.stopLoading();
		WebViewUtils.clearAll(webView);
		webView.setWebViewClient(client);
		ChanLocator locator = ChanLocator.get(chanName);
		webView.getSettings().setUserAgentString(AdvancedPreferences.getUserAgent(chanName));
		webView.loadUrl(locator.buildPath().toString());
	}

	private class CloudFlareClient extends WebViewClient {
		private final String chanName;
		private final ArrayList<CheckHolder> checkHolders = new ArrayList<>();

		private boolean started = false;
		private boolean wasChecked = false;

		public CloudFlareClient(String chanName, CheckHolder checkHolder) {
			this.chanName = chanName;
			add(checkHolder);
		}

		public void add(CheckHolder checkHolder) {
			checkHolders.add(checkHolder);
			if (started) {
				synchronized (checkHolder) {
					checkHolder.started = true;
					checkHolder.notifyAll();
				}
			}
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

		@Override
		public void onPageFinished(WebView view, String url) {
			super.onPageFinished(view, url);
			if ("Just a moment...".equals(view.getTitle())) {
				wasChecked = true;
			} else {
				String cookie = null;
				boolean success = false;
				if (wasChecked) {
					cookie = StringUtils.nullIfEmpty(extractCookie(url, COOKIE_CLOUDFLARE));
					if (cookie != null) {
						success = true;
					}
				}
				storeCookie(chanName, cookie, url);
				view.stopLoading();
				for (CheckHolder checkHolder : checkHolders) {
					synchronized (checkHolder) {
						checkHolder.success = success;
						checkHolder.ready = true;
						checkHolder.notifyAll();
					}
				}
				handleNextJavaScript();
			}
		}

		@SuppressWarnings("deprecation")
		@Override
		public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
			Uri uri = Uri.parse(url);
			// Disallow downloading all resources instead of chan and cloudflare redirects (see regex pattern)
			if (ALLOWED_LINKS.matcher(uri.getPath()).matches()) {
				return null;
			}
			return new WebResourceResponse("text/html", "UTF-8", null);
		}

		private String extractCookie(String url, String name) {
			String data = CookieManager.getInstance().getCookie(url);
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

	private WebView webView;

	@SuppressLint("SetJavaScriptEnabled")
	private void initWebView() {
		if (webView == null) {
			Context context = MainApplication.getInstance();
			webView = new WebView(context);
			WebSettings settings = webView.getSettings();
			settings.setJavaScriptEnabled(true);
			settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
			settings.setAppCacheEnabled(false);
		}
	}

	public static class Result {
		public final boolean success;
		public final HttpHolder replaceHolder;

		private Result(boolean success, HttpHolder replaceHolder) {
			this.success = success;
			this.replaceHolder = replaceHolder;
		}
	}

	private final HashMap<String, CheckHolder> captchaHolders = new HashMap<>();
	private final HashMap<String, Long> captchaLastCancel = new HashMap<>();

	private Result handleCaptcha(String chanName, Uri requestedUri,
			Uri specialUri, String recaptchaApiKey) throws HttpException {
		CheckHolder checkHolder = null;
		synchronized (captchaLastCancel) {
			Long lastCancelTime = captchaLastCancel.get(chanName);
			if (lastCancelTime != null && System.currentTimeMillis() - lastCancelTime < 5000) {
				return new Result(false, null);
			}
		}
		if (specialUri == null) {
			boolean handle;
			synchronized (captchaHolders) {
				checkHolder = captchaHolders.get(chanName);
				if (checkHolder == null) {
					checkHolder = new CheckHolder(chanName);
					captchaHolders.put(chanName, checkHolder);
					handle = true;
				} else {
					handle = false;
				}
			}
			if (!handle) {
				try {
					checkHolder.waitReady(true);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return new Result(false, null);
				}
				return new Result(checkHolder.success, null);
			}
		}
		HttpHolder holder = new HttpHolder();
		try {
			boolean retry = false;
			while (true) {
				ChanPerformer.CaptchaData captchaData = ForegroundManager.getInstance().requireUserCaptcha
						(new CloudFlareCaptchaReader(recaptchaApiKey, requestedUri.toString()),
						ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2, null, null, null, null,
						R.string.message_cloudflate_block, retry);
				if (captchaData == null) {
					synchronized (captchaLastCancel) {
						captchaLastCancel.put(chanName, System.currentTimeMillis());
					}
					return new Result(false, null);
				}
				if (Thread.currentThread().isInterrupted()) {
					return new Result(false, null);
				}
				String recaptchaResponse = captchaData.get(ChanPerformer.CaptchaData.INPUT);
				ChanLocator locator = ChanLocator.get(chanName);
				if (specialUri != null) {
					new HttpRequest(specialUri, holder).setRedirectHandler(HttpRequest.RedirectHandler.NONE)
							.setPostMethod(new UrlEncodedEntity("g-recaptcha-response", recaptchaResponse))
							.setSuccessOnly(false).setCheckCloudFlare(false).read();
				} else {
					Uri uri = locator.buildQuery("cdn-cgi/l/chk_captcha", "g-recaptcha-response", recaptchaResponse)
							.buildUpon().scheme(requestedUri.getScheme())
							.authority(requestedUri.getAuthority()).build();
					new HttpRequest(uri, holder).setRedirectHandler(HttpRequest.RedirectHandler.NONE)
							.setSuccessOnly(false).setCheckCloudFlare(false).read();
				}
				String cookie = holder.getCookieValue(COOKIE_CLOUDFLARE);
				if (cookie != null) {
					storeCookie(chanName, cookie, null);
					if (checkHolder != null) {
						checkHolder.success = true;
					}
					return new Result(true, specialUri != null ? holder : null);
				}
				retry = true;
			}
		} finally {
			holder.cleanup();
			if (checkHolder != null) {
				synchronized (captchaHolders) {
					captchaHolders.remove(chanName);
				}
				synchronized (checkHolder) {
					checkHolder.ready = true;
					checkHolder.notifyAll();
				}
			}
		}
	}

	private static class CloudFlareCaptchaReader implements ReadCaptchaTask.CaptchaReader {
		private final String recaptchaApiKey;
		private final String referer;

		public CloudFlareCaptchaReader(String recaptchaApiKey, String referer) {
			this.recaptchaApiKey = recaptchaApiKey;
			this.referer = referer;
		}

		@Override
		public ChanPerformer.ReadCaptchaResult onReadCaptcha(ChanPerformer.ReadCaptchaData data)
				throws HttpException, InvalidResponseException {
			ChanPerformer.CaptchaData captchaData = new ChanPerformer.CaptchaData();
			captchaData.put(ChanPerformer.CaptchaData.API_KEY, recaptchaApiKey);
			captchaData.put(ChanPerformer.CaptchaData.REFERER, referer);
			return new ChanPerformer.ReadCaptchaResult(ChanPerformer.CaptchaState.CAPTCHA, captchaData);
		}
	}

	public static Result checkResponse(String chanName, Uri uri, HttpHolder holder) throws HttpException {
		int responseCode = holder.getResponseCode();
		if ((responseCode == HttpURLConnection.HTTP_FORBIDDEN || responseCode == HttpURLConnection.HTTP_UNAVAILABLE)
				&& holder.getHeaderFields().containsKey("CF-RAY")) {
			String responseText = holder.read().getString();
			switch (responseCode) {
				case HttpURLConnection.HTTP_FORBIDDEN: {
					Matcher matcher = PATTERN_FORBIDDEN.matcher(responseText);
					if (matcher.find()) {
						Uri specialUri = null;
						if ("post".equals(matcher.group(2))) {
							specialUri = Uri.parse(matcher.group(1));
							specialUri = specialUri.buildUpon().scheme(uri.getScheme())
									.authority(uri.getAuthority()).build();
						}
						matcher = PATTERN_CAPTCHA.matcher(responseText);
						if (matcher.find()) {
							String captchaApiKey = matcher.group(1);
							return INSTANCE.handleCaptcha(chanName, uri, specialUri, captchaApiKey);
						}
					}
					break;
				}
				case HttpURLConnection.HTTP_UNAVAILABLE: {
					Matcher matcher = PATTERN_UNAVAILABLE.matcher(responseText);
					if (matcher.find()) {
						CheckHolder checkHolder = new CheckHolder(chanName);
						INSTANCE.handler.obtainMessage(MESSAGE_CHECK_JAVASCRIPT, checkHolder).sendToTarget();
						try {
							checkHolder.waitReady(false);
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
							return new Result(false, null);
						}
						return new Result(checkHolder.success, null);
					}
					break;
				}
			}
		}
		return new Result(false, null);
	}

	private static void storeCookie(String chanName, String cookie, String uriString) {
		ChanConfiguration configuration = ChanConfiguration.get(chanName);
		configuration.storeCookie(COOKIE_CLOUDFLARE, cookie, cookie != null ? "CloudFlare" : null);
		configuration.commit();
		Uri uri = uriString != null ? Uri.parse(uriString) : null;
		if (uri != null) {
			ChanLocator locator = ChanLocator.get(chanName);
			String host = uri.getHost();
			if (locator.isConvertableChanHost(host)) {
				locator.setPreferredHost(host);
			}
			Preferences.setUseHttps(chanName, "https".equals(uri.getScheme()));
		}
	}

	public static String getCookie(String chanName) {
		return StringUtils.nullIfEmpty(ChanConfiguration.get(chanName).getCookie(COOKIE_CLOUDFLARE));
	}
}