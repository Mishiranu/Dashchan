package com.mishiranu.dashchan.ui;

import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.fragment.app.Fragment;
import com.mishiranu.dashchan.util.ConfigurationLock;

public interface FragmentHandler {
	default ViewGroup getToolbarView() {
		throw new IllegalStateException();
	}

	default FrameLayout getToolbarExtra() {
		throw new IllegalStateException();
	}

	void pushFragment(Fragment fragment);
	void removeFragment();
	ConfigurationLock getConfigurationLock();

	default void scrollToPost(String chanName, String boardName, String threadNumber, String postNumber) {}
}
