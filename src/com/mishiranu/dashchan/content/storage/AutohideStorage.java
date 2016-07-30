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

package com.mishiranu.dashchan.content.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.os.Parcel;
import android.os.Parcelable;

import chan.util.CommonUtils;
import chan.util.StringUtils;

public class AutohideStorage extends StorageManager.Storage
{
	private static final String KEY_CHAN_NAMES = "chanNames";
	private static final String KEY_BOARD_NAME = "boardName";
	private static final String KEY_THREAD_NUMBER = "threadNumber";
	private static final String KEY_OPTION_ORIGINAL_POST = "optionOriginalPost";
	private static final String KEY_OPTION_SAGE = "optionSage";
	private static final String KEY_OPTION_SUBJECT = "optionSubject";
	private static final String KEY_OPTION_COMMENT = "optionComment";
	private static final String KEY_OPTION_NAME = "optionName";
	private static final String KEY_VALUE = "value";
	
	private static final AutohideStorage INSTANCE = new AutohideStorage();
	
	public static AutohideStorage getInstance()
	{
		return INSTANCE;
	}
	
	private final ArrayList<AutohideItem> mAutohideItems = new ArrayList<>();
	
	private AutohideStorage()
	{
		super("autohide", 1000, 10000);
		JSONObject jsonObject = read();
		if (jsonObject != null)
		{
			JSONArray jsonArray = jsonObject.optJSONArray("data");
			if (jsonArray != null)
			{
				for (int i = 0; i < jsonArray.length(); i++)
				{
					jsonObject = jsonArray.optJSONObject(i);
					if (jsonObject != null)
					{
						HashSet<String> chanNames = null;
						JSONArray chanNamesArray = jsonObject.optJSONArray(KEY_CHAN_NAMES);
						if (chanNamesArray != null)
						{
							for (int j = 0; j < chanNamesArray.length(); j++)
							{
								String chanName = chanNamesArray.optString(j, null);
								if (!StringUtils.isEmpty(chanName))
								{
									if (chanNames == null) chanNames = new HashSet<>();
									chanNames.add(chanName);
								}
							}
						}
						String boardName = jsonObject.optString(KEY_BOARD_NAME, null);
						String threadNumber = jsonObject.optString(KEY_THREAD_NUMBER, null);
						boolean optionOriginalPost = jsonObject.optBoolean(KEY_OPTION_ORIGINAL_POST);
						boolean optionSage = jsonObject.optBoolean(KEY_OPTION_SAGE);
						boolean optionSubject = jsonObject.optBoolean(KEY_OPTION_SUBJECT);
						boolean optionComment = jsonObject.optBoolean(KEY_OPTION_COMMENT);
						boolean optionName = jsonObject.optBoolean(KEY_OPTION_NAME);
						String value = jsonObject.optString(KEY_VALUE, null);
						mAutohideItems.add(new AutohideItem(chanNames, boardName, threadNumber, optionOriginalPost,
								optionSage, optionSubject, optionComment, optionName, value));
					}
				}
			}
		}
	}
	
	public ArrayList<AutohideItem> getItems()
	{
		return mAutohideItems;
	}

	@Override
	public Object onClone()
	{
		ArrayList<AutohideItem> autohideItems = new ArrayList<>(mAutohideItems.size());
		for (AutohideItem autohideItem : mAutohideItems) autohideItems.add(new AutohideItem(autohideItem));
		return autohideItems;
	}

	@Override
	public JSONObject onSerialize(Object data) throws JSONException
	{
		@SuppressWarnings("unchecked")
		ArrayList<AutohideItem> autohideItems = (ArrayList<AutohideItem>) data;
		if (autohideItems.size() > 0)
		{
			JSONArray jsonArray = new JSONArray();
			for (AutohideItem autohideItem : autohideItems)
			{
				JSONObject jsonObject = new JSONObject();
				if (autohideItem.chanNames != null && autohideItem.chanNames.size() > 0)
				{
					JSONArray chanNamesArray = new JSONArray();
					for (String chanName : autohideItem.chanNames) chanNamesArray.put(chanName);
					jsonObject.put(KEY_CHAN_NAMES, chanNamesArray);
				}
				putJson(jsonObject, KEY_BOARD_NAME, autohideItem.boardName);
				putJson(jsonObject, KEY_THREAD_NUMBER, autohideItem.threadNumber);
				putJson(jsonObject, KEY_OPTION_ORIGINAL_POST, autohideItem.optionOriginalPost);
				putJson(jsonObject, KEY_OPTION_SAGE, autohideItem.optionSage);
				putJson(jsonObject, KEY_OPTION_SUBJECT, autohideItem.optionSubject);
				putJson(jsonObject, KEY_OPTION_COMMENT, autohideItem.optionComment);
				putJson(jsonObject, KEY_OPTION_NAME, autohideItem.optionName);
				putJson(jsonObject, KEY_VALUE, autohideItem.value);
				jsonArray.put(jsonObject);
			}
			JSONObject jsonObject = new JSONObject();
			jsonObject.put("data", jsonArray);
			return jsonObject;
		}
		return null;
	}
	
