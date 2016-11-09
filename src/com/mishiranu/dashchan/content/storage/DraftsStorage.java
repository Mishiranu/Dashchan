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

import com.mishiranu.dashchan.content.MainApplication;
import com.mishiranu.dashchan.content.model.FileHolder;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.LruCache;

public class DraftsStorage extends StorageManager.Storage {
	private static final String KEY_POST_DRAFTS = "postDrafts";

	private static final DraftsStorage INSTANCE = new DraftsStorage();

	public static DraftsStorage getInstance() {
		return INSTANCE;
	}

	private final LruCache<String, PostDraft> postDrafts = new LruCache<>(5);

	private String captchaChanName;
	private CaptchaDraft captchaDraft;

	private DraftsStorage() {
		super("drafts", 2000, 10000);
		JSONObject jsonObject = read();
		if (jsonObject != null) {
			JSONArray postsArray = jsonObject.optJSONArray(KEY_POST_DRAFTS);
			if (postsArray != null && postsArray.length() > 0) {
				try {
					for (int i = 0; i < postsArray.length(); i++) {
						PostDraft postDraft = PostDraft.fromJsonObject(postsArray.getJSONObject(i));
						if (postDraft != null) {
							postDrafts.put(makeKey(postDraft), postDraft);
						}
					}
				} catch (JSONException e) {
					// Invalid data, ignore exception
				}
			}
		}
	}

	@Override
	public Object onClone() {
		return new ArrayList<>(postDrafts.values());
	}

	@Override
	public JSONObject onSerialize(Object data) throws JSONException {
		@SuppressWarnings("unchecked")
		ArrayList<PostDraft> postDrafts = (ArrayList<PostDraft>) data;
		JSONObject jsonObject = new JSONObject();
		if (postDrafts.size() > 0) {
			JSONArray jsonArray = new JSONArray();
			for (PostDraft postDraft : postDrafts) {
				jsonArray.put(postDraft.toJsonObject());
			}
			jsonObject.put(KEY_POST_DRAFTS, jsonArray);
		}
		return jsonObject;
	}

	private static String makeKey(String chanName, String boardName, String threadNumber) {
		return chanName + "/" + boardName + "/" + threadNumber;
	}

	private static String makeKey(PostDraft postDraft) {
		return makeKey(postDraft.chanName, postDraft.boardName, postDraft.threadNumber);
	}

	public void store(PostDraft postDraft) {
		if (postDraft != null) {
			boolean serialize = true;
			if (postDraft.isEmpty()) {
				serialize = postDrafts.remove(makeKey(postDraft)) != null;
			} else {
				postDrafts.put(makeKey(postDraft), postDraft);
			}
			if (serialize) {
				serialize();
			}
		}
	}

	public PostDraft getPostDraft(String chanName, String boardName, String threadNumber) {
		return postDrafts.get(makeKey(chanName, boardName, threadNumber));
	}

	public void removePostDraft(String chanName, String boardName, String threadNumber) {
		PostDraft postDraft = postDrafts.remove(makeKey(chanName, boardName, threadNumber));
		if (postDraft != null) {
			serialize();
		}
	}

	public void movePostDraft(String chanName, String fromBoardName, String fromThreadNumber,
			String toBoardName, String toThreadNumber) {
		String fromKey = makeKey(chanName, fromBoardName, fromThreadNumber);
		String toKey = makeKey(chanName, toBoardName, toThreadNumber);
		PostDraft postDraft = postDrafts.get(fromKey);
		if (postDraft != null) {
			if (postDrafts.get(toKey) == null) {
				ArrayList<PostDraft> postDrafts = new ArrayList<>(this.postDrafts.values());
				int index = postDrafts.indexOf(postDraft);
				postDrafts.remove(index);
				postDraft = new PostDraft(chanName, toBoardName, toThreadNumber, postDraft.name, postDraft.email,
						postDraft.password, postDraft.subject, postDraft.comment, postDraft.commentCarriage,
						postDraft.attachmentDrafts, postDraft.optionSage, postDraft.optionSpoiler,
						postDraft.optionOriginalPoster, postDraft.userIcon);
				postDrafts.add(index, postDraft);
				this.postDrafts.clear();
				for (PostDraft addPostDraft : postDrafts) {
					this.postDrafts.put(makeKey(addPostDraft), addPostDraft);
				}
			} else {
				postDrafts.remove(fromKey);
			}
			serialize();
		}
	}

	public void store(String chanName, CaptchaDraft captchaDraft) {
		this.captchaChanName = chanName;
		this.captchaDraft = captchaDraft;
	}

	public CaptchaDraft getCaptchaDraft(String chanName) {
		if (captchaDraft != null && captchaChanName.equals(chanName) && System.currentTimeMillis() -
				captchaDraft.loadTime <= 5 * 60 * 1000) {
			return captchaDraft;
		}
		return null;
	}

