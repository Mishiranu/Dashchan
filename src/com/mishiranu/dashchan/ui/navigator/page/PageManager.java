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

package com.mishiranu.dashchan.ui.navigator.page;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import android.os.Bundle;
import android.os.Parcel;

import chan.content.ChanConfiguration;
import chan.content.ChanManager;

import com.mishiranu.dashchan.content.CacheManager;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.widget.ListPosition;

public class PageManager {
	private static final String EXTRA_SAVED_PAGES = "savedPages";

	private PageHolder pageHolder;
	private final ArrayList<PageHolder> pageHolders = new ArrayList<>();

	public PageHolder getCurrentPage() {
		return pageHolder;
	}

	public ArrayList<PageHolder> getPages() {
		return pageHolders;
	}

	public void save(Bundle outState) {
		outState.putParcelableArrayList(EXTRA_SAVED_PAGES, pageHolders);
	}

	public PageHolder restore(Bundle inState) {
		if (inState != null) {
			Collection<String> chanNames = ChanManager.getInstance().getAvailableChanNames();
			PageHolder savedCurrentPage = null;
			ArrayList<PageHolder> savedPages = inState.getParcelableArrayList(EXTRA_SAVED_PAGES);
			if (savedPages != null) {
				pageHolders.ensureCapacity(savedPages.size());
				for (PageHolder pageHolder : savedPages) {
					if (chanNames.contains(pageHolder.chanName)) {
						pageHolders.add(pageHolder);
						if (pageHolder.inStack) {
							savedCurrentPage = pageHolder;
						}
					}
				}
			}
			return savedCurrentPage;
		}
		return null;
	}

	private File getSavedPagesFile() {
		return CacheManager.getInstance().getInternalCacheFile("saved-pages");
	}

	public void writeToStorage(Bundle outState) {
		File file = getSavedPagesFile();
		if (file != null) {
			Parcel parcel = Parcel.obtain();
			FileOutputStream output = null;
			try {
				outState.writeToParcel(parcel, 0);
				byte[] data = parcel.marshall();
				output = new FileOutputStream(file);
				IOUtils.copyStream(new ByteArrayInputStream(data), output);
			} catch (IOException e) {
				file.delete();
			} finally {
				IOUtils.close(output);
				parcel.recycle();
			}
		}
	}

	public Bundle readFromStorage() {
		File file = getSavedPagesFile();
		if (file != null && file.exists()) {
			Parcel parcel = Parcel.obtain();
			FileInputStream input = null;
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			try {
				input = new FileInputStream(file);
				IOUtils.copyStream(input, output);
				byte[] data = output.toByteArray();
				parcel.unmarshall(data, 0, data.length);
				parcel.setDataPosition(0);
				Bundle bundle = new Bundle();
				bundle.setClassLoader(PageManager.class.getClassLoader());
				bundle.readFromParcel(parcel);
				return bundle;
			} catch (IOException e) {
				// Ignore exception
			} finally {
				IOUtils.close(input);
				parcel.recycle();
				file.delete();
			}
		}
		return null;
	}

	public ListPage<?> newPage(PageHolder.Content content) {
		switch (content) {
			case THREADS: {
				return new ThreadsPage();
			}
			case POSTS: {
				return new PostsPage();
			}
			case SEARCH: {
				return new SearchPage();
			}
			case ARCHIVE: {
				return new ArchivePage();
			}
			case ALL_BOARDS: {
				return new BoardsPage();
			}
			case USER_BOARDS: {
				return new UserBoardsPage();
			}
			case HISTORY: {
				return new HistoryPage();
			}
		}
		return null;
	}

	public PageHolder add(PageHolder.Content content, String chanName, String boardName, String threadNumber,
			String threadTitle, String searchQuery) {
		PageHolder pageHolder = get(chanName, boardName, threadNumber, content);
		ListPosition position = null;
		PageHolder.Extra extra = null;
		if (pageHolder != null) {
			position = pageHolder.position;
			extra = pageHolder.extra;
			pageHolders.remove(pageHolder);
		}
		pageHolder = new PageHolder(content, chanName, boardName, threadNumber, threadTitle, searchQuery);

		if (this.pageHolder != null && !this.pageHolder.inStack && this.pageHolder.canDestroyIfNotInStack()) {
			pageHolders.remove(this.pageHolder);
		}

		pageHolders.add(pageHolder);
		pageHolder.position = position;
		pageHolder.extra = extra;
		this.pageHolder = pageHolder;

		boolean mergeChans = Preferences.isMergeChans();
		int depth = 0;
		// Remove deep search, boards, etc pages if they are deep in stack
		for (int i = pageHolders.size() - 1; i >= 0; i--) {
			PageHolder workPageHolder = pageHolders.get(i);
			if (workPageHolder.inStack && (mergeChans || workPageHolder.chanName.equals(chanName))) {
				if (depth++ >= 2 && workPageHolder.canRemoveFromStackIfDeep()) {
					if (workPageHolder.canDestroyIfNotInStack()) {
						pageHolders.remove(i);
					} else {
						workPageHolder.inStack = false;
					}
				}
			}
		}
		return pageHolder;
	}

