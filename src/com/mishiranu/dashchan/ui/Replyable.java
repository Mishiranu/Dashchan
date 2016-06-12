/*
 * Copyright 2014-2016 Fukurou Mishiranu
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mishiranu.dashchan.ui;

import android.os.Parcel;
import android.os.Parcelable;

public interface Replyable
{
	public void onRequestReply(ReplyData... data);
	
	public static class ReplyData implements Parcelable
	{
		public final String postNumber;
		public final String comment;
		
		public ReplyData(String postNumber, String comment)
		{
			this.postNumber = postNumber;
			this.comment = comment;
		}
		
		@Override
		public int describeContents()
		{
			return 0;
		}
		
		@Override
		public void writeToParcel(Parcel dest, int flags)
		{
			dest.writeString(postNumber);
			dest.writeString(comment);
		}
		
		public static final Creator<ReplyData> CREATOR = new Creator<ReplyData>()
		{
			@Override
			public ReplyData createFromParcel(Parcel source)
			{
				String postNumber = source.readString();
				String comment = source.readString();
				return new ReplyData(postNumber, comment);
			}
			
			@Override
			public ReplyData[] newArray(int size)
			{
				return new ReplyData[size];
			}
		};
	}
}