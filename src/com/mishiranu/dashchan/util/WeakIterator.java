package com.mishiranu.dashchan.util;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class WeakIterator<T, R, N> implements Iterator<N> {
	public interface Provider<T, R, N> {
		WeakReference<R> getWeakReference(T data);
		N transform(T data, R referenced);

		@SuppressWarnings("unchecked")
		static <T> Provider<WeakReference<T>, T, T> identity() {
			return (Provider<WeakReference<T>, T, T>) (Provider<?, ?, ?>) PROVIDER_IDENTITY;
		}
	}

	private static final Provider<WeakReference<Object>, Object, Object> PROVIDER_IDENTITY =
			new Provider<WeakReference<Object>, Object, Object>() {
		@Override
		public WeakReference<Object> getWeakReference(WeakReference<Object> data) {
			return data;
		}

		@Override
		public Object transform(WeakReference<Object> data, Object referenced) {
			return referenced;
		}
	};

	private final Iterator<T> iterator;
	private final Provider<T, R, N> provider;

	public WeakIterator(Iterator<T> iterator, Provider<T, R, N> provider) {
		this.iterator = iterator;
		this.provider = provider;
	}

	private N next;

	@Override
	public boolean hasNext() {
		if (next == null) {
			while (iterator.hasNext()) {
				T data = iterator.next();
				WeakReference<R> reference = provider.getWeakReference(data);
				R referenced = reference != null ? reference.get() : null;
				if (referenced != null) {
					N next = provider.transform(data, referenced);
					if (next != null) {
						this.next = next;
						break;
					}
				} else {
					iterator.remove();
				}
			}
		}
		return next != null;
	}

	@Override
	public N next() {
		if (!hasNext()) {
			throw new NoSuchElementException();
		}
		N next = this.next;
		this.next = null;
		return next;
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}
}
