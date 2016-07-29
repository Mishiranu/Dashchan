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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import chan.content.ChanConfiguration;
import chan.content.ChanPerformer;
import chan.util.CommonUtils;
import chan.util.StringUtils;

import com.mishiranu.dashchan.app.MainApplication;
import com.mishiranu.dashchan.content.model.FileHolder;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.LruCache;

public class DraftsStorage extends StorageManager.Storage
{
	private static final String KEY_THREAD_DRAFTS = "threadDrafts";
	private static final String KEY_POST_DRAFTS = "postDrafts";
	
	private static final DraftsStorage INSTANCE = new DraftsStorage();
	
	public static DraftsStorage getInstance()
	{
		return INSTANCE;
	}
	
	private final LruCache<String, ThreadDraft> mThreadDrafts = new LruCache<>(15);
	private final LruCache<String, PostDraft> mPostDrafts = new LruCache<>(5);
	
	private String mCaptchaChanName;
	private CaptchaDraft mCaptchaDraft;
	
	private DraftsStorage()
	{
		super("drafts", 2000, 10000);
		JSONObject jsonObject = read();
		if (jsonObject != null)
		{
			JSONArray threadsArray = jsonObject.optJSONArray(KEY_THREAD_DRAFTS);
			if (threadsArray != null && threadsArray.length() > 0)
			{
				try
				{
					for (int i = 0; i < threadsArray.length(); i++)
					{
						ThreadDraft threadDraft = ThreadDraft.fromJsonObject(threadsArray.getJSONObject(i));
						if (threadDraft != null) mThreadDrafts.put(makeKey(threadDraft), threadDraft);
					}
				}
				catch (JSONException e)
				{
					
				}
			}
			JSONArray postsArray = jsonObject.optJSONArray(KEY_POST_DRAFTS);
			if (postsArray != null && postsArray.length() > 0)
			{
				try
				{
					for (int i = 0; i < postsArray.length(); i++)
					{
						PostDraft postDraft = PostDraft.fromJsonObject(postsArray.getJSONObject(i));
						if (postDraft != null) mPostDrafts.put(makeKey(postDraft), postDraft);
					}
				}
				catch (JSONException e)
				{
					
				}
			}
		}
	}
	
	@Override
	public Object onClone()
	{
		return new Object[] {new ArrayList<>(mThreadDrafts.values()), new ArrayList<>(mPostDrafts.values())};
	}
	
	@Override
	public JSONObject onSerialize(Object data) throws JSONException
	{
		Object[] dataArray = (Object[]) data;
		@SuppressWarnings("unchecked")
		ArrayList<ThreadDraft> threadDrafts = (ArrayList<ThreadDraft>) dataArray[0];
		@SuppressWarnings("unchecked")
		ArrayList<PostDraft> postDrafts = (ArrayList<PostDraft>) dataArray[1];
		JSONObject jsonObject = new JSONObject();
		if (threadDrafts.size() > 0)
		{
			JSONArray jsonArray = new JSONArray();
			for (ThreadDraft threadDraft : threadDrafts) jsonArray.put(threadDraft.toJsonObject());
			jsonObject.put(KEY_THREAD_DRAFTS, jsonArray);
		}
		if (postDrafts.size() > 0)
		{
			JSONArray jsonArray = new JSONArray();
			for (PostDraft postDraft : postDrafts) jsonArray.put(postDraft.toJsonObject());
			jsonObject.put(KEY_POST_DRAFTS, jsonArray);
		}
		return jsonObject;
	}
	
	private static String makeKey(String chanName, String boardName, String threadNumber)
	{
		return chanName + "/" + boardName + "/" + threadNumber;
	}
	
	private static String makeKey(ThreadDraft threadDraft)
	{
		return makeKey(threadDraft.chanName, threadDraft.boardName, threadDraft.threadNumber);
	}
	
	public void store(ThreadDraft threadDraft)
	{
		if (threadDraft != null)
		{
			boolean serialize = true;
			if (threadDraft.isEmpty()) serialize = mThreadDrafts.remove(makeKey(threadDraft)) != null;
			else mThreadDrafts.put(makeKey(threadDraft), threadDraft);
			if (serialize) serialize();
		}
	}
	
