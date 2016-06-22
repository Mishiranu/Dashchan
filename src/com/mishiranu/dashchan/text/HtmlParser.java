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

package com.mishiranu.dashchan.text;

import java.io.StringReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.ccil.cowan.tagsoup.HTMLSchema;
import org.ccil.cowan.tagsoup.Parser;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

import android.graphics.Color;

import chan.util.StringUtils;

public class HtmlParser implements ContentHandler
{
	public static CharSequence parse(String source, Markup markup, String parentPostNumber, Object extra)
	{
		return parse(source, markup, MODE_PARSE, parentPostNumber, extra);
	}
	
	public static String clear(String source)
	{
		return parse(source, null, MODE_CLEAR, null, null).toString();
	}
	
	public static String unescape(String source)
	{
		return parse(source, null, MODE_UNESCAPE, null, null).toString();
	}
	
	public static String unmark(String source, Markup markup, Object extra)
	{
		return parse(source, markup, MODE_UNMARK, null, extra).toString();
	}
	
	private static CharSequence parse(String source, Markup markup, int parsingMode, String parentPostNumber,
			Object extra)
	{
		if (source == null) return "";
		return new HtmlParser(source, markup != null ? markup : MARKUP_IMPL, parsingMode, parentPostNumber,
				extra).convert();
	}
	
	public static interface Markup
	{
		public Object onBeforeTagStart(HtmlParser parser, StringBuilder builder, String tagName,
				Attributes attributes, TagData tagData);
		public void onTagStart(HtmlParser parser, StringBuilder builder, String tagName,
				Attributes attributes, Object object);
		public void onTagEnd(HtmlParser parser, StringBuilder builder, String tagName);
		
		public int onListLineStart(HtmlParser parser, StringBuilder builder, boolean ordered, int line);
		public void onCutBlock(HtmlParser parser, StringBuilder builder);
		public void onStartEnd(HtmlParser parser, StringBuilder builder, boolean end);
		
		public SpanProvider initSpanProvider(HtmlParser parser);
	}
	
	public static interface SpanProvider
	{
		public CharSequence transformBuilder(HtmlParser parser, StringBuilder builder);
	}
	
	public static class TagData
	{
		public static int UNDEFINED = 0;
		public static int ENABLED = 1;
		public static int DISABLED = 2;
		
		public boolean block;
		public boolean spaced;
		public int preformatted;
		
		public TagData(boolean block, boolean spaced, boolean preformatted)
		{
			this.block = block;
			this.spaced = spaced;
			this.preformatted = preformatted ? ENABLED : UNDEFINED;
		}
	}
	
	private static final int MODE_PARSE = 0;
	private static final int MODE_CLEAR = 1;
	private static final int MODE_UNESCAPE = 2;
	private static final int MODE_UNMARK = 3;
	
	private final String mSource;
	private final StringBuilder mBuilder = new StringBuilder();
	private final Markup mMarkup;
	private final int mParsingMode;
	private final SpanProvider mSpanProvider;

	private final String mParentPostNumber;
	
	private final Object mExtra;
	
	private HtmlParser(String source, Markup markup, int parsingMode, String parentPostNumber, Object extra)
	{
		if (parsingMode == MODE_UNESCAPE) source = "<span>" + source + "</span>";
		mSource = source;
		mMarkup = markup;
		mParsingMode = parsingMode;
		mParentPostNumber = parentPostNumber;
		mExtra = extra;
		boolean spannedPostMarkup = parsingMode == MODE_PARSE || parsingMode == MODE_UNMARK;
		mSpanProvider = spannedPostMarkup ? markup.initSpanProvider(this) : null;
	}
	
	private static final HTMLSchema HTML_SCHEMA = new HTMLSchema();
	
