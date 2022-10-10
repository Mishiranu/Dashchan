package com.mishiranu.dashchan.content;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.util.Pair;
import androidx.annotation.RequiresApi;
import chan.content.Chan;
import chan.content.ChanConfiguration;
import chan.content.ChanManager;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.util.SharedPreferences;
import com.mishiranu.dashchan.widget.ClickableToast;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Preferences {
	public static final SharedPreferences PREFERENCES;

	private static final String PREFERENCES_NAME = "preferences";
	private static final String PREFERENCES_RESTORE_NAME = "preferences.restore";

	static {
		MainApplication application = MainApplication.getInstance();
		if (application.isMainProcess()) {
			File newFile = getPreferencesFile(application, PREFERENCES_NAME);
			File newBackupFile = new File(newFile.getParentFile(), newFile.getName() + ".bak");
			if (!newFile.exists() && !newBackupFile.exists()) {
				// Rename preferences file
				File oldFile = getPreferencesFile(application, application.getPackageName() + "_preferences");
				File oldBackupFile = new File(oldFile.getParentFile(), oldFile.getName() + ".bak");
				if (oldBackupFile.exists()) {
					oldBackupFile.renameTo(newBackupFile);
				}
				if (oldFile.exists()) {
					oldFile.renameTo(newFile);
				}
			}
			File restoreFile = getPreferencesFile(application, PREFERENCES_RESTORE_NAME);
			if (restoreFile.exists()) {
				newBackupFile.delete();
				restoreFile.renameTo(newFile);
			}
			PREFERENCES = new SharedPreferences(application, PREFERENCES_NAME);
		} else {
			PREFERENCES = null;
		}
	}

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
			return chanName + "_" + key;
		}
	}

	private static File getPreferencesFile(MainApplication application, String name) {
		return new File(application.getSharedPrefsDir(), name + ".xml");
	}

	public static Pair<File, File> getFilesForBackup() {
		File preferences = getPreferencesFile(MainApplication.getInstance(), PREFERENCES_NAME);
		File restore = getPreferencesFile(MainApplication.getInstance(), PREFERENCES_RESTORE_NAME);
		return new Pair<>(preferences, restore);
	}

	public static File getFileForRestore() {
		return getPreferencesFile(MainApplication.getInstance(), PREFERENCES_RESTORE_NAME);
	}

	public static List<String> unpackOrCastMultipleValues(String value, int count) {
		ArrayList<String> values = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			values.add(null);
		}
		if (value != null) {
			try {
				JSONArray jsonArray = new JSONArray(value);
				int length = Math.min(count, jsonArray.length());
				for (int i = 0; i < length; i++) {
					values.set(i, jsonArray.isNull(i) ? null : StringUtils.nullIfEmpty(jsonArray.getString(i)));
				}
				return values;
			} catch (JSONException e) {
				// Backward compatibility
				if (value.length() > 0 && !value.startsWith("[")) {
					values.set(0, value);
				}
			}
		}
		return values;
	}

	public static Map<String, String> unpackOrCastMultipleValues(String value, List<String> keys) {
		HashMap<String, String> values = new HashMap<>(keys.size());
		if (value != null) {
			try {
				JSONObject jsonObject = new JSONObject(value);
				for (String key : keys) {
					String stringValue = jsonObject.optString(key);
					if (!StringUtils.isEmpty(stringValue)) {
						values.put(key, stringValue);
					}
				}
			} catch (JSONException e) {
				// Migration
				List<String> list = unpackOrCastMultipleValues(value, keys.size());
				if (list != null && list.size() == keys.size()) {
					for (int i = 0; i < keys.size(); i++) {
						values.put(keys.get(i), list.get(i));
					}
				}
			}
		}
		return values;
	}

	public static boolean checkHasMultipleValues(List<String> values) {
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

	private interface EnumValueProvider<T extends Enum<T>> {
		String getValue(T enumValue);
	}

	private static <T extends Enum<T>> T getEnumValue(String key, T[] values, T defaultValue,
			EnumValueProvider<T> enumValueProvider) {
		String stringValue = PREFERENCES.getString(key, enumValueProvider.getValue(defaultValue));
		for (T value : values) {
			if (enumValueProvider.getValue(value).equals(stringValue)) {
				return value;
			}
		}
		return defaultValue;
	}

	public enum NetworkMode {
		ALWAYS("always", R.string.always, o -> true),
		WIFI_3G("wifi_3g", R.string.wifi_or_3g_plus, NetworkObserver::isMobile3GConnected),
		WIFI("wifi", R.string.wifi_only, NetworkObserver::isWifiConnected),
		NEVER("never", R.string.never, o -> false);

		private static final EnumValueProvider<NetworkMode> VALUE_PROVIDER = o -> o.value;

		private interface Check {
			boolean isNetworkAvailable(NetworkObserver networkObserver);
		}

		public final String value;
		public final int titleResId;
		private final Check check;

		NetworkMode(String value, int titleResId, Check check) {
			this.value = value;
			this.titleResId = titleResId;
			this.check = check;
		}

		public boolean isNetworkAvailable(NetworkObserver networkObserver) {
			return check.isNetworkAvailable(networkObserver);
		}
	}

	private static NetworkMode getNetworkModeGeneric(String key, NetworkMode defaultValue) {
		return getEnumValue(key, NetworkMode.values(), defaultValue, NetworkMode.VALUE_PROVIDER);
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

	public static final String KEY_AUTO_REFRESH_INTERVAL = "auto_refresh_interval";
	public static final int DISABLED_AUTO_REFRESH_INTERVAL = 0;
	public static final int MIN_AUTO_REFRESH_INTERVAL = 15;
	public static final int MAX_AUTO_REFRESH_INTERVAL = 90;
	public static final int STEP_AUTO_REFRESH_INTERVAL = 5;
	public static final int DEFAULT_AUTO_REFRESH_INTERVAL = DISABLED_AUTO_REFRESH_INTERVAL;

	public static int getAutoRefreshInterval() {
		int value = PREFERENCES.getInt(KEY_AUTO_REFRESH_INTERVAL, DEFAULT_AUTO_REFRESH_INTERVAL);
		return value > MAX_AUTO_REFRESH_INTERVAL ? MAX_AUTO_REFRESH_INTERVAL
				: value < MIN_AUTO_REFRESH_INTERVAL ? DISABLED_AUTO_REFRESH_INTERVAL : value;
	}

	static {
		if (PREFERENCES != null) {
			String key = "auto_refresh_mode";
			String value = PREFERENCES.getString(key, null);
			if (value != null) {
				boolean enabled = "enabled".equals(value);
				try (SharedPreferences.Editor editor = PREFERENCES.edit()) {
					editor.remove(value);
					if (!enabled) {
						editor.put(KEY_AUTO_REFRESH_INTERVAL, DISABLED_AUTO_REFRESH_INTERVAL);
					}
				}
			}
		}
	}

	public static final String KEY_CACHE_SIZE = "cache_size";
	public static final int MIN_CACHE_SIZE = 100;
	public static final int MAX_CACHE_SIZE = 800;
	public static final int STEP_CACHE_SIZE = 50;
	public static final int DEFAULT_CACHE_SIZE = 200;

	public static int getCacheSize() {
		return PREFERENCES.getInt(KEY_CACHE_SIZE, DEFAULT_CACHE_SIZE);
	}

	public static final ChanKey KEY_CAPTCHA = new ChanKey("captcha");
	private static final String VALUE_CAPTCHA_START = "captcha_";

	public static String getCaptchaTypeForChan(Chan chan) {
		Collection<String> supportedCaptchaTypes = chan.configuration.getSupportedCaptchaTypes();
		if (supportedCaptchaTypes == null || supportedCaptchaTypes.isEmpty()) {
			return null;
		}
		String defaultCaptchaType = supportedCaptchaTypes.iterator().next();
		String captchaTypeValue = PREFERENCES.getString(KEY_CAPTCHA.bind(chan.name),
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

	public static List<String> getCaptchaTypeValues(Collection<String> captchaTypes) {
		ArrayList<String> values = new ArrayList<>();
		for (String captchaType : captchaTypes) {
			values.add(transformCaptchaTypeToValue(captchaType));
		}
		return values;
	}

	public static List<CharSequence> getCaptchaTypeEntries(Chan chan, Collection<String> captchaTypes) {
		ArrayList<CharSequence> entries = new ArrayList<>();
		for (String captchaType : captchaTypes) {
			entries.add(chan.configuration.safe().obtainCaptcha(captchaType).title);
		}
		return entries;
	}

	public static String getCaptchaTypeDefaultValue(Chan chan) {
		Collection<String> supportedCaptchaTypes = chan.configuration.getSupportedCaptchaTypes();
		if (supportedCaptchaTypes == null || supportedCaptchaTypes.isEmpty()) {
			return null;
		}
		return transformCaptchaTypeToValue(supportedCaptchaTypes.iterator().next());
	}

	private static String transformCaptchaTypeToValue(String captchaType) {
		return VALUE_CAPTCHA_START + captchaType;
	}

	public static final ChanKey KEY_CAPTCHA_PASS = new ChanKey("captcha_pass");

	public static List<String> getCaptchaPass(Chan chan) {
		ChanConfiguration.Authorization authorization = chan.configuration.safe().obtainCaptchaPass();
		if (authorization != null && authorization.fieldsCount > 0) {
			String value = PREFERENCES.getString(KEY_CAPTCHA_PASS.bind(chan.name), null);
			return unpackOrCastMultipleValues(value, authorization.fieldsCount);
		} else {
			return null;
		}
	}

	public static final String KEY_CAPTCHA_SOLVING = "captcha_solving";
	public static final String SUB_KEY_CAPTCHA_SOLVING_ENDPOINT = "endpoint";
	public static final String SUB_KEY_CAPTCHA_SOLVING_TOKEN = "token";
	public static final String SUB_KEY_CAPTCHA_SOLVING_TIMEOUT = "timeout";
	public static final List<String> KEYS_CAPTCHA_SOLVING = Arrays
			.asList(SUB_KEY_CAPTCHA_SOLVING_ENDPOINT, SUB_KEY_CAPTCHA_SOLVING_TOKEN, SUB_KEY_CAPTCHA_SOLVING_TIMEOUT);

	public static Map<String, String> getCaptchaSolving() {
		String value = PREFERENCES.getString(KEY_CAPTCHA_SOLVING, null);
		return unpackOrCastMultipleValues(value, KEYS_CAPTCHA_SOLVING);
	}

	public static final String KEY_CAPTCHA_SOLVING_CHANS = "captcha_solving_chans";

	public static Set<String> getCaptchaSolvingChans() {
		String value = PREFERENCES.getString(KEY_CAPTCHA_SOLVING_CHANS, null);
		if (StringUtils.isEmpty(value)) {
			return Collections.emptySet();
		}
		try {
			JSONArray jsonArray = new JSONArray(value);
			HashSet<String> chanNames = new HashSet<>(jsonArray.length());
			for (int i = 0; i < jsonArray.length(); i++) {
				String chanName = jsonArray.optString(i);
				if (!StringUtils.isEmpty(chanName)) {
					chanNames.add(chanName);
				}
			}
			return chanNames;
		} catch (JSONException e) {
			return Collections.emptySet();
		}
	}

	public static void setCaptchaSolvingChans(Collection<String> chanNames) {
		if (chanNames == null || chanNames.isEmpty()) {
			PREFERENCES.edit().remove(KEY_CAPTCHA_SOLVING_CHANS).close();
		} else {
			JSONArray jsonArray = new JSONArray();
			for (String chanName : chanNames) {
				jsonArray.put(chanName);
			}
			PREFERENCES.edit().put(KEY_CAPTCHA_SOLVING_CHANS, jsonArray.toString()).close();
		}
	}

	public enum CatalogSort {
		UNSORTED("unsorted", R.id.menu_unsorted, R.string.unsorted, null),
		CREATED("created", R.id.menu_date_created, R.string.date_created,
				(lhs, rhs) -> Long.compare(rhs.getTimestamp(), lhs.getTimestamp())),
		REPLIES("replies", R.id.menu_replies, R.string.replies_count,
				(lhs, rhs) -> Integer.compare(rhs.getThreadPostsCount(), lhs.getThreadPostsCount()));

		private static final EnumValueProvider<CatalogSort> VALUE_PROVIDER = o -> o.value;

		public interface Comparable {
			long getTimestamp();
			int getThreadPostsCount();
		}

		private final String value;
		public final int menuItemId;
		public final int titleResId;
		public final Comparator<Comparable> comparator;

		CatalogSort(String value, int menuItemId, int titleResId, Comparator<Comparable> comparator) {
			this.value = value;
			this.menuItemId = menuItemId;
			this.titleResId = titleResId;
			this.comparator = comparator;
		}
	}

	public static final String KEY_CATALOG_SORT = "catalog_sort";
	public static final CatalogSort DEFAULT_CATALOG_SORT = CatalogSort.UNSORTED;

	public static CatalogSort getCatalogSort() {
		return getEnumValue(KEY_CATALOG_SORT, CatalogSort.values(), DEFAULT_CATALOG_SORT, CatalogSort.VALUE_PROVIDER);
	}

	public static void setCatalogSort(CatalogSort catalogSort) {
		PREFERENCES.edit().put(KEY_CATALOG_SORT, catalogSort != null ? catalogSort.value : null).close();
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
		PREFERENCES.edit().put(KEY_CHANS_ORDER, jsonArray.toString()).close();
	}

	public static final String KEY_CHECK_UPDATES_ON_START = "check_updates_on_start";
	public static final boolean DEFAULT_CHECK_UPDATES_ON_START = true;

	public static boolean isCheckUpdatesOnStart() {
		return PREFERENCES.getBoolean(KEY_CHECK_UPDATES_ON_START, DEFAULT_CHECK_UPDATES_ON_START);
	}

	public static void setCheckUpdatesOnStart(boolean checkUpdatesOnStart) {
		PREFERENCES.edit().put(KEY_CHECK_UPDATES_ON_START, checkUpdatesOnStart).close();
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

	public enum CyclicalRefreshMode {
		DEFAULT("default", R.string.use_forum_settings),
		FULL_LOAD("full_load", R.string.load_full_thread),
		FULL_LOAD_CLEANUP("full_load_cleanup", R.string.load_full_thread_and_clear_old_posts);

		private static final EnumValueProvider<CyclicalRefreshMode> VALUE_PROVIDER = o -> o.value;

		public final String value;
		public final int titleResId;

		CyclicalRefreshMode(String value, int titleResId) {
			this.value = value;
			this.titleResId = titleResId;
		}
	}

	public static final String KEY_CYCLICAL_REFRESH = "cyclical_refresh";
	public static final CyclicalRefreshMode DEFAULT_CYCLICAL_REFRESH = CyclicalRefreshMode.DEFAULT;

	public static CyclicalRefreshMode getCyclicalRefreshMode() {
		return getEnumValue(KEY_CYCLICAL_REFRESH, CyclicalRefreshMode.values(),
				DEFAULT_CYCLICAL_REFRESH, CyclicalRefreshMode.VALUE_PROVIDER);
	}

	public static final ChanKey KEY_DEFAULT_BOARD_NAME = new ChanKey("default_board_name");

	public static String getDefaultBoardName(Chan chan) {
		return chan.configuration.getOption(ChanConfiguration.OPTION_SINGLE_BOARD_MODE)
				? chan.configuration.getSingleBoardName()
				: StringUtils.validateBoardName(PREFERENCES.getString(KEY_DEFAULT_BOARD_NAME.bind(chan.name), null));
	}

	public static void setDefaultBoardName(String chanName, String boardName) {
		PREFERENCES.edit().put(KEY_DEFAULT_BOARD_NAME.bind(chanName), boardName).close();
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

	public static final ChanKey KEY_DOMAIN = new ChanKey("domain");

	public static String getDomainUnhandled(Chan chan) {
		return PREFERENCES.getString(KEY_DOMAIN.bind(chan.name), "");
	}

	public static void setDomainUnhandled(Chan chan, String domain) {
		PREFERENCES.edit().put(KEY_DOMAIN.bind(chan.name), domain).close();
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
				try (Cursor cursor = contentResolver.query(uri, null, null, null, null)) {
					if (cursor != null && cursor.moveToFirst()) {
						return treeUri;
					}
				} catch (SecurityException e) {
					e.printStackTrace();
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
			ClickableToast.show(R.string.no_access_to_memory);
		} else {
			contentResolver.takePersistableUriPermission(uri, uriFlags &
					(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
		}
	}

	public enum DownloadSubdirMode {
		DISABLED("disabled", R.string.never, multiple -> false),
		MULTIPLE_ONLY("multiple_only", R.string.on_multiple_downloading, multiple -> multiple),
		ENABLED("enabled", R.string.always, multiple -> true);

		private static final EnumValueProvider<DownloadSubdirMode> VALUE_PROVIDER = o -> o.value;

		private interface Check {
			boolean isEnabled(boolean multiple);
		}

		public final String value;
		public final int titleResId;
		private final Check check;

		DownloadSubdirMode(String value, int titleResId, Check check) {
			this.value = value;
			this.titleResId = titleResId;
			this.check = check;
		}

		public boolean isEnabled(boolean multiple) {
			return check.isEnabled(multiple);
		}
	}

	public static final String KEY_DOWNLOAD_SUBDIR = "download_subdir";
	public static final DownloadSubdirMode DEFAULT_DOWNLOAD_SUBDIR = DownloadSubdirMode.DISABLED;

	public static DownloadSubdirMode getDownloadSubdirMode() {
		return getEnumValue(KEY_DOWNLOAD_SUBDIR, DownloadSubdirMode.values(),
				DEFAULT_DOWNLOAD_SUBDIR, DownloadSubdirMode.VALUE_PROVIDER);
	}

	public enum DrawerInitialPosition {
		CLOSED("closed", R.string.closed),
		FAVORITES("favorites", R.string.favorites),
		FORUMS("forums", R.string.forums);

		public static final EnumValueProvider<DrawerInitialPosition> VALUE_PROVIDER = o -> o.value;

		public final String value;
		public final int titleResId;

		DrawerInitialPosition(String value, int titleResId) {
			this.value = value;
			this.titleResId = titleResId;
		}
	}

	public static final String KEY_DRAWER_INITIAL_POSITION = "drawer_initial_position";
	public static final DrawerInitialPosition DEFAULT_DRAWER_INITIAL_POSITION = DrawerInitialPosition.CLOSED;

	public static DrawerInitialPosition getDrawerInitialPosition() {
		return getEnumValue(KEY_DRAWER_INITIAL_POSITION, DrawerInitialPosition.values(),
				DEFAULT_DRAWER_INITIAL_POSITION, DrawerInitialPosition.VALUE_PROVIDER);
	}

	public static final String KEY_EXPANDED_SCREEN = "expanded_screen";
	public static final boolean DEFAULT_EXPANDED_SCREEN = false;

	public static boolean isExpandedScreen() {
		return PREFERENCES.getBoolean(KEY_EXPANDED_SCREEN, DEFAULT_EXPANDED_SCREEN);
	}

	public static void setExpandedScreen(boolean expandedScreen) {
		PREFERENCES.edit().put(KEY_EXPANDED_SCREEN, expandedScreen).close();
	}

	public enum FavoriteOnReplyMode {
		DISABLED("disabled", R.string.disabled, sage -> false),
		ENABLED("enabled", R.string.enabled, sage -> true),
		WITHOUT_SAGE("without_sage", R.string.only_if_without_sage, sage -> !sage);

		private static final EnumValueProvider<FavoriteOnReplyMode> VALUE_PROVIDER = o -> o.value;

		private interface Check {
			boolean isEnabled(boolean sage);
		}

		public final String value;
		public final int titleResId;
		private final Check check;

		FavoriteOnReplyMode(String value, int titleResId, Check check) {
			this.value = value;
			this.titleResId = titleResId;
			this.check = check;
		}

		public boolean isEnabled(boolean sage) {
			return check.isEnabled(sage);
		}
	}

	public static final String KEY_FAVORITE_ON_REPLY = "favorite_on_reply";
	public static final FavoriteOnReplyMode DEFAULT_FAVORITE_ON_REPLY = FavoriteOnReplyMode.DISABLED;

	public static FavoriteOnReplyMode getFavoriteOnReply() {
		return getEnumValue(KEY_FAVORITE_ON_REPLY, FavoriteOnReplyMode.values(),
				DEFAULT_FAVORITE_ON_REPLY, FavoriteOnReplyMode.VALUE_PROVIDER);
	}

	static {
		if (PREFERENCES != null) {
			String key = "favorite_on_reply";
			Object value = PREFERENCES.getAll().get(key);
			if (value instanceof Boolean) {
				FavoriteOnReplyMode favoriteOnReplyMode = (boolean) value
						? FavoriteOnReplyMode.ENABLED : FavoriteOnReplyMode.DISABLED;
				PREFERENCES.edit().remove(key).put(KEY_FAVORITE_ON_REPLY, favoriteOnReplyMode.value).close();
			}
		}
	}

	public enum FavoritesOrder {
		DATE_DESC("date_desc", R.string.add_to_top__imperfective),
		DATE_ASC("date_asc", R.string.add_to_bottom__imperfective),
		TITLE("title", R.string.order_by_title);

		private static final EnumValueProvider<FavoritesOrder> VALUE_PROVIDER = o -> o.value;

		public final String value;
		public final int titleResId;

		FavoritesOrder(String value, int titleResId) {
			this.value = value;
			this.titleResId = titleResId;
		}
	}

	public static final String KEY_FAVORITES_ORDER = "favorites_order";
	public static final FavoritesOrder DEFAULT_FAVORITES_ORDER = FavoritesOrder.DATE_DESC;

	public static FavoritesOrder getFavoritesOrder() {
		return getEnumValue(KEY_FAVORITES_ORDER, FavoritesOrder.values(),
				DEFAULT_FAVORITES_ORDER, FavoritesOrder.VALUE_PROVIDER);
	}

	public static final String KEY_HIDE_PERSONAL_DATA = "hide_personal_data";
	public static final boolean DEFAULT_HIDE_PERSONAL_DATA = false;

	public static boolean isHidePersonalData() {
		return PREFERENCES.getBoolean(KEY_HIDE_PERSONAL_DATA, DEFAULT_HIDE_PERSONAL_DATA);
	}

	public enum HighlightUnreadMode {
		AUTOMATICALLY("automatically", R.string.hide_eventually__imperfective),
		MANUALLY("manually", R.string.hide_on_tap__imperfective),
		NEVER("never", R.string.never_highlight);

		private static final EnumValueProvider<HighlightUnreadMode> VALUE_PROVIDER = o -> o.value;

		public final String value;
		public final int titleResId;

		HighlightUnreadMode(String value, int titleResId) {
			this.value = value;
			this.titleResId = titleResId;
		}
	}

	public static final String KEY_HIGHLIGHT_UNREAD = "highlight_unread_posts";
	public static final HighlightUnreadMode DEFAULT_HIGHLIGHT_UNREAD = HighlightUnreadMode.AUTOMATICALLY;

	public static HighlightUnreadMode getHighlightUnreadMode() {
		return getEnumValue(KEY_HIGHLIGHT_UNREAD, HighlightUnreadMode.values(),
				DEFAULT_HIGHLIGHT_UNREAD, HighlightUnreadMode.VALUE_PROVIDER);
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
		PREFERENCES.edit().put(KEY_LAST_UPDATE_CHECK, lastUpdateCheck).close();
	}

	public static final ChanKey KEY_LOAD_CATALOG = new ChanKey("load_catalog");
	public static final boolean DEFAULT_LOAD_CATALOG = false;

	public static boolean isLoadCatalog(Chan chan) {
		return PREFERENCES.getBoolean(KEY_LOAD_CATALOG.bind(chan.name), DEFAULT_LOAD_CATALOG);
	}

	public static final String KEY_LOAD_NEAREST_IMAGE = "load_nearest_image";
	public static final NetworkMode DEFAULT_LOAD_NEAREST_IMAGE = NetworkMode.NEVER;

	public static NetworkMode getLoadNearestImage() {
		return getNetworkModeGeneric(KEY_LOAD_NEAREST_IMAGE, DEFAULT_LOAD_NEAREST_IMAGE);
	}

	public static final String KEY_LOAD_THUMBNAILS = "load_thumbnails";
	public static final NetworkMode DEFAULT_LOAD_THUMBNAILS = NetworkMode.ALWAYS;

	public static NetworkMode getLoadThumbnails() {
		return getNetworkModeGeneric(KEY_LOAD_THUMBNAILS, DEFAULT_LOAD_THUMBNAILS);
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
		PREFERENCES.edit().put(KEY_LOCK_DRAWER, locked).close();
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

	public enum PagesListMode {
		PAGES_FIRST("pages_first", R.string.pages_first),
		FAVORITES_FIRST("favorites_first", R.string.favorites_first),
		HIDE_PAGES("hide_pages", R.string.hide_pages);

		public static final EnumValueProvider<PagesListMode> VALUE_PROVIDER = o -> o.value;

		public final String value;
		public final int titleResId;

		PagesListMode(String value, int titleResId) {
			this.value = value;
			this.titleResId = titleResId;
		}
	}

	public static final String KEY_PAGES_LIST = "pages_list";
	public static final PagesListMode DEFAULT_PAGES_LIST = PagesListMode.PAGES_FIRST;

	public static PagesListMode getPagesListMode() {
		return getEnumValue(KEY_PAGES_LIST, PagesListMode.values(),
				DEFAULT_PAGES_LIST, PagesListMode.VALUE_PROVIDER);
	}

	public static final ChanKey KEY_PARTIAL_THREAD_LOADING = new ChanKey("partial_thread_loading");
	public static final boolean DEFAULT_PARTIAL_THREAD_LOADING = true;

	public static boolean isPartialThreadLoading(Chan chan) {
		if (chan.configuration.getOption(ChanConfiguration.OPTION_READ_THREAD_PARTIALLY)) {
			return PREFERENCES.getBoolean(KEY_PARTIAL_THREAD_LOADING.bind(chan.name),
					DEFAULT_PARTIAL_THREAD_LOADING);
		} else {
			return false;
		}
	}

	public static final ChanKey KEY_PASSWORD = new ChanKey("password");

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

	public static String getPassword(Chan chan) {
		String key = KEY_PASSWORD.bind(chan.name);
		String password = PREFERENCES.getString(key, null);
		if (StringUtils.isEmpty(password)) {
			password = generatePassword();
			PREFERENCES.edit().put(key, password).close();
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

	public static final ChanKey KEY_PROXY = new ChanKey("proxy");
	public static final String SUB_KEY_PROXY_HOST = "host";
	public static final String SUB_KEY_PROXY_PORT = "port";
	public static final String SUB_KEY_PROXY_TYPE = "type";
	public static final List<String> KEYS_PROXY = Arrays
			.asList(SUB_KEY_PROXY_HOST, SUB_KEY_PROXY_PORT, SUB_KEY_PROXY_TYPE);
	public static final String VALUE_PROXY_TYPE_HTTP = "http";
	public static final String VALUE_PROXY_TYPE_SOCKS = "socks";
	public static final List<CharSequence> ENTRIES_PROXY_TYPE = Arrays.asList("HTTP", "SOCKS");
	public static final List<String> VALUES_PROXY_TYPE = Arrays
			.asList(VALUE_PROXY_TYPE_HTTP, VALUE_PROXY_TYPE_SOCKS);

	public static Map<String, String> getProxy(Chan chan) {
		if (chan.configuration.getOption(ChanConfiguration.OPTION_LOCAL_MODE)) {
			return null;
		}
		String value = PREFERENCES.getString(KEY_PROXY.bind(chan.name), null);
		return unpackOrCastMultipleValues(value, KEYS_PROXY);
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
		PREFERENCES.edit().put(KEY_SHOWCASE_GALLERY, false).close();
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
		PREFERENCES.edit().put(KEY_SFW_MODE, sfwMode).close();
	}

	public static final String KEY_SHOW_MY_POSTS = "show_my_posts";
	public static final boolean DEFAULT_SHOW_MY_POSTS = true;

	public static boolean isShowMyPosts() {
		return PREFERENCES.getBoolean(KEY_SHOW_MY_POSTS, DEFAULT_SHOW_MY_POSTS);
	}

	public static void setShowMyPosts(boolean showMyPosts) {
		PREFERENCES.edit().put(KEY_SHOW_MY_POSTS, showMyPosts).close();
	}

	public static final String KEY_SHOW_SPOILERS = "show_spoilers";
	public static final boolean DEFAULT_SHOW_SPOILERS = false;

	public static boolean isShowSpoilers() {
		return PREFERENCES.getBoolean(KEY_SHOW_SPOILERS, DEFAULT_SHOW_SPOILERS);
	}

	public static void setShowSpoilers(boolean showSpoilers) {
		PREFERENCES.edit().put(KEY_SHOW_SPOILERS, showSpoilers).close();
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

	public static final String KEY_TEXT_SCALE = "text_scale";
	public static final int MIN_TEXT_SCALE = 75;
	public static final int MAX_TEXT_SCALE = 200;
	public static final int STEP_TEXT_SCALE = 5;
	public static final int DEFAULT_TEXT_SCALE = 100;

	public static float getTextScale() {
		return Math.max(MIN_TEXT_SCALE, Math.min(PREFERENCES.getInt(KEY_TEXT_SCALE,
				DEFAULT_TEXT_SCALE), MAX_TEXT_SCALE)) / 100f;
	}

	public static final String KEY_THEME = "theme";

	public static String getTheme() {
		return PREFERENCES.getString(KEY_THEME, null);
	}

	public static void setTheme(String value) {
		PREFERENCES.edit().put(KEY_THEME, value).close();
	}

	public enum ThreadsView {
		LIST("list", R.id.menu_list, R.string.list),
		CARDS("cards", R.id.menu_cards, R.string.cards),
		LARGE_GRID("large_grid", R.id.menu_large_grid, R.string.large_grid),
		SMALL_GRID("small_grid", R.id.menu_small_grid, R.string.small_grid);

		private static final EnumValueProvider<ThreadsView> VALUE_PROVIDER = o -> o.value;

		private final String value;
		public final int menuItemId;
		public final int titleResId;

		ThreadsView(String value, int menuItemId, int titleResId) {
			this.value = value;
			this.menuItemId = menuItemId;
			this.titleResId = titleResId;
		}
	}

	public static final String KEY_THREADS_VIEW = "threads_view";
	public static final ThreadsView DEFAULT_THREADS_VIEW = ThreadsView.CARDS;

	public static ThreadsView getThreadsView() {
		return getEnumValue(KEY_THREADS_VIEW, ThreadsView.values(), DEFAULT_THREADS_VIEW, ThreadsView.VALUE_PROVIDER);
	}

	public static void setThreadsView(ThreadsView threadsView) {
		PREFERENCES.edit().put(KEY_THREADS_VIEW, threadsView != null ? threadsView.value : null).close();
	}

	static {
		if (PREFERENCES != null) {
			Object threadsGridMode = PREFERENCES.getAll().get("threads_grid_mode");
			if (threadsGridMode instanceof Boolean) {
				String value = (boolean) threadsGridMode ? ThreadsView.LARGE_GRID.value : ThreadsView.CARDS.value;
				PREFERENCES.edit().remove("threads_grid_mode").put(KEY_THREADS_VIEW, value).close();
			}
		}
	}

	public static final String KEY_THUMBNAILS_SCALE = "thumbnails_scale";
	public static final int MIN_THUMBNAILS_SCALE = 100;
	public static final int MAX_THUMBNAILS_SCALE = 200;
	public static final int STEP_THUMBNAILS_SCALE = 10;
	public static final int DEFAULT_THUMBNAILS_SCALE = 100;

	public static float getThumbnailsScale() {
		return Math.max(MIN_THUMBNAILS_SCALE, Math.min(PREFERENCES.getInt(KEY_THUMBNAILS_SCALE,
				DEFAULT_THUMBNAILS_SCALE), MAX_THUMBNAILS_SCALE)) / 100f;
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
		PREFERENCES.edit().put(KEY_TRUSTED_EXSTENSIONS, packageNameFingerprints).close();
	}

	public static final String KEY_USE_GMS_PROVIDER = "use_gms_provider";
	public static final boolean DEFAULT_USE_GMS_PROVIDER = false;

	public static boolean isUseGmsProvider() {
		return PREFERENCES.getBoolean(KEY_USE_GMS_PROVIDER, DEFAULT_USE_GMS_PROVIDER);
	}

	public static final ChanKey KEY_USE_HTTPS = new ChanKey("use_https");
	public static final String KEY_USE_HTTPS_GENERAL = KEY_USE_HTTPS.bind(SPECIAL_CHAN_NAME_GENERAL);
	public static final boolean DEFAULT_USE_HTTPS = true;

	public static boolean isUseHttps(Chan chan) {
		return PREFERENCES.getBoolean(KEY_USE_HTTPS.bind(chan.name), DEFAULT_USE_HTTPS);
	}

	public static void setUseHttps(Chan chan, boolean useHttps) {
		PREFERENCES.edit().put(KEY_USE_HTTPS.bind(chan.name), useHttps).close();
	}

	public static boolean isUseHttpsGeneral() {
		return PREFERENCES.getBoolean(KEY_USE_HTTPS_GENERAL, DEFAULT_USE_HTTPS);
	}

	public static final String KEY_USE_VIDEO_PLAYER = "use_video_player";
	public static final boolean DEFAULT_USE_VIDEO_PLAYER = false;

	public static boolean isUseVideoPlayer() {
		return PREFERENCES.getBoolean(KEY_USE_VIDEO_PLAYER, DEFAULT_USE_VIDEO_PLAYER);
	}

	public static final String KEY_USER_AGENT_REFERENCE = "user_agent_reference";

	public static String getUserAgentReference() {
		return PREFERENCES.getString(KEY_USER_AGENT_REFERENCE, null);
	}

	public static void setUserAgentReference(String userAgentReference) {
		PREFERENCES.edit().put(KEY_USER_AGENT_REFERENCE, userAgentReference).close();
	}

	public static final ChanKey KEY_USER_AUTHORIZATION = new ChanKey("user_authorization");

	public static List<String> getUserAuthorizationData(Chan chan) {
		ChanConfiguration.Authorization authorization = chan.configuration.safe().obtainUserAuthorization();
		if (authorization != null && authorization.fieldsCount > 0) {
			String value = PREFERENCES.getString(KEY_USER_AUTHORIZATION.bind(chan.name), null);
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

	public enum VideoCompletionMode {
		NOTHING("nothing", R.string.do_nothing),
		LOOP("loop", R.string.play_again);

		private static final EnumValueProvider<VideoCompletionMode> VALUE_PROVIDER = o -> o.value;

		public final String value;
		public final int titleResId;

		VideoCompletionMode(String value, int titleResId) {
			this.value = value;
			this.titleResId = titleResId;
		}
	}

	public static final String KEY_VIDEO_COMPLETION = "video_completion";
	public static final VideoCompletionMode DEFAULT_VIDEO_COMPLETION = VideoCompletionMode.NOTHING;

	public static VideoCompletionMode getVideoCompletionMode() {
		return getEnumValue(KEY_VIDEO_COMPLETION, VideoCompletionMode.values(),
				DEFAULT_VIDEO_COMPLETION, VideoCompletionMode.VALUE_PROVIDER);
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

	public static final String KEY_WATCHER_REFRESH_INTERVAL = "watcher_refresh_interval";
	public static final int DISABLED_WATCHER_REFRESH_INTERVAL = 0;
	public static final int MIN_WATCHER_REFRESH_INTERVAL = 15;
	public static final int MAX_WATCHER_REFRESH_INTERVAL = 60;
	public static final int STEP_WATCHER_REFRESH_INTERVAL = 5;
	public static final int DEFAULT_WATCHER_REFRESH_INTERVAL = 30;

	public static int getWatcherRefreshInterval() {
		int value = PREFERENCES.getInt(KEY_WATCHER_REFRESH_INTERVAL, DEFAULT_WATCHER_REFRESH_INTERVAL);
		return value > MAX_WATCHER_REFRESH_INTERVAL ? MAX_WATCHER_REFRESH_INTERVAL
				: value < MIN_WATCHER_REFRESH_INTERVAL ? DISABLED_WATCHER_REFRESH_INTERVAL : value;
	}

	static {
		if (PREFERENCES != null) {
			String key = "watcher_refresh_periodically";
			Object value = PREFERENCES.getAll().get(key);
			if (value instanceof Boolean) {
				try (SharedPreferences.Editor editor = PREFERENCES.edit()) {
					editor.remove(key);
					if (!((boolean) value)) {
						editor.put(KEY_WATCHER_REFRESH_INTERVAL, DISABLED_WATCHER_REFRESH_INTERVAL);
					}
				}
			}
		}
	}

	public enum NotificationFeature {
		ENABLED("enabled", R.string.enabled),
		IMPORTANT("important", R.string.important__plural),
		SOUND("sound", R.string.sound),
		VIBRATION("vibration", R.string.vibration);

		public final String value;
		public final int titleResId;

		NotificationFeature(String value, int titleResId) {
			this.value = value;
			this.titleResId = titleResId;
		}

		private static NotificationFeature find(String value) {
			for (NotificationFeature notificationFeature : values()) {
				if (notificationFeature.value.equals(value)) {
					return notificationFeature;
				}
			}
			return null;
		}
	}

	public static final String KEY_WATCHER_NOTIFICATIONS = "watcher_notifications";
	public static final Set<NotificationFeature> DEFAULT_WATCHER_NOTIFICATIONS = Collections
			.unmodifiableSet(new HashSet<>(Arrays.asList(NotificationFeature.IMPORTANT,
					NotificationFeature.SOUND, NotificationFeature.VIBRATION)));

	public static Set<NotificationFeature> getWatcherNotifications() {
		Set<String> strings = PREFERENCES.getStringSet(KEY_WATCHER_NOTIFICATIONS, null);
		if (strings == null) {
			return DEFAULT_WATCHER_NOTIFICATIONS;
		} else {
			HashSet<NotificationFeature> notificationFeatures = new HashSet<>(strings.size());
			for (String value : strings) {
				NotificationFeature notificationFeature = NotificationFeature.find(value);
				if (notificationFeature != null) {
					notificationFeatures.add(notificationFeature);
				}
			}
			return notificationFeatures;
		}
	}

	public static void setWatcherNotifications(Collection<NotificationFeature> notificationFeatures) {
		Set<String> strings;
		if (notificationFeatures == null || notificationFeatures.isEmpty()) {
			strings = Collections.emptySet();
		} else {
			strings = new HashSet<>();
			for (NotificationFeature notificationFeature : notificationFeatures) {
				strings.add(notificationFeature.value);
			}
		}
		PREFERENCES.edit().put(KEY_WATCHER_NOTIFICATIONS, strings).close();
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

	public static final String KEY_SWIPE_TO_HIDE_THREAD = "swipe_to_hide_thread";
	public static final boolean DEFAULT_SWIPE_TO_HIDE_THREAD = false;

	public static boolean isSwipeToHideThreadEnabled(){
		return PREFERENCES.getBoolean(KEY_SWIPE_TO_HIDE_THREAD, DEFAULT_SWIPE_TO_HIDE_THREAD);
	}

}
