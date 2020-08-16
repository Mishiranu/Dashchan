package com.mishiranu.dashchan.ui.navigator.manager;

import android.app.AlertDialog;
import android.content.Context;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.view.View;
import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.content.ChanManager;
import chan.util.StringUtils;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.model.AttachmentItem;
import com.mishiranu.dashchan.content.model.GalleryItem;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.text.style.LinkSuffixSpan;
import com.mishiranu.dashchan.ui.gallery.GalleryOverlay;
import com.mishiranu.dashchan.ui.posting.Replyable;
import com.mishiranu.dashchan.util.DialogMenu;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.NavigationUtils;
import com.mishiranu.dashchan.widget.AttachmentView;
import java.util.ArrayList;

public class InteractionUnit {
	private final UiManager uiManager;

	InteractionUnit(UiManager uiManager) {
		this.uiManager = uiManager;
	}

	public void handleLinkClick(UiManager.ConfigurationSet configurationSet,
			String chanName, Uri uri, boolean confirmed) {
		boolean handled = false;
		final String uriChanName = ChanManager.getInstance().getChanNameByHost(uri.getHost());
		if (uriChanName != null) {
			boolean sameChan = uriChanName.equals(chanName);
			ChanLocator.NavigationData navigationData = null;
			ChanLocator locator = ChanLocator.get(uriChanName);
			ChanConfiguration configuration = ChanConfiguration.get(uriChanName);
			if (locator.safe(false).isBoardUri(uri)) {
				navigationData = new ChanLocator.NavigationData(ChanLocator.NavigationData.TARGET_THREADS,
						locator.safe(false).getBoardName(uri), null, null, null);
				handled = true;
			} else if (locator.safe(false).isThreadUri(uri)) {
				String boardName = locator.safe(false).getBoardName(uri);
				String threadNumber = locator.safe(false).getThreadNumber(uri);
				String postNumber = locator.safe(false).getPostNumber(uri);
				if (threadNumber != null) {
					if (sameChan && configuration.getOption(ChanConfiguration.OPTION_READ_SINGLE_POST)) {
						uiManager.dialog().displayReplyAsync(configurationSet,
								uriChanName, boardName, threadNumber, postNumber);
					} else {
						navigationData = new ChanLocator.NavigationData(ChanLocator.NavigationData.TARGET_POSTS,
								boardName, threadNumber, postNumber, null);
					}
					handled = true;
				}
			} else if (sameChan) {
				navigationData = locator.safe(false).handleUriClickSpecial(uri);
				if (navigationData != null) {
					handled = true;
				}
			}
			if (handled && navigationData != null) {
				if (confirmed) {
					uiManager.navigator().navigateTarget(uriChanName, navigationData, NavigationUtils.FLAG_RETURNABLE);
				} else {
					int messageId = 0;
					if (sameChan) {
						switch (navigationData.target) {
							case ChanLocator.NavigationData.TARGET_THREADS: {
								messageId = R.string.message_open_threads_confirm;
								break;
							}
							case ChanLocator.NavigationData.TARGET_POSTS: {
								messageId = R.string.message_open_posts_confirm;
								break;
							}
							case ChanLocator.NavigationData.TARGET_SEARCH: {
								messageId = R.string.message_open_search_confirm;
								break;
							}
						}
					} else {
						messageId = R.string.message_open_link_confirm;
					}
					final ChanLocator.NavigationData navigationDataFinal = navigationData;
					AlertDialog dialog = new AlertDialog.Builder(uiManager.getContext())
							.setMessage(messageId)
							.setNegativeButton(android.R.string.cancel, null)
							.setPositiveButton(android.R.string.ok, (d, which) -> uiManager.navigator()
									.navigateTarget(uriChanName, navigationDataFinal, NavigationUtils.FLAG_RETURNABLE))
							.show();
					uiManager.getConfigurationLock().lockConfiguration(dialog);
					uiManager.dialog().notifySwitchBackground(configurationSet.stackInstance);
				}
			}
		}
		if (!handled) {
			NavigationUtils.handleUriInternal(uiManager.getContext(), chanName, uri);
		}
	}