	public ThreadDraft getThreadDraft(String chanName, String boardName, String threadNumber)
	{
		return mThreadDrafts.get(makeKey(chanName, boardName, threadNumber));
	}
	
	public void store(PostDraft postDraft)
	{
		if (postDraft != null)
		{
			boolean serialize = true;
			if (postDraft.isEmpty()) serialize = mPostDrafts.remove(makeKey(postDraft)) != null;
			else mPostDrafts.put(makeKey(postDraft), postDraft);
			if (serialize) serialize();
		}
	}
	
	public PostDraft getPostDraft(String chanName, String boardName, String threadNumber)
	{
		return mPostDrafts.get(makeKey(chanName, boardName, threadNumber));
	}
	
	public void removePostDraft(String chanName, String boardName, String threadNumber)
	{
		PostDraft postDraft = mPostDrafts.remove(makeKey(chanName, boardName, threadNumber));
		if (postDraft != null) serialize();
	}
	
	public void store(String chanName, CaptchaDraft captchaDraft)
	{
		mCaptchaChanName = chanName;
		mCaptchaDraft = captchaDraft;
	}
	
	public CaptchaDraft getCaptchaDraft(String chanName)
	{
		if (mCaptchaDraft != null && mCaptchaChanName.equals(chanName) && System.currentTimeMillis() -
				mCaptchaDraft.loadTime <= 5 * 60 * 1000)
		{
			return mCaptchaDraft;
		}
		return null;
	}
	
	public void removeCaptchaDraft()
	{
		mCaptchaDraft = null;
		mCaptchaChanName = null;
	}
	
	public static class ThreadDraft
	{
		private static final String KEY_CHAN_NAME = "chanName";
		private static final String KEY_BOARD_NAME = "boardName";
		private static final String KEY_THREAD_NUMBER = "threadNumber";
		
		private static final String KEY_NAME = "name";
		private static final String KEY_EMAIL = "email";
		private static final String KEY_PASSWORD = "password";
		private static final String KEY_OPTION_SAGE = "optionSage";
		private static final String KEY_OPTION_ORIGINAL_POSTER = "optionOriginalPoster";
		private static final String KEY_USER_ICON = "userIcon";
		
		public final String chanName;
		public final String boardName;
		public final String threadNumber;
		
		public final String name;
		public final String email;
		public final String password;
		public final boolean optionSage;
		public final boolean optionOriginalPoster;
		public final String userIcon;
		
		public ThreadDraft(String chanName, String boardName, String threadNumber, String name, String email,
				String password, boolean optionSage, boolean optionOriginalPoster, String userIcon)
		{
			this.chanName = chanName;
			this.boardName = boardName;
			this.threadNumber = threadNumber;
			this.name = name;
			this.email = email;
			this.password = password;
			this.optionSage = optionSage;
			this.optionOriginalPoster = optionOriginalPoster;
			this.userIcon = userIcon;
		}
		
		public boolean isEmpty()
		{
			return StringUtils.isEmpty(name) && StringUtils.isEmpty(email) &&
					(StringUtils.isEmpty(password) || password.equals(Preferences.getPassword(chanName))) &&
					!optionSage && !optionOriginalPoster && StringUtils.isEmpty(userIcon);
		}
		
		public JSONObject toJsonObject() throws JSONException
		{
			JSONObject jsonObject = new JSONObject();
			putJson(jsonObject, KEY_CHAN_NAME, chanName);
			putJson(jsonObject, KEY_BOARD_NAME, boardName);
			putJson(jsonObject, KEY_THREAD_NUMBER, threadNumber);
			putJson(jsonObject, KEY_NAME, name);
			putJson(jsonObject, KEY_EMAIL, email);
			putJson(jsonObject, KEY_PASSWORD, password);
			putJson(jsonObject, KEY_OPTION_SAGE, optionSage);
			putJson(jsonObject, KEY_OPTION_ORIGINAL_POSTER, optionOriginalPoster);
			putJson(jsonObject, KEY_USER_ICON, userIcon);
			return jsonObject;
		}
		
