package com.mishiranu.dashchan.content.service.webview;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import chan.http.HttpClient;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.WebViewUtils;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;

public class WebViewService extends Service {
	private static class CookieRequest {
		public final String uriString;
		public final String userAgent;
		public final HttpClient.ProxyData proxyData;
		public final boolean verifyCertificate;
		public final long timeout;
		public final WebViewExtra extra;
		public final IRequestCallback requestCallback;

		public boolean ready;
		public boolean finished;

		public String recaptchaV2ApiKey;
		public String recaptchaV2Result;
		public boolean recaptchaIsHcaptcha;

		private CookieRequest(String uriString, String userAgent, HttpClient.ProxyData proxyData,
				boolean verifyCertificate, long timeout, WebViewExtra extra, IRequestCallback requestCallback) {
			this.uriString = uriString;
			this.userAgent = userAgent;
			this.proxyData = proxyData;
			this.verifyCertificate = verifyCertificate;
			this.timeout = timeout;
			this.extra = extra;
			this.requestCallback = requestCallback;
		}
	}

	private final LinkedList<CookieRequest> cookieRequests = new LinkedList<>();

	private WebView webView;
	private CookieRequest cookieRequest;
	private Thread captchaThread;

	private static boolean captureImageFileInit;
	private static File captureImageFile;

	private static final int DRAW_TO_FILE_INTERVAL = 2000;

	private static final int MESSAGE_HANDLE_NEXT = 1;
	private static final int MESSAGE_HANDLE_FINISH = 2;
	private static final int MESSAGE_HANDLE_AFTER_INTERRUPT = 3;
	private static final int MESSAGE_HANDLE_CAPTCHA = 4;
	private static final int MESSAGE_DRAW_TO_FILE = 5;

	private final Handler handler = new Handler(Looper.getMainLooper(), message -> {
		if (webView == null) {
			return false;
		}
		switch (message.what) {
			case MESSAGE_HANDLE_NEXT: {
				handleNextCookieRequest();
				return true;
			}
			case MESSAGE_HANDLE_FINISH: {
				CookieRequest cookieRequest = this.cookieRequest;
				if (cookieRequest != null) {
					synchronized (cookieRequest) {
						if (!cookieRequest.ready) {
							cookieRequest.ready = true;
							cookieRequest.notifyAll();
						}
					}
					this.cookieRequest = null;
				}
				handleNextCookieRequest();
				return true;
			}
			case MESSAGE_HANDLE_AFTER_INTERRUPT: {
				CookieRequest cookieRequest = (CookieRequest) message.obj;
				if (cookieRequest == this.cookieRequest) {
					this.cookieRequest = null;
					handleNextCookieRequest();
				}
				return true;
			}
			case MESSAGE_HANDLE_CAPTCHA: {
				CookieRequest cookieRequest = (CookieRequest) message.obj;
				if (cookieRequest == this.cookieRequest) {
					message.getTarget().removeMessages(MESSAGE_HANDLE_FINISH);
					if (cookieRequest.recaptchaV2Result != null) {
						webView.loadUrl("javascript:handleResult('" + cookieRequest.recaptchaV2Result + "')");
						message.getTarget().sendEmptyMessageDelayed(MESSAGE_HANDLE_FINISH, cookieRequest.timeout);
					} else {
						message.getTarget().sendEmptyMessage(MESSAGE_HANDLE_FINISH);
					}
				}
				return true;
			}
			case MESSAGE_DRAW_TO_FILE: {
				if (captureImageFile != null && webView != null) {
					Bitmap bitmap = Bitmap.createBitmap(webView.getLayoutParams().width,
							webView.getLayoutParams().height, Bitmap.Config.ARGB_8888);
					webView.draw(new Canvas(bitmap));
					try (FileOutputStream output = new FileOutputStream(captureImageFile)) {
						bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
					} catch (IOException e) {
						// Ignore exception
					} finally {
						bitmap.recycle();
					}
					message.getTarget().sendEmptyMessageDelayed(MESSAGE_DRAW_TO_FILE, DRAW_TO_FILE_INTERVAL);
				}
				return true;
			}
		}
		return false;
	});

	private class ServiceClient extends WebViewClient {
		@SuppressWarnings("deprecation")
		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
			return false;
		}

