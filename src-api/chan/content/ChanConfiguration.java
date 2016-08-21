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

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.util.Pair;

import chan.annotation.Extendable;
import chan.annotation.Public;
import chan.content.model.BoardCategory;
import chan.util.CommonUtils;
import chan.util.StringUtils;

import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.MainApplication;
import com.mishiranu.dashchan.preference.Preferences;

@Extendable
public class ChanConfiguration implements ChanManager.Linked
{
	private final String mChanName;
	private final Resources mResources;
	private final SharedPreferences mPreferences;
	
	private boolean mCanSetOptions = true;
	
	public static final ChanManager.Initializer INITIALIZER = new ChanManager.Initializer();
	
	@Public
	public ChanConfiguration()
	{
		this(true);
	}
	
	ChanConfiguration(boolean useInitializer)
	{
		if (useInitializer)
		{
			ChanManager.Initializer.Holder holder = INITIALIZER.consume();
			mChanName = holder.chanName;
			mResources = holder.resources;
			mPreferences = MainApplication.getInstance().getSharedPreferences("chan." + mChanName,
					Context.MODE_PRIVATE);
		}
		else
		{
			mChanName = null;
			mResources = null;
			mPreferences = null;
		}
	}
	
	@Override
	public final String getChanName()
	{
		return mChanName;
	}
	
	@Override
	public final void init()
	{
		mCanSetOptions = false;
	}
	
	@Public public static final String OPTION_SINGLE_BOARD_MODE = "single_board_mode";
	@Public public static final String OPTION_READ_THREAD_PARTIALLY = "read_thread_partially";
	@Public public static final String OPTION_READ_SINGLE_POST = "read_single_post";
	@Public public static final String OPTION_READ_POSTS_COUNT = "read_posts_count";
	@Public public static final String OPTION_READ_USER_BOARDS = "read_user_boards";
	@Public public static final String OPTION_ALLOW_CAPTCHA_PASS = "allow_captcha_pass";
	@Public public static final String OPTION_ALLOW_USER_AUTHORIZATION = "allow_user_authorization";
	
	public static final String OPTION_HIDDEN_DISALLOW_PROXY = "disallow_proxy";
	public static final String OPTION_HIDDEN_DISALLOW_ARCHIVATION = "disallow_archivation";
	public static final String OPTION_HIDDEN_DISABLE_SERIALIZATION = "disable_serialization";
	
	private static final String KEY_TITLE = "title";
	private static final String KEY_DESCRIPTION = "description";
	private static final String KEY_DEFAULT_NAME = "default_name";
	private static final String KEY_BUMP_LIMIT = "bump_limit";
	private static final String KEY_PAGES_COUNT = "pages_count";
	private static final String KEY_BOARDS = "boards";
	
	public static final int PAGES_COUNT_INVALID = Integer.MAX_VALUE;
	public static final int BUMP_LIMIT_INVALID = Integer.MAX_VALUE;
	
	@Public public static final String CAPTCHA_TYPE_RECAPTCHA_1 = "recaptcha_1";
	public static final String CAPTCHA_TYPE_RECAPTCHA_1_JAVASCRIPT = CAPTCHA_TYPE_RECAPTCHA_1 + "_javascript";
	public static final String CAPTCHA_TYPE_RECAPTCHA_1_NOSCRIPT = CAPTCHA_TYPE_RECAPTCHA_1 + "_noscript";
	@Public public static final String CAPTCHA_TYPE_RECAPTCHA_2 = "recaptcha_2";
	public static final String CAPTCHA_TYPE_RECAPTCHA_2_JAVASCRIPT = CAPTCHA_TYPE_RECAPTCHA_2 + "_javascript";
	public static final String CAPTCHA_TYPE_RECAPTCHA_2_FALLBACK = CAPTCHA_TYPE_RECAPTCHA_2 + "_fallback";
	@Public public static final String CAPTCHA_TYPE_MAILRU = "mailru";
	
