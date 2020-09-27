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

package chan.content.model;

import chan.annotation.Public;

@Public
public final class ThreadSummary {
	private final String boardName;
	private final String threadNumber;
	private final String description;

	private int postsCount = -1;

	@Public
	public ThreadSummary(String boardName, String threadNumber, String description) {
		this.boardName = boardName;
		this.threadNumber = threadNumber;
		this.description = description;
	}

	@Public
	public String getBoardName() {
		return boardName;
	}

	@Public
	public String getThreadNumber() {
		return threadNumber;
	}

	@Public
	public String getDescription() {
		return description;
	}

	@Public
	public int getPostsCount() {
		return postsCount;
	}

	@Public
	public ThreadSummary setPostsCount(int postsCount) {
		this.postsCount = postsCount;
		return this;
	}
}