	public CharSequence convert()
	{
		Parser parser = new Parser();
		try
		{
			parser.setProperty("http://www.ccil.org/~cowan/tagsoup/properties/schema", HTML_SCHEMA);
		}
		catch (SAXNotRecognizedException | SAXNotSupportedException e)
		{
			throw new RuntimeException(e);
		}
		parser.setContentHandler(this);
		StringBuilder builder = mBuilder;
		mMarkup.onStartEnd(this, builder, false);
		try
		{
			parser.parse(new InputSource(new StringReader(mSource)));
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
		finally
		{
			mMarkup.onStartEnd(this, builder, true);
		}
		int start = 0;
		int end = 0;
		boolean substring = false;
		boolean found = false;
		int length = builder.length();
		for (int i = 0; i < length; i++)
		{
			if (builder.charAt(i) != '\n')
			{
				start = i;
				found = true;
				substring |= i > 0;
				break;
			}
		}
		if (!found) return "";
		for (int i = length - 1; i >= start; i--)
		{
			if (builder.charAt(i) != '\n')
			{
				end = i + 1;
				substring |= end < length;
				break;
			}
		}
		boolean removeNbsps = mParsingMode == MODE_PARSE || mParsingMode == MODE_CLEAR;
		if (removeNbsps)
		{
			for (int i = 0; i < length; i++)
			{
				char c = builder.charAt(i);
				if (c == '\u00a0') builder.setCharAt(i, ' ');
			}
		}
		if (isParseMode() && mSpanProvider != null)
		{
			CharSequence charSequence = mSpanProvider.transformBuilder(this, builder);
			if (charSequence == null) return "";
			return substring ? charSequence.subSequence(start, end) : charSequence;
		}
		return substring ? builder.substring(start, end) : builder;
	}
	
	public boolean isParseMode()
	{
		return mParsingMode == MODE_PARSE;
	}
	
	public boolean isUnmarkMode()
	{
		return mParsingMode == MODE_UNMARK;
	}
	
	public StringBuilder getBuilder()
	{
		return mBuilder;
	}
	
	public String getParentPostNumber()
	{
		return mParentPostNumber;
	}
	
	@SuppressWarnings("unchecked")
	public <T> T getExtra()
	{
		return (T) mExtra;
	}
	
	@SuppressWarnings("unchecked")
	public <T extends SpanProvider> T getSpanProvider()
	{
		return (T) mSpanProvider;
	}
	
	@Override
	public void setDocumentLocator(Locator locator)
	{
		
	}
	
	@Override
	public void startDocument()
	{
		
	}
	
	@Override
	public void endDocument()
	{
		
	}
	
	@Override
	public void startPrefixMapping(String prefix, String uri)
	{
		
	}
	
	@Override
	public void endPrefixMapping(String prefix)
	{
		
	}
	
	private static class PositiveStateStack
	{
		private boolean[] mState = null;
		private int mPosition = -1;
		
		public void push(boolean state)
		{
			if (state || mPosition >= 0)
			{
				mPosition++;
				if (mState == null) mState = new boolean[4];
				else if (mPosition == mState.length) mState = Arrays.copyOf(mState, mState.length * 2);
				mState[mPosition] = state;
			}
		}
		
		public boolean pop()
		{
			return mPosition >= 0 ? mState[mPosition--] : false;
		}
		
		public boolean check()
		{
			return mPosition >= 0 ? mState[mPosition] : false;
		}
	}
	
	private static final HashMap<String, TagData> DEFAULT_TAGS = new HashMap<>();
	
	static
	{
		DEFAULT_TAGS.put("blockquote", new TagData(true, true, false));
		DEFAULT_TAGS.put("center", new TagData(true, false, false));
		DEFAULT_TAGS.put("div", new TagData(true, false, false));
		DEFAULT_TAGS.put("h1", new TagData(true, true, false));
		DEFAULT_TAGS.put("h2", new TagData(true, true, false));
		DEFAULT_TAGS.put("h3", new TagData(true, true, false));
		DEFAULT_TAGS.put("h4", new TagData(true, true, false));
		DEFAULT_TAGS.put("h5", new TagData(true, true, false));
		DEFAULT_TAGS.put("h6", new TagData(true, true, false));
		DEFAULT_TAGS.put("hr", new TagData(true, false, false));
		DEFAULT_TAGS.put("li", new TagData(true, false, false));
		DEFAULT_TAGS.put("ol", new TagData(true, true, false));
		DEFAULT_TAGS.put("p", new TagData(true, true, false));
		DEFAULT_TAGS.put("pre", new TagData(true, true, true));
		DEFAULT_TAGS.put("ul", new TagData(true, true, false));
		
		// Show tables as lists
		DEFAULT_TAGS.put("table", new TagData(true, false, false));
		DEFAULT_TAGS.put("td", new TagData(true, false, false));
		DEFAULT_TAGS.put("th", new TagData(true, false, false));
		DEFAULT_TAGS.put("tr", new TagData(true, true, false));
	}
	
	private final TagData mTagData = new TagData(false, false, false);
	
	private TagData fillBaseTagData(String tagName)
	{
		TagData tagData = mTagData;
		TagData copyTagData = DEFAULT_TAGS.get(tagName);
		if (copyTagData != null)
		{
			tagData.block = copyTagData.block;
			tagData.spaced = copyTagData.spaced;
			tagData.preformatted = copyTagData.preformatted;
		}
		else
		{
			tagData.block = false;
			tagData.spaced = false;
			tagData.preformatted = TagData.UNDEFINED;
		}
		return tagData;
	}
	
	private void appendLineBreak()
	{
		StringBuilder builder = mBuilder;
		boolean mayAppend = true;
		if (mParsingMode == MODE_PARSE)
		{
			int length = builder.length();
			if (length >= 2) mayAppend = builder.charAt(length - 1) != '\n' || builder.charAt(length - 2) != '\n';
			else mayAppend = length == 1 && builder.charAt(0) != '\n';
		}
		if (mayAppend) builder.append("\n");
	}
	
	private void appendBlockBreak(boolean spacedTag)
	{
		switch (mLastBlock)
		{
			case LAST_BLOCK_NONE:
			{
				appendLineBreak();
				if (spacedTag) appendLineBreak();
				break;
			}
			case LAST_BLOCK_COMMON:
			{
				if (spacedTag) appendLineBreak();
				break;
			}
			case LAST_BLOCK_SPACED:
			{
				// Do nothing
				break;
			}
		}
	}

	/*
	 * Removes unnecessary whitespaces in the end of block.
	 */
	private void removeBlockLastWhitespaces()
	{
		if (!mPreformattedMode.check())
		{
			StringBuilder builder = mBuilder;
			int length = builder.length();
			int remove = 0;
			for (int i = length - 1, s = length - mLastCharactersLength; i >= s; i--)
			{
				char c = builder.charAt(i);
				if (c == ' ') remove++; else break;
			}
			if (remove > 0)
			{
				builder.delete(length - remove, length);
				mMarkup.onCutBlock(this, builder);
				mLastCharactersLength -= remove;
			}
		}
	}
	
	private static final int LAST_BLOCK_NONE = 0;
	private static final int LAST_BLOCK_COMMON = 1;
	private static final int LAST_BLOCK_SPACED = 2;
	
	private int mLastCharactersLength = 0;
	private int mLastBlock = LAST_BLOCK_NONE;
	private PositiveStateStack mBlockMode = new PositiveStateStack();
	private PositiveStateStack mSpacedMode = new PositiveStateStack();
	private PositiveStateStack mPreformattedMode = new PositiveStateStack();
	
	private int mTableStart = 0;
	
	private boolean mOrderedList;
	private int mListStart = -1;
	
	@Override
	public void startElement(String uri, String localName, String qName, Attributes attributes)
	{
		if (mParsingMode == MODE_UNESCAPE) return;
		String tagName = localName;
		StringBuilder builder = mBuilder;
		if ("br".equals(tagName)) return; // Ignore
		TagData tagData = fillBaseTagData(tagName);
		Object object = mMarkup.onBeforeTagStart(this, builder, tagName, attributes, tagData);
		boolean blockTag = tagData.block;
		boolean spacedTag = blockTag && tagData.spaced;
		boolean preformattedTag = tagData.preformatted == TagData.ENABLED ||
				tagData.preformatted == TagData.UNDEFINED && mPreformattedMode.check();
		if (blockTag) appendBlockBreak(spacedTag);
		mMarkup.onTagStart(this, builder, tagName, attributes, object);
		mBlockMode.push(blockTag);
		mSpacedMode.push(spacedTag);
		mPreformattedMode.push(preformattedTag);
		mLastBlock = blockTag ? spacedTag ? LAST_BLOCK_SPACED : LAST_BLOCK_COMMON : LAST_BLOCK_NONE;
		
		if (tagName.equals("tr") || tagName.equals("th") || tagName.equals("td"))
		{
			if (tagName.equals("tr")) mTableStart = 1; else
			{
				int length = builder.length();
				builder.append(mTableStart).append(". ");
				mLastBlock = LAST_BLOCK_NONE;
				mLastCharactersLength = builder.length() - length;
				int colspan;
				try
				{
					colspan = Integer.parseInt(attributes.getValue("", "colspan"));
				}
				catch (Exception e)
				{
					colspan = 1;
				}
				mTableStart += colspan;
			}
		}
		else if (tagName.equals("ol") || tagName.equals("ul"))
		{
			mOrderedList = tagName.equals("ol");
			mListStart = 0;
		}
		else if (tagName.equals("li") && mListStart >= 0)
		{
			int added = mMarkup.onListLineStart(this, builder, mOrderedList, ++mListStart);
			if (added > 0)
			{
				mLastBlock = LAST_BLOCK_NONE;
				mLastCharactersLength = added;
			}
		}
	}
	
	@Override
	public void endElement(String uri, String localName, String qName)
	{
		if (mParsingMode == MODE_UNESCAPE) return;
		String tagName = localName;
		StringBuilder builder = mBuilder;
		if ("br".equals(tagName))
		{
			removeBlockLastWhitespaces();
			appendLineBreak();
			mLastBlock = LAST_BLOCK_COMMON;
			return;
		}
		boolean blockTag = mBlockMode.pop();
		boolean spacedTag = mSpacedMode.pop();
		mPreformattedMode.pop();
		if (blockTag) removeBlockLastWhitespaces();
		mMarkup.onTagEnd(this, builder, tagName);
		if (blockTag) appendBlockBreak(spacedTag);
		if ((tagName.equals("ol") || tagName.equals("ul"))) mListStart = -1;
		mLastBlock = blockTag ? spacedTag ? LAST_BLOCK_SPACED : LAST_BLOCK_COMMON : LAST_BLOCK_NONE;
	}
	
	@Override
	public void characters(char ch[], int start, int length)
	{
		StringBuilder builder = mBuilder;
		if (mParsingMode == MODE_UNESCAPE)
		{
			builder.append(ch, start, length);
			return;
		}
		int realLength = 0;
		if (mPreformattedMode.check())
		{
			int lastLineBeginning = 0;
			for (int i = builder.length() - 1; i >= 0; i--)
			{
				if (builder.charAt(i) == '\n')
				{
					lastLineBeginning = i + 1;
					break;
				}
			}
			char p = '\0';
			for (int i = start, to = start + length; i < to; i++)
			{
				char c = ch[i];
				if (c == '\n' && i - 1 == to) break; // Last line break may be ignored
				// \r\r - 2 spaces, \n\n - 2 spaces, \n\r - 2 spaces, \r\n - 1 space
				if ((c >= ' ' || c == '\n' || c == '\r') && !(c == '\n' && p == '\r'))
				{
					if (c == '\r' || c == '\n')
					{
						builder.append('\n');
						lastLineBeginning = builder.length();
					}
					else builder.append(c);
					realLength++;
				}
				else if (c == '\t')
				{
					final int tabSize = 8;
					int lineLength = builder.length() - lastLineBeginning;
					int size = tabSize - lineLength % tabSize;
					for (int j = 0 ; j < size; j++) builder.append(' ');
					realLength += size;
				}
				p = c;
			}
		}
		else
		{
			for (int i = start, to = start + length; i < to; i++)
			{
				char c = ch[i];
				// Special characters (< 0x20): \n, \r and \t are handled as whitespace. The rest are ignored.
				// This behavior is the same as in Firefox.
				if (c == '\n' || c == '\r' || c == '\t') ch[i] = ' ';
			}
			char p = builder.length() > 0 ? builder.charAt(builder.length() - 1) : ' ';
			for (int i = start, to = start + length; i < to; i++)
			{
				char c = ch[i];
				// Ignore special characters
				if (c >= ' ')
				{
					if (c != ' ' || p != ' ' && p != '\n')
					{
						builder.append(c);
						realLength++;
					}
					p = c;
				}
			}
		}
		if (mLastBlock == LAST_BLOCK_NONE) mLastCharactersLength += realLength;
		else mLastCharactersLength = realLength;
		if (realLength > 0) mLastBlock = LAST_BLOCK_NONE;
	}
	
	@Override
	public void ignorableWhitespace(char ch[], int start, int length)
	{
		
	}
	
	@Override
	public void processingInstruction(String target, String data)
	{
		
	}
	
	@Override
	public void skippedEntity(String name)
	{
		
	}
	
	private static final Pattern COLOR_PATTERN = Pattern.compile("color: ?(?:rgba?\\((\\d+), ?(\\d+)," +
			" ?(\\d+)(?:, ?\\d+)?\\)|(#[0-9A-Fa-f]+|[A-Za-z]+))");
	
	public Integer getColorAttribute(Attributes attributes)
	{
		String spanStyle = attributes.getValue("", "style");
		if (spanStyle != null)
		{
			Matcher matcher = COLOR_PATTERN.matcher(spanStyle);
			if (matcher.find())
			{
				String colorString = matcher.group(4);
				if (!StringUtils.isEmpty(colorString))
				{
					try
					{
						int color = Color.parseColor(colorString);
						return Color.BLACK | color;
					}
					catch (IllegalArgumentException e)
					{
						
					}
				}
				else
				{
					int r = Integer.parseInt(matcher.group(1));
					int g = Integer.parseInt(matcher.group(2));
					int b = Integer.parseInt(matcher.group(3));
					return Color.rgb(r, g, b);
				}
			}
		}
		return null;
	}
	
	private static final Markup MARKUP_IMPL = new Markup()
	{
		@Override
		public Object onBeforeTagStart(HtmlParser parser, StringBuilder builder, String tagName,
				Attributes attributes, TagData tagData)
		{
			return null;
		}
		
		@Override
		public void onTagStart(HtmlParser parser, StringBuilder builder, String tagName,
				Attributes attributes, Object object)
		{
			
		}
		
		@Override
		public void onTagEnd(HtmlParser parser, StringBuilder builder, String tagName)
		{
			
		}
		
		@Override
		public int onListLineStart(HtmlParser parser, StringBuilder builder, boolean ordered, int line)
		{
			return 0;
		}
		
		@Override
		public void onCutBlock(HtmlParser parser, StringBuilder builder)
		{
			
		}
		
		@Override
		public void onStartEnd(HtmlParser parser, StringBuilder builder, boolean end)
		{
			
		}
		
		@Override
		public SpanProvider initSpanProvider(HtmlParser parser)
		{
			return null;
		}
	};
}