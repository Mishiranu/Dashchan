package com.mishiranu.dashchan.ui.gallery;

import android.content.Context;
import chan.content.ChanLocator;
import com.mishiranu.dashchan.content.model.GalleryItem;
import com.mishiranu.dashchan.util.ConfigurationLock;
import java.util.ArrayList;

public class GalleryInstance {
	public static final int FLAG_LOCKED_USER = 0x00000001;
	public static final int FLAG_LOCKED_GRID = 0x00000002;
	public static final int FLAG_LOCKED_ERROR = 0x00000004;

	public final Context context;
	public final Callback callback;

	public String chanName;
	public ChanLocator locator;
	public ArrayList<GalleryItem> galleryItems;

	public int actionBarColor;

	public GalleryInstance(Context context, Callback callback) {
		this.context = context;
		this.callback = callback;
	}

	public interface Callback {
		ConfigurationLock getConfigurationLock();

		void downloadGalleryItem(GalleryItem galleryItem);
		void downloadGalleryItems(ArrayList<GalleryItem> galleryItems);

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
