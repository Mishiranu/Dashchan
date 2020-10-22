package com.mishiranu.dashchan.text;

import android.graphics.Color;
import chan.util.StringUtils;
import com.mishiranu.dashchan.content.model.PostNumber;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
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

public class HtmlParser<E, D, S extends HtmlParser.SpanProvider<E>> implements ContentHandler {
	public static <E, D, S extends SpanProvider<E>> CharSequence spanify(String source,
			Markup<E, D, S> markup, String threadNumber, PostNumber originalPostNumber, E extra) {
		return parse(source, markup, Mode.SPANIFY, threadNumber, originalPostNumber, extra);
	}

	public static String clear(String source) {
		return parse(source, null, Mode.CLEAR, null, null, null).toString();
	}

	public static <E, D, S extends SpanProvider<E>> String unmark(String source, Markup<E, D, S> markup, E extra) {
		return parse(source, markup, Mode.UNMARK, null, null, extra).toString();
	}

	private static <E, D, S extends SpanProvider<E>> CharSequence parse(String source,
			Markup<E, D, S> markup, Mode parsingMode, String threadNumber, PostNumber originalPostNumber, E extra) {
		if (StringUtils.isEmpty(source)) {
			return "";
		}
		return new HtmlParser<>(source, markup, parsingMode, threadNumber, originalPostNumber, extra).convert();
	}

	public interface Markup<E, D, S extends SpanProvider<E>> {
		D onBeforeTagStart(HtmlParser<E, D, S> parser, StringBuilder builder, String tagName,
				Attributes attributes, TagData tagData);
		void onTagStart(HtmlParser<E, D, S> parser, StringBuilder builder, String tagName,
				Attributes attributes, D object);
		void onTagEnd(HtmlParser<E, D, S> parser, StringBuilder builder, String tagName);

		int onListLineStart(HtmlParser<E, D, S> parser, StringBuilder builder, boolean ordered, int line);
		void onCutBlock(HtmlParser<E, D, S> parser, StringBuilder builder);

		S initSpanProvider(HtmlParser<E, D, S> parser);
	}

	public interface SpanProvider<E> {
		CharSequence transformBuilder(HtmlParser<E, ?, ?> parser, StringBuilder builder);
	}

	public static class TagData {
		public enum Preformatted {UNDEFINED, ENABLED, DISABLED}

		public boolean block;
		public boolean spaced;
		public Preformatted preformatted;

		public TagData(boolean block, boolean spaced, boolean preformatted) {
			this.block = block;
			this.spaced = spaced;
			this.preformatted = preformatted ? Preformatted.ENABLED : Preformatted.UNDEFINED;
		}
	}

	private enum Mode {SPANIFY, CLEAR, UNMARK}

	private final String source;
	private final StringBuilder builder = new StringBuilder();
	private final Markup<E, D, S> markup;
	private final Mode parsingMode;
	private final S spanProvider;

	private final String threadNumber;
	private final PostNumber originalPostNumber;
	private final E extra;

	private HtmlParser(String source, Markup<E, D, S> markup, Mode parsingMode,
			String threadNumber, PostNumber originalPostNumber, E extra) {
		if (markup == null) {
			@SuppressWarnings("unchecked")
			Markup<E, D, S> uncheckedMarkup = (Markup<E, D, S>) IDLE_MARKUP;
			markup = uncheckedMarkup;
		}
		source = source.replace("&#10;", "\n");
		this.source = source;
		this.markup = markup;
		this.parsingMode = parsingMode;
		this.threadNumber = threadNumber;
		this.originalPostNumber = originalPostNumber;
		this.extra = extra;
		spanProvider = isSpanifyMode() || isUnmarkMode() ? markup.initSpanProvider(this) : null;
	}

	public static final HTMLSchema SCHEMA = new HTMLSchema();

