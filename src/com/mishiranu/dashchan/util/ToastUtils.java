package com.mishiranu.dashchan.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.widget.Toast;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.widget.ClickableToast;

public class ToastUtils implements Runnable {
	private static Toast toast;

	@SuppressLint("ShowToast")
	private static void makeNewToast(Context context) {
		synchronized (ToastUtils.class) {
			if (toast == null) {
				toast = Toast.makeText(context, "", Toast.LENGTH_LONG);
			}
			ToastUtils.class.notifyAll();
		}
	}

	private final Context context;

	private ToastUtils(Context context) {
		this.context = context;
	}

	@Override
	public void run() {
		makeNewToast(context);
	}

	public static void show(Context context, String message) {
		synchronized (ToastUtils.class) {
			ClickableToast.cancel(context);
			if (toast == null) {
				if (ConcurrentUtils.isMain()) {
					makeNewToast(context.getApplicationContext());
				} else {
					ConcurrentUtils.HANDLER.post(new ToastUtils(context.getApplicationContext()));
					try {
						while (toast == null) {
							ToastUtils.class.wait();
						}
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						return;
					}
				}
			}
			toast.setText(message);
			toast.show();
		}
	}

	public static void show(Context context, int resId) {
		show(context, context.getString(resId != 0 ? resId : R.string.unknown_error));
	}

	public static void show(Context context, ErrorItem errorItem) {
		if (errorItem != null) {
			show(context, errorItem.toString());
		} else {
			show(context, 0);
		}
	}

	public static void cancel() {
		synchronized (ToastUtils.class) {
			if (toast != null) {
				toast.cancel();
				// Toast can't be recycled, so I reset reference and create new toast later
				toast = null;
			}
		}
	}
}
