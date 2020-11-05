package com.mishiranu.dashchan.ui.navigator.manager;

import android.app.AlertDialog;
import android.content.Context;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.view.ContextThemeWrapper;
import android.view.View;
import androidx.fragment.app.FragmentManager;
import chan.content.Chan;
import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.util.StringUtils;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.model.AttachmentItem;
import com.mishiranu.dashchan.content.model.GalleryItem;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.content.model.PostNumber;
import com.mishiranu.dashchan.content.service.DownloadService;
import com.mishiranu.dashchan.text.style.LinkSuffixSpan;
import com.mishiranu.dashchan.ui.DialogMenu;
import com.mishiranu.dashchan.ui.InstanceDialog;
import com.mishiranu.dashchan.ui.SearchImageDialog;
import com.mishiranu.dashchan.ui.gallery.GalleryOverlay;
import com.mishiranu.dashchan.ui.posting.Replyable;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.NavigationUtils;
import com.mishiranu.dashchan.widget.AttachmentView;
import com.mishiranu.dashchan.widget.CommentTextView;
import java.util.Collections;
import java.util.List;

public class InteractionUnit {
	private final UiManager uiManager;

	InteractionUnit(UiManager uiManager) {
		this.uiManager = uiManager;
	}

	public void handleLinkClick(UiManager.ConfigurationSet configurationSet,
			Uri uri, CommentTextView.LinkListener.Extra extra, boolean confirmed) {
		boolean handled = false;
		Chan chan = Chan.getPreferred(null, uri);
		if (chan.name != null) {
			boolean sameChan = chan.name.equals(extra.chanName);
			ChanLocator.NavigationData navigationData = null;
			if (chan.locator.safe(false).isBoardUri(uri)) {
				navigationData = new ChanLocator.NavigationData(ChanLocator.NavigationData.Target.THREADS,
						chan.locator.safe(false).getBoardName(uri), null, null, null);
				handled = true;
			} else if (chan.locator.safe(false).isThreadUri(uri)) {
				String boardName = chan.locator.safe(false).getBoardName(uri);
				String threadNumber = chan.locator.safe(false).getThreadNumber(uri);
				PostNumber postNumber = chan.locator.safe(false).getPostNumber(uri);
				if (threadNumber != null) {
					if (sameChan && chan.configuration.getOption(ChanConfiguration.OPTION_READ_SINGLE_POST)) {
						uiManager.dialog().displayReplyAsync(configurationSet,
								chan.name, boardName, threadNumber, postNumber);
					} else {
						navigationData = new ChanLocator.NavigationData(ChanLocator.NavigationData.Target.POSTS,
								boardName, threadNumber, postNumber, null);
					}
					handled = true;
				}
			} else if (sameChan) {
				navigationData = chan.locator.safe(false).handleUriClickSpecial(uri);
				if (navigationData != null) {
					handled = true;
				}
			}
			if (handled && navigationData != null) {
				if (confirmed) {
					uiManager.navigator().navigateTargetAllowReturn(chan.name, navigationData);
				} else {
					handleLinkNavigation(configurationSet.fragmentManager, chan.name, navigationData, sameChan);
				}
			}
		}
		if (!handled) {
			NavigationUtils.handleUriInternal(uiManager.getContext(), extra.chanName, uri);
		}
	}

	private static void handleLinkNavigation(FragmentManager fragmentManager,
			String chanName, ChanLocator.NavigationData navigationData, boolean sameChan) {
		new InstanceDialog(fragmentManager, null, provider -> {
			int messageId;
			if (sameChan) {
				switch (navigationData.target) {
					case THREADS: {
						messageId = R.string.go_to_threads_list__sentence;
						break;
					}
					case POSTS: {
						messageId = R.string.open_thread__sentence;
						break;
					}
					case SEARCH: {
						messageId = R.string.go_to_search__sentence;
						break;
					}
					default: {
						throw new IllegalArgumentException();
					}
				}
			} else {
				messageId = R.string.follow_the_link__sentence;
			}
			final ChanLocator.NavigationData navigationDataFinal = navigationData;
			return new AlertDialog.Builder(provider.getContext())
					.setMessage(messageId)
					.setNegativeButton(android.R.string.cancel, null)
					.setPositiveButton(android.R.string.ok, (d, which) -> UiManager.extract(provider).navigator()
							.navigateTargetAllowReturn(chanName, navigationDataFinal))
					.create();
		});
	}

