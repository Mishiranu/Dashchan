package com.mishiranu.dashchan.ui;

import android.annotation.SuppressLint;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import com.mishiranu.dashchan.content.LocaleManager;

@SuppressLint("Registered")
public class StateActivity extends FragmentActivity {
	public static class InstanceFragment extends Fragment {
		@Override
		public void onDetach() {
			((StateActivity) getActivity()).callOnFinish(true);
			super.onDetach();
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		LocaleManager.getInstance().apply(this);
		super.onCreate(savedInstanceState);
		String tag = "instance";
		FragmentManager fragmentManager = getSupportFragmentManager();
		InstanceFragment fragment = (InstanceFragment) fragmentManager.findFragmentByTag(tag);
		if (fragment == null) {
			fragment = new InstanceFragment();
			fragment.setRetainInstance(true);
			fragmentManager.beginTransaction().add(fragment, tag).commit();
		}
	}

	private boolean isRecreateCalled = false;

	public void postRecreate() {
		if (!isRecreateCalled) {
			isRecreateCalled = true;
			getWindow().getDecorView().post(this::recreate);
		}
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
