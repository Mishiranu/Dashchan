package com.mishiranu.dashchan.content.model;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import chan.util.StringUtils;

public final class PostNumber implements Comparable<PostNumber>, Parcelable {
	public final int major;
	public final int minor;

	public PostNumber(int major, int minor) {
		this.major = major;
		this.minor = minor;
	}

	@Override
	public String toString() {
		if (minor != 0) {
			return major + "." + minor;
		} else {
			return Integer.toString(major);
		}
	}

	@Override
	public int compareTo(PostNumber o) {
		int result = Integer.compare(major, o.major);
		return result != 0 ? result : Integer.compare(minor, o.minor);
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (o instanceof PostNumber) {
			PostNumber n = (PostNumber) o;
			return major == n.major && minor == n.minor;
		}
		return false;
	}

	@Override
	public int hashCode() {
		int prime = 31;
		int result = 1;
		result = prime * result + major;
		result = prime * result + minor;
		return result;
	}

	public static PostNumber parseNullable(String string) {
		if (string == null) {
			return null;
		}
		String minorString = null;
		int index = -1;
		for (int i = 0; i < string.length(); i++) {
			char c = string.charAt(i);
			if (c < '0' || c > '9') {
				if (c == '.' && index == -1) {
					index = i;
				} else {
					return null;
				}
			}
		}
		if (index >= 0) {
			minorString = string.substring(index + 1);
			string = string.substring(0, index);
		}
		try {
			int major = Integer.parseInt(string);
			int minor = minorString != null ? Integer.parseInt(minorString) : 0;
			if (major > 0 && minor >= 0) {
				return new PostNumber(major, minor);
			} else {
				return null;
			}
		} catch (NumberFormatException e) {
			return null;
		}
	}

	@NonNull
	public static PostNumber parseOrThrow(String postNumber) {
		PostNumber number = parseNullable(postNumber);
		if (number == null) {
			throw new IllegalArgumentException("Post number is not valid: " + postNumber + ". " +
					"Post number must be a positive number or a pair of positive numbers separated by dot.");
		}
		return number;
	}

	public static void validateThreadNumber(String threadNumber, boolean allowEmpty) {
		if (StringUtils.isEmpty(threadNumber)) {
			if (!allowEmpty) {
				throw new IllegalArgumentException("Thread number is not defined");
			}
		} else {
			String escapedThreadNumber = StringUtils.escapeFile(threadNumber, false);
			if (threadNumber.length() > 30 || !escapedThreadNumber.equals(threadNumber)) {
				throw new IllegalArgumentException("Thread number is not valid: " + threadNumber + ". Thread number " +
						"is limited to 30 characters and must not contain any characters from \":\\/*?|<>\".");
			}
		}
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(major);
		dest.writeInt(minor);
	}

	public static final Creator<PostNumber> CREATOR = new Creator<PostNumber>() {
		@Override
		public PostNumber createFromParcel(Parcel source) {
			int major = source.readInt();
			int minor = source.readInt();
			return new PostNumber(major, minor);
		}

		@Override
		public PostNumber[] newArray(int size) {
			return new PostNumber[size];
		}
	};
}
