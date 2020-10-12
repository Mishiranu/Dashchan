package chan.content.model;

import android.net.Uri;
import chan.annotation.Public;
import chan.content.ChanLocator;
import chan.util.StringUtils;

@Public
public final class Icon {
	private final Uri uri;
	private final String title;

	@Public
	public Icon(ChanLocator locator, Uri uri, String title) {
		this(uri != null ? locator.makeRelative(uri) : null, StringUtils.nullIfEmpty(title));
	}

	Icon(Uri uri, String title) {
		this.uri = uri;
		this.title = StringUtils.nullIfEmpty(title);
	}

	Uri getUri() {
		return uri;
	}

	@Public
	public Uri getUri(ChanLocator locator) {
		return uri != null ? locator.convert(uri) : null;
	}

	@Public
	public String getTitle() {
		return title;
	}
}