	private static final int LINK_MENU_COPY = 0;
	private static final int LINK_MENU_SHARE = 1;
	private static final int LINK_MENU_BROWSER = 2;
	private static final int LINK_MENU_DOWNLOAD_FILE = 3;
	private static final int LINK_MENU_OPEN_THREAD = 4;

	public void handleLinkLongClick(UiManager.ConfigurationSet configurationSet, final Uri uri) {
		String uriChanName = ChanManager.getInstance().getChanNameByHost(uri.getHost());
		String fileName = null;
		String boardName = null;
		String threadNumber = null;
		boolean isAttachment = false;
		ChanLocator locator = null;
		if (uriChanName != null) {
			locator = ChanLocator.get(uriChanName);
			if (locator.safe(false).isAttachmentUri(uri)) {
				fileName = locator.createAttachmentFileName(uri);
				boardName = locator.safe(false).getBoardName(uri);
				threadNumber = locator.safe(false).getThreadNumber(uri);
				if (threadNumber == null) {
					boardName = null;
				}
				isAttachment = true;
			}
		}
		final String finalChanName = uriChanName;
		final String finalFileName = fileName;
		final String finalBoardName = boardName;
		final String finalThreadNumber = threadNumber;
		DialogMenu dialogMenu = new DialogMenu(uiManager.getContext(), (context, id) -> {
			switch (id) {
				case LINK_MENU_COPY: {
					StringUtils.copyToClipboard(context, uri.toString());
					break;
				}
				case LINK_MENU_SHARE: {
					NavigationUtils.shareLink(uiManager.getContext(), null, uri);
					break;
				}
				case LINK_MENU_BROWSER: {
					NavigationUtils.handleUri(uiManager.getContext(), finalChanName, uri,
							NavigationUtils.BrowserType.INTERNAL);
					break;
				}
				case LINK_MENU_DOWNLOAD_FILE: {
					uiManager.download(binder -> binder.downloadStorage(uri, finalFileName, null,
							finalChanName, finalBoardName, finalThreadNumber, null));
					break;
				}
				case LINK_MENU_OPEN_THREAD: {
					uiManager.navigator().navigatePosts(finalChanName, finalBoardName, finalThreadNumber,
							null, null, NavigationUtils.FLAG_RETURNABLE);
					break;
				}
			}
		});
		dialogMenu.addItem(LINK_MENU_COPY, R.string.action_copy_link);
		dialogMenu.addItem(LINK_MENU_SHARE, R.string.action_share_link);
		if (Preferences.isUseInternalBrowser() && (locator == null || !locator.safe(false).isBoardUri(uri)
				&& !locator.safe(false).isThreadUri(uri) && !locator.safe(false).isAttachmentUri(uri)
				&& locator.safe(false).handleUriClickSpecial(uri) == null)) {
			dialogMenu.addItem(LINK_MENU_BROWSER, R.string.action_browser);
		}
		if (isAttachment) {
			dialogMenu.addItem(LINK_MENU_DOWNLOAD_FILE, R.string.action_download_file);
		}
		if (threadNumber != null) {
			dialogMenu.addItem(LINK_MENU_OPEN_THREAD, R.string.action_open_thread);
		}
		dialogMenu.show(uiManager.getConfigurationLock());
		uiManager.dialog().notifySwitchBackground(configurationSet.stackInstance);
	}

	private static class ThumbnailClickListenerImpl implements UiManager.ThumbnailClickListener {
		private final UiManager uiManager;

		private int index;
		private boolean mayShowDialog;
		private GalleryOverlay.NavigatePostMode navigatePostMode;

		public ThumbnailClickListenerImpl(UiManager uiManager) {
			this.uiManager = uiManager;
		}

		@Override
		public void update(int index, boolean mayShowDialog, GalleryOverlay.NavigatePostMode navigatePostMode) {
			this.index = index;
			this.mayShowDialog = mayShowDialog;
			this.navigatePostMode = navigatePostMode;
		}

