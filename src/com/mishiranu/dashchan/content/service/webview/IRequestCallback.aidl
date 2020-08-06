package com.mishiranu.dashchan.content.service.webview;

interface IRequestCallback {
	boolean onPageFinished(String uriString, String title);
	boolean onLoad(String uriString);
}
