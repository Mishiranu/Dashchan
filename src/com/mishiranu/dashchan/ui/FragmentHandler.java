package com.mishiranu.dashchan.ui;

import androidx.fragment.app.Fragment;
import com.mishiranu.dashchan.util.ConfigurationLock;

public interface FragmentHandler {
	void pushFragment(Fragment fragment);
	ConfigurationLock getConfigurationLock();

	default void scrollToPost(String chanName, String boardName, String threadNumber, String postNumber) {}
}
