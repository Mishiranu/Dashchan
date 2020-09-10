package com.mishiranu.dashchan.util;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.mishiranu.dashchan.C;

public class AndroidUtils {
	public interface OnReceiveListener {
		public void onReceive(BroadcastReceiver receiver, Context context, Intent intent);
	}

	public static BroadcastReceiver createReceiver(OnReceiveListener listener) {
		return new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				listener.onReceive(this, context, intent);
			}
		};
	}

	public static void startAnyService(Context context, Intent intent) {
		if (C.API_OREO) {
			context.startForegroundService(intent);
		} else {
			context.startService(intent);
		}
	}

	public static PendingIntent getAnyServicePendingIntent(Context context, int requestCode, Intent intent, int flags) {
		if (C.API_OREO) {
			return PendingIntent.getForegroundService(context, requestCode, intent, flags);
		} else {
			return PendingIntent.getService(context, requestCode, intent, flags);
		}
	}

	public static String getApplicationLabel(Context context) {
		return context.getApplicationInfo().loadLabel(context.getPackageManager()).toString();
	}
}
