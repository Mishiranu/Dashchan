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

package com.mishiranu.dashchan.ui;

import chan.content.ChanLocator;

public interface LocalNavigator
{
	public void navigateBoardsOrThreads(String chanName, String boardName, boolean navigateTop, boolean fromCache);
	public void navigatePosts(String chanName, String boardName, String threadNumber, String postNumber,
			String threadTitle, boolean fromCache);
	public void navigateSearch(String chanName, String boardName, String searchQuery);
	public void navigateArchive(String chanName, String boardName);
	public void navigateTarget(String chanName, ChanLocator.NavigationData data, boolean fromCache);
	public void navigateAddPost(String chanName, String boardName, String threadNumber, Replyable.ReplyData... data);
}