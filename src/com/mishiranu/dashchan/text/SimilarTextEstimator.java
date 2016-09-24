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

package com.mishiranu.dashchan.text;

import java.util.HashSet;
import java.util.Locale;

public class SimilarTextEstimator
{
	private static final int MIN_WORDS_COUNT = 1;

	private final int mMaxLength;
	private final boolean mRemovePostLinks;

	public SimilarTextEstimator(int maxLength, boolean removePostLinks)
	{
		mMaxLength = maxLength;
		mRemovePostLinks = removePostLinks;
	}

	public boolean checkSimiliar(WordsData wordsData1, WordsData wordsData2)
	{
		if (wordsData1 != null && wordsData2 != null)
		{
			int wdc = wordsData1.count;
			int swdc = wordsData2.count;
			if (wdc >= swdc / 2 && wdc <= swdc * 2)
			{
				int similarity = 0;
				HashSet<String> swords = wordsData2.words;
				for (String word : wordsData1.words)
				{
					if (swords.contains(word)) similarity++; else similarity--;
				}
				// 2/3 similarity
				if (similarity >= swords.size() / 3) return true;
			}
		}
		return false;
	}

	public WordsData getWords(String text)
	{
		if (text == null) return null;
		HashSet<String> words = null;
		int count = 0;
		if (text.length() > mMaxLength) text = text.substring(0, mMaxLength);
		text = text.toLowerCase(Locale.getDefault());
		StringBuilder builder = new StringBuilder(text);
		if (mRemovePostLinks)
		{
			int index = 0;
			while (true)
			{
				index = builder.indexOf(">>", index);
				if (index == -1) break;
				int charCount = 0;
				for (int i = index + 2; i < builder.length(); i++)
				{
					if (Character.isDigit(builder.charAt(i))) charCount++; else break;
				}
				if (charCount > 0)
				{
					builder.delete(index, index + 2 + charCount);
					index++;
				}
				else index += 2;
			}
		}
		// Remove not letter characters
		for (int i = 0; i < builder.length(); i++)
		{
			char c = builder.charAt(i);
			if (c != ' ' && !Character.isLetterOrDigit(c)) builder.setCharAt(i, ' ');
		}
		// Fast String.split(" +")
		boolean inWord = false;
		int wordStart = 0;
		for (int i = 0, length = builder.length(); i <= length; i++)
		{
			char c = i < length ? builder.charAt(i) : ' ';
			if (inWord)
			{
				if (c == ' ')
				{
					inWord = false;
					String word = builder.substring(wordStart, i);
					if (words == null) words = new HashSet<>();
					words.add(word);
					count++;
				}
			}
			else
			{
				if (c != ' ')
				{
					inWord = true;
					wordStart = i;
				}
			}
		}
		return count >= MIN_WORDS_COUNT ? new WordsData(words, count) : null;
	}

	public static class WordsData
	{
		public final HashSet<String> words;
		public final int count;
		public String postNumber;

		public WordsData(HashSet<String> words, int count)
		{
			this.words = words;
			this.count = count;
		}
	}
}