		public static ThreadDraft fromJsonObject(JSONObject jsonObject)
		{
			String chanName = jsonObject.optString(KEY_CHAN_NAME, null);
			if (chanName == null) return null;
			return new ThreadDraft(chanName, jsonObject.optString(KEY_BOARD_NAME, null),
					jsonObject.optString(KEY_THREAD_NUMBER, null), jsonObject.optString(KEY_NAME, null),
					jsonObject.optString(KEY_EMAIL, null), jsonObject.optString(KEY_PASSWORD, null),
					jsonObject.optBoolean(KEY_OPTION_SAGE), jsonObject.optBoolean(KEY_OPTION_ORIGINAL_POSTER),
					jsonObject.optString(KEY_USER_ICON, null));
		}
	}
	
	public static class PostDraft extends ThreadDraft
	{
		private static final String KEY_SUBJECT = "subject";
		private static final String KEY_COMMENT = "comment";
		private static final String KEY_COMMENT_CARRIAGE = "commentCarriage";
		private static final String KEY_ATTACHMENT_DRAFTS = "attachmentDrafts";
		private static final String KEY_OPTION_SPOILER = "optionSpoiler";
		
		public final String subject;
		public final String comment;
		public final int commentCarriage;
		public final AttachmentDraft[] attachmentDrafts;
		public final boolean optionSpoiler;
		
		public boolean isEmpty()
		{
			return super.isEmpty() && StringUtils.isEmpty(subject) && StringUtils.isEmpty(comment) &&
					(attachmentDrafts == null || attachmentDrafts.length == 0) && !optionSpoiler;
		}
		
		public PostDraft(String chanName, String boardName, String threadNumber, String subject, String comment,
				int commentCarriage, AttachmentDraft[] attachmentDrafts, String name, String email, String password,
				boolean optionSage, boolean optionSpoiler, boolean optionOriginalPoster, String userIcon)
		{
			super(chanName, boardName, threadNumber, name, email, password, optionSage, optionOriginalPoster, userIcon);
			this.subject = subject;
			this.comment = comment;
			this.commentCarriage = commentCarriage;
			this.attachmentDrafts = attachmentDrafts;
			this.optionSpoiler = optionSpoiler;
		}
		
		@Override
		public JSONObject toJsonObject() throws JSONException
		{
			JSONObject jsonObject = super.toJsonObject();
			putJson(jsonObject, KEY_SUBJECT, subject);
			putJson(jsonObject, KEY_COMMENT, comment);
			putJson(jsonObject, KEY_COMMENT_CARRIAGE, commentCarriage);
			if (attachmentDrafts != null && attachmentDrafts.length > 0)
			{
				JSONArray jsonArray = new JSONArray();
				for (AttachmentDraft attachmentDraft : attachmentDrafts) jsonArray.put(attachmentDraft.toJsonObject());
				jsonObject.put(KEY_ATTACHMENT_DRAFTS, jsonArray);
			}
			putJson(jsonObject, KEY_OPTION_SPOILER, optionSpoiler);
			return jsonObject;
		}
		
		public static PostDraft fromJsonObject(JSONObject jsonObject)
		{
			ThreadDraft threadDraft = ThreadDraft.fromJsonObject(jsonObject);
			if (threadDraft == null) return null;
			AttachmentDraft[] attachmentDrafts = null;
			JSONArray jsonArray = jsonObject.optJSONArray(KEY_ATTACHMENT_DRAFTS);
			if (jsonArray != null && jsonArray.length() > 0)
			{
				ArrayList<AttachmentDraft> attachmentDraftList = new ArrayList<>();
				for (int i = 0; i < jsonArray.length(); i++)
				{
					JSONObject draftObject = jsonArray.optJSONObject(i);
					if (draftObject != null)
					{
						AttachmentDraft attachmentDraft = AttachmentDraft.fromJsonObject(draftObject);
						if (attachmentDraft != null) attachmentDraftList.add(attachmentDraft);
					}
				}
				attachmentDrafts = CommonUtils.toArray(attachmentDraftList, AttachmentDraft.class);
			}
			return new PostDraft(threadDraft.chanName, threadDraft.boardName, threadDraft.threadNumber,
					jsonObject.optString(KEY_SUBJECT, null), jsonObject.optString(KEY_COMMENT, null),
					jsonObject.optInt(KEY_COMMENT_CARRIAGE), attachmentDrafts, threadDraft.name, threadDraft.email,
					threadDraft.password, threadDraft.optionSage, jsonObject.optBoolean(KEY_OPTION_SPOILER),
					threadDraft.optionOriginalPoster, threadDraft.userIcon);
		}
	}
	
