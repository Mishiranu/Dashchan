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

package com.mishiranu.dashchan.content.net;

import java.util.HashMap;

import org.json.JSONObject;

import android.net.Uri;

import chan.content.ChanLocator;
import chan.content.InvalidResponseException;
import chan.content.model.EmbeddedAttachment;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;
import chan.util.CommonUtils;
import chan.util.StringUtils;

import com.mishiranu.dashchan.C;

public class EmbeddedManager {
	private static final EmbeddedManager INSTANCE = new EmbeddedManager();

	private EmbeddedManager() {}

	public static EmbeddedManager getInstance() {
		return INSTANCE;
	}

	public EmbeddedAttachment obtainAttachment(String data) {
		if (data != null) {
			ChanLocator locator = ChanLocator.getDefault();
			String embeddedCode;
			embeddedCode = locator.getYouTubeEmbeddedCode(data);
			if (embeddedCode != null) {
				return obtainYouTubeAttachment(locator, embeddedCode);
			}
			embeddedCode = locator.getVimeoEmbeddedCode(data);
			if (embeddedCode != null) {
				return obtainVimeoAttachment(locator, embeddedCode);
			}
			embeddedCode = locator.getVocarooEmbeddedCode(data);
			if (embeddedCode != null) {
				return obtainVocarooAttachment(locator, embeddedCode);
			}
			embeddedCode = locator.getSoundCloudEmbeddedCode(data);
			if (embeddedCode != null) {
				return obtainSoundCloudAttachment(locator, embeddedCode);
			}
		}
		return null;
	}

	public EmbeddedAttachment obtainYouTubeAttachment(ChanLocator locator, String embeddedCode) {
		Uri fileUri = locator.buildQueryWithSchemeHost(true, "www.youtube.com", "watch", "v", embeddedCode);
		Uri thumbnailUri = locator.buildPathWithSchemeHost(true, "img.youtube.com", "vi", embeddedCode, "default.jpg");
		return new EmbeddedAttachment(fileUri, thumbnailUri, "YouTube", EmbeddedAttachment.ContentType.VIDEO,
				false, null);
	}

	public EmbeddedAttachment obtainVimeoAttachment(ChanLocator locator, String embeddedCode) {
		Uri fileUri = locator.buildPathWithSchemeHost(true, "vimeo.com", embeddedCode);
		return new EmbeddedAttachment(fileUri, null, "Vimeo", EmbeddedAttachment.ContentType.VIDEO, false, null);
	}

	public EmbeddedAttachment obtainVocarooAttachment(ChanLocator locator, String embeddedCode) {
		Uri fileUri = locator.buildQueryWithSchemeHost(false, "vocaroo.com", "media_command.php", "media", embeddedCode,
				"command", "download_mp3");
		String forcedName = "Vocaroo_" + embeddedCode + ".mp3";
		return new EmbeddedAttachment(fileUri, null, "Vocaroo", EmbeddedAttachment.ContentType.AUDIO, true, forcedName);
	}

	public EmbeddedAttachment obtainSoundCloudAttachment(ChanLocator locator, String embeddedCode) {
		Uri fileUri = locator.buildPathWithSchemeHost(true, "soundcloud.com", embeddedCode);
		String forcedName = "SoundCloud_" + embeddedCode.replace('/', '_') + ".mp3";
		return new EmbeddedAttachment(fileUri, null, "SoundCloud", EmbeddedAttachment.ContentType.AUDIO,
				true, forcedName);
	}

	private final HashMap<String, Uri> soundcloudUriMap = new HashMap<>();

	public Uri doReadRealUri(Uri uri, HttpHolder holder) throws HttpException, InvalidResponseException {
		if ("soundcloud.com".equals(uri.getHost()) && !StringUtils.isEmpty(C.API_KEY_SOUNDCLOUD)) {
			String path = uri.getPath();
			Uri resultUri;
			synchronized (soundcloudUriMap) {
				resultUri = soundcloudUriMap.get(path);
			}
			if (resultUri == null) {
				uri = ChanLocator.getDefault().buildQueryWithHost("api.soundcloud.com", "resolve.json",
						"url", uri.toString(), "client_id", C.API_KEY_SOUNDCLOUD);
				JSONObject jsonObject = new HttpRequest(uri, holder).setSuccessOnly(false).read().getJsonObject();
				if (jsonObject == null) {
					throw new InvalidResponseException();
				}
				String uriString = CommonUtils.optJsonString(jsonObject, "download_url");
				if (uriString == null) {
					uriString = CommonUtils.optJsonString(jsonObject, "stream_url");
				}
				if (uriString == null) {
					throw new InvalidResponseException();
				}
				resultUri = Uri.parse(uriString).buildUpon().scheme("http").appendQueryParameter("client_id",
						C.API_KEY_SOUNDCLOUD).build();
				synchronized (soundcloudUriMap) {
					soundcloudUriMap.put(path, resultUri);
				}
			}
			return resultUri;
		}
		return uri;
	}
}