package com.mishiranu.dashchan.content.model;

import com.mishiranu.dashchan.text.HtmlParser;
import com.mishiranu.dashchan.text.SimilarTextEstimator;
import java.util.List;

public interface PendingUserPost {
	PostNumber findUserPost(List<Post> posts, PostNumber lastExistingPostNumber, boolean firstPostIsOriginal);

	class NewThread implements PendingUserPost {
		public static final NewThread INSTANCE = new NewThread();

		private NewThread() {}

		@Override
		public PostNumber findUserPost(List<Post> posts,
				PostNumber lastExistingPostNumber, boolean firstPostIsOriginal) {
			return firstPostIsOriginal && !posts.isEmpty() ? posts.get(0).number : null;
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof NewThread;
		}

		@Override
		public int hashCode() {
			return 1;
		}
	}

	class SimilarComment implements PendingUserPost {
		private static final SimilarTextEstimator ESTIMATOR = new SimilarTextEstimator(Integer.MAX_VALUE, true);

		private final SimilarTextEstimator.WordsData<Void> wordsData;
		private final long time;

		public SimilarComment(String comment, long time) {
			wordsData = ESTIMATOR.getWords(comment);
			this.time = time;
		}

		@Override
		public PostNumber findUserPost(List<Post> posts,
				PostNumber lastExistingPostNumber, boolean firstPostIsOriginal) {
			if (lastExistingPostNumber != null) {
				for (int i = 0; i < posts.size(); i++) {
					Post post = posts.get(i);
					if (post.number.compareTo(lastExistingPostNumber) > 0) {
						posts = posts.subList(i, posts.size());
						break;
					}
				}
			}
			Post foundPost = null;
			for (Post post : posts) {
				String comment = HtmlParser.clear(post.comment);
				SimilarTextEstimator.WordsData<Void> wordsData = ESTIMATOR.getWords(comment);
				if (ESTIMATOR.checkSimiliar(this.wordsData, wordsData) ||
						this.wordsData == null && wordsData == null) {
					if (foundPost == null || Math.abs(foundPost.timestamp - time) > Math.abs(post.timestamp - time)) {
						foundPost = post;
					}
				}
			}
			return foundPost != null ? foundPost.number : null;
		}

		@Override
		public boolean equals(Object o) {
			if (o == this) {
				return true;
			}
			if (o instanceof SimilarComment) {
				SimilarComment co = (SimilarComment) o;
				return co.wordsData == wordsData || co.wordsData != null && wordsData != null
						&& co.wordsData.count == wordsData.count && co.wordsData.words.equals(wordsData.words);
			}
			return false;
		}

		@Override
		public int hashCode() {
			int prime = 31;
			int result = 1;
			if (wordsData != null) {
				result = prime * result + wordsData.words.hashCode();
				result = prime * result + wordsData.count;
			}
			return result;
		}
	}
}
