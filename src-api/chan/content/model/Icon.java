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

package chan.content.model;

import java.io.Serializable;

import android.net.Uri;

import chan.annotation.Public;
import chan.content.ChanLocator;
import chan.util.StringUtils;

@Public
public final class Icon implements Serializable
{
	private static final long serialVersionUID = 1L;

	private final String mUriString;
	private final String mTitle;

	@Public
	public Icon(ChanLocator locator, Uri uri, String title)
	{
		mUriString = uri != null ? locator.makeRelative(uri).toString() : null;
		mTitle = title;
	}

	public Uri getRelativeUri()
	{
		return mUriString != null ? Uri.parse(mUriString) : null;
	}

	@Public
	public Uri getUri(ChanLocator locator)
	{
		return mUriString != null ? locator.convert(Uri.parse(mUriString)) : null;
	}

	@Public
	public String getTitle()
	{
		return mTitle;
	}

	public boolean contentEquals(Icon o)
	{
		return StringUtils.equals(mUriString, o.mUriString) && StringUtils.equals(mTitle, o.mTitle);
	}
}