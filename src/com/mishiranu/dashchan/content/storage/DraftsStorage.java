package com.mishiranu.dashchan.content.storage;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import chan.content.ChanConfiguration;
import chan.content.ChanPerformer;
import chan.util.StringUtils;
import com.mishiranu.dashchan.content.MainApplication;
import com.mishiranu.dashchan.content.model.FileHolder;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.Hasher;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.LruCache;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class DraftsStorage extends StorageManager.Storage<List<DraftsStorage.PostDraft>> {
	private static final String KEY_POST_DRAFTS = "postDrafts";
	private static final String KEY_FUTURE_ATTACHMENT_DRAFTS = "futureAttachmentDrafts";

	private static final DraftsStorage INSTANCE = new DraftsStorage();

	public static DraftsStorage getInstance() {
		return INSTANCE;
	}

	private final LruCache<String, PostDraft> postDrafts = new LruCache<>(5, (k, v) -> handleRemovePostDraft(v));

	private String captchaChanName;
	private CaptchaDraft captchaDraft;
	private final ArrayList<AttachmentDraft> futureAttachmentDrafts = new ArrayList<>();

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
			JSONArray futureAttachmentsArray = jsonObject.optJSONArray(KEY_FUTURE_ATTACHMENT_DRAFTS);
			if (futureAttachmentsArray != null && futureAttachmentsArray.length() > 0) {
				try {
					for (int i = 0; i < futureAttachmentsArray.length(); i++) {
						AttachmentDraft attachmentDraft = AttachmentDraft.fromJsonObject(futureAttachmentsArray
								.getJSONObject(i));
						if (attachmentDraft != null) {
							futureAttachmentDrafts.add(attachmentDraft);
						}
					}
				} catch (JSONException e) {
					// Invalid data, ignore exception
				}
			}
		}
		File directory = getAttachmentDraftsDirectory();
		if (directory != null) {
			File[] files = directory.listFiles();
			if (files != null && files.length > 0) {
				HashSet<String> hashes = collectAttachmentDraftHashes();
				for (File file : files) {
					if (!hashes.contains(file.getName())) {
						file.delete();
					}
				}
			}
		}
	}

	@Override
	public List<PostDraft> onClone() {
		return new ArrayList<>(postDrafts.values());
	}

	@Override
	public JSONObject onSerialize(List<PostDraft> postDrafts) throws JSONException {
		JSONObject jsonObject = new JSONObject();
		if (postDrafts.size() > 0) {
			JSONArray jsonArray = new JSONArray();
			for (PostDraft postDraft : postDrafts) {
				jsonArray.put(postDraft.toJsonObject());
			}
			jsonObject.put(KEY_POST_DRAFTS, jsonArray);
		}
		if (futureAttachmentDrafts.size() > 0) {
			JSONArray jsonArray = new JSONArray();
			for (AttachmentDraft attachmentDraft : futureAttachmentDrafts) {
				jsonArray.put(attachmentDraft.toJsonObject());
			}
			jsonObject.put(KEY_FUTURE_ATTACHMENT_DRAFTS, jsonArray);
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

	public void store(String chanName, CaptchaDraft captchaDraft) {
		this.captchaChanName = chanName;
		this.captchaDraft = captchaDraft;
	}

	public CaptchaDraft getCaptchaDraft(String chanName) {
		if (captchaDraft != null && captchaChanName.equals(chanName) && SystemClock.elapsedRealtime() -
				captchaDraft.loadTime <= 5 * 60 * 1000) {
			return captchaDraft;
		}
		return null;
	}

	public void removeCaptchaDraft() {
		captchaDraft = null;
		captchaChanName = null;
	}

	private static File getAttachmentDraftsDirectory() {
		File directory = MainApplication.getInstance().getExternalCacheDir();
		if (directory != null) {
			directory = new File(directory, "attachments");
			if (directory.isDirectory() || directory.mkdirs()) {
				return directory;
			}
		}
		return null;
	}

	private static File getAttachmentDraftFile(String hash) {
		File directory = getAttachmentDraftsDirectory();
		return directory != null ? new File(directory, hash) : null;
	}

	public FileHolder getAttachmentDraftFileHolder(String hash) {
		File file = getAttachmentDraftFile(hash);
		return file != null && file.isFile() ? FileHolder.obtain(file) : null;
	}

	public String store(FileHolder fileHolder) {
		String hash;
		try (InputStream input = fileHolder.openInputStream()) {
			hash = StringUtils.formatHex(Hasher.getInstanceSha256().calculate(input));
		} catch (IOException e) {
			return null;
		}
		File file = getAttachmentDraftFile(hash);
		if (file == null) {
			return null;
		}
		if (file.isFile()) {
			return hash;
		}
		try (InputStream input = fileHolder.openInputStream();
				FileOutputStream output = new FileOutputStream(file)) {
			IOUtils.copyStream(input, output);
			serialize();
			return hash;
		} catch (IOException e) {
			file.delete();
			return null;
		}
	}

	private HashSet<String> collectAttachmentDraftHashes() {
		HashSet<String> hashes = new HashSet<>();
		for (PostDraft postDraft : postDrafts.values()) {
			if (postDraft.attachmentDrafts != null) {
				for (AttachmentDraft attachmentDraft : postDraft.attachmentDrafts) {
					hashes.add(attachmentDraft.hash);
				}
			}
		}
		for (AttachmentDraft attachmentDraft : futureAttachmentDrafts) {
			hashes.add(attachmentDraft.hash);
		}
		return hashes;
	}

	public boolean storeFuture(FileHolder fileHolder) {
		String hash = store(fileHolder);
		if (hash != null) {
			AttachmentDraft attachmentDraft = new AttachmentDraft(hash, fileHolder.getName(),
					null, false, false, false, false, null);
			futureAttachmentDrafts.add(attachmentDraft);
			serialize();
			return true;
		}
		return false;
	}

	public ArrayList<AttachmentDraft> getFutureAttachmentDrafts() {
		return futureAttachmentDrafts;
	}

	public void consumeFutureAttachmentDrafts() {
		if (!futureAttachmentDrafts.isEmpty()) {
			ArrayList<AttachmentDraft> attachmentDrafts = new ArrayList<>(futureAttachmentDrafts.size());
			attachmentDrafts.addAll(futureAttachmentDrafts);
			futureAttachmentDrafts.clear();
			handleRemoveAttachmentDrafts(attachmentDrafts);
			serialize();
		}
	}

	private void handleRemovePostDraft(PostDraft postDraft) {
		if (postDraft.attachmentDrafts != null) {
			handleRemoveAttachmentDrafts(postDraft.attachmentDrafts);
		}
	}

	private void handleRemoveAttachmentDrafts(ArrayList<AttachmentDraft> attachmentDrafts) {
		HashSet<String> hashes = collectAttachmentDraftHashes();
		for (AttachmentDraft attachmentDraft : attachmentDrafts) {
			if (!hashes.contains(attachmentDraft.hash)) {
				File file = getAttachmentDraftFile(attachmentDraft.hash);
				if (file != null) {
					file.delete();
				}
			}
		}
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
		public final ArrayList<AttachmentDraft> attachmentDrafts;
		public final boolean optionSage;
		public final boolean optionSpoiler;
		public final boolean optionOriginalPoster;
		public final String userIcon;

		public PostDraft(String chanName, String boardName, String threadNumber,
				String name, String email, String password, String subject, String comment, int commentCarriage,
				ArrayList<AttachmentDraft> attachmentDrafts, boolean optionSage, boolean optionSpoiler,
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
					(attachmentDrafts == null || attachmentDrafts.isEmpty()) &&
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
			if (attachmentDrafts != null && !attachmentDrafts.isEmpty()) {
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
			ArrayList<AttachmentDraft> attachmentDrafts = null;
			JSONArray jsonArray = jsonObject.optJSONArray(KEY_ATTACHMENT_DRAFTS);
			if (jsonArray != null && jsonArray.length() > 0) {
				for (int i = 0; i < jsonArray.length(); i++) {
					JSONObject draftObject = jsonArray.optJSONObject(i);
					if (draftObject != null) {
						AttachmentDraft attachmentDraft = AttachmentDraft.fromJsonObject(draftObject);
						if (attachmentDraft != null) {
							if (attachmentDrafts == null) {
								attachmentDrafts = new ArrayList<>();
							}
							attachmentDrafts.add(attachmentDraft);
						}
					}
				}
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
			dest.writeString(captchaState != null ? captchaState.name() : null);
			dest.writeByte((byte) (captchaData != null ? 1 : 0));
			if (captchaData != null) {
				captchaData.writeToParcel(dest, flags);
			}
			dest.writeString(loadedCaptchaType);
			dest.writeString(loadedInput != null ? loadedInput.name() : null);
			dest.writeString(loadedValidity != null ? loadedValidity.name() : null);
			dest.writeString(text);
			byte[] imageBytes = null;
			if (image != null && !image.isRecycled()) {
				// Avoid direct writing Bitmaps to Parcel to allow Parcel.marshall
				ByteArrayOutputStream output = new ByteArrayOutputStream();
				image.compress(Bitmap.CompressFormat.PNG, 100, output);
				imageBytes = output.size() > 1000000 ? null : output.toByteArray();
			}
			dest.writeByteArray(imageBytes);
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
				String captchaStateString = source.readString();
				ChanPerformer.CaptchaState captchaState = captchaStateString != null
						? ChanPerformer.CaptchaState.valueOf(captchaStateString) : null;
				ChanPerformer.CaptchaData captchaData = source.readByte() != 0
						? ChanPerformer.CaptchaData.CREATOR.createFromParcel(source) : null;
				String loadedCaptchaType = source.readString();
				String loadedInputString = source.readString();
				ChanConfiguration.Captcha.Input loadedInput = loadedInputString != null
						? ChanConfiguration.Captcha.Input.valueOf(loadedInputString) : null;
				String loadedValidityString = source.readString();
				ChanConfiguration.Captcha.Validity loadedValidity = loadedValidityString != null
						? ChanConfiguration.Captcha.Validity.valueOf(loadedValidityString) : null;
				String text = source.readString();
				byte[] imageBytes = source.createByteArray();
				Bitmap image = imageBytes != null ? BitmapFactory
						.decodeByteArray(imageBytes, 0, imageBytes.length) : null;
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
		private static final String KEY_HASH = "hash";
		private static final String KEY_NAME = "name";
		private static final String KEY_RATING = "rating";
		private static final String KEY_OPTION_UNIQUE_HASH = "optionUniqueHash";
		private static final String KEY_OPTION_REMOVE_METADATA = "optionRemoveMetadata";
		private static final String KEY_OPTION_REMOVE_FILE_NAME = "optionRemoveFileName";
		private static final String KEY_OPTION_SPOILER = "optionSpoiler";
		private static final String KEY_REENCODING = "reencoding";

		private static final String KEY_REENCODING_FORMAT = "format";
		private static final String KEY_REENCODING_QUALITY = "quality";
		private static final String KEY_REENCODING_REDUCE = "reduce";

		public final String hash;
		public final String name;
		public final String rating;
		public final boolean optionUniqueHash;
		public final boolean optionRemoveMetadata;
		public final boolean optionRemoveFileName;
		public final boolean optionSpoiler;
		public final GraphicsUtils.Reencoding reencoding;

		public AttachmentDraft(String hash, String name, String rating, boolean optionUniqueHash,
				boolean optionRemoveMetadata, boolean optionRemoveFileName, boolean optionSpoiler,
				GraphicsUtils.Reencoding reencoding) {
			this.hash = hash;
			this.name = name;
			this.rating = rating;
			this.optionUniqueHash = optionUniqueHash;
			this.optionRemoveMetadata = optionRemoveMetadata;
			this.optionRemoveFileName = optionRemoveFileName;
			this.optionSpoiler = optionSpoiler;
			this.reencoding = reencoding;
		}

		public JSONObject toJsonObject() throws JSONException {
			JSONObject jsonObject = new JSONObject();
			putJson(jsonObject, KEY_HASH, hash);
			putJson(jsonObject, KEY_NAME, name);
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
			String hash = jsonObject.optString(KEY_HASH, null);
			if (StringUtils.isEmpty(hash)) {
				return null;
			}
			JSONObject reencodingObject = jsonObject.optJSONObject(KEY_REENCODING);
			GraphicsUtils.Reencoding reencoding = null;
			if (reencodingObject != null) {
				reencoding = new GraphicsUtils.Reencoding(reencodingObject.optString(KEY_REENCODING_FORMAT),
						reencodingObject.optInt(KEY_REENCODING_QUALITY),
						reencodingObject.optInt(KEY_REENCODING_REDUCE));
			}
			return new AttachmentDraft(hash, jsonObject.optString(KEY_NAME, null),
					jsonObject.optString(KEY_RATING, null), jsonObject.optBoolean(KEY_OPTION_UNIQUE_HASH),
					jsonObject.optBoolean(KEY_OPTION_REMOVE_METADATA),
					jsonObject.optBoolean(KEY_OPTION_REMOVE_FILE_NAME), jsonObject.optBoolean(KEY_OPTION_SPOILER),
					reencoding);
		}
	}
}
