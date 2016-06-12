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

package chan.content.model;

import java.util.HashMap;

import org.json.JSONObject;

import android.net.Uri;

import chan.content.ChanLocator;
import chan.content.InvalidResponseException;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;
import chan.util.CommonUtils;
import chan.util.StringUtils;

import com.mishiranu.dashchan.C;

public final class EmbeddedAttachment implements Attachment
{
	private static final long serialVersionUID = 1L;
	
	public static enum ContentType {AUDIO, VIDEO}
	
	private final String mFileUriString;
	private final String mThumbnailUriString;
	private final String mEmbeddedType;
	private final ContentType mContentType;
	private final boolean mCanDownload;
	private final String mForcedName;
	
	private String mTitle;
	
	public EmbeddedAttachment(Uri fileUri, Uri thumbnailUri, String embeddedType, ContentType contentType,
			boolean canDownload, String forcedName)
	{
		if (fileUri == null) throw new IllegalArgumentException("fileUri is null");
		if (embeddedType == null) throw new IllegalArgumentException("embeddedType is null");
		if (contentType == null) throw new IllegalArgumentException("contentType is null");
		mFileUriString = fileUri != null ? fileUri.toString() : null;
		mThumbnailUriString = thumbnailUri != null ? thumbnailUri.toString() : null;
		mEmbeddedType = embeddedType;
		mContentType = contentType;
		mCanDownload = canDownload;
		mForcedName = forcedName;
	}
	
	public String getFileUriString()
	{
		return mFileUriString;
	}
	
	public Uri getFileUri()
	{
		return mFileUriString != null ? Uri.parse(mFileUriString) : null;
	}
	
	public String getThumbnailUriString()
	{
		return mFileUriString;
	}
	
	public Uri getThumbnailUri()
	{
		return mThumbnailUriString != null ? Uri.parse(mThumbnailUriString) : null;
	}
	
	public String getEmbeddedType()
	{
		return mEmbeddedType;
	}
	
	public ContentType getContentType()
	{
		return mContentType;
	}
	
	public boolean isCanDownload()
	{
		return mCanDownload;
	}
	
	public String getForcedName()
	{
		return mForcedName;
	}
	
	public String getNormalizedForcedName()
	{
		String forcedName = getForcedName();
		if (forcedName != null) return StringUtils.escapeFile(forcedName, false);
		return null;
	}
	
	public String getTitle()
	{
		return mTitle;
	}
	
	public EmbeddedAttachment setTitle(String title)
	{
		mTitle = title;
		return this;
	}
	
	public static EmbeddedAttachment obtain(String data)
	{
		if (data != null)
		{
			ChanLocator locator = ChanLocator.getDefault();
			String embeddedCode;
			embeddedCode = locator.getYouTubeEmbeddedCode(data);
			if (embeddedCode != null) return obtainYouTubeEmbedded(locator, embeddedCode);
			embeddedCode = locator.getVimeoEmbeddedCode(data);
			if (embeddedCode != null) return obtainVimeoEmbedded(locator, embeddedCode);
			embeddedCode = locator.getVocarooEmbeddedCode(data);
			if (embeddedCode != null) return obtainVocarooEmbedded(locator, embeddedCode);
			embeddedCode = locator.getSoundCloudEmbeddedCode(data);
			if (embeddedCode != null) return obtainSoundCloudEmbedded(locator, embeddedCode);
		}
		return null;
	}
	
	public static EmbeddedAttachment obtainYouTubeEmbedded(ChanLocator locator, String embeddedCode)
	{
		Uri fileUri = locator.buildQueryWithSchemeHost(true, "www.youtube.com", "watch", "v", embeddedCode);
		Uri thumbnailUri = locator.buildPathWithSchemeHost(true, "img.youtube.com", "vi", embeddedCode, "default.jpg");
		return new EmbeddedAttachment(fileUri, thumbnailUri, "YouTube", ContentType.VIDEO, false, null);
	}
	
	public static EmbeddedAttachment obtainVimeoEmbedded(ChanLocator locator, String embeddedCode)
	{
		Uri fileUri = locator.buildPathWithSchemeHost(true, "vimeo.com", embeddedCode);
		return new EmbeddedAttachment(fileUri, null, "Vimeo", ContentType.VIDEO, false, null);
	}
	
	public static EmbeddedAttachment obtainVocarooEmbedded(ChanLocator locator, String embeddedCode)
	{
		Uri fileUri = locator.buildQueryWithSchemeHost(false, "vocaroo.com", "media_command.php", "media", embeddedCode,
				"command", "download_mp3");
		String forcedName = "Vocaroo_" + embeddedCode + ".mp3";
		return new EmbeddedAttachment(fileUri, null, "Vocaroo", ContentType.AUDIO, true, forcedName);
	}
	
	public static EmbeddedAttachment obtainSoundCloudEmbedded(ChanLocator locator, String embeddedCode)
	{
		Uri fileUri = locator.buildPathWithSchemeHost(true, "soundcloud.com", embeddedCode);
		String forcedName = "SoundCloud_" + embeddedCode.replace('/', '_') + ".mp3";
		return new EmbeddedAttachment(fileUri, null, "SoundCloud", ContentType.AUDIO, true, forcedName);
	}
	
	private static final HashMap<String, Uri> SOUNDCLOUD_MAP = new HashMap<>();
	
	public static Uri doReadRealUri(String chanName, Uri uri, HttpHolder holder) throws HttpException,
			InvalidResponseException
	{
		if ("soundcloud.com".equals(uri.getHost()))
		{
			String path = uri.getPath();
			Uri resultUri;
			synchronized (SOUNDCLOUD_MAP)
			{
				resultUri = SOUNDCLOUD_MAP.get(path);
			}
			if (resultUri == null)
			{
				uri = ChanLocator.getDefault().buildQueryWithHost("api.soundcloud.com", "resolve.json",
						"url", uri.toString(), "client_id", C.API_KEY_SOUNDCLOUD);
				JSONObject jsonObject = new HttpRequest(uri, holder).setSuccessOnly(false).read().getJsonObject();
				if (jsonObject == null) throw new InvalidResponseException();
				String uriString = CommonUtils.optJsonString(jsonObject, "download_url");
				if (uriString == null) uriString = CommonUtils.optJsonString(jsonObject, "stream_url");
				if (uriString == null) throw new InvalidResponseException();
				resultUri = Uri.parse(uriString).buildUpon().scheme("http").appendQueryParameter("client_id",
						C.API_KEY_SOUNDCLOUD).build();
				synchronized (SOUNDCLOUD_MAP)
				{
					SOUNDCLOUD_MAP.put(path, resultUri);
				}
			}
			return resultUri;
		}
		return uri;
	}
	
	public boolean contentEquals(EmbeddedAttachment o)
	{
		return StringUtils.equals(mFileUriString, o.mFileUriString) && StringUtils.equals(mThumbnailUriString,
				o.mThumbnailUriString) && StringUtils.equals(mEmbeddedType, o.mEmbeddedType) &&
				mContentType == o.mContentType && mCanDownload == o.mCanDownload &&
				StringUtils.equals(mForcedName, o.mForcedName);
	}
}