	private static final String KEY_COOKIE = "cookie";
	private static final String KEY_COOKIES = "cookies";
	
	public static <T extends ChanConfiguration> T get(String chanName)
	{
		return ChanManager.getInstance().getConfiguration(chanName, true);
	}
	
	@Public
	public static <T extends ChanConfiguration> T get(Object object)
	{
		ChanManager manager = ChanManager.getInstance();
		return manager.getConfiguration(manager.getLinkedChanName(object), false);
	}
	
	@Public
	public enum BumpLimitMode
	{
		@Public AFTER_POST,
		@Public AFTER_REPLY,
		@Public BEFORE_POST
	}
	
	@Public
	public static final class Board
	{
		@Public public boolean allowSearch;
		@Public public boolean allowCatalog;
		@Public public boolean allowCatalogSearch;
		@Public public boolean allowArchive;
		@Public public boolean allowPosting;
		@Public public boolean allowDeleting;
		@Public public boolean allowReporting;
		
		@Public
		public Board()
		{
			
		}
	}
	
	@Public
	public static final class Captcha
	{
		@Public
		public enum Input
		{
			@Public ALL,
			@Public LATIN,
			@Public NUMERIC
		}
		
		@Public
		public enum Validity
		{
			@Public SHORT_LIFETIME,
			@Public IN_THREAD,
			@Public IN_BOARD_SEPARATELY,
			@Public IN_BOARD,
			@Public LONG_LIFETIME
		}
		
		@Public public String title;
		@Public public Input input = Input.ALL;
		@Public public Validity validity = Validity.LONG_LIFETIME;
		
		@Public
		public Captcha()
		{
			
		}
	}
	
	@Public
	public static final class Posting
	{
		@Public public boolean allowName = false;
		@Public public boolean allowTripcode = false;
		@Public public boolean allowEmail = false;
		@Public public boolean allowSubject = false;
		
		@Public public boolean optionSage = false;
		@Public public boolean optionSpoiler = false;
		@Public public boolean optionOriginalPoster = false;
		
		@Public public int maxCommentLength = 0;
		@Public public String maxCommentLengthEncoding;
		
		@Public public int attachmentCount = 0;
		@Public public final Set<String> attachmentMimeTypes = new HashSet<>();
		@Public public final List<Pair<String, String>> attachmentRatings = new ArrayList<>();
		@Public public boolean attachmentSpoiler = false;
		
		@Public public final List<Pair<String, String>> userIcons = new ArrayList<>();
		@Public public boolean hasCountryFlags = false;
		
		@Public
		public Posting()
		{
			
		}
	}
	
	@Public
	public static final class Deleting
	{
		@Public public boolean password = false;
		@Public public boolean multiplePosts = false;
		@Public public boolean optionFilesOnly = false;
		
		@Public
		public Deleting()
		{
			
		}
	}
	
	@Public
	public static final class Reporting
	{
		@Public public boolean comment = false;
		@Public public boolean multiplePosts = false;
		@Public public final List<Pair<String, String>> types = new ArrayList<>();
		@Public public final List<Pair<String, String>> options = new ArrayList<>();
		
		@Public
		public Reporting()
		{
			
		}
	}
	
	@Public
	public static final class Authorization
	{
		@Public public int fieldsCount;
		@Public public String[] hints;
		
		@Public
		public Authorization()
		{
			
		}
	}
	
	@Public
	public static final class Archivation
	{
		@Public public final List<String> hosts = new ArrayList<>();
		@Public public final List<Pair<String, String>> options = new ArrayList<>();
		
		@Public
		public Archivation()
		{
			
		}
	}
	
	@Public
	public static final class Statistics
	{
		@Public public boolean threadsViewed = true;
		@Public public boolean postsSent = true;
		@Public public boolean threadsCreated = true;
		
		@Public
		public Statistics()
		{
			
		}
	}
	
