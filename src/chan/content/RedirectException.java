package chan.content;

import android.net.Uri;
import chan.annotation.Public;
import com.mishiranu.dashchan.content.model.PostNumber;

@Public
public final class RedirectException extends Exception {
	private final Uri uri;
	private final String boardName;
	private final String threadNumber;
	private final PostNumber postNumber;

	private RedirectException(String boardName, String threadNumber, PostNumber postNumber) {
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
		return toThread(boardName, threadNumber, postNumber != null ? PostNumber.parseOrThrow(postNumber) : null);
	}

	public static RedirectException toThread(String boardName, String threadNumber, PostNumber postNumber) {
		PostNumber.validateThreadNumber(threadNumber, false);
		return new RedirectException(boardName, threadNumber, postNumber);
	}

	public static class Target {
		public final String chanName;
		public final String boardName;
		public final String threadNumber;
		public final PostNumber postNumber;

		private Target(String chanName, String boardName, String threadNumber, PostNumber postNumber) {
			this.chanName = chanName;
			this.boardName = boardName;
			this.threadNumber = threadNumber;
			this.postNumber = postNumber;
		}
	}

	public final Target obtainTarget(String chanName) throws ExtensionException {
		if (uri != null) {
			Chan chan = Chan.getPreferred(null, uri);
			if (chan.name != null) {
				try {
					if (chan.locator.isBoardUri(uri)) {
						String boardName = chan.locator.getBoardName(uri);
						return new Target(chan.name, boardName, null, null);
					} else if (chan.locator.isThreadUri(uri)) {
						String boardName = chan.locator.getBoardName(uri);
						String threadNumber = chan.locator.getThreadNumber(uri);
						String postNumberString = chan.locator.getPostNumber(uri);
						PostNumber.validateThreadNumber(threadNumber, false);
						PostNumber postNumber = null;
						if (postNumberString != null) {
							postNumber = PostNumber.parseOrThrow(postNumberString);
						}
						return new Target(chan.name, boardName, threadNumber, postNumber);
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
