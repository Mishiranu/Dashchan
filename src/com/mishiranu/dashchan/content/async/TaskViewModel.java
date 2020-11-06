package com.mishiranu.dashchan.content.async;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;
import java.lang.reflect.ParameterizedType;

public class TaskViewModel<Task extends ExecutorTask<?, ?>, Result> extends ViewModel {
	private Task task;
	private final MutableLiveData<Result> result = new MutableLiveData<>();

	public boolean hasTaskOrValue() {
		return task != null || result.getValue() != null;
	}

	public Task getTask() {
		return task;
	}

	public void attach(Task task) {
		result.setValue(null);
		if (this.task != null) {
			this.task.cancel();
		}
		this.task = task;
	}

	public void observe(LifecycleOwner owner, Observer<? super Result> observer) {
		result.observe(owner, result -> {
			if (result != null) {
				this.result.setValue(null);
				observer.onChanged(result);
			}
		});
	}

	@Override
	protected void onCleared() {
		if (task != null) {
			task.cancel();
			task = null;
		}
	}

	public void handleResult(Result result) {
		task = null;
		this.result.setValue(result);
	}

	public static class Proxy<Task extends ExecutorTask<?, ?>, Callback>
			extends TaskViewModel<Task, CallbackProxy<Callback>> {
		public final Callback callback;

		public Proxy() {
			ParameterizedType type = (ParameterizedType) getClass().getGenericSuperclass();
			@SuppressWarnings("unchecked")
			Class<Callback> callbackClass = (Class<Callback>) type.getActualTypeArguments()[1];
			this.callback = CallbackProxy.create(callbackClass, this::handleResult);
		}

		public void observe(LifecycleOwner owner, Callback callback) {
			observe(owner, result -> result.invoke(callback));
		}
	}
}
