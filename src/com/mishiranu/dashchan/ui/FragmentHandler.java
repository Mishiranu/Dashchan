package com.mishiranu.dashchan.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import chan.content.ChanLocator;
import com.mishiranu.dashchan.content.model.PostNumber;
import com.mishiranu.dashchan.content.service.DownloadService;
import com.mishiranu.dashchan.util.ResourceUtils;
import java.util.Collection;

public interface FragmentHandler {
	interface Callback {
		default void onChansChanged(Collection<String> changed, Collection<String> removed) {}
		default void onStorageRequestResult() {}
	}

	void setTitleSubtitle(CharSequence title, CharSequence subtitle);
	ViewGroup getToolbarView();
	FrameLayout getToolbarExtra();
	Context getToolbarContext();

	default Drawable getActionBarIcon(int attr) {
		return ResourceUtils.getActionBarIcon(getToolbarContext(), attr);
	}

	void pushFragment(ContentFragment fragment);
	void removeFragment();

	DownloadService.Binder getDownloadBinder();
	boolean requestStorage();

	void navigateTargetAllowReturn(String chanName, ChanLocator.NavigationData navigationData);
	void scrollToPost(String chanName, String boardName, String threadNumber, PostNumber postNumber);
	Collection<DrawerForm.Page> obtainDrawerPages();

	void setActionBarLocked(String locker, boolean locked);
	void setNavigationAreaLocked(String locker, boolean locked);
}
