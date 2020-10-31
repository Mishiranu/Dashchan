package com.mishiranu.dashchan.content.storage;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.util.Pair;
import chan.content.ChanConfiguration;
import chan.content.ChanPerformer;
import chan.text.JsonSerial;
import chan.text.ParseException;
import chan.util.StringUtils;
import com.mishiranu.dashchan.content.MainApplication;
import com.mishiranu.dashchan.content.async.ReadCaptchaTask;
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
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class DraftsStorage extends StorageManager.Storage<Pair<List<DraftsStorage.PostDraft>,
		List<DraftsStorage.AttachmentDraft>>> {
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
		startRead();
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
	public Pair<List<PostDraft>, List<AttachmentDraft>> onClone() {
		return new Pair<>(new ArrayList<>(postDrafts.values()), new ArrayList<>(futureAttachmentDrafts));
	}

	@Override
	public void onRead(InputStream input) throws IOException {
		try {
			JsonSerial.Reader reader = JsonSerial.reader(input);
			reader.startObject();
			while (!reader.endStruct()) {
				switch (reader.nextName()) {
					case KEY_POST_DRAFTS: {
						reader.startArray();
						while (!reader.endStruct()) {
							PostDraft postDraft = PostDraft.deserialize(reader);
							if (postDraft != null) {
								postDrafts.put(makeKey(postDraft), postDraft);
							}
						}
						break;
					}
					case KEY_FUTURE_ATTACHMENT_DRAFTS: {
						reader.startArray();
						while (!reader.endStruct()) {
							AttachmentDraft attachmentDraft = AttachmentDraft.deserialize(reader);
							if (attachmentDraft != null) {
								futureAttachmentDrafts.add(attachmentDraft);
							}
						}
						break;
					}
					default: {
						reader.skip();
						break;
					}
				}
			}
		} catch (ParseException e) {
			throw new IOException(e);
		}
	}

	@Override
	public void onWrite(Pair<List<PostDraft>, List<AttachmentDraft>> pair, OutputStream output) throws IOException {
		JsonSerial.Writer writer = JsonSerial.writer(output);
		writer.startObject();
		if (pair.first.size() > 0) {
			writer.name(KEY_POST_DRAFTS);
			writer.startArray();
			for (PostDraft postDraft : pair.first) {
				postDraft.serialize(writer);
			}
			writer.endArray();
		}
		if (pair.second.size() > 0) {
			writer.name(KEY_FUTURE_ATTACHMENT_DRAFTS);
			writer.startArray();
			for (AttachmentDraft attachmentDraft : pair.second) {
				attachmentDraft.serialize(writer);
			}
			writer.endArray();
		}
		writer.endObject();
		writer.flush();
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

		public void serialize(JsonSerial.Writer writer) throws IOException {
			writer.startObject();
			if (!StringUtils.isEmpty(chanName)) {
				writer.name(KEY_CHAN_NAME);
				writer.value(chanName);
			}
			if (!StringUtils.isEmpty(boardName)) {
				writer.name(KEY_BOARD_NAME);
				writer.value(boardName);
			}
			if (!StringUtils.isEmpty(threadNumber)) {
				writer.name(KEY_THREAD_NUMBER);
				writer.value(threadNumber);
			}
			if (!StringUtils.isEmpty(name)) {
				writer.name(KEY_NAME);
				writer.value(name);
			}
			if (!StringUtils.isEmpty(email)) {
				writer.name(KEY_EMAIL);
				writer.value(email);
			}
			if (!StringUtils.isEmpty(password)) {
				writer.name(KEY_PASSWORD);
				writer.value(password);
			}
			if (!StringUtils.isEmpty(subject)) {
				writer.name(KEY_SUBJECT);
				writer.value(subject);
			}
			if (!StringUtils.isEmpty(comment)) {
				writer.name(KEY_COMMENT);
				writer.value(comment);
			}
			writer.name(KEY_COMMENT_CARRIAGE);
			writer.value(commentCarriage);
			if (attachmentDrafts != null && !attachmentDrafts.isEmpty()) {
				writer.name(KEY_ATTACHMENT_DRAFTS);
				writer.startArray();
				for (AttachmentDraft attachmentDraft : attachmentDrafts) {
					attachmentDraft.serialize(writer);
				}
				writer.endArray();
			}
			writer.name(KEY_OPTION_SAGE);
			writer.value(optionSage);
			writer.name(KEY_OPTION_SPOILER);
			writer.value(optionSpoiler);
			writer.name(KEY_OPTION_ORIGINAL_POSTER);
			writer.value(optionOriginalPoster);
			if (!StringUtils.isEmpty(userIcon)) {
				writer.name(KEY_USER_ICON);
				writer.value(userIcon);
			}
			writer.endObject();
		}

		public static PostDraft deserialize(JsonSerial.Reader reader) throws IOException, ParseException {
			String chanName = null;
			String boardName = null;
			String threadNumber = null;
			String name = null;
			String email = null;
			String password = null;
			String subject = null;
			String comment = null;
			int commentCarriage = 0;
			ArrayList<AttachmentDraft> attachmentDrafts = null;
			boolean optionSage = false;
			boolean optionSpoiler = false;
			boolean optionOriginalPoster = false;
			String userIcon = null;
			reader.startObject();
			while (!reader.endStruct()) {
				switch (reader.nextName()) {
					case KEY_CHAN_NAME: {
						chanName = reader.nextString();
						break;
					}
					case KEY_BOARD_NAME: {
						boardName = reader.nextString();
						break;
					}
					case KEY_THREAD_NUMBER: {
						threadNumber = reader.nextString();
						break;
					}
					case KEY_NAME: {
						name = reader.nextString();
						break;
					}
					case KEY_EMAIL: {
						email = reader.nextString();
						break;
					}
					case KEY_PASSWORD: {
						password = reader.nextString();
						break;
					}
					case KEY_SUBJECT: {
						subject = reader.nextString();
						break;
					}
					case KEY_COMMENT: {
						comment = reader.nextString();
						break;
					}
					case KEY_COMMENT_CARRIAGE: {
						commentCarriage = reader.nextInt();
						break;
					}
					case KEY_ATTACHMENT_DRAFTS: {
						reader.startArray();
						while (!reader.endStruct()) {
							AttachmentDraft attachmentDraft = AttachmentDraft.deserialize(reader);
							if (attachmentDraft != null) {
								if (attachmentDrafts == null) {
									attachmentDrafts = new ArrayList<>();
								}
								attachmentDrafts.add(attachmentDraft);
							}
						}
						break;
					}
					case KEY_OPTION_SAGE: {
						optionSage = reader.nextBoolean();
						break;
					}
					case KEY_OPTION_SPOILER: {
						optionSpoiler = reader.nextBoolean();
						break;
					}
					case KEY_OPTION_ORIGINAL_POSTER: {
						optionOriginalPoster = reader.nextBoolean();
						break;
					}
					case KEY_USER_ICON: {
						userIcon = reader.nextString();
						break;
					}
					default: {
						reader.skip();
						break;
					}
				}
			}
			return new PostDraft(chanName, boardName, threadNumber, name, email, password, subject, comment,
					commentCarriage, attachmentDrafts, optionSage, optionSpoiler, optionOriginalPoster, userIcon);
		}
	}

	public static class CaptchaDraft implements Parcelable {
		public final String captchaType;
		public final ReadCaptchaTask.CaptchaState captchaState;
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

		public CaptchaDraft(String captchaType, ReadCaptchaTask.CaptchaState captchaState,
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
				ReadCaptchaTask.CaptchaState captchaState = captchaStateString != null
						? ReadCaptchaTask.CaptchaState.valueOf(captchaStateString) : null;
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

		public void serialize(JsonSerial.Writer writer) throws IOException {
			writer.startObject();
			if (!StringUtils.isEmpty(hash)) {
				writer.name(KEY_HASH);
				writer.value(hash);
			}
			if (!StringUtils.isEmpty(name)) {
				writer.name(KEY_NAME);
				writer.value(name);
			}
			if (!StringUtils.isEmpty(rating)) {
				writer.name(KEY_RATING);
				writer.value(rating);
			}
			writer.name(KEY_OPTION_UNIQUE_HASH);
			writer.value(optionUniqueHash);
			writer.name(KEY_OPTION_REMOVE_METADATA);
			writer.value(optionRemoveMetadata);
			writer.name(KEY_OPTION_REMOVE_FILE_NAME);
			writer.value(optionRemoveFileName);
			writer.name(KEY_OPTION_SPOILER);
			writer.value(optionSpoiler);
			if (reencoding != null) {
				writer.name(KEY_REENCODING);
				writer.startObject();
				if (!StringUtils.isEmpty(reencoding.format)) {
					writer.name(KEY_REENCODING_FORMAT);
					writer.value(reencoding.format);
				}
				writer.name(KEY_REENCODING_QUALITY);
				writer.value(reencoding.quality);
				writer.name(KEY_REENCODING_REDUCE);
				writer.value(reencoding.reduce);
				writer.endObject();
			}
			writer.endObject();
		}

		public static AttachmentDraft deserialize(JsonSerial.Reader reader) throws IOException, ParseException {
			String hash = null;
			String name = null;
			String rating = null;
			boolean optionUniqueHash = false;
			boolean optionRemoveMetadata = false;
			boolean optionRemoveFileName = false;
			boolean optionSpoiler = false;
			GraphicsUtils.Reencoding reencoding = null;
			reader.startObject();
			while (!reader.endStruct()) {
				switch (reader.nextName()) {
					case KEY_HASH: {
						hash = reader.nextString();
						break;
					}
					case KEY_NAME: {
						name = reader.nextString();
						break;
					}
					case KEY_RATING: {
						rating = reader.nextString();
						break;
					}
					case KEY_OPTION_UNIQUE_HASH: {
						optionUniqueHash = reader.nextBoolean();
						break;
					}
					case KEY_OPTION_REMOVE_METADATA: {
						optionRemoveMetadata = reader.nextBoolean();
						break;
					}
					case KEY_OPTION_REMOVE_FILE_NAME: {
						optionRemoveFileName = reader.nextBoolean();
						break;
					}
					case KEY_OPTION_SPOILER: {
						optionSpoiler = reader.nextBoolean();
						break;
					}
					case KEY_REENCODING: {
						String format = null;
						int quality = 0;
						int reduce = 0;
						reader.startObject();
						while (!reader.endStruct()) {
							switch (reader.nextName()) {
								case KEY_REENCODING_FORMAT: {
									format = reader.nextString();
									break;
								}
								case KEY_REENCODING_QUALITY: {
									quality = reader.nextInt();
									break;
								}
								case KEY_REENCODING_REDUCE: {
									reduce = reader.nextInt();
									break;
								}
								default: {
									reader.skip();
									break;
								}
							}
						}
						reencoding = new GraphicsUtils.Reencoding(format, quality, reduce);
						break;
					}
					default: {
						reader.skip();
						break;
					}
				}
			}
			if (StringUtils.isEmpty(hash)) {
				return null;
			}
			return new AttachmentDraft(hash, name, rating, optionUniqueHash, optionRemoveMetadata,
					optionRemoveFileName, optionSpoiler, reencoding);
		}
	}
}
