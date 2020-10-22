package com.mishiranu.dashchan.content.service.webview;

import android.os.Parcel;
import android.os.Parcelable;

public interface WebViewExtra extends Parcelable {
	default String getInjectJavascript() {
		return null;
	}

	@Override
	default int describeContents() {
		return 0;
	}

	@Override
	default void writeToParcel(Parcel dest, int flags) {
		dest.writeString(getClass().getName());
	}

	Creator<WebViewExtra> CREATOR = new Creator<WebViewExtra>() {
		@SuppressWarnings("unchecked")
		@Override
		public WebViewExtra createFromParcel(Parcel source) {
			String className = source.readString();
			Creator<WebViewExtra> creator;
			try {
				creator = (Creator<WebViewExtra>) Class.forName(className).getField("CREATOR").get(null);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			return creator.createFromParcel(source);
		}

		@Override
		public WebViewExtra[] newArray(int size) {
			return new WebViewExtra[size];
		}
	};
}
