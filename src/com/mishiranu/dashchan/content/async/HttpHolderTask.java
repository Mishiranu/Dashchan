package com.mishiranu.dashchan.content.async;

import chan.http.HttpHolder;

@SuppressWarnings("unchecked")
public abstract class HttpHolderTask<Params, Progress, Result> extends CancellableTask<Params, Progress, Result> {
	private final HttpHolder holder = new HttpHolder();

	@Override
	protected final Result doInBackground(Params... params) {
		try (HttpHolder holder = this.holder) {
			return doInBackground(holder, params);
		}
	}

	protected abstract Result doInBackground(HttpHolder holder, Params... params);

	@Override
	public void cancel() {
		cancel(true);
		holder.interrupt();
	}
}
