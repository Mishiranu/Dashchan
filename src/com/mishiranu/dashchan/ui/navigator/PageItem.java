package com.mishiranu.dashchan.ui.navigator;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.fragment.app.FragmentManager;
import com.mishiranu.dashchan.ui.StackItem;

public class PageItem implements Parcelable {
	public long createdRealtime;
	public String threadTitle;
	public boolean allowReturn;

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
				createdRealtime, threadTitle, allowReturn);
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeLong(createdRealtime);
		dest.writeString(threadTitle);
		dest.writeByte((byte) (allowReturn ? 1 : 0));
	}

	public static final Creator<PageItem> CREATOR = new Creator<PageItem>() {
		@Override
		public PageItem createFromParcel(Parcel in) {
			PageItem pageItem = new PageItem();
			pageItem.createdRealtime = in.readLong();
			pageItem.threadTitle = in.readString();
			pageItem.allowReturn = in.readByte() != 0;
			return pageItem;
		}

		@Override
		public PageItem[] newArray(int size) {
			return new PageItem[size];
		}
	};
}
