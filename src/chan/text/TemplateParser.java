package chan.text;

import android.util.Pair;
import chan.annotation.Extendable;
import chan.annotation.Public;
import chan.util.CommonUtils;
import java.util.ArrayList;
import java.util.HashMap;

// TODO CHAN
// Remove this class and ChanManger.CompatPathClassLoader after updating
// alphachan alterchan brchan candydollchan chaosach chiochan dangeru exach fiftyfive fourplebs haibane kropyvach
// lainchan lolifox nulltirech onechanca ponyach randomarchive sevenchan synch taima tiretirech twentyseven uboachan
// valkyria wizardchan
// Added: 24.09.20 08:23
@SuppressWarnings("ALL")
@Public
public final class TemplateParser<H> {
	private final HashMap<String, ArrayList<AttributeMatcher<H>>> openMatchers = new HashMap<>();
	private final HashMap<String, ArrayList<AttributeMatcher<H>>> closeMatchers = new HashMap<>();
	private final ArrayList<TextCallback<H>> textCallbacks = new ArrayList<>();
	private boolean ready;

	private final ArrayList<Pair<String, AttributeMatcher<H>>> buildingMatchers = new ArrayList<>();
	private OpenCallback<H> openCallback;
	private ContentCallback<H> contentCallback;
	private CloseCallback<H> closeCallback;

	public TemplateParser() {}

	public static <H> InitialBuilder<H> builder() {
		return new TemplateParser<H>().contentBuilder;
	}

	private static class AttributeMatcher<H> {
		public enum Method {EQUALS, STARTS, CONTAINS, ENDS}

		private final String attribute;
		private final String value;
		private final Method method;

		public OpenCallback<H> openCallback;
		public ContentCallback<H> contentCallback;
		public CloseCallback<H> closeCallback;

		public AttributeMatcher(String attribute, String value, Method method) {
			this.attribute = attribute;
			this.value = value;
			this.method = method;
		}

		public boolean match(Attributes attributes) {
			if (method == null) {
				return true;
			}
			String value = attributes.get(attribute);
			switch (method) {
				case EQUALS: {
					return CommonUtils.equals(value, this.value);
				}
				case STARTS: {
					return value != null && value.startsWith(this.value);
				}
				case CONTAINS: {
					return value != null && value.contains(this.value);
				}
				case ENDS: {
					return value != null && value.endsWith(this.value);
				}
			}
			throw new RuntimeException();
		}
	}

	private void copyCallbacks() {
		if (openCallback != null || contentCallback != null || closeCallback != null) {
			if ((openCallback != null || contentCallback != null) && closeCallback != null) {
				throw new IllegalStateException("OpenCallback and ContentCallback can not be defined "
						+ "with CloseCallback at once");
			}
			for (Pair<String, AttributeMatcher<H>> pair : buildingMatchers) {
				if (closeCallback != null && pair.second.attribute != null) {
					throw new IllegalStateException("Attributed tag definition is not supported for closing tags");
				}
				HashMap<String, ArrayList<AttributeMatcher<H>>> map = closeCallback != null
						? closeMatchers : openMatchers;
				ArrayList<AttributeMatcher<H>> matchers = map.get(pair.first);
				if (matchers == null) {
					matchers = new ArrayList<>();
					map.put(pair.first, matchers);
				}
				pair.second.openCallback = openCallback;
				pair.second.contentCallback = contentCallback;
				pair.second.closeCallback = closeCallback;
				matchers.add(pair.second);
			}
			buildingMatchers.clear();
			openCallback = null;
			contentCallback = null;
			closeCallback = null;
		}
	}

	private void normalize() {
		for (ArrayList<AttributeMatcher<H>> matchers : openMatchers.values()) {
			for (int i = 0, j = matchers.size(); i < j; i++) {
				AttributeMatcher<H> matcher = matchers.get(i);
				if (matcher.attribute == null) {
					// Move to end
					matchers.remove(i);
					matchers.add(matcher);
					j--;
				}
			}
		}
	}

	private void checkReady() {
		if (ready) {
			throw new IllegalStateException("You can not call this method after prepare() call");
		}
	}

	public TemplateParser<H> name(String tagName) {
		return tag(tagName, null, null, null);
	}

