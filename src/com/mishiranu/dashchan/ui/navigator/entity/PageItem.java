package com.mishiranu.dashchan.ui.navigator.entity;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.fragment.app.FragmentManager;
import com.mishiranu.dashchan.ui.navigator.PageFragment;

public class PageItem implements Parcelable {
	public long createdRealtime;
	public String threadTitle;
	public boolean returnable;

	private static final StackItem.SaveFragment SAVE = (fragmentManager, fragment) -> {
		PageFragment pageFragment = (PageFragment) fragment;
		try {
			pageFragment.setSaveToStack(true);
			return fragmentManager.saveFragmentInstanceState(fragment);
		} finally {
			pageFragment.setSaveToStack(false);
		}
	};

	public SavedPageItem toSaved(FragmentManager fragmentManager, PageFragment fragment) {
		return new SavedPageItem(new StackItem(fragmentManager, fragment, SAVE),
				createdRealtime, threadTitle, returnable);
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeLong(createdRealtime);
		dest.writeString(threadTitle);
		dest.writeByte((byte) (returnable ? 1 : 0));
	}

	public static final Creator<PageItem> CREATOR = new Creator<PageItem>() {
		@Override
		public PageItem createFromParcel(Parcel in) {
			PageItem pageItem = new PageItem();
			pageItem.createdRealtime = in.readLong();
			pageItem.threadTitle = in.readString();
			pageItem.returnable = in.readByte() != 0;
			return pageItem;
		}

		@Override
		public PageItem[] newArray(int size) {
			return new PageItem[size];
		}
	};
}
