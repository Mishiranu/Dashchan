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

package chan.text;

import java.util.Locale;

import chan.util.StringUtils;

public class GroupParser
{
	public static interface Callback
	{
		public boolean onStartElement(GroupParser parser, String tagName, String attrs) throws ParseException;
		public void onEndElement(GroupParser parser, String tagName) throws ParseException;
		public void onText(GroupParser parser, String source, int start, int end) throws ParseException;
		public void onGroupComplete(GroupParser parser, String text) throws ParseException;
	}
	
	private final String mSource;
	private final Callback mCallback;
	
	private final StringBuilder mGroup = new StringBuilder();
	
	private String mGroupTagName;
	private int mGroupCount = -1;
	
	private static final int MARK_STATE_NONE = 0;
	private static final int MARK_STATE_MARK = 1;
	private static final int MARK_STATE_RESET = 2;
	
	private boolean mMarkAvailable = false;
	private int mMarkCalled = MARK_STATE_NONE;
	private int mMark = 0;
	
	public static void parse(String source, Callback callback) throws ParseException
	{
		try
		{
			new GroupParser(source, callback).convert();
		}
		catch (RuntimeException e)
		{
			throw new ParseException(e);
		}
	}
	
	private GroupParser(String source, Callback callback)
	{
		mSource = source;
		mCallback = callback;
	}
	
	private void convert() throws ParseException
	{
		String source = mSource;
		int index = source.indexOf('<');
		char[] tagNameEndCharacters = {' ', '\r', '\n', '\t'};
		while (index != -1)
		{
			char next = source.charAt(index + 1);
			if (next == '!')
			{
				// Skip comment
				if (source.startsWith("<!--", index)) index = source.indexOf("-->", index);
				else index = source.indexOf(">", index);
				index = source.indexOf('<', index);
			}
			else
			{
				int length = source.length();
				int start = index;
				boolean inQuotes1 = false;
				boolean inQuotes2 = false;
				int end = -1;
				for (int i = index; i < length; i++)
				{
					char c = source.charAt(i);
					if (c == '"' && !inQuotes1) inQuotes2 = !inQuotes2;
					else if (c == '\'' && !inQuotes2) inQuotes1 = !inQuotes1;
					else if (c == '>' && !inQuotes1 && !inQuotes2)
					{
						end = i;
						break;
					}
				}
				if (end == -1)
				{
					// HTML is malformed. Try to find possible end of tag.
					end = source.indexOf('>', start);
				}
				if (end == -1)
				{
					end = Math.min(start + 50, length);
					throw new ParseException("Malformed HTML after " + start + ": end of tag was not found ("
							+ source.substring(start, end) + ")");
				}
				boolean close = next == '/';
				String raw = source.substring(start, end + 1);
				String fullTag = raw.substring(close ? 2 : 1, raw.length() - 1);
				String tagName = fullTag;
				String attrs = null;
				int t = StringUtils.nearestIndexOf(fullTag, 0, tagNameEndCharacters);
				if (t >= 0)
				{
					tagName = fullTag.substring(0, t);
					if (!close) attrs = fullTag.substring(t + 1);
				}
				else
				{
					t = fullTag.indexOf('/');
					if (t >= 0) tagName = fullTag.substring(0, t);
				}
				tagName = tagName.toLowerCase(Locale.US);
				if (!close && ("script".equals(tagName) || "style".equals(tagName)))
				{
					index = source.indexOf("</" + tagName, index + 1);
					if (index == -1) throw new ParseException("Can't find " + tagName + " closing after " + start);
					end = index + 3 + tagName.length();
				}
				else
				{
					mMarkCalled = MARK_STATE_NONE;
					mMarkAvailable = true;
					if (close) onEndElement(tagName, raw); else onStartElement(tagName, attrs, raw);
					mMarkAvailable = false;
					if (mMarkCalled == MARK_STATE_MARK)
					{
						mMark = start;
					}
					else if (mMarkCalled == MARK_STATE_RESET)
					{
						index = mMark;
						continue;
					}
				}
				index = source.indexOf('<', end);
				start = end + 1;
				end = index >= 0 ? index : length;
				if (start < end) onText(source, start, end);
			}
		}
	}
	
	private boolean isGroupMode()
	{
		return mGroupTagName != null;
	}
	
	private void onStartElement(String tagName, String attrs, String raw) throws ParseException
	{
		if (isGroupMode())
		{
			if (tagName.equals(mGroupTagName)) mGroupCount++;
			mGroup.append(raw);
		}
		else
		{
			boolean groupStart = mCallback.onStartElement(this, tagName, attrs);
			if (groupStart)
			{
				mGroupTagName = tagName;
				mGroupCount = 1;
				mGroup.setLength(0);
			}
		}
	}
	
	private void onEndElement(String tagName, String raw) throws ParseException
	{
		if (!isGroupMode())
		{
			mCallback.onEndElement(this, tagName);
		}
		else if (tagName.equals(mGroupTagName))
		{
			mGroupCount--;
			if (mGroupCount == 0)
			{
				mCallback.onGroupComplete(this, mGroup.toString());
				mGroupTagName = null;
			}
		}
		if (isGroupMode()) mGroup.append(raw);
	}
	
	private void onText(String source, int start, int end) throws ParseException
	{
		if (isGroupMode()) mGroup.append(source, start, end); else mCallback.onText(this, source, start, end);
	}
	
	private void checkMarkAvailable()
	{
		if (!mMarkAvailable)
		{
			throw new IllegalStateException("This method can only be called in onStartElement or onEndElement methods");
		}
	}
	
	public void mark()
	{
		checkMarkAvailable();
		mMarkCalled = MARK_STATE_MARK;
	}
	
	public void reset()
	{
		checkMarkAvailable();
		mMarkCalled = MARK_STATE_RESET;
	}
	
	public String getAttr(String attrs, String attr)
	{
		if (attrs != null)
		{
			int index = attrs.indexOf(attr + "=");
			if (index >= 0)
			{
				index += attr.length() + 1;
				char c = attrs.charAt(index);
				if (c == '\'' || c == '"')
				{
					int end = attrs.indexOf(c, index + 1);
					if (index < end) return attrs.substring(index + 1, end);
				}
				else
				{
					int endIndex = StringUtils.nearestIndexOf(attrs, index, ' ', '\r', '\n', '\t');
					if (endIndex >= index) return attrs.substring(index, endIndex);
					return attrs.substring(index);
				}
			}
		}
		return null;
	}
}