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

package com.mishiranu.dashchan.net;

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
import chan.util.StringUtils;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.app.MainApplication;
import com.mishiranu.dashchan.async.ReadCaptchaTask;
import com.mishiranu.dashchan.content.CaptchaManager;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.util.WebViewUtils;

public class CloudFlarePasser implements Handler.Callback
{
	private static CloudFlarePasser INSTANCE = new CloudFlarePasser();
	private static final int WEB_VIEW_TIMEOUT = 20000;
	
	private static final String CF_FORBIDDEN_FLAG = "<form class=\"challenge-form\" id=\"challenge-form\" "
			+ "action=\"/cdn-cgi/l/chk_captcha\" method=\"get\">";
	private static final String CF_UNAVAILABLE_FLAG = "<span data-translate=\"checking_browser\">"
			+ "Checking your browser before accessing</span>";
	
	private static final Pattern ALLOWED_LINKS = Pattern.compile("/?(|cdn-cgi/l/.*)");
	private static final Pattern CF_CAPTCHA_PATTERN = Pattern.compile("data-sitekey=\"(.*?)\"");
	
	public static final String COOKIE_CLOUDFLARE = "cf_clearance";
	
	private CloudFlarePasser()
	{
		
	}
	
	private final Handler mHandler = new Handler(Looper.getMainLooper(), this);
	
	private static class CheckHolder
	{
		public final String chanName;
		
		public volatile boolean started;
		public volatile boolean ready;
		public volatile boolean success;
		
		public CheckHolder(String chanName)
		{
			this.chanName = chanName;
		}
		
		public void waitReady(boolean infinite) throws InterruptedException
		{
			synchronized (this)
			{
				if (infinite)
				{
					while (!ready) wait();
				}
				else
				{
					while (!started) wait();
					long t = System.currentTimeMillis();
					while (!ready)
					{
						long dt = WEB_VIEW_TIMEOUT + t - System.currentTimeMillis();
						if (dt <= 0) return;
						wait(dt);
					}
				}
			}
		}
	}
	
	private final LinkedHashMap<String, CloudFlareClient> mClientHandlers = new LinkedHashMap<>();
	
	private static final int MESSAGE_CHECK_JAVASCRIPT = 1;
	private static final int MESSAGE_HANDLE_NEXT_JAVASCRIPT = 2;
	
	@Override
	public boolean handleMessage(Message msg)
	{
		switch (msg.what)
		{
			case MESSAGE_CHECK_JAVASCRIPT:
			{
				initWebView();
				CheckHolder checkHolder = (CheckHolder) msg.obj;
				CloudFlareClient client = mClientHandlers.get(checkHolder.chanName);
				if (client == null)
				{
					client = new CloudFlareClient(checkHolder.chanName, checkHolder);
					mClientHandlers.put(checkHolder.chanName, client);
				}
				else client.add(checkHolder);
				if (!mHandler.hasMessages(MESSAGE_HANDLE_NEXT_JAVASCRIPT))
				{
					handleJavaScript(client);
					mHandler.sendEmptyMessageDelayed(MESSAGE_HANDLE_NEXT_JAVASCRIPT, WEB_VIEW_TIMEOUT);
				}
				return true;
			}
			case MESSAGE_HANDLE_NEXT_JAVASCRIPT:
			{
				handleNextJavaScript();
				return true;
			}
		}
		return false;
	}
	
	private void handleNextJavaScript()
	{
		mHandler.removeMessages(MESSAGE_HANDLE_NEXT_JAVASCRIPT);
		Iterator<LinkedHashMap.Entry<String, CloudFlareClient>> iterator = mClientHandlers.entrySet().iterator();
		CloudFlareClient client = null;
		if (iterator.hasNext())
		{
			iterator.next();
			iterator.remove();
			if (iterator.hasNext()) client = iterator.next().getValue();
		}
		if (client != null)
		{
			handleJavaScript(client);
			mHandler.sendEmptyMessageDelayed(MESSAGE_HANDLE_NEXT_JAVASCRIPT, WEB_VIEW_TIMEOUT);
		}
	}
	
	private void handleJavaScript(CloudFlareClient client)
	{
		String chanName = client.mChanName;
		client.notifyStarted();
		mWebView.stopLoading();
		WebViewUtils.clearAll(mWebView);
		mWebView.setWebViewClient(client);
		ChanLocator locator = ChanLocator.get(chanName);
		mWebView.loadUrl(locator.buildPath().toString());
	}
	
	private class CloudFlareClient extends WebViewClient
	{
		private final String mChanName;
		private final ArrayList<CheckHolder> mCheckHolders = new ArrayList<>();
		
