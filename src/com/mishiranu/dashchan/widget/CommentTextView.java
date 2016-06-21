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

package com.mishiranu.dashchan.widget;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.TypefaceSpan;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ListView;
import android.widget.TextView;

import chan.content.ChanLocator;
import chan.util.StringUtils;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.graphics.ActionIconSet;
import com.mishiranu.dashchan.text.style.LinkSpan;
import com.mishiranu.dashchan.text.style.OverlineSpan;
import com.mishiranu.dashchan.text.style.SpoilerSpan;
import com.mishiranu.dashchan.ui.Replyable;
import com.mishiranu.dashchan.util.NavigationUtils;
import com.mishiranu.dashchan.util.ResourceUtils;

/*
 * TextView with LinkSpan feedback ability without conflict with selection MovementMethod.
 * This class has method to start text selection directly.
 */
public class CommentTextView extends TextView
{
	private final int[][] mDeltaAttempts;
	private final int mTouchSlop;
	private final int mAdditionalPadding;
	
	private ClickableSpan mSpanToClick;
	private float mStartX, mStartY;
	private float mLastX, mLastY;
	private long mLastXYSetted;
	private long mLastStartSelectionCalled;
	private int mCurrentMax = Integer.MAX_VALUE, mReservedMax;
	private boolean mMaxModeLines;
	private boolean mAppendAdditionalPadding;
	private boolean mUseAdditionalPadding;
	
	private CommentListener mCommentListener;
	private LinkListener mLinkListener;
	private boolean mSpoilersEnabled;
	
	private String mChanName;
	private String mBoardName;
	private String mThreadNumber;
	
	private Replyable mReplyable;
	private String mPostNumber;
	
	private static final double[][] BASE_POINTS;
	private static final int RING_RADIUS = 6;
	private static final int RINGS = 3;
	
	static
	{
		BASE_POINTS = new double[8][];
		BASE_POINTS[0] = new double[] {-1, 0};
		BASE_POINTS[1] = new double[] {0, -1};
		BASE_POINTS[2] = new double[] {1, 0};
		BASE_POINTS[3] = new double[] {0, 1};
		double sqrth2 = Math.sqrt(0.5);
		BASE_POINTS[4] = new double[] {-sqrth2, -sqrth2};
		BASE_POINTS[5] = new double[] {sqrth2, -sqrth2};
		BASE_POINTS[6] = new double[] {-sqrth2, sqrth2};
		BASE_POINTS[7] = new double[] {sqrth2, sqrth2};
	}
	
	private static LinkListener DEFAULT_LINK_LISTENER = new LinkListener()
	{
		@Override
		public void onLinkClick(CommentTextView view, String chanName, Uri uri, boolean confirmed)
		{
			NavigationUtils.handleUri(view.getContext(), chanName, uri, NavigationUtils.BrowserType.AUTO);
		}
		
		@Override
		public void onLinkLongClick(CommentTextView view, String chanName, Uri uri)
		{
			
		}
	};
	
	public CommentTextView(Context context, AttributeSet attrs)
	{
		this(context, attrs, android.R.attr.textViewStyle);
	}
	
	public CommentTextView(Context context, AttributeSet attrs, int defStyleAttr)
	{
		super(context, attrs, defStyleAttr);
		float density = ResourceUtils.obtainDensity(this);
		int delta = (int) (RING_RADIUS * density);
		mDeltaAttempts = new int[1 + RINGS * BASE_POINTS.length][2];
		mDeltaAttempts[0][0] = 0;
		mDeltaAttempts[0][1] = 0;
		int add = 1;
		for (int r = 0; r < RINGS; r++)
		{
			for (int i = 0; i < BASE_POINTS.length; i++)
			{
				for (int j = 0; j < BASE_POINTS[i].length; j++)
				{
					mDeltaAttempts[i + add][j] = (int) ((r + 1) * delta * BASE_POINTS[i][j]);
				}
			}
			add += BASE_POINTS.length;
		}
		mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
		mAdditionalPadding = (int) (64f * density);
		super.setCustomSelectionActionModeCallback(new CustomSelectionCallback());
	}
	
	public static interface ClickableSpan
	{
		public void setClicked(boolean clicked);
	}
	
	public static interface CommentListener
	{
		public void onRequestSiblingsInvalidate(CommentTextView view);
		public String onPrepareToCopy(CommentTextView view, Spannable text, int start, int end);
	}
	