		@Override
		public void onClick(View v) {
			UiManager.Holder holder = ListViewUtils.getViewHolder(v, UiManager.Holder.class);
			ArrayList<AttachmentItem> attachmentItems = holder.postItem.getAttachmentItems();
			if (attachmentItems != null) {
				GalleryItem.GallerySet gallerySet = holder.getGallerySet();
				int startImageIndex = uiManager.view().findImageIndex(gallerySet.getItems(), holder.postItem);
				if (mayShowDialog) {
					uiManager.dialog().openAttachmentOrDialog(holder.configurationSet.stackInstance, v,
							attachmentItems, startImageIndex, navigatePostMode, gallerySet);
				} else {
					int index = this.index;
					int imageIndex = startImageIndex;
					for (int i = 0; i < index; i++) {
						if (attachmentItems.get(i).isShowInGallery()) {
							imageIndex++;
						}
					}
					uiManager.dialog().openAttachment(v, attachmentItems, index, imageIndex,
							navigatePostMode, gallerySet);
				}
			}
		}
	}

	private static class ThumbnailLongClickListenerImpl implements UiManager.ThumbnailLongClickListener {
		private final UiManager uiManager;
		private AttachmentItem attachmentItem;

		public ThumbnailLongClickListenerImpl(UiManager uiManager) {
			this.uiManager = uiManager;
		}

		@Override
		public void update(AttachmentItem attachmentItem) {
			this.attachmentItem = attachmentItem;
		}

		@Override
		public boolean onLongClick(View v) {
			UiManager.Holder holder = ListViewUtils.getViewHolder(v, UiManager.Holder.class);
			new ThumbnailLongClickDialog(uiManager, holder.configurationSet.stackInstance,
					attachmentItem, (AttachmentView) v, true, holder.getGallerySet().getThreadTitle());
			return true;
		}
	}

	public UiManager.ThumbnailClickListener createThumbnailClickListener() {
		return new ThumbnailClickListenerImpl(uiManager);
	}

	public UiManager.ThumbnailLongClickListener createThumbnailLongClickListener() {
		return new ThumbnailLongClickListenerImpl(uiManager);
	}

	private static class ThumbnailLongClickDialog implements DialogMenu.Callback {
		private final AttachmentItem attachmentItem;
		private final AttachmentView attachmentView;
		private final String threadTitle;
		private final UiManager uiManager;

		private static final int MENU_DOWNLOAD_FILE = 0;
		private static final int MENU_SEARCH_IMAGE = 1;
		private static final int MENU_SHOW_THUMBNAIL = 2;
		private static final int MENU_COPY_LINK = 3;
		private static final int MENU_SHARE_LINK = 4;

		public ThumbnailLongClickDialog(UiManager uiManager, DialogUnit.StackInstance stackInstance,
				AttachmentItem attachmentItem, AttachmentView attachmentView,
				boolean hasViewHolder, String threadTitle) {
			this.attachmentItem = attachmentItem;
			this.attachmentView = attachmentView;
			this.threadTitle = threadTitle;
			this.uiManager = uiManager;
			Context context = attachmentView.getContext();
			DialogMenu dialogMenu = new DialogMenu(context, this);
			dialogMenu.setTitle(attachmentItem.getDialogTitle(), true);
			if (attachmentItem.canDownloadToStorage()) {
				dialogMenu.addItem(MENU_DOWNLOAD_FILE, R.string.action_download_file);
				if (attachmentItem.getType() == AttachmentItem.TYPE_IMAGE ||
						attachmentItem.getThumbnailKey() != null) {
					dialogMenu.addItem(MENU_SEARCH_IMAGE, R.string.action_search_image);
				}
			}
			if (hasViewHolder && attachmentItem.canLoadThumbnailManually(attachmentView)) {
				dialogMenu.addItem(MENU_SHOW_THUMBNAIL, R.string.action_show_thumbnail);
			}
			dialogMenu.addItem(MENU_COPY_LINK, R.string.action_copy_link);
			dialogMenu.addItem(MENU_SHARE_LINK, R.string.action_share_link);
			dialogMenu.show(uiManager.getConfigurationLock());
			uiManager.dialog().notifySwitchBackground(stackInstance);
		}