	public void handleLinkLongClick(UiManager.ConfigurationSet configurationSet, Uri uri) {
		handleLinkLongClick(configurationSet.fragmentManager, uri);
	}

	private static void handleLinkLongClick(FragmentManager fragmentManager, Uri uri) {
		new InstanceDialog(fragmentManager, null, provider -> createLinkLongClick(provider, uri));
	}

	private static AlertDialog createLinkLongClick(InstanceDialog.Provider provider, Uri uri) {
		Chan chan = Chan.getPreferred(null, uri);
		String fileName = null;
		String boardName = null;
		String threadNumber = null;
		boolean isAttachment = false;
		if (chan.name != null && chan.locator.safe(false).isAttachmentUri(uri)) {
			fileName = chan.locator.createAttachmentFileName(uri);
			boardName = chan.locator.safe(false).getBoardName(uri);
			threadNumber = chan.locator.safe(false).getThreadNumber(uri);
			if (threadNumber == null) {
				boardName = null;
			}
			isAttachment = true;
		}
		String finalFileName = fileName;
		String finalBoardName = boardName;
		String finalThreadNumber = threadNumber;
		Context context = provider.getContext();
		DialogMenu dialogMenu = new DialogMenu(context);
		dialogMenu.add(R.string.copy_link, () -> StringUtils.copyToClipboard(context, uri.toString()));
		dialogMenu.add(R.string.share_link, () -> NavigationUtils.shareLink(context, null, uri));
		if (Preferences.isUseInternalBrowser() && (chan.name == null || !chan.locator.safe(false).isBoardUri(uri)
				&& !chan.locator.safe(false).isThreadUri(uri) && !chan.locator.safe(false).isAttachmentUri(uri)
				&& chan.locator.safe(false).handleUriClickSpecial(uri) == null)) {
			dialogMenu.add(R.string.web_browser, () -> NavigationUtils.handleUri(context, chan.name, uri,
					NavigationUtils.BrowserType.INTERNAL));
		}
		if (isAttachment) {
			dialogMenu.add(R.string.download_file, () -> {
				DownloadService.Binder binder = UiManager.extract(provider).callback().getDownloadBinder();
				if (binder != null) {
					binder.downloadStorage(uri, finalFileName, null,
							chan.name, finalBoardName, finalThreadNumber, null);
				}
			});
		}
		if (threadNumber != null) {
			dialogMenu.add(R.string.open_thread, () -> UiManager.extract(provider).navigator()
					.navigateTargetAllowReturn(chan.name, new ChanLocator.NavigationData
							(ChanLocator.NavigationData.Target.POSTS, finalBoardName, finalThreadNumber, null, null)));
		}
		return dialogMenu.create();
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
			PostItem postItem = holder.getPostItem();
			List<AttachmentItem> attachmentItems = postItem.getAttachmentItems();
			if (attachmentItems != null && !attachmentItems.isEmpty()) {
				GalleryItem.Set gallerySet = holder.getGallerySet();
				int startImageIndex = gallerySet.findIndex(postItem);
				if (mayShowDialog) {
					uiManager.dialog().openAttachmentOrDialog(holder.getConfigurationSet(), v,
							attachmentItems, startImageIndex, navigatePostMode, gallerySet);
				} else {
					int index = this.index;
					int imageIndex = startImageIndex;
					for (int i = 0; i < index; i++) {
						if (attachmentItems.get(i).isShowInGallery()) {
							imageIndex++;
						}
					}
					uiManager.dialog().openAttachment(v, holder.getConfigurationSet().chanName,
							attachmentItems, index, imageIndex, navigatePostMode, gallerySet);
				}
			}
		}
	}

	private static class ThumbnailLongClickListenerImpl implements UiManager.ThumbnailLongClickListener {
		private AttachmentItem attachmentItem;

		@Override
		public void update(AttachmentItem attachmentItem) {
			this.attachmentItem = attachmentItem;
		}

		@Override
		public boolean onLongClick(View v) {
			UiManager.Holder holder = ListViewUtils.getViewHolder(v, UiManager.Holder.class);
			showThumbnailLongClickDialogStatic(holder.getConfigurationSet(),
					attachmentItem, (AttachmentView) v, holder.getGallerySet().getThreadTitle());
			return true;
		}
	}

	public UiManager.ThumbnailClickListener createThumbnailClickListener() {
		return new ThumbnailClickListenerImpl(uiManager);
	}

	public UiManager.ThumbnailLongClickListener createThumbnailLongClickListener() {
		return new ThumbnailLongClickListenerImpl();
	}

	private static void showThumbnailLongClickDialogStatic(UiManager.ConfigurationSet configurationSet,
			AttachmentItem attachmentItem, AttachmentView attachmentView, String threadTitle) {
		String chanName = configurationSet.chanName;
		Chan chan = Chan.get(configurationSet.chanName);
		boolean canLoadThumbnailManually = attachmentItem.canLoadThumbnailManually(attachmentView, chan);
		new InstanceDialog(configurationSet.fragmentManager, null, provider -> createThumbnailLongClickDialog(provider,
				chanName, attachmentItem, threadTitle, canLoadThumbnailManually));
	}

	private static AlertDialog createThumbnailLongClickDialog(InstanceDialog.Provider provider,
			String chanName, AttachmentItem attachmentItem, String threadTitle, boolean canLoadThumbnailManually) {
		Chan chan = Chan.get(chanName);
		Context context = new ContextThemeWrapper(provider.getContext(), R.style.Theme_Gallery);
		DialogMenu dialogMenu = new DialogMenu(context);
		dialogMenu.setTitle(attachmentItem.getDialogTitle(chan));
		if (attachmentItem.canDownloadToStorage()) {
			dialogMenu.add(R.string.download_file, () -> {
				UiManager uiManager = UiManager.extract(provider);
				DownloadService.Binder binder = uiManager.callback().getDownloadBinder();
				if (binder != null) {
					binder.downloadStorage(attachmentItem.getFileUri(chan), attachmentItem.getFileName(chan),
							attachmentItem.getOriginalName(), chan.name, attachmentItem.getBoardName(),
							attachmentItem.getThreadNumber(), threadTitle);
				}
			});
			if (attachmentItem.getType() == AttachmentItem.Type.IMAGE ||
					attachmentItem.getThumbnailKey(chan) != null) {
				dialogMenu.add(R.string.search_image, () -> {
					Uri fileUri = attachmentItem.getType() == AttachmentItem.Type.IMAGE
							? attachmentItem.getFileUri(chan) : attachmentItem.getThumbnailUri(chan);
					Chan fileChan = Chan.getPreferred(null, fileUri);
					new SearchImageDialog(fileChan.name, fileUri).show(provider.getFragmentManager(), null);
				});
			}
		}
		if (canLoadThumbnailManually) {
			dialogMenu.add(R.string.show_thumbnail, () -> {
				UiManager uiManager = UiManager.extract(provider);
				uiManager.reloadAttachmentItem(attachmentItem);
			});
		}
		dialogMenu.add(R.string.copy_link, () -> StringUtils.copyToClipboard(context,
				attachmentItem.getFileUri(chan).toString()));
		dialogMenu.add(R.string.share_link, () -> NavigationUtils.shareLink(context, null,
				attachmentItem.getFileUri(chan)));
		return dialogMenu.create();
	}

	public void showThumbnailLongClickDialog(UiManager.ConfigurationSet configurationSet,
			AttachmentItem attachmentItem, AttachmentView attachmentView, String threadTitle) {
		showThumbnailLongClickDialogStatic(configurationSet, attachmentItem, attachmentView, threadTitle);
	}

	public boolean handlePostClick(View view, UiManager.PostStateProvider postStateProvider,
			PostItem postItem, Iterable<PostItem> localPostItems) {
		if (postItem.getHideState().hidden) {
			uiManager.sendPostItemMessage(postItem, UiManager.Message.PERFORM_SWITCH_HIDE);
			return true;
		} else {
			if (Preferences.getHighlightUnreadMode() == Preferences.HighlightUnreadMode.MANUALLY) {
				for (PostItem localPostItem : localPostItems) {
					if (!postStateProvider.isRead(localPostItem.getPostNumber())) {
						postStateProvider.setRead(localPostItem.getPostNumber());
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

	public void handlePostContextMenu(UiManager.ConfigurationSet configurationSet, PostItem postItem) {
		Chan chan = Chan.get(configurationSet.chanName);
		Context context = uiManager.getContext();
		ChanConfiguration.Board board = chan.configuration.safe().obtainBoard(postItem.getBoardName());
		boolean postEmpty = StringUtils.isEmpty(postItem.getComment(chan).toString());
		boolean copyText = !postEmpty;
		boolean shareText = !postEmpty;
		boolean userPost = configurationSet.postStateProvider.isUserPost(postItem.getPostNumber());
		DialogMenu dialogMenu = new DialogMenu(context);
		if (configurationSet.replyable != null && configurationSet.replyable.onRequestReply(false)) {
			dialogMenu.add(R.string.reply, () -> configurationSet.replyable
					.onRequestReply(true, new Replyable.ReplyData(postItem.getPostNumber(), null)));
			if (!postEmpty) {
				dialogMenu.add(R.string.quote__verb, () -> configurationSet.replyable
						.onRequestReply(true, new Replyable.ReplyData(postItem.getPostNumber(),
								getCopyReadyComment(postItem.getComment(chan)))));
			}
		}
		if (copyText) {
			dialogMenu.addMore(R.string.copy, () -> showPostCopyDialog(configurationSet.fragmentManager,
					configurationSet.chanName, postItem));
		} else {
			dialogMenu.add(R.string.copy_link, () -> handlePostContextMenuCopy(context,
					configurationSet.chanName, postItem, PostCopyShareAction.COPY_LINK));
		}
		if (shareText) {
			dialogMenu.addMore(R.string.share, () -> showPostShareDialog(configurationSet.fragmentManager,
					configurationSet.chanName, postItem));
		} else {
			dialogMenu.add(R.string.share_link, () -> handlePostContextMenuCopy(context,
					configurationSet.chanName, postItem, PostCopyShareAction.SHARE_LINK));
		}
		if (!postItem.isDeleted()) {
			if (board.allowReporting) {
				dialogMenu.add(R.string.report, () -> uiManager.dialog()
						.performSendReportPosts(configurationSet.fragmentManager, chan.name, postItem.getBoardName(),
								postItem.getThreadNumber(), Collections.singletonList(postItem.getPostNumber())));
			}
			if (board.allowDeleting) {
				dialogMenu.add(R.string.delete, () -> uiManager.dialog()
						.performSendDeletePosts(configurationSet.fragmentManager, chan.name, postItem.getBoardName(),
								postItem.getThreadNumber(), Collections.singletonList(postItem.getPostNumber())));
			}
		}
		if (configurationSet.allowMyMarkEdit) {
			dialogMenu.addCheck(R.string.my_post, userPost, () -> uiManager
					.sendPostItemMessage(postItem, UiManager.Message.PERFORM_SWITCH_USER_MARK));
		}
		if (configurationSet.isDialog && configurationSet.allowGoToPost) {
			dialogMenu.add(R.string.go_to_post, () -> uiManager
					.sendPostItemMessage(postItem, UiManager.Message.PERFORM_GO_TO_POST));
		}
		if (configurationSet.allowHiding && !postItem.getHideState().hidden) {
			dialogMenu.addMore(R.string.hide,
					() -> showPostHideDialog(configurationSet.fragmentManager, postItem));
		}
		AlertDialog dialog = dialogMenu.create();
		uiManager.dialog().handlePostContextMenu(configurationSet, postItem.getPostNumber(), true, dialog);
		dialog.setOnDismissListener(d -> uiManager.dialog().handlePostContextMenu(configurationSet,
				postItem.getPostNumber(), false, dialog));
		dialog.show();
	}

	private static void showPostCopyDialog(FragmentManager fragmentManager, String chanName, PostItem postItem) {
		new InstanceDialog(fragmentManager, null, provider -> {
			Context context = provider.getContext();
			DialogMenu dialogMenu = new DialogMenu(context);
			dialogMenu.add(R.string.copy_text, () -> handlePostContextMenuCopy(context,
					chanName, postItem, PostCopyShareAction.COPY_TEXT));
			dialogMenu.add(R.string.copy_markup, () -> handlePostContextMenuCopy(context,
					chanName, postItem, PostCopyShareAction.COPY_MARKUP));
			dialogMenu.add(R.string.copy_link, () -> handlePostContextMenuCopy(context,
					chanName, postItem, PostCopyShareAction.COPY_LINK));
			return dialogMenu.create();
		});
	}

	private static void showPostShareDialog(FragmentManager fragmentManager, String chanName, PostItem postItem) {
		new InstanceDialog(fragmentManager, null, provider -> {
			Context context = provider.getContext();
			DialogMenu dialogMenu = new DialogMenu(context);
			dialogMenu.add(R.string.share_text, () -> handlePostContextMenuCopy(context,
					chanName, postItem, PostCopyShareAction.SHARE_TEXT));
			dialogMenu.add(R.string.share_link, () -> handlePostContextMenuCopy(context,
					chanName, postItem, PostCopyShareAction.SHARE_LINK));
			return dialogMenu.create();
		});
	}

	private static void showPostHideDialog(FragmentManager fragmentManager, PostItem postItem) {
		new InstanceDialog(fragmentManager, null, provider -> {
			UiManager uiManager = UiManager.extract(provider);
			DialogMenu dialogMenu = new DialogMenu(provider.getContext());
			dialogMenu.add(R.string.this_post, () -> uiManager
					.sendPostItemMessage(postItem, UiManager.Message.PERFORM_SWITCH_HIDE));
			dialogMenu.add(R.string.replies_tree, () -> uiManager
					.sendPostItemMessage(postItem, UiManager.Message.PERFORM_HIDE_REPLIES));
			dialogMenu.add(R.string.posts_with_same_name, () -> uiManager
					.sendPostItemMessage(postItem, UiManager.Message.PERFORM_HIDE_NAME));
			dialogMenu.add(R.string.similar_posts, () -> uiManager
					.sendPostItemMessage(postItem, UiManager.Message.PERFORM_HIDE_SIMILAR));
			return dialogMenu.create();
		});
	}

	private enum PostCopyShareAction {COPY_TEXT, COPY_MARKUP, COPY_LINK, SHARE_LINK, SHARE_TEXT}

	private static void handlePostContextMenuCopy(Context context,
			String chanName, PostItem postItem, PostCopyShareAction action) {
		Chan chan = Chan.get(chanName);
		switch (action) {
			case COPY_TEXT: {
				StringUtils.copyToClipboard(context, getCopyReadyComment(postItem.getComment(chan)));
				break;
			}
			case COPY_MARKUP: {
				StringUtils.copyToClipboard(context, postItem.getCommentMarkup(chan));
				break;
			}
			case COPY_LINK:
			case SHARE_LINK:
			case SHARE_TEXT: {
				String boardName = postItem.getBoardName();
				String threadNumber = postItem.getThreadNumber();
				PostNumber postNumber = postItem.getPostNumber();
				Uri uri = postItem.isOriginalPost()
						? chan.locator.safe(true).createThreadUri(boardName, threadNumber)
						: chan.locator.safe(true).createPostUri(boardName, threadNumber, postNumber);
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
									getCopyReadyComment(postItem.getComment(chan)), uri);
							break;
						}
					}
				}
				break;
			}
		}
	}

	private static String getCopyReadyComment(CharSequence text) {
		return getCopyReadyComment(text, 0, text.length());
	}

	static String getCopyReadyComment(CharSequence text, int start, int end) {
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