		private boolean mStarted = false;
		private boolean mWasChecked = false;
		
		public CloudFlareClient(String chanName, CheckHolder checkHolder)
		{
			mChanName = chanName;
			add(checkHolder);
		}
		
		public void add(CheckHolder checkHolder)
		{
			mCheckHolders.add(checkHolder);
			if (mStarted)
			{
				synchronized (checkHolder)
				{
					checkHolder.started = true;
					checkHolder.notifyAll();
				}
			}
		}
		
		public void notifyStarted()
		{
			mStarted = true;
			for (CheckHolder checkHolder : mCheckHolders)
			{
				synchronized (checkHolder)
				{
					checkHolder.started = true;
					checkHolder.notifyAll();
				}
			}
		}
		
		@Override
		public void onPageFinished(WebView view, String url)
		{
			super.onPageFinished(view, url);
			if ("Just a moment...".equals(view.getTitle())) mWasChecked = true; else
			{
				String cookie = null;
				boolean success = false;
				if (mWasChecked)
				{
					cookie = StringUtils.nullIfEmpty(extractCookie(url, COOKIE_CLOUDFLARE));
					if (cookie != null) success = true;
				}
				storeCookie(mChanName, cookie, url);
				view.stopLoading();
				for (CheckHolder checkHolder : mCheckHolders)
				{
					synchronized (checkHolder)
					{
						checkHolder.success = success;
						checkHolder.ready = true;
						checkHolder.notifyAll();
					}
				}
				handleNextJavaScript();
			}
		}
		
		@Override
		public WebResourceResponse shouldInterceptRequest(WebView view, String url)
		{
			Uri uri = Uri.parse(url);
			// Disallow downloading all resources instead of chan and cloudflare redirects (see regex pattern)
			if (ALLOWED_LINKS.matcher(uri.getPath()).matches()) return null;
			return new WebResourceResponse("text/html", "UTF-8", null);
		}
		
		private String extractCookie(String url, String name)
		{
			try
			{
				String data = CookieManager.getInstance().getCookie(url);
				String[] splitted = data.split(";\\s*");
				for (int i = 0; i < splitted.length; i++)
				{
					if (!StringUtils.isEmptyOrWhitespace(splitted[i]) && splitted[i].startsWith(name + "="))
					{
						return splitted[i].substring(name.length() + 1);
					}
				}
			}
			catch (Exception e)
			{
				
			}
			return null;
		}
	};
	
	private WebView mWebView;
	
	@SuppressLint("SetJavaScriptEnabled")
	private void initWebView()
	{
		if (mWebView == null)
		{
			Context context = MainApplication.getInstance();
			mWebView = new WebView(context);
			WebSettings settings = mWebView.getSettings();
			settings.setUserAgentString(C.USER_AGENT);
			settings.setJavaScriptEnabled(true);
			settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
			settings.setAppCacheEnabled(false);
		}
	}
	
	private final HashMap<String, CheckHolder> mCaptchaHolders = new HashMap<>();
	private final HashMap<String, Long> mCaptchaLastCancel = new HashMap<>();
	
	private boolean handleCaptcha(String chanName, String recaptchaApiKey) throws HttpException
	{
		CheckHolder checkHolder;
		boolean handle;
		synchronized (mCaptchaLastCancel)
		{
			Long lastCancelTime = mCaptchaLastCancel.get(chanName);
			if (lastCancelTime != null && System.currentTimeMillis() - lastCancelTime < 5000) return false;
		}
		synchronized (mCaptchaHolders)
		{
			checkHolder = mCaptchaHolders.get(chanName);
			if (checkHolder == null)
			{
				checkHolder = new CheckHolder(chanName);
				mCaptchaHolders.put(chanName, checkHolder);
				handle = true;
			}
			else handle = false;
		}
		if (!handle)
		{
			try
			{
				checkHolder.waitReady(true);
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
				return false;
			}
			return checkHolder.success;
		}
		try
		{
			HttpHolder holder = new HttpHolder();
			String captchaType = Preferences.getCloudFlareCaptchaType();
			boolean retry = false;
			while (true)
			{
				ChanPerformer.CaptchaData captchaData = CaptchaManager.getInstance().requireUserCaptcha
						(new CloudFlareCaptchaReader(recaptchaApiKey), captchaType, null, null, null, null,
						R.string.message_cloudflate_block, retry);
				if (captchaData == null)
				{
					synchronized (mCaptchaLastCancel)
					{
						mCaptchaLastCancel.put(chanName, System.currentTimeMillis());						
					}
					return false;
				}
				if (Thread.currentThread().isInterrupted()) return false;
				String recaptchaResponse = captchaData.get(ChanPerformer.CaptchaData.INPUT);
				ChanLocator locator = ChanLocator.get(chanName);
				Uri uri = locator.buildQuery("cdn-cgi/l/chk_captcha", "g-recaptcha-response", recaptchaResponse);
				new HttpRequest(uri, holder).setRedirectHandler(HttpRequest.RedirectHandler.NONE)
						.setSuccessOnly(false).setCheckCloudFlare(false).read();
				String cookie = holder.getCookieValue(COOKIE_CLOUDFLARE);
				if (cookie != null)
				{
					storeCookie(chanName, cookie, null);
					checkHolder.success = true;
					return true;
				}
				retry = true;
			}
		}
		finally
		{
			synchronized (mCaptchaHolders)
			{
				mCaptchaHolders.remove(chanName);
			}
			synchronized (checkHolder)
			{
				checkHolder.ready = true;
				checkHolder.notifyAll();
			}
		}
	}
	