		@Override
		public void onItemClick(Context context, int id) {
			Uri fileUri = attachmentItem.getFileUri();
			Uri thumbnailUri = attachmentItem.getThumbnailUri();
			int type = attachmentItem.getType();
			switch (id) {
				case MENU_DOWNLOAD_FILE: {
					uiManager.download(binder -> binder.downloadStorage(fileUri,
							attachmentItem.getFileName(), attachmentItem.getOriginalName(),
							attachmentItem.getChanName(), attachmentItem.getBoardName(),
							attachmentItem.getThreadNumber(), threadTitle));
					break;
				}
				case MENU_SEARCH_IMAGE: {
					NavigationUtils.searchImage(context, uiManager.getConfigurationLock(),
							ChanManager.getInstance().getChanNameByHost(fileUri.getAuthority()),
							type == AttachmentItem.TYPE_IMAGE ? fileUri : thumbnailUri);
					break;
				}
				case MENU_SHOW_THUMBNAIL: {
					attachmentItem.startLoad(attachmentView, true);
					break;
				}
				case MENU_COPY_LINK: {
					StringUtils.copyToClipboard(context, fileUri.toString());
					break;
				}
				case MENU_SHARE_LINK: {
					NavigationUtils.shareLink(context, null, fileUri);
					break;
				}
			}
		}
	}

	public void showThumbnailLongClickDialog(DialogUnit.StackInstance stackInstance, AttachmentItem attachmentItem,
			AttachmentView attachmentView, boolean hasViewHolder, String threadTitle) {
		new ThumbnailLongClickDialog(uiManager, stackInstance,
				attachmentItem, attachmentView, hasViewHolder, threadTitle);
	}

	public boolean handlePostClick(View view, PostItem postItem, Iterable<PostItem> localPostItems) {
		if (postItem.isHiddenUnchecked()) {
			uiManager.sendPostItemMessage(postItem, UiManager.MESSAGE_PERFORM_SWITCH_HIDE);
			return true;
		} else {
			if (Preferences.getHighlightUnreadMode() == Preferences.HIGHLIGHT_UNREAD_MANUALLY) {
				for (PostItem localPostItem : localPostItems) {
					if (localPostItem.isUnread()) {
						localPostItem.setUnread(false);
						uiManager.sendPostItemMessage(localPostItem, UiManager.MESSAGE_INVALIDATE_VIEW);
					}
					if (localPostItem == postItem) {
						break;
					}
				}
			}
			return uiManager.view().handlePostForDoubleClick(view);
		}
	}

	private static final int MENU_REPLY = 0;
	private static final int MENU_QUOTE = 1;
	private static final int MENU_COPY = 2;
	private static final int MENU_COPY_TEXT = 3;
	private static final int MENU_COPY_MARKUP = 4;
	private static final int MENU_COPY_LINK = 5;
	private static final int MENU_SHARE = 6;
	private static final int MENU_SHARE_LINK = 7;
	private static final int MENU_SHARE_TEXT = 8;
	private static final int MENU_REPORT = 9;
	private static final int MENU_DELETE = 10;
	private static final int MENU_ADD_REMOVE_MY_MARK = 11;
	private static final int MENU_GO_TO_POST = 12;
	private static final int MENU_HIDE = 13;
	private static final int MENU_HIDE_POST = 14;
	private static final int MENU_HIDE_REPLIES = 15;
	private static final int MENU_HIDE_NAME = 16;
	private static final int MENU_HIDE_SIMILAR = 17;

