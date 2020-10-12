package chan.content.model;

import chan.annotation.Public;
import chan.util.CommonUtils;
import chan.util.StringUtils;
import com.mishiranu.dashchan.content.model.PostNumber;
import java.util.ArrayList;
import java.util.Collection;

@Public
public final class Post implements Comparable<Post> {
	private final ChanBuilder builder;
	private final PostNumber postNumberCompat;

	@Public
	public Post() {
		builder = new ChanBuilder();
		postNumberCompat = null;
	}

	Post(PostNumber postNumber) {
		builder = null;
		postNumberCompat = postNumber;
	}

	@Public
	public String getThreadNumber() {
		return builder.threadNumber;
	}

	@Public
	public Post setThreadNumber(String threadNumber) {
		PostNumber.validateThreadNumber(threadNumber, true);
		builder.threadNumber = threadNumber;
		return this;
	}

	public String getParentPostNumberOrNull() {
		String parentPostNumber = getParentPostNumber();
		if (parentPostNumber == null || "0".equals(parentPostNumber) || parentPostNumber.equals(getPostNumber())) {
			return null;
		}
		return parentPostNumber;
	}

	@Public
	public String getParentPostNumber() {
		return builder.parentPostNumber;
	}

	@Public
	public Post setParentPostNumber(String parentPostNumber) {
		if (parentPostNumber != null) {
			PostNumber.parseOrThrow(parentPostNumber);
		}
		builder.parentPostNumber = parentPostNumber;
		return this;
	}

	@Public
	public String getPostNumber() {
		if (builder != null) {
			PostNumber number = builder.builder.number;
			return number != null ? number.toString() : null;
		} else {
			return postNumberCompat.toString();
		}
	}

	@Public
	public Post setPostNumber(String postNumber) {
		builder.builder.number = PostNumber.parseOrThrow(postNumber);
		return this;
	}

	public String getOriginalPostNumber() {
		String parentPostNumber = getParentPostNumberOrNull();
		return parentPostNumber != null ? parentPostNumber : getPostNumber();
	}

	public String getThreadNumberOrOriginalPostNumber() {
		String threadNumber = getThreadNumber();
		return threadNumber != null ? threadNumber : getOriginalPostNumber();
	}

	@Public
	public long getTimestamp() {
		return builder.builder.timestamp;
	}

	@Public
	public Post setTimestamp(long timestamp) {
		builder.builder.timestamp = timestamp;
		return this;
	}

	@Public
	public String getSubject() {
		return builder.builder.subject;
	}

	@Public
	public Post setSubject(String subject) {
		builder.builder.subject = StringUtils.nullIfEmpty(subject);
		return this;
	}

	@Public
	public String getComment() {
		return builder.builder.comment;
	}

	@Public
	public Post setComment(String comment) {
		builder.builder.comment = StringUtils.nullIfEmpty(comment);
		return this;
	}

	@Public
	public String getCommentMarkup() {
		return builder.builder.commentMarkup;
	}

	@Public
	public Post setCommentMarkup(String commentMarkup) {
		builder.builder.commentMarkup = StringUtils.nullIfEmpty(commentMarkup);
		return this;
	}

	@Public
	public String getName() {
		return builder.builder.name;
	}

	@Public
	public Post setName(String name) {
		builder.builder.name = StringUtils.nullIfEmpty(name);
		return this;
	}

	@Public
	public String getIdentifier() {
		return builder.builder.identifier;
	}

	@Public
	public Post setIdentifier(String identifier) {
		builder.builder.identifier = StringUtils.nullIfEmpty(identifier);
		return this;
	}

	@Public
	public String getTripcode() {
		return builder.builder.tripcode;
	}

	@Public
	public Post setTripcode(String tripcode) {
		builder.builder.tripcode = StringUtils.nullIfEmpty(tripcode);
		return this;
	}

	@Public
	public String getCapcode() {
		return builder.builder.capcode;
	}

	@Public
	public Post setCapcode(String capcode) {
		builder.builder.capcode = StringUtils.nullIfEmpty(capcode);
		return this;
	}

	@Public
	public String getEmail() {
		return builder.builder.email;
	}

	@Public
	public Post setEmail(String email) {
		builder.builder.email = StringUtils.nullIfEmpty(email);
		return this;
	}

	@Public
	public int getAttachmentsCount() {
		return builder.attachments != null ? builder.attachments.length : 0;
	}

	@Public
	public Attachment getAttachmentAt(int index) {
		return builder.attachments[index];
	}

	@Public
	public Post setAttachments(Attachment... attachments) {
		builder.attachments = CommonUtils.removeNullItems(attachments, Attachment.class);
		return this;
	}

	@Public
	public Post setAttachments(Collection<? extends Attachment> attachments) {
		return setAttachments(CommonUtils.toArray(attachments, Attachment.class));
	}

