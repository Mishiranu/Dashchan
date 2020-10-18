package chan.content;

import chan.annotation.Public;
import com.mishiranu.dashchan.content.model.PostNumber;

// TODO CHAN
// Remove this class after updating
// alphachan anonfm chuckdfwk diochan exach ponychan sharechan
// Added: 13.10.16 14:55
@Public
public final class ThreadRedirectException extends Exception {
	private final String boardName;
	private final String threadNumber;
	private final String postNumber;

	@Public
	public ThreadRedirectException(String boardName, String threadNumber, String postNumber) {
		this.boardName = boardName;
		this.threadNumber = threadNumber;
		this.postNumber = postNumber;
		PostNumber.validateThreadNumber(threadNumber, false);
	}

	@Public
	public ThreadRedirectException(String threadNumber, String postNumber) {
		this(null, threadNumber, postNumber);
	}

	public RedirectException.Target obtainTarget(String chanName, String boardName) throws ExtensionException {
		return RedirectException.toThread(this.boardName != null ? this.boardName : boardName,
				threadNumber, postNumber).obtainTarget(chanName);
	}
}
