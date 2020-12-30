package chan.text;

import chan.annotation.Extendable;
import chan.annotation.Public;

@Extendable
public class ParseException extends Exception {
	@Public
	public ParseException() {
		super();
	}

	public ParseException(String detailMessage) {
		super(detailMessage);
	}

	@Public
	public ParseException(Throwable throwable) {
		super(throwable);
	}
}
