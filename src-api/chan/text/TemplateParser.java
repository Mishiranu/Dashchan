package chan.text;

import java.util.ArrayList;
import java.util.HashMap;

import android.util.Pair;

import chan.annotation.Extendable;
import chan.annotation.Public;
import chan.util.StringUtils;

@Public
public final class TemplateParser<H>
{
	private final HashMap<String, ArrayList<AttributeMatcher<H>>> mOpenMatchers = new HashMap<>();
	private final HashMap<String, ArrayList<AttributeMatcher<H>>> mCloseMatchers = new HashMap<>();
	private final ArrayList<TextCallback<H>> mTextCallbacks = new ArrayList<>();
	private boolean mReady;

	private final ArrayList<Pair<String, AttributeMatcher<H>>> mBuildingMatchers = new ArrayList<>();
	private OpenCallback<H> mOpenCallback;
	private ContentCallback<H> mContentCallback;
	private CloseCallback<H> mCloseCallback;

	@Public
	public TemplateParser()
	{

	}

	private static class AttributeMatcher<H>
	{
		public enum Method {EQUALS, STARTS, CONTAINS, ENDS}

		private final String mAttribute;
		private final String mValue;
		private final Method mMethod;

		public OpenCallback<H> openCallback;
		public ContentCallback<H> contentCallback;
		public CloseCallback<H> closeCallback;

		public AttributeMatcher(String attribute, String value, Method method)
		{
			mAttribute = attribute;
			mValue = value;
			mMethod = method;
		}

		public boolean match(Attributes attributes)
		{
			if (mMethod == null) return true;
			String value = attributes.get(mAttribute);
			switch (mMethod)
			{
				case EQUALS: return StringUtils.equals(value, mValue);
				case STARTS: return value != null && value.startsWith(mValue);
				case CONTAINS: return value != null && value.contains(mValue);
				case ENDS: return value != null && value.endsWith(mValue);
			}
			throw new RuntimeException();
		}
	}

	private void copyCallbacks()
	{
		if (mOpenCallback != null || mContentCallback != null || mCloseCallback != null)
		{
			if ((mOpenCallback != null || mContentCallback != null) && mCloseCallback != null)
			{
				throw new IllegalStateException("OpenCallback and ContentCallback can not be defined "
						+ "with CloseCallback at once");
			}
			for (Pair<String, AttributeMatcher<H>> pair : mBuildingMatchers)
			{
				if (mCloseCallback != null && pair.second.mAttribute != null)
				{
					throw new IllegalStateException("Attributed tag definition is not supported for closing tags");
				}
				HashMap<String, ArrayList<AttributeMatcher<H>>> map = mCloseCallback != null
						? mCloseMatchers : mOpenMatchers;
				ArrayList<AttributeMatcher<H>> matchers = map.get(pair.first);
				if (matchers == null)
				{
					matchers = new ArrayList<>();
					map.put(pair.first, matchers);
				}
				pair.second.openCallback = mOpenCallback;
				pair.second.contentCallback = mContentCallback;
				pair.second.closeCallback = mCloseCallback;
				matchers.add(pair.second);
			}
			mBuildingMatchers.clear();
			mOpenCallback = null;
			mContentCallback = null;
			mCloseCallback = null;
		}
	}

	private void normalize()
	{
		for (ArrayList<AttributeMatcher<H>> matchers : mOpenMatchers.values())
		{
			for (int i = 0, j = matchers.size(); i < j; i++)
			{
				AttributeMatcher<H> matcher = matchers.get(i);
				if (matcher.mAttribute == null)
				{
					// Move to end
					matchers.remove(i);
					matchers.add(matcher);
					j--;
				}
			}
		}
	}

	private void checkReady()
	{
		if (mReady) throw new IllegalStateException("You can not call this method after prepare() call");
	}

	@Public
	public TemplateParser<H> name(String tagName)
	{
		return tag(tagName, null, null, null);
	}

	@Public
	public TemplateParser<H> equals(String tagName, String attribute, String value)
	{
		return tag(tagName, attribute, value, AttributeMatcher.Method.EQUALS);
	}

	@Public
	public TemplateParser<H> starts(String tagName, String attribute, String value)
	{
		return tag(tagName, attribute, value, AttributeMatcher.Method.STARTS);
	}

	@Public
	public TemplateParser<H> contains(String tagName, String attribute, String value)
	{
		return tag(tagName, attribute, value, AttributeMatcher.Method.CONTAINS);
	}

	@Public
	public TemplateParser<H> ends(String tagName, String attribute, String value)
	{
		return tag(tagName, attribute, value, AttributeMatcher.Method.ENDS);
	}

	private TemplateParser<H> tag(String tagName, String attribute, String value, AttributeMatcher.Method method)
	{
		checkReady();
		copyCallbacks();
		if (attribute == null) value = null;
		mBuildingMatchers.add(new Pair<>(tagName, new AttributeMatcher<>(attribute, value, method)));
		return this;
	}

	@Public
	public TemplateParser<H> open(OpenCallback<H> openCallback)
	{
		checkReady();
		checkHasMatchers();
		mOpenCallback = openCallback;
		return this;
	}

	@Public
	public TemplateParser<H> content(ContentCallback<H> contentCallback)
	{
		checkReady();
		checkHasMatchers();
		mContentCallback = contentCallback;
		return this;
	}

