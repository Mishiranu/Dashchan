package chan.content.model;

import android.net.Uri;
import chan.annotation.Public;
import com.mishiranu.dashchan.content.model.Post;
import com.mishiranu.dashchan.content.net.EmbeddedType;

@Public
public final class EmbeddedAttachment implements Attachment {
	@Public
	public enum ContentType {
		@Public AUDIO,
		@Public VIDEO
	}

	final Post.Attachment.Embedded embedded;

	private EmbeddedAttachment(Post.Attachment.Embedded embedded) {
		this.embedded = embedded;
	}

	@Public
	public EmbeddedAttachment(Uri fileUri, Uri thumbnailUri, String embeddedType, ContentType contentType,
			boolean canDownload, String forcedName) {
		Post.Attachment.Embedded.ContentType embeddedContentType;
		switch (contentType) {
			case AUDIO: {
				embeddedContentType = Post.Attachment.Embedded.ContentType.AUDIO;
				break;
			}
			case VIDEO: {
				embeddedContentType = Post.Attachment.Embedded.ContentType.VIDEO;
				break;
			}
			default: {
				embeddedContentType = null;
				break;
			}
		}
		embedded = Post.Attachment.Embedded.createExternal(true, fileUri, thumbnailUri, embeddedType,
				embeddedContentType, canDownload, forcedName);
	}

	@Public
	public Uri getFileUri() {
		return embedded.fileUri;
	}

	@Public
	public Uri getThumbnailUri() {
		return embedded.thumbnailUri;
	}

	@Public
	public String getEmbeddedType() {
		return embedded.embeddedType;
	}

	@Public
	public ContentType getContentType() {
		switch (embedded.contentType) {
			case AUDIO: {
				return ContentType.AUDIO;
			}
			case VIDEO: {
				return ContentType.VIDEO;
			}
			default: {
				return null;
			}
		}
	}

	@Public
	public boolean isCanDownload() {
		return embedded.canDownload;
	}

	@Public
	public String getForcedName() {
		return embedded.forcedName;
	}

	@Public
	public static EmbeddedAttachment obtain(String data) {
		Post.Attachment.Embedded embedded = EmbeddedType.extractAttachment(data);
		return embedded != null ? new EmbeddedAttachment(embedded) : null;
	}
}
