package com.mishiranu.dashchan.ui.gallery;

import android.content.Context;
import android.view.Window;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelStoreOwner;
import com.mishiranu.dashchan.content.model.GalleryItem;
import com.mishiranu.dashchan.ui.InstanceDialog;
import java.util.List;

public class GalleryInstance {
	public interface Flags {
		int LOCKED_USER = 0x00000001;
		int LOCKED_GRID = 0x00000002;
		int LOCKED_ERROR = 0x00000004;
	}

	public final Context context;
	public final Callback callback;
	public final int actionBarColor;

	public final String chanName;
	public final List<GalleryItem> galleryItems;

	public GalleryInstance(Context context, Callback callback, int actionBarColor,
			String chanName, List<GalleryItem> galleryItems) {
		this.context = context;
		this.callback = callback;
		this.actionBarColor = actionBarColor;
		this.chanName = chanName;
		this.galleryItems = galleryItems;
	}

	public static Callback getCallback(InstanceDialog.Provider provider) {
		return (Callback) provider.getParentFragment();
	}

	public interface Callback extends ViewModelStoreOwner {
		Window getWindow();
		FragmentManager getChildFragmentManager();

		void downloadGalleryItem(GalleryItem galleryItem);
		void downloadGalleryItems(List<GalleryItem> galleryItems);

		void modifyVerticalSwipeState(boolean ignoreIfGallery, float value);
		void updateTitle();

		void navigateGalleryOrFinish(boolean enableGalleryMode);
		void navigatePageFromList(int position);
		void navigatePost(GalleryItem galleryItem, boolean manually, boolean force);

		boolean isAllowNavigatePostManually(boolean fromPager);

		void invalidateOptionsMenu();
		void setScreenOnFixed(boolean fixed);

		boolean isGalleryWindow();
		boolean isGalleryMode();

		boolean isSystemUiVisible();
		void modifySystemUiVisibility(int flag, boolean value);
		void toggleSystemUIVisibility(int flag);
	}
}