	public void removeCaptchaDraft() {
		captchaDraft = null;
		captchaChanName = null;
	}

	public static class PostDraft {
		private static final String KEY_CHAN_NAME = "chanName";
		private static final String KEY_BOARD_NAME = "boardName";
		private static final String KEY_THREAD_NUMBER = "threadNumber";

		private static final String KEY_NAME = "name";
		private static final String KEY_EMAIL = "email";
		private static final String KEY_PASSWORD = "password";
		private static final String KEY_SUBJECT = "subject";
		private static final String KEY_COMMENT = "comment";
		private static final String KEY_COMMENT_CARRIAGE = "commentCarriage";
		private static final String KEY_ATTACHMENT_DRAFTS = "attachmentDrafts";
		private static final String KEY_OPTION_SAGE = "optionSage";
		private static final String KEY_OPTION_SPOILER = "optionSpoiler";
		private static final String KEY_OPTION_ORIGINAL_POSTER = "optionOriginalPoster";
		private static final String KEY_USER_ICON = "userIcon";

		public final String chanName;
		public final String boardName;
		public final String threadNumber;

		public final String name;
		public final String email;
		public final String password;
		public final String subject;
		public final String comment;
		public final int commentCarriage;
		public final AttachmentDraft[] attachmentDrafts;
		public final boolean optionSage;
		public final boolean optionSpoiler;
		public final boolean optionOriginalPoster;
		public final String userIcon;

		public PostDraft(String chanName, String boardName, String threadNumber,
				String name, String email, String password, String subject, String comment, int commentCarriage,
				AttachmentDraft[] attachmentDrafts, boolean optionSage, boolean optionSpoiler,
				boolean optionOriginalPoster, String userIcon) {
			this.chanName = chanName;
			this.boardName = boardName;
			this.threadNumber = threadNumber;
			this.name = name;
			this.email = email;
			this.password = password;
			this.optionSage = optionSage;
			this.optionOriginalPoster = optionOriginalPoster;
			this.userIcon = userIcon;
			this.subject = subject;
			this.comment = comment;
			this.commentCarriage = commentCarriage;
			this.attachmentDrafts = attachmentDrafts;
			this.optionSpoiler = optionSpoiler;
		}

		public PostDraft(String chanName, String boardName, String threadNumber,
				String name, String email, String password,
				boolean optionSage, boolean optionOriginalPoster, String userIcon) {
			this(chanName, boardName, threadNumber, name, email, password, null, null, 0,
					null, optionSage, false, optionOriginalPoster, userIcon);
		}

		public boolean isEmpty() {
			return StringUtils.isEmpty(name) && StringUtils.isEmpty(email) && StringUtils.isEmpty(password) &&
					StringUtils.isEmpty(subject) && StringUtils.isEmpty(comment) &&
					(attachmentDrafts == null || attachmentDrafts.length == 0) &&
					!optionSage && !optionSpoiler && !optionOriginalPoster && StringUtils.isEmpty(userIcon);
		}

		public JSONObject toJsonObject() throws JSONException {
			JSONObject jsonObject = new JSONObject();
			putJson(jsonObject, KEY_CHAN_NAME, chanName);
			putJson(jsonObject, KEY_BOARD_NAME, boardName);
			putJson(jsonObject, KEY_THREAD_NUMBER, threadNumber);
			putJson(jsonObject, KEY_NAME, name);
			putJson(jsonObject, KEY_EMAIL, email);
			putJson(jsonObject, KEY_PASSWORD, password);
			putJson(jsonObject, KEY_SUBJECT, subject);
			putJson(jsonObject, KEY_COMMENT, comment);
			putJson(jsonObject, KEY_COMMENT_CARRIAGE, commentCarriage);
			if (attachmentDrafts != null && attachmentDrafts.length > 0) {
				JSONArray jsonArray = new JSONArray();
				for (AttachmentDraft attachmentDraft : attachmentDrafts) {
					jsonArray.put(attachmentDraft.toJsonObject());
				}
				jsonObject.put(KEY_ATTACHMENT_DRAFTS, jsonArray);
			}
			putJson(jsonObject, KEY_OPTION_SAGE, optionSage);
			putJson(jsonObject, KEY_OPTION_SPOILER, optionSpoiler);
			putJson(jsonObject, KEY_OPTION_ORIGINAL_POSTER, optionOriginalPoster);
			putJson(jsonObject, KEY_USER_ICON, userIcon);
			return jsonObject;
		}