	public TemplateParser<H> equals(String tagName, String attribute, String value) {
		return tag(tagName, attribute, value, AttributeMatcher.Method.EQUALS);
	}

	public TemplateParser<H> starts(String tagName, String attribute, String value) {
		return tag(tagName, attribute, value, AttributeMatcher.Method.STARTS);
	}

	public TemplateParser<H> contains(String tagName, String attribute, String value) {
		return tag(tagName, attribute, value, AttributeMatcher.Method.CONTAINS);
	}

	public TemplateParser<H> ends(String tagName, String attribute, String value) {
		return tag(tagName, attribute, value, AttributeMatcher.Method.ENDS);
	}

	private TemplateParser<H> tag(String tagName, String attribute, String value, AttributeMatcher.Method method) {
		checkReady();
		copyCallbacks();
		if (attribute == null) {
			value = null;
		}
		buildingMatchers.add(new Pair<>(tagName, new AttributeMatcher<>(attribute, value, method)));
		return this;
	}

	public TemplateParser<H> open(OpenCallback<H> openCallback) {
		checkReady();
		checkHasMatchers();
		this.openCallback = openCallback;
		return this;
	}

	public TemplateParser<H> content(ContentCallback<H> contentCallback) {
		checkReady();
		checkHasMatchers();
		this.contentCallback = contentCallback;
		return this;
	}

	public TemplateParser<H> close(CloseCallback<H> closeCallback) {
		checkReady();
		checkHasMatchers();
		this.closeCallback = closeCallback;
		return this;
	}

	private void checkHasMatchers() {
		if (buildingMatchers.isEmpty()) {
			throw new IllegalStateException("You must define at least one parsing rule before adding this callback");
		}
	}

	public TemplateParser<H> text(TextCallback<H> textCallback) {
		checkReady();
		copyCallbacks();
		if (!buildingMatchers.isEmpty()) {
			throw new IllegalStateException("This callback can not be used with any parsing rules");
		}
		textCallbacks.add(textCallback);
		return this;
	}

	public TemplateParser<H> prepare() {
		checkReady();
		copyCallbacks();
		normalize();
		ready = true;
		return this;
	}

	@Public
	public void parse(String source, H holder) throws ParseException {
		if (!ready) {
			throw new IllegalStateException("prepare() was not called");
		}
		try {
			GroupParser.parse(source, new Implementation<>(this, holder));
		} catch (FinishException e) {
			// finish() was called
		}
	}

	@Public
	public static final class Attributes {
		private static final String NULL = "null";

		private GroupParser.Attributes attributes;

		private final HashMap<String, String> lastValues = new HashMap<>();

		@Public
		@SuppressWarnings("StringEquality")
		public String get(String attribute) {
			String value = lastValues.get(attribute);
			if (value == null) {
				value = attributes.get(attribute);
				lastValues.put(attribute, value != null ? value : NULL);
			}
			return value == NULL ? null : value;
		}

		public void set(GroupParser.Attributes attributes) {
			this.attributes = attributes;
			lastValues.clear();
		}
	}

	@Public
	public static final class Instance {
		private final Implementation implementation;

		public Instance(Implementation implementation) {
			this.implementation = implementation;
		}

		@Public
		public void finish() {
			implementation.finish = true;
		}
	}

	@Extendable
	public interface OpenCallback<H> {
		@Extendable
		public boolean onOpen(Instance instance, H holder, String tagName, Attributes attributes) throws ParseException;
	}

	@Extendable
	public interface ContentCallback<H> {
		@Extendable
		public void onContent(Instance instance, H holder, String text) throws ParseException;
	}

	@Extendable
	public interface CloseCallback<H> {
		@Extendable
		public void onClose(Instance instance, H holder, String tagName) throws ParseException;
	}

	@Extendable
	public interface TextCallback<H> {
		@Extendable
		public void onText(Instance instance, H holder, String source, int start, int end) throws ParseException;
	}

	private static class FinishException extends ParseException {}

	private static class Implementation<H> implements GroupParser.Callback {
		private final TemplateParser<H> parser;
		private final H holder;

		private final Attributes attributes = new Attributes();
		private final Instance instance = new Instance(this);

		private AttributeMatcher<H> workMatcher;
		private boolean finish = false;