	@Public
	public static final class CustomPreference
	{
		@Public public String title;
		@Public public String summary;
		
		@Public
		public CustomPreference()
		{
			
		}
	}
	
	private final HashMap<String, Object> mEditData = new HashMap<>();
	
	public final void commit()
	{
		if (mPreferences != null)
		{
			synchronized (mEditData)
			{
				SharedPreferences.Editor editor = mPreferences.edit();
				for (HashMap.Entry<String, Object> entry : mEditData.entrySet())
				{
					String key = entry.getKey();
					Object value = entry.getValue();
					if (value instanceof Boolean) editor.putBoolean(key, (boolean) value);
					else if (value instanceof Integer) editor.putInt(key, (int) value);
					else editor.putString(key, (String) value);
				}
				editor.commit();
				mEditData.clear();
			}
		}
	}
	
	public final String buildKey(String boardName, String key)
	{
		StringBuilder builder = new StringBuilder();
		if (boardName != null) builder.append(boardName).append('_');
		builder.append(key);
		return builder.toString();
	}
	
	@Public
	public final boolean get(String boardName, String key, boolean defaultValue)
	{
		if (mPreferences == null) return defaultValue;
		String realKey = buildKey(boardName, key);
		synchronized (mEditData)
		{
			Object result = mEditData.get(realKey);
			if (result != null) return (boolean) result;
		}
		return mPreferences.getBoolean(realKey, defaultValue);
	}
	
	@Public
	public final int get(String boardName, String key, int defaultValue)
	{
		if (mPreferences == null) return defaultValue;
		String realKey = buildKey(boardName, key);
		synchronized (mEditData)
		{
			Object result = mEditData.get(realKey);
			if (result != null) return (int) result;
		}
		return mPreferences.getInt(realKey, defaultValue);
	}
	
	@Public
	public final String get(String boardName, String key, String defaultValue)
	{
		if (mPreferences == null) return defaultValue;
		String realKey = buildKey(boardName, key);
		synchronized (mEditData)
		{
			Object result = mEditData.get(realKey);
			if (result != null) return (String) result;
		}
		return mPreferences.getString(realKey, defaultValue);
	}
	
	private void set(String boardName, String key, Object value)
	{
		if (mPreferences != null)
		{
			synchronized (mEditData)
			{
				mEditData.put(buildKey(boardName, key), value);
			}
		}
	}
	
	@Public
	public final void set(String boardName, String key, boolean value)
	{
		set(boardName, key, (Object) value);
	}
	
	@Public
	public final void set(String boardName, String key, int value)
	{
		set(boardName, key, (Object) value);
	}
	
	@Public
	public final void set(String boardName, String key, String value)
	{
		set(boardName, key, (Object) value);
	}
	
	private String mTitle;
	
	@Public
	public final String getTitle()
	{
		if (mChanName != null)
		{
			if (mTitle == null)
			{
				String title = null;
				ArrayList<String> hosts = ChanLocator.get(this).getChanHosts(false);
				if (hosts.size() > 0) title = hosts.get(0);
				if (title == null) title = "";
				mTitle = title;
			}
			return mTitle;
		}
		return null;
	}
	
	private void checkInit()
	{
		if (!mCanSetOptions) throw new IllegalStateException("This method available only from constructor");
	}
	
	private final HashSet<String> mOptions = new HashSet<>();
	
	@Public
	public final void request(String option)
	{
		checkInit();
		mOptions.add(option);
	}
	
	public final boolean getOption(String option)
	{
		return mOptions.contains(option);
	}
	
	private String mSingleBoardName;
	
	@Public
	public final void setSingleBoardName(String boardName)
	{
		checkInit();
		mSingleBoardName = boardName;
	}
	
	public final String getSingleBoardName()
	{
		return mSingleBoardName;
	}
	
	public final void storeBoards(JSONArray jsonArray)
	{
		set(null, KEY_BOARDS, jsonArray != null ? jsonArray.toString() : null);
	}
	
