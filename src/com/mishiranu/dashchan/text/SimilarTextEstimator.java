package com.mishiranu.dashchan.text;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class SimilarTextEstimator {
	private static final int MIN_WORDS_COUNT = 1;

	private final int maxLength;
	private final boolean removePostLinks;

	public SimilarTextEstimator(int maxLength, boolean removePostLinks) {
		this.maxLength = maxLength;
		this.removePostLinks = removePostLinks;
	}

	public <E> boolean checkSimiliar(WordsData<E> wordsData1, WordsData<E> wordsData2) {
		if (wordsData1 != null && wordsData2 != null) {
			int wdc = wordsData1.count;
			int swdc = wordsData2.count;
			if (wdc >= swdc / 2 && wdc <= swdc * 2) {
				int similarity = 0;
				Set<String> swords = wordsData2.words;
				for (String word : wordsData1.words) {
					if (swords.contains(word)) {
						similarity++;
					} else {
						similarity--;
					}
				}
				// 2/3 similarity
				return similarity >= swords.size() / 3;
			}
		}
		return false;
	}

	public <E> WordsData<E> getWords(String text) {
		if (text == null) {
			return null;
		}
		HashSet<String> words = null;
		int count = 0;
		if (text.length() > maxLength) {
			text = text.substring(0, maxLength);
		}
		text = text.toLowerCase(Locale.getDefault());
		StringBuilder builder = new StringBuilder(text);
		if (removePostLinks) {
			int index = 0;
			while (true) {
				index = builder.indexOf(">>", index);
				if (index == -1) {
					break;
				}
				int charCount = 0;
				for (int i = index + 2; i < builder.length(); i++) {
					if (Character.isDigit(builder.charAt(i))) {
						charCount++;
					} else {
						break;
					}
				}
				if (charCount > 0) {
					builder.delete(index, index + 2 + charCount);
					index++;
				} else {
					index += 2;
				}
			}
		}
		// Remove not letter characters
		for (int i = 0; i < builder.length(); i++) {
			char c = builder.charAt(i);
			if (c != ' ' && !Character.isLetterOrDigit(c)) {
				builder.setCharAt(i, ' ');
			}
		}
		// Fast String.split(" +")
		boolean inWord = false;
		int wordStart = 0;
		for (int i = 0, length = builder.length(); i <= length; i++) {
			char c = i < length ? builder.charAt(i) : ' ';
			if (inWord) {
				if (c == ' ') {
					inWord = false;
					String word = builder.substring(wordStart, i);
					if (words == null) {
						words = new HashSet<>();
					}
					words.add(word);
					count++;
				}
			} else {
				if (c != ' ') {
					inWord = true;
					wordStart = i;
				}
			}
		}
		return count >= MIN_WORDS_COUNT ? new WordsData<>(words, count) : null;
	}

	public static class WordsData<E> {
		public final Set<String> words;
		public final int count;
		public E extra;

		public WordsData(Set<String> words, int count) {
			this.words = words;
			this.count = count;
		}
	}
}