	public static class CaptchaDraft implements Parcelable
	{
		public final String captchaType;
		public final ChanPerformer.CaptchaState captchaState;
		public final ChanPerformer.CaptchaData captchaData;
		public final String loadedCaptchaType;
		public final ChanConfiguration.Captcha.Input loadedInput;
		public final ChanConfiguration.Captcha.Validity loadedValidity;
		public final String text;
		public final Bitmap image;
		public final boolean large;
		public final boolean blackAndWhite;
		public final long loadTime;

		public final String boardName;
		public final String threadNumber;
		
		public CaptchaDraft(String captchaType, ChanPerformer.CaptchaState captchaState, ChanPerformer.CaptchaData captchaData,
				String loadedCaptchaType, ChanConfiguration.Captcha.Input loadedInput,
				ChanConfiguration.Captcha.Validity loadedValidity, String text, Bitmap image, boolean large,
				boolean blackAndWhite, long loadTime, String boardName, String threadNumber)
		{
			this.captchaType = captchaType;
			this.captchaState = captchaState;
			this.captchaData = captchaData;
			this.loadedCaptchaType = loadedCaptchaType;
			this.loadedInput = loadedInput;
			this.loadedValidity = loadedValidity;
			this.text = text;
			this.image = image;
			this.large = large;
			this.blackAndWhite = blackAndWhite;
			this.loadTime = loadTime;
			this.boardName = boardName;
			this.threadNumber = threadNumber;
		}
		
		@Override
		public int describeContents()
		{
			return 0;
		}
		
		@Override
		public void writeToParcel(Parcel dest, int flags)
		{
			dest.writeString(captchaType);
			dest.writeSerializable(captchaState);
			dest.writeSerializable(captchaData);
			dest.writeString(loadedCaptchaType);
			dest.writeSerializable(loadedInput);
			dest.writeSerializable(loadedValidity);
			dest.writeString(text);
			dest.writeParcelable(image, 0);
			dest.writeInt(large ? 1 : 0);
			dest.writeInt(blackAndWhite ? 1 : 0);
			dest.writeLong(loadTime);
			dest.writeString(boardName);
			dest.writeString(threadNumber);
		}
		
		public static final Creator<CaptchaDraft> CREATOR = new Creator<CaptchaDraft>()
		{
			@Override
			public CaptchaDraft createFromParcel(Parcel source)
			{
				String captchaType = source.readString();
				ChanPerformer.CaptchaState captchaState = (ChanPerformer.CaptchaState) source.readSerializable();
				ChanPerformer.CaptchaData captchaData = (ChanPerformer.CaptchaData) source.readSerializable();
				String loadedCaptchaType = source.readString();
				ChanConfiguration.Captcha.Input loadedInput = (ChanConfiguration.Captcha.Input)
						source.readSerializable();
				ChanConfiguration.Captcha.Validity loadedValidity = (ChanConfiguration.Captcha.Validity)
						source.readSerializable();
				String text = source.readString();
				Bitmap image = source.readParcelable(Bitmap.class.getClassLoader());
				boolean large = source.readInt() != 0;
				boolean blackAndWhite = source.readInt() != 0;
				long loadTime = source.readLong();
				String boardName = source.readString();
				String threadNumber = source.readString();
				return new CaptchaDraft(captchaType, captchaState, captchaData, loadedCaptchaType, loadedInput,
						loadedValidity, text, image, large, blackAndWhite, loadTime, boardName, threadNumber);
			}
			
			@Override
			public CaptchaDraft[] newArray(int size)
			{
				return new CaptchaDraft[size];
			}
		};
	}
	
