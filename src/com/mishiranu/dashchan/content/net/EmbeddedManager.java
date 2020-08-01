package com.mishiranu.dashchan.content.net;

import android.net.Uri;
import chan.content.ChanLocator;
import chan.content.model.EmbeddedAttachment;

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
}
