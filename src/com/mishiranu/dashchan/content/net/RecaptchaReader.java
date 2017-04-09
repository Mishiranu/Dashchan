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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Pair;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import chan.content.ChanLocator;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;
import chan.http.UrlEncodedEntity;
import chan.util.StringUtils;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.content.MainApplication;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.preference.AdvancedPreferences;
import com.mishiranu.dashchan.text.HtmlParser;
import com.mishiranu.dashchan.ui.ForegroundManager;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.Log;
import com.mishiranu.dashchan.util.WebViewUtils;

public class RecaptchaReader implements Handler.Callback {
	private static final RecaptchaReader INSTANCE = new RecaptchaReader();

	public static RecaptchaReader getInstance() {
		return INSTANCE;
	}

	private final Handler handler = new Handler(Looper.getMainLooper(), this);

	private RecaptchaReader() {}

	private WebView webView;
	private RecaptchaClient client;

	private String recaptchaV1Html;

	private final Object accessLock = new Object();

	private static final String BASE_URI_STRING = "https://www.google.com/";

	private static final Pattern RECAPTCHA_CHALLENGE_PATTERN = Pattern.compile("[\"'](.{100,}?)[\"']");
	private static final Pattern RECAPTCHA_FALLBACK_PATTERN = Pattern.compile("(?:(?:<label for=\"response\" " +
			"class=\"fbc-imageselect-message-text\">|<div class=\"fbc-imageselect-message-error\">)(.*?)" +
			"(?:</label>|</div>).*?)?value=\"(.{20,}?)\"");
	private static final Pattern RECAPTCHA_RESULT_PATTERN = Pattern.compile("<textarea.*?>(.*?)</textarea>");

	private static final byte[] STUB_IMAGE;

	static {
		Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
		STUB_IMAGE = output.toByteArray();
		bitmap.recycle();
	}

	public String getChallenge2(HttpHolder holder, String apiKey, String referer)
			throws SkipException, CancelException, HttpException {
		if (referer == null) {
			referer = BASE_URI_STRING;
		}
		ChanLocator locator = ChanLocator.getDefault();
		Uri uri = locator.buildQueryWithHost("www.google.com", "recaptcha/api/fallback", "k", apiKey);
		Bitmap captchaImage = null;
		String responseText = new HttpRequest(uri, holder).addCookie(AdvancedPreferences.getGoogleCookie())
				.addHeader("Accept-Language", "en-US").addHeader("Referer", referer).read().getString();
		while (true) {
			Matcher matcher = RECAPTCHA_FALLBACK_PATTERN.matcher(responseText);
			if (matcher.find()) {
				String imageSelectorDescription = matcher.group(1);
				String challenge = matcher.group(2);
				if (imageSelectorDescription != null) {
					if (captchaImage != null) {
						captchaImage.recycle();
					}
					captchaImage = getImage2(holder, apiKey, challenge, null, false).first;
					boolean[] result = ForegroundManager.getInstance().requireUserImageMultipleChoice(3, null,
							splitImages(captchaImage, 3, 3), HtmlParser.clear(imageSelectorDescription), null);
					if (result != null) {
						boolean hasSelected = false;
						UrlEncodedEntity entity = new UrlEncodedEntity("c", challenge);
						for (int i = 0; i < result.length; i++) {
							if (result[i]) {
								entity.add("response", Integer.toString(i));
								hasSelected = true;
							}
						}
						if (!hasSelected) {
							continue;
						}
						responseText = new HttpRequest(uri, holder).setPostMethod(entity)
								.addCookie(AdvancedPreferences.getGoogleCookie())
								.setRedirectHandler(HttpRequest.RedirectHandler.STRICT)
								.addHeader("Accept-Language", "en-US")
								.addHeader("Referer", BASE_URI_STRING).read().getString();
						matcher = RECAPTCHA_RESULT_PATTERN.matcher(responseText);
						if (matcher.find()) {
							String response = matcher.group(1);
							throw new SkipException(response);
						}
						continue;
					}
					throw new CancelException();
				}
				return challenge;
			} else {
				return null;
			}
		}
	}

	private final HashMap<String, Pair<Long, String>> lastChallenges1 = new HashMap<>();

