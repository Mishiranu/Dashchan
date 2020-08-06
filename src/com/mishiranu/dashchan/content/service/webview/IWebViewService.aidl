package com.mishiranu.dashchan.content.service.webview;

import com.mishiranu.dashchan.content.service.webview.IRequestCallback;

interface IWebViewService {
	String loadWithCookieResult(String name, String uriString, String userAgent,
			String httpProxyHost, int httpProxyPort, boolean verifyCertificate, long timeout,
			IRequestCallback requestCallback);
}