	public final JSONArray getBoards()
	{
		String data = StringUtils.nullIfEmpty(get(null, KEY_BOARDS, null));
		if (data != null)
		{
			try
			{
				return new JSONArray(data);
			}
			catch (JSONException e)
			{
				
			}
		}
		return null;
	}
	
	private HashMap<String, String> mBoardTitlesMap;
	
	@Public
	public final void setBoardTitle(String boardName, String title)
	{
		checkInit();
		if (mBoardTitlesMap == null) mBoardTitlesMap = new HashMap<>();
		mBoardTitlesMap.put(boardName, title);
	}
	
	@Public
	public final void storeBoardTitle(String boardName, String title)
	{
		set(boardName, KEY_TITLE, title);
	}
	
	public final String getBoardTitle(String boardName)
	{
		if (mBoardTitlesMap != null)
		{
			String title = mBoardTitlesMap.get(boardName);
			if (title != null) return title;
		}
		return get(boardName, KEY_TITLE, null);
	}
	
	private HashMap<String, String> mBoardDescriptionsMap;
	
	@Public
	public final void setBoardDescription(String boardName, String description)
	{
		checkInit();
		if (mBoardDescriptionsMap == null) mBoardDescriptionsMap = new HashMap<>();
		mBoardDescriptionsMap.put(boardName, description);
	}
	
	@Public
	public final void storeBoardDescription(String boardName, String description)
	{
		set(boardName, KEY_DESCRIPTION, description);
	}
	
	public final String getBoardDescription(String boardName)
	{
		if (mBoardDescriptionsMap != null)
		{
			String description = mBoardDescriptionsMap.get(boardName);
			if (description != null) return description;
		}
		return get(boardName, KEY_DESCRIPTION, null);
	}
	
	private String mDefaultName;
	private HashMap<String, String> mDefaultNameMap;
	
	@Public
	public final void setDefaultName(String defaultName)
	{
		checkInit();
		mDefaultName = defaultName;
	}
	
	@Public
	public final void setDefaultName(String boardName, String defaultName)
	{
		checkInit();
		if (mDefaultNameMap == null) mDefaultNameMap = new HashMap<>();
		mDefaultNameMap.put(boardName, defaultName);
	}
	
	@Public
	public final void storeDefaultName(String boardName, String defaultName)
	{
		set(boardName, KEY_DEFAULT_NAME, defaultName);
	}
	
	public final String getDefaultName(String boardName)
	{
		if (mDefaultNameMap != null)
		{
			String defaultName = mDefaultNameMap.get(boardName);
			if (defaultName != null) return defaultName;
		}
		String defaultName = get(boardName, KEY_DEFAULT_NAME, null);
		if (!StringUtils.isEmpty(defaultName)) return defaultName;
		if (!StringUtils.isEmpty(mDefaultName)) return mDefaultName;
		return "Anonymous";
	}

	private int mBumpLimit = BUMP_LIMIT_INVALID;
	private HashMap<String, Integer> mBumpLimitMap;
	
	@Public
	public final void setBumpLimit(int bumpLimit)
	{
		checkInit();
		mBumpLimit = bumpLimit;
	}
	
	@Public
	public final void setBumpLimit(String boardName, int bumpLimit)
	{
		checkInit();
		if (mBumpLimitMap == null) mBumpLimitMap = new HashMap<>();
		mBumpLimitMap.put(boardName, bumpLimit);
	}
	
	@Public
	public final void storeBumpLimit(String boardName, int bumpLimit)
	{
		set(boardName, KEY_BUMP_LIMIT, bumpLimit);
	}
	
	public final int getBumpLimit(String boardName)
	{
		if (mBumpLimitMap != null)
		{
			Integer bumpLimit = mBumpLimitMap.get(boardName);
			if (bumpLimit != null) return bumpLimit;
		}
		int bumpLimit = get(boardName, KEY_BUMP_LIMIT, BUMP_LIMIT_INVALID);
		if (bumpLimit != BUMP_LIMIT_INVALID) return bumpLimit;
		return mBumpLimit;
	}
	
