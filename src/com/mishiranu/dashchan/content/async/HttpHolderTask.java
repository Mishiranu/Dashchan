package com.mishiranu.dashchan.content.async;

import chan.content.Chan;
import chan.http.HttpHolder;

@SuppressWarnings("unchecked")
public abstract class HttpHolderTask<Params, Progress, Result> extends CancellableTask<Params, Progress, Result> {
	private final HttpHolder holder;

	public HttpHolderTask(Chan chan) {
		holder = new HttpHolder(chan);
	}

	@Override
	protected final Result doInBackground(Params... params) {
		try (HttpHolder.Use ignored = holder.use()) {
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