	public static interface LinkListener
	{
		public void onLinkClick(CommentTextView view, String chanName, Uri uri, boolean confirmed);
		public void onLinkLongClick(CommentTextView view, String chanName, Uri uri);
	}
	
	public void setCommentListener(CommentListener l)
	{
		mCommentListener = l;
	}
	
	public void setLinkListener(LinkListener l, String chanName, String boardName, String threadNumber)
	{
		mLinkListener = l;
		mChanName = chanName;
		mBoardName = boardName;
		mThreadNumber = threadNumber;
	}
	
	private LinkListener getLinkListener()
	{
		return mLinkListener != null ? mLinkListener : DEFAULT_LINK_LISTENER;
	}
	
	public void setSubjectAndComment(CharSequence subject, CharSequence comment)
	{
		boolean hasSubject = !StringUtils.isEmpty(subject);
		boolean hasComment = !StringUtils.isEmpty(comment);
		if (hasComment && comment instanceof Spanned)
		{
			SpoilerSpan[] spoilerSpans = ((Spanned) comment).getSpans(0, comment.length(), SpoilerSpan.class);
			if (spoilerSpans != null)
			{
				boolean enabled = mSpoilersEnabled;
				for (SpoilerSpan spoilerSpan : spoilerSpans) spoilerSpan.setEnabled(enabled);
			}
		}
		if (hasSubject)
		{
			SpannableStringBuilder spannable = new SpannableStringBuilder();
			spannable.append(subject);
			int length = spannable.length();
			spannable.setSpan(new TypefaceSpan("sans-serif-light"), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
			spannable.setSpan(new RelativeSizeSpan(4f / 3f), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
			if (hasComment)
			{
				spannable.append("\n\n");
				spannable.setSpan(new RelativeSizeSpan(0.75f), length, length + 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				spannable.append(comment);
			}
			setText(spannable);
		}
		else if (hasComment) setText(comment);
		else setText(null);
	}
	
	public void setSpoilersEnabled(boolean enabled)
	{
		// Must invalidate text after changing this field
		mSpoilersEnabled = enabled;
	}
	
	private final Runnable mSyncRunnable = new Runnable()
	{
		@Override
		public void run()
		{
			if (mCommentListener != null) mCommentListener.onRequestSiblingsInvalidate(CommentTextView.this);
		}
	};
	
	private boolean startSelectionActionMode(int start, int end)
	{
		selectNecessaryText(start, end);
		try
		{
			Object editor;
			Class<?> editorClass;
			if (EDITOR_FIELD != null)
			{
				editor = EDITOR_FIELD.get(CommentTextView.this);
				editorClass = EDITOR_FIELD.getType();
			}
			else
			{
				editor = CommentTextView.this;
				editorClass = TextView.class;
			}
			Method method = null;
			NoSuchMethodException exception = null;
			String[] methodNames = {"startSelectionActionModeWithSelection", "startSelectionActionMode"};
			for (String m : methodNames)
			{
				try
				{
					method = editorClass.getDeclaredMethod(m);
					method.setAccessible(true);
					break;
				}
				catch (NoSuchMethodException e)
				{
					exception = e;
				}
			}
			if (method == null) throw exception;
			Object result = method.invoke(editor);
			if (result instanceof Boolean) return (boolean) result;
			return true;
		}
		catch (InvocationTargetException e)
		{
			Throwable t = e.getCause();
			if (t instanceof Error) throw (Error) t;
			if (t instanceof RuntimeException) throw (RuntimeException) t;
			return false;
		}
		catch (Exception e)
		{
			return false;
		}
	}
	
	private static final Pattern LIST_PATTERN = Pattern.compile("^(?:(?:\\d+[\\.\\)]|[\u2022-]) |>(?!>) ?)");
	
	private void selectNecessaryText(int start, int end)
	{
		Spannable spannable = (Spannable) getText();
		int length = spannable.length();
		if (mLastStartSelectionCalled - mLastXYSetted <= getPreferredDoubleTapTimeout() &&
				(start < 0 || end < 0 || start > length || end > length || start >= end))
		{
			start = 0;
			end = spannable.length();
			int offset = getOffsetForPosition(mLastX, mLastY);
			if (offset >= 0 && offset < length)
			{
				for (int i = offset; i >= 0; i--)
				{
					if (spannable.charAt(i) == '\n')
					{
						start = i + 1;
						break;
					}
				}
				for (int i = offset; i < length; i++)
				{
					if (spannable.charAt(i) == '\n')
					{
						end = i;
						break;
					}
				}
				if (end > start)
				{
					String part = spannable.subSequence(start, end).toString();
					Matcher matcher = LIST_PATTERN.matcher(part);
					if (matcher.find()) start += matcher.group().length();
				}
			}
		}
		if (end <= start)
		{
			start = 0;
			end = spannable.length();
		}
		Selection.setSelection(spannable, start, end);
	}
	
	public void setReplyable(Replyable replyable, String postNumber)
	{
		mReplyable = replyable;
		mPostNumber = postNumber;
	}
	
	@Override
	public void setCustomSelectionActionModeCallback(ActionMode.Callback actionModeCallback)
	{
		
	}
	
	private String getPartialCommentString(Spannable text, int start, int end)
	{
		return mCommentListener != null ? mCommentListener.onPrepareToCopy(CommentTextView.this, text, start, end)
				: text.subSequence(start, end).toString();
	}
	
	private Uri extractSelectedUri()
	{
		int start = getSelectionStart();
		int end = getSelectionEnd();
		if (end > start)
		{
			String text = getText().toString().substring(start, end);
			String fixedText = StringUtils.fixParsedUriString(text);
			if (text.equals(fixedText))
			{
				if (!text.matches("[a-z]+:.*")) text = "http://" + text.replaceAll("^/+", "");
				Uri uri = Uri.parse(text);
				if (uri != null)
				{
					if (StringUtils.isEmpty(uri.getAuthority())) uri = uri.buildUpon().scheme("http").build();
					String host = uri.getHost();
					if (host != null && host.matches(".+\\..+") && ChanLocator.getDefault().isWebScheme(uri))
					{
						return uri;
					}
				}
			}
		}
		return null;
	}
	
	private ActionMode mCurrentActionMode;
	private Menu mCurrentActionModeMenu;
	
	private class CustomSelectionCallback implements ActionMode.Callback, Runnable
	{
		private void addCopyMenuItemIfNotNull(Menu menu, MenuItem menuItem, int flags)
		{
			if (menuItem != null)
			{
				menu.add(0, menuItem.getItemId(), 0, menuItem.getTitle()).setIcon(menuItem.getIcon())
						.setShowAsAction(flags);
			}
		}
		
		@TargetApi(Build.VERSION_CODES.M)
		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu)
		{
			mCurrentActionMode = mode;
			mCurrentActionModeMenu = menu;
			int pasteResId = ResourceUtils.getSystemSelectionIcon(getContext(), "actionModePasteDrawable",
					"ic_menu_paste_holo_dark");
			SparseArray<MenuItem> validItems = new SparseArray<>();
			validItems.put(android.R.id.selectAll, menu.findItem(android.R.id.selectAll));
			validItems.put(android.R.id.copy, menu.findItem(android.R.id.copy));
			menu.clear();
			ActionIconSet set = new ActionIconSet(getContext());
			int flags = MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT;
			if (C.API_MARSHMALLOW && mode.getType() == ActionMode.TYPE_FLOATING)
			{
				if (mReplyable != null)
				{
					menu.add(0, android.R.id.button1, 0, R.string.action_quote).setIcon(pasteResId)
							.setShowAsAction(flags);
				}
				menu.add(0, android.R.id.button2, 0, R.string.action_browser).setIcon(set.getId(R.attr.actionForward))
						.setShowAsAction(flags);
				addCopyMenuItemIfNotNull(menu, validItems.get(android.R.id.copy), flags);
				addCopyMenuItemIfNotNull(menu, validItems.get(android.R.id.selectAll), flags);
			}
			else
			{
				addCopyMenuItemIfNotNull(menu, validItems.get(android.R.id.selectAll), flags);
				addCopyMenuItemIfNotNull(menu, validItems.get(android.R.id.copy), flags);
				if (mReplyable != null)
				{
					menu.add(0, android.R.id.button1, 0, R.string.action_quote).setIcon(pasteResId)
							.setShowAsAction(flags);
				}
				menu.add(0, android.R.id.button2, 0, R.string.action_browser).setIcon(set.getId(R.attr.actionForward))
						.setShowAsAction(flags);
			}
			return true;
		}
		
		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu)
		{
			Uri uri = extractSelectedUri();
			menu.findItem(android.R.id.button2).setVisible(uri != null);
			return true;
		}
		
		@Override
		public void onDestroyActionMode(ActionMode mode)
		{
			if (mCurrentActionMode == mode)
			{
				mCurrentActionMode = null;
				mCurrentActionModeMenu = null;
			}
			post(this);
		}
		
		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item)
		{
			Spannable text = getText() instanceof Spannable ? (Spannable) getText() : null;
			if (text != null)
			{
				int selStart = getSelectionStart(), selEnd = getSelectionEnd();
				int min = Math.max(0, Math.min(selStart, selEnd)), max = Math.max(0, Math.max(selStart, selEnd));
				switch (item.getItemId())
				{
					case android.R.id.selectAll:
					{
						Selection.setSelection(text, 0, text.length());
						break;
					}
					case android.R.id.copy:
					{
						StringUtils.copyToClipboard(getContext(), getPartialCommentString(text, min, max));
						mode.finish();
						break;
					}
					case android.R.id.button1:
					{
						if (mReplyable != null)
						{
							mReplyable.onRequestReply(new Replyable.ReplyData(mPostNumber,
									getPartialCommentString(text, min, max)));
						}
						mode.finish();
						break;
					}
					case android.R.id.button2:
					{
						Uri uri = extractSelectedUri();
						if (uri != null) getLinkListener().onLinkClick(CommentTextView.this, null, uri, true);
						mode.finish();
						break;
					}
				}
			}
			return true;
		}
		
		@Override
		public void run()
		{
			setTextIsSelectable(false);
		}
	}
	