	@Public
	public int getIconsCount() {
		return builder.icons != null ? builder.icons.length : 0;
	}

	@Public
	public Icon getIconAt(int index) {
		return builder.icons[index];
	}

	@Public
	public Post setIcons(Icon... icons) {
		builder.icons = CommonUtils.removeNullItems(icons, Icon.class);
		return this;
	}

	@Public
	public Post setIcons(Collection<? extends Icon> icons) {
		return setIcons(CommonUtils.toArray(icons, Icon.class));
	}

	@Public
	public boolean isSage() {
		return builder.builder.isSage();
	}

	@Public
	public Post setSage(boolean sage) {
		builder.builder.setSage(sage);
		return this;
	}

	@Public
	public boolean isSticky() {
		return builder.builder.isSticky();
	}

	@Public
	public Post setSticky(boolean sticky) {
		builder.builder.setSticky(sticky);
		return this;
	}

	@Public
	public boolean isClosed() {
		return builder.builder.isClosed();
	}

	@Public
	public Post setClosed(boolean closed) {
		builder.builder.setClosed(closed);
		return this;
	}

	@Public
	public boolean isArchived() {
		return builder.builder.isArchived();
	}

	@Public
	public Post setArchived(boolean archived) {
		builder.builder.setArchived(archived);
		return this;
	}

	@Public
	public boolean isCyclical() {
		return builder.builder.isCyclical();
	}

	@Public
	public Post setCyclical(boolean cyclical) {
		builder.builder.setCyclical(cyclical);
		return this;
	}

	@Public
	public boolean isPosterWarned() {
		return builder.builder.isPosterWarned();
	}

	@Public
	public Post setPosterWarned(boolean posterWarned) {
		builder.builder.setPosterWarned(posterWarned);
		return this;
	}

	@Public
	public boolean isPosterBanned() {
		return builder.builder.isPosterBanned();
	}

	@Public
	public Post setPosterBanned(boolean posterBanned) {
		builder.builder.setPosterBanned(posterBanned);
		return this;
	}

	@Public
	public boolean isOriginalPoster() {
		return builder.builder.isOriginalPoster();
	}

	@Public
	public Post setOriginalPoster(boolean originalPoster) {
		builder.builder.setOriginalPoster(originalPoster);
		return this;
	}

	@Public
	public boolean isDefaultName() {
		return builder.builder.isDefaultName();
	}

	@Public
	public Post setDefaultName(boolean defaultName) {
		builder.builder.setDefaultName(defaultName);
		return this;
	}

	@Public
	public boolean isBumpLimitReached() {
		return builder.builder.isBumpLimitReached();
	}

	@Public
	public Post setBumpLimitReached(boolean bumpLimitReached) {
		builder.builder.setBumpLimitReached(bumpLimitReached);
		return this;
	}

	@Public
	@Override
	public int compareTo(Post another) {
		return builder.builder.number.compareTo(another.builder.builder.number);
	}

	private static final class ChanBuilder {
		public final com.mishiranu.dashchan.content.model.Post.Builder builder =
				new com.mishiranu.dashchan.content.model.Post.Builder();

		public String threadNumber;
		public String parentPostNumber;
		public Attachment[] attachments;
		public Icon[] icons;
	}

	public com.mishiranu.dashchan.content.model.Post build() {
		if (builder.attachments != null && builder.attachments.length > 0) {
			builder.builder.attachments = new ArrayList<>();
			for (Attachment attachment : builder.attachments) {
				if (attachment instanceof FileAttachment) {
					FileAttachment fileAttachment = (FileAttachment) attachment;
					com.mishiranu.dashchan.content.model.Post.Attachment.File file =
							com.mishiranu.dashchan.content.model.Post.Attachment.File
									.createExternal(fileAttachment.getFileUri(), fileAttachment.getThumbnailUri(),
											fileAttachment.getOriginalName(), fileAttachment.getSize(),
											fileAttachment.getWidth(), fileAttachment.getHeight(),
											fileAttachment.isSpoiler());
					if (file != null) {
						builder.builder.attachments.add(file);
					}
				} else if (attachment instanceof EmbeddedAttachment) {
					builder.builder.attachments.add(((EmbeddedAttachment) attachment).embedded);
				}
			}
		}
		if (builder.icons != null && builder.icons.length > 0) {
			builder.builder.icons = new ArrayList<>();
			for (Icon icon : builder.icons) {
				if (icon != null) {
					com.mishiranu.dashchan.content.model.Post.Icon postIcon =
							com.mishiranu.dashchan.content.model.Post.Icon
									.createExternal(icon.getUri(), icon.getTitle());
					if (postIcon != null) {
						builder.builder.icons.add(postIcon);
					}
				}
			}
		}
		return builder.builder.build(false);
	}
}
