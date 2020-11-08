package com.mishiranu.dashchan.content;

import android.content.Context;
import chan.content.Chan;
import chan.text.JsonSerial;
import chan.text.ParseException;
import chan.util.StringUtils;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.model.AttachmentItem;
import com.mishiranu.dashchan.content.model.Post;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.content.model.PostNumber;
import com.mishiranu.dashchan.content.storage.AutohideStorage;
import com.mishiranu.dashchan.text.SimilarTextEstimator;
import com.mishiranu.dashchan.widget.ClickableToast;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

public class HidePerformer {
	private static final int MAX_COMMENT_LENGTH = 1000;

	public interface PostsProvider {
		PostItem findPostItem(PostNumber postNumber);
	}

	private final AutohideStorage autohideStorage = AutohideStorage.getInstance();
	private final SimilarTextEstimator estimator = new SimilarTextEstimator(MAX_COMMENT_LENGTH, true);
	private final String autohidePrefix;
	private PostsProvider postsProvider;

	private LinkedHashSet<PostNumber> replies;
	private LinkedHashSet<String> names;
	private ArrayList<SimilarTextEstimator.WordsData<PostNumber>> similar;

	public HidePerformer(Context context) {
		autohidePrefix = context != null ? context.getString(R.string.autohide) + ": " : "";
	}

	public void setPostsProvider(PostsProvider postsProvider) {
		this.postsProvider = postsProvider;
	}

	public String checkHidden(Chan chan, PostItem postItem) {
		String message = checkHiddenByReplies(postItem);
		if (message == null) {
			message = checkHiddenByName(chan, postItem);
		}
		if (message == null) {
			message = checkHiddenBySimilarPost(chan, postItem);
		}
		if (message == null) {
			message = checkHiddenGlobalAutohide(chan, postItem);
		}
		return message != null ? autohidePrefix + message : null;
	}

	private String checkHiddenByReplies(PostItem postItem) {
		if (replies != null && postsProvider != null) {
			if (replies.contains(postItem.getPostNumber())) {
				return "replies tree " + postItem.getPostNumber();
			}
			for (PostNumber postNumber : postItem.getReferencesTo()) {
				postItem = postsProvider.findPostItem(postNumber);
				if (postItem != null) {
					String message = checkHiddenByReplies(postItem);
					if (message != null) {
						return message;
					}
				}
			}
		}
		return null;
	}

	private String checkHiddenByName(Chan chan, PostItem postItem) {
		if (names != null) {
			String name = postItem.getFullName(chan).toString();
			if (names.contains(name)) {
				return "name " + name;
			}
		}
		return null;
	}

	private String checkHiddenBySimilarPost(Chan chan, PostItem postItem) {
		if (similar != null) {
			SimilarTextEstimator.WordsData<PostNumber> wordsData =
					estimator.getWords(postItem.getComment(chan).toString());
			if (wordsData != null) {
				for (SimilarTextEstimator.WordsData<PostNumber> similarWordsData : similar) {
					if (estimator.checkSimiliar(wordsData, similarWordsData)) {
						return "similar to " + similarWordsData.extra;
					}
				}
			}
		}
		return null;
	}

