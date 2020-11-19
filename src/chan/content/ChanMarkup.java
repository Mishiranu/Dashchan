package chan.content;

import android.net.Uri;
import android.text.SpannableString;
import android.text.style.StrikethroughSpan;
import android.text.style.UnderlineSpan;
import android.util.Pair;
import chan.annotation.Extendable;
import chan.annotation.Public;
import chan.text.CommentEditor;
import chan.util.StringUtils;
import com.mishiranu.dashchan.content.model.PostNumber;
import com.mishiranu.dashchan.text.HtmlParser;
import com.mishiranu.dashchan.text.style.GainedColorSpan;
import com.mishiranu.dashchan.text.style.HeadingSpan;
import com.mishiranu.dashchan.text.style.ItalicSpan;
import com.mishiranu.dashchan.text.style.LinkSpan;
import com.mishiranu.dashchan.text.style.LinkSuffixSpan;
import com.mishiranu.dashchan.text.style.MediumSpan;
import com.mishiranu.dashchan.text.style.MonospaceSpan;
import com.mishiranu.dashchan.text.style.OverlineSpan;
import com.mishiranu.dashchan.text.style.QuoteSpan;
import com.mishiranu.dashchan.text.style.ScriptSpan;
import com.mishiranu.dashchan.text.style.SpoilerSpan;
import com.mishiranu.dashchan.text.style.TabulationSpan;
import com.mishiranu.dashchan.text.style.UnderlyingSpoilerSpan;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import org.xml.sax.Attributes;

@Extendable
public class ChanMarkup implements Chan.Linked {
	private final Chan.Provider chanProvider;

	static final ChanManager.Initializer INITIALIZER = new ChanManager.Initializer();

	@Public
	public ChanMarkup() {
		this(null);
	}

	ChanMarkup(Chan.Provider chanProvider) {
		if (chanProvider == null) {
			ChanManager.Initializer.Holder holder = INITIALIZER.consume();
			this.chanProvider = holder.chanProvider;
		} else {
			this.chanProvider = chanProvider;
		}
	}

	@Override
	public final void init() {}

	@Override
	public final Chan get() {
		return chanProvider.get();
	}

	@Public
	public static ChanMarkup get(Object object) {
		return ((Chan.Linked) object).get().markup;
	}

	@Public public static final int TAG_BOLD = 0x00000001;
	@Public public static final int TAG_ITALIC = 0x00000002;
	@Public public static final int TAG_UNDERLINE = 0x00000004;
	@Public public static final int TAG_OVERLINE = 0x00000008;
	@Public public static final int TAG_STRIKE = 0x00000010;
	@Public public static final int TAG_SUBSCRIPT = 0x00000020;
	@Public public static final int TAG_SUPERSCRIPT = 0x00000040;
	@Public public static final int TAG_SPOILER = 0x00000080;
	@Public public static final int TAG_QUOTE = 0x00000100;
	@Public public static final int TAG_CODE = 0x00000200;
	@Public public static final int TAG_ASCII_ART = 0x00000400;
	@Public public static final int TAG_HEADING = 0x00000800;

	public static final int TAG_SPECIAL_UNUSED = 0x01000000;
	public static final int TAG_SPECIAL_LINK = 0x01000001;
	public static final int TAG_SPECIAL_COLOR = 0x01000002;
	public static final int TAG_SPECIAL_LINK_SUFFIX = 0x01000003;

	@Extendable
	protected CommentEditor obtainCommentEditor(String boardName) {
		return null;
	}

	@Extendable
	protected boolean isTagSupported(String boardName, int tag) {
		return false;
	}

	private static class MarkupItem {
		public TagItem tagItem;
		public HashMap<String, TagItem> cssClassTagItems;
		public ArrayList<AttributeItem> attrubuteItems;
	}

	private static class AttributeItem {
		public final String attribute;
		public final String value;

		public final TagItem tagItem = new TagItem();

		public AttributeItem(String attribute, String value) {
			this.attribute = attribute;
			this.value = value;
		}
	}

	private static class TagItem {
		public TagItem parentTagItem;

		public int tag;
		public boolean colorable;

		public boolean blockDefined;
		public boolean block;
		public boolean spaced;

		public boolean preformattedDefined;
		public boolean preformatted;

		public void setTag(int tag) {
			this.tag = tag;
		}

