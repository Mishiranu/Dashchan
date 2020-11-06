package chan.content;

import android.os.Parcel;
import android.os.Parcelable;
import chan.annotation.Public;
import chan.util.StringUtils;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.util.FlagUtils;
import java.util.ArrayList;
import java.util.LinkedHashSet;

@Public
public final class ApiException extends Exception {
	@Public public static final int SEND_ERROR_NO_BOARD = 100;
	@Public public static final int SEND_ERROR_NO_THREAD = 101;
	@Public public static final int SEND_ERROR_NO_ACCESS = 102;
	@Public public static final int SEND_ERROR_CAPTCHA = 103;
	@Public public static final int SEND_ERROR_BANNED = 104;
	@Public public static final int SEND_ERROR_CLOSED = 105;
	@Public public static final int SEND_ERROR_TOO_FAST = 106;
	@Public public static final int SEND_ERROR_FIELD_TOO_LONG = 107;
	@Public public static final int SEND_ERROR_FILE_EXISTS = 108;
	@Public public static final int SEND_ERROR_FILE_NOT_SUPPORTED = 109;
	@Public public static final int SEND_ERROR_FILE_TOO_BIG = 110;
	@Public public static final int SEND_ERROR_FILES_TOO_MANY = 111;
	@Public public static final int SEND_ERROR_SPAM_LIST = 112;
	@Public public static final int SEND_ERROR_EMPTY_FILE = 113;
	@Public public static final int SEND_ERROR_EMPTY_SUBJECT = 114;
	@Public public static final int SEND_ERROR_EMPTY_COMMENT = 115;
	@Public public static final int SEND_ERROR_FILES_LIMIT = 116;

	@Public public static final int DELETE_ERROR_NO_ACCESS = 200;
	@Public public static final int DELETE_ERROR_PASSWORD = 201;
	@Public public static final int DELETE_ERROR_NOT_FOUND = 202;
	@Public public static final int DELETE_ERROR_TOO_NEW = 203;
	@Public public static final int DELETE_ERROR_TOO_OLD = 204;
	@Public public static final int DELETE_ERROR_TOO_OFTEN = 205;

	@Public public static final int REPORT_ERROR_NO_ACCESS = 300;
	@Public public static final int REPORT_ERROR_TOO_OFTEN = 301;
	@Public public static final int REPORT_ERROR_EMPTY_COMMENT = 302;
	@Public public static final int ARCHIVE_ERROR_NO_ACCESS = 400;
	@Public public static final int ARCHIVE_ERROR_TOO_OFTEN = 401;

	@Public public static final int FLAG_KEEP_CAPTCHA = 0x00000001;

	private final int errorType;
	private final int flags;
	private final Object extra;

	@Public
	public ApiException(int errorType) {
		this(errorType, 0, null);
	}

	@Public
	public ApiException(int errorType, int flags) {
		this(errorType, flags, null);
	}

	@Public
	public ApiException(int errorType, Object extra) {
		this(errorType, 0, extra);
	}

	@Public
	public ApiException(int errorType, int flags, Object extra) {
		this.errorType = errorType;
		this.flags = flags;
		this.extra = extra;
	}

	@Public
	public ApiException(String detailMessage) {
		this(detailMessage, 0);
	}

	@Public
	public ApiException(String detailMessage, int flags) {
		super(detailMessage);
		this.errorType = 0;
		this.flags = flags;
		this.extra = null;
	}

	public int getErrorType() {
		return errorType;
	}

	public boolean checkFlag(int flag) {
		return FlagUtils.get(flags, flag);
	}

	public Extra getExtra() {
		switch (errorType) {
			case ApiException.SEND_ERROR_BANNED: {
				if (extra instanceof BanExtra) {
					return (BanExtra) extra;
				}
				break;
			}
			case ApiException.SEND_ERROR_SPAM_LIST: {
				if (extra instanceof WordsExtra) {
					return (WordsExtra) extra;
				}
				break;
			}
		}
		return null;
	}