	private BumpLimitMode mBumpLimitMode = BumpLimitMode.AFTER_POST;
	
	@Public
	public final void setBumpLimitMode(BumpLimitMode mode)
	{
		checkInit();
		if (mode == null) throw new NullPointerException();
		mBumpLimitMode = mode;
	}
	
	public final BumpLimitMode getBumpLimitMode()
	{
		return mBumpLimitMode;
	}

	private HashMap<String, Integer> mPagesCountMap;
	
	@Public
	public final void setPagesCount(String boardName, int pagesCount)
	{
		checkInit();
		if (mPagesCountMap == null) mPagesCountMap = new HashMap<>();
		mPagesCountMap.put(boardName, pagesCount);
	}
	
	@Public
	public final void storePagesCount(String boardName, int pagesCount)
	{
		set(boardName, KEY_PAGES_COUNT, pagesCount);
	}
	
	public final int getPagesCount(String boardName)
	{
		if (mPagesCountMap != null)
		{
			Integer pagesCount = mPagesCountMap.get(boardName);
			if (pagesCount != null) return pagesCount;
		}
		int pagesCount = get(boardName, KEY_PAGES_COUNT, PAGES_COUNT_INVALID);
		if (pagesCount != PAGES_COUNT_INVALID) return pagesCount;
		return PAGES_COUNT_INVALID;
	}

	private ArrayList<String> mSupportedCaptchaTypesList;
	private String[] mSupportedCaptchaTypes;
	
	@Public
	public final void addCaptchaType(String captchaType)
	{
		checkInit();
		if (captchaType == null) throw new NullPointerException();
		if (mSupportedCaptchaTypesList == null) mSupportedCaptchaTypesList = new ArrayList<>();
		String[] childCaptchaTypes = getCaptchaChildTypesIfExists(captchaType);
		if (childCaptchaTypes != null)
		{
			for (String childCaptchaType : childCaptchaTypes)
			{
				if (childCaptchaType != null && !mSupportedCaptchaTypesList.contains(childCaptchaType))
				{
					mSupportedCaptchaTypesList.add(childCaptchaType);
				}
			}
		}
		else if (captchaType != null && !mSupportedCaptchaTypesList.contains(captchaType))
		{
			mSupportedCaptchaTypesList.add(captchaType);
		}
	}
	
	public final String[] getSupportedCaptchaTypes()
	{
		if (mSupportedCaptchaTypes == null && mSupportedCaptchaTypesList != null)
		{
			mSupportedCaptchaTypes = CommonUtils.toArray(mSupportedCaptchaTypesList, String.class);
			mSupportedCaptchaTypesList = null;
		}
		return mSupportedCaptchaTypes;
	}
	
	public final String getCaptchaType()
	{
		return Preferences.getCaptchaTypeForChanConfiguration(getChanName());
	}
	
	public final String getCaptchaPreferredChildType(String captchaType, String checkCaptchaType)
	{
		String[] childTypes = getCaptchaChildTypesIfExists(captchaType);
		if (childTypes != null)
		{
			if (checkCaptchaType != null)
			{
				for (String childType : childTypes)
				{
					if (childType.equals(checkCaptchaType)) return childType;
				}
			}
			return childTypes[0];
		}
		return captchaType;
	}
	
	public final String getCaptchaParentType(String captchaType)
	{
		if (CAPTCHA_TYPE_RECAPTCHA_1_JAVASCRIPT.equals(captchaType) ||
				CAPTCHA_TYPE_RECAPTCHA_1_NOSCRIPT.equals(captchaType))
		{
			return CAPTCHA_TYPE_RECAPTCHA_1;
		}
		if (CAPTCHA_TYPE_RECAPTCHA_2_JAVASCRIPT.equals(captchaType) ||
				CAPTCHA_TYPE_RECAPTCHA_2_FALLBACK.equals(captchaType))
		{
			return CAPTCHA_TYPE_RECAPTCHA_2;
		}
		return captchaType;
	}
	
