package com.mishiranu.dashchan.content.storage;

import android.os.Parcel;
import android.os.Parcelable;
import chan.util.CommonUtils;
import chan.util.StringUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class AutohideStorage extends StorageManager.JsonOrgStorage<List<AutohideStorage.AutohideItem>> {
	private static final String KEY_DATA = "data";
	private static final String KEY_CHAN_NAMES = "chanNames";
	private static final String KEY_BOARD_NAME = "boardName";
	private static final String KEY_THREAD_NUMBER = "threadNumber";
	private static final String KEY_OPTION_ORIGINAL_POST = "optionOriginalPost";
	private static final String KEY_OPTION_SAGE = "optionSage";
	private static final String KEY_OPTION_SUBJECT = "optionSubject";
	private static final String KEY_OPTION_COMMENT = "optionComment";
	private static final String KEY_OPTION_NAME = "optionName";
	private static final String KEY_OPTION_FILE_NAME = "optionFileName";
	private static final String KEY_VALUE = "value";

	private static final AutohideStorage INSTANCE = new AutohideStorage();

	public static AutohideStorage getInstance() {
		return INSTANCE;
	}

	private final ArrayList<AutohideItem> autohideItems = new ArrayList<>();

	private AutohideStorage() {
		super("autohide", 1000, 10000);
		startRead();
	}

	public ArrayList<AutohideItem> getItems() {
		return autohideItems;
	}

	@Override
	public List<AutohideItem> onClone() {
		ArrayList<AutohideItem> autohideItems = new ArrayList<>(this.autohideItems.size());
		for (AutohideItem autohideItem : this.autohideItems) {
			autohideItems.add(new AutohideItem(autohideItem));
		}
		return autohideItems;
	}

	@Override
	public void onDeserialize(JSONObject jsonObject) {
		JSONArray jsonArray = jsonObject.optJSONArray(KEY_DATA);
		if (jsonArray != null) {
			for (int i = 0; i < jsonArray.length(); i++) {
				jsonObject = jsonArray.optJSONObject(i);
				if (jsonObject != null) {
					HashSet<String> chanNames = null;
					JSONArray chanNamesArray = jsonObject.optJSONArray(KEY_CHAN_NAMES);
					if (chanNamesArray != null) {
						for (int j = 0; j < chanNamesArray.length(); j++) {
							String chanName = chanNamesArray.optString(j, null);
							if (!StringUtils.isEmpty(chanName)) {
								if (chanNames == null) {
									chanNames = new HashSet<>();
								}
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
					boolean optionFileName = jsonObject.optBoolean(KEY_OPTION_FILE_NAME);
					String value = jsonObject.optString(KEY_VALUE, null);
					autohideItems.add(new AutohideItem(chanNames, boardName, threadNumber, optionOriginalPost,
							optionSage, optionSubject, optionComment, optionName, optionFileName, value));
				}
			}
		}
	}

	@Override
	public JSONObject onSerialize(List<AutohideItem> autohideItems) throws JSONException {
		if (autohideItems.size() > 0) {
			JSONArray jsonArray = new JSONArray();
			for (AutohideItem autohideItem : autohideItems) {
				JSONObject jsonObject = new JSONObject();
				if (autohideItem.chanNames != null && autohideItem.chanNames.size() > 0) {
					JSONArray chanNamesArray = new JSONArray();
					for (String chanName : autohideItem.chanNames) {
						chanNamesArray.put(chanName);
					}
					jsonObject.put(KEY_CHAN_NAMES, chanNamesArray);
				}
				putJson(jsonObject, KEY_BOARD_NAME, autohideItem.boardName);
				putJson(jsonObject, KEY_THREAD_NUMBER, autohideItem.threadNumber);
				putJson(jsonObject, KEY_OPTION_ORIGINAL_POST, autohideItem.optionOriginalPost);
				putJson(jsonObject, KEY_OPTION_SAGE, autohideItem.optionSage);
				putJson(jsonObject, KEY_OPTION_SUBJECT, autohideItem.optionSubject);
				putJson(jsonObject, KEY_OPTION_COMMENT, autohideItem.optionComment);
				putJson(jsonObject, KEY_OPTION_NAME, autohideItem.optionName);
				putJson(jsonObject, KEY_OPTION_FILE_NAME, autohideItem.optionFileName);
				putJson(jsonObject, KEY_VALUE, autohideItem.value);
				jsonArray.put(jsonObject);
			}
			JSONObject jsonObject = new JSONObject();
			jsonObject.put(KEY_DATA, jsonArray);
			return jsonObject;
		}
		return null;
	}

	public static void putJson(JSONObject jsonObject, String name, String value) throws JSONException {
		if (!StringUtils.isEmpty(value)) {
			jsonObject.put(name, value);
		}
	}

	public static void putJson(JSONObject jsonObject, String name, boolean value) throws JSONException {
		if (value) {
			jsonObject.put(name, true);
		}
	}

	public void add(AutohideItem autohideItem) {
		autohideItems.add(autohideItem);
		serialize();
	}

	public void update(int index, AutohideItem autohideItem) {
		autohideItems.set(index, autohideItem);
		serialize();
	}

	public void delete(int index) {
		autohideItems.remove(index);
		serialize();
	}

	public static class AutohideItem implements Parcelable {
		public HashSet<String> chanNames;

		public String boardName;
		public String threadNumber;

		public boolean optionOriginalPost;
		public boolean optionSage;

		public boolean optionSubject;
		public boolean optionComment;
		public boolean optionName;
		public boolean optionFileName;

		public String value;

		private volatile boolean ready = false;
		private Pattern pattern;

		public AutohideItem() {}

		@SuppressWarnings("CopyConstructorMissesField")
		public AutohideItem(AutohideItem autohideItem) {
			this(autohideItem.chanNames, autohideItem.boardName, autohideItem.threadNumber,
					autohideItem.optionOriginalPost, autohideItem.optionSage, autohideItem.optionSubject,
					autohideItem.optionComment, autohideItem.optionName, autohideItem.optionFileName,
					autohideItem.value);
		}

		public AutohideItem(HashSet<String> chanNames, String boardName, String threadNumber,
				boolean optionOriginalPost, boolean optionSage, boolean optionSubject, boolean optionComment,
				boolean optionName, boolean optionFileName, String value) {
			update(chanNames, boardName, threadNumber, optionOriginalPost, optionSage,
					optionSubject, optionComment, optionName, optionFileName, value);
		}

		public static Pattern makePattern(String value) {
			return Pattern.compile(value, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
		}

		public void update(HashSet<String> chanNames, String boardName, String threadNumber,
				boolean optionOriginalPost, boolean optionSage, boolean optionSubject,
				boolean optionComment, boolean optionName, boolean optionFileName, String value) {
			this.chanNames = chanNames;
			this.boardName = boardName;
			this.threadNumber = threadNumber;
			this.optionOriginalPost = optionOriginalPost;
			this.optionSage = optionSage;
			this.optionSubject = optionSubject;
			this.optionComment = optionComment;
			this.optionName = optionName;
			this.optionFileName = optionFileName;
			this.value = StringUtils.emptyIfNull(value);
		}

		public String find(String data) {
			if (!ready) {
				synchronized (this) {
					if (!ready) {
						try {
							pattern = makePattern(value);
						} catch (Exception e) {
							// Invalid pattern syntax, ignore exception
						}
						ready = true;
					}
				}
			}
			try {
				Matcher matcher = pattern.matcher(data);
				if (matcher.find()) {
					String result = matcher.group();
					if (StringUtils.isEmpty(result)) {
						result = value;
					}
					return result;
				}
			} catch (Exception e) {
				// Ignore matching exceptions
			}
			return null;
		}

		public enum ReasonSource {NAME, SUBJECT, COMMENT, FILE}

		public String getReason(ReasonSource reasonSource, String text, String findResult) {
			StringBuilder builder = new StringBuilder();
			if (optionSage) {
				builder.append("sage ");
			}
			if (optionSubject && reasonSource == ReasonSource.SUBJECT) {
				builder.append("subject ");
			}
			if (optionName && reasonSource == ReasonSource.NAME) {
				builder.append("name ");
			}
			if (optionFileName && reasonSource == ReasonSource.FILE) {
				builder.append("file ");
			}
			if (!StringUtils.isEmpty(findResult)) {
				builder.append(findResult);
			} else if (!StringUtils.isEmpty(text)) {
				builder.append(StringUtils.cutIfLongerToLine(text, 80, true));
			}
			return builder.toString();
		}

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			dest.writeStringArray(CommonUtils.toArray(chanNames, String.class));
			dest.writeString(boardName);
			dest.writeString(threadNumber);
			dest.writeByte((byte) (optionOriginalPost ? 1 : 0));
			dest.writeByte((byte) (optionSage ? 1 : 0));
			dest.writeByte((byte) (optionSubject ? 1 : 0));
			dest.writeByte((byte) (optionComment ? 1 : 0));
			dest.writeByte((byte) (optionName ? 1 : 0));
			dest.writeByte((byte) (optionFileName ? 1 : 0));
			dest.writeString(value);
		}

		public static final Creator<AutohideItem> CREATOR = new Creator<AutohideItem>() {
			@Override
			public AutohideItem createFromParcel(Parcel source) {
				AutohideItem autohideItem = new AutohideItem();
				String[] chanNames = source.createStringArray();
				if (chanNames != null) {
					autohideItem.chanNames = new HashSet<>();
					Collections.addAll(autohideItem.chanNames, chanNames);
				}
				autohideItem.boardName = source.readString();
				autohideItem.threadNumber = source.readString();
				autohideItem.optionOriginalPost = source.readByte() != 0;
				autohideItem.optionSage = source.readByte() != 0;
				autohideItem.optionSubject = source.readByte() != 0;
				autohideItem.optionComment = source.readByte() != 0;
				autohideItem.optionName = source.readByte() != 0;
				autohideItem.optionFileName = source.readByte() != 0;
				autohideItem.value = source.readString();
				return autohideItem;
			}

			@Override
			public AutohideItem[] newArray(int size) {
				return new AutohideItem[size];
			}
		};
	}
}
