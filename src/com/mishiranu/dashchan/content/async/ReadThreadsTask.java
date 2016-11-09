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

package com.mishiranu.dashchan.content.async;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;

import chan.content.ChanConfiguration;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.content.RedirectException;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpValidator;
import chan.util.StringUtils;

import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.content.net.YouTubeTitlesReader;
import com.mishiranu.dashchan.util.Log;

public class ReadThreadsTask extends HttpHolderTask<Void, Void, Boolean> {
	private final Callback callback;
	private final String chanName;
	private final String boardName;
	private final int pageNumber;
	private final HttpValidator validator;
	private final boolean append;

	private ArrayList<PostItem> postItems;
	private int boardSpeed = 0;
	private HttpValidator resultValidator;

	private RedirectException.Target target;
	private ErrorItem errorItem;

	public interface Callback {
		public void onReadThreadsSuccess(ArrayList<PostItem> postItems, int pageNumber,
				int boardSpeed, boolean append, boolean checkModified, HttpValidator validator);
		public void onReadThreadsRedirect(RedirectException.Target target);
		public void onReadThreadsFail(ErrorItem errorItem, int pageNumber);
	}

	public ReadThreadsTask(Callback callback, String chanName, String boardName, int pageNumber,
			HttpValidator validator, boolean append) {
		this.callback = callback;
		this.chanName = chanName;
		this.boardName = boardName;
		this.pageNumber = pageNumber;
		this.validator = validator;
		this.append = append;
	}

	@Override
	protected Boolean doInBackground(HttpHolder holder, Void... params) {
		try {
			ChanPerformer.ReadThreadsResult result;
			try {
				result = ChanPerformer.get(chanName).safe().onReadThreads(new ChanPerformer.ReadThreadsData(boardName,
						pageNumber, holder, validator));
			} catch (RedirectException e) {
				RedirectException.Target target = e.obtainTarget(chanName);
				if (target == null) {
					throw HttpException.createNotFoundException();
				}
				if (target.threadNumber != null) {
					Log.persistent().write(Log.TYPE_ERROR, Log.DISABLE_QUOTES, "Only board redirects available there");
					errorItem = new ErrorItem(ErrorItem.TYPE_INVALID_DATA_FORMAT);
					return false;
				} else if (chanName.equals(target.chanName) && StringUtils.equals(boardName, target.boardName)) {
					throw HttpException.createNotFoundException();
				} else {
					this.target = target;
					return true;
				}
			}
			Posts[] threadsArray = result != null ? result.threads : null;
			ArrayList<PostItem> postItems = null;
			int boardSpeed = result != null ? result.boardSpeed : 0;
			HttpValidator validator = result != null ? result.validator : null;
			if (threadsArray != null && threadsArray.length > 0) {
				ArrayList<Post> posts = new ArrayList<>();
				for (Posts thread : threadsArray) {
					Post[] postsArray = thread.getPosts();
					if (postsArray != null) {
						Collections.addAll(posts, postsArray);
					}
				}
				YouTubeTitlesReader.getInstance().readAndApplyIfNecessary(posts, holder);
				for (Posts thread : threadsArray) {
					Post[] postsArray = thread.getPosts();
					if (postsArray != null && postsArray.length > 0) {
						if (postItems == null) {
							postItems = new ArrayList<>();
						}
						postItems.add(new PostItem(thread, chanName, boardName));
					}
				}
			}
			if (validator == null) {
				validator = holder.getValidator();
			}
			this.postItems = postItems;
			this.boardSpeed = boardSpeed;
			this.resultValidator = validator;
			return true;
		} catch (HttpException e) {
			if (e.getResponseCode() == HttpURLConnection.HTTP_NOT_MODIFIED) {
				return true;
			}
			if (e.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND) {
				errorItem = new ErrorItem(ErrorItem.TYPE_BOARD_NOT_EXISTS);
			} else {
				errorItem = e.getErrorItemAndHandle();
			}
			return false;
		} catch (ExtensionException | InvalidResponseException e) {
			errorItem = e.getErrorItemAndHandle();
			return false;
		} finally {
			ChanConfiguration.get(chanName).commit();
		}
	}

	@Override
	public void onPostExecute(Boolean success) {
		if (success) {
			if (target != null) {
				callback.onReadThreadsRedirect(target);
			} else {
				callback.onReadThreadsSuccess(postItems, pageNumber, boardSpeed, append,
						validator != null, resultValidator);
			}
		} else {
			callback.onReadThreadsFail(errorItem, pageNumber);
		}
	}
}