	public final String[] getCaptchaChildTypesIfExists(String captchaType)
	{
		if (CAPTCHA_TYPE_RECAPTCHA_1.equals(captchaType))
		{
			return new String[] {CAPTCHA_TYPE_RECAPTCHA_1_JAVASCRIPT, CAPTCHA_TYPE_RECAPTCHA_1_NOSCRIPT};
		}
		if (CAPTCHA_TYPE_RECAPTCHA_2.equals(captchaType))
		{
			return new String[] {CAPTCHA_TYPE_RECAPTCHA_2_JAVASCRIPT, CAPTCHA_TYPE_RECAPTCHA_2_FALLBACK};
		}
		return null;
	}
	
	private LinkedHashMap<String, Boolean> mCustomPreferences;
	
	@Public
	public final void addCustomPreference(String key, boolean defaultValue)
	{
		if (mCustomPreferences == null) mCustomPreferences = new LinkedHashMap<>();
		mCustomPreferences.put(key, defaultValue);
	}
	
	public final LinkedHashMap<String, Boolean> getCustomPreferences()
	{
		return mCustomPreferences;
	}
	
	@Extendable
	protected Board obtainBoardConfiguration(String boardName)
	{
		return null;
	}
	
	private Captcha obtainCaptchaConfigurationSafe(String captchaType)
	{
		if (CAPTCHA_TYPE_RECAPTCHA_1_JAVASCRIPT.equals(captchaType))
		{
			Captcha captcha = new Captcha();
			captcha.title = "reCAPTCHA (JavaScript)";
			captcha.input = Captcha.Input.LATIN;
			captcha.validity = Captcha.Validity.LONG_LIFETIME;
			return captcha;
		}
		else if (CAPTCHA_TYPE_RECAPTCHA_1_NOSCRIPT.equals(captchaType))
		{
			Captcha captcha = new Captcha();
			captcha.title = "reCAPTCHA (NoScript)";
			captcha.input = Captcha.Input.LATIN;
			captcha.validity = Captcha.Validity.LONG_LIFETIME;
			return captcha;
		}
		else if (CAPTCHA_TYPE_RECAPTCHA_2_JAVASCRIPT.equals(captchaType))
		{
			Captcha captcha = new Captcha();
			captcha.title = "reCAPTCHA 2 (JavaScript)";
			captcha.input = Captcha.Input.LATIN;
			captcha.validity = Captcha.Validity.SHORT_LIFETIME;
			return captcha;
		}
		else if (CAPTCHA_TYPE_RECAPTCHA_2_FALLBACK.equals(captchaType))
		{
			Captcha captcha = new Captcha();
			captcha.title = "reCAPTCHA 2 (Fallback)";
			captcha.input = Captcha.Input.LATIN;
			captcha.validity = Captcha.Validity.LONG_LIFETIME;
			return captcha;
		}
		else if (CAPTCHA_TYPE_MAILRU.equals(captchaType))
		{
			Captcha captcha = new Captcha();
			captcha.title = "Mail.Ru Nocaptcha";
			captcha.input = Captcha.Input.LATIN;
			captcha.validity = Captcha.Validity.LONG_LIFETIME;
			return captcha;
		}
		else if (captchaType != null)
		{
			try
			{
				return obtainCustomCaptchaConfiguration(captchaType);
			}
			catch (LinkageError | RuntimeException e)
			{
				ExtensionException.logException(e, false);
			}
		}
		return null;
	}
	
	@Extendable
	protected Captcha obtainCustomCaptchaConfiguration(String captchaType)
	{
		return null;
	}
	
