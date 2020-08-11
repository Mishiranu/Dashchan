package com.mishiranu.dashchan;

import android.os.Build;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class C {
	public static final boolean API_JELLY_BEAN_MR1 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1;
	public static final boolean API_JELLY_BEAN_MR2 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2;
	public static final boolean API_KITKAT = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
	public static final boolean API_LOLLIPOP = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
	public static final boolean API_LOLLIPOP_MR1 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1;
	public static final boolean API_MARSHMALLOW = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
	public static final boolean API_NOUGAT = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
	public static final boolean API_PIE = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P;

	public static final String BUILD_VERSION = BuildConfig.VERSION_NAME;
	public static final long BUILD_TIMESTAMP = BuildConfig.BUILD_TIMESTAMP;

	public static final Set<String> IMAGE_EXTENSIONS;
	public static final Set<String> AUDIO_EXTENSIONS;
	public static final Set<String> VIDEO_EXTENSIONS;

	public static final Set<String> OPENABLE_VIDEO_EXTENSIONS;

	public static final Map<String, String> EXTENSION_TRANSFORMATION;

	public static final boolean WEB_VIEW_BITMAP_DECODER_SUPPORTED;

	@SafeVarargs
	private static <T> Set<T> immutableSet(T... items) {
		HashSet<T> hashSet = new HashSet<>();
		for (T item : items) {
			if (item != null) {
				hashSet.add(item);
			}
		}
		return Collections.unmodifiableSet(hashSet);
	}

	static {
		WEB_VIEW_BITMAP_DECODER_SUPPORTED = API_KITKAT;
		IMAGE_EXTENSIONS = immutableSet("jpg", "jpe", "jpeg", "png", "apng", "gif", "webp", "bmp",
				WEB_VIEW_BITMAP_DECODER_SUPPORTED ? "svg" : null);
		AUDIO_EXTENSIONS = immutableSet("mp3", "ogg", "flac", "wav");
		VIDEO_EXTENSIONS = immutableSet("webm", "mp4");
		OPENABLE_VIDEO_EXTENSIONS = immutableSet("webm", "mp4");
		HashMap<String, String> extensionTransformation = new HashMap<>();
		extensionTransformation.put("jpg", "jpeg");
		extensionTransformation.put("jpe", "jpeg");
		extensionTransformation.put("apng", "png");
		EXTENSION_TRANSFORMATION = Collections.unmodifiableMap(extensionTransformation);
	}

	public static final String UPDATE_SOURCE_URI_STRING = "//raw.githubusercontent.com/Mishiranu/Dashchan/master/"
			+ "update/data.json";

	public static final String DEFAULT_DOWNLOAD_PATH = "/Download/Dashchan/";

	public static final int HIDDEN_UNKNOWN = 0;
	public static final int HIDDEN_FALSE = 1;
	public static final int HIDDEN_TRUE = 2;

	public static final String ACTION_POST_SENT = "com.mishiranu.dashchan.action.POST_SENT";
	public static final String ACTION_GALLERY_NAVIGATE_POST = "com.mishiranu.dashchan.action.GALLERY_NAVIGATE_POST";

	public static final int REQUEST_CODE_ATTACH = 1;
	public static final int REQUEST_CODE_UNINSTALL = 2;
	public static final int REQUEST_CODE_OPEN_PATH = 3;

	public static final String NOTIFICATION_TAG_UPDATE = "update";
	public static final String NOTIFICATION_TAG_POSTING = "posting";

	public static final int NOTIFICATION_ID_DOWNLOAD = 1;
	public static final int NOTIFICATION_ID_AUDIO_PLAYER = 2;

	public static final String EXTRA_ALLOW_EXPANDED_SCREEN = "com.mishiranu.dashchan.extra.ALLOW_EXPANDED_SCREEN";
	public static final String EXTRA_BOARD_NAME = "com.mishiranu.dashchan.extra.BOARD_NAME";
	public static final String EXTRA_CHAN_NAME = "com.mishiranu.dashchan.extra.CHAN_NAME";
	public static final String EXTRA_EXTERNAL_BROWSER = "com.mishiranu.dashchan.extra.EXTERNAL_BROWSER";
	public static final String EXTRA_FAIL_RESULT = "com.mishiranu.dashchan.extra.FAIL_RESULT";
	public static final String EXTRA_FROM_CLIENT = "com.mishiranu.dashchan.extra.FROM_CLIENT";
	public static final String EXTRA_GALLERY_MODE = "com.mishiranu.dashchan.extra.GALLERY_MODE";
	public static final String EXTRA_IMAGE_INDEX = "com.mishiranu.dashchan.extra.IMAGE_INDEX";
	public static final String EXTRA_NAVIGATE_POST_MODE = "com.mishiranu.dashchan.extra.NAVIGATE_POST_MODE";
	public static final String EXTRA_NAVIGATION_FLAGS = "com.mishiranu.dashchan.extra.NAVIGATION_FLAGS";
	public static final String EXTRA_OBTAIN_ITEMS = "com.mishiranu.dashchan.extra.EXTRA_OBTAIN_ITEMS";
	public static final String EXTRA_POST_NUMBER = "com.mishiranu.dashchan.extra.POST_NUMBER";
	public static final String EXTRA_REPLY_DATA = "com.mishiranu.dashchan.extra.REPLY_DATA";
	public static final String EXTRA_SEARCH_QUERY = "com.mishiranu.dashchan.extra.SEARCH_QUERY";
	public static final String EXTRA_THREAD_NUMBER = "com.mishiranu.dashchan.extra.THREAD_NUMBER";
	public static final String EXTRA_THREAD_TITLE = "com.mishiranu.dashchan.extra.THREAD_TITLE";
	public static final String EXTRA_VIEW_POSITION = "com.mishiranu.dashchan.extra.VIEW_POSITION";
	public static final String EXTRA_UPDATE_DATA_MAP = "com.mishiranu.dashchan.extra.UPDATE_DATA_MAP";
}
