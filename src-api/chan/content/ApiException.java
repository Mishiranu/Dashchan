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

package chan.content;

import java.io.Serializable;
import java.util.LinkedHashSet;

import chan.util.StringUtils;

import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.util.FlagUtils;

public class ApiException extends Exception
{
	private static final long serialVersionUID = 1L;
	
	public static final int SEND_ERROR_NO_BOARD = 100;
	public static final int SEND_ERROR_NO_THREAD = 101;
	public static final int SEND_ERROR_NO_ACCESS = 102;
	public static final int SEND_ERROR_CAPTCHA = 103;
	public static final int SEND_ERROR_BANNED = 104;
	public static final int SEND_ERROR_CLOSED = 105;
	public static final int SEND_ERROR_TOO_FAST = 106;
	public static final int SEND_ERROR_FIELD_TOO_LONG = 107;
	public static final int SEND_ERROR_FILE_EXISTS = 108;
	public static final int SEND_ERROR_FILE_NOT_SUPPORTED = 109;
	public static final int SEND_ERROR_FILE_TOO_BIG = 110;
	public static final int SEND_ERROR_FILES_TOO_MANY = 111;
	public static final int SEND_ERROR_SPAM_LIST = 112;
	public static final int SEND_ERROR_EMPTY_FILE = 113;
	public static final int SEND_ERROR_EMPTY_SUBJECT = 114;
	public static final int SEND_ERROR_EMPTY_COMMENT = 115;
	public static final int SEND_ERROR_FILES_LIMIT = 116;
	
	public static final int DELETE_ERROR_NO_ACCESS = 200;
	public static final int DELETE_ERROR_PASSWORD = 201;
	public static final int DELETE_ERROR_NOT_FOUND = 202;
	public static final int DELETE_ERROR_TOO_NEW = 203;
	public static final int DELETE_ERROR_TOO_OLD = 204;
	public static final int DELETE_ERROR_TOO_OFTEN = 205;
	
	public static final int REPORT_ERROR_NO_ACCESS = 300;
	public static final int REPORT_ERROR_TOO_OFTEN = 301;
	public static final int REPORT_ERROR_EMPTY_COMMENT = 302;
	
	public static final int ARCHIVE_ERROR_NO_ACCESS = 400;
	public static final int ARCHIVE_ERROR_TOO_OFTEN = 401;
	
	public static final int FLAG_KEEP_CAPTCHA = 0x00000001;
	
	private final int mErrorType;
	private final int mFlags;
	private final Object mExtra;
	
	public ApiException(int errorType)
	{
		this(errorType, 0, null);
	}
	
	public ApiException(int errorType, int flags)
	{
		this(errorType, flags, null);
	}
	
	public ApiException(int errorType, Object extra)
	{
		this(errorType, 0, extra);
	}
	
	public ApiException(int errorType, int flags, Object extra)
	{
		mErrorType = errorType;
		mFlags = flags;
		mExtra = extra;
	}
	
	public ApiException(String detailMessage)
	{
		this(detailMessage, 0);
	}
	
	public ApiException(String detailMessage, int flags)
	{
		super(detailMessage);
		mErrorType = 0;
		mFlags = flags;
		mExtra = null;
	}
	
	public int getErrorType()
	{
		return mErrorType;
	}
	
	public boolean checkFlag(int flag)
	{
		return FlagUtils.get(mFlags, flag);
	}
	
	public Object getExtra()
	{
		switch (mErrorType)
		{
			case ApiException.SEND_ERROR_BANNED:
			{
				if (mExtra instanceof BanExtra) return mExtra;
				break;
			}
			case ApiException.SEND_ERROR_SPAM_LIST:
			{
				if (mExtra instanceof WordsExtra) return mExtra;
				break;
			}
		}
		return null;
	}
	
