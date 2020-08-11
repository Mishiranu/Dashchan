package com.mishiranu.dashchan.util;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.view.Surface;
import android.view.WindowManager;
import java.util.HashSet;

public class ConfigurationLock {
	public interface Callback {
		interface Binding {
			void unlock();
		}

		void bindUnlock(Binding binding);
	}

	private final Activity activity;
	private final int initialOrientation;
	private final HashSet<Callback.Binding> bindings = new HashSet<>();

	private boolean lockOrientation = false;

	public ConfigurationLock(Activity activity) {
		this.activity = activity;
		try {
			initialOrientation = activity.getPackageManager()
					.getActivityInfo(activity.getComponentName(), 0).screenOrientation;
		} catch (PackageManager.NameNotFoundException e) {
			throw new RuntimeException(e);
		}
		activity.setRequestedOrientation(initialOrientation);
	}

	public void lockConfiguration(Dialog dialog) {
		lockConfiguration(dialog, null);
	}

	public void lockConfiguration(Dialog dialog, DialogInterface.OnDismissListener onDismissListener) {
		lockConfiguration(binding -> dialog.setOnDismissListener(d -> {
			if (onDismissListener != null) {
				onDismissListener.onDismiss(d);
			}
			binding.unlock();
		}));
	}

	public void lockConfiguration(Callback callback) {
		Callback.Binding[] binding = {null};
		binding[0] = () -> activity.getWindow().getDecorView().postDelayed(() -> {
			bindings.remove(binding[0]);
			updateConfiguration();
		}, 1000);
		bindings.add(binding[0]);
		callback.bindUnlock(binding[0]);
		updateConfiguration();
	}

	private void updateConfiguration() {
		boolean lockOrientation = !bindings.isEmpty();
		if (this.lockOrientation != lockOrientation) {
			this.lockOrientation = lockOrientation;
			int orientation;
			if (lockOrientation) {
				int rotation = ((WindowManager) activity.getSystemService(Activity.WINDOW_SERVICE))
						.getDefaultDisplay().getRotation();
				switch (rotation) {
					case Surface.ROTATION_90: {
						orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
						break;
					}
					case Surface.ROTATION_180: {
						orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
						break;
					}
					case Surface.ROTATION_270: {
						orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
						break;
					}
					case Surface.ROTATION_0:
					default: {
						orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
						break;
					}
				}
			} else {
				orientation = initialOrientation;
			}
			activity.setRequestedOrientation(orientation);
		}
	}
}