		public void setColorable(boolean colorable) {
			this.colorable = colorable;
		}

		public void setBlock(boolean block, boolean spaced) {
			blockDefined = true;
			this.block = block;
			this.spaced = spaced;
		}

		public void setPreformatted(boolean preformatted) {
			preformattedDefined = true;
			this.preformatted = preformatted;
		}

		public void applyTagData(HtmlParser.TagData tagData) {
			if (parentTagItem != null) {
				parentTagItem.applyTagData(tagData);
			}
			if (blockDefined) {
				tagData.block = block;
				tagData.spaced = spaced;
			}
			if (preformattedDefined) {
				tagData.preformatted = preformatted ? HtmlParser.TagData.Preformatted.ENABLED
						: HtmlParser.TagData.Preformatted.DISABLED;
			}
		}

		public boolean isMorePreferredThanParent() {
			return parentTagItem == null || tag != 0 || parentTagItem.tag == 0;
		}
	}

	private final HashMap<String, MarkupItem> markupItems = new HashMap<>();

	private TagItem obtainTagItem(String tagName, boolean withCssClass, String cssClass, boolean withAttribute,
			String attribute, String value) {
		if (withCssClass && cssClass == null) {
			throw new NullPointerException("cssClass must not be null");
		}
		if (withAttribute && (attribute == null || value == null)) {
			throw new NullPointerException("attribute and value must not be null");
		}
		tagName = tagName.toLowerCase(Locale.US);
		MarkupItem markupItem = markupItems.get(tagName);
		if (markupItem == null) {
			markupItem = new MarkupItem();
			markupItems.put(tagName, markupItem);
		}
		if (withCssClass) {
			if (markupItem.cssClassTagItems == null) {
				markupItem.cssClassTagItems = new HashMap<>();
			}
			TagItem tagItem = markupItem.cssClassTagItems.get(cssClass);
			if (tagItem == null) {
				tagItem = new TagItem();
				markupItem.cssClassTagItems.put(cssClass, tagItem);
				tagItem.parentTagItem = markupItem.tagItem;
			}
			return tagItem;
		} else if (withAttribute) {
			if (markupItem.attrubuteItems == null) {
				markupItem.attrubuteItems = new ArrayList<>();
			}
			TagItem tagItem = null;
			for (AttributeItem attributeItem : markupItem.attrubuteItems) {
				if (attribute.equals(attributeItem.attribute) && value.equals(attributeItem.value)) {
					tagItem = attributeItem.tagItem;
					break;
				}
			}
			if (tagItem == null) {
				AttributeItem attributeItem = new AttributeItem(attribute, value);
				markupItem.attrubuteItems.add(attributeItem);
				attributeItem.tagItem.parentTagItem = markupItem.tagItem;
				tagItem = attributeItem.tagItem;
			}
			return tagItem;
		} else {
			if (markupItem.tagItem == null) {
				markupItem.tagItem = new TagItem();
				if (markupItem.cssClassTagItems != null) {
					for (TagItem cssClassTagItem : markupItem.cssClassTagItems.values()) {
						cssClassTagItem.parentTagItem = markupItem.tagItem;
					}
				}
				if (markupItem.attrubuteItems != null) {
					for (AttributeItem attributeItem : markupItem.attrubuteItems) {
						attributeItem.tagItem.parentTagItem = markupItem.tagItem;
					}
				}
			}
			return markupItem.tagItem;
		}
	}

	@Public
	public final void addTag(String tagName, int tag) {
		obtainTagItem(tagName, false, null, false, null, null).setTag(tag);
	}

	@Public
	public final void addTag(String tagName, String cssClass, int tag) {
		obtainTagItem(tagName, true, cssClass, false, null, null).setTag(tag);
	}

	@Public
	public final void addTag(String tagName, String attribute, String value, int tag) {
		obtainTagItem(tagName, false, null, true, attribute, value).setTag(tag);
	}

	@Public
	public final void addColorable(String tagName) {
		obtainTagItem(tagName, false, null, false, null, null).setColorable(true);
	}

	@Public
	public final void addColorable(String tagName, String cssClass) {
		obtainTagItem(tagName, true, cssClass, false, null, null).setColorable(true);
	}

	@Public
	public final void addColorable(String tagName, String attribute, String value) {
		obtainTagItem(tagName, false, null, true, attribute, value).setColorable(true);
	}

