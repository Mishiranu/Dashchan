package com.mishiranu.dashchan.ui;

import android.graphics.drawable.Drawable;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.fragment.app.Fragment;
import com.mishiranu.dashchan.content.service.DownloadService;
import com.mishiranu.dashchan.util.ConfigurationLock;

public interface FragmentHandler {
	void setTitleSubtitle(CharSequence title, CharSequence subtitle);

	default ViewGroup getToolbarView() {
		throw new IllegalStateException();
	}

	default FrameLayout getToolbarExtra() {
		throw new IllegalStateException();
	}

	Drawable getActionBarIcon(int attr);

	void pushFragment(Fragment fragment);
	void removeFragment();
	DownloadService.Binder getDownloadBinder();
	ConfigurationLock getConfigurationLock();

	default void scrollToPost(String chanName, String boardName, String threadNumber, String postNumber) {}
}