	private static class CloudFlareCaptchaReader implements ReadCaptchaTask.CaptchaReader
	{
		private final String mRecaptchaApiKey;
		
		public CloudFlareCaptchaReader(String recaptchaApiKey)
		{
			mRecaptchaApiKey = recaptchaApiKey;
		}
		
		@Override
		public ChanPerformer.ReadCaptchaResult onReadCaptcha(ChanPerformer.ReadCaptchaData data)
				throws HttpException, InvalidResponseException
		{
			ChanPerformer.CaptchaData captchaData = new ChanPerformer.CaptchaData();
			captchaData.put(ChanPerformer.CaptchaData.API_KEY, mRecaptchaApiKey);
			return new ChanPerformer.ReadCaptchaResult(ChanPerformer.CaptchaState.CAPTCHA, captchaData);
		}
	}
	
	public static boolean checkResponse(String chanName, HttpHolder holder) throws HttpException
	{
		int responseCode = holder.getResponseCode();
		if ((responseCode == HttpURLConnection.HTTP_FORBIDDEN || responseCode == HttpURLConnection.HTTP_UNAVAILABLE)
				&& holder.getHeaderFields().containsKey("CF-RAY"))
		{
			String responseText = holder.read().getString();
			switch (responseCode)
			{
				case HttpURLConnection.HTTP_FORBIDDEN:
				{
					if (responseText.contains(CF_FORBIDDEN_FLAG))
					{
						Matcher matcher = CF_CAPTCHA_PATTERN.matcher(responseText);
						if (matcher.find())
						{
							String captchaApiKey = matcher.group(1);
							return INSTANCE.handleCaptcha(chanName, captchaApiKey);
						}
					}
					break;
				}
				case HttpURLConnection.HTTP_UNAVAILABLE:
				{
					if (responseText.contains(CF_UNAVAILABLE_FLAG))
					{
						CheckHolder checkHolder = new CheckHolder(chanName);
						INSTANCE.mHandler.obtainMessage(MESSAGE_CHECK_JAVASCRIPT, checkHolder).sendToTarget();
						try
						{
							checkHolder.waitReady(false);
						}
						catch (InterruptedException e)
						{
							Thread.currentThread().interrupt();
							return false;
						}
						return checkHolder.success;
					}
					/*if (responseText.contains(CF_UNAVAILABLE_FLAG))
					{
						return INSTANCE.handleCaptcha(chanName, "6LeT6gcAAAAAAAZ_yDmTMqPH57dJQZdQcu6VFqog");
					}*/
					break;
				}
			}
		}
		return false;
	}
	
	private static void storeCookie(String chanName, String cookie, String uriString)
	{
		ChanConfiguration configuration = ChanConfiguration.get(chanName);
		configuration.storeCookie(COOKIE_CLOUDFLARE, cookie, cookie != null ? "CloudFlare" : null);
		configuration.commit();
		Uri uri = uriString != null ? Uri.parse(uriString) : null;
		if (uri != null)
		{
			ChanLocator locator = ChanLocator.get(chanName);
			String host = uri.getHost();
			if (locator.isConvertableChanHost(host)) locator.setPreferredHost(host);
			Preferences.setUseHttps(chanName, "https".equals(uri.getScheme()));
		}
	}
	
	public static String getCookie(String chanName)
	{
		return StringUtils.nullIfEmpty(ChanConfiguration.get(chanName).getCookie(COOKIE_CLOUDFLARE));
	}
}