package com.mishiranu.dashchan.content.async;

import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class ExecutorTask<Progress, Result> {
	private enum Message {PROGRESS, RESULT}

	private static class ProgressHolder<Progress> {
		public final ExecutorTask<Progress, ?> task;
		public final Progress progress;

		public ProgressHolder(ExecutorTask<Progress, ?> task, Progress progress) {
			this.task = task;
			this.progress = progress;
		}

		public void handle() {
			if (!task.isCancelled()) {
				task.onProgress(progress);
			}
		}
	}

	private static class ResultHolder<Result> {
		public final ExecutorTask<?, Result> task;
		public final Result result;

		public ResultHolder(ExecutorTask<?, Result> task, Result result) {
			this.task = task;
			this.result = result;
		}

		public void handle() {
			if (task.isCancelled()) {
				task.onCancel(result);
			} else {
				task.onComplete(result);
			}
		}
	}

	private static final Handler HANDLER = new Handler(Looper.getMainLooper(), msg -> {
		switch (Message.values()[msg.what]) {
			case PROGRESS: {
				ProgressHolder<?> progressHolder = (ProgressHolder<?>) msg.obj;
				progressHolder.handle();
				return true;
			}
			case RESULT: {
				ResultHolder<?> resultHolder = (ResultHolder<?>) msg.obj;
				resultHolder.handle();
				return true;
			}
			default: {
				return false;
			}
		}
	});

	private final AtomicBoolean executed = new AtomicBoolean(false);
	private final AtomicBoolean cancelled = new AtomicBoolean(false);
	private final AtomicBoolean started = new AtomicBoolean(false);

	private final Callable<Result> worker = () -> {
		started.set(true);
		Result result = null;
		boolean success = false;
		try {
			result = run();
			success = true;
		} catch (InterruptedException e) {
			if (cancelled.get()) {
				throw e;
			} else {
				throw new IllegalStateException(e);
			}
		} finally {
			if (!success) {
				cancelled.set(true);
			}
			postResult(result);
		}
		return result;
	};

	private final FutureTask<Result> task = new FutureTask<Result>(worker) {
		@SuppressWarnings("StatementWithEmptyBody")
		@Override
		protected void done() {
			Result result = null;
			try {
				result = get();
			} catch (ExecutionException e) {
				Throwable cause = e.getCause();
				if (cause instanceof InterruptedException) {
					// Ignore
				} else if (cause instanceof RuntimeException) {
					throw (RuntimeException) cause;
				} else if (cause instanceof Error) {
					throw (Error) cause;
				} else {
					throw new RuntimeException(cause);
				}
			} catch (InterruptedException | CancellationException e) {
				// Ignore
			}
			if (!started.get()) {
				postResult(result);
			}
		}
	};

	private void postResult(Result result) {
		HANDLER.obtainMessage(Message.RESULT.ordinal(), new ResultHolder<>(this, result)).sendToTarget();
	}

	public void execute(Executor executor) {
		boolean executed = this.executed.getAndSet(true);
		if (executed) {
			throw new IllegalStateException();
		}
		onPrepare();
		executor.execute(task);
	}

	public void cancel() {
		cancelled.set(true);
		task.cancel(true);
	}

	protected void notifyProgress(Progress progress) {
		if (!isCancelled()) {
			HANDLER.obtainMessage(Message.PROGRESS.ordinal(), new ProgressHolder<>(this, progress)).sendToTarget();
		}
	}

	protected boolean isCancelled() {
		return cancelled.get();
	}

	protected void onPrepare() {}
	protected abstract Result run() throws InterruptedException;
	protected void onProgress(Progress progress) {}
	protected void onCancel(Result result) {}
	protected void onComplete(Result result) {}
}
