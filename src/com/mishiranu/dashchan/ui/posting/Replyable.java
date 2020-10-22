package com.mishiranu.dashchan.ui.posting;

import android.os.Parcel;
import android.os.Parcelable;
import com.mishiranu.dashchan.content.model.PostNumber;

public interface Replyable {
	boolean onRequestReply(boolean click, ReplyData... data);

	class ReplyData implements Parcelable {
		public final PostNumber postNumber;
		public final String comment;

		public ReplyData(PostNumber postNumber, String comment) {
			this.postNumber = postNumber;
			this.comment = comment;
		}

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			dest.writeByte((byte) (postNumber != null ? 1 : 0));
			if (postNumber != null) {
				postNumber.writeToParcel(dest, flags);
			}
			dest.writeString(comment);
		}

		public static final Creator<ReplyData> CREATOR = new Creator<ReplyData>() {
			@Override
			public ReplyData createFromParcel(Parcel source) {
				PostNumber postNumber = source.readByte() != 0
						? PostNumber.CREATOR.createFromParcel(source) : null;
				String comment = source.readString();
				return new ReplyData(postNumber, comment);
			}

			@Override
			public ReplyData[] newArray(int size) {
				return new ReplyData[size];
			}
		};
	}
}
