/*
 * Copyright 2017 Fukurou Mishiranu
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

package com.mishiranu.dashchan.util;

import android.util.Pair;
import android.webkit.MimeTypeMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import chan.util.StringUtils;

public class MimeTypes {
	private static final HashMap<String, String> MIME_TYPE_MAP = new HashMap<>();
	private static final HashMap<String, String> EXTENSION_MAP = new HashMap<>();

	static {
		ArrayList<Pair<String, String>> pairs = new ArrayList<>();

		pairs.add(new Pair<>("ogg", "application/ogg"));
		pairs.add(new Pair<>("pdf", "application/pdf"));
		pairs.add(new Pair<>("apk", "application/vnd.android.package-archive"));
		pairs.add(new Pair<>("xhtml", "application/xhtml+xml"));
		pairs.add(new Pair<>("swf", "application/x-shockwave-flash"));
		pairs.add(new Pair<>("tar", "application/x-tar"));
		pairs.add(new Pair<>("zip", "application/zip"));
		pairs.add(new Pair<>("aac", "audio/aac"));
		pairs.add(new Pair<>("flac", "audio/flac"));
		pairs.add(new Pair<>("mid", "audio/midi"));
		pairs.add(new Pair<>("midi", "audio/midi"));
		pairs.add(new Pair<>("mp3", "audio/mpeg"));
		pairs.add(new Pair<>("wav", "audio/x-wav"));
		pairs.add(new Pair<>("gif", "image/gif"));
		pairs.add(new Pair<>("jpeg", "image/jpeg"));
		pairs.add(new Pair<>("jpe", "image/jpeg"));
		pairs.add(new Pair<>("jpg", "image/jpeg"));
		pairs.add(new Pair<>("png", "image/png"));
		pairs.add(new Pair<>("apng", "image/png"));
		pairs.add(new Pair<>("svg", "image/svg+xml"));
		pairs.add(new Pair<>("svgz", "image/svg+xml"));
		pairs.add(new Pair<>("webp", "image/webp"));
		pairs.add(new Pair<>("ico", "image/x-icon"));
		pairs.add(new Pair<>("bmp", "image/x-ms-bmp"));
		pairs.add(new Pair<>("css", "text/css"));
		pairs.add(new Pair<>("html", "text/html"));
		pairs.add(new Pair<>("htm", "text/html"));
		pairs.add(new Pair<>("txt", "text/plain"));
		pairs.add(new Pair<>("xml", "text/xml"));
		pairs.add(new Pair<>("mp4", "video/mp4"));
		pairs.add(new Pair<>("webm", "video/webm"));

		for (Pair<String, String> pair : pairs) {
			MIME_TYPE_MAP.put(pair.first, pair.second);
		}
		for (Pair<String, String> pair : pairs) {
			if (!EXTENSION_MAP.containsKey(pair.second)) {
				EXTENSION_MAP.put(pair.second, pair.first);
			}
		}
	}

	public static String forExtension(String extension, String defaultMimeType) {
		if (!StringUtils.isEmpty(extension)) {
			extension = extension.toLowerCase(Locale.US);
			String mimeType = MIME_TYPE_MAP.get(extension);
			if (mimeType == null) {
				mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
			}
			if (mimeType != null) {
				return mimeType;
			}
		}
		return defaultMimeType;
	}

	public static String forExtension(String extension) {
		return forExtension(extension, null);
	}

	public static String toExtension(String mimeType, String defaultExtension) {
		if (!StringUtils.isEmpty(mimeType)) {
			String extension = EXTENSION_MAP.get(mimeType);
			if (extension == null) {
				extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
			}
			if (extension != null) {
				return extension;
			}
		}
		return defaultExtension;
	}

	public static String toExtension(String mimeType) {
		return toExtension(mimeType, null);
	}
}