	// TODO CHAN
	// Remove this method after updating
	// horochan ponychan
	// Added: 14.05.16 12:12
	@Deprecated
	public final void addBlock(String tagName, boolean spaced) {
		addBlock(tagName, true, spaced);
	}

	@Public
	public final void addBlock(String tagName, boolean block, boolean spaced) {
		obtainTagItem(tagName, false, null, false, null, null).setBlock(block, spaced);
	}

	@Public
	public final void addBlock(String tagName, String cssClass, boolean block, boolean spaced) {
		obtainTagItem(tagName, true, cssClass, false, null, null).setBlock(block, spaced);
	}

	@Public
	public final void addBlock(String tagName, String attribute, String value, boolean block, boolean spaced) {
		obtainTagItem(tagName, false, null, true, attribute, value).setBlock(block, spaced);
	}

	// TODO CHAN
	// Remove this method after updating
	// synch
	// Added: 14.05.16 12:12
	@Deprecated
	public final void addPreformatted(String tagName) {
		addPreformatted(tagName, true);
	}

	@Public
	public final void addPreformatted(String tagName, boolean preformatted) {
		obtainTagItem(tagName, false, null, false, null, null).setPreformatted(preformatted);
	}

	@Public
	public final void addPreformatted(String tagName, String cssClass, boolean preformatted) {
		obtainTagItem(tagName, true, cssClass, false, null, null).setPreformatted(preformatted);
	}

	@Public
	public final void addPreformatted(String tagName, String attribute, String value, boolean preformatted) {
		obtainTagItem(tagName, false, null, true, attribute, value).setPreformatted(preformatted);
	}

	public final HtmlParser.Markup<MarkupExtra, ?, ChanSpanProvider> getMarkup() {
		return markup;
	}

	private static final TagItem UNUSED_TAG_ITEM = new TagItem();

