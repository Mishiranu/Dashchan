/*
 * Copyright 2014-2016 Fukurou Mishiranu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package chan.http;

import java.net.HttpURLConnection;

import chan.annotation.Public;
import chan.util.StringUtils;

import com.mishiranu.dashchan.content.model.ErrorItem;

@Public
public final class HttpException extends Exception implements ErrorItem.Holder {
	private static final long serialVersionUID = 1L;

	private final int responseCode;
	private final String responseText;
	private final int errorItemType;

	private final boolean httpException;
	private final boolean socketException;

	public HttpException(int errorItemType, boolean httpException, boolean socketException) {
		this.responseCode = 0;
		this.errorItemType = errorItemType;
		this.responseText = null;
		this.httpException = httpException;
		this.socketException = socketException;
	}

	public HttpException(int errorItemType, boolean httpException, boolean socketException, Throwable throwable) {
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
		this.errorItemType = 0;
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