	@Override
	protected void onSelectionChanged(int selStart, int selEnd)
	{
		super.onSelectionChanged(selStart, selEnd);
		if (mCurrentActionMode != null)
		{
			// This works better than simple "invalidate" call
			// because "invalidate" can cause action mode resizing bug in Android 5
			getCustomSelectionActionModeCallback().onPrepareActionMode(mCurrentActionMode, mCurrentActionModeMenu);
		}
	}
	
	@Override
	public void setMaxLines(int maxlines)
	{
		mReservedMax = 0;
		mCurrentMax = maxlines;
		mMaxModeLines = true;
		super.setMaxLines(maxlines);
	}
	
	@Override
	public void setMaxHeight(int maxHeight)
	{
		mReservedMax = 0;
		mCurrentMax = maxHeight;
		mMaxModeLines = false;
		super.setMaxHeight(maxHeight);
	}
	
	@Override
	public void setPadding(int left, int top, int right, int bottom)
	{
		if (mUseAdditionalPadding) bottom += mAdditionalPadding;
		super.setPadding(left, top, right, bottom);
	}
	
	public void setAppendAdditionalPadding(boolean appendAdditionalPadding)
	{
		mAppendAdditionalPadding = appendAdditionalPadding;
		updateUseAdditionalPadding();
	}
	
