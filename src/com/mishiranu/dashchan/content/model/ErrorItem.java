package com.mishiranu.dashchan.content.model;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import chan.content.ApiException;
import chan.util.StringUtils;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.MainApplication;

public final class ErrorItem implements Parcelable {
	public enum Type {
		UNKNOWN,
		API,
		SSL,
		DOWNLOAD,
		READ_TIMEOUT,
		CONNECT_TIMEOUT,
		CONNECTION_RESET,
		INVALID_CERTIFICATE,
		UNSAFE_REDIRECT,
		UNSUPPORTED_SCHEME,
		CAPTCHA_EXPIRED,
		EMPTY_RESPONSE,
		INVALID_RESPONSE,
		INVALID_DATA_FORMAT,
		BOARD_NOT_EXISTS,
		THREAD_NOT_EXISTS,
		POST_NOT_FOUND,
		NO_ACCESS_TO_MEMORY,
		INSUFFICIENT_SPACE,
		EXTENSION,
		RELAY_BLOCK,
		UNSUPPORTED_SERVICE,
		INVALID_AUTHORIZATION_DATA,
		UNSUPPORTED_RECAPTCHA
	}

	public final Type type;
	public final int specialType;

	public final int httpResponseCode;
	public final String message;

	public interface Holder {
		ErrorItem getErrorItemAndHandle();
	}

	private ErrorItem(Type type, int specialType, int httpResponseCode, String message) {
		this.type = type;
		this.specialType = specialType;
		this.httpResponseCode = httpResponseCode;
		this.message = message;
	}

	public ErrorItem(Type type, int specialType) {
		this(type, specialType, 0, null);
	}

	public ErrorItem(Type type) {
		this(type, 0);
	}

	public ErrorItem(int httpResponseCode, String message) {
		this(null, 0, httpResponseCode, StringUtils.removeSingleDot(message));
	}

	@NonNull
	@Override
	public String toString() {
		if (!StringUtils.isEmpty(message)) {
			return httpResponseCode != 0 ? "HTTP " + httpResponseCode + ": " + message : message;
		}
		int resId = 0;
		switch (type != null ? type : Type.UNKNOWN) {
			case API: {
				resId = ApiException.getResId(specialType);
				break;
			}
			case SSL: {
				resId = R.string.ssl_https_error;
				break;
			}
			case DOWNLOAD: {
				resId = R.string.unable_to_download_data;
				break;
			}
			case READ_TIMEOUT: {
				resId = R.string.read_timeout_expired;
				break;
			}
			case CONNECT_TIMEOUT: {
				resId = R.string.connect_timeout_expired;
				break;
			}
			case CONNECTION_RESET: {
				resId = R.string.connection_was_reset;
				break;
			}
			case INVALID_CERTIFICATE: {
				resId = R.string.invalid_certificate;
				break;
			}
			case UNSAFE_REDIRECT: {
				resId = R.string.unsafe_redirect;
				break;
			}
			case UNSUPPORTED_SCHEME: {
				resId = R.string.scheme_is_not_supported;
				break;
			}
			case CAPTCHA_EXPIRED: {
				resId = R.string.captcha_expired;
				break;
			}
			case EMPTY_RESPONSE: {
				resId = R.string.empty_response;
				break;
			}
			case INVALID_RESPONSE: {
				resId = R.string.invalid_server_response;
				break;
			}
			case INVALID_DATA_FORMAT: {
				resId = R.string.invalid_data_format;
				break;
			}
			case BOARD_NOT_EXISTS: {
				resId = R.string.board_doesnt_exist;
				break;
			}
			case THREAD_NOT_EXISTS: {
				resId = R.string.thread_doesnt_exist;
				break;
			}
			case POST_NOT_FOUND: {
				resId = R.string.post_is_not_found;
				break;
			}
			case NO_ACCESS_TO_MEMORY: {
				resId = R.string.no_access_to_memory;
				break;
			}
			case INSUFFICIENT_SPACE: {
				resId = R.string.insufficient_device_space;
				break;
			}
			case EXTENSION: {
				resId = R.string.extension_error;
				break;
			}
			case RELAY_BLOCK: {
				resId = R.string.ddos_protection_bypass_failed;
				break;
			}
			case UNSUPPORTED_SERVICE: {
				resId = R.string.unsupported_service;
				break;
			}
			case INVALID_AUTHORIZATION_DATA: {
				resId = R.string.invalid_authorization_data;
				break;
			}
			case UNSUPPORTED_RECAPTCHA: {
				resId = R.string.this_recaptcha_is_not_supported;
				break;
			}
		}
		if (resId == 0) {
			resId = R.string.unknown_error;
		}
		return MainApplication.getInstance().getLocalizedContext().getString(resId);
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(type != null ? type.name() : null);
		dest.writeInt(specialType);
		dest.writeInt(httpResponseCode);
		dest.writeString(message);
	}

	public static final Creator<ErrorItem> CREATOR = new Creator<ErrorItem>() {
		@Override
		public ErrorItem createFromParcel(Parcel in) {
			String typeString = in.readString();
			Type type = typeString != null ? Type.valueOf(typeString) : null;
			int specialType = in.readInt();
			int httpResponseCode = in.readInt();
			String message = in.readString();
			return new ErrorItem(type, specialType, httpResponseCode, message);
		}

		@Override
		public ErrorItem[] newArray(int size) {
			return new ErrorItem[size];
		}
	};
}
