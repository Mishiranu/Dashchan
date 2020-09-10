package com.mishiranu.dashchan.ui.navigator.manager;

import android.content.res.Resources;
import chan.content.model.Posts;
import chan.util.StringUtils;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.MainApplication;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.content.storage.AutohideStorage;
import com.mishiranu.dashchan.text.SimilarTextEstimator;
import com.mishiranu.dashchan.util.ToastUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;

public class HidePerformer implements PostItem.HidePerformer {
	private static final int MAX_COMMENT_LENGTH = 1000;

	private final AutohideStorage autohideStorage = AutohideStorage.getInstance();
	private final SimilarTextEstimator estimator = new SimilarTextEstimator(MAX_COMMENT_LENGTH, true);
	private final String autohidePrefix;
	private UiManager.PostsProvider postsProvider;

	private LinkedHashSet<String> replies;
	private LinkedHashSet<String> names;
	private ArrayList<SimilarTextEstimator.WordsData> words;

	public HidePerformer() {
		autohidePrefix = MainApplication.getInstance().getString(R.string.autohide) + ": ";
	}

	public void setPostsProvider(UiManager.PostsProvider postsProvider) {
		this.postsProvider = postsProvider;
	}

	@Override
	public String checkHidden(PostItem postItem) {
		String message = checkHiddenByReplies(postItem);
		if (message == null) {
			message = checkHiddenByName(postItem);
		}
		if (message == null) {
			message = checkHiddenBySimilarPost(postItem);
		}
		if (message == null) {
			message = checkHiddenGlobalAutohide(postItem);
		}
		return message != null ? autohidePrefix + message : null;
	}

	private String checkHiddenByReplies(PostItem postItem) {
		if (replies != null && postsProvider != null) {
			if (replies.contains(postItem.getPostNumber())) {
				return "replies tree " + postItem.getPostNumber();
			}
			HashSet<String> referencesTo = postItem.getReferencesTo();
			if (referencesTo != null) {
				for (String postNumber : referencesTo) {
					postItem = postsProvider.findPostItem(postNumber);
					if (postItem != null) {
						String message = checkHiddenByReplies(postItem);
						if (message != null) {
							return message;
						}
					}
				}
			}
		}
		return null;
	}

	private String checkHiddenByName(PostItem postItem) {
		if (names != null) {
			String name = postItem.getFullName().toString();
			if (names.contains(name)) {
				return "name " + name;
			}
		}
		return null;
	}

	private String checkHiddenBySimilarPost(PostItem postItem) {
		if (words != null) {
			SimilarTextEstimator.WordsData wordsData = estimator.getWords(postItem.getComment().toString());
			if (wordsData != null) {
				for (SimilarTextEstimator.WordsData similarWordsData : words) {
					if (estimator.checkSimiliar(wordsData, similarWordsData)) {
						return "similar to " + similarWordsData.postNumber;
					}
				}
			}
		}
		return null;
	}

	private String checkHiddenGlobalAutohide(PostItem postItem) {
		String chanName = postItem.getChanName();
		String boardName = postItem.getBoardName();
		String originalPostNumber = postItem.getOriginalPostNumber();
		boolean originalPost = postItem.getParentPostNumber() == null;
		boolean sage = postItem.isSage();
		String subject = null;
		String comment = null;
		String name = null;
		ArrayList<AutohideStorage.AutohideItem> autohideItems = autohideStorage.getItems();
		for (int i = 0; i < autohideItems.size(); i++) {
			AutohideStorage.AutohideItem autohideItem = autohideItems.get(i);
			// AND selection (only if chan, board, thread, op and sage matches to rule)
			if (autohideItem.chanNames == null || autohideItem.chanNames.contains(chanName)) {
				if (StringUtils.isEmpty(autohideItem.boardName) || boardName == null
						|| autohideItem.boardName.equals(boardName)) {
					if (StringUtils.isEmpty(autohideItem.threadNumber) || autohideItem.boardName != null &&
							autohideItem.threadNumber.equals(originalPostNumber)) {
						if ((!autohideItem.optionOriginalPost || autohideItem.optionOriginalPost == originalPost)
								&& (!autohideItem.optionSage || autohideItem.optionSage == sage)) {
							String result;
							// OR selection (hide if subj, exp or name matches to rule)
							if (subject == null) {
								subject = postItem.getSubject();
							}
							if (autohideItem.optionSubject && (result = autohideItem.find(subject)) != null) {
								return autohideItem.getReason(true, false, comment, result);
							}
							if (comment == null) {
								comment = postItem.getComment().toString();
							}
							if (autohideItem.optionComment && (result = autohideItem.find(comment)) != null) {
								return autohideItem.getReason(false, false, comment, result);
							}
							if (name == null) {
								name = postItem.getFullName().toString();
							}
							if (autohideItem.optionName && (result = autohideItem.find(name)) != null) {
								return autohideItem.getReason(false, true, name, result);
							}
						}
					}
				}
			}
		}
		return null;
	}

	public enum AddResult {SUCCESS, FAIL, EXISTS}

	public AddResult addHideByReplies(PostItem postItem) {
		if (replies == null) {
			replies = new LinkedHashSet<>();
		}
		String postNumber = postItem.getPostNumber();
		if (replies.contains(postNumber)) {
			return AddResult.EXISTS;
		}
		replies.add(postNumber);
		return AddResult.SUCCESS;
	}

	public AddResult addHideByName(PostItem postItem) {
		if (postItem.isUseDefaultName()) {
			ToastUtils.show(MainApplication.getInstance(), R.string.default_name_cant_be_hidden);
			return AddResult.FAIL;
		}
		if (names == null) {
			names = new LinkedHashSet<>();
		}
		String fullName = postItem.getFullName().toString();
		if (names.contains(fullName)) {
			return AddResult.EXISTS;
		}
		names.add(fullName);
		return AddResult.SUCCESS;
	}