	public static class AttachmentDraft
	{
		private static final String KEY_FILE_URI = "fileUri";
		private static final String KEY_RATING = "rating";
		private static final String KEY_OPTION_UNIQUE_HASH = "optionUniqueHash";
		private static final String KEY_OPTION_REMOVE_METADATA = "optionRemoveMetadata";
		private static final String KEY_OPTION_REMOVE_FILE_NAME = "optionRemoveFileName";
		private static final String KEY_OPTION_SPOILER = "optionSpoiler";
		private static final String KEY_REENCODING = "reencoding";

		private static final String KEY_REENCODING_FORMAT = "format";
		private static final String KEY_REENCODING_QUALITY = "quality";
		private static final String KEY_REENCODING_REDUCE = "reduce";
		
		public final FileHolder fileHolder;
		public final String rating;
		public final boolean optionUniqueHash;
		public final boolean optionRemoveMetadata;
		public final boolean optionRemoveFileName;
		public final boolean optionSpoiler;
		public final GraphicsUtils.Reencoding reencoding;
		
		public AttachmentDraft(FileHolder fileHolder, String rating, boolean optionUniqueHash,
				boolean optionRemoveMetadata, boolean optionRemoveFileName, boolean optionSpoiler,
				GraphicsUtils.Reencoding reencoding)
		{
			this.fileHolder = fileHolder;
			this.rating = rating;
			this.optionUniqueHash = optionUniqueHash;
			this.optionRemoveMetadata = optionRemoveMetadata;
			this.optionRemoveFileName = optionRemoveFileName;
			this.optionSpoiler = optionSpoiler;
			this.reencoding = reencoding;
		}
		
		public JSONObject toJsonObject() throws JSONException
		{
			JSONObject jsonObject = new JSONObject();
			putJson(jsonObject, KEY_FILE_URI, fileHolder.toUri().toString());
			putJson(jsonObject, KEY_RATING, rating);
			putJson(jsonObject, KEY_OPTION_UNIQUE_HASH, optionUniqueHash);
			putJson(jsonObject, KEY_OPTION_REMOVE_METADATA, optionRemoveMetadata);
			putJson(jsonObject, KEY_OPTION_REMOVE_FILE_NAME, optionRemoveFileName);
			putJson(jsonObject, KEY_OPTION_SPOILER, optionSpoiler);
			if (reencoding != null)
			{
				JSONObject reencodingObject = new JSONObject();
				putJson(reencodingObject, KEY_REENCODING_FORMAT, reencoding.format);
				putJson(reencodingObject, KEY_REENCODING_QUALITY, reencoding.quality);
				putJson(reencodingObject, KEY_REENCODING_REDUCE, reencoding.reduce);
				jsonObject.put(KEY_REENCODING, reencodingObject);
			}
			return jsonObject;
		}
		
		public static AttachmentDraft fromJsonObject(JSONObject jsonObject)
		{
			String uriString = jsonObject.optString(KEY_FILE_URI, null);
			if (uriString == null) return null;
			Uri uri = Uri.parse(uriString);
			if (uri == null) return null;
			FileHolder fileHolder = FileHolder.obtain(MainApplication.getInstance(), uri);
			if (fileHolder == null) return null;
			JSONObject reencodingObject = jsonObject.optJSONObject(KEY_REENCODING);
			GraphicsUtils.Reencoding reencoding = null;
			if (reencodingObject != null)
			{
				reencoding = new GraphicsUtils.Reencoding(reencodingObject.optString(KEY_REENCODING_FORMAT),
						reencodingObject.optInt(KEY_REENCODING_QUALITY),
						reencodingObject.optInt(KEY_REENCODING_REDUCE));
			}
			return new AttachmentDraft(fileHolder, jsonObject.optString(KEY_RATING, null),
					jsonObject.optBoolean(KEY_OPTION_UNIQUE_HASH), jsonObject.optBoolean(KEY_OPTION_REMOVE_METADATA),
					jsonObject.optBoolean(KEY_OPTION_REMOVE_FILE_NAME), jsonObject.optBoolean(KEY_OPTION_SPOILER),
					reencoding);
		}
	}
}