	public int getAdditionalPadding()
	{
		return mAppendAdditionalPadding ? mAdditionalPadding : 0;
	}
	
	private void updateUseAdditionalPadding()
	{
		boolean needUseAdditionalPadding = mAppendAdditionalPadding && isTextSelectable();
		if (needUseAdditionalPadding != mUseAdditionalPadding)
		{
			int bottom = getPaddingBottom();
			bottom = needUseAdditionalPadding ? bottom + mAdditionalPadding : bottom - mAdditionalPadding;
			// Set false to normal calculate padding (see setPadding method overriding)
			mUseAdditionalPadding = false;
			setPadding(getPaddingLeft(), getPaddingTop(), getPaddingRight(), bottom);
			mUseAdditionalPadding = needUseAdditionalPadding;
		}
	}
	
	@Override
	public void setTextIsSelectable(boolean selectable)
	{
		if (selectable == isTextSelectable()) return;
		if (selectable)
		{
			mReservedMax = mCurrentMax;
			super.setMaxHeight(Integer.MAX_VALUE);
		}
		else if (mReservedMax > 0)
		{
			// Also will reset mReservedMax
			if (mMaxModeLines) setMaxLines(mReservedMax); else setMaxHeight(mReservedMax);
		}
		super.setTextIsSelectable(selectable);
		updateUseAdditionalPadding();
	}
	
	private int mSetTextDepth = 0;
	