		public Implementation(TemplateParser<H> parser, H holder) {
			this.parser = parser;
			this.holder = holder;
		}

		private void checkFinish() throws FinishException {
			if (finish) {
				throw new FinishException();
			}
		}

		@Override
		public boolean onStartElement(GroupParser parser, String tagName,
				GroupParser.Attributes attributes) throws ParseException {
			ArrayList<AttributeMatcher<H>> matchers = this.parser.openMatchers.get(tagName);
			if (matchers != null) {
				this.attributes.set(attributes);
				for (AttributeMatcher<H> matcher : matchers) {
					if (matcher.match(this.attributes)) {
						boolean readContent;
						if (matcher.openCallback != null) {
							readContent = matcher.openCallback.onOpen(instance, holder, tagName, this.attributes);
							checkFinish();
						} else {
							readContent = true;
						}
						if (readContent) {
							workMatcher = matcher;
							return true;
						}
					}
				}
			}
			return false;
		}

		@Deprecated
		@Override
		public boolean onStartElement(GroupParser parser, String tagName, String attrs) {
			throw new IllegalStateException();
		}

		@Override
		public void onEndElement(GroupParser parser, String tagName) throws ParseException {
			ArrayList<AttributeMatcher<H>> matchers = this.parser.closeMatchers.get(tagName);
			if (matchers != null) {
				for (AttributeMatcher<H> matcher : matchers) {
					matcher.closeCallback.onClose(instance, holder, tagName);
					checkFinish();
				}
			}
		}

		@Override
		public void onGroupComplete(GroupParser parser, String text) throws ParseException {
			if (workMatcher.contentCallback != null) {
				workMatcher.contentCallback.onContent(instance, holder, text);
				checkFinish();
			}
		}

		@Override
		public void onText(GroupParser parser, CharSequence text) throws ParseException {
			String textString = null;
			ArrayList<TextCallback<H>> textCallbacks = this.parser.textCallbacks;
			for (int i = 0, size = textCallbacks.size(); i < size; i++) {
				if (textString == null) {
					textString = text.toString();
				}
				textCallbacks.get(i).onText(instance, holder, textString, 0, textString.length());
				checkFinish();
			}
		}

		@Deprecated
		@Override
		public void onText(GroupParser parser, String source, int start, int end) {
			throw new IllegalStateException();
		}
	}

	@Public
	public interface SimpleRuleBuilder<H> {
		@Public
		public SimpleBuilder<H> name(String tagName);
	}

	@Public
	public interface ComplexSimpleRuleBuilder<H> {
		@Public
		public ComplexBuilder<H> name(String tagName);
	}

	@Public
	public interface ComplexRuleBuilder<H> {
		@Public
		public ComplexBuilder<H> equals(String tagName, String attribute, String value);

		@Public
		public ComplexBuilder<H> starts(String tagName, String attribute, String value);

		@Public
		public ComplexBuilder<H> contains(String tagName, String attribute, String value);

		@Public
		public ComplexBuilder<H> ends(String tagName, String attribute, String value);
	}

	@Public
	public interface OpenBuilder<H> {
		@Public
		public ContentBuilder<H> open(OpenCallback<H> openCallback);

		@Public
		public InitialBuilder<H> content(ContentCallback<H> contentCallback);
	}

	@Public
	public interface InitialBuilder<H> extends SimpleRuleBuilder<H>, ComplexRuleBuilder<H> {
		@Public
		public InitialBuilder<H> text(TextCallback<H> textCallback);

		@Public
		public TemplateParser<H> prepare();
	}

	@Public
	public interface SimpleBuilder<H> extends SimpleRuleBuilder<H>, ComplexRuleBuilder<H>, OpenBuilder<H> {
		@Public
		public InitialBuilder<H> close(CloseCallback<H> closeCallback);
	}

	@Public
	public interface ComplexBuilder<H> extends ComplexSimpleRuleBuilder<H>, ComplexRuleBuilder<H>, OpenBuilder<H> {}

	@Public
	public interface ContentBuilder<H> extends InitialBuilder<H> {
		@Public
		public InitialBuilder<H> content(ContentCallback<H> contentCallback);
	}

