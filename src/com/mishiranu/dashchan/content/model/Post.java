package com.mishiranu.dashchan.content.model;

import android.net.Uri;
import chan.text.JsonSerial;
import chan.text.ParseException;
import chan.util.StringUtils;
import com.mishiranu.dashchan.util.FlagUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Post implements Comparable<Post> {
	public interface Attachment {
		final class File implements Attachment {
			public final Uri fileUri;
			public final Uri thumbnailUri;
			public final String originalName;

			public final int size;
			public final int width;
			public final int height;
			public final boolean spoiler;

			private File(Uri fileUri, Uri thumbnailUri, String originalName,
					int size, int width, int height, boolean spoiler) {
				this.fileUri = fileUri;
				this.thumbnailUri = thumbnailUri;
				this.originalName = originalName;
				this.size = size;
				this.width = width;
				this.height = height;
				this.spoiler = spoiler;
			}

			public static File createExternal(Uri fileUri, Uri thumbnailUri, String originalName,
					int size, int width, int height, boolean spoiler) {
				if (fileUri != null || thumbnailUri != null) {
					return new File(fileUri, thumbnailUri, StringUtils.emptyIfNull(originalName),
							size, width, height, spoiler);
				} else {
					return null;
				}
			}
		}

		final class Embedded implements Attachment {
			public enum ContentType {AUDIO, VIDEO}

			public final Uri fileUri;
			public final Uri thumbnailUri;
			public final String embeddedType;
			public final ContentType contentType;
			public final boolean canDownload;
			public final String forcedName;

			private Embedded(Uri fileUri, Uri thumbnailUri, String embeddedType, ContentType contentType,
					boolean canDownload, String forcedName) {
				this.fileUri = fileUri;
				this.thumbnailUri = thumbnailUri;
				this.embeddedType = embeddedType;
				this.contentType = contentType;
				this.canDownload = canDownload;
				this.forcedName = forcedName;
			}

			private static boolean validate(boolean throwOnFail,
					Uri fileUri, String embeddedType, ContentType contentType) {
				if (fileUri == null) {
					if (throwOnFail) {
						throw new IllegalArgumentException("fileUri is null");
					} else {
						return false;
					}
				}
				if (StringUtils.isEmpty(embeddedType)) {
					if (throwOnFail) {
						throw new IllegalArgumentException("embeddedType is empty");
					} else {
						return false;
					}
				}
				if (contentType == null) {
					if (throwOnFail) {
						throw new IllegalArgumentException("contentType is null");
					} else {
						return false;
					}
				}
				return true;
			}

			public static Embedded createExternal(boolean throwOnFail, Uri fileUri, Uri thumbnailUri,
					String embeddedType, ContentType contentType, boolean canDownload, String forcedName) {
				if (!validate(throwOnFail, fileUri, embeddedType, contentType)) {
					return null;
				}
				return new Embedded(fileUri, thumbnailUri, StringUtils.emptyIfNull(embeddedType),
						contentType, canDownload, StringUtils.emptyIfNull(forcedName));
			}
		}
	}

	public static final class Icon {
		public final Uri uri;
		public final String title;

		private Icon(Uri uri, String title) {
			this.uri = uri;
			this.title = title;
		}

		public static Icon createExternal(Uri uri, String title) {
			if (uri != null && !StringUtils.isEmpty(title)) {
				return new Icon(uri, title);
			} else {
				return null;
			}
		}
	}

	private interface Flags {
		int SAGE = 0x00000001;
		int STICKY = 0x00000002;
		int CLOSED = 0x00000004;
		int ARCHIVED = 0x00000008;
		int CYCLICAL = 0x00000010;
		int POSTER_WARNED = 0x00000020;
		int POSTER_BANNED = 0x00000040;
		int ORIGINAL_POSTER = 0x00000080;
		int DEFAULT_NAME = 0x00000100;
		int BUMP_LIMIT_REACHED = 0x00000200;
	}

	public final PostNumber number;
	public final boolean deleted;
	private final int flags;
	public final long timestamp;
	public final String subject;
	public final String comment;
	public final String commentMarkup;
	public final String name;
	public final String identifier;
	public final String tripcode;
	public final String capcode;
	public final String email;
	public final List<Attachment> attachments;
	public final List<Icon> icons;

	private Post(PostNumber number, boolean deleted, int flags, long timestamp,
			String subject, String comment, String commentMarkup,
			String name, String identifier, String tripcode, String capcode, String email,
			List<Attachment> attachments, List<Icon> icons) {
		this.number = number;
		this.deleted = deleted;
		this.flags = flags;
		this.timestamp = timestamp;
		this.subject = subject;
		this.comment = comment;
		this.commentMarkup = commentMarkup;
		this.name = name;
		this.identifier = identifier;
		this.tripcode = tripcode;
		this.capcode = capcode;
		this.email = email;
		this.attachments = attachments;
		this.icons = icons;
	}

	public boolean isSage() {
		return FlagUtils.get(flags, Flags.SAGE);
	}

	public boolean isSticky() {
		return FlagUtils.get(flags, Flags.STICKY);
	}

	public boolean isClosed() {
		return FlagUtils.get(flags, Flags.CLOSED);
	}

	public boolean isArchived() {
		return FlagUtils.get(flags, Flags.ARCHIVED);
	}

	public boolean isCyclical() {
		return FlagUtils.get(flags, Flags.CYCLICAL);
	}

	public boolean isPosterWarned() {
		return FlagUtils.get(flags, Flags.POSTER_WARNED);
	}

	public boolean isPosterBanned() {
		return FlagUtils.get(flags, Flags.POSTER_BANNED);
	}

	public boolean isOriginalPoster() {
		return FlagUtils.get(flags, Flags.ORIGINAL_POSTER);
	}

	public boolean isDefaultName() {
		return FlagUtils.get(flags, Flags.DEFAULT_NAME);
	}

	public boolean isBumpLimitReached() {
		return FlagUtils.get(flags, Flags.BUMP_LIMIT_REACHED);
	}

	@Override
	public int compareTo(Post another) {
		return number.compareTo(another.number);
	}

	public void serialize(JsonSerial.Writer writer) throws IOException {
		writer.startObject();
		writer.name("flags");
		writer.value(flags);
		writer.name("timestamp");
		writer.value(timestamp);
		if (!subject.isEmpty()) {
			writer.name("subject");
			writer.value(subject);
		}
		if (!comment.isEmpty()) {
			writer.name("comment");
			writer.value(comment);
		}
		if (!commentMarkup.isEmpty()) {
			writer.name("commentMarkup");
			writer.value(commentMarkup);
		}
		if (!name.isEmpty()) {
			writer.name("name");
			writer.value(name);
		}
		if (!identifier.isEmpty()) {
			writer.name("identifier");
			writer.value(identifier);
		}
		if (!tripcode.isEmpty()) {
			writer.name("tripcode");
			writer.value(tripcode);
		}
		if (!capcode.isEmpty()) {
			writer.name("capcode");
			writer.value(capcode);
		}
		if (!email.isEmpty()) {
			writer.name("email");
			writer.value(email);
		}
		if (!attachments.isEmpty()) {
			writer.name("attachments");
			writer.startArray();
			for (Attachment attachment : attachments) {
				if (attachment instanceof Attachment.File) {
					Attachment.File file = (Attachment.File) attachment;
					writer.startObject();
					writer.name("type");
					writer.value("file");
					if (file.fileUri != null) {
						writer.name("fileUri");
						writer.value(file.fileUri.toString());
					}
					if (file.thumbnailUri != null) {
						writer.name("thumbnailUri");
						writer.value(file.thumbnailUri.toString());
					}
					if (!file.originalName.isEmpty()) {
						writer.name("originalName");
						writer.value(file.originalName);
					}
					writer.name("size");
					writer.value(file.size);
					writer.name("width");
					writer.value(file.width);
					writer.name("height");
					writer.value(file.height);
					writer.name("spoiler");
					writer.value(file.spoiler);
					writer.endObject();
				} else if (attachment instanceof Attachment.Embedded) {
					Attachment.Embedded embedded = (Attachment.Embedded) attachment;
					writer.startObject();
					writer.name("type");
					writer.value("embedded");
					if (embedded.fileUri != null) {
						writer.name("fileUri");
						writer.value(embedded.fileUri.toString());
					}
					if (embedded.thumbnailUri != null) {
						writer.name("thumbnailUri");
						writer.value(embedded.thumbnailUri.toString());
					}
					if (!embedded.embeddedType.isEmpty()) {
						writer.name("embeddedType");
						writer.value(embedded.embeddedType);
					}
					if (embedded.contentType != null) {
						writer.name("contentType");
						writer.value(embedded.contentType.toString());
					}
					writer.name("canDownload");
					writer.value(embedded.canDownload);
					if (!embedded.forcedName.isEmpty()) {
						writer.name("forcedName");
						writer.value(embedded.forcedName);
					}
					writer.endObject();
				}
			}
			writer.endArray();
		}
		if (!icons.isEmpty()) {
			writer.name("icons");
			writer.startArray();
			for (Icon icon : icons) {
				writer.startObject();
				if (icon.uri != null) {
					writer.name("uri");
					writer.value(icon.uri.toString());
				}
				if (!icon.title.isEmpty()) {
					writer.name("title");
					writer.value(icon.title);
				}
				writer.endObject();
			}
			writer.endArray();
		}
		writer.endObject();
	}

	public static Post deserialize(PostNumber number, boolean deleted, JsonSerial.Reader reader)
			throws IOException, ParseException {
		int flags = 0;
		long timestamp = 0;
		String subject = "";
		String comment = "";
		String commentMarkup = "";
		String name = "";
		String identifier = "";
		String tripcode = "";
		String capcode = "";
		String email = "";
		List<Attachment> attachments = Collections.emptyList();
		List<Icon> icons = Collections.emptyList();
		reader.startObject();
		while (!reader.endStruct()) {
			switch (reader.nextName()) {
				case "flags": {
					flags = reader.nextInt();
					break;
				}
				case "timestamp": {
					timestamp = reader.nextLong();
					break;
				}
				case "subject": {
					subject = reader.nextString();
					break;
				}
				case "comment": {
					comment = reader.nextString();
					break;
				}
				case "commentMarkup": {
					commentMarkup = reader.nextString();
					break;
				}
				case "name": {
					name = reader.nextString();
					break;
				}
				case "identifier": {
					identifier = reader.nextString();
					break;
				}
				case "tripcode": {
					tripcode = reader.nextString();
					break;
				}
				case "capcode": {
					capcode = reader.nextString();
					break;
				}
				case "email": {
					email = reader.nextString();
					break;
				}
				case "attachments": {
					reader.startArray();
					attachments = new ArrayList<>();
					while (!reader.endStruct()) {
						reader.startObject();
						String type = "";
						Uri fileUri = null;
						Uri thumbnailUri = null;
						String originalName = "";
						int size = 0;
						int width = 0;
						int height = 0;
						boolean spoiler = false;
						String embeddedType = "";
						Attachment.Embedded.ContentType contentType = null;
						boolean canDownload = false;
						String forcedName = "";
						while (!reader.endStruct()) {
							switch (reader.nextName()) {
								case "type": {
									type = reader.nextString();
									break;
								}
								case "fileUri": {
									fileUri = Uri.parse(reader.nextString());
									break;
								}
								case "thumbnailUri": {
									thumbnailUri = Uri.parse(reader.nextString());
									break;
								}
								case "originalName": {
									originalName = reader.nextString();
									break;
								}
								case "size": {
									size = reader.nextInt();
									break;
								}
								case "width": {
									width = reader.nextInt();
									break;
								}
								case "height": {
									height = reader.nextInt();
									break;
								}
								case "spoiler": {
									spoiler = reader.nextBoolean();
									break;
								}
								case "embeddedType": {
									embeddedType = reader.nextString();
									break;
								}
								case "contentType": {
									try {
										contentType = Attachment.Embedded.ContentType.valueOf(reader.nextString());
									} catch (IllegalArgumentException e) {
										// Ignore
									}
									break;
								}
								case "canDownload": {
									canDownload = reader.nextBoolean();
									break;
								}
								case "forcedName": {
									forcedName = reader.nextString();
									break;
								}
								default: {
									reader.skip();
									break;
								}
							}
						}
						if ("file".equals(type)) {
							attachments.add(new Attachment.File(fileUri, thumbnailUri, originalName,
									size, width, height, spoiler));
						} else if ("embedded".equals(type) &&
								Attachment.Embedded.validate(false, fileUri, embeddedType, contentType)) {
							attachments.add(new Attachment.Embedded(fileUri, thumbnailUri,
									embeddedType, contentType, canDownload, forcedName));
						}
					}
					break;
				}
				case "icons": {
					reader.startArray();
					icons = new ArrayList<>();
					while (!reader.endStruct()) {
						reader.startObject();
						Uri uri = null;
						String title = "";
						while (!reader.endStruct()) {
							switch (reader.nextName()) {
								case "uri": {
									uri = Uri.parse(reader.nextString());
									break;
								}
								case "title": {
									title = reader.nextString();
									break;
								}
								default: {
									reader.skip();
									break;
								}
							}
						}
						icons.add(new Icon(uri, title));
					}
					break;
				}
				default: {
					reader.skip();
					break;
				}
			}
		}
		return new Post(number, deleted, flags, timestamp, subject, comment, commentMarkup,
				name, identifier, tripcode, capcode, email, attachments, icons);
	}

	public static final class Builder {
		public PostNumber number;
		private int flags;
		public long timestamp;
		public String subject;
		public String comment;
		public String commentMarkup;
		public String name;
		public String identifier;
		public String tripcode;
		public String capcode;
		public String email;
		public List<Attachment> attachments;
		public List<Icon> icons;

		public boolean isSage() {
			return FlagUtils.get(flags, Flags.SAGE);
		}

		public void setSage(boolean sage) {
			flags = FlagUtils.set(flags, Flags.SAGE, sage);
		}

		public boolean isSticky() {
			return FlagUtils.get(flags, Flags.STICKY);
		}

		public void setSticky(boolean sticky) {
			flags = FlagUtils.set(flags, Flags.STICKY, sticky);
		}

		public boolean isClosed() {
			return FlagUtils.get(flags, Flags.CLOSED);
		}

		public void setClosed(boolean closed) {
			flags = FlagUtils.set(flags, Flags.CLOSED, closed);
		}

		public boolean isArchived() {
			return FlagUtils.get(flags, Flags.ARCHIVED);
		}

		public void setArchived(boolean archived) {
			flags = FlagUtils.set(flags, Flags.ARCHIVED, archived);
		}

		public boolean isCyclical() {
			return FlagUtils.get(flags, Flags.CYCLICAL);
		}

		public void setCyclical(boolean cyclical) {
			flags = FlagUtils.set(flags, Flags.CYCLICAL, cyclical);
		}

		public boolean isPosterWarned() {
			return FlagUtils.get(flags, Flags.POSTER_WARNED);
		}

		public void setPosterWarned(boolean posterWarned) {
			flags = FlagUtils.set(flags, Flags.POSTER_WARNED, posterWarned);
		}

		public boolean isPosterBanned() {
			return FlagUtils.get(flags, Flags.POSTER_BANNED);
		}

		public void setPosterBanned(boolean posterBanned) {
			flags = FlagUtils.set(flags, Flags.POSTER_BANNED, posterBanned);
		}

		public boolean isOriginalPoster() {
			return FlagUtils.get(flags, Flags.ORIGINAL_POSTER);
		}

		public void setOriginalPoster(boolean originalPoster) {
			flags = FlagUtils.set(flags, Flags.ORIGINAL_POSTER, originalPoster);
		}

		public boolean isDefaultName() {
			return FlagUtils.get(flags, Flags.DEFAULT_NAME);
		}

		public void setDefaultName(boolean defaultName) {
			flags = FlagUtils.set(flags, Flags.DEFAULT_NAME, defaultName);
		}

		public boolean isBumpLimitReached() {
			return FlagUtils.get(flags, Flags.BUMP_LIMIT_REACHED);
		}

		public void setBumpLimitReached(boolean bumpLimitReached) {
			flags = FlagUtils.set(flags, Flags.BUMP_LIMIT_REACHED, bumpLimitReached);
		}

		public Post build(boolean deleted) {
			PostNumber number = this.number;
			if (number == null) {
				throw new IllegalStateException("Post number is null");
			}
			String subject = StringUtils.emptyIfNull(this.subject);
			String comment = StringUtils.emptyIfNull(this.comment);
			String commentMarkup = StringUtils.emptyIfNull(this.commentMarkup);
			String name = StringUtils.emptyIfNull(this.name);
			String identifier = StringUtils.emptyIfNull(this.identifier);
			String tripcode = StringUtils.emptyIfNull(this.tripcode);
			String capcode = StringUtils.emptyIfNull(this.capcode);
			String email = StringUtils.emptyIfNull(this.email);
			List<Attachment> attachments = this.attachments;
			if (attachments == null) {
				attachments = Collections.emptyList();
			}
			List<Icon> icons = this.icons;
			if (icons == null) {
				icons = Collections.emptyList();
			}
			return new Post(number, deleted, flags, timestamp, subject, comment, commentMarkup,
					name, identifier, tripcode, capcode, email, attachments, icons);
		}
	}
}
