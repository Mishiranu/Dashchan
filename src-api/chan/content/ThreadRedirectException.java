package chan.content;

import chan.annotation.Public;

// TODO CHAN
// Remove this class after updating
// exach fourplebs
// Added: 13.10.16 14:55
@Public
public final class ThreadRedirectException extends Exception {
	private static final long serialVersionUID = 1L;

	private final String boardName;
	private final String threadNumber;
	private final String postNumber;

	@Public
	public ThreadRedirectException(String boardName, String threadNumber, String postNumber) {
		this.boardName = boardName;
		this.threadNumber = threadNumber;
		this.postNumber = postNumber;
	}

	@Public
	public ThreadRedirectException(String threadNumber, String postNumber) {
		this(null, threadNumber, postNumber);
	}

	@SuppressWarnings("ThrowableResultOfMethodCallIgnored")
	public RedirectException.Target obtainTarget(String chanName, String boardName) throws ExtensionException {
		return RedirectException.toThread(this.boardName != null ? this.boardName : boardName,
				threadNumber, postNumber).obtainTarget(chanName);
	}
}