	public String getChallenge1(HttpHolder holder, String apiKey, boolean useJavaScript) throws HttpException {
		if (useJavaScript) {
			synchronized (accessLock) {
				LoadingHolder loadingHolder = new LoadingHolder(apiKey, BASE_URI_STRING);
				handler.sendMessage(handler.obtainMessage(MESSAGE_LOAD, loadingHolder));
				if (loadingHolder.waitForExpiredOrReady()) {
					handler.sendEmptyMessage(MESSAGE_CANCEL);
					return null;
				}
				if (loadingHolder.challenge != null) {
					return loadingHolder.challenge;
				}
				handler.sendEmptyMessage(MESSAGE_CANCEL);
				throw new HttpException(ErrorItem.TYPE_DOWNLOAD, false, false);
			}
		} else {
			ChanLocator locator = ChanLocator.getDefault();
			String challenge = null;
			synchronized (lastChallenges1) {
				Pair<Long, String> pair = lastChallenges1.get(apiKey);
				if (pair != null && System.currentTimeMillis() - pair.first < 10 * 60 * 1000) {
					challenge = pair.second;
				}
			}
			if (challenge == null) {
				Uri uri = locator.buildQueryWithHost("www.google.com", "recaptcha/api/challenge", "k", apiKey);
				String responseText = new HttpRequest(uri, holder).addCookie(AdvancedPreferences.getGoogleCookie())
						.read().getString();
				Matcher matcher = RECAPTCHA_CHALLENGE_PATTERN.matcher(responseText);
				if (!matcher.find()) {
					return null;
				}
				challenge = matcher.group(1);
			}
			Uri uri = locator.buildQueryWithHost("www.google.com", "recaptcha/api/reload", "c", challenge,
					"k", apiKey, "type", "image");
			String responseText = new HttpRequest(uri, holder).addCookie(AdvancedPreferences.getGoogleCookie())
					.read().getString();
			Matcher matcher = RECAPTCHA_CHALLENGE_PATTERN.matcher(responseText);
			if (matcher.find()) {
				challenge = matcher.group(1);
				synchronized (lastChallenges1) {
					lastChallenges1.put(apiKey, new Pair<>(System.currentTimeMillis(), challenge));
				}
				return challenge;
			}
			return null;
		}
	}

	public Pair<Bitmap, Boolean> getImage1(HttpHolder holder, String challenge, boolean transformBlackAndWhite)
			throws HttpException {
		return getImage(holder, null, challenge, null, transformBlackAndWhite, false);
	}

	public Pair<Bitmap, Boolean> getImage2(HttpHolder holder, String apiKey, String challenge, String id,
			boolean transformBlackAndWhite) throws HttpException {
		return getImage(holder, apiKey, challenge, id, transformBlackAndWhite, true);
	}

	private Pair<Bitmap, Boolean> getImage(HttpHolder holder, String apiKey, String challenge, String id,
			boolean transformBlackAndWhite, boolean recaptcha2) throws HttpException {
		ChanLocator locator = ChanLocator.getDefault();
		Uri uri;
		if (recaptcha2) {
			uri = locator.buildQueryWithHost("www.google.com", "recaptcha/api2/payload", "c", challenge, "k", apiKey,
					"id", StringUtils.emptyIfNull(id));
		} else {
			uri = locator.buildQueryWithHost("www.google.com", "recaptcha/api/image", "c", challenge);
		}
		Bitmap image = new HttpRequest(uri, holder).read().getBitmap();
		if (transformBlackAndWhite) {
			transformBlackAndWhite = GraphicsUtils.isBlackAndWhiteCaptchaImage(image);
		}
		return transformBlackAndWhite ? GraphicsUtils.handleBlackAndWhiteCaptchaImage(image) : new Pair<>(image, false);
	}

	// Returns g-recaptcha-response field, or null if captcha invalid.
	public String getResponseField2(HttpHolder holder, String apiKey, String challenge, String input)
			throws HttpException {
		if (apiKey == null || challenge == null) {
			return null;
		}
		ChanLocator locator = ChanLocator.getDefault();
		Uri uri = locator.buildQueryWithHost("www.google.com", "recaptcha/api/fallback", "k", apiKey);
		UrlEncodedEntity entity = new UrlEncodedEntity("c", challenge, "response", input);
		String data = new HttpRequest(uri, holder).setPostMethod(entity)
				.setRedirectHandler(HttpRequest.RedirectHandler.STRICT).read().getString();
		Matcher matcher = RECAPTCHA_RESULT_PATTERN.matcher(data);
		return matcher.find() ? matcher.group(1) : null;
	}

