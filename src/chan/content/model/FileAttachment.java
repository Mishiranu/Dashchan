package chan.content.model;

import android.net.Uri;
import chan.annotation.Public;
import chan.content.ChanLocator;
import chan.util.StringUtils;

@Public
public final class FileAttachment implements Attachment {
	private Uri fileUri;
	private Uri thumbnailUri;
	private String originalName;

	private int size;
	private int width;
	private int height;
	private boolean spoiler;

	@Public
	public FileAttachment() {}

	Uri getFileUri() {
		return fileUri;
	}

	@Public
	public Uri getFileUri(ChanLocator locator) {
		return locator.convert(locator.fixRelativeFileUri(fileUri));
	}

	@Public
	public FileAttachment setFileUri(ChanLocator locator, Uri fileUri) {
		this.fileUri = fileUri != null ? locator.makeRelative(fileUri) : null;
		return this;
	}

	Uri getThumbnailUri() {
		return thumbnailUri;
	}

	@Public
	public Uri getThumbnailUri(ChanLocator locator) {
		return locator.convert(locator.fixRelativeFileUri(thumbnailUri));
	}

	@Public
	public FileAttachment setThumbnailUri(ChanLocator locator, Uri thumbnailUri) {
		this.thumbnailUri = thumbnailUri != null ? locator.makeRelative(thumbnailUri) : null;
		return this;
	}

	@Public
	public String getOriginalName() {
		return originalName;
	}

	@Public
	public FileAttachment setOriginalName(String originalName) {
		this.originalName = StringUtils.nullIfEmpty(originalName);
		return this;
	}

	@Public
	public int getSize() {
		return size;
	}

	@Public
	public FileAttachment setSize(int size) {
		this.size = size;
		return this;
	}

	@Public
	public int getWidth() {
		return width;
	}

	@Public
	public FileAttachment setWidth(int width) {
		this.width = width;
		return this;
	}

	@Public
	public int getHeight() {
		return height;
	}

	@Public
	public FileAttachment setHeight(int height) {
		this.height = height;
		return this;
	}

	@Public
	public boolean isSpoiler() {
		return spoiler;
	}

	@Public
	public FileAttachment setSpoiler(boolean spoiler) {
		this.spoiler = spoiler;
		return this;
	}
}