	@Public
	public TemplateParser<H> close(CloseCallback<H> closeCallback)
	{
		checkReady();
		checkHasMatchers();
		mCloseCallback = closeCallback;
		return this;
	}

	private void checkHasMatchers()
	{
		if (mBuildingMatchers.isEmpty())
		{
			throw new IllegalStateException("You must define at least one parsing rule before adding this callback");
		}
	}

	@Public
	public TemplateParser<H> text(TextCallback<H> textCallback)
	{
		checkReady();
		copyCallbacks();
		if (!mBuildingMatchers.isEmpty())
		{
			throw new IllegalStateException("This callback can not be used with any parsing rules");
		}
		mTextCallbacks.add(textCallback);
		return this;
	}

	@Public
	public TemplateParser<H> prepare()
	{
		checkReady();
		copyCallbacks();
		normalize();
		mReady = true;
		return this;
	}

	@Public
	public void parse(String source, H holder) throws ParseException
	{
		if (!mReady) throw new IllegalStateException("prepare() was not called");
		try
		{
			GroupParser.parse(source, new Implementation<>(this, holder));
		}
		catch (FinishException e)
		{
			// finish() was called
		}
	}

	@Public
	public static final class Attributes
	{
		private static final Object NULL = new Object();

		private GroupParser mParser;
		private String mAttributes;

		private final HashMap<String, Object> mLastValues = new HashMap<>();

		@Public
		public String get(String attribute)
		{
			Object value = mLastValues.get(attribute);
			if (value != null) return value == NULL ? null : (String) value;
			String stringValue = mParser.getAttr(mAttributes, attribute);
			mLastValues.put(attribute, stringValue != null ? stringValue : NULL);
			return stringValue;
		}

		public void set(GroupParser parser, String attributes)
		{
			mParser = parser;
			mAttributes = attributes;
			mLastValues.clear();
		}
	}

	@Public
	public static final class Instance
	{
		private final Implementation mImplementation;

		public Instance(Implementation implementation)
		{
			mImplementation = implementation;
		}

		@Public
		public void finish()
		{
			mImplementation.mFinish = true;
		}
	}

	@Extendable
	public interface OpenCallback<H>
	{
		@Extendable
		public boolean onOpen(Instance instance, H holder, String tagName, Attributes attributes) throws ParseException;
	}

	@Extendable
	public interface ContentCallback<H>
	{
		@Extendable
		public void onContent(Instance instance, H holder, String text) throws ParseException;
	}

	@Extendable
	public interface CloseCallback<H>
	{
		@Extendable
		public void onClose(Instance instance, H holder, String tagName) throws ParseException;
	}

	@Extendable
	public interface TextCallback<H>
	{
		@Extendable
		public void onText(Instance instance, H holder, String source, int start, int end) throws ParseException;
	}

	private static class FinishException extends ParseException
	{

	}

	private static class Implementation<H> implements GroupParser.Callback
	{
		private final TemplateParser<H> mParser;
		private final H mHolder;

		private final Attributes mAttributes = new Attributes();
		private final Instance mInstance = new Instance(this);

		private AttributeMatcher<H> mWorkMatcher;
		private boolean mFinish = false;

		public Implementation(TemplateParser<H> parser, H holder)
		{
			mParser = parser;
			mHolder = holder;
		}

		private void checkFinish() throws FinishException
		{
			if (mFinish) throw new FinishException();
		}

		@Override
		public boolean onStartElement(GroupParser parser, String tagName, String attrs) throws ParseException
		{
			ArrayList<AttributeMatcher<H>> matchers = mParser.mOpenMatchers.get(tagName);
			if (matchers != null)
			{
				mAttributes.set(parser, attrs);
				for (AttributeMatcher<H> matcher : matchers)
				{
					if (matcher.match(mAttributes))
					{
						boolean readContent;
						if (matcher.openCallback != null)
						{
							readContent = matcher.openCallback.onOpen(mInstance, mHolder, tagName, mAttributes);
							checkFinish();
						}
						else readContent = true;
						if (readContent)
						{
							mWorkMatcher = matcher;
							return true;
						}
					}
				}
			}
			return false;
		}

		@Override
		public void onEndElement(GroupParser parser, String tagName) throws ParseException
		{
			ArrayList<AttributeMatcher<H>> matchers = mParser.mCloseMatchers.get(tagName);
			if (matchers != null)
			{
				for (AttributeMatcher<H> matcher : matchers)
				{
					matcher.closeCallback.onClose(mInstance, mHolder, tagName);
					checkFinish();
				}
			}
		}

		@Override
		public void onGroupComplete(GroupParser parser, String text) throws ParseException
		{
			if (mWorkMatcher.contentCallback != null)
			{
				mWorkMatcher.contentCallback.onContent(mInstance, mHolder, text);
				checkFinish();
			}
		}

		@Override
		public void onText(GroupParser parser, String source, int start, int end) throws ParseException
		{
			ArrayList<TextCallback<H>> textCallbacks = mParser.mTextCallbacks;
			for (int i = 0, size = textCallbacks.size(); i < size; i++)
			{
				textCallbacks.get(i).onText(mInstance, mHolder, source, start, end);
				checkFinish();
			}
		}
	}
}