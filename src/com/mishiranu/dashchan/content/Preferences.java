package com.mishiranu.dashchan.content;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import androidx.annotation.RequiresApi;
import chan.content.ChanConfiguration;
import chan.content.ChanManager;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.storage.StatisticsStorage;
import com.mishiranu.dashchan.util.ToastUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;

public class Preferences {
	public static final SharedPreferences PREFERENCES = MainApplication.getInstance().getSharedPreferences
			(MainApplication.getInstance().getPackageName() + "_preferences", Context.MODE_PRIVATE);

	private static final String SPECIAL_CHAN_NAME_GENERAL = "general";
	private static final String SPECIAL_CHAN_NAME_CLOUDFLARE = "cloudflare";

	public static final String[] SPECIAL_EXTENSION_NAMES = {
			SPECIAL_CHAN_NAME_GENERAL,
			SPECIAL_CHAN_NAME_CLOUDFLARE
	};

	public static class ChanKey {
		private final String key;

		private ChanKey(String key) {
			this.key = key;
		}

		public String bind(String chanName) {
			return String.format(key, chanName);
		}
	}

	public static File getPreferencesFile() {
		Context context = MainApplication.getInstance();
		String name = context.getPackageName() + "_preferences";
		return new File(new File(context.getCacheDir().getParentFile(), "shared_prefs"), name + ".xml");
	}

	public static String[] unpackOrCastMultipleValues(String value, int count) {
		String[] values = new String[count];
		if (value != null) {
			try {
				JSONArray jsonArray = new JSONArray(value);
				int length = Math.min(count, jsonArray.length());
				for (int i = 0; i < length; i++) {
					values[i] = jsonArray.isNull(i) ? null : StringUtils.nullIfEmpty(jsonArray.getString(i));
				}
				return values;
			} catch (JSONException e) {
				// Backward compatibility
				if (value.length() > 0 && !value.startsWith("[")) {
					values[0] = value;
				}
			}
		}
		return values;
	}

	public static boolean checkHasMultipleValues(String[] values) {
		boolean hasValues = false;
		if (values != null) {
			for (String value : values) {
				if (value != null) {
					hasValues = true;
					break;
				}
			}
		}
		return hasValues;
	}

	static {
		String statistics = PREFERENCES.getString("statistics", null);
		if (statistics != null) {
			StatisticsStorage.getInstance().convertFromOldFormat(statistics);
			PREFERENCES.edit().remove("statistics").commit();
		}
	}

	private static final String GENERIC_VALUE_NETWORK_ALWAYS = "always";
	private static final String GENERIC_VALUE_NETWORK_WIFI_3G = "wifi_3g";
	private static final String GENERIC_VALUE_NETWORK_WIFI = "wifi";
	private static final String GENERIC_VALUE_NETWORK_NEVER = "never";
	public static final String[] GENERIC_VALUES_NETWORK = {
			GENERIC_VALUE_NETWORK_ALWAYS,
			GENERIC_VALUE_NETWORK_WIFI_3G,
			GENERIC_VALUE_NETWORK_WIFI,
			GENERIC_VALUE_NETWORK_NEVER
	};
	public static final int[] GENERIC_ENTRIES_NETWORK = {
			R.string.always,
			R.string.wifi_or_3g_plus,
			R.string.wifi_only,
			R.string.never
	};

	private static boolean isNetworkAvailable(String value, String defaultValue) {
		switch (StringUtils.emptyIfNull(value)) {
			case GENERIC_VALUE_NETWORK_ALWAYS: {
				return true;
			}
			case GENERIC_VALUE_NETWORK_WIFI_3G: {
				return NetworkObserver.getInstance().isMobile3GConnected();
			}
			case GENERIC_VALUE_NETWORK_WIFI: {
				return NetworkObserver.getInstance().isWifiConnected();
			}
			case GENERIC_VALUE_NETWORK_NEVER: {
				return false;
			}
		}
		return defaultValue != null && isNetworkAvailable(defaultValue, null);
	}

	public static final String KEY_ACTIVE_SCROLLBAR = "active_scrollbar";
	public static final boolean DEFAULT_ACTIVE_SCROLLBAR = true;

	public static boolean isActiveScrollbar() {
		return PREFERENCES.getBoolean(KEY_ACTIVE_SCROLLBAR, DEFAULT_ACTIVE_SCROLLBAR);
	}

	public static final String KEY_ADVANCED_SEARCH = "advanced_search";
	public static final boolean DEFAULT_ADVANCED_SEARCH = false;

	public static boolean isAdvancedSearch() {
		return PREFERENCES.getBoolean(KEY_ADVANCED_SEARCH, DEFAULT_ADVANCED_SEARCH);
	}

	public static final String KEY_ALL_ATTACHMENTS = "all_attachments";
	public static final boolean DEFAULT_ALL_ATTACHMENTS = false;

	public static boolean isAllAttachments() {
		return PREFERENCES.getBoolean(KEY_ALL_ATTACHMENTS, DEFAULT_ALL_ATTACHMENTS);
	}

	public static final String KEY_AUTO_REFRESH_MODE = "auto_refresh_mode";
	public static final String VALUE_AUTO_REFRESH_MODE_SEPARATE = "separate";
	public static final String VALUE_AUTO_REFRESH_MODE_DISABLED = "disabled";
	public static final String VALUE_AUTO_REFRESH_MODE_ENABLED = "enabled";
	public static final String[] VALUES_AUTO_REFRESH_MODE = {
			VALUE_AUTO_REFRESH_MODE_SEPARATE,
			VALUE_AUTO_REFRESH_MODE_DISABLED,
			VALUE_AUTO_REFRESH_MODE_ENABLED
	};
	public static final int[] ENTRIES_AUTO_REFRESH_MODE = {
			R.string.separate__adjective,
			R.string.always_disabled,
			R.string.always_enabled
	};
	public static final String DEFAULT_AUTO_REFRESH_MODE = VALUE_AUTO_REFRESH_MODE_SEPARATE;

	public static final int AUTO_REFRESH_MODE_SEPARATE = 0;
	public static final int AUTO_REFRESH_MODE_DISABLED = 1;
	public static final int AUTO_REFRESH_MODE_ENABLED = 2;

	public static int getAutoRefreshMode() {
		String value = PREFERENCES.getString(KEY_AUTO_REFRESH_MODE, DEFAULT_AUTO_REFRESH_MODE);
		if (VALUE_AUTO_REFRESH_MODE_SEPARATE.equals(value)) {
			return AUTO_REFRESH_MODE_SEPARATE;
		}
		if (VALUE_AUTO_REFRESH_MODE_DISABLED.equals(value)) {
			return AUTO_REFRESH_MODE_DISABLED;
		}
		if (VALUE_AUTO_REFRESH_MODE_ENABLED.equals(value)) {
			return AUTO_REFRESH_MODE_ENABLED;
		}
		return AUTO_REFRESH_MODE_SEPARATE;
	}

