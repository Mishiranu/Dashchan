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

package com.mishiranu.dashchan.util;

import android.annotation.TargetApi;
import android.os.Build;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewDatabase;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.content.MainApplication;

public class WebViewUtils
{
	@SuppressWarnings("deprecation")
	public static void clearCookie()
	{
		CookieManager.getInstance().removeAllCookie();
	}
	
	public static void clearAll(WebView webView)
	{
		clearCookie();
		if (webView != null) webView.clearCache(true);
		WebViewDatabase webViewDatabase = WebViewDatabase.getInstance(MainApplication.getInstance());
		webViewDatabase.clearFormData();
		webViewDatabase.clearHttpAuthUsernamePassword();
		WebStorage.getInstance().deleteAllData();
	}
	
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public static void setThirdPartyCookiesEnabled(WebView webView)
	{
		if (C.API_LOLLIPOP) CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
	}
}