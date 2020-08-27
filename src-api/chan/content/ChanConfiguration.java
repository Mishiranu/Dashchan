package chan.content;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.util.Pair;
import chan.annotation.Extendable;
import chan.annotation.Public;
import chan.content.model.BoardCategory;
import chan.util.StringUtils;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.MainApplication;
import com.mishiranu.dashchan.content.Preferences;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@Extendable
public class ChanConfiguration implements ChanManager.Linked {
	private final String chanName;
	private final Resources resources;
	private final SharedPreferences preferences;

	private boolean canSetOptions = true;

	public static final ChanManager.Initializer INITIALIZER = new ChanManager.Initializer();

	@Public
	public ChanConfiguration() {
		this(true);
	}

	ChanConfiguration(boolean useInitializer) {
		if (useInitializer) {
			ChanManager.Initializer.Holder holder = INITIALIZER.consume();
			chanName = holder.chanName;
			resources = holder.resources;
			preferences = MainApplication.getInstance().getSharedPreferences("chan." + chanName,
					Context.MODE_PRIVATE);
		} else {
			chanName = null;
			resources = null;
			preferences = null;
		}
	}

	@Override
	public final String getChanName() {
		return chanName;
	}

	@Override
	public final void init() {
		canSetOptions = false;
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

	// TODO CHAN
	// Remove this field after updating
	// allchan arhivach cablesix dvach fourchan meguca ronery sevenchan tumbach
	// Added: 28.07.20 22:23
	@Public public static final String CAPTCHA_TYPE_RECAPTCHA_1 = "recaptcha_1";
	@Public public static final String CAPTCHA_TYPE_RECAPTCHA_2 = "recaptcha_2";
	@Public public static final String CAPTCHA_TYPE_RECAPTCHA_2_INVISIBLE = "recaptcha_2_invisible";
	@Public public static final String CAPTCHA_TYPE_HCAPTCHA = "hcaptcha";
	@Public public static final String CAPTCHA_TYPE_MAILRU = "mailru";

	private static final String KEY_COOKIES = "cookies";
	private static final String KEY_COOKIE_VALUE = "value";
	private static final String KEY_COOKIE_DISPLAY_NAME = "displayName";
	private static final String KEY_COOKIE_BLOCKED = "blocked";

	public static <T extends ChanConfiguration> T get(String chanName) {
		return ChanManager.getInstance().getConfiguration(chanName, true);
	}

	@Public
	public static <T extends ChanConfiguration> T get(Object object) {
		ChanManager manager = ChanManager.getInstance();
		return manager.getConfiguration(manager.getLinkedChanName(object), false);
	}

	@Public
	public enum BumpLimitMode {
		@Public AFTER_POST,
		@Public AFTER_REPLY,
		@Public BEFORE_POST
	}

	@Public
	public static final class Board {
		@Public public boolean allowSearch;
		@Public public boolean allowCatalog;
		@Public public boolean allowCatalogSearch;
		@Public public boolean allowArchive;
		@Public public boolean allowPosting;
		@Public public boolean allowDeleting;
		@Public public boolean allowReporting;

		@Public
		public Board() {}
	}

	@Public
	public static final class Captcha {
		@Public
		public enum Input {
			@Public ALL,
			@Public LATIN,
			@Public NUMERIC
		}

		@Public
		public enum Validity {
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
		public Captcha() {}
	}

	@Public
	public static final class Posting {
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
		public Posting() {}
	}

	@Public
	public static final class Deleting {
		@Public public boolean password = false;
		@Public public boolean multiplePosts = false;
		@Public public boolean optionFilesOnly = false;

		@Public
		public Deleting() {}
	}

	@Public
	public static final class Reporting {
		@Public public boolean comment = false;
		@Public public boolean multiplePosts = false;
		@Public public final List<Pair<String, String>> types = new ArrayList<>();
		@Public public final List<Pair<String, String>> options = new ArrayList<>();

		@Public
		public Reporting() {}
	}

	@Public
	public static final class Authorization {
		@Public public int fieldsCount;
		@Public public String[] hints;

		@Public
		public Authorization() {}
	}

	@Public
	public static final class Archivation {
		@Public public final List<String> hosts = new ArrayList<>();
		@Public public final List<Pair<String, String>> options = new ArrayList<>();

		@Public
		public Archivation() {}
	}

	@Public
	public static final class Statistics {
		@Public public boolean threadsViewed = true;
		@Public public boolean postsSent = true;
		@Public public boolean threadsCreated = true;

		@Public
		public Statistics() {}
	}

	@Public
	public static final class CustomPreference {
		@Public public String title;
		@Public public String summary;

		@Public
		public CustomPreference() {}
	}

	private final HashMap<String, Object> editData = new HashMap<>();

	public final void commit() {
		if (preferences != null) {
			synchronized (editData) {
				SharedPreferences.Editor editor = preferences.edit();
				for (HashMap.Entry<String, Object> entry : editData.entrySet()) {
					String key = entry.getKey();
					Object value = entry.getValue();
					if (value instanceof Boolean) {
						editor.putBoolean(key, (boolean) value);
					} else if (value instanceof Integer) {
						editor.putInt(key, (int) value);
					} else {
						editor.putString(key, (String) value);
					}
				}
				editor.commit();
				editData.clear();
			}
		}
	}

	public final String buildKey(String boardName, String key) {
		StringBuilder builder = new StringBuilder();
		if (boardName != null) {
			builder.append(boardName).append('_');
		}
		builder.append(key);
		return builder.toString();
	}

	@Public
	public final boolean get(String boardName, String key, boolean defaultValue) {
		if (preferences == null) {
			return defaultValue;
		}
		String realKey = buildKey(boardName, key);
		synchronized (editData) {
			Object result = editData.get(realKey);
			if (result != null) {
				return (boolean) result;
			}
		}
		return preferences.getBoolean(realKey, defaultValue);
	}

	@Public
	public final int get(String boardName, String key, int defaultValue) {
		if (preferences == null) {
			return defaultValue;
		}
		String realKey = buildKey(boardName, key);
		synchronized (editData) {
			Object result = editData.get(realKey);
			if (result != null) {
				return (int) result;
			}
		}
		return preferences.getInt(realKey, defaultValue);
	}

	@Public
	public final String get(String boardName, String key, String defaultValue) {
		if (preferences == null) {
			return defaultValue;
		}
		String realKey = buildKey(boardName, key);
		synchronized (editData) {
			Object result = editData.get(realKey);
			if (result != null) {
				return (String) result;
			}
		}
		return preferences.getString(realKey, defaultValue);
	}

	private void set(String boardName, String key, Object value) {
		if (preferences != null) {
			synchronized (editData) {
				editData.put(buildKey(boardName, key), value);
			}
		}
	}

	@Public
	public final void set(String boardName, String key, boolean value) {
		set(boardName, key, (Object) value);
	}

	@Public
	public final void set(String boardName, String key, int value) {
		set(boardName, key, (Object) value);
	}

	@Public
	public final void set(String boardName, String key, String value) {
		set(boardName, key, (Object) value);
	}

	private String title;

	@Public
	public final String getTitle() {
		if (chanName != null) {
			if (title == null) {
				String title = null;
				ArrayList<String> hosts = ChanLocator.get(this).getChanHosts(false);
				if (hosts.size() > 0) {
					title = hosts.get(0);
				}
				if (title == null) {
					title = "";
				}
				this.title = title;
			}
			return title;
		}
		return null;
	}

	private void checkInit() {
		if (!canSetOptions) {
			throw new IllegalStateException("This method available only from constructor");
		}
	}

	private final HashSet<String> options = new HashSet<>();

	@Public
	public final void request(String option) {
		checkInit();
		options.add(option);
	}

	public final boolean getOption(String option) {
		return options.contains(option);
	}

	private String singleBoardName;

	@Public
	public final void setSingleBoardName(String boardName) {
		checkInit();
		singleBoardName = boardName;
	}

	public final String getSingleBoardName() {
		return singleBoardName;
	}

	public final void storeBoards(JSONArray jsonArray) {
		set(null, KEY_BOARDS, jsonArray != null ? jsonArray.toString() : null);
	}

	public final JSONArray getBoards() {
		String data = StringUtils.nullIfEmpty(get(null, KEY_BOARDS, null));
		if (data != null) {
			try {
				return new JSONArray(data);
			} catch (JSONException e) {
				// Invalid or unspecified data, ignore exception
			}
		}
		return null;
	}

	private HashMap<String, String> boardTitlesMap;

	@Public
	public final void setBoardTitle(String boardName, String title) {
		checkInit();
		if (boardTitlesMap == null) {
			boardTitlesMap = new HashMap<>();
		}
		boardTitlesMap.put(boardName, title);
	}

	@Public
	public final void storeBoardTitle(String boardName, String title) {
		set(boardName, KEY_TITLE, title);
	}

	public final String getBoardTitle(String boardName) {
		if (boardTitlesMap != null) {
			String title = boardTitlesMap.get(boardName);
			if (title != null) {
				return title;
			}
		}
		return get(boardName, KEY_TITLE, null);
	}

	private HashMap<String, String> boardDescriptionsMap;

	@Public
	public final void setBoardDescription(String boardName, String description) {
		checkInit();
		if (boardDescriptionsMap == null) {
			boardDescriptionsMap = new HashMap<>();
		}
		boardDescriptionsMap.put(boardName, description);
	}

	@Public
	public final void storeBoardDescription(String boardName, String description) {
		set(boardName, KEY_DESCRIPTION, description);
	}

	public final String getBoardDescription(String boardName) {
		if (boardDescriptionsMap != null) {
			String description = boardDescriptionsMap.get(boardName);
			if (description != null) {
				return description;
			}
		}
		return get(boardName, KEY_DESCRIPTION, null);
	}

	private String defaultName;
	private HashMap<String, String> defaultNameMap;

	@Public
	public final void setDefaultName(String defaultName) {
		checkInit();
		this.defaultName = defaultName;
	}

	@Public
	public final void setDefaultName(String boardName, String defaultName) {
		checkInit();
		if (defaultNameMap == null) {
			defaultNameMap = new HashMap<>();
		}
		defaultNameMap.put(boardName, defaultName);
	}

	@Public
	public final void storeDefaultName(String boardName, String defaultName) {
		set(boardName, KEY_DEFAULT_NAME, defaultName);
	}

	public final String getDefaultName(String boardName) {
		if (defaultNameMap != null) {
			String defaultName = defaultNameMap.get(boardName);
			if (defaultName != null) {
				return defaultName;
			}
		}
		String defaultName = get(boardName, KEY_DEFAULT_NAME, null);
		if (!StringUtils.isEmpty(defaultName)) {
			return defaultName;
		}
		if (!StringUtils.isEmpty(this.defaultName)) {
			return this.defaultName;
		}
		return "Anonymous";
	}

	private int bumpLimit = BUMP_LIMIT_INVALID;
	private HashMap<String, Integer> bumpLimitMap;

	@Public
	public final void setBumpLimit(int bumpLimit) {
		checkInit();
		this.bumpLimit = bumpLimit;
	}

	@Public
	public final void setBumpLimit(String boardName, int bumpLimit) {
		checkInit();
		if (bumpLimitMap == null) {
			bumpLimitMap = new HashMap<>();
		}
		bumpLimitMap.put(boardName, bumpLimit);
	}

	@Public
	public final void storeBumpLimit(String boardName, int bumpLimit) {
		set(boardName, KEY_BUMP_LIMIT, bumpLimit);
	}

	public final int getBumpLimit(String boardName) {
		int bumpLimit = BUMP_LIMIT_INVALID;
		if (bumpLimitMap != null) {
			Integer bumpLimitValue = bumpLimitMap.get(boardName);
			if (bumpLimitValue != null) {
				bumpLimit = bumpLimitValue;
			}
		}
		if (bumpLimit == BUMP_LIMIT_INVALID) {
			bumpLimit = get(boardName, KEY_BUMP_LIMIT, BUMP_LIMIT_INVALID);
		}
		if (bumpLimit == BUMP_LIMIT_INVALID) {
			bumpLimit = this.bumpLimit;
		}
		return bumpLimit > 0 ? bumpLimit : BUMP_LIMIT_INVALID;
	}

	public final int getBumpLimitWithMode(String boardName) {
		int bumpLimit = getBumpLimit(boardName);
		if (bumpLimit != ChanConfiguration.BUMP_LIMIT_INVALID) {
			switch (bumpLimitMode) {
				case AFTER_POST: {
					break;
				}
				case AFTER_REPLY: {
					bumpLimit++;
					break;
				}
				case BEFORE_POST: {
					bumpLimit--;
					break;
				}
			}
		}
		return bumpLimit;
	}

	private BumpLimitMode bumpLimitMode = BumpLimitMode.AFTER_POST;

	@Public
	public final void setBumpLimitMode(BumpLimitMode mode) {
		checkInit();
		if (mode == null) {
			throw new NullPointerException();
		}
		bumpLimitMode = mode;
	}

	private HashMap<String, Integer> pagesCountMap;

	@Public
	public final void setPagesCount(String boardName, int pagesCount) {
		checkInit();
		if (pagesCountMap == null) {
			pagesCountMap = new HashMap<>();
		}
		pagesCountMap.put(boardName, pagesCount);
	}

	@Public
	public final void storePagesCount(String boardName, int pagesCount) {
		set(boardName, KEY_PAGES_COUNT, pagesCount);
	}

	public final int getPagesCount(String boardName) {
		if (pagesCountMap != null) {
			Integer pagesCount = pagesCountMap.get(boardName);
			if (pagesCount != null) {
				return pagesCount;
			}
		}
		return get(boardName, KEY_PAGES_COUNT, PAGES_COUNT_INVALID);
	}

	private LinkedHashSet<String> supportedCaptchaTypes;

	@Public
	public final void addCaptchaType(String captchaType) {
		checkInit();
		if (captchaType == null) {
			throw new NullPointerException();
		}
		if (CAPTCHA_TYPE_RECAPTCHA_1.equals(captchaType)) {
			// Unsupported captcha type
			return;
		}
		if (supportedCaptchaTypes == null) {
			supportedCaptchaTypes = new LinkedHashSet<>();
		}
		supportedCaptchaTypes.add(captchaType);
	}

	public final Collection<String> getSupportedCaptchaTypes() {
		return supportedCaptchaTypes;
	}

	public final String getCaptchaType() {
		return Preferences.getCaptchaTypeForChanConfiguration(getChanName());
	}

	private LinkedHashMap<String, Boolean> customPreferences;

	@Public
	public final void addCustomPreference(String key, boolean defaultValue) {
		if (customPreferences == null) {
			customPreferences = new LinkedHashMap<>();
		}
		customPreferences.put(key, defaultValue);
	}

	public final LinkedHashMap<String, Boolean> getCustomPreferences() {
		return customPreferences;
	}

	@Extendable
	protected Board obtainBoardConfiguration(String boardName) {
		return null;
	}

	private Captcha obtainCaptchaConfigurationSafe(String captchaType) {
		if (CAPTCHA_TYPE_RECAPTCHA_1.equals(captchaType)) {
			// Unsupported captcha type
			return null;
		} else if (CAPTCHA_TYPE_RECAPTCHA_2.equals(captchaType)) {
			Captcha captcha = new Captcha();
			captcha.title = "reCAPTCHA 2";
			captcha.input = Captcha.Input.LATIN;
			captcha.validity = Captcha.Validity.SHORT_LIFETIME;
			return captcha;
		} else if (CAPTCHA_TYPE_RECAPTCHA_2_INVISIBLE.equals(captchaType)) {
			Captcha captcha = new Captcha();
			captcha.title = "reCAPTCHA 2 Invisible";
			captcha.input = Captcha.Input.ALL;
			captcha.validity = Captcha.Validity.SHORT_LIFETIME;
			return captcha;
		} else if (CAPTCHA_TYPE_HCAPTCHA.equals(captchaType)) {
			Captcha captcha = new Captcha();
			captcha.title = "hCaptcha";
			captcha.input = Captcha.Input.ALL;
			captcha.validity = Captcha.Validity.SHORT_LIFETIME;
			return captcha;
		} else if (CAPTCHA_TYPE_MAILRU.equals(captchaType)) {
			Captcha captcha = new Captcha();
			captcha.title = "Mail.Ru Nocaptcha";
			captcha.input = Captcha.Input.LATIN;
			captcha.validity = Captcha.Validity.LONG_LIFETIME;
			return captcha;
		} else if (captchaType != null) {
			try {
				return obtainCustomCaptchaConfiguration(captchaType);
			} catch (LinkageError | RuntimeException e) {
				ExtensionException.logException(e, false);
			}
		}
		return null;
	}

	@Extendable
	protected Captcha obtainCustomCaptchaConfiguration(String captchaType) {
		return null;
	}

	@Extendable
	protected Posting obtainPostingConfiguration(String boardName, boolean newThread) {
		return null;
	}

	@Extendable
	protected Deleting obtainDeletingConfiguration(String boardName) {
		return null;
	}

	@Extendable
	protected Reporting obtainReportingConfiguration(String boardName) {
		return null;
	}

	@Extendable
	protected Authorization obtainCaptchaPassConfiguration() {
		Authorization authorization = new Authorization();
		authorization.fieldsCount = 1;
		authorization.hints = new String[] {MainApplication.getInstance().getString(R.string.text_password)};
		return authorization;
	}

	@Extendable
	protected Authorization obtainUserAuthorizationConfiguration() {
		Authorization authorization = new Authorization();
		authorization.fieldsCount = 1;
		authorization.hints = new String[] {MainApplication.getInstance().getString(R.string.text_password)};
		return authorization;
	}

	@Extendable
	protected Archivation obtainArchivationConfiguration() {
		return null;
	}

	@Extendable
	protected Statistics obtainStatisticsConfiguration() {
		return new Statistics();
	}

	@Extendable
	protected CustomPreference obtainCustomPreferenceConfiguration(String key) {
		return null;
	}

	@Public
	public final Context getContext() {
		return MainApplication.getInstance();
	}

	@Public
	public final Resources getResources() {
		return resources;
	}

	private JSONObject cookies;

	private JSONObject obtainCookiesLocked() {
		if (cookies == null) {
			try {
				cookies = new JSONObject(get(null, KEY_COOKIES, null));
			} catch (Exception e) {
				cookies = new JSONObject();
			}
		}
		return cookies;
	}

	@Public
	public final String getCookie(String cookie) {
		if (cookie == null) {
			return null;
		}
		synchronized (cookieLock) {
			JSONObject jsonObject = obtainCookiesLocked().optJSONObject(cookie);
			if (jsonObject != null && !jsonObject.optBoolean(KEY_COOKIE_BLOCKED, false)) {
				return jsonObject.optString(KEY_COOKIE_VALUE, null);
			}
			return null;
		}
	}

	private final Object cookieLock = new Object();

	@Public
	public final void storeCookie(String cookie, String value, String displayName) {
		if (cookie == null) {
			throw new NullPointerException("cookie must not be null");
		}
		synchronized (cookieLock) {
			JSONObject cookiesObject = obtainCookiesLocked();
			JSONObject jsonObject = cookiesObject.optJSONObject(cookie);
			if (!(jsonObject == null && value == null)) {
				if (jsonObject == null) {
					jsonObject = new JSONObject();
				}
				boolean blocked = jsonObject.optBoolean(KEY_COOKIE_BLOCKED, false);
				if (value != null || blocked) {
					try {
						if (!blocked) {
							if (value != null) {
								jsonObject.put(KEY_COOKIE_VALUE, value);
							} else {
								jsonObject.remove(KEY_COOKIE_VALUE);
							}
						}
						if (!StringUtils.isEmptyOrWhitespace(displayName)) {
							jsonObject.put(KEY_COOKIE_DISPLAY_NAME, displayName);
						}
						cookiesObject.put(cookie, jsonObject);
					} catch (JSONException e) {
						throw new RuntimeException(e);
					}
				} else {
					cookiesObject.remove(cookie);
				}
				set(null, KEY_COOKIES, cookiesObject.toString());
			}
		}
	}

	public final boolean setCookieBlocked(String cookie, boolean blocked) {
		synchronized (cookieLock) {
			JSONObject cookiesObject = obtainCookiesLocked();
			JSONObject jsonObject = cookiesObject.optJSONObject(cookie);
			if (jsonObject == null) {
				jsonObject = new JSONObject();
			}
			try {
				boolean removed = false;
				if (blocked) {
					jsonObject.put(KEY_COOKIE_BLOCKED, true);
				} else {
					jsonObject.remove(KEY_COOKIE_BLOCKED);
					if (jsonObject.optString(KEY_COOKIE_VALUE, null) != null) {
						cookiesObject.put(cookie, jsonObject);
					} else {
						cookiesObject.remove(cookie);
						removed = true;
					}
				}
				set(null, KEY_COOKIES, cookiesObject.toString());
				return removed;
			} catch (JSONException e) {
				throw new RuntimeException(e);
			}
		}
	}

	public final boolean hasCookies() {
		synchronized (cookieLock) {
			return obtainCookiesLocked().length() > 0;
		}
	}

	public static class CookieData {
		public final String cookie;
		public final String value;
		public final String displayName;
		public final boolean blocked;

		public CookieData(String cookie, String value, String displayName, boolean blocked) {
			this.cookie = cookie;
			this.value = value;
			this.displayName = displayName;
			this.blocked = blocked;
		}
	}

	public final ArrayList<CookieData> getCookies() {
		ArrayList<CookieData> result = new ArrayList<>();
		synchronized (cookieLock) {
			JSONObject cookiesObject = obtainCookiesLocked();
			for (Iterator<String> iterator = cookiesObject.keys(); iterator.hasNext();) {
				String cookie = iterator.next();
				JSONObject jsonObject = cookiesObject.optJSONObject(cookie);
				String value = jsonObject != null ? jsonObject.optString(KEY_COOKIE_VALUE, "") : "";
				String displayName = jsonObject != null ? jsonObject.optString(KEY_COOKIE_DISPLAY_NAME, null) : null;
				boolean blocked = jsonObject != null && jsonObject.optBoolean(KEY_COOKIE_BLOCKED, false);
				if (StringUtils.isEmptyOrWhitespace(displayName)) {
					displayName = cookie;
				}
				result.add(new CookieData(cookie, value, displayName, blocked));
			}
		}
		return result;
	}

	@Public
	public final String[] getUserAuthorizationData() {
		return Preferences.getUserAuthorizationData(getChanName());
	}

	@Public
	public final File getDownloadDirectory() {
		return Preferences.getDownloadDirectory();
	}

	public final void updateFromBoards(BoardCategory[] boardCategories) {
		for (BoardCategory boardCategory : boardCategories) {
			updateFromBoards(boardCategory.getBoards());
		}
	}

	public final void updateFromBoards(chan.content.model.Board[] boards) {
		for (chan.content.model.Board board : boards) {
			String boardName = board.getBoardName();
			String title = board.getTitle();
			String description = board.getDescription();
			storeBoardTitle(boardName, title);
			storeBoardDescription(boardName, description);
		}
	}

	public static final class Safe {
		private static final Board DEFAULT_BOARD = new Board();
		private static final Captcha DEFAULT_CAPTCHA = new Captcha();
		private static final Authorization DEFAULT_AUTHORIZATION = new Authorization();

		static {
			DEFAULT_CAPTCHA.validity = Captcha.Validity.LONG_LIFETIME;
			DEFAULT_CAPTCHA.input = Captcha.Input.ALL;
		}

		private final ChanConfiguration configuration;

		private Safe(ChanConfiguration configuration) {
			this.configuration = configuration;
		}

		public Board obtainBoard(String boardName) {
			Board board = null;
			try {
				board = configuration.obtainBoardConfiguration(boardName);
			} catch (LinkageError | RuntimeException e) {
				ExtensionException.logException(e, false);
			}
			if (board == null) {
				board = DEFAULT_BOARD;
			}
			return board;
		}

		public Captcha obtainCaptcha(String captchaType) {
			Captcha captcha = configuration.obtainCaptchaConfigurationSafe(captchaType);
			if (captcha == null) {
				captcha = DEFAULT_CAPTCHA;
			}
			return captcha;
		}

		public Posting obtainPosting(String boardName, boolean newThread) {
			Posting posting = null;
			try {
				posting = configuration.obtainPostingConfiguration(boardName, newThread);
			} catch (LinkageError | RuntimeException e) {
				ExtensionException.logException(e, false);
			}
			return posting;
		}

		public Deleting obtainDeleting(String boardName) {
			Deleting deleting = null;
			try {
				deleting = configuration.obtainDeletingConfiguration(boardName);
			} catch (LinkageError | RuntimeException e) {
				ExtensionException.logException(e, false);
			}
			return deleting;
		}

		public Reporting obtainReporting(String boardName) {
			Reporting reporting = null;
			try {
				reporting = configuration.obtainReportingConfiguration(boardName);
			} catch (LinkageError | RuntimeException e) {
				ExtensionException.logException(e, false);
			}
			return reporting;
		}

		public Authorization obtainCaptchaPass() {
			Authorization authorization = null;
			try {
				authorization = configuration.obtainCaptchaPassConfiguration();
			} catch (LinkageError | RuntimeException e) {
				ExtensionException.logException(e, false);
			}
			if (authorization == null) {
				authorization = DEFAULT_AUTHORIZATION;
			}
			return authorization;
		}

		public Authorization obtainUserAuthorization() {
			Authorization authorization = null;
			try {
				authorization = configuration.obtainUserAuthorizationConfiguration();
			} catch (LinkageError | RuntimeException e) {
				ExtensionException.logException(e, false);
			}
			if (authorization == null) {
				authorization = DEFAULT_AUTHORIZATION;
			}
			return authorization;
		}

		public Archivation obtainArchivation() {
			Archivation archivation = null;
			try {
				archivation = configuration.obtainArchivationConfiguration();
			} catch (LinkageError | RuntimeException e) {
				ExtensionException.logException(e, false);
			}
			return archivation;
		}

		public Statistics obtainStatistics() {
			Statistics statistics = null;
			try {
				statistics = configuration.obtainStatisticsConfiguration();
			} catch (LinkageError | RuntimeException e) {
				ExtensionException.logException(e, false);
			}
			return statistics;
		}

		public CustomPreference obtainCustomPreference(String key) {
			CustomPreference customPreference = null;
			try {
				customPreference = configuration.obtainCustomPreferenceConfiguration(key);
			} catch (LinkageError | RuntimeException e) {
				ExtensionException.logException(e, false);
			}
			return customPreference;
		}
	}

	private final Safe safe = new Safe(this);

	public final Safe safe() {
		return safe;
	}
}
