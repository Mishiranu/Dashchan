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

package chan.content;

import chan.annotation.Public;

import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.util.Log;

@Public
public final class InvalidResponseException extends Exception implements ErrorItem.Holder {
	private static final long serialVersionUID = 1L;

	@Public
	public InvalidResponseException() {}

	@Public
	public InvalidResponseException(Throwable throwable) {
		super(throwable);
	}

	@Override
	public ErrorItem getErrorItemAndHandle() {
		Log.persistent().stack(this);
		return new ErrorItem(ErrorItem.TYPE_INVALID_RESPONSE);
	}
}