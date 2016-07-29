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

package com.mishiranu.dashchan;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

import android.os.Build;

public class C
{
	public static final boolean API_JELLY_BEAN_MR1 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1;
	public static final boolean API_JELLY_BEAN_MR2 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2;
	public static final boolean API_KITKAT = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
	public static final boolean API_LOLLIPOP = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
	public static final boolean API_MARSHMALLOW = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
	
	public static final HashSet<String> IMAGE_EXTENSIONS = new HashSet<>();
	public static final HashSet<String> AUDIO_EXTENSIONS = new HashSet<>();
	public static final HashSet<String> VIDEO_EXTENSIONS = new HashSet<>();
	
	public static final HashMap<String, String> EXTENSION_TRANSFORMATION = new HashMap<>();
	
	public static final boolean WEB_VIEW_BITMAP_DECODER_SUPPORTED;
	
	static
	{
		Collections.addAll(IMAGE_EXTENSIONS, "jpg", "jpe", "jpeg", "png", "apng", "gif", "webp", "bmp");
		Collections.addAll(AUDIO_EXTENSIONS, "mp3", "ogg", "flac", "wav");
		Collections.addAll(VIDEO_EXTENSIONS, "webm", "mp4");
		WEB_VIEW_BITMAP_DECODER_SUPPORTED = API_KITKAT;
		if (WEB_VIEW_BITMAP_DECODER_SUPPORTED) IMAGE_EXTENSIONS.add("svg");
		EXTENSION_TRANSFORMATION.put("jpg", "jpeg");
		EXTENSION_TRANSFORMATION.put("jpe", "jpeg");
		EXTENSION_TRANSFORMATION.put("apng", "png");
	}
	
	public static final String UPDATE_SOURCE_URI_STRING = "//raw.githubusercontent.com/Mishiranu/Dashchan/master/"
			+ "update/data.json";
	
	public static final String DEFAULT_DOWNLOAD_PATH = "/Download/Dashchan/";
	
	public static final String USER_AGENT = "Mozilla/5.0 (Android 6.0; Mobile; rv:47.0) Gecko/20100101 Firefox/47.0";
	
	public static final String API_KEY_GOOGLE = BuildConfig.API_KEY_GOOGLE;
	public static final String API_KEY_SOUNDCLOUD = BuildConfig.API_KEY_SOUNDCLOUD;
	
	public static final int HIDDEN_UNKNOWN = 0;
	public static final int HIDDEN_FALSE = 1;
	public static final int HIDDEN_TRUE = 2;
	
	public static final String ACTION_POST_SENT = "com.mishiranu.dashchan.action.POST_SENT";
	public static final String ACTION_GALLERY_GO_TO_POST = "com.mishiranu.dashchan.action.GALLERY_GO_TO_POST";
	
	public static final int REQUEST_CODE_ATTACH = 1;
	public static final int REQUEST_CODE_UNINSTALL = 2;
	public static final int REQUEST_CODE_OPEN_PATH = 3;
	
	public static final String NOTIFICATION_TAG_UPDATE = "update";
	public static final String NOTIFICATION_TAG_POSTING = "posting";
	
	public static final int NOTIFICATION_ID_DOWNLOAD = 1;
	public static final int NOTIFICATION_ID_AUDIO_PLAYER = 2;
	
	public static final String EXTRA_ALLOW_EXPANDED_SCREEN = "com.mishiranu.dashchan.extra.ALLOW_EXPANDED_SCREEN";
	public static final String EXTRA_ALLOW_GO_TO_POST = "com.mishiranu.dashchan.extra.ALLOW_GO_TO_POST";
	public static final String EXTRA_ANIMATED_TRANSITION = "com.mishiranu.dashchan.extra.ANIMATED_TRANSITION";
	public static final String EXTRA_BOARD_NAME = "com.mishiranu.dashchan.extra.BOARD_NAME";
	public static final String EXTRA_CHAN_NAME = "com.mishiranu.dashchan.extra.CHAN_NAME";
	public static final String EXTRA_COMMENT = "com.mishiranu.dashchan.extra.COMMENT";
	public static final String EXTRA_FAIL_RESULT = "com.mishiranu.dashchan.extra.FAIL_RESULT";
	public static final String EXTRA_FROM_CACHE = "com.mishiranu.dashchan.extra.FROM_CACHE";
	public static final String EXTRA_FROM_CLIENT = "com.mishiranu.dashchan.extra.FROM_CLIENT";
	public static final String EXTRA_GALLERY_ITEM = "com.mishiranu.dashchan.extra.EXTRA_GALLERY_ITEM";
	public static final String EXTRA_GALLERY_MODE = "com.mishiranu.dashchan.extra.GALLERY_MODE";
	public static final String EXTRA_IMAGE_INDEX = "com.mishiranu.dashchan.extra.IMAGE_INDEX";
	public static final String EXTRA_LAUNCHER = "com.mishiranu.dashchan.extra.LAUNCHER";
	public static final String EXTRA_NAVIGATE_TOP = "com.mishiranu.dashchan.extra.NAVIGATE_TOP";
	public static final String EXTRA_NEW_THREAD = "com.mishiranu.dashchan.extra.NEW_THREAD";
	public static final String EXTRA_OBTAIN_ITEMS = "com.mishiranu.dashchan.extra.EXTRA_OBTAIN_ITEMS";
	public static final String EXTRA_POST_NUMBER = "com.mishiranu.dashchan.extra.POST_NUMBER";
	public static final String EXTRA_REPLY_DATA = "com.mishiranu.dashchan.extra.REPLY_DATA";
	public static final String EXTRA_SEARCH_QUERY = "com.mishiranu.dashchan.extra.SEARCH_QUERY";
	public static final String EXTRA_SELECTED_FILE = "com.mishiranu.dashchan.extra.SELECTED_FILE";
	public static final String EXTRA_THREAD_NUMBER = "com.mishiranu.dashchan.extra.THREAD_NUMBER";
	public static final String EXTRA_TIMESTAMP = "com.mishiranu.dashchan.extra.TIMESTAMP";
	public static final String EXTRA_THREAD_TITLE = "com.mishiranu.dashchan.extra.THREAD_TITLE";
	public static final String EXTRA_VIEW_POSITION = "com.mishiranu.dashchan.extra.VIEW_POSITION";
}