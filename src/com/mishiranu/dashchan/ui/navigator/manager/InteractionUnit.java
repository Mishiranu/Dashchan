/*
 * Copyright 2014-2016 Fukurou Mishiranu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mishiranu.dashchan.ui.navigator.manager;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;

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
import com.mishiranu.dashchan.content.DownloadManager;
import com.mishiranu.dashchan.content.model.AttachmentItem;
import com.mishiranu.dashchan.content.model.GalleryItem;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.text.style.LinkSuffixSpan;
import com.mishiranu.dashchan.ui.posting.Replyable;
import com.mishiranu.dashchan.util.DialogMenu;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.NavigationUtils;
import com.mishiranu.dashchan.widget.AttachmentView;

public class InteractionUnit
{
	private final UiManager mUiManager;

	InteractionUnit(UiManager uiManager)
	{
		mUiManager = uiManager;
	}

	public void handleLinkClick(String chanName, Uri uri, boolean confirmed)
	{
		boolean handled = false;
		final String uriChanName = ChanManager.getInstance().getChanNameByHost(uri.getHost());
		if (uriChanName != null)
		{
			boolean sameChan = uriChanName.equals(chanName);
			ChanLocator.NavigationData navigationData = null;
			ChanLocator locator = ChanLocator.get(uriChanName);
			ChanConfiguration configuration = ChanConfiguration.get(uriChanName);
			if (locator.safe(false).isBoardUri(uri))
			{
				navigationData = new ChanLocator.NavigationData(ChanLocator.NavigationData.TARGET_THREADS,
						locator.safe(false).getBoardName(uri), null, null, null);
				handled = true;
			}
			else if (locator.safe(false).isThreadUri(uri))
			{
				String boardName = locator.safe(false).getBoardName(uri);
				String threadNumber = locator.safe(false).getThreadNumber(uri);
				String postNumber = locator.safe(false).getPostNumber(uri);
				if (sameChan && configuration.getOption(ChanConfiguration.OPTION_READ_SINGLE_POST))
				{
					mUiManager.dialog().displayReplyAsync(uriChanName, boardName, threadNumber, postNumber);
				}
				else
				{
					navigationData = new ChanLocator.NavigationData(ChanLocator.NavigationData.TARGET_POSTS,
							boardName, threadNumber, postNumber, null);
				}
				handled = true;
			}
			else if (sameChan)
			{
				navigationData = locator.safe(false).handleUriClickSpecial(uri);
				if (navigationData != null) handled = true;
			}
			if (handled && navigationData != null)
			{
				if (confirmed) mUiManager.navigator().navigateTarget(uriChanName, navigationData, false); else
				{
					int messageId = 0;
					if (sameChan)
					{
						switch (navigationData.target)
						{
							case ChanLocator.NavigationData.TARGET_THREADS:
							{
								messageId = R.string.message_open_threads_confirm;
								break;
							}
							case ChanLocator.NavigationData.TARGET_POSTS:
							{
								messageId = R.string.message_open_posts_confirm;
								break;
							}
							case ChanLocator.NavigationData.TARGET_SEARCH:
							{
								messageId = R.string.message_open_search_confirm;
								break;
							}
						}
					}
					else messageId = R.string.message_open_link_confirm;
					final ChanLocator.NavigationData navigationDataFinal = navigationData;
					new AlertDialog.Builder(mUiManager.getContext()).setMessage(messageId)
							.setNegativeButton(android.R.string.cancel, null)
							.setPositiveButton(android.R.string.ok, (dialog, which) ->
					{
						mUiManager.navigator().navigateTarget(uriChanName, navigationDataFinal, false);

					}).show();
					mUiManager.dialog().notifySwitchBackground();
				}
			}
		}
		if (!handled) NavigationUtils.handleUriInternal(mUiManager.getContext(), chanName, uri, true);
	}

	private static final int LINK_MENU_COPY = 0;
	private static final int LINK_MENU_SHARE = 1;
	private static final int LINK_MENU_BROWSER = 2;
	private static final int LINK_MENU_DOWNLOAD_FILE = 3;
	private static final int LINK_MENU_OPEN_THREAD = 4;

	public void handleLinkLongClick(final Uri uri)
	{
		String uriChanName = ChanManager.getInstance().getChanNameByHost(uri.getHost());
		String fileName = null;
		String boardName = null;
		String threadNumber = null;
		boolean isAttachment = false;
		ChanLocator locator = null;
		if (uriChanName != null)
		{
			locator = ChanLocator.get(uriChanName);
			if (locator.safe(false).isAttachmentUri(uri))
			{
				fileName = locator.createAttachmentFileName(uri);
				boardName = locator.safe(false).getBoardName(uri);
				threadNumber = locator.safe(false).getThreadNumber(uri);
				if (threadNumber == null) boardName = null;
				isAttachment = true;
			}
		}
		final String finalChanName = uriChanName;
		final String finalFileName = fileName;
		final String finalBoardName = boardName;
		final String finalThreadNumber = threadNumber;
		DialogMenu dialogMenu = new DialogMenu(mUiManager.getContext(), (context, id, extra) ->
		{
			switch (id)
			{
				case LINK_MENU_COPY:
				{
					StringUtils.copyToClipboard(context, uri.toString());
					break;
				}
				case LINK_MENU_SHARE:
				{
					NavigationUtils.share(mUiManager.getContext(), uri);
					break;
				}
				case LINK_MENU_BROWSER:
				{
					NavigationUtils.handleUri(mUiManager.getContext(), finalChanName, uri,
							NavigationUtils.BrowserType.INTERNAL);
					break;
				}
				case LINK_MENU_DOWNLOAD_FILE:
				{
					DownloadManager.getInstance().downloadStorage(context, uri, finalFileName, null, finalChanName,
							finalBoardName, finalThreadNumber, null);
					break;
				}
				case LINK_MENU_OPEN_THREAD:
				{
					mUiManager.navigator().navigatePosts(finalChanName, finalBoardName, finalThreadNumber,
							null, null, false);
					break;
				}
			}
		});
		dialogMenu.addItem(LINK_MENU_COPY, R.string.action_copy_link);
		dialogMenu.addItem(LINK_MENU_SHARE, R.string.action_share_link);
		if (Preferences.isUseInternalBrowser() && (locator == null || !locator.safe(false).isBoardUri(uri)
				&& !locator.safe(false).isThreadUri(uri) && !locator.safe(false).isAttachmentUri(uri)
				&& locator.safe(false).handleUriClickSpecial(uri) == null))
		{
			dialogMenu.addItem(LINK_MENU_BROWSER, R.string.action_browser);
		}
		if (isAttachment) dialogMenu.addItem(LINK_MENU_DOWNLOAD_FILE, R.string.action_download_file);
		if (threadNumber != null) dialogMenu.addItem(LINK_MENU_OPEN_THREAD, R.string.action_open_thread);
		dialogMenu.show();
		mUiManager.dialog().notifySwitchBackground();
	}

	private static class ThumbnailClickListenerImpl implements UiManager.ThumbnailClickListener
	{
		private final UiManager mUiManager;
		private int mIndex;
		private boolean mMayShowDialog;

		public ThumbnailClickListenerImpl(UiManager uiManager)
		{
			mUiManager = uiManager;
		}

		@Override
		public void update(int index, boolean mayShowDialog)
		{
			mIndex = index;
			mMayShowDialog = mayShowDialog;
		}

		@Override
		public void onClick(View v)
		{
			UiManager.Holder holder = ListViewUtils.getViewHolder(v, UiManager.Holder.class);
			ArrayList<AttachmentItem> attachmentItems = holder.postItem.getAttachmentItems();
			if (attachmentItems != null)
			{
				GalleryItem.GallerySet gallerySet = holder.getGallerySet();
				int startImageIndex = mUiManager.view().findImageIndex(gallerySet.getItems(), holder.postItem);
				if (mMayShowDialog)
				{
					mUiManager.dialog().openAttachmentOrDialog(mUiManager.getContext(), v,
							attachmentItems, startImageIndex, gallerySet, holder.postItem);
				}
				else
				{
					int index = mIndex;
					int imageIndex = startImageIndex;
					for (int i = 0; i < index; i++)
					{
						if (attachmentItems.get(i).isShowInGallery()) imageIndex++;
					}
					mUiManager.dialog().openAttachment(mUiManager.getContext(), v, attachmentItems,
							index, imageIndex, gallerySet);
				}
			}
		}
	}

	private static class ThumbnailLongClickListenerImpl implements UiManager.ThumbnailLongClickListener
	{
		private final UiManager mUiManager;
		private AttachmentItem mAttachmentItem;

		public ThumbnailLongClickListenerImpl(UiManager uiManager)
		{
			mUiManager = uiManager;
		}

		@Override
		public void update(AttachmentItem attachmentItem)
		{
			mAttachmentItem = attachmentItem;
		}

		@Override
		public boolean onLongClick(View v)
		{
			UiManager.Holder holder = ListViewUtils.getViewHolder(v, UiManager.Holder.class);
			new ThumbnailLongClickDialog(mUiManager, mAttachmentItem, (AttachmentView) v, true,
					holder.getGallerySet().getThreadTitle());
			return true;
		}
	}

	public UiManager.ThumbnailClickListener createThumbnailClickListener()
	{
		return new ThumbnailClickListenerImpl(mUiManager);
	}

	public UiManager.ThumbnailLongClickListener createThumbnailLongClickListener()
	{
		return new ThumbnailLongClickListenerImpl(mUiManager);
	}

	private static class ThumbnailLongClickDialog implements DialogMenu.Callback
	{
		private final UiManager mUiManager;
		private final AttachmentItem mAttachmentItem;
		private final AttachmentView mAttachmentView;
		private final String mThreadTitle;

		private static final int MENU_DOWNLOAD_FILE = 0;
		private static final int MENU_SEARCH_IMAGE = 1;
		private static final int MENU_SHOW_THUMBNAIL = 2;
		private static final int MENU_COPY_LINK = 3;
		private static final int MENU_SHARE_LINK = 4;

		public ThumbnailLongClickDialog(UiManager uiManager, AttachmentItem attachmentItem,
				AttachmentView attachmentView, boolean hasViewHolder, String threadTitle)
		{
			mUiManager = uiManager;
			mAttachmentItem = attachmentItem;
			mAttachmentView = attachmentView;
			mThreadTitle = threadTitle;
			Context context = attachmentView.getContext();
			DialogMenu dialogMenu = new DialogMenu(context, this);
			dialogMenu.setTitle(mAttachmentItem.getDialogTitle(), true);
			if (mAttachmentItem.canDownloadToStorage())
			{
				dialogMenu.addItem(MENU_DOWNLOAD_FILE, R.string.action_download_file);
				if (mAttachmentItem.getType() == AttachmentItem.TYPE_IMAGE || mAttachmentItem.getThumbnailKey() != null)
				{
					dialogMenu.addItem(MENU_SEARCH_IMAGE, R.string.action_search_image);
				}
			}
			if (hasViewHolder && mAttachmentItem.canLoadThumbnailManually())
			{
				dialogMenu.addItem(MENU_SHOW_THUMBNAIL, R.string.action_show_thumbnail);
			}
			dialogMenu.addItem(MENU_COPY_LINK, R.string.action_copy_link);
			dialogMenu.addItem(MENU_SHARE_LINK, R.string.action_share_link);
			dialogMenu.show();
			uiManager.dialog().notifySwitchBackground();
		}

		@Override
		public void onItemClick(Context context, int id, Map<String, Object> extra)
		{
			Uri fileUri = mAttachmentItem.getFileUri();
			Uri thumbnailUri = mAttachmentItem.getThumbnailUri();
			int type = mAttachmentItem.getType();
			switch (id)
			{
				case MENU_DOWNLOAD_FILE:
				{
					DownloadManager.getInstance().downloadStorage(context, fileUri, mAttachmentItem.getFileName(),
							mAttachmentItem.getOriginalName(), mAttachmentItem.getChanName(),
							mAttachmentItem.getBoardName(), mAttachmentItem.getThreadNumber(), mThreadTitle);
					break;
				}
				case MENU_SEARCH_IMAGE:
				{
					NavigationUtils.searchImage(context, ChanManager.getInstance().getChanNameByHost
							(fileUri.getAuthority()), type == AttachmentItem.TYPE_IMAGE ? fileUri : thumbnailUri);
					break;
				}
				case MENU_SHOW_THUMBNAIL:
				{
					mUiManager.sendPostItemMessage(mAttachmentView, UiManager.MESSAGE_PERFORM_DISPLAY_THUMBNAILS);
					break;
				}
				case MENU_COPY_LINK:
				{
					StringUtils.copyToClipboard(context, fileUri.toString());
					break;
				}
				case MENU_SHARE_LINK:
				{
					NavigationUtils.share(context, fileUri);
					break;
				}
			}
		}
	}

	public void showThumbnailLongClickDialog(AttachmentItem attachmentItem, AttachmentView attachmentView,
			boolean hasViewHolder, String threadTitle)
	{
		new ThumbnailLongClickDialog(mUiManager, attachmentItem, attachmentView, hasViewHolder, threadTitle);
	}

	public boolean handlePostClick(View view, PostItem postItem, Iterable<PostItem> localPostItems)
	{
		if (postItem.isHiddenUnchecked())
		{
			mUiManager.sendPostItemMessage(postItem, UiManager.MESSAGE_PERFORM_SWITCH_HIDE);
			return true;
		}
		else
		{
			if (Preferences.getHighlightUnreadMode() == Preferences.HIGHLIGHT_UNREAD_MANUALLY)
			{
				for (PostItem localPostItem : localPostItems)
				{
					if (localPostItem.isUnread())
					{
						localPostItem.setUnread(false);
						mUiManager.sendPostItemMessage(localPostItem, UiManager.MESSAGE_INVALIDATE_VIEW);
					}
					if (localPostItem == postItem) break;
				}
			}
			return mUiManager.view().handlePostForDoubleClick(view);
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
	private static final int MENU_ADD_REMOVE_MY_MARK = 9;
	private static final int MENU_REPORT = 10;
	private static final int MENU_DELETE = 11;
	private static final int MENU_HIDE = 12;
	private static final int MENU_HIDE_POST = 13;
	private static final int MENU_HIDE_REPLIES = 14;
	private static final int MENU_HIDE_NAME = 15;
	private static final int MENU_HIDE_SIMILAR = 16;

	public boolean handlePostContextMenu(final PostItem postItem, final Replyable replyable, boolean allowMyMarkEdit,
			boolean allowHiding)
	{
		if (postItem != null)
		{
			Context context = mUiManager.getContext();
			String chanName = postItem.getChanName();
			ChanConfiguration configuration = ChanConfiguration.get(chanName);
			ChanConfiguration.Board board = configuration.safe().obtainBoard(postItem.getBoardName());
			boolean postEmpty = StringUtils.isEmpty(postItem.getComment().toString());
			final boolean copyText = !postEmpty;
			final boolean shareText = !postEmpty;
			DialogMenu dialogMenu = new DialogMenu(context, new DialogMenu.Callback()
			{
				@Override
				public void onItemClick(Context context, int id, Map<String, Object> extra)
				{
					switch (id)
					{
						case MENU_REPLY:
						{
							replyable.onRequestReply(new Replyable.ReplyData(postItem.getPostNumber(), null));
							break;
						}
						case MENU_QUOTE:
						{
							replyable.onRequestReply(new Replyable.ReplyData(postItem.getPostNumber(),
									getCopyReadyComment(postItem.getComment())));
							break;
						}
						case MENU_COPY:
						{
							DialogMenu dialogMenu = new DialogMenu(context, this);
							if (copyText)
							{
								dialogMenu.addItem(MENU_COPY_TEXT, R.string.action_copy_text);
								dialogMenu.addItem(MENU_COPY_MARKUP, R.string.action_copy_markup);
							}
							dialogMenu.addItem(MENU_COPY_LINK, R.string.action_copy_link);
							dialogMenu.show();
							break;
						}
						case MENU_COPY_TEXT:
						{
							StringUtils.copyToClipboard(context, getCopyReadyComment(postItem.getComment()));
							break;
						}
						case MENU_COPY_MARKUP:
						{
							StringUtils.copyToClipboard(context, postItem.getCommentMarkup());
							break;
						}
						case MENU_COPY_LINK:
						case MENU_SHARE_LINK:
						case MENU_SHARE_TEXT:
						{
							ChanLocator locator = ChanLocator.get(postItem.getChanName());
							String boardName = postItem.getBoardName();
							String threadNumber = postItem.getThreadNumber();
							String postNumber = postItem.getPostNumber();
							Uri uri = postItem.getParentPostNumber() == null
									? locator.safe(true).createThreadUri(boardName, threadNumber)
									: locator.safe(true).createPostUri(boardName, threadNumber, postNumber);
							if (uri != null)
							{
								switch (id)
								{
									case MENU_COPY_LINK:
									{
										StringUtils.copyToClipboard(context, uri.toString());
										break;
									}
									case MENU_SHARE_LINK:
									{
										String subject = postItem.getSubjectOrComment();
										if (StringUtils.isEmptyOrWhitespace(subject)) subject = uri.toString();
										NavigationUtils.share(context, subject, null, uri);
										break;
									}
									case MENU_SHARE_TEXT:
									{
										String subject = postItem.getSubjectOrComment();
										if (StringUtils.isEmptyOrWhitespace(subject)) subject = uri.toString();
										NavigationUtils.share(context, subject,
												getCopyReadyComment(postItem.getComment()), uri);
										break;
									}
								}
							}
							break;
						}
						case MENU_SHARE:
						{
							DialogMenu dialogMenu = new DialogMenu(context, this);
							if (shareText) dialogMenu.addItem(MENU_SHARE_TEXT, R.string.action_share_text);
							dialogMenu.addItem(MENU_SHARE_LINK, R.string.action_share_link);
							dialogMenu.show();
							break;
						}
						case MENU_ADD_REMOVE_MY_MARK:
						{
							mUiManager.sendPostItemMessage(postItem, UiManager.MESSAGE_PERFORM_SWITCH_USER_MARK);
							break;
						}
						case MENU_REPORT:
						{
							ArrayList<String> postNumbers = new ArrayList<>(1);
							postNumbers.add(postItem.getPostNumber());
							mUiManager.dialog().performSendReportPosts(postItem.getChanName(), postItem.getBoardName(),
									postItem.getThreadNumber(), postNumbers);
							break;
						}
						case MENU_DELETE:
						{
							ArrayList<String> postNumbers = new ArrayList<>(1);
							postNumbers.add(postItem.getPostNumber());
							mUiManager.dialog().performSendDeletePosts(postItem.getChanName(), postItem.getBoardName(),
									postItem.getThreadNumber(), postNumbers);
							break;
						}
						case MENU_HIDE:
						{
							LinkedHashSet<String> referencesFrom = postItem.getReferencesFrom();
							DialogMenu dialogMenu = new DialogMenu(context, this);
							dialogMenu.addItem(MENU_HIDE_POST, R.string.action_hide_post);
							if (referencesFrom != null && referencesFrom.size() > 0)
							{
								dialogMenu.addItem(MENU_HIDE_REPLIES, R.string.action_hide_replies);
							}
							dialogMenu.addItem(MENU_HIDE_NAME, R.string.action_hide_name);
							dialogMenu.addItem(MENU_HIDE_SIMILAR, R.string.action_hide_similar);
							dialogMenu.show();
							break;
						}
						case MENU_HIDE_POST:
						{
							mUiManager.sendPostItemMessage(postItem, UiManager.MESSAGE_PERFORM_SWITCH_HIDE);
							break;
						}
						case MENU_HIDE_REPLIES:
						{
							mUiManager.sendPostItemMessage(postItem, UiManager.MESSAGE_PERFORM_HIDE_REPLIES);
							break;
						}
						case MENU_HIDE_NAME:
						{
							mUiManager.sendPostItemMessage(postItem, UiManager.MESSAGE_PERFORM_HIDE_NAME);
							break;
						}
						case MENU_HIDE_SIMILAR:
						{
							mUiManager.sendPostItemMessage(postItem, UiManager.MESSAGE_PERFORM_HIDE_SIMILAR);
							break;
						}
					}
				}
			});
			if (replyable != null)
			{
				dialogMenu.addItem(MENU_REPLY, R.string.action_reply);
				if (!postEmpty) dialogMenu.addItem(MENU_QUOTE, R.string.action_quote);
			}
			if (copyText) dialogMenu.addItem(MENU_COPY, R.string.action_copy_expand);
			else dialogMenu.addItem(MENU_COPY_LINK, R.string.action_copy_link);
			if (shareText) dialogMenu.addItem(MENU_SHARE, R.string.action_share_expand);
			else dialogMenu.addItem(MENU_SHARE_LINK, R.string.action_share_link);
			if (!postItem.isDeleted())
			{
				if (board.allowReporting) dialogMenu.addItem(MENU_REPORT, R.string.action_report);
				if (board.allowDeleting) dialogMenu.addItem(MENU_DELETE, R.string.action_delete);
			}
			if (allowMyMarkEdit)
			{
				dialogMenu.addCheckableItem(MENU_ADD_REMOVE_MY_MARK, R.string.text_my_post, postItem.isUserPost());
			}
			if (allowHiding && !postItem.isHiddenUnchecked())
			{
				dialogMenu.addItem(MENU_HIDE, R.string.action_hide_expand);
			}
			dialogMenu.show();
			mUiManager.dialog().notifySwitchBackground();
			return true;
		}
		return false;
	}

	private String getCopyReadyComment(CharSequence text)
	{
		return getCopyReadyComment(text, 0, text.length());
	}

	String getCopyReadyComment(CharSequence text, int start, int end)
	{
		if (text instanceof Spanned)
		{
			SpannableStringBuilder builder = new SpannableStringBuilder(text.subSequence(start, end));
			LinkSuffixSpan[] spans = builder.getSpans(0, builder.length(), LinkSuffixSpan.class);
			if (spans != null && spans.length > 0)
			{
				for (LinkSuffixSpan span : spans)
				{
					int spanStart = builder.getSpanStart(span);
					int spanEnd = builder.getSpanEnd(span);
					builder.delete(spanStart, spanEnd);
				}
			}
			return builder.toString();
		}
		else return text.subSequence(start, end).toString();
	}
}