	@Extendable
	protected Posting obtainPostingConfiguration(String boardName, boolean newThread)
	{
		return null;
	}
	
	@Extendable
	protected Deleting obtainDeletingConfiguration(String boardName)
	{
		return null;
	}
	
	@Extendable
	protected Reporting obtainReportingConfiguration(String boardName)
	{
		return null;
	}
	
	@Extendable
	protected Authorization obtainCaptchaPassConfiguration()
	{
		Authorization authorization = new Authorization();
		authorization.fieldsCount = 1;
		authorization.hints = new String[] {MainApplication.getInstance().getString(R.string.text_password)};
		return authorization;
	}
	
	@Extendable
	protected Authorization obtainUserAuthorizationConfiguration()
	{
		Authorization authorization = new Authorization();
		authorization.fieldsCount = 1;
		authorization.hints = new String[] {MainApplication.getInstance().getString(R.string.text_password)};
		return authorization;
	}
	
	@Extendable
	protected Archivation obtainArchivationConfiguration()
	{
		return null;
	}
	
	@Extendable
	protected Statistics obtainStatisticsConfiguration()
	{
		return new Statistics();
	}
	
	@Extendable
	protected CustomPreference obtainCustomPreferenceConfiguration(String key)
	{
		return null;
	}
	
	@Public
	public final Context getContext()
	{
		return MainApplication.getInstance();
	}
	
	@Public
	public final Resources getResources()
	{
		return mResources;
	}
	
	@Public
	public final String getCookie(String cookie)
	{
		return get(null, KEY_COOKIE + "_" + cookie, null);
	}
	
	@Public
	public final void storeCookie(String cookie, String value, String displayName)
	{
		set(null, KEY_COOKIE + "_" + cookie, value);
		JSONObject jsonObject;
		try
		{
			String data = get(null, KEY_COOKIES, null);
			jsonObject = new JSONObject(data);
		}
		catch (Exception e)
		{
			jsonObject = new JSONObject();
		}
		if (displayName != null)
		{
			try
			{
				jsonObject.put(cookie, displayName);
			}
			catch (JSONException e)
			{
				throw new RuntimeException(e);
			}
			set(null, KEY_COOKIES, jsonObject.toString());
		}
	}
	
	public final boolean hasCookies()
	{
		try
		{
			String data = get(null, KEY_COOKIES, null);
			JSONObject jsonObject = new JSONObject(data);
			return jsonObject.length() > 0;
		}
		catch (Exception e)
		{
			
		}
		return false;
	}
	
	public final HashMap<String, String> getAllCookieNames()
	{
		HashMap<String, String> result = new HashMap<>();
		try
		{
			String data = get(null, KEY_COOKIES, null);
			JSONObject jsonObject = new JSONObject(data);
			Iterator<String> keys = jsonObject.keys();
			while (keys.hasNext())
			{
				String key = keys.next();
				result.put(key, jsonObject.getString(key));
			}
		}
		catch (Exception e)
		{
			
		}
		return result;
	}
	
	@Public
	public final String[] getUserAuthorizationData()
	{
		return Preferences.getUserAuthorizationData(getChanName());
	}
	
	@Public
	public final File getDownloadDirectory()
	{
		return Preferences.getDownloadDirectory();
	}
	
	public final void updateFromBoards(BoardCategory[] boardCategories)
	{
		for (BoardCategory boardCategory : boardCategories)
		{
			updateFromBoards(boardCategory.getBoards());
		}
	}
	
	public final void updateFromBoards(chan.content.model.Board[] boards)
	{
		for (chan.content.model.Board board : boards)
		{
			String boardName = board.getBoardName();
			String title = board.getTitle();
			String description = board.getDescription();
			storeBoardTitle(boardName, title);
			storeBoardDescription(boardName, description);
		}
	}
	
	public static final class Safe
	{
		private static final Board DEFAULT_BOARD = new Board();
		private static final Captcha DEFAULT_CAPTCHA = new Captcha();
		private static final Authorization DEFAULT_AUTHORIZATION = new Authorization();
		