	public static final String KEY_AUTO_REFRESH_INTERVAL = "auto_refresh_interval";
	public static final int DEFAULT_AUTO_REFRESH_INTERVAL = 30;
	public static final int MIN_AUTO_REFRESH_INTERVAL = 15;
	public static final int MAX_AUTO_REFRESH_INTERVAL = 120;
	public static final int STEP_AUTO_REFRESH_INTERVAL = 5;

	public static int getAutoRefreshInterval() {
		return PREFERENCES.getInt(KEY_AUTO_REFRESH_INTERVAL, DEFAULT_AUTO_REFRESH_INTERVAL);
	}

	public static final String KEY_CACHE_SIZE = "cache_size";
	public static final int DEFAULT_CACHE_SIZE = 100;
	public static final float MULTIPLIER_CACHE_SIZE = 1.2f;

	public static int getCacheSize() {
		return (int) (PREFERENCES.getInt(KEY_CACHE_SIZE, DEFAULT_CACHE_SIZE) * MULTIPLIER_CACHE_SIZE);
	}

	public static final ChanKey KEY_CAPTCHA = new ChanKey("%s_captcha");
	private static final String VALUE_CAPTCHA_START = "captcha_";

	public static String getCaptchaTypeForChanConfiguration(String chanName) {
		ChanConfiguration configuration = ChanConfiguration.get(chanName);
		Collection<String> supportedCaptchaTypes = configuration.getSupportedCaptchaTypes();
		if (supportedCaptchaTypes == null || supportedCaptchaTypes.isEmpty()) {
			return null;
		}
		String defaultCaptchaType = supportedCaptchaTypes.iterator().next();
		String captchaTypeValue = PREFERENCES.getString(KEY_CAPTCHA.bind(chanName),
				transformCaptchaTypeToValue(defaultCaptchaType));
		String captchaType;
		if (captchaTypeValue != null && captchaTypeValue.startsWith(VALUE_CAPTCHA_START)) {
			captchaType = captchaTypeValue.substring(VALUE_CAPTCHA_START.length());
			for (String supportedCaptchaType : supportedCaptchaTypes) {
				if (supportedCaptchaType.equals(captchaType)) {
					return captchaType;
				}
			}
		}
		return defaultCaptchaType;
	}

	public static String[] getCaptchaTypeValues(Collection<String> captchaTypes) {
		int i = 0;
		String[] values = new String[captchaTypes.size()];
		for (String captchaType : captchaTypes) {
			values[i++] = transformCaptchaTypeToValue(captchaType);
		}
		return values;
	}

	public static String[] getCaptchaTypeEntries(String chanName, Collection<String> captchaTypes) {
		int i = 0;
		ChanConfiguration configuration = ChanConfiguration.get(chanName);
		String[] entries = new String[captchaTypes.size()];
		for (String captchaType : captchaTypes) {
			entries[i++] = configuration.safe().obtainCaptcha(captchaType).title;
		}
		return entries;
	}

	public static String getCaptchaTypeDefaultValue(String chanName) {
		Collection<String> supportedCaptchaTypes = ChanConfiguration.get(chanName).getSupportedCaptchaTypes();
		if (supportedCaptchaTypes == null || supportedCaptchaTypes.isEmpty()) {
			return null;
		}
		return transformCaptchaTypeToValue(supportedCaptchaTypes.iterator().next());
	}

	private static String transformCaptchaTypeToValue(String captchaType) {
		return VALUE_CAPTCHA_START + captchaType;
	}

	public static final ChanKey KEY_CAPTCHA_PASS = new ChanKey("%s_captcha_pass");

	public static String[] getCaptchaPass(String chanName) {
		ChanConfiguration.Authorization authorization = ChanConfiguration.get(chanName).safe().obtainCaptchaPass();
		if (authorization != null && authorization.fieldsCount > 0) {
			String value = PREFERENCES.getString(KEY_CAPTCHA_PASS.bind(chanName), null);
			return unpackOrCastMultipleValues(value, authorization.fieldsCount);
		} else {
			return null;
		}
	}

	public static final String KEY_CHANS_ORDER = "chans_order";

	public static ArrayList<String> getChansOrder() {
		String data = PREFERENCES.getString(KEY_CHANS_ORDER, null);
		if (data != null) {
			try {
				JSONArray jsonArray = new JSONArray(data);
				ArrayList<String> chanNames = new ArrayList<>();
				for (int i = 0; i < jsonArray.length(); i++) {
					chanNames.add(jsonArray.getString(i));
				}
				return chanNames;
			} catch (JSONException e) {
				// Invalid or unspecified data, ignore exception
			}
		}
		return null;
	}

	public static void setChansOrder(List<String> chanNames) {
		JSONArray jsonArray = new JSONArray();
		for (String chanName : chanNames) {
			jsonArray.put(chanName);
		}
		PREFERENCES.edit().putString(KEY_CHANS_ORDER, jsonArray.toString()).commit();
	}

	public static final String KEY_CHECK_UPDATES_ON_START = "check_updates_on_start";
	public static final boolean DEFAULT_CHECK_UPDATES_ON_START = true;

	public static boolean isCheckUpdatesOnStart() {
		return PREFERENCES.getBoolean(KEY_CHECK_UPDATES_ON_START, DEFAULT_CHECK_UPDATES_ON_START);
	}

	public static void setCheckUpdatesOnStart(boolean checkUpdatesOnStart) {
		PREFERENCES.edit().putBoolean(KEY_CHECK_UPDATES_ON_START, checkUpdatesOnStart).commit();
	}

	public static final String KEY_CLOSE_ON_BACK = "close_on_back";
	public static final boolean DEFAULT_CLOSE_ON_BACK = false;

	public static boolean isCloseOnBack() {
		return PREFERENCES.getBoolean(KEY_CLOSE_ON_BACK, DEFAULT_CLOSE_ON_BACK);
	}

	public static final String KEY_CUT_THUMBNAILS = "cut_thumbnails";
	public static final boolean DEFAULT_CUT_THUMBNAILS = true;

	public static boolean isCutThumbnails() {
		return PREFERENCES.getBoolean(KEY_CUT_THUMBNAILS, DEFAULT_CUT_THUMBNAILS);
	}

	public static final ChanKey KEY_DEFAULT_BOARD_NAME = new ChanKey("%s_default_board_name");

