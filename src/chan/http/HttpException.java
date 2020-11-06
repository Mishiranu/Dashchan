package chan.http;

import androidx.annotation.NonNull;
import chan.annotation.Public;
import chan.util.StringUtils;
import com.mishiranu.dashchan.content.model.ErrorItem;
import java.net.HttpURLConnection;

@Public
public final class HttpException extends Exception implements ErrorItem.Holder {
	private final int responseCode;
	private final String responseText;
	private final ErrorItem.Type errorItemType;

	private final boolean httpException;
	private final boolean socketException;

	public HttpException(ErrorItem.Type errorItemType,
			boolean httpException, boolean socketException) {
		this.responseCode = 0;
		this.errorItemType = errorItemType;
		this.responseText = null;
		this.httpException = httpException;
		this.socketException = socketException;
	}

	public HttpException(ErrorItem.Type errorItemType,
			boolean httpException, boolean socketException, Throwable throwable) {
		super(throwable);
		this.responseCode = 0;
		this.errorItemType = errorItemType;
		this.responseText = null;
		this.httpException = httpException;
		this.socketException = socketException;
	}

	@Public
	public HttpException(int responseCode, String responseText) {
		this.responseCode = responseCode;
		this.errorItemType = null;
		this.responseText = responseText;
		this.httpException = true;
		this.socketException = false;
	}

	@Public
	public int getResponseCode() {
		return responseCode;
	}

	@Public
	public boolean isHttpException() {
		return httpException;
	}

	@Public
	public boolean isSocketException() {
		return socketException;
	}

	@NonNull
	@Override
	public ErrorItem getErrorItemAndHandle() {
		if (!StringUtils.isEmpty(responseText)) {
			return new ErrorItem(responseCode, responseText);
		}
		return new ErrorItem(errorItemType);
	}

	@Public
	public static HttpException createNotFoundException() {
		return new HttpException(HttpURLConnection.HTTP_NOT_FOUND, "Not Found");
	}
}
