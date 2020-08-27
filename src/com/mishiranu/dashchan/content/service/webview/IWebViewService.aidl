package com.mishiranu.dashchan.content.service.webview;

import com.mishiranu.dashchan.content.service.webview.IRequestCallback;
import com.mishiranu.dashchan.content.service.webview.WebViewExtra;

interface IWebViewService {
	boolean loadWithCookieResult(String uriString, String userAgent, String httpProxyHost, int httpProxyPort,
			boolean verifyCertificate, long timeout, in WebViewExtra extra, IRequestCallback requestCallback);
}
