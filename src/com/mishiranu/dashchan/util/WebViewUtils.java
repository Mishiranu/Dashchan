package com.mishiranu.dashchan.util;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Proxy;
import android.net.ProxyInfo;
import android.os.Parcelable;
import android.util.Pair;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewDatabase;
import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebViewFeature;
import chan.http.HttpClient;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.content.MainApplication;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.Executor;

public class WebViewUtils {
	@SuppressWarnings("deprecation")
	public static void clearCookie() {
		CookieManager.getInstance().removeAllCookie();
	}

	@SuppressWarnings("deprecation")
	public static void clearAll(WebView webView) {
		clearCookie();
		if (webView != null) {
			webView.clearCache(true);
		}
		WebViewDatabase webViewDatabase = WebViewDatabase.getInstance(MainApplication.getInstance());
		webViewDatabase.clearFormData();
		webViewDatabase.clearHttpAuthUsernamePassword();
		WebStorage.getInstance().deleteAllData();
	}

	private static final Field FIELD_APPLICATION_LOADED_APK;
	private static final Field FIELD_LOADED_APK_RECEIVERS;
	private static final Method METHOD_WEB_VIEW_CORE_SEND_STATIC_MESSAGE;
	private static final Constructor<Parcelable> CONSTRUCTOR_PROXY_PROPERTIES;

	static {
		Field applicationLoadedApkField = null;
		Field loadedApkReceiversField = null;
		Method webViewCoreSendStaticMessage = null;
		Constructor<Parcelable> proxyPropertiesConstructor = null;
		if (C.API_KITKAT) {
			try {
				applicationLoadedApkField = Application.class.getField("mLoadedApk");
				loadedApkReceiversField = applicationLoadedApkField.getType().getDeclaredField("mReceivers");
				loadedApkReceiversField.setAccessible(true);
			} catch (Exception e) {
				applicationLoadedApkField = null;
				loadedApkReceiversField = null;
			}
		}
		if (!C.API_KITKAT) {
			try {
				@SuppressLint("PrivateApi")
				Class<?> webViewCoreClass = Class.forName("android.webkit.WebViewCore");
				webViewCoreSendStaticMessage = webViewCoreClass.getDeclaredMethod("sendStaticMessage",
						int.class, Object.class);
				webViewCoreSendStaticMessage.setAccessible(true);
			} catch (Exception e) {
				webViewCoreSendStaticMessage = null;
			}
		}
		if (!C.API_LOLLIPOP) {
			try {
				@SuppressWarnings("unchecked")
				@SuppressLint("PrivateApi")
				Class<Parcelable> proxyPropertiesClass = (Class<Parcelable>)
						Class.forName("android.net.ProxyProperties");
				proxyPropertiesConstructor = proxyPropertiesClass
						.getConstructor(String.class, int.class, String.class);
			} catch (Exception e) {
				proxyPropertiesConstructor = null;
			}
		}
		FIELD_APPLICATION_LOADED_APK = applicationLoadedApkField;
		FIELD_LOADED_APK_RECEIVERS = loadedApkReceiversField;
		METHOD_WEB_VIEW_CORE_SEND_STATIC_MESSAGE = webViewCoreSendStaticMessage;
		CONSTRUCTOR_PROXY_PROPERTIES = proxyPropertiesConstructor;
	}

	private static BroadcastReceiver findProxyChangeReceiver(Map<?, ?> receivers) {
		for (Map.Entry<?, ?> entry : receivers.entrySet()) {
			Object value = entry.getValue();
			if (value instanceof Map) {
				BroadcastReceiver result = findProxyChangeReceiver((Map<?, ?>) value);
				if (result != null) {
					return result;
				}
			} else {
				Object key = entry.getKey();
				if (key instanceof BroadcastReceiver && key.getClass().getName().contains("ProxyChangeListener")) {
					return (BroadcastReceiver) key;
				}
			}
		}
		return null;
	}

	private static final Executor EXECUTOR = Runnable::run;

	public static void setProxy(Context context, HttpClient.ProxyData proxyData, Runnable callback) {
		if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
			if (proxyData != null) {
				String uriString = (proxyData.socks ? "socks" : "http") + "://"
						+ proxyData.host + ":" + proxyData.port;
				ProxyController.getInstance().setProxyOverride(new ProxyConfig.Builder()
						.addProxyRule(uriString).build(), EXECUTOR, callback);
			} else {
				ProxyController.getInstance().clearProxyOverride(EXECUTOR, callback);
			}
		} else {
			setHttpProxy(context, proxyData != null && !proxyData.socks
					? new Pair<>(proxyData.host, proxyData.port) : null);
			if (callback != null) {
				callback.run();
			}
		}
	}

	private static void setHttpProxy(Context context, Pair<String, Integer> proxy) {
		String hostProperty = proxy != null ? proxy.first : "";
		String portProperty = proxy != null ? proxy.second.toString() : "";
		System.setProperty("http.proxyHost", hostProperty);
		System.setProperty("http.proxyPort", portProperty);
		System.setProperty("https.proxyHost", hostProperty);
		System.setProperty("https.proxyPort", portProperty);

		if (C.API_KITKAT) {
			if (FIELD_APPLICATION_LOADED_APK != null && FIELD_LOADED_APK_RECEIVERS != null &&
					(C.API_LOLLIPOP || CONSTRUCTOR_PROXY_PROPERTIES != null)) {
				Context applicationContext = context.getApplicationContext();
				Map<?, ?> receivers = null;
				try {
					Object loadedApk = FIELD_APPLICATION_LOADED_APK.get(applicationContext);
					receivers = (Map<?, ?>) FIELD_LOADED_APK_RECEIVERS.get(loadedApk);
				} catch (Exception e) {
					e.printStackTrace();
				}
				BroadcastReceiver proxyChangeListener = receivers != null
						? findProxyChangeReceiver(receivers) : null;
				if (proxyChangeListener != null) {
					Intent intent = new Intent(Proxy.PROXY_CHANGE_ACTION);
					if (C.API_LOLLIPOP) {
						@SuppressWarnings("deprecation")
						String name = Proxy.EXTRA_PROXY_INFO;
						intent.putExtra(name, proxy != null ? ProxyInfo
								.buildDirectProxy(proxy.first, proxy.second) : null);
					} else {
						Parcelable proxyProperties = null;
						if (proxy != null) {
							try {
								proxyProperties = CONSTRUCTOR_PROXY_PROPERTIES
										.newInstance(proxy.first, proxy.second, null);
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
						intent.putExtra("proxy", proxyProperties);
					}
					proxyChangeListener.onReceive(applicationContext, intent);
				}
			}
		} else {
			if (METHOD_WEB_VIEW_CORE_SEND_STATIC_MESSAGE != null && CONSTRUCTOR_PROXY_PROPERTIES != null) {
				try {
					int messageProxyChanged = 193;
					Parcelable proxyProperties = proxy != null ? CONSTRUCTOR_PROXY_PROPERTIES
							.newInstance(proxy.first, proxy.second, null) : null;
					METHOD_WEB_VIEW_CORE_SEND_STATIC_MESSAGE.invoke(null, messageProxyChanged, proxyProperties);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
}