	private String checkHiddenGlobalAutohide(Chan chan, PostItem postItem) {
		String boardName = postItem.getBoardName();
		PostNumber originalPostNumber = postItem.getOriginalPostNumber();
		String originalPostNumberString = originalPostNumber != null
				? originalPostNumber.toString() : null;
		boolean originalPost = postItem.isOriginalPost();
		boolean sage = postItem.isSage();
		String subject = null;
		String comment = null;
		List<String> names = null;
		ArrayList<AutohideStorage.AutohideItem> autohideItems = autohideStorage.getItems();
		for (int i = 0; i < autohideItems.size(); i++) {
			AutohideStorage.AutohideItem autohideItem = autohideItems.get(i);
			// AND selection (only if chan, board, thread, op, and sage match the rule)
			if (autohideItem.chanNames == null || autohideItem.chanNames.contains(chan.name)) {
				if (StringUtils.isEmpty(autohideItem.boardName) || boardName == null
						|| autohideItem.boardName.equals(boardName)) {
					if (StringUtils.isEmpty(autohideItem.threadNumber) || autohideItem.boardName != null &&
							autohideItem.threadNumber.equals(originalPostNumberString)) {
						if ((!autohideItem.optionOriginalPost || autohideItem.optionOriginalPost == originalPost)
								&& (!autohideItem.optionSage || autohideItem.optionSage == sage)) {
							String result;
							// OR selection (hide if subject, comment, or name match the rule)
							if (autohideItem.optionSubject) {
								if (subject == null) {
									subject = postItem.getSubject();
								}
								if ((result = autohideItem.find(subject)) != null) {
									return autohideItem.getReason(AutohideStorage.AutohideItem
											.ReasonSource.SUBJECT, comment, result);
								}
							}
							if (autohideItem.optionComment) {
								if (comment == null) {
									comment = postItem.getComment(chan).toString();
								}
								if ((result = autohideItem.find(comment)) != null) {
									return autohideItem.getReason(AutohideStorage.AutohideItem
											.ReasonSource.COMMENT, comment, result);
								}
							}
							if (autohideItem.optionName) {
								if (names == null) {
									String name = postItem.getFullName(chan).toString();
									List<Post.Icon> icons = postItem.getIcons();
									if (!icons.isEmpty()) {
										names = new ArrayList<>(1 + icons.size());
										names.add(name);
										for (Post.Icon icon : icons) {
											names.add(icon.title);
										}
									} else {
										names = Collections.singletonList(name);
									}
								}
								for (String name : names) {
									if ((result = autohideItem.find(name)) != null) {
										return autohideItem.getReason(AutohideStorage.AutohideItem
												.ReasonSource.NAME, name, result);
									}
								}
							}
							if (autohideItem.optionFileName && postItem.hasAttachments()) {
								for (AttachmentItem attachmentItem : postItem.getAttachmentItems()) {
									String originalName = StringUtils.emptyIfNull(attachmentItem.getOriginalName());
									if ((result = autohideItem.find(originalName)) != null) {
										return autohideItem.getReason(AutohideStorage.AutohideItem
												.ReasonSource.FILE, originalName, result);
									}
								}
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
		PostNumber postNumber = postItem.getPostNumber();
		if (replies.contains(postNumber)) {
			return AddResult.EXISTS;
		}
		replies.add(postNumber);
		return AddResult.SUCCESS;
	}

	public AddResult addHideByName(Chan chan, PostItem postItem) {
		if (postItem.isUseDefaultName()) {
			ClickableToast.show(R.string.default_name_cant_be_hidden);
			return AddResult.FAIL;
		}
		if (names == null) {
			names = new LinkedHashSet<>();
		}
		String fullName = postItem.getFullName(chan).toString();
		if (names.contains(fullName)) {
			return AddResult.EXISTS;
		}
		names.add(fullName);
		return AddResult.SUCCESS;
	}

	public AddResult addHideSimilar(Chan chan, PostItem postItem) {
		String comment = postItem.getComment(chan).toString();
		SimilarTextEstimator.WordsData<PostNumber> wordsData = estimator.getWords(comment);
		if (wordsData == null) {
			ClickableToast.show(R.string.too_few_meaningful_words);
			return AddResult.FAIL;
		}
		if (similar == null) {
			similar = new ArrayList<>();
		}
		PostNumber postNumber = postItem.getPostNumber();
		wordsData.extra = postNumber;
		// Remove repeats
		for (int i = similar.size() - 1; i >= 0; i--) {
			if (postNumber.equals(similar.get(i).extra)) {
				similar.remove(i);
			}
		}
		similar.add(wordsData);
		return AddResult.SUCCESS;
	}

	public boolean hasLocalFilters() {
		int repliesLength = replies != null ? replies.size() : 0;
		int namesLength = names != null ? names.size() : 0;
		int similarLength = similar != null ? similar.size() : 0;
		return repliesLength + namesLength + similarLength > 0;
	}

	public List<String> getReadableLocalFilters(Context context) {
		ArrayList<String> localFilters = new ArrayList<>();
		if (replies != null) {
			for (PostNumber postNumber : replies) {
				localFilters.add(context.getString(R.string.replies_to_number__format, postNumber.toString()));
			}
		}
		if (names != null) {
			for (String name : names) {
				localFilters.add(context.getString(R.string.with_name_name__format, name));
			}
		}
		if (similar != null) {
			for (SimilarTextEstimator.WordsData<PostNumber> wordsData : similar) {
				localFilters.add(context.getString(R.string.similar_to_number__format, wordsData.extra.toString()));
			}
		}
		return localFilters;
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
	public void removeLocalFilter(int index) {
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
		if (similar != null) {
			if (index >= similar.size()) {
				index -= similar.size();
			} else {
				similar.remove(index);
				if (similar.isEmpty()) {
					similar = null;
				}
				return;
			}
		}
	}

	public void encodeLocalFilters(JsonSerial.Writer writer) throws IOException {
		int repliesLength = replies != null ? replies.size() : 0;
		int namesLength = names != null ? names.size() : 0;
		int similarLength = similar != null ? similar.size() : 0;
		writer.startObject();
		if (repliesLength > 0) {
			writer.name("replies");
			writer.startArray();
			for (PostNumber postNumber : replies) {
				writer.value(postNumber.toString());
			}
			writer.endArray();
		}
		if (namesLength > 0) {
			writer.name("names");
			writer.startArray();
			for (String name : names) {
				writer.value(name);
			}
			writer.endArray();
		}
		if (similarLength > 0) {
			writer.name("similar");
			writer.startArray();
			for (SimilarTextEstimator.WordsData<PostNumber> wordsData : similar) {
				writer.startObject();
				writer.name("number");
				writer.value(wordsData.extra.toString());
				writer.name("count");
				writer.value(wordsData.count);
				writer.name("words");
				writer.startArray();
				for (String word : wordsData.words) {
					writer.value(word);
				}
				writer.endArray();
				writer.endObject();
			}
			writer.endArray();
		}
		writer.endObject();
	}

	public void decodeLocalFilters(JsonSerial.Reader reader) throws IOException, ParseException {
		this.replies = null;
		this.names = null;
		this.similar = null;
		if (reader != null) {
			reader.startObject();
			while (!reader.endStruct()) {
				switch (reader.nextName()) {
					case "replies": {
						reader.startArray();
						while (!reader.endStruct()) {
							if (replies == null) {
								replies = new LinkedHashSet<>();
							}
							replies.add(PostNumber.parseOrThrow(reader.nextString()));
						}
						break;
					}
					case "names": {
						reader.startArray();
						while (!reader.endStruct()) {
							if (names == null) {
								names = new LinkedHashSet<>();
							}
							names.add(reader.nextString());
						}
						break;
					}
					case "similar": {
						reader.startArray();
						while (!reader.endStruct()) {
							PostNumber postNumber = null;
							int count = 0;
							HashSet<String> words = new HashSet<>();
							reader.startObject();
							while (!reader.endStruct()) {
								switch (reader.nextName()) {
									case "number": {
										postNumber = PostNumber.parseOrThrow(reader.nextString());
										break;
									}
									case "count": {
										count = reader.nextInt();
										break;
									}
									case "words": {
										reader.startArray();
										while (!reader.endStruct()) {
											words.add(reader.nextString());
										}
										break;
									}
								}
							}
							SimilarTextEstimator.WordsData<PostNumber> wordsData =
									new SimilarTextEstimator.WordsData<>(words, count);
							wordsData.extra = postNumber;
							if (similar == null) {
								similar = new ArrayList<>();
							}
							similar.add(wordsData);
						}
						break;
					}
					default: {
						reader.skip();
						break;
					}
				}
			}
		}
	}

	public void decodeLocalFiltersLegacy(String[][] localFilters) {
		this.replies = null;
		this.names = null;
		this.similar = null;
		if (localFilters != null) {
			for (String[] rule : localFilters) {
				if (rule == null || rule.length < 2) {
					continue;
				}
				switch (StringUtils.emptyIfNull(rule[0])) {
					case "replies": {
						PostNumber postNumber = PostNumber.parseNullable(rule[1]);
						if (postNumber != null) {
							if (replies == null) {
								replies = new LinkedHashSet<>();
							}
							replies.add(postNumber);
						}
						break;
					}
					case "name": {
						if (!StringUtils.isEmpty(rule[1])) {
							if (names == null) {
								names = new LinkedHashSet<>();
							}
							names.add(rule[1]);
						}
						break;
					}
					case "similar": {
						if (rule.length >= 3) {
							PostNumber postNumber = PostNumber.parseNullable(rule[1]);
							if (postNumber != null) {
								int count = 0;
								try {
									count = Integer.parseInt(rule[2]);
								} catch (NumberFormatException e) {
									// Ignore exception
								}
								if (count > 0) {
									HashSet<String> words = new HashSet<>(Arrays.asList(rule).subList(3, rule.length));
									words.remove(null);
									words.remove("");
									if (!words.isEmpty()) {
										SimilarTextEstimator.WordsData<PostNumber> wordsData =
												new SimilarTextEstimator.WordsData<>(words, count);
										wordsData.extra = postNumber;
										if (similar == null) {
											similar = new ArrayList<>();
										}
										this.similar.add(wordsData);
									}
								}
							}
						}
						break;
					}
				}
			}
		}
	}
}
