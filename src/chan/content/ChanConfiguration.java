package chan.content;

import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import android.os.CancellationSignal;
import android.util.Pair;
import android.util.SparseArray;
import chan.annotation.Extendable;
import chan.annotation.Public;
import chan.content.model.BoardCategory;
import chan.util.CommonUtils;
import chan.util.DataFile;
import chan.util.StringUtils;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.MainApplication;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.database.ChanDatabase;
import com.mishiranu.dashchan.util.IOUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Extendable
public class ChanConfiguration implements Chan.Linked {
	private final Chan.Provider chanProvider;
	private final Resources resources;
	private final HashMap<ChanDatabase.DataKey, Object> editData;

	private boolean isInitialized = false;

	static final ChanManager.Initializer INITIALIZER = new ChanManager.Initializer();

	@Public
	public ChanConfiguration() {
		this(null);
	}

	ChanConfiguration(Chan.Provider chanProvider) {
		if (chanProvider == null) {
			ChanManager.Initializer.Holder holder = INITIALIZER.consume();
			this.chanProvider = holder.chanProvider;
			resources = holder.resources;
			editData = new HashMap<>();
		} else {
			this.chanProvider = chanProvider;
			resources = null;
			editData = null;
		}
	}

	@Override
	public final void init() {
		isInitialized = true;
	}

	@Override
	public final Chan get() {
		return chanProvider.get();
	}

	public static final String SCHEME_CHAN = "chan";

	@Public public static final String OPTION_SINGLE_BOARD_MODE = "single_board_mode";
	@Public public static final String OPTION_READ_THREAD_PARTIALLY = "read_thread_partially";
	@Public public static final String OPTION_READ_SINGLE_POST = "read_single_post";
	@Public public static final String OPTION_READ_POSTS_COUNT = "read_posts_count";
	@Public public static final String OPTION_READ_USER_BOARDS = "read_user_boards";
	@Public public static final String OPTION_ALLOW_CAPTCHA_PASS = "allow_captcha_pass";
	@Public public static final String OPTION_ALLOW_USER_AUTHORIZATION = "allow_user_authorization";
	@Public public static final String OPTION_LOCAL_MODE = "local_mode";

	private static final String KEY_TITLE = "title";
	private static final String KEY_DESCRIPTION = "description";
	private static final String KEY_DEFAULT_NAME = "default_name";
	private static final String KEY_BUMP_LIMIT = "bump_limit";
	private static final String KEY_PAGES_COUNT = "pages_count";

	public static final int PAGES_COUNT_INVALID = Integer.MAX_VALUE;
	public static final int BUMP_LIMIT_INVALID = Integer.MAX_VALUE;

	// TODO CHAN
	// Remove this field after updating
	// allchan sevenchan tumbach
	// Added: 28.07.20 22:23
	@Public public static final String CAPTCHA_TYPE_RECAPTCHA_1 = "recaptcha_1";
	@Public public static final String CAPTCHA_TYPE_RECAPTCHA_2 = "recaptcha_2";
	@Public public static final String CAPTCHA_TYPE_RECAPTCHA_2_INVISIBLE = "recaptcha_2_invisible";
	@Public public static final String CAPTCHA_TYPE_HCAPTCHA = "hcaptcha";

