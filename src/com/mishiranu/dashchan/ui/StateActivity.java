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

package com.mishiranu.dashchan.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Bundle;

@SuppressLint("Registered")
public class StateActivity extends Activity {
	public static class InstanceFragment extends Fragment {
		@Override
		public void onDetach() {
			((StateActivity) getActivity()).callOnFinish(true);
			super.onDetach();
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		String tag = "instance";
		FragmentManager fragmentManager = getFragmentManager();
		InstanceFragment fragment = (InstanceFragment) fragmentManager.findFragmentByTag(tag);
		if (fragment == null) {
			fragment = new InstanceFragment();
			fragment.setRetainInstance(true);
			fragmentManager.beginTransaction().add(fragment, tag).commit();
		}
	}

	public void postRecreate() {
		getWindow().getDecorView().post(() -> recreate());
	}

	private boolean onFinishCalled = false;

	@Override
	public void recreate() {
		super.recreate();
		callOnFinish(true);
	}

	@Override
	protected void onPause() {
		super.onPause();
		callOnFinish(false);
	}

	@Override
	protected void onStop() {
		super.onStop();
		callOnFinish(false);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		callOnFinish(false);
	}

	private void callOnFinish(boolean force) {
		if (!onFinishCalled && (isFinishing() || force)) {
			onFinish();
			onFinishCalled = true;
		}
	}

	protected void onFinish() {}
}