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

package com.mishiranu.dashchan.content;

import java.util.ArrayList;
import java.util.HashSet;

import android.content.res.Resources;

import chan.content.model.Posts;
import chan.util.StringUtils;

import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.content.storage.AutohideStorage;
import com.mishiranu.dashchan.text.SimilarTextEstimator;
import com.mishiranu.dashchan.util.ToastUtils;

public class HidePerformer
{
	private static final int MAX_COMMENT_LENGTH = 1000;
	
	private final AutohideStorage mAutohideStorage;
	private final SimilarTextEstimator mEstimator = new SimilarTextEstimator(MAX_COMMENT_LENGTH, true);
	private final String mAutohidePrefix;
	
	private ArrayList<String> mNames;
	private ArrayList<SimilarTextEstimator.WordsData> mWords;
	
	public HidePerformer()
	{
		mAutohideStorage = AutohideStorage.getInstance();
		mAutohidePrefix = MainApplication.getInstance().getString(R.string.preference_header_autohide) + ": ";
	}
	
	public String checkHidden(PostItem postItem)
	{
		String message = checkHiddenByName(postItem);
		if (message == null) message = checkHiddenBySimilarPost(postItem);
		if (message == null) message = checkHiddenGlobalAutohide(postItem);
		return message != null ? mAutohidePrefix + message : null;
	}
	
	private String checkHiddenByName(PostItem postItem)
	{
		if (mNames != null)
		{
			String name = postItem.getFullName().toString();
			if (mNames.contains(name)) return "name " + name;
		}
		return null;
	}
	
	private String checkHiddenBySimilarPost(PostItem postItem)
	{
		if (mWords != null)
		{
			SimilarTextEstimator.WordsData wordsData = mEstimator.getWords(postItem.getComment().toString());
			if (wordsData != null)
			{
				for (SimilarTextEstimator.WordsData similarWordsData : mWords)
				{
					if (mEstimator.checkSimiliar(wordsData, similarWordsData))
					{
						return "similar to " + similarWordsData.postNumber;
					}
				}
			}
		}
		return null;
	}
	
	private String checkHiddenGlobalAutohide(PostItem postItem)
	{
		String chanName = postItem.getChanName();
		String boardName = postItem.getBoardName();
		String originalPostNumber = postItem.getOriginalPostNumber();
		boolean originalPost = postItem.getParentPostNumber() == null;
		boolean sage = postItem.isSage();
		String subject = null;
		String comment = null;
		String name = null;
		ArrayList<AutohideStorage.AutohideItem> autohideItems = mAutohideStorage.getItems();
		for (int i = 0; i < autohideItems.size(); i++)
		{
			AutohideStorage.AutohideItem autohideItem = autohideItems.get(i);
			// AND selection (only if chan, board, thread, op and sage matches to rule)
			if (autohideItem.chanNames == null || autohideItem.chanNames.contains(chanName))
			{
				if (StringUtils.isEmpty(autohideItem.boardName) || boardName == null
						|| autohideItem.boardName.equals(boardName))
				{
					if (StringUtils.isEmpty(autohideItem.threadNumber) || autohideItem.boardName != null &&
							autohideItem.threadNumber.equals(originalPostNumber))
					{
						if ((!autohideItem.optionOriginalPost || autohideItem.optionOriginalPost == originalPost)
								&& (!autohideItem.optionSage || autohideItem.optionSage == sage))
						{
							String result;
							// OR selection (hide if subj, exp or name matches to rule)
							if (subject == null) subject = postItem.getSubject();
							if (autohideItem.optionSubject && (result = autohideItem.find(subject)) != null)
							{
								return autohideItem.getReason(true, false, comment, result);
							}
							if (comment == null) comment = postItem.getComment().toString();
							if (autohideItem.optionComment && (result = autohideItem.find(comment)) != null)
							{
								return autohideItem.getReason(false, false, comment, result);
							}
							if (name == null) name = postItem.getFullName().toString();
							if (autohideItem.optionName && (result = autohideItem.find(name)) != null)
							{
								return autohideItem.getReason(false, true, name, result);
							}
						}
					}
				}
			}
		}
		return null;
	}
	