	public static int getResId(int errorType)
	{
		int resId = 0;
		switch (errorType)
		{
			case ApiException.SEND_ERROR_NO_BOARD:
			{
				resId = R.string.message_board_not_exist;
				break;
			}
			case ApiException.SEND_ERROR_NO_THREAD:
			{
				resId = R.string.message_thread_not_exist;
				break;
			}
			case ApiException.SEND_ERROR_NO_ACCESS:
			{
				resId = R.string.message_no_access;
				break;
			}
			case ApiException.SEND_ERROR_CAPTCHA:
			{
				resId = R.string.message_captcha_not_valid;
				break;
			}
			case ApiException.SEND_ERROR_BANNED:
			{
				resId = R.string.message_banned;
				break;
			}
			case ApiException.SEND_ERROR_CLOSED:
			{
				resId = R.string.message_thread_closed;
				break;
			}
			case ApiException.SEND_ERROR_TOO_FAST:
			{
				resId = R.string.message_posting_too_fast;
				break;
			}
			case ApiException.SEND_ERROR_FIELD_TOO_LONG:
			{
				resId = R.string.message_field_too_long;
				break;
			}
			case ApiException.SEND_ERROR_FILE_EXISTS:
			{
				resId = R.string.message_file_exists;
				break;
			}
			case ApiException.SEND_ERROR_FILE_NOT_SUPPORTED:
			{
				resId = R.string.message_file_not_support;
				break;
			}
			case ApiException.SEND_ERROR_FILE_TOO_BIG:
			{
				resId = R.string.message_files_too_big;
				break;
			}
			case ApiException.SEND_ERROR_FILES_TOO_MANY:
			{
				resId = R.string.message_files_too_many;
				break;
			}
			case ApiException.SEND_ERROR_SPAM_LIST:
			{
				resId = R.string.message_spam_list;
				break;
			}
			case ApiException.SEND_ERROR_EMPTY_FILE:
			{
				resId = R.string.message_empty_file;
				break;
			}
			case ApiException.SEND_ERROR_EMPTY_SUBJECT:
			{
				resId = R.string.message_empty_subject;
				break;
			}
			case ApiException.SEND_ERROR_EMPTY_COMMENT:
			{
				resId = R.string.message_empty_post;
				break;
			}
			case ApiException.SEND_ERROR_FILES_LIMIT:
			{
				resId = R.string.message_files_limit_reached;
				break;
			}
			case ApiException.DELETE_ERROR_NO_ACCESS:
			{
				resId = R.string.message_no_access;
				break;
			}
			case ApiException.DELETE_ERROR_PASSWORD:
			{
				resId = R.string.message_incorrect_password;
				break;
			}
			case ApiException.DELETE_ERROR_NOT_FOUND:
			{
				resId = R.string.message_not_found;
				break;
			}
			case ApiException.DELETE_ERROR_TOO_NEW:
			{
				resId = R.string.message_delete_too_new_error;
				break;
			}
			case ApiException.DELETE_ERROR_TOO_OLD:
			{
				resId = R.string.message_delete_too_old_error;
				break;
			}
			case ApiException.DELETE_ERROR_TOO_OFTEN:
			{
				resId = R.string.message_delete_too_often_error;
				break;
			}
			case ApiException.REPORT_ERROR_NO_ACCESS:
			{
				resId = R.string.message_no_access;
				break;
			}
			case ApiException.REPORT_ERROR_TOO_OFTEN:
			{
				resId = R.string.message_report_too_often_error;
				break;
			}
			case ApiException.REPORT_ERROR_EMPTY_COMMENT:
			{
				resId = R.string.message_empty_post;
				break;
			}
			case ApiException.ARCHIVE_ERROR_NO_ACCESS:
			{
				resId = R.string.message_no_access;
				break;
			}
			case ApiException.ARCHIVE_ERROR_TOO_OFTEN:
			{
				resId = R.string.message_archive_too_often_error;
				break;
			}
		}
		return resId;
	}
	
	public ErrorItem getErrorItem()
	{
		String message = getMessage();
		if (!StringUtils.isEmpty(message)) return new ErrorItem(0, message);
		return new ErrorItem(ErrorItem.TYPE_API, mErrorType);
	}
	
	public static class BanExtra implements Serializable
	{
		private static final long serialVersionUID = 1L;
		
		public String id;
		public String message;
		public long startDate;
		public long expireDate;
		
		public BanExtra setId(String id)
		{
			this.id = id;
			return this;
		}
		
		public BanExtra setMessage(String message)
		{
			this.message = message;
			return this;
		}
		
		public BanExtra setStartDate(long startDate)
		{
			this.startDate = startDate;
			return this;
		}
		
		public BanExtra setExpireDate(long expireDate)
		{
			this.expireDate = expireDate;
			return this;
		}
		
		@Override
		public String toString()
		{
			return id + " " + message + " " + startDate + " " + expireDate;
		}
	}
	
	public static class WordsExtra implements Serializable
	{
		private static final long serialVersionUID = 1L;
		
		public final LinkedHashSet<String> words = new LinkedHashSet<>();
		
		public WordsExtra addWord(String word)
		{
			words.add(word);
			return this;
		}
		
		@Override
		public String toString()
		{
			return words.toString();
		}
	}
}