	@Override
	public void setText(CharSequence text, BufferType type)
	{
		// Some devices may call setText recursively; fix it
		if (mSetTextDepth > 0)
		{
			String className = getClass().getName();
			StackTraceElement[] elements = Thread.currentThread().getStackTrace();
			for (int i = 0; i < elements.length; i++)
			{
				StackTraceElement element = elements[i];
				if (className.equals(element.getClassName()) && "setText".equals(element.getMethodName()))
				{
					if (i + 1 < elements.length)
					{
						element = elements[i + 1];
						if (TextView.class.getName().equals(element.getClassName()) &&
									"getIterableTextForAccessibility".equals(element.getMethodName()))
						{
							// This will cause StackOverflowError
							return;
						}
					}
					break;
				}
			}
		}
		mSetTextDepth++;
		try
		{
			super.setText(text, type);
		}
		finally
		{
			mSetTextDepth--;
		}
	}
	
	private class SelectorRunnable implements Runnable
	{
		public int start = -1, end = -1;
		
		@Override
		public void run()
		{
			if (!startSelectionActionMode(start, end)) setTextIsSelectable(false);
		}
	};
	
	private SelectorRunnable mSelectorRunnable;
	
	public void startSelection()
	{
		startSelection(-1, -1);
	}
	
	public void startSelection(int start, int end)
	{
		mLastStartSelectionCalled = System.currentTimeMillis();
		setTextIsSelectable(true);
		if (ENABLE_HTC_TEXT_SELECTION_METHOD != null)
		{
			try
			{
				ENABLE_HTC_TEXT_SELECTION_METHOD.invoke(this, false, 0);
			}
			catch (Exception e)
			{
				
			}
		}
		if (!startSelectionActionMode(start, end))
		{
			if (mSelectorRunnable == null) mSelectorRunnable = new SelectorRunnable();
			mSelectorRunnable.start = start;
			mSelectorRunnable.end = end;
			post(mSelectorRunnable);
		}
	}
	
	public boolean isSelectionEnabled()
	{
		return isTextSelectable();
	}
	
	public long getPreferredDoubleTapTimeout()
	{
		return Math.max(ViewConfiguration.getDoubleTapTimeout(), 500);
	}
	
	private Uri createUri(String uriString)
	{
		if (mChanName != null)
		{
			ChanLocator locator = ChanLocator.get(mChanName);
			return locator.validateClickedUriString(uriString, mBoardName, mThreadNumber);
		}
		else return Uri.parse(uriString);
	}
	
	private final Runnable mLinkLongClickRunnable = new Runnable()
	{
		@Override
		public void run()
		{
			if (mSpanToClick instanceof LinkSpan)
			{
				Uri uri = createUri(((LinkSpan) mSpanToClick).getUriString());
				mSpanToClick.setClicked(false);
				mSpanToClick = null;
				invalidate();
				if (uri != null) getLinkListener().onLinkLongClick(CommentTextView.this, mChanName, uri);
			}
		}
	};
	