	private static final int WAIT_TIMEOUT = 15000;

	private static class LoadingHolder {
		public final String apiKey;
		public final String referer;

		public String challenge;

		public boolean ready = false;
		public long time;

		public LoadingHolder(String apiKey, String referer) {
			this.apiKey = apiKey;
			this.referer = referer;
			updateTime();
		}

		public void applyReady() {
			synchronized (this) {
				ready = true;
				updateTime();
				notifyAll();
			}
		}

		public void updateTime() {
			synchronized (this) {
				time = System.currentTimeMillis();
			}
		}

		public long getTimeFromLastUpdate() {
			synchronized (this) {
				return System.currentTimeMillis() - time;
			}
		}

		public boolean waitForExpiredOrReady() {
			synchronized (this) {
				while (true) {
					if (ready) {
						break;
					}
					long time = WAIT_TIMEOUT - getTimeFromLastUpdate();
					if (time <= 0L) {
						break;
					}
					try {
						wait(time);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						return true;
					}
				}
			}
			return false;
		}

		public boolean hasValidResult() {
			return challenge != null;
		}

		@Override
		public boolean equals(Object o) {
			if (o == this) {
				return true;
			}
			if (o instanceof LoadingHolder) {
				LoadingHolder loadingHolder = (LoadingHolder) o;
				return loadingHolder.apiKey.equals(apiKey);
			}
			return false;
		}

		@Override
		public int hashCode() {
			return apiKey.hashCode();
		}
	}

	private static final int MESSAGE_LOAD = 1;
	private static final int MESSAGE_CANCEL = 2;

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	@Override
	public boolean handleMessage(Message msg) {
		initWebView();
		switch (msg.what) {
			case MESSAGE_LOAD: {
				LoadingHolder loadingHolder = (LoadingHolder) msg.obj;
				LoadingHolder oldLoadingHolder = client.getLoadingHolder();
				// Refresh if possible (simulate click or call js method, without reload)
				// if script worked correctly last time
				boolean canRefresh = loadingHolder.equals(oldLoadingHolder) && oldLoadingHolder.hasValidResult()
						&& loadingHolder.referer.equals(oldLoadingHolder.referer);
				if (canRefresh) {
					long time = oldLoadingHolder.getTimeFromLastUpdate();
					time = 1000 - time;
					if (time > 0) {
						// Captcha can be reloaded only once per second (recaptcha restriction)
						handler.sendMessageDelayed(Message.obtain(msg), time);
					} else {
						client.setLoadingHolder(loadingHolder);
						webView.loadUrl("javascript:recaptchaReload()");
					}
				} else {
					webView.stopLoading();
					WebViewUtils.clearAll(webView);
					CookieManager cookieManager = CookieManager.getInstance();
					String cookies = AdvancedPreferences.getGoogleCookie();
					if (cookies != null) {
						WebViewUtils.setThirdPartyCookiesEnabled(webView);
						for (String cookie : cookies.split("; *")) {
							cookieManager.setCookie("google.com", cookie + "; path=/; domain=.google.com");
						}
					}
					client.setLoadingHolder(loadingHolder);
					if (recaptchaV1Html == null) {
						recaptchaV1Html = readHtmlAsset("recaptcha-v1.html");
					}
					String data = recaptchaV1Html.replace("__REPLACE_API_KEY__", loadingHolder.apiKey);
					webView.loadDataWithBaseURL(BASE_URI_STRING, data, "text/html", "UTF-8", null);
				}
				return true;
			}
			case MESSAGE_CANCEL: {
				webView.stopLoading();
				client.setLoadingHolder(null);
				return true;
			}
		}
		return false;
	}

	private static interface InterceptResult {
		public WebResourceResponse get();
	}