		public static PostDraft fromJsonObject(JSONObject jsonObject) {
			String chanName = jsonObject.optString(KEY_CHAN_NAME, null);
			String boardName = jsonObject.optString(KEY_BOARD_NAME, null);
			String threadNumber = jsonObject.optString(KEY_THREAD_NUMBER, null);
			String name = jsonObject.optString(KEY_NAME, null);
			String email = jsonObject.optString(KEY_EMAIL, null);
			String password = jsonObject.optString(KEY_PASSWORD, null);
			String subject = jsonObject.optString(KEY_SUBJECT, null);
			String comment = jsonObject.optString(KEY_COMMENT, null);
			int commentCarriage = jsonObject.optInt(KEY_COMMENT_CARRIAGE);
			AttachmentDraft[] attachmentDrafts = null;
			JSONArray jsonArray = jsonObject.optJSONArray(KEY_ATTACHMENT_DRAFTS);
			if (jsonArray != null && jsonArray.length() > 0) {
				ArrayList<AttachmentDraft> attachmentDraftList = new ArrayList<>();
				for (int i = 0; i < jsonArray.length(); i++) {
					JSONObject draftObject = jsonArray.optJSONObject(i);
					if (draftObject != null) {
						AttachmentDraft attachmentDraft = AttachmentDraft.fromJsonObject(draftObject);
						if (attachmentDraft != null) {
							attachmentDraftList.add(attachmentDraft);
						}
					}
				}
				attachmentDrafts = CommonUtils.toArray(attachmentDraftList, AttachmentDraft.class);
			}
			boolean optionSage = jsonObject.optBoolean(KEY_OPTION_SAGE);
			boolean optionSpoiler = jsonObject.optBoolean(KEY_OPTION_SPOILER);
			boolean optionOriginalPoster = jsonObject.optBoolean(KEY_OPTION_ORIGINAL_POSTER);
			String userIcon = jsonObject.optString(KEY_USER_ICON, null);
			return new PostDraft(chanName, boardName, threadNumber, name, email, password, subject, comment,
					commentCarriage, attachmentDrafts, optionSage, optionSpoiler, optionOriginalPoster, userIcon);
		}
	}

	public static class CaptchaDraft implements Parcelable {
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

		public CaptchaDraft(String captchaType, ChanPerformer.CaptchaState captchaState,
				ChanPerformer.CaptchaData captchaData, String loadedCaptchaType,
				ChanConfiguration.Captcha.Input loadedInput, ChanConfiguration.Captcha.Validity loadedValidity,
				String text, Bitmap image, boolean large, boolean blackAndWhite, long loadTime,
				String boardName, String threadNumber) {
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
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
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

		public static final Creator<CaptchaDraft> CREATOR = new Creator<CaptchaDraft>() {
			@Override
			public CaptchaDraft createFromParcel(Parcel source) {
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
			public CaptchaDraft[] newArray(int size) {
				return new CaptchaDraft[size];
			}
		};
	}

	public static class AttachmentDraft {
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
				GraphicsUtils.Reencoding reencoding) {
			this.fileHolder = fileHolder;
			this.rating = rating;
			this.optionUniqueHash = optionUniqueHash;
			this.optionRemoveMetadata = optionRemoveMetadata;
			this.optionRemoveFileName = optionRemoveFileName;
			this.optionSpoiler = optionSpoiler;
			this.reencoding = reencoding;
		}

		public JSONObject toJsonObject() throws JSONException {
			JSONObject jsonObject = new JSONObject();
			putJson(jsonObject, KEY_FILE_URI, fileHolder.toUri().toString());
			putJson(jsonObject, KEY_RATING, rating);
			putJson(jsonObject, KEY_OPTION_UNIQUE_HASH, optionUniqueHash);
			putJson(jsonObject, KEY_OPTION_REMOVE_METADATA, optionRemoveMetadata);
			putJson(jsonObject, KEY_OPTION_REMOVE_FILE_NAME, optionRemoveFileName);
			putJson(jsonObject, KEY_OPTION_SPOILER, optionSpoiler);
			if (reencoding != null) {
				JSONObject reencodingObject = new JSONObject();
				putJson(reencodingObject, KEY_REENCODING_FORMAT, reencoding.format);
				putJson(reencodingObject, KEY_REENCODING_QUALITY, reencoding.quality);
				putJson(reencodingObject, KEY_REENCODING_REDUCE, reencoding.reduce);
				jsonObject.put(KEY_REENCODING, reencodingObject);
			}
			return jsonObject;
		}

		public static AttachmentDraft fromJsonObject(JSONObject jsonObject) {
			String uriString = jsonObject.optString(KEY_FILE_URI, null);
			if (uriString == null) {
				return null;
			}
			Uri uri = Uri.parse(uriString);
			if (uri == null) {
				return null;
			}
			FileHolder fileHolder = FileHolder.obtain(MainApplication.getInstance(), uri);
			if (fileHolder == null) {
				return null;
			}
			JSONObject reencodingObject = jsonObject.optJSONObject(KEY_REENCODING);
			GraphicsUtils.Reencoding reencoding = null;
			if (reencodingObject != null) {
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