	public AddResult addHideSimilar(PostItem postItem) {
		String comment = postItem.getComment().toString();
		SimilarTextEstimator.WordsData wordsData = estimator.getWords(comment);
		if (wordsData == null) {
			ToastUtils.show(MainApplication.getInstance(), R.string.too_few_meaningful_words);
			return AddResult.FAIL;
		}
		if (words == null) {
			words = new ArrayList<>();
		}
		String postNumber = postItem.getPostNumber();
		wordsData.postNumber = postNumber;
		// Remove repeats
		for (int i = words.size() - 1; i >= 0; i--) {
			if (postNumber.equals(words.get(i).postNumber)) {
				words.remove(i);
			}
		}
		words.add(wordsData);
		return AddResult.SUCCESS;
	}

	public boolean hasLocalAutohide() {
		int repliesLength = replies != null ? replies.size() : 0;
		int namesLength = names != null ? names.size() : 0;
		int wordsLength = words != null ? words.size() : 0;
		return repliesLength + namesLength + wordsLength > 0;
	}

	public ArrayList<String> getReadableLocalAutohide() {
		Resources resources = MainApplication.getInstance().getResources();
		ArrayList<String> localAutohide = new ArrayList<>();
		if (replies != null) {
			for (String postNumber : replies) {
				localAutohide.add(resources.getString(R.string.replies_to_number__format, postNumber));
			}
		}
		if (names != null) {
			for (String name : names) {
				localAutohide.add(resources.getString(R.string.with_name_name__format, name));
			}
		}
		if (words != null) {
			for (SimilarTextEstimator.WordsData wordsData : words) {
				localAutohide.add(resources.getString(R.string.similar_to_number__format, wordsData.postNumber));
			}
		}
		return localAutohide;
	}

	private static void removeFromLinkedHashSet(LinkedHashSet<?> set, int index) {
		int k = 0;
		for (Iterator<?> iterator = set.iterator(); iterator.hasNext();) {
			iterator.next();
			if (k++ == index) {
				iterator.remove();
				break;
			}
		}
	}

	@SuppressWarnings({"UnnecessaryReturnStatement", "UnusedAssignment"})
	public void removeLocalAutohide(int index) {
		if (replies != null) {
			if (index >= replies.size()) {
				index -= replies.size();
			} else {
				removeFromLinkedHashSet(replies, index);
				if (replies.isEmpty()) {
					replies = null;
				}
				return;
			}
		}
		if (names != null) {
			if (index >= names.size()) {
				index -= names.size();
			} else {
				removeFromLinkedHashSet(names, index);
				if (names.isEmpty()) {
					names = null;
				}
				return;
			}
		}
		if (words != null) {
			if (index >= words.size()) {
				index -= words.size();
			} else {
				words.remove(index);
				if (words.isEmpty()) {
					words = null;
				}
				return;
			}
		}
	}

	private static final String TYPE_REPLIES = "replies";
	private static final String TYPE_NAME = "name";
	private static final String TYPE_SIMILAR = "similar";

	public void encodeLocalAutohide(Posts posts) {
		String[][] localAutohide = null;
		int repliesLength = replies != null ? replies.size() : 0;
		int namesLength = names != null ? names.size() : 0;
		int wordsLength = words != null ? words.size() : 0;
		if (repliesLength + namesLength + wordsLength > 0) {
			localAutohide = new String[repliesLength + namesLength + wordsLength][];
			int i = 0;
			if (repliesLength > 0) {
				for (String postNumber : replies) {
					localAutohide[i++] = new String[] {TYPE_REPLIES, postNumber};
				}
			}
			if (namesLength > 0) {
				for (String name : names) {
					localAutohide[i++] = new String[] {TYPE_NAME, name};
				}
			}
			if (wordsLength > 0) {
				for (SimilarTextEstimator.WordsData wordsData : words) {
					String[] rule = new String[wordsData.words.size() + 3];
					rule[0] = TYPE_SIMILAR;
					rule[1] = wordsData.postNumber;
					rule[2] = Integer.toString(wordsData.count);
					int j = 3;
					for (String word : wordsData.words) {
						rule[j++] = word;
					}
					localAutohide[i++] = rule;
				}
			}
		}
		posts.setLocalAutohide(localAutohide);
	}

	@SuppressWarnings("ManualArrayToCollectionCopy")
	public void decodeLocalAutohide(Posts posts) {
		String[][] localAutohide = posts.getLocalAutohide();
		if (localAutohide != null) {
			for (String[] rule : localAutohide) {
				switch (rule[0]) {
					case TYPE_REPLIES: {
						if (replies == null) {
							replies = new LinkedHashSet<>();
						}
						replies.add(rule[1]);
						break;
					}
					case TYPE_NAME: {
						if (names == null) {
							names = new LinkedHashSet<>();
						}
						names.add(rule[1]);
						break;
					}
					case TYPE_SIMILAR: {
						if (words == null) {
							words = new ArrayList<>();
						}
						String postNumber = rule[1];
						int count = Integer.parseInt(rule[2]);
						HashSet<String> words = new HashSet<>();
						for (int i = 3; i < rule.length; i++) {
							words.add(rule[i]);
						}
						SimilarTextEstimator.WordsData wordsData = new SimilarTextEstimator.WordsData(words, count);
						wordsData.postNumber = postNumber;
						this.words.add(wordsData);
						break;
					}
				}
			}
		}
	}
}