	public void add(AutohideItem autohideItem)
	{
		mAutohideItems.add(autohideItem);
		serialize();
	}
	
	public void update(int index, AutohideItem autohideItem)
	{
		mAutohideItems.set(index, autohideItem);
		serialize();
	}
	
	public void delete(int index)
	{
		mAutohideItems.remove(index);
		serialize();
	}
	
	public static class AutohideItem implements Parcelable
	{
		public HashSet<String> chanNames;
		
		public String boardName;
		public String threadNumber;
		
		public boolean optionOriginalPost;
		public boolean optionSage;
		
		public boolean optionSubject;
		public boolean optionComment;
		public boolean optionName;
		
		public String value;
		
		private volatile boolean mReady = false;
		private Pattern mPattern;
		
		public AutohideItem()
		{
			
		}
		
		public AutohideItem(AutohideItem autohideItem)
		{
			this(autohideItem.chanNames, autohideItem.boardName, autohideItem.threadNumber,
					autohideItem.optionOriginalPost, autohideItem.optionSage, autohideItem.optionSubject,
					autohideItem.optionComment, autohideItem.optionName, autohideItem.value);
		}
		
		public AutohideItem(HashSet<String> chanNames, String boardName, String threadNumber, boolean optionOriginalPost,
				boolean optionSage, boolean optionSubject, boolean optionComment, boolean optionName, String value)
		{
			update(chanNames, boardName, threadNumber, optionOriginalPost, optionSage,
					optionSubject, optionComment, optionName, value);
		}
		
		public static Pattern makePattern(String value)
		{
			return Pattern.compile(value, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
		}
		
		public void update(HashSet<String> chanNames, String boardName, String threadNumber,
				boolean optionOriginalPost, boolean optionSage, boolean optionSubject,
				boolean optionComment, boolean optionName, String value)
		{
			this.chanNames = chanNames;
			this.boardName = boardName;
			this.threadNumber = threadNumber;
			this.optionOriginalPost = optionOriginalPost;
			this.optionSage = optionSage;
			this.optionSubject = optionSubject;
			this.optionComment = optionComment;
			this.optionName = optionName;
			this.value = value;
		}
		
		public String find(String data)
		{
			if (!mReady)
			{
				synchronized (this)
				{
					if (!mReady)
					{
						try
						{
							mPattern = makePattern(value);
						}
						catch (Exception e)
						{
							
						}
						mReady = true;
					}
				}
			}
			try
			{
				Matcher matcher = mPattern.matcher(data);
				if (matcher.find())
				{
					String result = matcher.group();
					if (StringUtils.isEmpty(result)) result = value;
					return result;
				}
			}
			catch (Exception e)
			{
				
			}
			return null;
		}
		
		public String getReason(boolean subject, boolean name, String text, String findResult)
		{
			StringBuilder builder = new StringBuilder();
			if (optionSage) builder.append("sage ");
			if (optionSubject && subject) builder.append("subject ");
			if (optionName && name) builder.append("name ");
			if (!StringUtils.isEmpty(findResult)) builder.append(findResult);
			else if (!StringUtils.isEmpty(text)) builder.append(StringUtils.cutIfLongerToLine(text, 80, true));
			return builder.toString();
		}

		@Override
		public int describeContents()
		{
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags)
		{
			dest.writeStringArray(CommonUtils.toArray(chanNames, String.class));
			dest.writeString(boardName);
			dest.writeString(threadNumber);
			dest.writeInt(optionOriginalPost ? 1 : 0);
			dest.writeInt(optionSage ? 1 : 0);
			dest.writeInt(optionSubject ? 1 : 0);
			dest.writeInt(optionComment ? 1 : 0);
			dest.writeInt(optionName ? 1 : 0);
			dest.writeString(value);
		}
		
		public static final Creator<AutohideItem> CREATOR = new Creator<AutohideItem>()
		{
			@Override
			public AutohideItem createFromParcel(Parcel source)
			{
				AutohideItem autohideItem = new AutohideItem();
				String[] chanNames = source.createStringArray();
				if (chanNames != null)
				{
					autohideItem.chanNames = new HashSet<>();
					Collections.addAll(autohideItem.chanNames, chanNames);
				}
				autohideItem.boardName = source.readString();
				autohideItem.threadNumber = source.readString();
				autohideItem.optionOriginalPost = source.readInt() != 0;
				autohideItem.optionSage = source.readInt() != 0;
				autohideItem.optionSubject = source.readInt() != 0;
				autohideItem.optionComment = source.readInt() != 0;
				autohideItem.optionName = source.readInt() != 0;
				autohideItem.value = source.readString();
				return autohideItem;
			}
			
			@Override
			public AutohideItem[] newArray(int size)
			{
				return new AutohideItem[size];
			}
		};
	}
}