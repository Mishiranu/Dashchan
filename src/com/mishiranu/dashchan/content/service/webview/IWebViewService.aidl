package com.mishiranu.dashchan.content.service.webview;

import com.mishiranu.dashchan.content.service.webview.IRequestCallback;
import com.mishiranu.dashchan.content.service.webview.WebViewExtra;

interface IWebViewService {
	boolean loadWithCookieResult(String requestId, String uriString, String userAgent,
			boolean proxySocks, String proxyHost, int proxyPort, boolean verifyCertificate, long timeout,
			in WebViewExtra extra, IRequestCallback requestCallback);
	void interrupt(String requestId);
}
