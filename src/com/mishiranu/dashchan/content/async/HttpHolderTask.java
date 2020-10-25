package com.mishiranu.dashchan.content.async;

import chan.content.Chan;
import chan.http.HttpHolder;

public abstract class HttpHolderTask<Progress, Result> extends ExecutorTask<Progress, Result> {
	private final HttpHolder holder;

	public HttpHolderTask(Chan chan) {
		holder = new HttpHolder(chan);
	}

	@Override
	protected final Result run() {
		try (HttpHolder.Use ignored = holder.use()) {
			return run(holder);
		}
	}

	protected abstract Result run(HttpHolder holder);

	@Override
	public void cancel() {
		super.cancel();
		holder.interrupt();
	}
}