	public static String getDefaultBoardName(String chanName) {
		ChanConfiguration configuration = ChanConfiguration.get(chanName);
		return configuration.getOption(ChanConfiguration.OPTION_SINGLE_BOARD_MODE) ? configuration.getSingleBoardName()
				: StringUtils.validateBoardName(PREFERENCES.getString(KEY_DEFAULT_BOARD_NAME.bind(chanName), null));
	}

	public static void setDefaultBoardName(String chanName, String boardName) {
		PREFERENCES.edit().putString(KEY_DEFAULT_BOARD_NAME.bind(chanName), boardName).commit();
	}

	public static final String KEY_SUBDIR_PATTERN = "subdir_pattern";
	public static final String DEFAULT_SUBDIR_PATTERN = "\\c-<\\b->\\t";

	public static String getSubdir(String chanName, String chanTitle, String boardName,
			String threadNumber, String threadTitle) {
		if (threadNumber == null) {
			return null;
		}
		String pattern = StringUtils.emptyIfNull(PREFERENCES.getString(KEY_SUBDIR_PATTERN, DEFAULT_SUBDIR_PATTERN));
		return formatSubdir(pattern, chanName, chanTitle, boardName, threadNumber, threadTitle);
	}

	public static String formatSubdir(String pattern, String chanName, String chanTitle, String boardName,
			String threadNumber, String threadTitle) {
		StringBuilder builder = new StringBuilder(pattern);
		for (int i = builder.length() - 2; i >= 0; i--) {
			char c = builder.charAt(i);
			if (c == '\\') {
				char cn = builder.charAt(i + 1);
				boolean replace = false;
				String replaceTo = null;
				switch (cn) {
					case 'c': {
						replace = true;
						replaceTo = chanName;
						break;
					}
					case 'd': {
						replace = true;
						replaceTo = chanTitle;
						break;
					}
					case 'b': {
						replace = true;
						replaceTo = boardName;
						break;
					}
					case 't': {
						replace = true;
						replaceTo = threadNumber;
						break;
					}
					case 'e': {
						replace = true;
						replaceTo = threadTitle;
						break;
					}
				}
				if (!replace) {
					continue;
				}
				replaceTo = StringUtils.nullIfEmpty(replaceTo);
				if (replaceTo == null) {
					int optStart = -1;
					int optEnd = -1;
					for (int j = i - 1; j >= 0; j--) {
						char cj = builder.charAt(j);
						if (cj == '<') {
							optStart = j;
							break;
						} else if (cj == '\\') {
							break;
						}
					}
					for (int j = i + 2; j < builder.length(); j++) {
						char cj = builder.charAt(j);
						if (cj == '>') {
							optEnd = j + 1;
							break;
						} else if (cj == '\\') {
							break;
						}
					}
					if (optEnd > optStart && optStart >= 0) {
						builder.replace(optStart, optEnd, "");
						i = optStart - 1;
					} else {
						replaceTo = "null";
					}
				}
				if (replaceTo != null) {
					builder.replace(i, i + 2, replaceTo);
				}
			}
		}
		return builder.toString().replaceAll("[:\\\\*?|]", "_").replaceAll("[<>]", "");
	}

	public static final String KEY_DISPLAY_HIDDEN_THREADS = "display_hidden_threads";
	public static final boolean DEFAULT_DISPLAY_HIDDEN_THREADS = true;

	public static boolean isDisplayHiddenThreads() {
		return PREFERENCES.getBoolean(KEY_DISPLAY_HIDDEN_THREADS, DEFAULT_DISPLAY_HIDDEN_THREADS);
	}

	public static final String KEY_DISPLAY_ICONS = "display_icons";
	public static final boolean DEFAULT_DISPLAY_ICONS = true;

	public static boolean isDisplayIcons() {
		return PREFERENCES.getBoolean(KEY_DISPLAY_ICONS, DEFAULT_DISPLAY_ICONS);
	}

	public static final ChanKey KEY_DOMAIN = new ChanKey("%s_domain");

	public static String getDomainUnhandled(String chanName) {
		return PREFERENCES.getString(KEY_DOMAIN.bind(chanName), "");
	}

	public static void setDomainUnhandled(String chanName, String domain) {
		PREFERENCES.edit().putString(KEY_DOMAIN.bind(chanName), domain).commit();
	}

	public static final String KEY_DOWNLOAD_DETAIL_NAME = "download_detail_name";
	public static final boolean DEFAULT_DOWNLOAD_DETAIL_NAME = false;

	public static boolean isDownloadDetailName() {
		return PREFERENCES.getBoolean(KEY_DOWNLOAD_DETAIL_NAME, DEFAULT_DOWNLOAD_DETAIL_NAME);
	}

	public static final String KEY_DOWNLOAD_ORIGINAL_NAME = "download_original_name";
	public static final boolean DEFAULT_DOWNLOAD_ORIGINAL_NAME = false;

	public static boolean isDownloadOriginalName() {
		return PREFERENCES.getBoolean(KEY_DOWNLOAD_ORIGINAL_NAME, DEFAULT_DOWNLOAD_ORIGINAL_NAME);
	}

	public static final String KEY_DOWNLOAD_PATH = "download_path";

	private static String getDownloadPathLegacy() {
		String path = PREFERENCES.getString(KEY_DOWNLOAD_PATH, null);
		return !StringUtils.isEmptyOrWhitespace(path) ? path : C.DEFAULT_DOWNLOAD_PATH;
	}

	private static File externalStorageDirectory;

	@SuppressWarnings("deprecation")
	public static File getDownloadDirectoryLegacy() {
		String path = getDownloadPathLegacy();
		File dir = new File(path);
		boolean absolute = false;
		Uri uri = Uri.fromFile(dir);
		List<String> pathSegments = uri.getPathSegments();
		if (pathSegments.size() > 0) {
			File first = new File("/" + uri.getPathSegments().get(0));
			if (first.exists() && first.isDirectory()) {
				absolute = true;
			}
		}
		if (!absolute) {
			File file = externalStorageDirectory;
			if (file == null) {
				// Cache for faster calls
				file = Environment.getExternalStorageDirectory();
				externalStorageDirectory = file;
			}
			dir = new File(file, path);
		}
		dir.mkdirs();
		return dir;
	}

	@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
	public static Uri getDownloadUriTree(Context context) {
		ContentResolver contentResolver = context.getContentResolver();
		List<UriPermission> uriPermissions = contentResolver.getPersistedUriPermissions();
		if (uriPermissions == null) {
			return null;
		}
		for (UriPermission uriPermission : uriPermissions) {
			if (uriPermission.isReadPermission() && uriPermission.isWritePermission()) {
				Uri treeUri = uriPermission.getUri();
				Uri uri = DocumentsContract.buildDocumentUriUsingTree(treeUri,
						DocumentsContract.getTreeDocumentId(treeUri));
				Cursor cursor = contentResolver.query(uri, null, null, null, null);
				if (cursor == null) {
					return null;
				}
				try {
					return cursor.moveToFirst() ? treeUri : null;
				} finally {
					cursor.close();
				}
			}
		}
		return null;
	}