	public int getStackSize(String chanName) {
		boolean mergeChans = Preferences.isMergeChans();
		int count = 0;
		for (PageHolder pageHolder : pageHolders) {
			if (pageHolder.inStack && (mergeChans || pageHolder.chanName.equals(chanName))) {
				count++;
			}
		}
		return count;
	}

	public int getStackSize() {
		return getStackSize(pageHolder.chanName);
	}

	public PageHolder getTargetPreviousPage(boolean allowForeignChan) {
		boolean mergeChans = Preferences.isMergeChans();
		int current = pageHolders.indexOf(pageHolder);
		PageHolder foreignChanPageHolder = null;
		for (int i = current - 1; i >= 0; i--) {
			PageHolder pageHolder = pageHolders.get(i);
			if (pageHolder.inStack) {
				if (mergeChans || pageHolder.chanName.equals(this.pageHolder.chanName)) {
					return pageHolder;
				} else if (allowForeignChan && this.pageHolder.returnable && foreignChanPageHolder == null) {
					foreignChanPageHolder = pageHolder;
				}
			}
		}
		return foreignChanPageHolder;
	}

	public PageHolder getLastPage(String chanName) {
		boolean mergeChans = Preferences.isMergeChans();
		for (int i = pageHolders.size() - 1; i >= 0; i--) {
			PageHolder pageHolder = pageHolders.get(i);
			if (pageHolder.inStack && (mergeChans || pageHolder.chanName.equals(chanName))) {
				return pageHolder;
			}
		}
		return null;
	}

	public void removeCurrentPageFromStack() {
		pageHolder.inStack = false;
		if (pageHolder.isThreadsOrPosts() && Preferences.isCloseOnBack()) {
			pageHolders.remove(pageHolder);
		}
	}

	public void removeCurrentPage() {
		pageHolders.remove(pageHolder);
	}

	public void clearStack() {
		boolean mergeChans = Preferences.isMergeChans();
		boolean closeOnBack = Preferences.isCloseOnBack();
		String chanName = pageHolder.chanName;
		Iterator<PageHolder> iterator = pageHolders.iterator();
		while (iterator.hasNext()) {
			PageHolder pageHolder = iterator.next();
			if (mergeChans || pageHolder.chanName.equals(chanName)) {
				pageHolder.inStack = false;
				if (pageHolder.canDestroyIfNotInStack() || closeOnBack && pageHolder.isThreadsOrPosts()) {
					iterator.remove();
				}
			}
		}
	}

	public void moveCurrentPageTop() {
		pageHolder.inStack = true;
		pageHolders.remove(pageHolder);
		pageHolders.add(pageHolder);
	}

	public PageHolder get(String chanName, String boardName, String threadNumber, PageHolder.Content content) {
		for (PageHolder pageHolder : pageHolders) {
			if (pageHolder.is(chanName, boardName, threadNumber, content)) {
				return pageHolder;
			}
		}
		return null;
	}

	public void closeAllExcept(PageHolder exceptPageHolder) {
		boolean mergeChans = Preferences.isMergeChans();
		String chanName = pageHolder.chanName;
		Iterator<PageHolder> iterator = pageHolders.iterator();
		while (iterator.hasNext()) {
			PageHolder pageHolder = iterator.next();
			if (exceptPageHolder != pageHolder && (mergeChans || pageHolder.chanName.equals(chanName))) {
				if (pageHolder.isThreadsOrPosts() || pageHolder.canDestroyIfNotInStack()) {
					iterator.remove();
				} else {
					pageHolder.inStack = false;
				}
				if (this.pageHolder == pageHolder) {
					this.pageHolder = null;
				}
			}
		}
	}

	public boolean isSingleBoardMode(String chanName) {
		return ChanConfiguration.get(chanName).getOption(ChanConfiguration.OPTION_SINGLE_BOARD_MODE);
	}

	public boolean isSingleBoardMode() {
		return isSingleBoardMode(pageHolder.chanName);
	}

	public String getSingleBoardName(String chanName) {
		return ChanConfiguration.get(chanName).getSingleBoardName();
	}
}