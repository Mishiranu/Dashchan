package com.mishiranu.dashchan.content.service;

import android.app.Service;
import java.util.ArrayList;

public abstract class BaseService extends Service {
	private ArrayList<Runnable> onDestroyListeners;

	public void addOnDestroyListener(Runnable listener) {
		if (onDestroyListeners == null) {
			onDestroyListeners = new ArrayList<>();
		}
		onDestroyListeners.add(listener);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (onDestroyListeners != null) {
			ArrayList<Runnable> onDestroyListeners = new ArrayList<>(this.onDestroyListeners);
			for (Runnable listener : onDestroyListeners) {
				listener.run();
			}
		}
	}
}
