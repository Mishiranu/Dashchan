package com.mishiranu.dashchan.util;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import androidx.annotation.RequiresApi;
import com.mishiranu.dashchan.C;

public class AndroidUtils {
	public interface OnReceiveListener {
		void onReceive(BroadcastReceiver receiver, Context context, Intent intent);
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

	@RequiresApi(Build.VERSION_CODES.O)
	public static NotificationChannel createHeadsUpNotificationChannel(String id, CharSequence name) {
		NotificationChannel channel = new NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH);
		channel.setSound(null, null);
		channel.setVibrationPattern(new long[] {0});
		return channel;
	}

	@SuppressLint("NewApi")
	public static boolean hasCallbacks(Handler handler, Runnable runnable) {
		return handler.hasCallbacks(runnable);
	}
}