	private static class StubInterceptResult implements InterceptResult {
		@Override
		public WebResourceResponse get() {
			return new WebResourceResponse("text/html", "UTF-8", null);
		}
	}

	private static class StubImageInterceptResult implements InterceptResult {
		@Override
		public WebResourceResponse get() {
			// 1x1 image is better than text, sometimes recaptcha can be jammed if I return text
			return new WebResourceResponse("image/png", null, new ByteArrayInputStream(STUB_IMAGE));
		}
	}

	private class RecaptchaClient extends WebViewClient {
		private LoadingHolder loadingHolder;

		public void setLoadingHolder(LoadingHolder loadingHolder) {
			this.loadingHolder = loadingHolder;
		}

		public LoadingHolder getLoadingHolder() {
			return loadingHolder;
		}

		private InterceptResult interceptRequest(String uriString) {
			LoadingHolder loadingHolder = this.loadingHolder;
			if (loadingHolder != null) {
				loadingHolder.updateTime();
			}
			Uri uri = Uri.parse(uriString);
			String host = uri.getAuthority();
			if (host == null) {
				return new StubInterceptResult();
			}
			boolean google = host.contains("google");
			boolean gstatic = host.equals("www.gstatic.com");
			if (!google && !gstatic || gstatic && !uriString.endsWith(".js")) {
				return new StubInterceptResult();
			}
			if (google && uri.getPath().startsWith("/js/bg")) {
				return null;
			}
			String path = uri.getPath();
			if ("/recaptcha/api/image".equals(path)) {
				if (loadingHolder != null) {
					String challenge = uri.getQueryParameter("c");
					String oldChallenge = loadingHolder.challenge;
					if (!challenge.equals(oldChallenge)) {
						loadingHolder.challenge = challenge;
						loadingHolder.applyReady();
					}
				}
				return new StubImageInterceptResult();
			}
			return null;
		}

		@SuppressWarnings("deprecation")
		@Override
		public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
			Log.persistent().write("recaptcha intercept", url.length() > 100 ? url.substring(0, 100) + "..." : url);
			InterceptResult result = interceptRequest(url);
			return result != null ? result.get() : null;
		}
	}

	@SuppressWarnings("deprecation")
	@SuppressLint("SetJavaScriptEnabled")
	private void initWebView() {
		if (webView == null) {
			Context context = MainApplication.getInstance();
			if (!C.API_LOLLIPOP) {
				android.webkit.CookieSyncManager.createInstance(context);
			}
			webView = new WebView(context);
			client = new RecaptchaClient();
			webView.setWebViewClient(client);
			webView.setWebChromeClient(new WebChromeClient() {
				@Override
				@Deprecated
				public void onConsoleMessage(String message, int lineNumber, String sourceID) {
					Log.persistent().write("recaptcha js log", lineNumber, sourceID, message);
					super.onConsoleMessage(message, lineNumber, sourceID);
				}
			});
			WebSettings settings = webView.getSettings();
			settings.setJavaScriptEnabled(true);
			settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
			settings.setAppCacheEnabled(false);
			WebViewUtils.clearAll(webView);
		}
	}

	private String readHtmlAsset(String fileName) {
		InputStream input = null;
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		try {
			input = webView.getContext().getAssets().open("html/" + fileName);
			IOUtils.copyStream(input, output);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			IOUtils.close(input);
		}
		return new String(output.toByteArray());
	}

	private static Bitmap[] splitImages(Bitmap image, int sizeX, int sizeY) {
		Bitmap[] images = new Bitmap[sizeX * sizeY];
		int width = image.getWidth() / sizeX;
		int height = image.getHeight() / sizeY;
		for (int y = 0; y < sizeY; y++) {
			for (int x = 0; x < sizeX; x++) {
				Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
				new Canvas(bitmap).drawBitmap(image, -x * width, -y * height, null);
				images[y * sizeX + x] = bitmap;
			}
		}
		return images;
	}

	public static class CancelException extends Exception {
		private static final long serialVersionUID = 1L;

		public CancelException() {}
	}

	public class SkipException extends Exception {
		private static final long serialVersionUID = 1L;

		private final String response;

		public SkipException(String response) {
			this.response = response;
		}

		public String getResponse() {
			return response;
		}
	}
}