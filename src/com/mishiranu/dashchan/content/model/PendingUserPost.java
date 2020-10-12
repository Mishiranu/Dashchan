package com.mishiranu.dashchan.content.model;

import com.mishiranu.dashchan.text.HtmlParser;
import com.mishiranu.dashchan.text.SimilarTextEstimator;

public interface PendingUserPost {
	boolean isUserPost(Post post, boolean originalPost);

	class NewThread implements PendingUserPost {
		public static final NewThread INSTANCE = new NewThread();

		private NewThread() {}

		@Override
		public boolean isUserPost(Post post, boolean originalPost) {
			return originalPost;
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

		public SimilarComment(String comment) {
			wordsData = ESTIMATOR.getWords(comment);
		}

		@Override
		public boolean isUserPost(Post post, boolean originalPost) {
			String comment = HtmlParser.clear(post.comment);
			SimilarTextEstimator.WordsData<Void> wordsData = ESTIMATOR.getWords(comment);
			return ESTIMATOR.checkSimiliar(this.wordsData, wordsData) || this.wordsData == null && wordsData == null;
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
