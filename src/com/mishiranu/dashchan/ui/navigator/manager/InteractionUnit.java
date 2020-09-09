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
				}
			}
		}
		if (!handled) {
			NavigationUtils.handleUriInternal(uiManager.getContext(), chanName, uri);
		}
	}

	public void handleLinkLongClick(final Uri uri) {
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
		Context context = uiManager.getContext();
		DialogMenu dialogMenu = new DialogMenu(context);
		dialogMenu.add(R.string.action_copy_link, () -> StringUtils.copyToClipboard(context, uri.toString()));
		dialogMenu.add(R.string.action_share_link, () -> NavigationUtils.shareLink(context, null, uri));
		if (Preferences.isUseInternalBrowser() && (locator == null || !locator.safe(false).isBoardUri(uri)
				&& !locator.safe(false).isThreadUri(uri) && !locator.safe(false).isAttachmentUri(uri)
				&& locator.safe(false).handleUriClickSpecial(uri) == null)) {
			dialogMenu.add(R.string.action_browser, () -> NavigationUtils.handleUri(context, finalChanName, uri,
					NavigationUtils.BrowserType.INTERNAL));
		}
		if (isAttachment) {
			dialogMenu.add(R.string.action_download_file, () -> uiManager
					.download(binder -> binder.downloadStorage(uri, finalFileName, null,
							finalChanName, finalBoardName, finalThreadNumber, null)));
		}
		if (threadNumber != null) {
			dialogMenu.add(R.string.action_open_thread, () -> uiManager.navigator()
					.navigatePosts(finalChanName, finalBoardName, finalThreadNumber,
							null, null, NavigationUtils.FLAG_RETURNABLE));
		}
		dialogMenu.show(uiManager.getConfigurationLock());
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
			ArrayList<AttachmentItem> attachmentItems = holder.getPostItem().getAttachmentItems();
			if (attachmentItems != null) {
				GalleryItem.GallerySet gallerySet = holder.getGallerySet();
				int startImageIndex = uiManager.view().findImageIndex(gallerySet.getItems(), holder.getPostItem());
				if (mayShowDialog) {
					uiManager.dialog().openAttachmentOrDialog(holder.getConfigurationSet().stackInstance, v,
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
			showThumbnailLongClickDialog(uiManager, attachmentItem, (AttachmentView) v, true,
					holder.getGallerySet().getThreadTitle());
			return true;
		}
	}

	public UiManager.ThumbnailClickListener createThumbnailClickListener() {
		return new ThumbnailClickListenerImpl(uiManager);
	}

	public UiManager.ThumbnailLongClickListener createThumbnailLongClickListener() {
		return new ThumbnailLongClickListenerImpl(uiManager);
	}

	private static void showThumbnailLongClickDialog(UiManager uiManager, AttachmentItem attachmentItem,
			AttachmentView attachmentView, boolean hasViewHolder, String threadTitle) {
		Context context = attachmentView.getContext();
		DialogMenu dialogMenu = new DialogMenu(context);
		dialogMenu.setTitle(attachmentItem.getDialogTitle(), true);
		if (attachmentItem.canDownloadToStorage()) {
			dialogMenu.add(R.string.action_download_file, () -> uiManager
					.download(binder -> binder.downloadStorage(attachmentItem.getFileUri(),
							attachmentItem.getFileName(), attachmentItem.getOriginalName(),
							attachmentItem.getChanName(), attachmentItem.getBoardName(),
							attachmentItem.getThreadNumber(), threadTitle)));
			if (attachmentItem.getType() == AttachmentItem.TYPE_IMAGE ||
					attachmentItem.getThumbnailKey() != null) {
				dialogMenu.add(R.string.action_search_image, () -> {
					Uri fileUri = attachmentItem.getFileUri();
					NavigationUtils.searchImage(context, uiManager.getConfigurationLock(),
							ChanManager.getInstance().getChanNameByHost(fileUri.getAuthority()),
							attachmentItem.getType() == AttachmentItem.TYPE_IMAGE
									? fileUri : attachmentItem.getThumbnailUri());
				});
			}
		}
		if (hasViewHolder && attachmentItem.canLoadThumbnailManually(attachmentView)) {
			dialogMenu.add(R.string.action_show_thumbnail, () -> attachmentItem.startLoad(attachmentView, true));
		}
		dialogMenu.add(R.string.action_copy_link, () -> StringUtils.copyToClipboard(context,
				attachmentItem.getFileUri().toString()));
		dialogMenu.add(R.string.action_share_link, () -> NavigationUtils.shareLink(context, null,
				attachmentItem.getFileUri()));
		dialogMenu.show(uiManager.getConfigurationLock());
	}

	public void showThumbnailLongClickDialog(AttachmentItem attachmentItem,
			AttachmentView attachmentView, boolean hasViewHolder, String threadTitle) {
		showThumbnailLongClickDialog(uiManager, attachmentItem, attachmentView, hasViewHolder, threadTitle);
	}

	public boolean handlePostClick(View view, PostItem postItem, Iterable<PostItem> localPostItems) {
		if (postItem.isHiddenUnchecked()) {
			uiManager.sendPostItemMessage(postItem, UiManager.Message.PERFORM_SWITCH_HIDE);
			return true;
		} else {
			if (Preferences.getHighlightUnreadMode() == Preferences.HIGHLIGHT_UNREAD_MANUALLY) {
				for (PostItem localPostItem : localPostItems) {
					if (localPostItem.isUnread()) {
						localPostItem.setUnread(false);
						uiManager.sendPostItemMessage(localPostItem, UiManager.Message.POST_INVALIDATE_ALL_VIEWS);
					}
					if (localPostItem == postItem) {
						break;
					}
				}
			}
			return uiManager.view().handlePostForDoubleClick(view);
		}
	}

	public boolean handlePostContextMenu(final PostItem postItem, Replyable replyable,
			boolean allowMyMarkEdit, boolean allowHiding, boolean allowGoToPost) {
		if (postItem != null) {
			Context context = uiManager.getContext();
			String chanName = postItem.getChanName();
			ChanConfiguration configuration = ChanConfiguration.get(chanName);
			ChanConfiguration.Board board = configuration.safe().obtainBoard(postItem.getBoardName());
			boolean postEmpty = StringUtils.isEmpty(postItem.getComment().toString());
			final boolean copyText = !postEmpty;
			final boolean shareText = !postEmpty;
			DialogMenu dialogMenu = new DialogMenu(context);
			if (replyable != null) {
				dialogMenu.add(R.string.action_reply, () -> replyable
						.onRequestReply(new Replyable.ReplyData(postItem.getPostNumber(), null)));
				if (!postEmpty) {
					dialogMenu.add(R.string.action_quote, () -> replyable
							.onRequestReply(new Replyable.ReplyData(postItem.getPostNumber(),
									getCopyReadyComment(postItem.getComment()))));
				}
			}
			if (copyText) {
				dialogMenu.add(R.string.action_copy_expand, () -> {
					DialogMenu innerDialogMenu = new DialogMenu(context);
					innerDialogMenu.add(R.string.action_copy_text,
							() -> handlePostContextMenuCopy(postItem, PostCopyShareAction.COPY_TEXT));
					innerDialogMenu.add(R.string.action_copy_markup,
							() -> handlePostContextMenuCopy(postItem, PostCopyShareAction.COPY_MARKUP));
					innerDialogMenu.add(R.string.action_copy_link,
							() -> handlePostContextMenuCopy(postItem, PostCopyShareAction.COPY_LINK));
					innerDialogMenu.show(uiManager.getConfigurationLock());
				});
			} else {
				dialogMenu.add(R.string.action_copy_link,
						() -> handlePostContextMenuCopy(postItem, PostCopyShareAction.COPY_LINK));
			}
			if (shareText) {
				dialogMenu.add(R.string.action_share_expand, () -> {
					DialogMenu innerDialogMenu = new DialogMenu(context);
					innerDialogMenu.add(R.string.action_share_text,
							() -> handlePostContextMenuCopy(postItem, PostCopyShareAction.SHARE_TEXT));
					innerDialogMenu.add(R.string.action_share_link,
							() -> handlePostContextMenuCopy(postItem, PostCopyShareAction.SHARE_LINK));
					innerDialogMenu.show(uiManager.getConfigurationLock());
				});
			} else {
				dialogMenu.add(R.string.action_share_link,
						() -> handlePostContextMenuCopy(postItem, PostCopyShareAction.SHARE_LINK));
			}
			if (!postItem.isDeleted()) {
				if (board.allowReporting) {
					dialogMenu.add(R.string.action_report, () -> {
						ArrayList<String> postNumbers = new ArrayList<>(1);
						postNumbers.add(postItem.getPostNumber());
						uiManager.dialog().performSendReportPosts(postItem.getChanName(), postItem.getBoardName(),
								postItem.getThreadNumber(), postNumbers);
					});
				}
				if (board.allowDeleting) {
					dialogMenu.add(R.string.action_delete, () -> {
						ArrayList<String> postNumbers = new ArrayList<>(1);
						postNumbers.add(postItem.getPostNumber());
						uiManager.dialog().performSendDeletePosts(postItem.getChanName(), postItem.getBoardName(),
								postItem.getThreadNumber(), postNumbers);
					});
				}
			}
			if (allowMyMarkEdit) {
				dialogMenu.add(R.string.text_my_post, postItem.isUserPost(), () -> uiManager
						.sendPostItemMessage(postItem, UiManager.Message.PERFORM_SWITCH_USER_MARK));
			}
			if (allowGoToPost) {
				dialogMenu.add(R.string.action_go_to_post, () -> uiManager
						.sendPostItemMessage(postItem, UiManager.Message.PERFORM_GO_TO_POST));
			}
			if (allowHiding && !postItem.isHiddenUnchecked()) {
				dialogMenu.add(R.string.action_hide_expand, () -> {
					DialogMenu innerDialogMenu = new DialogMenu(context);
					innerDialogMenu.add(R.string.action_hide_post, () -> uiManager
							.sendPostItemMessage(postItem, UiManager.Message.PERFORM_SWITCH_HIDE));
					innerDialogMenu.add(R.string.action_hide_replies, () -> uiManager
							.sendPostItemMessage(postItem, UiManager.Message.PERFORM_HIDE_REPLIES));
					innerDialogMenu.add(R.string.action_hide_name, () -> uiManager
							.sendPostItemMessage(postItem, UiManager.Message.PERFORM_HIDE_NAME));
					innerDialogMenu.add(R.string.action_hide_similar, () -> uiManager
							.sendPostItemMessage(postItem, UiManager.Message.PERFORM_HIDE_SIMILAR));
					innerDialogMenu.show(uiManager.getConfigurationLock());
				});
			}
			dialogMenu.show(uiManager.getConfigurationLock());
			return true;
		}
		return false;
	}

	private enum PostCopyShareAction {COPY_TEXT, COPY_MARKUP, COPY_LINK, SHARE_LINK, SHARE_TEXT}

	private void handlePostContextMenuCopy(PostItem postItem, PostCopyShareAction action) {
		Context context = uiManager.getContext();
		switch (action) {
			case COPY_TEXT: {
				StringUtils.copyToClipboard(context, getCopyReadyComment(postItem.getComment()));
				break;
			}
			case COPY_MARKUP: {
				StringUtils.copyToClipboard(context, postItem.getCommentMarkup());
				break;
			}
			case COPY_LINK:
			case SHARE_LINK:
			case SHARE_TEXT: {
				ChanLocator locator = ChanLocator.get(postItem.getChanName());
				String boardName = postItem.getBoardName();
				String threadNumber = postItem.getThreadNumber();
				String postNumber = postItem.getPostNumber();
				Uri uri = postItem.getParentPostNumber() == null
						? locator.safe(true).createThreadUri(boardName, threadNumber)
						: locator.safe(true).createPostUri(boardName, threadNumber, postNumber);
				if (uri != null) {
					switch (action) {
						case COPY_LINK: {
							StringUtils.copyToClipboard(context, uri.toString());
							break;
						}
						case SHARE_LINK: {
							String subject = postItem.getSubjectOrComment();
							if (StringUtils.isEmptyOrWhitespace(subject)) {
								subject = uri.toString();
							}
							NavigationUtils.shareLink(context, subject, uri);
							break;
						}
						case SHARE_TEXT: {
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
		}
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