	@SuppressWarnings("DuplicateBranchesInSwitch")
	public static int getResId(int errorType) {
		int resId = 0;
		switch (errorType) {
			case ApiException.SEND_ERROR_NO_BOARD: {
				resId = R.string.board_doesnt_exist;
				break;
			}
			case ApiException.SEND_ERROR_NO_THREAD: {
				resId = R.string.thread_doesnt_exist;
				break;
			}
			case ApiException.SEND_ERROR_NO_ACCESS: {
				resId = R.string.no_access;
				break;
			}
			case ApiException.SEND_ERROR_CAPTCHA: {
				resId = R.string.captcha_is_not_valid;
				break;
			}
			case ApiException.SEND_ERROR_BANNED: {
				resId = R.string.you_are_banned;
				break;
			}
			case ApiException.SEND_ERROR_CLOSED: {
				resId = R.string.thread_is_closed;
				break;
			}
			case ApiException.SEND_ERROR_TOO_FAST: {
				resId = R.string.you_cant_send_too_often;
				break;
			}
			case ApiException.SEND_ERROR_FIELD_TOO_LONG: {
				resId = R.string.fields_limit_exceeded;
				break;
			}
			case ApiException.SEND_ERROR_FILE_EXISTS: {
				resId = R.string.repeated_files_are_prohibited;
				break;
			}
			case ApiException.SEND_ERROR_FILE_NOT_SUPPORTED: {
				resId = R.string.file_format_is_not_supported;
				break;
			}
			case ApiException.SEND_ERROR_FILE_TOO_BIG: {
				resId = R.string.attachments_are_too_large;
				break;
			}
			case ApiException.SEND_ERROR_FILES_TOO_MANY: {
				resId = R.string.too_many_attachments;
				break;
			}
			case ApiException.SEND_ERROR_SPAM_LIST: {
				resId = R.string.post_rejected;
				break;
			}
			case ApiException.SEND_ERROR_EMPTY_FILE: {
				resId = R.string.you_need_to_attach_a_file;
				break;
			}
			case ApiException.SEND_ERROR_EMPTY_SUBJECT: {
				resId = R.string.subject_is_too_short;
				break;
			}
			case ApiException.SEND_ERROR_EMPTY_COMMENT: {
				resId = R.string.comment_is_too_short;
				break;
			}
			case ApiException.SEND_ERROR_FILES_LIMIT: {
				resId = R.string.files_limit_reached;
				break;
			}
			case ApiException.DELETE_ERROR_NO_ACCESS: {
				resId = R.string.no_access;
				break;
			}
			case ApiException.DELETE_ERROR_PASSWORD: {
				resId = R.string.incorrect_password;
				break;
			}
			case ApiException.DELETE_ERROR_NOT_FOUND: {
				resId = R.string.not_found;
				break;
			}
			case ApiException.DELETE_ERROR_TOO_NEW: {
				resId = R.string.you_cant_delete_too_new_posts;
				break;
			}
			case ApiException.DELETE_ERROR_TOO_OLD: {
				resId = R.string.you_cant_delete_too_old_posts;
				break;
			}
			case ApiException.DELETE_ERROR_TOO_OFTEN: {
				resId = R.string.you_cant_send_too_often;
				break;
			}
			case ApiException.REPORT_ERROR_NO_ACCESS: {
				resId = R.string.no_access;
				break;
			}
			case ApiException.REPORT_ERROR_TOO_OFTEN: {
				resId = R.string.you_cant_send_too_often;
				break;
			}
			case ApiException.REPORT_ERROR_EMPTY_COMMENT: {
				resId = R.string.comment_is_too_short;
				break;
			}
			case ApiException.ARCHIVE_ERROR_NO_ACCESS: {
				resId = R.string.no_access;
				break;
			}
			case ApiException.ARCHIVE_ERROR_TOO_OFTEN: {
				resId = R.string.you_cant_send_too_often;
				break;
			}
		}
		return resId;
	}

	public ErrorItem getErrorItem() {
		String message = getMessage();
		if (!StringUtils.isEmpty(message)) {
			return new ErrorItem(message);
		}
		return new ErrorItem(ErrorItem.Type.API, errorType);
	}

	public interface Extra extends Parcelable {}

	@Public
	public static final class BanExtra implements Extra {
		public String id;
		public String message;
		public long startDate;
		public long expireDate;

		@Public
		public BanExtra() {}

		@Public
		public BanExtra setId(String id) {
			this.id = id;
			return this;
		}

		@Public
		public BanExtra setMessage(String message) {
			this.message = message;
			return this;
		}

		@Public
		public BanExtra setStartDate(long startDate) {
			this.startDate = startDate;
			return this;
		}

		@Public
		public BanExtra setExpireDate(long expireDate) {
			this.expireDate = expireDate;
			return this;
		}

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			dest.writeString(id);
			dest.writeString(message);
			dest.writeLong(startDate);
			dest.writeLong(expireDate);
		}

		public static final Creator<BanExtra> CREATOR = new Creator<BanExtra>() {
			@Override
			public BanExtra createFromParcel(Parcel in) {
				String id = in.readString();
				String message = in.readString();
				long startDate = in.readLong();
				long expireDate = in.readLong();
				return new BanExtra().setId(id).setMessage(message).setStartDate(startDate).setExpireDate(expireDate);
			}

			@Override
			public BanExtra[] newArray(int size) {
				return new BanExtra[size];
			}
		};
	}

	@Public
	public static final class WordsExtra implements Extra {
		public final LinkedHashSet<String> words = new LinkedHashSet<>();

		@Public
		public WordsExtra() {}

		@Public
		public WordsExtra addWord(String word) {
			words.add(word);
			return this;
		}

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			dest.writeStringList(new ArrayList<>(words));
		}

		public static final Creator<WordsExtra> CREATOR = new Creator<WordsExtra>() {
			@Override
			public WordsExtra createFromParcel(Parcel in) {
				ArrayList<String> words = in.createStringArrayList();
				WordsExtra wordsExtra = new WordsExtra();
				for (String word : words) {
					wordsExtra.addWord(word);
				}
				return wordsExtra;
			}

			@Override
			public WordsExtra[] newArray(int size) {
				return new WordsExtra[size];
			}
		};
	}
}