	public boolean handlePostContextMenu(final PostItem postItem, DialogUnit.StackInstance stackInstance,
			Replyable replyable, boolean allowMyMarkEdit, boolean allowHiding, boolean allowGoToPost) {
		if (postItem != null) {
			Context context = uiManager.getContext();
			String chanName = postItem.getChanName();
			ChanConfiguration configuration = ChanConfiguration.get(chanName);
			ChanConfiguration.Board board = configuration.safe().obtainBoard(postItem.getBoardName());
			boolean postEmpty = StringUtils.isEmpty(postItem.getComment().toString());
			final boolean copyText = !postEmpty;
			final boolean shareText = !postEmpty;
			DialogMenu dialogMenu = new DialogMenu(context, new DialogMenu.SimpleCallback() {
				@Override
				public void onItemClick(int id) {
					switch (id) {
						case MENU_REPLY: {
							replyable.onRequestReply(new Replyable.ReplyData(postItem.getPostNumber(), null));
							break;
						}
						case MENU_QUOTE: {
							replyable.onRequestReply(new Replyable.ReplyData(postItem.getPostNumber(),
									getCopyReadyComment(postItem.getComment())));
							break;
						}
						case MENU_COPY: {
							DialogMenu dialogMenu = new DialogMenu(context, this);
							if (copyText) {
								dialogMenu.addItem(MENU_COPY_TEXT, R.string.action_copy_text);
								dialogMenu.addItem(MENU_COPY_MARKUP, R.string.action_copy_markup);
							}
							dialogMenu.addItem(MENU_COPY_LINK, R.string.action_copy_link);
							dialogMenu.show(uiManager.getConfigurationLock());
							break;
						}
						case MENU_COPY_TEXT: {
							StringUtils.copyToClipboard(context, getCopyReadyComment(postItem.getComment()));
							break;
						}
						case MENU_COPY_MARKUP: {
							StringUtils.copyToClipboard(context, postItem.getCommentMarkup());
							break;
						}
						case MENU_COPY_LINK:
						case MENU_SHARE_LINK:
						case MENU_SHARE_TEXT: {
							ChanLocator locator = ChanLocator.get(postItem.getChanName());
							String boardName = postItem.getBoardName();
							String threadNumber = postItem.getThreadNumber();
							String postNumber = postItem.getPostNumber();
							Uri uri = postItem.getParentPostNumber() == null
									? locator.safe(true).createThreadUri(boardName, threadNumber)
									: locator.safe(true).createPostUri(boardName, threadNumber, postNumber);
							if (uri != null) {
								switch (id) {
									case MENU_COPY_LINK: {
										StringUtils.copyToClipboard(context, uri.toString());
										break;
									}
									case MENU_SHARE_LINK: {
										String subject = postItem.getSubjectOrComment();
										if (StringUtils.isEmptyOrWhitespace(subject)) {
											subject = uri.toString();
										}
										NavigationUtils.shareLink(context, subject, uri);
										break;
									}
									case MENU_SHARE_TEXT: {
										String subject = postItem.getSubjectOrComment();
										if (StringUtils.isEmptyOrWhitespace(subject)) {
											subject = uri.toString();
										}
										NavigationUtils.shareText(context, subject,
												getCopyReadyComment(postItem.getComment()), uri);
										break;
									}
								}
							}
							break;
						}
						case MENU_SHARE: {
							DialogMenu dialogMenu = new DialogMenu(context, this);
							if (shareText) {
								dialogMenu.addItem(MENU_SHARE_TEXT, R.string.action_share_text);
							}
							dialogMenu.addItem(MENU_SHARE_LINK, R.string.action_share_link);
							dialogMenu.show(uiManager.getConfigurationLock());
							break;
						}
						case MENU_REPORT: {
							ArrayList<String> postNumbers = new ArrayList<>(1);
							postNumbers.add(postItem.getPostNumber());
							uiManager.dialog().performSendReportPosts(postItem.getChanName(), postItem.getBoardName(),
									postItem.getThreadNumber(), postNumbers);
							break;
						}
						case MENU_DELETE: {
							ArrayList<String> postNumbers = new ArrayList<>(1);
							postNumbers.add(postItem.getPostNumber());
							uiManager.dialog().performSendDeletePosts(postItem.getChanName(), postItem.getBoardName(),
									postItem.getThreadNumber(), postNumbers);
							break;
						}
						case MENU_ADD_REMOVE_MY_MARK: {
							uiManager.sendPostItemMessage(postItem, UiManager.MESSAGE_PERFORM_SWITCH_USER_MARK);
							break;
						}
						case MENU_GO_TO_POST: {
							uiManager.sendPostItemMessage(postItem, UiManager.MESSAGE_PERFORM_GO_TO_POST);
							break;
						}
						case MENU_HIDE: {
							DialogMenu dialogMenu = new DialogMenu(context, this);
							dialogMenu.addItem(MENU_HIDE_POST, R.string.action_hide_post);
							dialogMenu.addItem(MENU_HIDE_REPLIES, R.string.action_hide_replies);
							dialogMenu.addItem(MENU_HIDE_NAME, R.string.action_hide_name);
							dialogMenu.addItem(MENU_HIDE_SIMILAR, R.string.action_hide_similar);
							dialogMenu.show(uiManager.getConfigurationLock());
							break;
						}
						case MENU_HIDE_POST: {
							uiManager.sendPostItemMessage(postItem, UiManager.MESSAGE_PERFORM_SWITCH_HIDE);
							break;
						}
						case MENU_HIDE_REPLIES: {
							uiManager.sendPostItemMessage(postItem, UiManager.MESSAGE_PERFORM_HIDE_REPLIES);
							break;
						}
						case MENU_HIDE_NAME: {
							uiManager.sendPostItemMessage(postItem, UiManager.MESSAGE_PERFORM_HIDE_NAME);
							break;
						}
						case MENU_HIDE_SIMILAR: {
							uiManager.sendPostItemMessage(postItem, UiManager.MESSAGE_PERFORM_HIDE_SIMILAR);
							break;
						}
					}
				}
			});
			if (replyable != null) {
				dialogMenu.addItem(MENU_REPLY, R.string.action_reply);
				if (!postEmpty) {
					dialogMenu.addItem(MENU_QUOTE, R.string.action_quote);
				}
			}
			if (copyText) {
				dialogMenu.addItem(MENU_COPY, R.string.action_copy_expand);
			} else {
				dialogMenu.addItem(MENU_COPY_LINK, R.string.action_copy_link);
			}
			if (shareText) {
				dialogMenu.addItem(MENU_SHARE, R.string.action_share_expand);
			} else {
				dialogMenu.addItem(MENU_SHARE_LINK, R.string.action_share_link);
			}
			if (!postItem.isDeleted()) {
				if (board.allowReporting) {
					dialogMenu.addItem(MENU_REPORT, R.string.action_report);
				}
				if (board.allowDeleting) {
					dialogMenu.addItem(MENU_DELETE, R.string.action_delete);
				}
			}
			if (allowMyMarkEdit) {
				dialogMenu.addCheckableItem(MENU_ADD_REMOVE_MY_MARK, R.string.text_my_post, postItem.isUserPost());
			}
			if (allowGoToPost) {
				dialogMenu.addItem(MENU_GO_TO_POST, R.string.action_go_to_post);
			}
			if (allowHiding && !postItem.isHiddenUnchecked()) {
				dialogMenu.addItem(MENU_HIDE, R.string.action_hide_expand);
			}
			dialogMenu.show(uiManager.getConfigurationLock());
			uiManager.dialog().notifySwitchBackground(stackInstance);
			return true;
		}
		return false;
	}

	private String getCopyReadyComment(CharSequence text) {
		return getCopyReadyComment(text, 0, text.length());
	}

	String getCopyReadyComment(CharSequence text, int start, int end) {
		if (text instanceof Spanned) {
			SpannableStringBuilder builder = new SpannableStringBuilder(text.subSequence(start, end));
			LinkSuffixSpan[] spans = builder.getSpans(0, builder.length(), LinkSuffixSpan.class);
			if (spans != null && spans.length > 0) {
				for (LinkSuffixSpan span : spans) {
					int spanStart = builder.getSpanStart(span);
					int spanEnd = builder.getSpanEnd(span);
					builder.delete(spanStart, spanEnd);
				}
			}
			return builder.toString();
		} else {
			return text.subSequence(start, end).toString();
		}
	}
}