	private final HtmlParser.Markup<MarkupExtra, ?, ChanSpanProvider> markup =
			new HtmlParser.Markup<MarkupExtra, TagItem, ChanSpanProvider>() {
		@Override
		public TagItem onBeforeTagStart(HtmlParser<MarkupExtra, TagItem, ChanSpanProvider> parser,
				StringBuilder builder, String tagName, Attributes attributes, HtmlParser.TagData tagData) {
			if (!tagName.equals("a")) {
				MarkupItem markupItem = markupItems.get(tagName);
				if (markupItem != null) {
					TagItem tagItem = markupItem.tagItem;
					boolean preferredTagItemFound = false;
					if (markupItem.cssClassTagItems != null) {
						String fullCssClass = attributes.getValue("", "class");
						String[] cssClasses = fullCssClass != null ? fullCssClass.split(" +") : null;
						if (cssClasses != null) {
							for (String cssClass : cssClasses) {
								TagItem preferredTagItem = markupItem.cssClassTagItems.get(cssClass);
								if (preferredTagItem != null &&
										preferredTagItem.isMorePreferredThanParent()) {
									tagItem = preferredTagItem;
									preferredTagItemFound = true;
									break;
								}
							}
						}
					}
					if (!preferredTagItemFound && markupItem.attrubuteItems != null) {
						for (AttributeItem attributeItem : markupItem.attrubuteItems) {
							String value = attributes.getValue("", attributeItem.attribute);
							if (attributeItem.value.equals(value) &&
									attributeItem.tagItem.isMorePreferredThanParent()) {
								tagItem = attributeItem.tagItem;
								break;
							}
						}
					}
					if (tagItem != null) {
						tagItem.applyTagData(tagData);
						return tagItem;
					}
					return UNUSED_TAG_ITEM;
				}
			}
			return null;
		}

		@Override
		public void onTagStart(HtmlParser<MarkupExtra, TagItem, ChanSpanProvider> parser,
				StringBuilder builder, String tagName, Attributes attributes, TagItem tagItem) {
			ChanSpanProvider provider = parser.getSpanProvider();
			int tag = 0;
			Object extra = null;
			if (tagName.equals("a")) {
				tag = TAG_SPECIAL_LINK;
				LinkHolder linkHolder = new LinkHolder();
				linkHolder.uriString = StringUtils.nullIfEmpty(attributes.getValue("", "href"));
				extra = linkHolder;
			} else if (tagItem != null) {
				if (tagItem.tag != 0) {
					tag = tagItem.tag;
				} else if (tagItem.colorable) {
					extra = parser.getColorAttribute(attributes);
					if (extra != null) {
						tag = TAG_SPECIAL_COLOR;
					} else {
						tag = TAG_SPECIAL_UNUSED;
					}
				} else {
					tag = TAG_SPECIAL_UNUSED;
				}
			}
			if (tag != 0) {
				if (parser.isUnmarkMode()) {
					CommentEditor commentEditor = provider.commentEditor;
					if (commentEditor != null) {
						String markupTag = commentEditor.getTag(tag, false, 0);
						if (markupTag != null) {
							builder.append(markupTag);
						}
					}
				}
				provider.add(tag, extra, builder.length());
			}
		}

		@Override
		public void onTagEnd(HtmlParser<MarkupExtra, TagItem, ChanSpanProvider> parser,
				StringBuilder builder, String tagName) {
			ChanSpanProvider provider = parser.getSpanProvider();
			StyledItem styledItem = null;
			if (tagName.equals("a") || markupItems.containsKey(tagName)) {
				styledItem = provider.getLastOpenStyledItem();
			}
			if (styledItem != null) {
				if (parser.isUnmarkMode()) {
					CommentEditor commentEditor = provider.commentEditor;
					if (commentEditor != null) {
						if (styledItem.tag != 0) {
							String markupTag = commentEditor.getTag(styledItem.tag, true,
									builder.length() - styledItem.start);
							if (markupTag != null) {
								builder.append(markupTag);
							}
						}
					}
				}
				int end = builder.length();
				if (styledItem.tag == TAG_SPECIAL_LINK) {
					LinkHolder linkHolder = (LinkHolder) styledItem.extra;
					if (parser.isSpanifyMode()) {
						provider.modifyLink(parser, styledItem.start, end, linkHolder);
					} else if (parser.isUnmarkMode()) {
						end += provider.replaceLink(parser, styledItem.start, end, linkHolder.uriString);
					}
				}
				styledItem.close(end);
			}
		}

		@Override
		public int onListLineStart(HtmlParser<MarkupExtra, TagItem, ChanSpanProvider> parser,
				StringBuilder builder, boolean ordered, int line) {
			int length = builder.length();
			if (parser.isSpanifyMode()) {
				if (ordered) {
					builder.append(line).append(". ");
				} else {
					builder.append("\u2022 ");
				}
			} else if (parser.isUnmarkMode()) {
				ChanSpanProvider provider = parser.getSpanProvider();
				CommentEditor commentEditor = provider.commentEditor;
				String mark;
				if (commentEditor != null) {
					if (ordered) {
						mark = commentEditor.getOrderedListMark();
					} else {
						mark = commentEditor.getUnorderedListMark();
					}
				} else {
					if (ordered) {
						mark = null;
					} else {
						mark = "- ";
					}
				}
				if (mark == null) {
					builder.append(line).append(". ");
				} else {
					builder.append(mark);
				}
			}
			return builder.length() - length;
		}

		@Override
		public void onCutBlock(HtmlParser<MarkupExtra, TagItem, ChanSpanProvider> parser, StringBuilder builder) {
			ChanSpanProvider provider = parser.getSpanProvider();
			provider.cut(builder.length());
		}

		@Override
		public ChanSpanProvider initSpanProvider(HtmlParser<MarkupExtra, TagItem, ChanSpanProvider> parser) {
			ChanSpanProvider provider = new ChanSpanProvider();
			if (parser.isUnmarkMode()) {
				MarkupExtra extra = parser.getExtra();
				if (extra != null) {
					String boardName = extra.getBoardName();
					provider.commentEditor = safe.obtainCommentEditor(boardName);
				}
			}
			return provider;
		}
	};

	private static final class NotImplementedException extends Exception {}

	@Extendable
	protected Pair<String, String> obtainPostLinkThreadPostNumbers(String uriString) throws NotImplementedException {
		throw new NotImplementedException();
	}

	private static class LinkHolder {
		public String uriString;
		public PostNumber postNumber;
	}

	private static class LinkSuffixHolder {
		public int suffix;
		public PostNumber postNumber;
	}

	private static class StyledItem {
		public final int tag;
		public final Object extra;

		public int start;
		public int end;

