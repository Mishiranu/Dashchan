package com.mishiranu.dashchan.ui.navigator.entity;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

public class StackItem implements Parcelable {
	interface ReplaceFragment {
		Fragment replace(Fragment fragment);
	}

	public final String className;
	public final Bundle arguments;
	public final Fragment.SavedState savedState;

	public StackItem(String className, Bundle arguments, Fragment.SavedState savedState) {
		this.className = className;
		this.arguments = arguments;
		this.savedState = savedState;
	}

	public StackItem(FragmentManager fragmentManager, Fragment fragment) {
		this(fragment.getClass().getName(), fragment.getArguments(),
				fragmentManager.saveFragmentInstanceState(fragment));
	}

	public Fragment create(ReplaceFragment replaceFragment) {
		Fragment fragment;
		try {
			fragment = (Fragment) Class.forName(className).newInstance();
		} catch (Error | RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		if (arguments != null) {
			fragment.setArguments(arguments);
		}
		if (replaceFragment != null) {
			fragment = replaceFragment.replace(fragment);
		}
		if (savedState != null) {
			fragment.setInitialSavedState(savedState);
		}
		return fragment;
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(className);
		dest.writeByte((byte) (arguments != null ? 1 : 0));
		if (arguments != null) {
			arguments.writeToParcel(dest, flags);
		}
		dest.writeByte((byte) (savedState != null ? 1 : 0));
		if (savedState != null) {
			savedState.writeToParcel(dest, flags);
		}
	}

	public static final Creator<StackItem> CREATOR = new Creator<StackItem>() {
		@Override
		public StackItem createFromParcel(Parcel in) {
			String className = in.readString();
			Bundle arguments = in.readByte() != 0 ? Bundle.CREATOR.createFromParcel(in) : null;
			if (arguments != null) {
				arguments.setClassLoader(getClass().getClassLoader());
			}
			Fragment.SavedState savedState = in.readByte() != 0
					? Fragment.SavedState.CREATOR.createFromParcel(in) : null;
			return new StackItem(className, arguments, savedState);
		}

		@Override
		public StackItem[] newArray(int size) {
			return new StackItem[size];
		}
	};
}