		static
		{
			DEFAULT_CAPTCHA.validity = Captcha.Validity.LONG_LIFETIME;
			DEFAULT_CAPTCHA.input = Captcha.Input.ALL;
		}
		
		private final ChanConfiguration mConfiguration;
		
		private Safe(ChanConfiguration configuration)
		{
			mConfiguration = configuration;
		}
		
		public Board obtainBoard(String boardName)
		{
			Board board = null;
			try
			{
				board = mConfiguration.obtainBoardConfiguration(boardName);
			}
			catch (LinkageError | RuntimeException e)
			{
				ExtensionException.logException(e, false);
			}
			if (board == null) board = DEFAULT_BOARD;
			return board;
		}
		
		public Captcha obtainCaptcha(String captchaType)
		{
			Captcha captcha = mConfiguration.obtainCaptchaConfigurationSafe(captchaType);
			if (captcha == null) captcha = DEFAULT_CAPTCHA;
			return captcha;
		}
		
		public Posting obtainPosting(String boardName, boolean newThread)
		{
			Posting posting = null;
			try
			{
				posting = mConfiguration.obtainPostingConfiguration(boardName, newThread);
			}
			catch (LinkageError | RuntimeException e)
			{
				ExtensionException.logException(e, false);
			}
			return posting;
		}
		
		public Deleting obtainDeleting(String boardName)
		{
			Deleting deleting = null;
			try
			{
				deleting = mConfiguration.obtainDeletingConfiguration(boardName);
			}
			catch (LinkageError | RuntimeException e)
			{
				ExtensionException.logException(e, false);
			}
			return deleting;
		}
		
		public Reporting obtainReporting(String boardName)
		{
			Reporting reporting = null;
			try
			{
				reporting = mConfiguration.obtainReportingConfiguration(boardName);
			}
			catch (LinkageError | RuntimeException e)
			{
				ExtensionException.logException(e, false);
			}
			return reporting;
		}
		
		public Authorization obtainCaptchaPass()
		{
			Authorization authorization = null;
			try
			{
				authorization = mConfiguration.obtainCaptchaPassConfiguration();
			}
			catch (LinkageError | RuntimeException e)
			{
				ExtensionException.logException(e, false);
			}
			if (authorization == null) authorization = DEFAULT_AUTHORIZATION;
			return authorization;
		}
		
		public Authorization obtainUserAuthorization()
		{
			Authorization authorization = null;
			try
			{
				authorization = mConfiguration.obtainUserAuthorizationConfiguration();
			}
			catch (LinkageError | RuntimeException e)
			{
				ExtensionException.logException(e, false);
			}
			if (authorization == null) authorization = DEFAULT_AUTHORIZATION;
			return authorization;
		}
		
		public Archivation obtainArchivation()
		{
			Archivation archivation = null;
			try
			{
				archivation = mConfiguration.obtainArchivationConfiguration();
			}
			catch (LinkageError | RuntimeException e)
			{
				ExtensionException.logException(e, false);
			}
			return archivation;
		}
		
		public Statistics obtainStatistics()
		{
			Statistics statistics = null;
			try
			{
				statistics = mConfiguration.obtainStatisticsConfiguration();
			}
			catch (LinkageError | RuntimeException e)
			{
				ExtensionException.logException(e, false);
			}
			return statistics;
		}
		
		public CustomPreference obtainCustomPreference(String key)
		{
			CustomPreference customPreference = null;
			try
			{
				customPreference = mConfiguration.obtainCustomPreferenceConfiguration(key);
			}
			catch (LinkageError | RuntimeException e)
			{
				ExtensionException.logException(e, false);
			}
			return customPreference;
		}
	}
	
	private final Safe mSafe = new Safe(this);
	
	public final Safe safe()
	{
		return mSafe;
	}
}