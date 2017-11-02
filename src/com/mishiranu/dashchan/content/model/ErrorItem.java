/*
 * Copyright 2014-2017 Fukurou Mishiranu
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

package com.mishiranu.dashchan.content.model;

import java.io.Serializable;

import chan.content.ApiException;
import chan.util.StringUtils;

import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.MainApplication;

public class ErrorItem implements Serializable {
	private static final long serialVersionUID = 1L;

	public static final int TYPE_UNKNOWN = 0;
	public static final int TYPE_API = 1;
	public static final int TYPE_SSL = 2;
	public static final int TYPE_DOWNLOAD = 3;
	public static final int TYPE_READ_TIMEOUT = 4;
	public static final int TYPE_CONNECT_TIMEOUT = 5;
	public static final int TYPE_CONNECTION_RESET = 6;
	public static final int TYPE_INVALID_CERTIFICATE = 7;
	public static final int TYPE_UNSAFE_REDIRECT = 8;
	public static final int TYPE_UNSUPPORTED_SCHEME = 9;
	public static final int TYPE_CAPTCHA_EXPIRED = 10;
	public static final int TYPE_EMPTY_RESPONSE = 11;
	public static final int TYPE_INVALID_RESPONSE = 12;
	public static final int TYPE_INVALID_DATA_FORMAT = 13;
	public static final int TYPE_BOARD_NOT_EXISTS = 14;
	public static final int TYPE_THREAD_NOT_EXISTS = 15;
	public static final int TYPE_POST_NOT_FOUND = 16;
	public static final int TYPE_NO_ACCESS_TO_MEMORY = 17;
	public static final int TYPE_INSUFFICIENT_SPACE = 18;
	public static final int TYPE_EXTENSION = 19;
	public static final int TYPE_UNSUPPORTED_RECAPTCHA = 20;

	public final int type;
	public final int specialType;

	public final int httpResponseCode;
	public final String message;

	public interface Holder {
		public ErrorItem getErrorItemAndHandle();
	}

	public ErrorItem(int type, int specialType) {
		this.type = type;
		this.specialType = specialType;
		httpResponseCode = 0;
		message = null;
	}

	public ErrorItem(int httpResponseCode, String message) {
		type = 0;
		specialType = 0;
		this.httpResponseCode = httpResponseCode;
		this.message = StringUtils.removeSingleDot(message);
	}

	public ErrorItem(int type) {
		this(type, 0);
	}

	@Override
	public String toString() {
		if (!StringUtils.isEmpty(message)) {
			return httpResponseCode != 0 ? "HTTP " + httpResponseCode + ": " + message : message;
		}
		int resId = 0;
		switch (type) {
			case TYPE_API: {
				resId = ApiException.getResId(specialType);
				break;
			}
			case TYPE_SSL: {
				resId = R.string.message_ssl_error;
				break;
			}
			case TYPE_DOWNLOAD: {
				resId = R.string.message_download_error;
				break;
			}
			case TYPE_READ_TIMEOUT: {
				resId = R.string.message_read_timeout;
				break;
			}
			case TYPE_CONNECT_TIMEOUT: {
				resId = R.string.message_connect_timeout;
				break;
			}
			case TYPE_CONNECTION_RESET: {
				resId = R.string.message_connection_reset;
				break;
			}
			case TYPE_INVALID_CERTIFICATE: {
				resId = R.string.message_invalid_certificate;
				break;
			}
			case TYPE_UNSAFE_REDIRECT: {
				resId = R.string.message_unsafe_redirect;
				break;
			}
			case TYPE_UNSUPPORTED_SCHEME: {
				resId = R.string.message_unsupported_scheme;
				break;
			}
			case TYPE_CAPTCHA_EXPIRED: {
				resId = R.string.message_captcha_expired;
				break;
			}
			case TYPE_EMPTY_RESPONSE: {
				resId = R.string.message_empty_response;
				break;
			}
			case TYPE_INVALID_RESPONSE: {
				resId = R.string.message_invalid_response;
				break;
			}
			case TYPE_INVALID_DATA_FORMAT: {
				resId = R.string.message_invalid_data_format;
				break;
			}
			case TYPE_BOARD_NOT_EXISTS: {
				resId = R.string.message_board_not_exist;
				break;
			}
			case TYPE_THREAD_NOT_EXISTS: {
				resId = R.string.message_thread_not_exist;
				break;
			}
			case TYPE_POST_NOT_FOUND: {
				resId = R.string.message_post_not_found;
				break;
			}
			case TYPE_NO_ACCESS_TO_MEMORY: {
				resId = R.string.message_no_access_to_memory;
				break;
			}
			case TYPE_INSUFFICIENT_SPACE: {
				resId = R.string.message_insufficient_space;
				break;
			}
			case TYPE_EXTENSION: {
				resId = R.string.message_extension_error;
				break;
			}
			case TYPE_UNSUPPORTED_RECAPTCHA: {
				resId = R.string.message_unsupported_recaptcha;
				break;
			}
		}
		if (resId == 0) {
			resId = R.string.message_unknown_error;
		}
		return MainApplication.getInstance().getString(resId);
	}
}