	@Public
	public static ChanConfiguration get(Object object) {
		return ((Chan.Linked) object).get().configuration;
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
		@Public public boolean queryOnly = false;

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

	public final void commit() {
		if (editData != null) {
			synchronized (editData) {
				ChanDatabase.getInstance().setData(get().name, editData);
				editData.clear();
			}
		}
	}

	@Public
	public final boolean get(String boardName, String key, boolean defaultValue) {
		if (editData == null) {
			return defaultValue;
		}
		ChanDatabase.DataKey dataKey = new ChanDatabase.DataKey(boardName, key);
		synchronized (editData) {
			Object result = editData.get(dataKey);
			if (result != null) {
				return result instanceof Boolean ? (boolean) result : defaultValue;
			}
		}
		String value = ChanDatabase.getInstance().getData(get().name, dataKey.boardName, dataKey.name);
		return value != null ? !"0".equals(value) : defaultValue;
	}

	@Public
	public final int get(String boardName, String key, int defaultValue) {
		if (editData == null) {
			return defaultValue;
		}
		ChanDatabase.DataKey dataKey = new ChanDatabase.DataKey(boardName, key);
		synchronized (editData) {
			Object result = editData.get(dataKey);
			if (result != null) {
				return result instanceof Integer ? (int) result : defaultValue;
			}
		}
		String value = ChanDatabase.getInstance().getData(get().name, dataKey.boardName, dataKey.name);
		try {
			return Integer.parseInt(value);
		} catch (Exception e) {
			return defaultValue;
		}
	}

	@Public
	public final String get(String boardName, String key, String defaultValue) {
		if (editData == null) {
			return defaultValue;
		}
		ChanDatabase.DataKey dataKey = new ChanDatabase.DataKey(boardName, key);
		synchronized (editData) {
			Object result = editData.get(dataKey);
			if (result != null) {
				return result instanceof String ? (String) result : defaultValue;
			}
		}
		String value = ChanDatabase.getInstance().getData(get().name, dataKey.boardName, dataKey.name);
		return value != null ? value : defaultValue;
	}

	private void set(String boardName, String key, Object value) {
		if (editData != null) {
			ChanDatabase.DataKey dataKey = new ChanDatabase.DataKey(boardName, key);
			synchronized (editData) {
				editData.put(dataKey, value);
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
		if (get().name != null) {
			if (title == null) {
				String title = null;
				ArrayList<String> hosts = get().locator.getChanHosts(false);
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
		if (isInitialized) {
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

	private HashMap<String, String> boardTitlesMap;
	private HashMap<String, String> boardDescriptionsMap;

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

	private final ChanDatabase.BoardExtraFallbackProvider titleFallbackProvider =
			boardName -> boardTitlesMap != null ? boardTitlesMap.get(boardName) : null;
	private final ChanDatabase.BoardExtraFallbackProvider descriptionFallbackProvider =
			boardName -> boardDescriptionsMap != null ? boardDescriptionsMap.get(boardName) : null;

	public final String getBoardTitle(String boardName) {
		String title = titleFallbackProvider.getExtra(boardName);
		return title != null ? title : get(boardName, KEY_TITLE, null);
	}

	public final String getBoardDescription(String boardName) {
		String description = descriptionFallbackProvider.getExtra(boardName);
		return description != null ? description : get(boardName, KEY_DESCRIPTION, null);
	}

	public final ChanDatabase.BoardCursor getBoards(String searchQuery, CancellationSignal signal) {
		return ChanDatabase.getInstance().getBoards(get().name, searchQuery, KEY_TITLE, titleFallbackProvider, signal);
	}

	public final ChanDatabase.BoardCursor getUserBoards(List<String> boardNames,
			String searchQuery, CancellationSignal signal) {
		return ChanDatabase.getInstance().getBoards(get().name, boardNames, searchQuery,
				KEY_TITLE, KEY_DESCRIPTION, titleFallbackProvider, descriptionFallbackProvider, signal);
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
		return Preferences.getCaptchaTypeForChan(get());
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
		authorization.hints = new String[] {MainApplication.getInstance().getString(R.string.password)};
		return authorization;
	}

	@Extendable
	protected Authorization obtainUserAuthorizationConfiguration() {
		Authorization authorization = new Authorization();
		authorization.fieldsCount = 1;
		authorization.hints = new String[] {MainApplication.getInstance().getString(R.string.password)};
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

	private final SparseArray<Uri> resourceUris = new SparseArray<>();

	@Public
	public final Uri getResourceUri(int resId) {
		Uri uri;
		synchronized (resourceUris) {
			uri = resourceUris.get(resId);
		}
		if (uri == null) {
			String packageName = resources.getResourcePackageName(resId);
			if (get().packageName.equals(packageName)) {
				String type = resources.getResourceTypeName(resId);
				String name = resources.getResourceEntryName(resId);
				uri = Uri.parse(SCHEME_CHAN + ":///res/" + type + "/" + name);
				if (uri != null) {
					synchronized (resourceUris) {
						resourceUris.put(resId, uri);
					}
				}
			}
		}
		return uri;
	}

	public final boolean readResourceUri(Uri uri, OutputStream output) throws IOException {
		Chan chan = get();
		if (chan.name == null) {
			return false;
		}
		String chanName = uri.getAuthority();
		if (!StringUtils.isEmpty(chanName) && !chanName.equals(chan.name)) {
			return false;
		}
		List<String> pathSegments = uri.getPathSegments();
		if (pathSegments == null || pathSegments.size() != 3 || !"res".equals(pathSegments.get(0))) {
			return false;
		}
		String type = pathSegments.get(1);
		String name = pathSegments.get(2);
		int id = resources.getIdentifier(name, type, chan.packageName);
		if (id == 0) {
			return false;
		}
		try (InputStream input = resources.openRawResource(id)) {
			IOUtils.copyStream(input, output);
			return true;
		}
	}

	@Public
	public final String getCookie(String cookie) {
		return editData == null || cookie == null ? null
				: ChanDatabase.getInstance().getCookieChecked(get().name, cookie);
	}

	@Public
	public final void storeCookie(String cookie, String value, String displayName) {
		if (editData != null) {
			if (cookie == null) {
				throw new NullPointerException("Сookie must not be null");
			}
			ChanDatabase.getInstance().setCookie(get().name, cookie, value,
					StringUtils.isEmptyOrWhitespace(displayName) ? null : displayName);
		}
	}

	@Public
	public final String[] getUserAuthorizationData() {
		return CommonUtils.toArray(Preferences.getUserAuthorizationData(get()), String.class);
	}

	@Public
	public final DataFile getDownloadDirectory() {
		return DataFile.obtain(DataFile.Target.DOWNLOADS, null);
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
