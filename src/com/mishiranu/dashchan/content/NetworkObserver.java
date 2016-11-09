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

package com.mishiranu.dashchan.content;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.telephony.TelephonyManager;

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
		BroadcastReceiver connectivityReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				onActiveNetworkChange();
			}
		};
		context.registerReceiver(connectivityReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
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
					if (System.currentTimeMillis() - last3GChecked >= 2000) {
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
						last3GChecked = System.currentTimeMillis();
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