/*
 * Copyright 2014-2016 Fukurou Mishiranu
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mishiranu.dashchan.async;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Bundle;
import android.util.Pair;

import chan.util.StringUtils;

import com.mishiranu.dashchan.C;

public final class AsyncManager
{
	public static final class TaskFragment extends Fragment
	{
		public AsyncManager manager;
		
		public TaskFragment()
		{
			manager = new AsyncManager(this);
		}
		
		@Override
		public void onCreate(Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);
			setRetainInstance(true);
		}
		
		public boolean attached = false;
				
		@Deprecated
		@Override
		public void onAttach(Activity activity)
		{
			super.onAttach(activity);
			if (!attached)
			{
				attached = true;
				manager.onAttach();
			}
		}
		
		@Override
		public void onDestroy()
		{
			super.onDestroy();
			// Destroy called before detach
			if (attached)
			{
				attached = false;
				manager.onDestroy();
			}
		}
		
		@Override
		public void onDetach()
		{
			super.onDetach();
			if (attached)
			{
				attached = false;
				manager.onDetach();
			}
		}
	}
	
	private static AsyncManager get(FragmentManager fragmentManager)
	{
		String tag = AsyncManager.class.getName();
		TaskFragment fragment = (TaskFragment) fragmentManager.findFragmentByTag(tag);
		if (fragment == null)
		{
			fragment = new TaskFragment();
			fragmentManager.beginTransaction().add(fragment, tag).commit();
		}
		return fragment.manager;
	}
	
	@SuppressLint("NewApi")
	public static AsyncManager get(Fragment fragment)
	{
		if (C.API_JELLY_BEAN_MR1)
		{
			while (true)
			{
				Fragment parent = fragment.getParentFragment();
				if (parent == null) break;
				fragment = parent;
			}
		}
		return get(fragment.getFragmentManager());
	}
	
	public static AsyncManager get(Activity activity)
	{
		return get(activity.getFragmentManager());
	}
	
	private final TaskFragment mFragment;
	
	private AsyncManager(TaskFragment fragment)
	{
		mFragment = fragment;
	}
	
	private ArrayList<QueuedHolder> mQueued;
	
	private final LinkedHashMap<String, Holder> mWorkTasks = new LinkedHashMap<>();
	
	private static class QueuedHolder
	{
		public final String name;
		public final Callback callback;
		public final HashMap<String, Object> extra;
		public final boolean restart;
		
		public QueuedHolder(String name, Callback callback, HashMap<String, Object> extra, boolean restart)
		{
			this.name = name;
			this.callback = callback;
			this.extra = extra;
			this.restart = restart;
		}
	}
	
	private void onAttach()
	{
		if (mQueued != null)
		{
			for (QueuedHolder holder : mQueued)
			{
				enqueue(holder.name, holder.callback, holder.extra, holder.restart);
			}
			mQueued = null;
		}
	}
	
	private void onDestroy()
	{
		for (Holder holder : mWorkTasks.values())
		{
			if (holder.callback != null)
			{
				holder.callback.onRequestTaskCancel(holder.name, holder.task);
			}
			holder.callback = null;
			holder.manager = null;
		}
	}
	
	private void onDetach()
	{
		for (Holder holder : mWorkTasks.values()) holder.callback = null;
	}
	
	public static interface Callback
	{
		public Pair<Object, Holder> onCreateAndExecuteTask(String name, HashMap<String, Object> extra);
		public void onFinishTaskExecution(String name, Holder holder);
		public void onRequestTaskCancel(String name, Object task);
	}
	
	public static class Holder
	{
		private String name;
		private Object task;
		private AsyncManager manager;
		private Callback callback;
		private boolean ready = false;
		
		private Object[] mArguments;
		private int mArgumentIndex = 0;
		
		public final void storeResult(Object... arguments)
		{
			ready = true;
			mArguments = arguments;
			mArgumentIndex = 0;
			if (manager != null && callback != null)
			{
				manager.mWorkTasks.remove(name);
				callback.onFinishTaskExecution(name, this);
			}
		}
		
		@SuppressWarnings("unchecked")
		public <T> T getArgument(int index)
		{
			return (T) mArguments[index];
		}
		
		public <T> T nextArgument()
		{
			return getArgument(mArgumentIndex++);
		}
	}
	
	private void removeQueued(String name)
	{
		for (int i = mQueued.size() - 1; i >= 0; i--)
		{
			if (StringUtils.equals(name, mQueued.get(i).name)) mQueued.remove(i);
		}
	}
	
	public void startTask(String name, Callback callback, HashMap<String, Object> extra, boolean restart)
	{
		if (mFragment.attached) enqueue(name, callback, extra, restart); else
		{
			if (mQueued == null) mQueued = new ArrayList<>();
			removeQueued(name);
			mQueued.add(new QueuedHolder(name, callback, extra, restart));
		}
	}
	
	public void cancelTask(String name, Callback callback)
	{
		if (mQueued != null) removeQueued(name);
		Holder holder = mWorkTasks.remove(name);
		if (holder != null)
		{
			holder.manager = null;
			holder.callback = null;
			callback.onRequestTaskCancel(name, holder.task);
		}
	}
	
	private void enqueue(String name, Callback callback, HashMap<String, Object> extra, boolean restart)
	{
		Holder holder = mWorkTasks.get(name);
		if (holder != null)
		{
			if (restart)
			{
				mWorkTasks.remove(name);
				if (!holder.ready)
				{
					holder.manager = null;
					holder.callback = null;
					callback.onRequestTaskCancel(name, holder.task);
				}
			}
			else
			{
				if (holder.ready) callback.onFinishTaskExecution(name, holder);
				else holder.callback = callback;
				return;
			}
		}
		Pair<Object, Holder> pair = callback.onCreateAndExecuteTask(name, extra);
		holder = pair.second;
		holder.name = name;
		holder.task = pair.first;
		holder.manager = this;
		holder.callback = callback;
		mWorkTasks.put(name, holder);
	}
	
	public static abstract class SimpleTask<Params, Progress, Result> extends CancellableTask<Params, Progress, Result>
	{
		private final Holder mHolder = new Holder();
		
		public final Pair<Object, Holder> getPair()
		{
			return new Pair<Object, Holder>(this, mHolder);
		}
		
		@Override
		protected final void onPostExecute(Result result)
		{
			onStoreResult(mHolder, result);
			onAfterPostExecute(result);
		}
		
		protected void onStoreResult(Holder holder, Result result)
		{
			mHolder.storeResult(result);
		}
		
		protected void onAfterPostExecute(Result result)
		{
			
		}
	}
}