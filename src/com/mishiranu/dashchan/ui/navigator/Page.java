package com.mishiranu.dashchan.ui.navigator;

import android.os.Parcel;
import android.os.Parcelable;
import chan.content.Chan;
import chan.util.CommonUtils;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.ui.navigator.page.ArchivePage;
import com.mishiranu.dashchan.ui.navigator.page.BoardsPage;
import com.mishiranu.dashchan.ui.navigator.page.HistoryPage;
import com.mishiranu.dashchan.ui.navigator.page.ListPage;
import com.mishiranu.dashchan.ui.navigator.page.PostsPage;
import com.mishiranu.dashchan.ui.navigator.page.SearchPage;
import com.mishiranu.dashchan.ui.navigator.page.ThreadsPage;
import com.mishiranu.dashchan.ui.navigator.page.UserBoardsPage;

public final class Page implements Parcelable {
	public enum Content {
		THREADS(ThreadsPage::new),
		POSTS(PostsPage::new),
		SEARCH(SearchPage::new),
		ARCHIVE(ArchivePage::new),
		BOARDS(BoardsPage::new),
		USER_BOARDS(UserBoardsPage::new),
		HISTORY(HistoryPage::new);

		private interface PageFactory {
			ListPage newPage();
		}

		private final PageFactory pageFactory;

		Content(PageFactory pageFactory) {
			this.pageFactory = pageFactory;
		}

		public ListPage newPage() {
			return pageFactory.newPage();
		}
	}

	public final Content content;
	public final String chanName;
	public final String boardName;
	public final String threadNumber;
	public final String searchQuery;

	public Page(Content content, String chanName,
			String boardName, String threadNumber, String searchQuery) {
		this.content = content;
		this.chanName = chanName;
		this.boardName = boardName;
		this.threadNumber = threadNumber;
		this.searchQuery = searchQuery;
	}

	public boolean isThreadsOrPosts() {
		return content == Content.THREADS || content == Content.POSTS;
	}

	public boolean canDestroyIfNotInStack() {
		return content == Content.SEARCH || content == Content.ARCHIVE || content == Content.BOARDS
				|| content == Content.HISTORY;
	}

	public boolean canRemoveFromStackIfDeep() {
		if (content == Content.BOARDS) {
			String boardName = Preferences.getDefaultBoardName(Chan.get(chanName));
			return boardName != null;
		}
		return content == Content.SEARCH || content == Content.ARCHIVE || content == Content.USER_BOARDS
				|| content == Content.HISTORY;
	}

	public boolean isMultiChanAllowed() {
		return content == Content.HISTORY;
	}

	public boolean isThreadsOrPosts(String chanName, String boardName, String threadNumber) {
		if (threadNumber != null) {
			return is(Content.POSTS, chanName, boardName, threadNumber);
		} else {
			return is(Content.THREADS, chanName, boardName, null);
		}
	}

	public boolean is(Content content, String chanName, String boardName, String threadNumber) {
		if (this.content != content) {
			return false;
		}
		if (!CommonUtils.equals(this.chanName, chanName) && !(isMultiChanAllowed() && Preferences.isMergeChans())) {
			return false;
		}
		boolean compareContentTypeOnlyThis = false;
		boolean compareContentTypeOnlyCompared = false;
		switch (this.content) {
			case SEARCH:
			case BOARDS:
			case USER_BOARDS:
			case HISTORY: {
				compareContentTypeOnlyThis = true;
				break;
			}
			default: {
				break;
			}
		}
		switch (content) {
			case SEARCH:
			case BOARDS:
			case USER_BOARDS:
			case HISTORY: {
				compareContentTypeOnlyCompared = true;
				break;
			}
			default: {
				break;
			}
		}
		if (compareContentTypeOnlyThis && compareContentTypeOnlyCompared) {
			return this.content == content;
		}
		if (compareContentTypeOnlyThis || compareContentTypeOnlyCompared) {
			return false;
		}
		return CommonUtils.equals(this.boardName, boardName) && CommonUtils.equals(this.threadNumber, threadNumber);
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (o instanceof Page) {
			Page page = (Page) o;
			return content == page.content &&
					CommonUtils.equals(chanName, page.chanName) &&
					CommonUtils.equals(boardName, page.boardName) &&
					CommonUtils.equals(threadNumber, page.threadNumber) &&
					CommonUtils.equals(searchQuery, page.searchQuery);
		}
		return false;
	}

	@Override
	public int hashCode() {
		int prime = 31;
		int result = 1;
		result = prime * result + content.hashCode();
		result = prime * result + (chanName != null ? chanName.hashCode() : 0);
		result = prime * result + (boardName != null ? boardName.hashCode() : 0);
		result = prime * result + (threadNumber != null ? threadNumber.hashCode() : 0);
		result = prime * result + (searchQuery != null ? searchQuery.hashCode() : 0);
		return result;
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(content.toString());
		dest.writeString(chanName);
		dest.writeString(boardName);
		dest.writeString(threadNumber);
		dest.writeString(searchQuery);
	}

	public static final Creator<Page> CREATOR = new Creator<Page>() {
		@Override
		public Page createFromParcel(Parcel in) {
			Content content = Content.valueOf(in.readString());
			String chanName = in.readString();
			String boardName = in.readString();
			String threadNumber = in.readString();
			String searchQuery = in.readString();
			return new Page(content, chanName, boardName, threadNumber, searchQuery);
		}

		@Override
		public Page[] newArray(int size) {
			return new Page[size];
		}
	};
}