	@Override
	public boolean onTouchEvent(MotionEvent event)
	{
		if (isTextSelectable()) return super.onTouchEvent(event);
		if (!isEnabled()) return false;
		int action = event.getAction();
		float x = event.getX();
		float y = event.getY();
		int ix = (int) x;
		int iy = (int) y;
		ix -= getTotalPaddingLeft();
		iy -= getTotalPaddingTop();
		ix += getScrollX();
		iy += getScrollY();
		mLastX = x;
		mLastY = y;
		mLastXYSetted = System.currentTimeMillis();
		if (action != MotionEvent.ACTION_DOWN && mSpanToClick != null)
		{
			boolean insideTouchSlop = Math.abs(x - mStartX) <= mTouchSlop && Math.abs(y - mStartY) <= mTouchSlop;
			if (action == MotionEvent.ACTION_MOVE)
			{
				if (!insideTouchSlop) removeCallbacks(mLinkLongClickRunnable);
			}
			else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
			{
				removeCallbacks(mLinkLongClickRunnable);
				if (action == MotionEvent.ACTION_UP)
				{
					boolean accept = insideTouchSlop;
					if (!accept)
					{
						// Check is finger still under initial span
						Layout layout = getLayout();
						if (layout != null && getText() instanceof Spanned)
						{
							Spanned spanned = (Spanned) getText();
							ArrayList<SpanHolder<Object>> spans = findSpansToClick(layout, spanned,
									Object.class, ix, iy);
							for (SpanHolder<Object> holder : spans)
							{
								if (holder.span == mSpanToClick)
								{
									accept = true;
									break;
								}
							}
						}
					}
					if (accept)
					{
						if (mSpanToClick instanceof LinkSpan)
						{
							Uri uri = createUri(((LinkSpan) mSpanToClick).getUriString());
							if (uri != null) getLinkListener().onLinkClick(this, mChanName, uri, false);
						}
						else if (mSpanToClick instanceof SpoilerSpan)
						{
							SpoilerSpan spoilerSpan = ((SpoilerSpan) mSpanToClick);
							if (spoilerSpan.isVisible()) spoilerSpan.setVisible(false);
							else spoilerSpan.setVisible(true);
							post(mSyncRunnable);
						}
					}
				}
				mSpanToClick.setClicked(false);
				mSpanToClick = null;
				invalidate();
			}
			return true;
		}
		if (action == MotionEvent.ACTION_DOWN && getText() instanceof Spanned)
		{
			Layout layout = getLayout();
			if (layout != null)
			{
				Spanned spanned = (Spanned) getText();
				if (mSpanToClick != null)
				{
					mSpanToClick.setClicked(false);
					mSpanToClick = null;
					invalidate();
				}
				// 1st priority: show spoiler
				ArrayList<SpanHolder<SpoilerSpan>> spoilerSpans = null;
				if (mSpoilersEnabled)
				{
					spoilerSpans = findSpansToClick(layout, spanned, SpoilerSpan.class, ix, iy);
					for (SpanHolder<SpoilerSpan> spanHolder : spoilerSpans)
					{
						if (!spanHolder.span.isVisible())
						{
							setSpanToClick(spanHolder);
							invalidate();
							return true;
						}
					}
				}
				// 2nd priority: open link
				ArrayList<SpanHolder<LinkSpan>> linkSpans = findSpansToClick(layout, spanned, LinkSpan.class, ix, iy);
				if (linkSpans.size() > 0)
				{
					SpanHolder<LinkSpan> spanHolder = linkSpans.get(0);
					setSpanToClick(spanHolder);
					invalidate();
					postDelayed(mLinkLongClickRunnable, ViewConfiguration.getLongPressTimeout());
					return true;
				}
				// 3rd priority: hide spoiler
				if (mSpoilersEnabled)
				{
					for (SpanHolder<SpoilerSpan> spanHolder : spoilerSpans)
					{
						if (spanHolder.span.isVisible())
						{
							setSpanToClick(spanHolder);
							invalidate();
							return true;
						}
					}
				}
			}
		}
		return false;
	}
	
	private <T extends ClickableSpan> void setSpanToClick(SpanHolder<T> spanHolder)
	{
		mSpanToClick = spanHolder.span;
		mSpanToClick.setClicked(true);
		mStartX = spanHolder.startX;
		mStartY = spanHolder.startY;
	}
	
	private static class SpanHolder<T>
	{
		public final T span;
		public final int startX, startY;
		
		public SpanHolder(T span, int startX, int startY)
		{
			this.span = span;
			this.startX = startX;
			this.startY = startY;
		}
	}
	
	private <T> ArrayList<SpanHolder<T>> findSpansToClick(Layout layout, Spanned spanned, Class<T> type, int x, int y)
	{
		ArrayList<SpanHolder<T>> result = new ArrayList<>();
		// Find spans around touch point for better click treatment
		for (int i = 0; i < mDeltaAttempts.length; i++)
		{
			int startX = x + mDeltaAttempts[i][0], startY = y + mDeltaAttempts[i][1];
			T[] spans = findSpansToClickSingle(layout, spanned, type, startX, startY);
			if (spans != null)
			{
				for (T span : spans)
				{
					if (span != null) result.add(new SpanHolder<>(span, startX, startY));
				}
			}
		}
		return result;
	}
	
	private <T> T[] findSpansToClickSingle(Layout layout, Spanned spanned, Class<T> type, int x, int y)
	{
		int line = layout.getLineForVertical(y);
		int off = layout.getOffsetForHorizontal(line, x);
		T[] spans = spanned.getSpans(off, off, type);
		if (spans != null)
		{
			for (int i = 0; i < spans.length; i++)
			{
				int end = spanned.getSpanEnd(spans[i]);
				if (off >= end) spans[i] = null;
			}
		}
		return spans;
	}
	