	public CharSequence convert() {
		Parser parser = new Parser();
		try {
			parser.setProperty(Parser.schemaProperty, SCHEMA);
		} catch (SAXNotRecognizedException | SAXNotSupportedException e) {
			throw new RuntimeException(e);
		}
		parser.setContentHandler(this);
		StringBuilder builder = this.builder;
		try {
			parser.parse(new InputSource(new StringReader(source)));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		removeBlockLastWhitespaces();
		int start = 0;
		int end = 0;
		boolean substring = false;
		boolean found = false;
		int length = builder.length();
		for (int i = 0; i < length; i++) {
			if (builder.charAt(i) != '\n') {
				start = i;
				found = true;
				substring |= i > 0;
				break;
			}
		}
		if (!found) {
			return "";
		}
		for (int i = length - 1; i >= start; i--) {
			if (builder.charAt(i) != '\n') {
				end = i + 1;
				substring |= end < length;
				break;
			}
		}
		if (isSpanifyMode() || isClearMode()) {
			// Replace non-breaking spaces with regular spaces
			for (int i = 0; i < length; i++) {
				char c = builder.charAt(i);
				if (c == '\u00a0') {
					builder.setCharAt(i, ' ');
				}
			}
		}
		if (isSpanifyMode() && spanProvider != null) {
			CharSequence charSequence = spanProvider.transformBuilder(this, builder);
			if (charSequence == null) {
				return "";
			}
			return substring ? charSequence.subSequence(start, end) : charSequence;
		}
		return substring ? builder.substring(start, end) : builder;
	}

	public boolean isSpanifyMode() {
		return parsingMode == Mode.SPANIFY;
	}

	public boolean isClearMode() {
		return parsingMode == Mode.CLEAR;
	}

	public boolean isUnmarkMode() {
		return parsingMode == Mode.UNMARK;
	}

	public StringBuilder getBuilder() {
		return builder;
	}

	public String getThreadNumber() {
		return threadNumber;
	}

	public PostNumber getOriginalPostNumber() {
		return originalPostNumber;
	}

	public E getExtra() {
		return extra;
	}

	public S getSpanProvider() {
		return spanProvider;
	}

	@Override
	public void setDocumentLocator(Locator locator) {}

	@Override
	public void startDocument() {}

	@Override
	public void endDocument() {}

	@Override
	public void startPrefixMapping(String prefix, String uri) {}

	@Override
	public void endPrefixMapping(String prefix) {}

	private static class PositiveStateStack {
		private boolean[] state = null;
		private int position = -1;

		public void push(boolean state) {
			if (state || position >= 0) {
				position++;
				if (this.state == null) {
					this.state = new boolean[4];
				} else if (position == this.state.length) {
					this.state = Arrays.copyOf(this.state, this.state.length * 2);
				}
				this.state[position] = state;
			}
		}

		public boolean pop() {
			return position >= 0 && state[position--];
		}

		public boolean check() {
			return position >= 0 && state[position];
		}
	}

	private static final HashMap<String, TagData> DEFAULT_TAGS = new HashMap<>();
	private static final HashSet<String> HIDDEN_TAGS = new HashSet<>();

	static {
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

		HIDDEN_TAGS.add("script");
		HIDDEN_TAGS.add("style");
	}

	private final TagData tagData = new TagData(false, false, false);

	private TagData fillBaseTagData(String tagName) {
		TagData tagData = this.tagData;
		TagData copyTagData = DEFAULT_TAGS.get(tagName);
		if (copyTagData != null) {
			tagData.block = copyTagData.block;
			tagData.spaced = copyTagData.spaced;
			tagData.preformatted = copyTagData.preformatted;
		} else {
			tagData.block = false;
			tagData.spaced = false;
			tagData.preformatted = TagData.Preformatted.UNDEFINED;
		}
		return tagData;
	}

	private void appendLineBreak() {
		StringBuilder builder = this.builder;
		boolean mayAppend = true;
		if (isSpanifyMode()) {
			int length = builder.length();
			if (length >= 2) {
				mayAppend = builder.charAt(length - 1) != '\n' || builder.charAt(length - 2) != '\n';
			} else {
				mayAppend = length == 1 && builder.charAt(0) != '\n';
			}
		}
		if (mayAppend) {
			builder.append("\n");
		}
	}

	private void appendBlockBreak(boolean spacedTag) {
		switch (lastBlock) {
			case LAST_BLOCK_NONE: {
				appendLineBreak();
				if (spacedTag) {
					appendLineBreak();
				}
				break;
			}
			case LAST_BLOCK_COMMON: {
				if (spacedTag) {
					appendLineBreak();
				}
				break;
			}
			case LAST_BLOCK_SPACED: {
				// Do nothing
				break;
			}
		}
	}

	// Removes unnecessary whitespaces in the end of block.
	private void removeBlockLastWhitespaces() {
		if (!preformattedMode.check()) {
			StringBuilder builder = this.builder;
			int length = builder.length();
			int remove = 0;
			for (int i = length - 1, s = length - lastCharactersLength; i >= s; i--) {
				char c = builder.charAt(i);
				if (c == ' ') {
					remove++;
				} else {
					break;
				}
			}
			if (remove > 0) {
				builder.delete(length - remove, length);
				markup.onCutBlock(this, builder);
				lastCharactersLength -= remove;
			}
		}
	}

	private static final int LAST_BLOCK_NONE = 0;
	private static final int LAST_BLOCK_COMMON = 1;
	private static final int LAST_BLOCK_SPACED = 2;

	private int lastCharactersLength = 0;
	private int lastBlock = LAST_BLOCK_NONE;
	private final PositiveStateStack blockMode = new PositiveStateStack();
	private final PositiveStateStack spacedMode = new PositiveStateStack();
	private final PositiveStateStack preformattedMode = new PositiveStateStack();

	private int tableStart = 0;

	private boolean orderedList;
	private int listStart = -1;

	private boolean hidden = false;

	@Override
	public void startElement(String uri, String tagName, String qName, Attributes attributes) {
		if (HIDDEN_TAGS.contains(tagName)) {
			hidden = true;
			return;
		}
		StringBuilder builder = this.builder;
		if ("br".equals(tagName)) {
			return; // Ignore tag
		}
		TagData tagData = fillBaseTagData(tagName);
		D object = markup.onBeforeTagStart(this, builder, tagName, attributes, tagData);
		boolean blockTag = tagData.block;
		boolean spacedTag = blockTag && tagData.spaced;
		boolean preformattedTag = tagData.preformatted == TagData.Preformatted.ENABLED ||
				tagData.preformatted == TagData.Preformatted.UNDEFINED && preformattedMode.check();
		if (blockTag) {
			appendBlockBreak(spacedTag);
		}
		markup.onTagStart(this, builder, tagName, attributes, object);
		blockMode.push(blockTag);
		spacedMode.push(spacedTag);
		preformattedMode.push(preformattedTag);
		lastBlock = blockTag ? spacedTag ? LAST_BLOCK_SPACED : LAST_BLOCK_COMMON : LAST_BLOCK_NONE;

		if (tagName.equals("tr") || tagName.equals("th") || tagName.equals("td")) {
			if (tagName.equals("tr")) {
				tableStart = 1;
			} else {
				int length = builder.length();
				builder.append(tableStart).append(". ");
				lastBlock = LAST_BLOCK_NONE;
				lastCharactersLength = builder.length() - length;
				int colspan;
				try {
					colspan = Integer.parseInt(attributes.getValue("", "colspan"));
				} catch (Exception e) {
					colspan = 1;
				}
				tableStart += colspan;
			}
		} else if (tagName.equals("ol") || tagName.equals("ul")) {
			orderedList = tagName.equals("ol");
			listStart = 0;
		} else if (tagName.equals("li") && listStart >= 0) {
			int added = markup.onListLineStart(this, builder, orderedList, ++listStart);
			if (added > 0) {
				lastBlock = LAST_BLOCK_NONE;
				lastCharactersLength = added;
			}
		}
	}

	@Override
	public void endElement(String uri, String tagName, String qName) {
		if (hidden) {
			if (HIDDEN_TAGS.contains(tagName)) {
				hidden = false;
			}
			return;
		}
		StringBuilder builder = this.builder;
		if ("br".equals(tagName)) {
			removeBlockLastWhitespaces();
			appendLineBreak();
			lastBlock = LAST_BLOCK_COMMON;
			return;
		}
		boolean blockTag = blockMode.pop();
		boolean spacedTag = spacedMode.pop();
		preformattedMode.pop();
		if (blockTag) {
			removeBlockLastWhitespaces();
		}
		markup.onTagEnd(this, builder, tagName);
		if (blockTag) {
			appendBlockBreak(spacedTag);
		}
		if ((tagName.equals("ol") || tagName.equals("ul"))) {
			listStart = -1;
		}
		lastBlock = blockTag ? spacedTag ? LAST_BLOCK_SPACED : LAST_BLOCK_COMMON : LAST_BLOCK_NONE;
	}

	@Override
	public void characters(char[] ch, int start, int length) {
		if (hidden) {
			return;
		}
		StringBuilder builder = this.builder;
		int realLength = 0;
		if (preformattedMode.check()) {
			char p = '\0';
			for (int i = start, to = start + length; i < to; i++) {
				char c = ch[i];
				// Last line break may be ignored
				if (c == '\n' && i - 1 == to) {
					break;
				}
				// \r\r - 2 spaces, \n\n - 2 spaces, \n\r - 2 spaces, \r\n - 1 space
				if ((c >= ' ' || c == '\t' || c == '\n' || c == '\r') && !(c == '\n' && p == '\r')) {
					builder.append(c == '\r' ? '\n' : c);
					realLength++;
				}
				p = c;
			}
		} else {
			for (int i = start, to = start + length; i < to; i++) {
				char c = ch[i];
				// Special characters (< 0x20): \n, \r and \t are handled as whitespace. The rest are ignored.
				// This behavior is the same as in Firefox.
				if (c == '\n' || c == '\r' || c == '\t') {
					ch[i] = ' ';
				}
			}
			char p = builder.length() > 0 ? builder.charAt(builder.length() - 1) : ' ';
			for (int i = start, to = start + length; i < to; i++) {
				char c = ch[i];
				// Ignore special characters
				if (c >= ' ') {
					if (c != ' ' || p != ' ' && p != '\n') {
						builder.append(c);
						realLength++;
					}
					p = c;
				}
			}
		}
		if (lastBlock == LAST_BLOCK_NONE) {
			lastCharactersLength += realLength;
		} else {
			lastCharactersLength = realLength;
		}
		if (realLength > 0) {
			lastBlock = LAST_BLOCK_NONE;
		}
	}

	@Override
	public void ignorableWhitespace(char[] ch, int start, int length) {}

	@Override
	public void processingInstruction(String target, String data) {}

	@Override
	public void skippedEntity(String name) {}

	private static final Pattern COLOR_PATTERN = Pattern.compile("color: ?(?:rgba?\\((\\d+), ?(\\d+)," +
			" ?(\\d+)(?:, ?\\d+)?\\)|(#[0-9A-Fa-f]+|[A-Za-z]+))");

	public Integer getColorAttribute(Attributes attributes) {
		String style = attributes.getValue("", "style");
		String color = null;
		if (style != null) {
			Matcher matcher = COLOR_PATTERN.matcher(style);
			if (matcher.find()) {
				if (matcher.group(1) != null) {
					int r = Integer.parseInt(matcher.group(1));
					int g = Integer.parseInt(matcher.group(2));
					int b = Integer.parseInt(matcher.group(3));
					return Color.rgb(r, g, b);
				}
				color = matcher.group(4);
			}
		}
		if (StringUtils.isEmpty(color)) {
			color = attributes.getValue("", "color");
		}
		if (!StringUtils.isEmpty(color)) {
			if (color.charAt(0) == '#' && color.length() != 7) {
				if (color.length() == 4) {
					color = "#" + color.charAt(1) + color.charAt(1) + color.charAt(2) + color.charAt(2)
							+ color.charAt(3) + color.charAt(3);
				} else if (color.length() < 7) {
					color = color + "000000".substring(color.length() - 1);
				} else {
					return null;
				}
			}
			try {
				return Color.BLACK | Color.parseColor(color);
			} catch (IllegalArgumentException e) {
				// Not a color, ignore exception
			}
		}
		return null;
	}

	private static final Markup<?, ?, ?> IDLE_MARKUP = new Markup<Void, Void, SpanProvider<Void>>() {
		@Override
		public Void onBeforeTagStart(HtmlParser<Void, Void, SpanProvider<Void>> parser,
				StringBuilder builder, String tagName, Attributes attributes, TagData tagData) {
			return null;
		}

		@Override
		public void onTagStart(HtmlParser<Void, Void, SpanProvider<Void>> parser,
				StringBuilder builder, String tagName, Attributes attributes, Void object) {}

		@Override
		public void onTagEnd(HtmlParser<Void, Void, SpanProvider<Void>> parser,
				StringBuilder builder, String tagName) {}

		@Override
		public int onListLineStart(HtmlParser<Void, Void, SpanProvider<Void>> parser,
				StringBuilder builder, boolean ordered, int line) {
			return 0;
		}

		@Override
		public void onCutBlock(HtmlParser<Void, Void, SpanProvider<Void>> parser, StringBuilder builder) {}

		@Override
		public SpanProvider<Void> initSpanProvider(HtmlParser<Void, Void, SpanProvider<Void>> parser) {
			return null;
		}
	};
}