		public StyledItem(int tag, Object extra, int start) {
			this.tag = tag;
			this.extra = extra;
			this.start = start;
			this.end = -1;
		}

		public boolean isClosed() {
			return end >= start;
		}

		public void close(int end) {
			this.end = end;
		}
	}

	public static final class MarkupBuilder {
		public interface Constructor {
			void configure(ChanMarkup markup);
		}

		private final ChanMarkup markup;

		public MarkupBuilder(Constructor constructor) {
			markup = new ChanMarkup(new Chan.Provider(Chan.getFallback()));
			constructor.configure(markup);
		}

		public CharSequence fromHtmlReduced(String html) {
			return StringUtils.reduceEmptyLines(HtmlParser.spanify(html, markup.getMarkup(), null, null, null));
		}
	}

	public class ChanSpanProvider implements HtmlParser.SpanProvider<MarkupExtra> {
		private ChanSpanProvider() {}

		private final ArrayList<StyledItem> styledItems = new ArrayList<>();

		public CommentEditor commentEditor;

		public StyledItem add(int tag, Object extra, int start) {
			StyledItem styledItem = new StyledItem(tag, extra, start);
			styledItems.add(styledItem);
			return styledItem;
		}

		public StyledItem getLastOpenStyledItem() {
			ArrayList<StyledItem> styledItems = this.styledItems;
			for (int i = styledItems.size() - 1; i >= 0; i--) {
				StyledItem styledItem = styledItems.get(i);
				if (!styledItem.isClosed()) {
					return styledItem;
				}
			}
			return null;
		}

		public void cut(int length) {
			ArrayList<StyledItem> styledItems = this.styledItems;
			for (int i = styledItems.size() - 1; i >= 0; i--) {
				StyledItem styledItem = styledItems.get(i);
				if (styledItem.end > length) {
					styledItem.end = length;
				}
				if (styledItem.start > length) {
					styledItem.start = length;
				}
			}
		}

		private void modifyLink(HtmlParser<MarkupExtra, ?, ?> parser, int start, int end, LinkHolder linkHolder) {
			String processThreadNumber = parser.getThreadNumber();
			PostNumber processOriginalPostNumber = parser.getOriginalPostNumber();
			if (linkHolder.uriString != null && processThreadNumber != null && processOriginalPostNumber != null) {
				StringBuilder builder = parser.getBuilder();
				String string = builder.substring(start, end);
				if (string.length() < 3) {
					return;
				}
				// Fast match >>\d+
				for (int i = 0; i < string.length(); i++) {
					char c = string.charAt(i);
					if (!(i < 2 && c == '>' || i >= 2 && c >= '0' && c <= '9')) {
						return;
					}
				}
				String uriString = linkHolder.uriString;
				String threadNumber = null;
				PostNumber postNumber = null;
				try {
					Pair<String, String> numbers = null;
					try {
						numbers = obtainPostLinkThreadPostNumbers(uriString);
					} catch (LinkageError | RuntimeException e) {
						ExtensionException.logException(e, false);
					}
					if (numbers != null) {
						threadNumber = numbers.first;
						if (!StringUtils.isEmpty(numbers.second)) {
							postNumber = PostNumber.parseNullable(numbers.second);
						}
					}
				} catch (NotImplementedException e) {
					MarkupExtra extra = parser.getExtra();
					if (extra != null) {
						ChanLocator locator = get().locator;
						Uri uri = locator.validateClickedUriString(uriString, extra.getBoardName(),
								extra.getThreadNumber());
						threadNumber = locator.safe(false).getThreadNumber(uri);
						postNumber = locator.safe(false).getPostNumber(uri);
					}
				}
				if (threadNumber != null || postNumber != null) {
					LinkSuffixHolder linkSuffixHolder = new LinkSuffixHolder();
					linkSuffixHolder.postNumber = postNumber;
					linkHolder.postNumber = postNumber == null ? processOriginalPostNumber : postNumber;
					if (!StringUtils.isEmpty(threadNumber) && !processThreadNumber.equals(threadNumber)) {
						linkSuffixHolder.suffix |= LinkSuffixSpan.SUFFIX_DIFFERENT_THREAD;
					} else if (postNumber == null || processOriginalPostNumber.equals(postNumber)) {
						linkSuffixHolder.suffix |= LinkSuffixSpan.SUFFIX_ORIGINAL_POSTER;
					}
					builder.append('\u00a0');
					int length = builder.length();
					add(TAG_SPECIAL_LINK_SUFFIX, linkSuffixHolder, length - 1).close(length);
				}
			}
		}

