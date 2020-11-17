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
	public static final boolean API_NOUGAT_MR1 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1;
	public static final boolean API_OREO = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
	public static final boolean API_PIE = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P;
	public static final boolean API_Q = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
	public static final boolean API_R = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;

	public static final boolean USE_SAF = API_MARSHMALLOW;

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
		VIDEO_EXTENSIONS = immutableSet("webm", "mkv", "mp4");
		OPENABLE_VIDEO_EXTENSIONS = immutableSet("webm", "mkv", "mp4");
		HashMap<String, String> extensionTransformation = new HashMap<>();
		extensionTransformation.put("jpg", "jpeg");
		extensionTransformation.put("jpe", "jpeg");
		extensionTransformation.put("apng", "png");
		EXTENSION_TRANSFORMATION = Collections.unmodifiableMap(extensionTransformation);
	}

	public static final String DEFAULT_DOWNLOAD_PATH = "/Download/Dashchan/";

	public static final String ACTION_POSTING = "com.mishiranu.dashchan.action.POSTING";
	public static final String ACTION_GALLERY = "com.mishiranu.dashchan.action.GALLERY";
	public static final String ACTION_PLAYER = "com.mishiranu.dashchan.action.PLAYER";
	public static final String ACTION_BROWSER = "com.mishiranu.dashchan.action.BROWSER";

	public static final int REQUEST_CODE_ATTACH = 1;
	public static final int REQUEST_CODE_OPEN_URI_TREE = 2;

	public static final int NOTIFICATION_ID_POSTING = 1;
	public static final int NOTIFICATION_ID_DOWNLOADING = 2;
	public static final int NOTIFICATION_ID_AUDIO_PLAYER = 3;
	public static final int NOTIFICATION_ID_UPDATES = 4;
	public static final int NOTIFICATION_ID_REPLIES = 5;

	public static final String NOTIFICATION_CHANNEL_POSTING = "posting";
	public static final String NOTIFICATION_CHANNEL_POSTING_COMPLETE = "postingComplete";
	public static final String NOTIFICATION_CHANNEL_DOWNLOADING = "downloading";
	public static final String NOTIFICATION_CHANNEL_DOWNLOADING_COMPLETE = "downloadingComplete";
	public static final String NOTIFICATION_CHANNEL_AUDIO_PLAYER = "audioPlayer";
	public static final String NOTIFICATION_CHANNEL_UPDATES = "updates";
	public static final String NOTIFICATION_CHANNEL_REPLIES = "replies";

	public static final String EXTRA_BOARD_NAME = "com.mishiranu.dashchan.extra.BOARD_NAME";
	public static final String EXTRA_CHAN_NAME = "com.mishiranu.dashchan.extra.CHAN_NAME";
	public static final String EXTRA_FAIL_RESULT = "com.mishiranu.dashchan.extra.FAIL_RESULT";
	public static final String EXTRA_POST_NUMBER = "com.mishiranu.dashchan.extra.POST_NUMBER";
	public static final String EXTRA_FROM_CLIENT = "com.mishiranu.dashchan.extra.FROM_CLIENT";
	public static final String EXTRA_THREAD_NUMBER = "com.mishiranu.dashchan.extra.THREAD_NUMBER";
	public static final String EXTRA_UPDATE_DATA_MAP = "com.mishiranu.dashchan.extra.UPDATE_DATA_MAP";
}