	public boolean addHideByName(PostItem postItem)
	{
		if (postItem.isUseDefaultName())
		{
			ToastUtils.show(MainApplication.getInstance(), R.string.message_hide_default_name_error);
			return false;
		}
		if (mNames == null) mNames = new ArrayList<>();
		String fullName = postItem.getFullName().toString();
		// Remove repeats
		mNames.remove(fullName);
		mNames.add(fullName);
		return true;
	}
	
	public boolean addHideSimilar(PostItem postItem)
	{
		String comment = postItem.getComment().toString();
		SimilarTextEstimator.WordsData wordsData = mEstimator.getWords(comment);
		if (wordsData == null)
		{
			ToastUtils.show(MainApplication.getInstance(), R.string.message_too_few_meaningful_words);
			return true;
		}
		if (mWords == null) mWords = new ArrayList<>();
		String postNumber = postItem.getPostNumber();
		wordsData.postNumber = postNumber;
		// Remove repeats
		for (int i = mWords.size() - 1; i >= 0; i--)
		{
			if (postNumber.equals(mWords.get(i).postNumber)) mWords.remove(i);
		}
		mWords.add(wordsData);
		return true;
	}
	
	public boolean hasLocalAutohide()
	{
		int namesLength = mNames != null ? mNames.size() : 0;
		int wordsLength = mWords != null ? mWords.size() : 0;
		return namesLength + wordsLength > 0;
	}
	
	public ArrayList<String> getReadableLocalAutohide()
	{
		Resources resources = MainApplication.getInstance().getResources();
		ArrayList<String> localAutohide = new ArrayList<>();
		if (mNames != null)
		{
			for (String name : mNames)
			{
				localAutohide.add(resources.getString(R.string.text_with_name_format, name));
			}
		}
		if (mWords != null)
		{
			for (SimilarTextEstimator.WordsData wordsData : mWords)
			{
				localAutohide.add(resources.getString(R.string.text_similar_to_format, wordsData.postNumber));
			}
		}
		return localAutohide;
	}
	
	public void removeLocalAutohide(int index)
	{
		if (mNames != null)
		{
			if (index >= mNames.size()) index -= mNames.size(); else
			{
				mNames.remove(index);
				if (mNames.isEmpty()) mNames = null;
				return;
			}
		}
		if (mWords != null)
		{
			mWords.remove(index);
			if (mWords.isEmpty()) mWords = null;
		}
	}
	
	private static final String TYPE_NAME = "name";
	private static final String TYPE_SIMILAR = "similar";
	
	public void encodeLocalAutohide(Posts posts)
	{
		String[][] localAutohide = null;
		int namesLength = mNames != null ? mNames.size() : 0;
		int wordsLength = mWords != null ? mWords.size() : 0;
		if (namesLength + wordsLength > 0)
		{
			localAutohide = new String[namesLength + wordsLength][];
			for (int i = 0; i < namesLength; i++) localAutohide[i] = new String[] {TYPE_NAME, mNames.get(i)};
			for (int i = 0; i < wordsLength; i++)
			{
				SimilarTextEstimator.WordsData wordsData = mWords.get(i);
				String[] rule = new String[wordsData.words.size() + 3];
				rule[0] = TYPE_SIMILAR;
				rule[1] = wordsData.postNumber;
				rule[2] = Integer.toString(wordsData.count);
				int j = 3;
				for (String word : wordsData.words) rule[j++] = word;
				localAutohide[i + namesLength] = rule;
			}
		}
		posts.setLocalAutohide(localAutohide);
	}
	
	@SuppressWarnings("ManualArrayToCollectionCopy")
	public void decodeLocalAutohide(Posts posts)
	{
		String[][] localAutohide = posts.getLocalAutohide();
		if (localAutohide != null)
		{
			for (String[] rule : localAutohide)
			{
				switch (rule[0])
				{
					case TYPE_NAME:
					{
						if (mNames == null) mNames = new ArrayList<>();
						mNames.add(rule[1]);
						break;
					}
					case TYPE_SIMILAR:
					{
						if (mWords == null) mWords = new ArrayList<>();
						String postNumber = rule[1];
						int count = Integer.parseInt(rule[2]);
						HashSet<String> words = new HashSet<>();
						for (int i = 3; i < rule.length; i++) words.add(rule[i]);
						SimilarTextEstimator.WordsData wordsData = new SimilarTextEstimator.WordsData(words, count);
						wordsData.postNumber = postNumber;
						mWords.add(wordsData);
						break;
					}
				}
			}
		}
	}
}