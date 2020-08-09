package com.mishiranu.dashchan.util;

import androidx.annotation.NonNull;
import java.util.Iterator;

public class ConcatIterable<T> implements Iterable<T> {
	private final Iterable<T>[] iterables;

	@SafeVarargs
	public ConcatIterable(Iterable<T>... iterables) {
		this.iterables = iterables;
	}

	@NonNull
	@Override
	public Iterator<T> iterator() {
		return new Iterator<T>() {
			private Iterator<T> iterator = null;
			private int next = 0;

			@Override
			public boolean hasNext() {
				if (iterator != null && iterator.hasNext()) {
					return true;
				}
				if (iterables != null && iterables.length > next) {
					iterator = iterables[next++].iterator();
					return hasNext();
				}
				return false;
			}

			@Override
			public T next() {
				return iterator.next();
			}

			@Override
			public void remove() {
				iterator.remove();
			}
		};
	}
}