	private ContentBuilder<H> contentBuilder = new ContentBuilder<H>() {
		@Override
		public SimpleBuilder<H> name(String tagName) {
			TemplateParser.this.name(tagName);
			return simpleBuilder;
		}

		@Override
		public ComplexBuilder<H> equals(String tagName, String attribute, String value) {
			TemplateParser.this.equals(tagName, attribute, value);
			return complexBuilder;
		}

		@Override
		public ComplexBuilder<H> starts(String tagName, String attribute, String value) {
			TemplateParser.this.starts(tagName, attribute, value);
			return complexBuilder;
		}

		@Override
		public ComplexBuilder<H> contains(String tagName, String attribute, String value) {
			TemplateParser.this.contains(tagName, attribute, value);
			return complexBuilder;
		}

		@Override
		public ComplexBuilder<H> ends(String tagName, String attribute, String value) {
			TemplateParser.this.ends(tagName, attribute, value);
			return complexBuilder;
		}

		@Override
		public InitialBuilder<H> text(TextCallback<H> textCallback) {
			TemplateParser.this.text(textCallback);
			return contentBuilder;
		}

		@Override
		public InitialBuilder<H> content(ContentCallback<H> contentCallback) {
			TemplateParser.this.content(contentCallback);
			return contentBuilder;
		}

		@Override
		public TemplateParser<H> prepare() {
			TemplateParser.this.prepare();
			return TemplateParser.this;
		}
	};

	private SimpleBuilder<H> simpleBuilder = new SimpleBuilder<H>() {
		@Override
		public SimpleBuilder<H> name(String tagName) {
			TemplateParser.this.name(tagName);
			return simpleBuilder;
		}

		@Override
		public ComplexBuilder<H> equals(String tagName, String attribute, String value) {
			TemplateParser.this.equals(tagName, attribute, value);
			return complexBuilder;
		}

		@Override
		public ComplexBuilder<H> starts(String tagName, String attribute, String value) {
			TemplateParser.this.starts(tagName, attribute, value);
			return complexBuilder;
		}

		@Override
		public ComplexBuilder<H> contains(String tagName, String attribute, String value) {
			TemplateParser.this.contains(tagName, attribute, value);
			return complexBuilder;
		}

		@Override
		public ComplexBuilder<H> ends(String tagName, String attribute, String value) {
			TemplateParser.this.ends(tagName, attribute, value);
			return complexBuilder;
		}

		@Override
		public ContentBuilder<H> open(OpenCallback<H> openCallback) {
			TemplateParser.this.open(openCallback);
			return contentBuilder;
		}

		@Override
		public InitialBuilder<H> content(ContentCallback<H> contentCallback) {
			TemplateParser.this.content(contentCallback);
			return contentBuilder;
		}

		@Override
		public InitialBuilder<H> close(CloseCallback<H> closeCallback) {
			TemplateParser.this.close(closeCallback);
			return contentBuilder;
		}
	};

	private ComplexBuilder<H> complexBuilder = new ComplexBuilder<H>() {
		@Override
		public ComplexBuilder<H> name(String tagName) {
			TemplateParser.this.name(tagName);
			return complexBuilder;
		}

		@Override
		public ComplexBuilder<H> equals(String tagName, String attribute, String value) {
			TemplateParser.this.equals(tagName, attribute, value);
			return complexBuilder;
		}

		@Override
		public ComplexBuilder<H> starts(String tagName, String attribute, String value) {
			TemplateParser.this.starts(tagName, attribute, value);
			return complexBuilder;
		}

		@Override
		public ComplexBuilder<H> contains(String tagName, String attribute, String value) {
			TemplateParser.this.contains(tagName, attribute, value);
			return complexBuilder;
		}

		@Override
		public ComplexBuilder<H> ends(String tagName, String attribute, String value) {
			TemplateParser.this.ends(tagName, attribute, value);
			return complexBuilder;
		}

		@Override
		public ContentBuilder<H> open(OpenCallback<H> openCallback) {
			TemplateParser.this.open(openCallback);
			return contentBuilder;
		}

		@Override
		public InitialBuilder<H> content(ContentCallback<H> contentCallback) {
			TemplateParser.this.content(contentCallback);
			return contentBuilder;
		}
	};
}
