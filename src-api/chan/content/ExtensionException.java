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

import com.mishiranu.dashchan.app.MainApplication;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.util.Log;
import com.mishiranu.dashchan.util.ToastUtils;

public class ExtensionException extends Exception implements ErrorItem.Holder
{
	private static final long serialVersionUID = 1L;
	
	public ExtensionException(Throwable throwable)
	{
		super(throwable);
	}
	
	@Override
	public ErrorItem getErrorItemAndHandle()
	{
		logException(this);
		return new ErrorItem(ErrorItem.TYPE_EXTENSION);
	}
	
	public static ErrorItem obtainErrorItemAndLogException(Throwable t)
	{
		logException(t);
		return new ErrorItem(ErrorItem.TYPE_EXTENSION);
	}
	
	public static void showToastAndLogException(Throwable t)
	{
		ToastUtils.show(MainApplication.getInstance(), obtainErrorItemAndLogException(t));
	}
	
	public static void logException(Throwable t)
	{
		if (t instanceof LinkageError || t instanceof RuntimeException) Log.persistent().stack(t);
	}
}