	@RequiresApi(Build.VERSION_CODES.KITKAT)
	public static void setDownloadUriTree(Context context, Uri uri, int uriFlags) {
		ContentResolver contentResolver = context.getContentResolver();
		for (UriPermission uriPermission : contentResolver.getPersistedUriPermissions()) {
			if (uri == null || !uri.equals(uriPermission.getUri())) {
				int flags = (uriPermission.isReadPermission() ? Intent.FLAG_GRANT_READ_URI_PERMISSION : 0) |
						(uriPermission.isWritePermission() ? Intent.FLAG_GRANT_WRITE_URI_PERMISSION : 0);
				contentResolver.releasePersistableUriPermission(uriPermission.getUri(), flags);
			}
		}
		if (uri == null || "com.android.providers.downloads.documents".equals(uri.getAuthority())) {
			// Downloads provider fails when ".nomedia" files present
			ToastUtils.show(context, R.string.no_access_to_memory);
		} else {
			contentResolver.takePersistableUriPermission(uri, uriFlags &
					(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
		}
	}

	public static final String KEY_DOWNLOAD_SUBDIR = "download_subdir";
	public static final String VALUE_DOWNLOAD_SUBDIR_DISABLED = "disabled";
	public static final String VALUE_DOWNLOAD_SUBDIR_MULTIPLE = "multiple_only";
	public static final String VALUE_DOWNLOAD_SUBDIR_ENABLED = "enabled";
	public static final String[] VALUES_DOWNLOAD_SUBDIR = {
			VALUE_DOWNLOAD_SUBDIR_DISABLED,
			VALUE_DOWNLOAD_SUBDIR_MULTIPLE,
			VALUE_DOWNLOAD_SUBDIR_ENABLED
	};
	public static final int[] ENTRIES_DOWNLOAD_SUBDIR = {
			R.string.never,
			R.string.on_multiple_downloading,
			R.string.always
	};
	public static final String DEFAULT_DOWNLOAD_SUBDIR = VALUE_DOWNLOAD_SUBDIR_DISABLED;

	@SuppressWarnings("RedundantIfStatement")
	public static boolean isDownloadSubdir(boolean multiple) {
		String value = PREFERENCES.getString(KEY_DOWNLOAD_SUBDIR, DEFAULT_DOWNLOAD_SUBDIR);
		if (VALUE_DOWNLOAD_SUBDIR_DISABLED.equals(value)) {
			return false;
		}
		if (VALUE_DOWNLOAD_SUBDIR_MULTIPLE.equals(value)) {
			return multiple;
		}
		if (VALUE_DOWNLOAD_SUBDIR_ENABLED.equals(value)) {
			return true;
		}
		return false;
	}

	public static final String KEY_DRAWER_INITIAL_POSITION = "drawer_initial_position";
	public static final String VALUE_DRAWER_INITIAL_POSITION_CLOSED = "closed";
	public static final String VALUE_DRAWER_INITIAL_POSITION_FAVORITES = "favorites";
	public static final String VALUE_DRAWER_INITIAL_POSITION_FORUMS = "forums";
	public static final String[] VALUES_DRAWER_INITIAL_POSITION = {
			VALUE_DRAWER_INITIAL_POSITION_CLOSED,
			VALUE_DRAWER_INITIAL_POSITION_FAVORITES,
			VALUE_DRAWER_INITIAL_POSITION_FORUMS
	};
	public static final int[] ENTRIES_DRAWER_INITIAL_POSITION = {
			R.string.closed,
			R.string.favorites,
			R.string.forums
	};
	public static final String DEFAULT_DRAWER_INITIAL_POSITION = VALUE_DRAWER_INITIAL_POSITION_CLOSED;

	public static final int DRAWER_INITIAL_POSITION_CLOSED = 0;
	public static final int DRAWER_INITIAL_POSITION_FAVORITES = 1;
	public static final int DRAWER_INITIAL_POSITION_FORUMS = 2;

	public static int getDrawerInitialPosition() {
		String value = PREFERENCES.getString(KEY_DRAWER_INITIAL_POSITION, DEFAULT_DRAWER_INITIAL_POSITION);
		if (VALUE_DRAWER_INITIAL_POSITION_CLOSED.equals(value)) {
			return DRAWER_INITIAL_POSITION_CLOSED;
		}
		if (VALUE_DRAWER_INITIAL_POSITION_FAVORITES.equals(value)) {
			return DRAWER_INITIAL_POSITION_FAVORITES;
		}
		if (VALUE_DRAWER_INITIAL_POSITION_FORUMS.equals(value)) {
			return DRAWER_INITIAL_POSITION_FORUMS;
		}
		return DRAWER_INITIAL_POSITION_CLOSED;
	}

	public static final String KEY_EXPANDED_SCREEN = "expanded_screen";
	public static final boolean DEFAULT_EXPANDED_SCREEN = false;

	public static boolean isExpandedScreen() {
		return PREFERENCES.getBoolean(KEY_EXPANDED_SCREEN, DEFAULT_EXPANDED_SCREEN);
	}

	public static void setExpandedScreen(boolean expandedScreen) {
		PREFERENCES.edit().putBoolean(KEY_EXPANDED_SCREEN, expandedScreen).commit();
	}

	public static final String KEY_FAVORITE_ON_REPLY = "favorite_on_reply";
	public static final String VALUE_FAVORITE_ON_REPLY_DISABLED = "disabled";
	public static final String VALUE_FAVORITE_ON_REPLY_ENABLED = "enabled";
	public static final String VALUE_FAVORITE_ON_REPLY_WITHOUT_SAGE = "without_sage";
	public static final String[] VALUES_FAVORITE_ON_REPLY = {
			VALUE_FAVORITE_ON_REPLY_DISABLED,
			VALUE_FAVORITE_ON_REPLY_ENABLED,
			VALUE_FAVORITE_ON_REPLY_WITHOUT_SAGE
	};
	public static final int[] ENTRIES_FAVORITE_ON_REPLY = {
			R.string.disabled,
			R.string.enabled,
			R.string.only_if_without_sage
	};
	public static final String DEFAULT_FAVORITE_ON_REPLY = VALUE_FAVORITE_ON_REPLY_DISABLED;

	public static boolean isFavoriteOnReply(boolean sage) {
		String value = PREFERENCES.getString(KEY_FAVORITE_ON_REPLY, DEFAULT_FAVORITE_ON_REPLY);
		return VALUE_FAVORITE_ON_REPLY_ENABLED.equals(value) ||
				VALUE_FAVORITE_ON_REPLY_WITHOUT_SAGE.equals(value) && !sage;
	}

	static {
		Object favoriteOnReply = PREFERENCES.getAll().get("favorite_on_reply");
		if (favoriteOnReply instanceof Boolean) {
			String value = (boolean) favoriteOnReply ? VALUE_FAVORITE_ON_REPLY_ENABLED
					: VALUE_FAVORITE_ON_REPLY_DISABLED;
			PREFERENCES.edit().remove("favorite_on_reply").putString(KEY_FAVORITE_ON_REPLY, value).commit();
		}
	}

	public static final String KEY_FAVORITES_ORDER = "favorites_order";
	public static final String VALUE_FAVORITES_ORDER_DATE_DESC = "date_desc";
	public static final String VALUE_FAVORITES_ORDER_DATE_ASC = "date_asc";
	public static final String VALUE_FAVORITES_ORDER_TITLE = "title";
	public static final String[] VALUES_FAVORITES_ORDER = {
			VALUE_FAVORITES_ORDER_DATE_DESC,
			VALUE_FAVORITES_ORDER_DATE_ASC,
			VALUE_FAVORITES_ORDER_TITLE
	};
	public static final int[] ENTRIES_FAVORITES_ORDER = {
			R.string.add_to_top__imperfective,
			R.string.add_to_bottom__imperfective,
			R.string.order_by_title
	};
	public static final String DEFAULT_FAVORITES_ORDER = VALUE_FAVORITES_ORDER_DATE_DESC;

	public static final int FAVORITES_ORDER_ADD_TO_THE_TOP = 0;
	public static final int FAVORITES_ORDER_ADD_TO_THE_BOTTOM = 1;
	public static final int FAVORITES_ORDER_BY_TITLE = 2;

	public static int getFavoritesOrder() {
		String value = PREFERENCES.getString(KEY_FAVORITES_ORDER, DEFAULT_FAVORITES_ORDER);
		if (VALUE_FAVORITES_ORDER_DATE_DESC.equals(value)) {
			return FAVORITES_ORDER_ADD_TO_THE_TOP;
		}
		if (VALUE_FAVORITES_ORDER_DATE_ASC.equals(value)) {
			return FAVORITES_ORDER_ADD_TO_THE_BOTTOM;
		}
		if (VALUE_FAVORITES_ORDER_TITLE.equals(value)) {
			return FAVORITES_ORDER_BY_TITLE;
		}
		return FAVORITES_ORDER_ADD_TO_THE_TOP;
	}

	public static final String KEY_HIDE_PERSONAL_DATA = "hide_personal_data";
	public static final boolean DEFAULT_HIDE_PERSONAL_DATA = false;

	public static boolean isHidePersonalData() {
		return PREFERENCES.getBoolean(KEY_HIDE_PERSONAL_DATA, DEFAULT_HIDE_PERSONAL_DATA);
	}

	public static final String KEY_HIGHLIGHT_UNREAD = "highlight_unread_posts";
	public static final String VALUE_HIGHLIGHT_UNREAD_AUTOMATICALLY = "automatically";
	public static final String VALUE_HIGHLIGHT_UNREAD_MANUALLY = "manually";
	public static final String VALUE_HIGHLIGHT_UNREAD_NEVER = "never";
	public static final String[] VALUES_HIGHLIGHT_UNREAD = {
			VALUE_HIGHLIGHT_UNREAD_AUTOMATICALLY,
			VALUE_HIGHLIGHT_UNREAD_MANUALLY,
			VALUE_HIGHLIGHT_UNREAD_NEVER
	};
	public static final int[] ENTRIES_HIGHLIGHT_UNREAD = {
			R.string.hide_eventually__imperfective,
			R.string.hide_on_tap__imperfective,
			R.string.never_highlight
	};
	public static final String DEFAULT_HIGHLIGHT_UNREAD = VALUE_HIGHLIGHT_UNREAD_AUTOMATICALLY;

	public static final int HIGHLIGHT_UNREAD_AUTOMATICALLY = 0;
	public static final int HIGHLIGHT_UNREAD_MANUALLY = 1;
	public static final int HIGHLIGHT_UNREAD_NEVER = 2;

	public static int getHighlightUnreadMode() {
		String value = PREFERENCES.getString(KEY_HIGHLIGHT_UNREAD, DEFAULT_HIGHLIGHT_UNREAD);
		if (VALUE_HIGHLIGHT_UNREAD_AUTOMATICALLY.equals(value)) {
			return HIGHLIGHT_UNREAD_AUTOMATICALLY;
		}
		if (VALUE_HIGHLIGHT_UNREAD_MANUALLY.equals(value)) {
			return HIGHLIGHT_UNREAD_MANUALLY;
		}
		if (VALUE_HIGHLIGHT_UNREAD_NEVER.equals(value)) {
			return HIGHLIGHT_UNREAD_NEVER;
		}
		return HIGHLIGHT_UNREAD_AUTOMATICALLY;
	}

	public static final String KEY_HUGE_CAPTCHA = "huge_captcha";
	public static final boolean DEFAULT_HUGE_CAPTCHA = false;

	public static boolean isHugeCaptcha() {
		return PREFERENCES.getBoolean(KEY_HUGE_CAPTCHA, DEFAULT_HUGE_CAPTCHA);
	}

	public static final String KEY_INTERNAL_BROWSER = "internal_browser";
	public static final boolean DEFAULT_INTERNAL_BROWSER = true;

	public static boolean isUseInternalBrowser() {
		return PREFERENCES.getBoolean(KEY_INTERNAL_BROWSER, DEFAULT_INTERNAL_BROWSER);
	}

	public static final String KEY_LAST_UPDATE_CHECK = "last_update_check";

	public static long getLastUpdateCheck() {
		return PREFERENCES.getLong(KEY_LAST_UPDATE_CHECK, 0L);
	}

	public static void setLastUpdateCheck(long lastUpdateCheck) {
		PREFERENCES.edit().putLong(KEY_LAST_UPDATE_CHECK, lastUpdateCheck).commit();
	}

	public static final ChanKey KEY_LOAD_CATALOG = new ChanKey("%s_load_catalog");
	public static final boolean DEFAULT_LOAD_CATALOG = false;

	public static boolean isLoadCatalog(String chanName) {
		return PREFERENCES.getBoolean(KEY_LOAD_CATALOG.bind(chanName), DEFAULT_LOAD_CATALOG);
	}

	public static final String KEY_LOAD_NEAREST_IMAGE = "load_nearest_image";
	public static final String DEFAULT_LOAD_NEAREST_IMAGE = GENERIC_VALUE_NETWORK_NEVER;

	public static boolean isLoadNearestImage() {
		return isNetworkAvailable(PREFERENCES.getString(KEY_LOAD_NEAREST_IMAGE, DEFAULT_LOAD_NEAREST_IMAGE),
				DEFAULT_LOAD_NEAREST_IMAGE);
	}

	public static final String KEY_LOAD_THUMBNAILS = "load_thumbnails";
	public static final String DEFAULT_LOAD_THUMBNAILS = GENERIC_VALUE_NETWORK_ALWAYS;

	private static String getLoadThumbnailsMode() {
		return PREFERENCES.getString(KEY_LOAD_THUMBNAILS, DEFAULT_LOAD_THUMBNAILS);
	}

	public static boolean isLoadThumbnails() {
		return isNetworkAvailable(getLoadThumbnailsMode(), DEFAULT_LOAD_THUMBNAILS);
	}

	public static final String KEY_LOCALE = "locale";

	public static String getLocale() {
		return PREFERENCES.getString(KEY_LOCALE, LocaleManager.DEFAULT_LOCALE);
	}

	public static final String KEY_LOCK_DRAWER = "lock_drawer";
	public static final boolean DEFAULT_LOCK_DRAWER = false;

	public static boolean isDrawerLocked() {
		return PREFERENCES.getBoolean(KEY_LOCK_DRAWER, DEFAULT_LOCK_DRAWER);
	}

	public static void setDrawerLocked(boolean locked) {
		PREFERENCES.edit().putBoolean(KEY_LOCK_DRAWER, locked).commit();
	}

	public static final String KEY_MERGE_CHANS = "merge_chans";
	public static final boolean DEFAULT_MERGE_CHANS = false;

	public static boolean isMergeChans() {
		return PREFERENCES.getBoolean(KEY_MERGE_CHANS, DEFAULT_MERGE_CHANS) &&
				ChanManager.getInstance().hasMultipleAvailableChans();
	}

	public static final String KEY_NOTIFY_DOWNLOAD_COMPLETE = "notify_download_complete";
	public static final boolean DEFAULT_NOTIFY_DOWNLOAD_COMPLETE = true;

	public static boolean isNotifyDownloadComplete() {
		return PREFERENCES.getBoolean(KEY_NOTIFY_DOWNLOAD_COMPLETE, DEFAULT_NOTIFY_DOWNLOAD_COMPLETE);
	}

	public static final String KEY_PAGE_BY_PAGE = "page_by_page";
	public static final boolean DEFAULT_PAGE_BY_PAGE = false;

	public static boolean isPageByPage() {
		return PREFERENCES.getBoolean(KEY_PAGE_BY_PAGE, DEFAULT_PAGE_BY_PAGE);
	}

	public static final String KEY_PAGES_LIST = "pages_list";
	public static final String VALUE_PAGES_LIST_PAGES_FIRST = "pages_first";
	public static final String VALUE_PAGES_LIST_FAVORITES_FIRST = "favorites_first";
	public static final String VALUE_PAGES_LIST_HIDE_PAGES = "hide_pages";
	public static final String[] VALUES_PAGES_LIST = {
			VALUE_PAGES_LIST_PAGES_FIRST,
			VALUE_PAGES_LIST_FAVORITES_FIRST,
			VALUE_PAGES_LIST_HIDE_PAGES
	};
	public static final int[] ENTRIES_PAGES_LIST = {
			R.string.pages_first,
			R.string.favorites_first,
			R.string.hide_pages
	};
	public static final String DEFAULT_PAGES_LIST = VALUE_PAGES_LIST_PAGES_FIRST;

	public static final int PAGES_LIST_MODE_PAGES_FIRST = 0;
	public static final int PAGES_LIST_MODE_FAVORITES_FIRST = 1;
	public static final int PAGES_LIST_MODE_HIDE_PAGES = 2;

	public static int getPagesListMode() {
		String value = PREFERENCES.getString(KEY_PAGES_LIST, DEFAULT_PAGES_LIST);
		if (VALUE_PAGES_LIST_PAGES_FIRST.equals(value)) {
			return PAGES_LIST_MODE_PAGES_FIRST;
		}
		if (VALUE_PAGES_LIST_FAVORITES_FIRST.equals(value)) {
			return PAGES_LIST_MODE_FAVORITES_FIRST;
		}
		if (VALUE_PAGES_LIST_HIDE_PAGES.equals(value)) {
			return PAGES_LIST_MODE_HIDE_PAGES;
		}
		return PAGES_LIST_MODE_PAGES_FIRST;
	}

	public static final ChanKey KEY_PARTIAL_THREAD_LOADING = new ChanKey("%s_partial_thread_loading");
	public static final boolean DEFAULT_PARTIAL_THREAD_LOADING = true;

	public static boolean isPartialThreadLoading(String chanName) {
		if (ChanConfiguration.get(chanName).getOption(ChanConfiguration.OPTION_READ_THREAD_PARTIALLY)) {
			return PREFERENCES.getBoolean(KEY_PARTIAL_THREAD_LOADING.bind(chanName),
					DEFAULT_PARTIAL_THREAD_LOADING);
		} else {
			return false;
		}
	}

	public static final ChanKey KEY_PASSWORD = new ChanKey("%s_password");

	private static String generatePassword() {
		StringBuilder password = new StringBuilder();
		Random random = new Random(System.currentTimeMillis());
		for (int i = 0, count = 10 + random.nextInt(6); i < count; i++) {
			int value = random.nextInt(26 + 26 + 10);
			if (value < 26) {
				value = 0x41 + value;
			} else if (value < 26 + 26) {
				value = 0x61 + value - 26;
			} else {
				value = 0x30 + value - 26 - 26;
			}
			password.append((char) value);
		}
		return password.toString();
	}

	public static String getPassword(String chanName) {
		String key = KEY_PASSWORD.bind(chanName);
		String password = PREFERENCES.getString(key, null);
		if (StringUtils.isEmpty(password)) {
			password = generatePassword();
			PREFERENCES.edit().putString(key, password).commit();
		}
		return password;
	}

	public static final String KEY_POST_MAX_LINES = "post_max_lines";
	public static final String DEFAULT_POST_MAX_LINES = "20";

	public static int getPostMaxLines() {
		try {
			return Integer.parseInt(PREFERENCES.getString(KEY_POST_MAX_LINES, DEFAULT_POST_MAX_LINES));
		} catch (Exception e) {
			return Integer.parseInt(DEFAULT_POST_MAX_LINES);
		}
	}

	public static final ChanKey KEY_PROXY = new ChanKey("%s_proxy");
	public static final String VALUE_PROXY_2_HTTP = "http";
	public static final String VALUE_PROXY_2_SOCKS = "socks";
	public static final String[] ENTRIES_PROXY_2 = {"HTTP", "SOCKS"};
	public static final String[] VALUES_PROXY_2 = {VALUE_PROXY_2_HTTP, VALUE_PROXY_2_SOCKS};

	public static String[] getProxy(String chanName) {
		if (ChanConfiguration.get(chanName).getOption(ChanConfiguration.OPTION_LOCAL_MODE)) {
			return null;
		}
		String value = PREFERENCES.getString(KEY_PROXY.bind(chanName), null);
		return unpackOrCastMultipleValues(value, 3);
	}

	public static final String KEY_RECAPTCHA_JAVASCRIPT = "recaptcha_javascript";
	public static final boolean DEFAULT_RECAPTCHA_JAVASCRIPT = true;

	public static boolean isRecaptchaJavascript() {
		return PREFERENCES.getBoolean(KEY_RECAPTCHA_JAVASCRIPT, DEFAULT_RECAPTCHA_JAVASCRIPT);
	}

	public static final String KEY_REMEMBER_HISTORY = "remember_history";
	public static final boolean DEFAULT_REMEMBER_HISTORY = true;

	public static boolean isRememberHistory() {
		return PREFERENCES.getBoolean(KEY_REMEMBER_HISTORY, DEFAULT_REMEMBER_HISTORY);
	}

	public static final String KEY_SCROLL_THREAD_GALLERY = "scroll_thread_gallery";
	public static final boolean DEFAULT_SCROLL_THREAD_GALLERY = false;

	public static boolean isScrollThreadGallery() {
		return PREFERENCES.getBoolean(KEY_SCROLL_THREAD_GALLERY, DEFAULT_SCROLL_THREAD_GALLERY);
	}

	public static final String KEY_SHOWCASE_GALLERY = "showcase_gallery";

	public static void consumeShowcaseGallery() {
		PREFERENCES.edit().putBoolean(KEY_SHOWCASE_GALLERY, false).commit();
	}

	public static boolean isShowcaseGalleryEnabled() {
		return PREFERENCES.getBoolean(KEY_SHOWCASE_GALLERY, true);
	}

	public static final String KEY_SFW_MODE = "sfw_mode";
	public static final boolean DEFAULT_SFW_MODE = false;

	public static boolean isSfwMode() {
		return PREFERENCES.getBoolean(KEY_SFW_MODE, DEFAULT_SFW_MODE);
	}

	public static void setSfwMode(boolean sfwMode) {
		PREFERENCES.edit().putBoolean(KEY_SFW_MODE, sfwMode).commit();
	}

	public static final String KEY_SHOW_MY_POSTS = "show_my_posts";
	public static final boolean DEFAULT_SHOW_MY_POSTS = true;

	public static boolean isShowMyPosts() {
		return PREFERENCES.getBoolean(KEY_SHOW_MY_POSTS, DEFAULT_SHOW_MY_POSTS);
	}

	public static void setShowMyPosts(boolean showMyPosts) {
		PREFERENCES.edit().putBoolean(KEY_SHOW_MY_POSTS, showMyPosts).commit();
	}

	public static final String KEY_SHOW_SPOILERS = "show_spoilers";
	public static final boolean DEFAULT_SHOW_SPOILERS = false;

	public static boolean isShowSpoilers() {
		return PREFERENCES.getBoolean(KEY_SHOW_SPOILERS, DEFAULT_SHOW_SPOILERS);
	}

	public static void setShowSpoilers(boolean showSpoilers) {
		PREFERENCES.edit().putBoolean(KEY_SHOW_SPOILERS, showSpoilers).commit();
	}

	public static final String KEY_USER_AGENT_REFERENCE = "user_agent_reference";

	public static String getUserAgentReference() {
		return PREFERENCES.getString(KEY_USER_AGENT_REFERENCE, null);
	}

	public static void setUserAgentReference(String userAgentReference) {
		PREFERENCES.edit().putString(KEY_USER_AGENT_REFERENCE, userAgentReference).commit();
	}

	public static final String KEY_TEXT_SCALE = "text_scale";
	public static final int DEFAULT_TEXT_SCALE = 100;

	public static int getTextScale() {
		return PREFERENCES.getInt(KEY_TEXT_SCALE, DEFAULT_TEXT_SCALE);
	}

	public static final String KEY_THEME = "theme";

	public static String getTheme() {
		return PREFERENCES.getString(KEY_THEME, null);
	}

	public static void setTheme(String value) {
		PREFERENCES.edit().putString(KEY_THEME, value).commit();
	}

	public static final String KEY_THREADS_GRID_MODE = "threads_grid_mode";
	public static final boolean DEFAULT_THREADS_GRID_MODE = false;

	public static boolean isThreadsGridMode() {
		return PREFERENCES.getBoolean(KEY_THREADS_GRID_MODE, DEFAULT_THREADS_GRID_MODE);
	}

	public static void setThreadsGridMode(boolean threadsGridMode) {
		PREFERENCES.edit().putBoolean(KEY_THREADS_GRID_MODE, threadsGridMode).commit();
	}

	public static final String KEY_THUMBNAILS_SCALE = "thumbnails_scale";
	public static final int DEFAULT_THUMBNAILS_SCALE = 100;

	public static int getThumbnailsScale() {
		return PREFERENCES.getInt(KEY_THUMBNAILS_SCALE, DEFAULT_THUMBNAILS_SCALE);
	}

	public static final String KEY_TRUSTED_EXSTENSIONS = "trusted_extensions";

	public static boolean isExtensionTrusted(String packageName, String fingerprint) {
		String packageNameFingerprint = packageName + ":" + fingerprint;
		Set<String> packageNameFingerprints = PREFERENCES.getStringSet(KEY_TRUSTED_EXSTENSIONS, null);
		return packageNameFingerprints != null && packageNameFingerprints.contains(packageNameFingerprint);
	}

	public static void setExtensionTrusted(String packageName, String fingerprint) {
		String packageNameFingerprint = packageName + ":" + fingerprint;
		Set<String> packageNameFingerprints = PREFERENCES.getStringSet(KEY_TRUSTED_EXSTENSIONS, null);
		packageNameFingerprints = packageNameFingerprints != null
				? new HashSet<>(packageNameFingerprints) : new HashSet<>();
		packageNameFingerprints.add(packageNameFingerprint);
		PREFERENCES.edit().putStringSet(KEY_TRUSTED_EXSTENSIONS, packageNameFingerprints).commit();
	}

	public static final String KEY_USE_GMS_PROVIDER = "use_gms_provider";
	public static final boolean DEFAULT_USE_GMS_PROVIDER = false;

	public static boolean isUseGmsProvider() {
		return PREFERENCES.getBoolean(KEY_USE_GMS_PROVIDER, DEFAULT_USE_GMS_PROVIDER);
	}

	public static final ChanKey KEY_USE_HTTPS = new ChanKey("%s_use_https");
	public static final String KEY_USE_HTTPS_GENERAL = KEY_USE_HTTPS.bind(SPECIAL_CHAN_NAME_GENERAL);
	public static final boolean DEFAULT_USE_HTTPS = true;

	public static boolean isUseHttps(String chanName) {
		return PREFERENCES.getBoolean(KEY_USE_HTTPS.bind(chanName), DEFAULT_USE_HTTPS);
	}

	public static void setUseHttps(String chanName, boolean useHttps) {
		PREFERENCES.edit().putBoolean(KEY_USE_HTTPS.bind(chanName), useHttps).commit();
	}

	public static boolean isUseHttpsGeneral() {
		return PREFERENCES.getBoolean(KEY_USE_HTTPS_GENERAL, DEFAULT_USE_HTTPS);
	}

	public static final String KEY_USE_VIDEO_PLAYER = "use_video_player";
	public static final boolean DEFAULT_USE_VIDEO_PLAYER = false;

	public static boolean isUseVideoPlayer() {
		return PREFERENCES.getBoolean(KEY_USE_VIDEO_PLAYER, DEFAULT_USE_VIDEO_PLAYER);
	}

	public static final ChanKey KEY_USER_AUTHORIZATION = new ChanKey("%s_user_authorization");

	public static String[] getUserAuthorizationData(String chanName) {
		ChanConfiguration.Authorization authorization = ChanConfiguration.get(chanName)
				.safe().obtainUserAuthorization();
		if (authorization != null && authorization.fieldsCount > 0) {
			String value = PREFERENCES.getString(KEY_USER_AUTHORIZATION.bind(chanName), null);
			return unpackOrCastMultipleValues(value, authorization.fieldsCount);
		} else {
			return null;
		}
	}

	public static final String KEY_VERIFY_CERTIFICATE = "verify_certificate";
	public static final boolean DEFAULT_VERIFY_CERTIFICATE = true;

	public static boolean isVerifyCertificate() {
		return PREFERENCES.getBoolean(KEY_VERIFY_CERTIFICATE, DEFAULT_VERIFY_CERTIFICATE);
	}

	public static final String KEY_VIDEO_COMPLETION = "video_completion";
	public static final String VALUE_VIDEO_COMPLETION_NOTHING = "nothing";
	public static final String VALUE_VIDEO_COMPLETION_LOOP = "loop";
	public static final String[] VALUES_VIDEO_COMPLETION = {
			VALUE_VIDEO_COMPLETION_NOTHING,
			VALUE_VIDEO_COMPLETION_LOOP
	};
	public static final int[] ENTRIES_VIDEO_COMPLETION = {
			R.string.do_nothing,
			R.string.play_again
	};
	public static final String DEFAULT_VIDEO_COMPLETION = VALUE_VIDEO_COMPLETION_NOTHING;

	public static final int VIDEO_COMPLETION_MODE_NOTHING = 0;
	public static final int VIDEO_COMPLETION_MODE_LOOP = 1;

	public static int getVideoCompletionMode() {
		String value = PREFERENCES.getString(KEY_VIDEO_COMPLETION, DEFAULT_VIDEO_COMPLETION);
		if (VALUE_VIDEO_COMPLETION_NOTHING.equals(value)) {
			return VIDEO_COMPLETION_MODE_NOTHING;
		}
		if (VALUE_VIDEO_COMPLETION_LOOP.equals(value)) {
			return VIDEO_COMPLETION_MODE_LOOP;
		}
		return VIDEO_COMPLETION_MODE_NOTHING;
	}

	public static final String KEY_VIDEO_PLAY_AFTER_SCROLL = "video_play_after_scroll";
	public static final boolean DEFAULT_VIDEO_PLAY_AFTER_SCROLL = false;

	public static boolean isVideoPlayAfterScroll() {
		return PREFERENCES.getBoolean(KEY_VIDEO_PLAY_AFTER_SCROLL, DEFAULT_VIDEO_PLAY_AFTER_SCROLL);
	}

	public static final String KEY_VIDEO_SEEK_ANY_FRAME = "video_seek_any_frame";
	public static final boolean DEFAULT_VIDEO_SEEK_ANY_FRAME = false;

	public static boolean isVideoSeekAnyFrame() {
		return PREFERENCES.getBoolean(KEY_VIDEO_SEEK_ANY_FRAME, DEFAULT_VIDEO_SEEK_ANY_FRAME);
	}

	public static final String KEY_WATCHER_AUTO_DISABLE = "watcher_auto_disable";
	public static final boolean DEFAULT_WATCHER_AUTO_DISABLE = true;

	public static boolean isWatcherAutoDisable() {
		return PREFERENCES.getBoolean(KEY_WATCHER_AUTO_DISABLE, DEFAULT_WATCHER_AUTO_DISABLE);
	}

	public static final String KEY_WATCHER_REFRESH_INTERVAL = "watcher_refresh_interval";
	public static final int DEFAULT_WATCHER_REFRESH_INTERVAL = 30;

	public static int getWatcherRefreshInterval() {
		return PREFERENCES.getInt(KEY_WATCHER_REFRESH_INTERVAL, DEFAULT_WATCHER_REFRESH_INTERVAL);
	}

	public static final String KEY_WATCHER_REFRESH_PERIODICALLY = "watcher_refresh_periodically";
	public static final boolean DEFAULT_WATCHER_REFRESH_PERIODICALLY = true;

	public static boolean isWatcherRefreshPeriodically() {
		return PREFERENCES.getBoolean(KEY_WATCHER_REFRESH_PERIODICALLY,
				DEFAULT_WATCHER_REFRESH_PERIODICALLY);
	}

	public static final String KEY_WATCHER_WATCH_INITIALLY = "watcher_watch_initially";
	public static final boolean DEFAULT_WATCHER_WATCH_INITIALLY = false;

	public static boolean isWatcherWatchInitially() {
		return PREFERENCES.getBoolean(KEY_WATCHER_WATCH_INITIALLY, DEFAULT_WATCHER_WATCH_INITIALLY);
	}

	public static final String KEY_WATCHER_WIFI_ONLY = "watcher_wifi_only";
	public static final boolean DEFAULT_WATCHER_WIFI_ONLY = false;

	public static boolean isWatcherWifiOnly() {
		return PREFERENCES.getBoolean(KEY_WATCHER_WIFI_ONLY, DEFAULT_WATCHER_WIFI_ONLY);
	}
}