		@Override
		public void onPageFinished(WebView view, String url) {
			super.onPageFinished(view, url);

			CookieRequest cookieRequest = WebViewService.this.cookieRequest;
			boolean finished = true;
			if (cookieRequest != null) {
				String cookie = CookieManager.getInstance().getCookie(url);
				try {
					finished = cookieRequest.requestCallback.onPageFinished(url, cookie, view.getTitle());
					cookieRequest.finished = finished;
				} catch (RemoteException e) {
					e.printStackTrace();
				}
				if (!finished) {
					if (cookieRequest.extra != null) {
						String injectJavascript = cookieRequest.extra.getInjectJavascript();
						if (injectJavascript != null) {
							String sanitized = injectJavascript
									.replace("\\", "\\\\").replace("\n", "\\n").replace("\"", "\\\"");
							view.loadUrl("javascript:eval(\"" + sanitized + "\")");
						}
					}
				}
			}
			if (finished) {
				view.stopLoading();
				handler.removeMessages(MESSAGE_HANDLE_FINISH);
				handler.sendEmptyMessage(MESSAGE_HANDLE_FINISH);
			}
		}

		@Override
		public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
			CookieRequest cookieRequest = WebViewService.this.cookieRequest;
			if (cookieRequest != null) {
				if (cookieRequest.verifyCertificate) {
					handler.cancel();
					handler.removeMessages(MESSAGE_HANDLE_FINISH);
					handler.sendEmptyMessage(MESSAGE_HANDLE_FINISH);
				} else {
					handler.proceed();
				}
			} else {
				super.onReceivedSslError(view, handler, error);
			}
		}

		@SuppressWarnings("deprecation")
		@Override
		public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
			super.onReceivedError(view, errorCode, description, failingUrl);

			handler.removeMessages(MESSAGE_HANDLE_FINISH);
			handler.sendEmptyMessage(MESSAGE_HANDLE_FINISH);
		}

		private WebResourceResponse getCaptchaApi(String onLoad) {
			String stub = IOUtils.readRawResourceString(getResources(), R.raw.web_captcha_api)
					.replace("__REPLACE_ON_LOAD__", onLoad != null ? "'" + onLoad + "'" : "null");
			return new WebResourceResponse("application/javascript", "UTF-8",
					new ByteArrayInputStream(stub.getBytes()));
		}

		@SuppressWarnings("deprecation")
		@Override
		public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
			boolean allowed = false;
			CookieRequest cookieRequest = WebViewService.this.cookieRequest;
			boolean recaptcha = url.contains("recaptcha");
			boolean hcaptcha = url.contains("hcaptcha");
			if (recaptcha || hcaptcha) {
				Uri uri = Uri.parse(url);
				String key = uri.getQueryParameter("render");
				String onLoad = uri.getQueryParameter("onload");
				if (key != null || onLoad != null) {
					cookieRequest.recaptchaV2ApiKey = key;
					cookieRequest.recaptchaIsHcaptcha = hcaptcha;
					return getCaptchaApi(onLoad);
				}
			}
			if (cookieRequest != null) {
				try {
					allowed = cookieRequest.requestCallback.onLoad(url);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}
			if (allowed) {
				return super.shouldInterceptRequest(view, url);
			} else {
				return new WebResourceResponse("text/html", "UTF-8", null);
			}
		}
	}

	@SuppressWarnings("unused")
	private final Object javascriptInterface = new Object() {
		@JavascriptInterface
		public void onRequestRecaptcha(String apiKey) {
			CookieRequest cookieRequest = WebViewService.this.cookieRequest;
			if (cookieRequest != null) {
				if (apiKey != null) {
					cookieRequest.recaptchaV2ApiKey = apiKey;
				}
				handler.removeMessages(MESSAGE_HANDLE_FINISH);
				startCaptchaThread(cookieRequest);
			}
		}
	};

	private void startCaptchaThread(CookieRequest cookieRequest) {
		synchronized (this) {
			if (captchaThread != null) {
				captchaThread.interrupt();
			}
			captchaThread = new Thread(() -> {
				try {
					if (cookieRequest.recaptchaIsHcaptcha) {
						cookieRequest.recaptchaV2Result = cookieRequest.requestCallback
								.onHcaptcha(cookieRequest.recaptchaV2ApiKey, cookieRequest.uriString);
					} else {
						cookieRequest.recaptchaV2Result = cookieRequest.requestCallback
								.onRecaptchaV2(cookieRequest.recaptchaV2ApiKey, true, cookieRequest.uriString);
					}
				} catch (RemoteException e) {
					// Ignore
				}
				handler.obtainMessage(MESSAGE_HANDLE_CAPTCHA, cookieRequest).sendToTarget();
			});
			captchaThread.start();
		}
	}

	private void handleNextCookieRequest() {
		if (cookieRequest == null) {
			synchronized (cookieRequests) {
				while (!cookieRequests.isEmpty()) {
					cookieRequest = cookieRequests.removeFirst();
					// Ignore interrupted requests
					if (!cookieRequest.ready) {
						break;
					}
				}
			}
			handler.removeMessages(MESSAGE_DRAW_TO_FILE);
			webView.stopLoading();
			WebViewUtils.clearAll(webView);
			CookieRequest cookieRequest = this.cookieRequest;
			if (cookieRequest != null) {
				handler.removeMessages(MESSAGE_HANDLE_FINISH);
				handler.sendEmptyMessageDelayed(MESSAGE_HANDLE_FINISH, cookieRequest.timeout);
				webView.getSettings().setUserAgentString(cookieRequest.userAgent);
				WebViewUtils.setProxy(this, cookieRequest.proxyData, () -> {
					if (this.cookieRequest == cookieRequest && webView != null) {
						webView.loadUrl(cookieRequest.uriString);
					}
				});
				if (captureImageFile != null) {
					handler.sendEmptyMessageDelayed(MESSAGE_DRAW_TO_FILE, DRAW_TO_FILE_INTERVAL);
				}
			} else {
				webView.loadUrl("about:blank");
			}
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return new IWebViewService.Stub() {
			private final HashMap<String, CookieRequest> interruptedRequests = new HashMap<>();

			@Override
			public boolean loadWithCookieResult(String requestId, String uriString, String userAgent,
					boolean proxySocks, String proxyHost, int proxyPort, boolean verifyCertificate, long timeout,
					WebViewExtra extra, IRequestCallback requestCallback) throws RemoteException {
				HttpClient.ProxyData proxyData = proxyHost != null
						? new HttpClient.ProxyData(proxySocks, proxyHost, proxyPort) : null;
				CookieRequest cookieRequest = new CookieRequest(uriString, userAgent, proxyData,
						verifyCertificate, timeout, extra, requestCallback);
				synchronized (interruptedRequests) {
					if (interruptedRequests.containsKey(requestId)) {
						interruptedRequests.remove(requestId);
						return false;
					} else {
						interruptedRequests.put(requestId, cookieRequest);
					}
				}
				try {
					synchronized (cookieRequests) {
						cookieRequests.add(cookieRequest);
					}
					synchronized (cookieRequest) {
						handler.sendEmptyMessage(MESSAGE_HANDLE_NEXT);
						while (!cookieRequest.ready) {
							try {
								cookieRequest.wait();
							} catch (InterruptedException e) {
								Thread.currentThread().interrupt();
								throw new RemoteException("interrupted");
							}
						}
					}
					return cookieRequest.finished;
				} finally {
					synchronized (interruptedRequests) {
						interruptedRequests.remove(requestId);
					}
				}
			}

			@Override
			public void interrupt(String requestId) {
				synchronized (interruptedRequests) {
					CookieRequest cookieRequest = interruptedRequests.get(requestId);
					if (cookieRequest != null) {
						synchronized (cookieRequest) {
							cookieRequest.ready = true;
							cookieRequest.notifyAll();
						}
						handler.obtainMessage(MESSAGE_HANDLE_AFTER_INTERRUPT, cookieRequest).sendToTarget();
					} else {
						interruptedRequests.put(requestId, null);
					}
				}
			}
		};
	}

	@SuppressWarnings("deprecation")
	private static void disableCacheCompat(WebView webView) {
		if (!C.API_R) {
			webView.getSettings().setAppCacheEnabled(false);
		}
	}

	@SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
	@Override
	public void onCreate() {
		super.onCreate();

		if (!captureImageFileInit) {
			captureImageFileInit = true;
			File file = new File(getExternalCacheDir().getParentFile(), "files/webview.png");
			if (file.exists()) {
				captureImageFile = file;
				if (C.API_LOLLIPOP) {
					WebView.enableSlowWholeDocumentDraw();
				}
			}
		}

		webView = new WebView(this);
		webView.getSettings().setJavaScriptEnabled(true);
		webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
		disableCacheCompat(webView);
		webView.addJavascriptInterface(javascriptInterface, "jsi");
		webView.setWebViewClient(new ServiceClient());
		webView.setWebChromeClient(new WebChromeClient() {
			@Override
			public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
				String text = consoleMessage.message();
				if (text != null && text.contains("SyntaxError")) {
					handler.removeMessages(MESSAGE_HANDLE_FINISH);
					handler.sendEmptyMessage(MESSAGE_HANDLE_FINISH);
					return true;
				}
				return false;
			}
		});
		webView.setLayoutParams(new ViewGroup.LayoutParams(480, 270));
		int initialScale = 25;
		if (captureImageFile != null) {
			int factor = 4;
			webView.getLayoutParams().width *= factor;
			webView.getLayoutParams().height *= factor;
			initialScale *= factor;
		}
		int measureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
		webView.measure(measureSpec, measureSpec);
		webView.layout(0, 0, webView.getLayoutParams().width, webView.getLayoutParams().height);
		webView.setInitialScale(initialScale);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		webView.destroy();
		webView = null;
	}
}
