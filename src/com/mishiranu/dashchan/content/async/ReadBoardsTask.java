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

import chan.content.ChanConfiguration;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.content.model.BoardCategory;
import chan.http.HttpException;
import chan.http.HttpHolder;

import com.mishiranu.dashchan.content.model.ErrorItem;

public class ReadBoardsTask extends HttpHolderTask<Void, Long, Boolean> {
	private final String chanName;
	private final Callback callback;

	private BoardCategory[] boardCategories;
	private ErrorItem errorItem;

	public interface Callback {
		public void onReadBoardsSuccess(BoardCategory[] boardCategories);
		public void onReadBoardsFail(ErrorItem errorItem);
	}

	public ReadBoardsTask(String chanName, Callback callback) {
		this.chanName = chanName;
		this.callback = callback;
	}

	@Override
	protected Boolean doInBackground(HttpHolder holder, Void... params) {
		try {
			ChanPerformer.ReadBoardsResult result = ChanPerformer.get(chanName).safe()
					.onReadBoards(new ChanPerformer.ReadBoardsData(holder));
			BoardCategory[] boardCategories = result != null ? result.boardCategories : null;
			if (boardCategories != null && boardCategories.length == 0) {
				boardCategories = null;
			}
			if (boardCategories != null) {
				ChanConfiguration.get(chanName).updateFromBoards(boardCategories);
			}
			this.boardCategories = boardCategories;
			return true;
		} catch (ExtensionException | HttpException | InvalidResponseException e) {
			errorItem = e.getErrorItemAndHandle();
			return false;
		} finally {
			ChanConfiguration.get(chanName).commit();
		}
	}

	@Override
	public void onPostExecute(Boolean success) {
		if (success) {
			if (boardCategories != null) {
				callback.onReadBoardsSuccess(boardCategories);
			} else {
				callback.onReadBoardsFail(new ErrorItem(ErrorItem.TYPE_EMPTY_RESPONSE));
			}
		} else {
			callback.onReadBoardsFail(errorItem);
		}
	}
}