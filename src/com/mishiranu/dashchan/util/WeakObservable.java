package com.mishiranu.dashchan.util;

import androidx.annotation.NonNull;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;

public class WeakObservable<T> implements Iterable<T> {
	private final ArrayList<WeakReference<T>> observers = new ArrayList<>();

	public void register(T observer) {
		observers.add(new WeakReference<>(observer));
	}

	public void unregister(T observer) {
		Iterator<WeakReference<T>> iterator = observers.iterator();
		while (iterator.hasNext()) {
			T item = iterator.next().get();
			if (item == observer || item == null) {
				iterator.remove();
			}
		}
	}

	@NonNull
	@Override
	public Iterator<T> iterator() {
		return new WeakIterator<>(observers.iterator(), WeakIterator.Provider.identity());
	}
}
