package chan.content.model;

import android.net.Uri;
import chan.annotation.Public;
import chan.util.StringUtils;
import com.mishiranu.dashchan.content.net.EmbeddedType;

@Public
public final class EmbeddedAttachment implements Attachment {
	private static final long serialVersionUID = 1L;

	@Public
	public enum ContentType {
		@Public AUDIO,
		@Public VIDEO
	}

	private final String mFileUriString;
	private final String mThumbnailUriString;
	private final String mEmbeddedType;
	private final ContentType mContentType;
	private final boolean mCanDownload;
	private final String mForcedName;

	private String mTitle;

	@Public
	public EmbeddedAttachment(Uri fileUri, Uri thumbnailUri, String embeddedType, ContentType contentType,
			boolean canDownload, String forcedName) {
		if (fileUri == null) {
			throw new IllegalArgumentException("fileUri is null");
		}
		if (embeddedType == null) {
			throw new IllegalArgumentException("embeddedType is null");
		}
		if (contentType == null) {
			throw new IllegalArgumentException("contentType is null");
		}
		mFileUriString = fileUri != null ? fileUri.toString() : null;
		mThumbnailUriString = thumbnailUri != null ? thumbnailUri.toString() : null;
		mEmbeddedType = embeddedType;
		mContentType = contentType;
		mCanDownload = canDownload;
		mForcedName = forcedName;
	}

	@Public
	public Uri getFileUri() {
		return mFileUriString != null ? Uri.parse(mFileUriString) : null;
	}

	@Public
	public Uri getThumbnailUri() {
		return mThumbnailUriString != null ? Uri.parse(mThumbnailUriString) : null;
	}

	@Public
	public String getEmbeddedType() {
		return mEmbeddedType;
	}

	@Public
	public ContentType getContentType() {
		return mContentType;
	}

	@Public
	public boolean isCanDownload() {
		return mCanDownload;
	}

	@Public
	public String getForcedName() {
		return mForcedName;
	}

	public String getNormalizedForcedName() {
		String forcedName = getForcedName();
		if (forcedName != null) {
			return StringUtils.escapeFile(forcedName, false);
		}
		return null;
	}

	public String getTitle() {
		return mTitle;
	}

	public EmbeddedAttachment setTitle(String title) {
		mTitle = title;
		return this;
	}

	@Public
	public static EmbeddedAttachment obtain(String data) {
		return EmbeddedType.extractAttachment(data);
	}

	public boolean contentEquals(EmbeddedAttachment o) {
		return StringUtils.equals(mFileUriString, o.mFileUriString) &&
				StringUtils.equals(mThumbnailUriString, o.mThumbnailUriString) &&
				StringUtils.equals(mEmbeddedType, o.mEmbeddedType) &&
				mContentType == o.mContentType &&
				mCanDownload == o.mCanDownload &&
				StringUtils.equals(mForcedName, o.mForcedName);
	}
}
