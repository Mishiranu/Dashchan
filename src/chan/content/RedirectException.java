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

package chan.content;

import android.net.Uri;

import chan.annotation.Public;

@Public
public final class RedirectException extends Exception {
	private static final long serialVersionUID = 1L;

	private final Uri uri;
	private final String boardName;
	private final String threadNumber;
	private final String postNumber;

	private RedirectException(String boardName, String threadNumber, String postNumber) {
		this.uri = null;
		this.boardName = boardName;
		this.threadNumber = threadNumber;
		this.postNumber = postNumber;
	}

	private RedirectException(Uri uri) {
		this.uri = uri;
		this.boardName = null;
		this.threadNumber = null;
		this.postNumber = null;
	}

	@Public
	public static RedirectException toUri(Uri uri) {
		if (uri == null) {
			throw new NullPointerException("uri must not be null");
		}
		return new RedirectException(uri);
	}

	@Public
	public static RedirectException toBoard(String boardName) {
		return new RedirectException(boardName, null, null);
	}

	@Public
	public static RedirectException toThread(String boardName, String threadNumber, String postNumber) {
		if (threadNumber == null) {
			throw new NullPointerException("threadNumber must not be null");
		}
		return new RedirectException(boardName, threadNumber, postNumber);
	}

	public static class Target {
		public final String chanName;
		public final String boardName;
		public final String threadNumber;
		public final String postNumber;

		private Target(String chanName, String boardName, String threadNumber, String postNumber) {
			this.chanName = chanName;
			this.boardName = boardName;
			this.threadNumber = threadNumber;
			this.postNumber = postNumber;
		}
	}

	public final Target obtainTarget(String chanName) throws ExtensionException {
		if (uri != null) {
			chanName = ChanManager.getInstance().getChanNameByHost(uri.getHost());
			if (chanName != null) {
				ChanLocator locator = ChanLocator.get(chanName);
				try {
					if (locator.isBoardUri(uri)) {
						String boardName = locator.getBoardName(uri);
						return new Target(chanName, boardName, null, null);
					} else if (locator.isThreadUri(uri)) {
						String boardName = locator.getBoardName(uri);
						String threadNumber = locator.getThreadNumber(uri);
						String postNumber = locator.getPostNumber(uri);
						return new Target(chanName, boardName, threadNumber, postNumber);
					} else {
						return null;
					}
				} catch (LinkageError | RuntimeException e) {
					throw new ExtensionException(e);
				}
			} else {
				return null;
			}
		} else {
			return new Target(chanName, boardName, threadNumber, postNumber);
		}
	}
}