		/*
		 * Returns number of characters added or removed
		 */
		private int replaceLink(HtmlParser<MarkupExtra, ?, ?> parser, int start, int end, String uriString) {
			if (uriString != null) {
				StringBuilder builder = parser.getBuilder();
				String string = builder.substring(start, end);
				if (!string.startsWith(">>")) {
					builder.replace(start, end, uriString);
					return uriString.length() - end + start;
				}
			}
			return 0;
		}

		@Override
		public CharSequence transformBuilder(HtmlParser<MarkupExtra, ?, ?> parser, StringBuilder builder) {
			SpannableString spannable = new SpannableString(builder);
			for (int i = 0; i < spannable.length(); i++) {
				if (spannable.charAt(i) == '\t') {
					spannable.setSpan(new TabulationSpan(), i, i + 1, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
				}
			}
			for (StyledItem styledItem : styledItems) {
				if (styledItem.isClosed()) {
					Object span = null;
					switch (styledItem.tag) {
						case TAG_BOLD: {
							span = new MediumSpan();
							break;
						}
						case TAG_ITALIC: {
							span = new ItalicSpan();
							break;
						}
						case TAG_SUBSCRIPT: {
							span = new ScriptSpan(false);
							break;
						}
						case TAG_SUPERSCRIPT: {
							span = new ScriptSpan(true);
							break;
						}
						case TAG_QUOTE: {
							span = new QuoteSpan();
							break;
						}
						case TAG_SPOILER: {
							span = new UnderlyingSpoilerSpan();
							break;
						}
						case TAG_UNDERLINE: {
							span = new UnderlineSpan();
							break;
						}
						case TAG_OVERLINE: {
							@SuppressWarnings("InstantiationOfUtilityClass")
							OverlineSpan result = new OverlineSpan();
							span = result;
							break;
						}
						case TAG_STRIKE: {
							span = new StrikethroughSpan();
							break;
						}
						case TAG_CODE: {
							span = new MonospaceSpan(false);
							break;
						}
						case TAG_ASCII_ART: {
							span = new MonospaceSpan(true);
							break;
						}
						case TAG_HEADING: {
							span = new HeadingSpan();
							break;
						}
						case TAG_SPECIAL_LINK: {
							LinkHolder linkHolder = (LinkHolder) styledItem.extra;
							if (linkHolder.uriString != null) {
								span = new LinkSpan(linkHolder.uriString, linkHolder.postNumber);
							}
							break;
						}
						case TAG_SPECIAL_COLOR: {
							span = new GainedColorSpan((int) styledItem.extra);
							break;
						}
						case TAG_SPECIAL_LINK_SUFFIX: {
							LinkSuffixHolder linkSuffixHolder = (LinkSuffixHolder) styledItem.extra;
							span = new LinkSuffixSpan(linkSuffixHolder.suffix, linkSuffixHolder.postNumber);
							break;
						}
					}
					if (span != null) {
						spannable.setSpan(span, styledItem.start, styledItem.end,
								SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
					}
				}
			}
			// Spoiler spans must be above the rest spans
			for (StyledItem styledItem : styledItems) {
				if (styledItem.tag == TAG_SPOILER && styledItem.isClosed()) {
					spannable.setSpan(new SpoilerSpan(), styledItem.start, styledItem.end,
							SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
				}
			}
			return spannable;
		}
	}

	public interface MarkupExtra {
		String getBoardName();
		String getThreadNumber();
	}

	public static final class Safe {
		private final ChanMarkup markup;

		private Safe(ChanMarkup markup) {
			this.markup = markup;
		}

		public CommentEditor obtainCommentEditor(String boardName) {
			try {
				return markup.obtainCommentEditor(boardName);
			} catch (LinkageError | RuntimeException e) {
				ExtensionException.logException(e, false);
				return null;
			}
		}

		public boolean isTagSupported(String boardName, int tag) {
			try {
				return markup.isTagSupported(boardName, tag);
			} catch (LinkageError | RuntimeException e) {
				ExtensionException.logException(e, false);
				return false;
			}
		}
	}

	private final Safe safe = new Safe(this);

	public final Safe safe() {
		return safe;
	}
}
