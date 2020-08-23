package com.mishiranu.dashchan.content;

import android.content.Context;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import com.mishiranu.dashchan.util.AndroidUtils;

// TODO Handle deprecation
public class NetworkObserver {
	private static final NetworkObserver INSTANCE = new NetworkObserver();

	public static NetworkObserver getInstance() {
		return INSTANCE;
	}

	private static final int NETWORK_WIFI = 2;
	private static final int NETWORK_MOBILE = 1;
	private static final int NETWORK_UNDEFINED = 0;

	private final ConnectivityManager connectivityManager;

	private int networkState = NETWORK_UNDEFINED;
	private long last3GChecked;
	private boolean last3GAvailable;

	private NetworkObserver() {
		Context context = MainApplication.getInstance();
		connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		onActiveNetworkChange();
		context.registerReceiver(AndroidUtils.createReceiver((r, c, i) -> onActiveNetworkChange()),
				new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
	}

	public boolean isWifiConnected() {
		return networkState == NETWORK_WIFI;
	}

	public boolean isMobile3GConnected() {
		switch (networkState) {
			case NETWORK_WIFI: {
				return true;
			}
			case NETWORK_MOBILE: {
				synchronized (this) {
					if (SystemClock.elapsedRealtime() - last3GChecked >= 2000) {
						boolean is3GAvailable = false;
						NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
						if (networkInfo != null && networkInfo.isConnected()) {
							int type = networkInfo.getType();
							if (type == ConnectivityManager.TYPE_MOBILE ||
									type == ConnectivityManager.TYPE_MOBILE_DUN) {
								switch (networkInfo.getSubtype()) {
									case TelephonyManager.NETWORK_TYPE_UMTS:
									case TelephonyManager.NETWORK_TYPE_EVDO_0:
									case TelephonyManager.NETWORK_TYPE_EVDO_A:
									case TelephonyManager.NETWORK_TYPE_HSDPA:
									case TelephonyManager.NETWORK_TYPE_HSUPA:
									case TelephonyManager.NETWORK_TYPE_HSPA:
									case TelephonyManager.NETWORK_TYPE_EVDO_B:
									case TelephonyManager.NETWORK_TYPE_EHRPD:
									case TelephonyManager.NETWORK_TYPE_HSPAP:
									case TelephonyManager.NETWORK_TYPE_LTE: {
										is3GAvailable = true;
										break;
									}
								}
							}
						}
						last3GAvailable = is3GAvailable;
						last3GChecked = SystemClock.elapsedRealtime();
					}
					return last3GAvailable;
				}
			}
		}
		return false;
	}

	private void onActiveNetworkChange() {
		int networkState = NETWORK_UNDEFINED;
		NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
		if (networkInfo != null && networkInfo.isConnected()) {
			int type = networkInfo.getType();
			switch (type) {
				case ConnectivityManager.TYPE_WIFI:
				case ConnectivityManager.TYPE_WIMAX: {
					networkState = NETWORK_WIFI;
					break;
				}
				case ConnectivityManager.TYPE_MOBILE:
				case ConnectivityManager.TYPE_MOBILE_DUN: {
					networkState = NETWORK_MOBILE;
					break;
				}
			}
		}
		this.networkState = networkState;
		last3GChecked = 0L;
	}
}
