package com.mishiranu.dashchan.content.async;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;

public final class AsyncManager {
	public static final class TaskFragment extends Fragment {
		public final AsyncManager manager;

		public TaskFragment() {
			manager = new AsyncManager(this);
		}

		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			setRetainInstance(true);
		}

		public boolean attached = false;
		public boolean resumed = false;

		@Override
		public void onActivityCreated(Bundle savedInstanceState) {
			super.onActivityCreated(savedInstanceState);
			if (!attached) {
				attached = true;
				manager.onAttach();
			}
		}

		@Override
		public void onResume() {
			super.onResume();
			resumed = true;
			manager.onResume();
		}

		@Override
		public void onPause() {
			super.onPause();
			resumed = false;
		}

		@Override
		public void onDestroy() {
			super.onDestroy();
			// Destroy called before detach
			if (attached) {
				attached = false;
				manager.onDestroy();
			}
		}

		@Override
		public void onDetach() {
			super.onDetach();
			if (attached) {
				attached = false;
				manager.onDetach();
			}
		}
	}

	private static AsyncManager get(FragmentManager fragmentManager) {
		String tag = AsyncManager.class.getName();
		TaskFragment fragment = (TaskFragment) fragmentManager.findFragmentByTag(tag);
		if (fragment == null) {
			fragment = new TaskFragment();
			fragmentManager.beginTransaction().add(fragment, tag).commit();
		}
		return fragment.manager;
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
	public static AsyncManager get(Fragment fragment) {
		if (C.API_JELLY_BEAN_MR1) {
			while (true) {
				Fragment parent = fragment.getParentFragment();
				if (parent == null) {
					break;
				}
				fragment = parent;
			}
		}
		return get(fragment.getParentFragmentManager());
	}

	public static AsyncManager get(FragmentActivity activity) {
		return get(activity.getSupportFragmentManager());
	}

	private final TaskFragment fragment;

	private AsyncManager(TaskFragment fragment) {
		this.fragment = fragment;
	}

	private ArrayList<QueuedHolder> queued;

	private final LinkedHashMap<String, Holder> workTasks = new LinkedHashMap<>();

	private static class QueuedHolder {
		public final String name;
		public final Callback callback;
		public final HashMap<String, Object> extra;
		public final boolean restart;

		public QueuedHolder(String name, Callback callback, HashMap<String, Object> extra, boolean restart) {
			this.name = name;
			this.callback = callback;
			this.extra = extra;
			this.restart = restart;
		}
	}

	private void onAttach() {
		if (queued != null) {
			for (QueuedHolder holder : queued) {
				enqueue(holder.name, holder.callback, holder.extra, holder.restart);
			}
			queued = null;
		}
	}

	private void onResume() {
		for (Iterator<Holder> iterator = workTasks.values().iterator(); iterator.hasNext();) {
			Holder holder = iterator.next();
			tryFinishTaskExecution(holder.name, holder, holder.callback, iterator::remove);
		}
	}

	private void onDestroy() {
		for (Holder holder : workTasks.values()) {
			if (holder.callback != null) {
				holder.callback.onRequestTaskCancel(holder.name, holder.task);
			}
			holder.manager = null;
			holder.callback = null;
		}
	}

	private void onDetach() {
		for (Holder holder : workTasks.values()) {
			holder.callback = null;
		}
	}

	public interface Callback {
		public Holder onCreateAndExecuteTask(String name, HashMap<String, Object> extra);
		public void onFinishTaskExecution(String name, Holder holder);
		public void onRequestTaskCancel(String name, Object task);
	}

	public static class Holder {
		private String name;
		private Object task;
		private AsyncManager manager;
		private Callback callback;
		private boolean ready = false;

		private Object[] arguments;
		private int argumentIndex = 0;

		public final Holder attach(Object task) {
			this.task = task;
			return this;
		}

		public final void storeResult(Object... arguments) {
			ready = true;
			this.arguments = arguments;
			argumentIndex = 0;
			if (manager != null && callback != null) {
				manager.tryFinishTaskExecution(name, this, callback);
			}
		}

		@SuppressWarnings("unchecked")
		public <T> T getArgument(int index) {
			return (T) arguments[index];
		}

		public <T> T nextArgument() {
			return getArgument(argumentIndex++);
		}
	}

	private void tryFinishTaskExecution(String name, Holder holder, Callback callback, Runnable removeCallback) {
		if (holder.ready && fragment.resumed && callback != null) {
			// Remove before onFinishTaskExecution call
			removeCallback.run();
			callback.onFinishTaskExecution(name, holder);
		}
	}

	private void tryFinishTaskExecution(String name, Holder holder, Callback callback) {
		tryFinishTaskExecution(name, holder, callback, () -> workTasks.remove(name));
	}

	private void removeQueued(String name) {
		for (int i = queued.size() - 1; i >= 0; i--) {
			if (StringUtils.equals(name, queued.get(i).name)) {
				queued.remove(i);
			}
		}
	}

	public void startTask(String name, Callback callback, HashMap<String, Object> extra, boolean restart) {
		if (fragment.attached) {
			enqueue(name, callback, extra, restart);
		} else {
			if (queued == null) {
				queued = new ArrayList<>();
			}
			removeQueued(name);
			queued.add(new QueuedHolder(name, callback, extra, restart));
		}
	}

	public void cancelTask(String name, Callback callback) {
		if (queued != null) {
			removeQueued(name);
		}
		Holder holder = workTasks.remove(name);
		if (holder != null) {
			cancelTaskInternal(name, holder, callback);
		}
	}

	public void cancelTaskInternal(String name, Holder holder, Callback callback) {
		holder.manager = null;
		holder.callback = null;
		callback.onRequestTaskCancel(name, holder.task);
	}

	private void enqueue(String name, Callback callback, HashMap<String, Object> extra, boolean restart) {
		Holder holder = workTasks.get(name);
		if (holder != null) {
			if (restart) {
				workTasks.remove(name);
				if (!holder.ready) {
					cancelTaskInternal(name, holder, callback);
				}
			} else {
				if (holder.ready) {
					tryFinishTaskExecution(name, holder, callback);
				} else {
					holder.callback = callback;
				}
				return;
			}
		}
		holder = callback.onCreateAndExecuteTask(name, extra);
		if (holder != null) {
			if (holder.task == null) {
				throw new IllegalArgumentException("Task is not attached to Holder");
			}
			holder.name = name;
			holder.manager = this;
			holder.callback = callback;
			workTasks.put(name, holder);
		}
	}

	@SuppressWarnings("UnusedParameters")
	public static abstract class SimpleTask<Params, Progress, Result> extends
			CancellableTask<Params, Progress, Result> {
		private final Holder holder = new Holder().attach(this);

		public final Holder getHolder() {
			return holder;
		}

		@Override
		protected final void onPostExecute(Result result) {
			onStoreResult(holder, result);
			onAfterPostExecute(result);
		}

		protected void onStoreResult(Holder holder, Result result) {
			holder.storeResult(result);
		}

		protected void onAfterPostExecute(Result result) {}
	}
}