	private final Paint mOverlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	
	@Override
	protected void onDraw(Canvas canvas)
	{
		super.onDraw(canvas);
		Layout layout = getLayout();
		if (layout != null)
		{
			CharSequence text = getText();
			if (text instanceof Spanned)
			{
				Spanned spanned = (Spanned) text;
				OverlineSpan[] spans = spanned.getSpans(0, spanned.length(), OverlineSpan.class);
				if (spans != null && spans.length > 0)
				{
					int paddingTop = getExtendedPaddingTop();
					int paddingLeft = getPaddingLeft();
					int shift = (int) (getTextSize() * 8f / 9f);
					float thickness = getTextSize() / 15f - 0.25f;
					int color = getTextColors().getDefaultColor();
					Paint paint = mOverlinePaint;
					paint.setColor(color);
					paint.setStrokeWidth(thickness);
					for (OverlineSpan span : spans)
					{
						int start = spanned.getSpanStart(span);
						int end = spanned.getSpanEnd(span);
						int lineStart = layout.getLineForOffset(start);
						int lineEnd = layout.getLineForOffset(end);
						for (int i = lineStart; i <= lineEnd; i++)
						{
							float left = i == lineStart ? layout.getPrimaryHorizontal(start) : layout.getLineLeft(i);
							float right = i == lineEnd ? layout.getPrimaryHorizontal(end) : layout.getLineRight(i);
							float top = layout.getLineBaseline(i) - shift + 0.5f;
							canvas.drawLine(paddingLeft + left, paddingTop + top, paddingLeft + right,
									paddingTop + top, paint);
						}
					}
				}
			}
		}
	}
	
	private static final Field EDITOR_FIELD;
	private static final Method ENABLE_HTC_TEXT_SELECTION_METHOD;
	
	static
	{
		Field editorField = null;
		try
		{
			editorField = TextView.class.getDeclaredField("mEditor");
			editorField.setAccessible(true);
		}
		catch (Exception e)
		{
			editorField = null;
		}
		EDITOR_FIELD = editorField;
		Method enableHtcTextSelectionMethod = null;
		if (!C.API_LOLLIPOP)
		{
			try
			{
				enableHtcTextSelectionMethod = TextView.class.getDeclaredMethod("enableHtcTextSelection",
						boolean.class, int.class);
			}
			catch (Exception e)
			{
				enableHtcTextSelectionMethod = null;
			}
		}
		ENABLE_HTC_TEXT_SELECTION_METHOD = enableHtcTextSelectionMethod;
	}
	
	public static class ListSelectionKeeper implements Runnable
	{
		public static interface Holder
		{
			public CommentTextView getCommentTextView();
		}
		
		private ListView mListView;
		private int mPostCount;
		
		private String mText;
		private int mSelectionStart;
		private int mSelectionEnd;
		private int mPosition;
		
		public ListSelectionKeeper(ListView listView)
		{
			mListView = listView;
		}
		
		public void onBeforeNotifyDataSetChanged()
		{
			mListView.removeCallbacks(this);
			int position = -1;
			for (int i = 0, count = mListView.getChildCount(); i < count; i++)
			{
				View view = mListView.getChildAt(i);
				Object tag = view.getTag();
				if (tag instanceof Holder)
				{
					Holder holder = (Holder) tag;
					CommentTextView textView = holder.getCommentTextView();
					if (textView.isSelectionEnabled())
					{
						position = mListView.getPositionForView(view);
						mText = textView.getText().toString();
						mSelectionStart = textView.getSelectionStart();
						mSelectionEnd = textView.getSelectionEnd();
						break;
					}
				}
			}
			mPosition = position;
		}
		
		public void onAfterNotifyDataSetChanged()
		{
			mPostCount = 2;
			mListView.removeCallbacks(this);
			mListView.post(this);
		}
		
		public void run()
		{
			if (mPostCount-- > 0)
			{
				mListView.post(this);
				return;
			}
			if (mPosition >= 0)
			{
				int index = mPosition - mListView.getFirstVisiblePosition();
				if (index >= 0 && index < mListView.getChildCount())
				{
					Holder holder = (Holder) mListView.getChildAt(index).getTag();
					if (holder != null)
					{
						CommentTextView textView = holder.getCommentTextView();
						String text = textView.getText().toString();
						if (text.equals(mText))
						{
							textView.startSelection(mSelectionStart, mSelectionEnd);
							mPosition = -1;
						}
					}
				}
			}
		}
	}
}