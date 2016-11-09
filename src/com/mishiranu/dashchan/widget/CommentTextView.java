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
import com.mishiranu.dashchan.ui.posting.Replyable;
import com.mishiranu.dashchan.util.Log;
import com.mishiranu.dashchan.util.NavigationUtils;
import com.mishiranu.dashchan.util.ResourceUtils;

/*
 * TextView with LinkSpan feedback ability without conflict with selection MovementMethod.
 * This class has method to start text selection directly.
 */
public class CommentTextView extends TextView {
	private final int[][] deltaAttempts;
	private final int touchSlop;
	private final int additionalPadding;

	private ClickableSpan spanToClick;
	private float startX, startY;
	private float lastX, lastY;
	private long lastXYSet;
	private long lastStartSelectionCalled;
	private int currentMax = Integer.MAX_VALUE, reservedMax;
	private boolean maxModeLines;
	private boolean appendAdditionalPadding;
	private boolean useAdditionalPadding;

	private CommentListener commentListener;
	private LinkListener linkListener;
	private boolean spoilersEnabled;

	private String chanName;
	private String boardName;
	private String threadNumber;

	private Replyable replyable;
	private String postNumber;

	private static final double[][] BASE_POINTS;
	private static final int RING_RADIUS = 6;
	private static final int RINGS = 3;

	static {
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

	private static final LinkListener DEFAULT_LINK_LISTENER = new LinkListener() {
		@Override
		public void onLinkClick(CommentTextView view, String chanName, Uri uri, boolean confirmed) {
			NavigationUtils.handleUri(view.getContext(), chanName, uri, NavigationUtils.BrowserType.AUTO);
		}

		@Override
		public void onLinkLongClick(CommentTextView view, String chanName, Uri uri) {}
	};

	public CommentTextView(Context context, AttributeSet attrs) {
		this(context, attrs, android.R.attr.textViewStyle);
	}

	public CommentTextView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		float density = ResourceUtils.obtainDensity(this);
		int delta = (int) (RING_RADIUS * density);
		deltaAttempts = new int[1 + RINGS * BASE_POINTS.length][2];
		deltaAttempts[0][0] = 0;
		deltaAttempts[0][1] = 0;
		int add = 1;
		for (int r = 0; r < RINGS; r++) {
			for (int i = 0; i < BASE_POINTS.length; i++) {
				for (int j = 0; j < BASE_POINTS[i].length; j++) {
					deltaAttempts[i + add][j] = (int) ((r + 1) * delta * BASE_POINTS[i][j]);
				}
			}
			add += BASE_POINTS.length;
		}
		touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
		additionalPadding = (int) (64f * density);
		super.setCustomSelectionActionModeCallback(new CustomSelectionCallback());
	}

	public interface ClickableSpan {
		public void setClicked(boolean clicked);
	}

	public interface CommentListener {
		public void onRequestSiblingsInvalidate(CommentTextView view);
		public String onPrepareToCopy(CommentTextView view, Spannable text, int start, int end);
	}

	public interface LinkListener {
		public void onLinkClick(CommentTextView view, String chanName, Uri uri, boolean confirmed);
		public void onLinkLongClick(CommentTextView view, String chanName, Uri uri);
	}

	public void setCommentListener(CommentListener listener) {
		commentListener = listener;
	}

	public void setLinkListener(LinkListener listener, String chanName, String boardName, String threadNumber) {
		linkListener = listener;
		this.chanName = chanName;
		this.boardName = boardName;
		this.threadNumber = threadNumber;
	}

	private LinkListener getLinkListener() {
		return linkListener != null ? linkListener : DEFAULT_LINK_LISTENER;
	}

	public void setSubjectAndComment(CharSequence subject, CharSequence comment) {
		boolean hasSubject = !StringUtils.isEmpty(subject);
		boolean hasComment = !StringUtils.isEmpty(comment);
		if (hasComment && comment instanceof Spanned) {
			SpoilerSpan[] spoilerSpans = ((Spanned) comment).getSpans(0, comment.length(), SpoilerSpan.class);
			if (spoilerSpans != null) {
				boolean enabled = spoilersEnabled;
				for (SpoilerSpan spoilerSpan : spoilerSpans) {
					spoilerSpan.setEnabled(enabled);
				}
			}
		}
		if (hasSubject) {
			SpannableStringBuilder spannable = new SpannableStringBuilder();
			spannable.append(subject);
			int length = spannable.length();
			spannable.setSpan(new TypefaceSpan("sans-serif-light"), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
			spannable.setSpan(new RelativeSizeSpan(4f / 3f), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
			if (hasComment) {
				spannable.append("\n\n");
				spannable.setSpan(new RelativeSizeSpan(0.75f), length, length + 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				spannable.append(comment);
			}
			setText(spannable);
		} else if (hasComment) {
			setText(comment);
		} else {
			setText(null);
		}
	}

	public void setSpoilersEnabled(boolean enabled) {
		// Must invalidate text after changing this field
		spoilersEnabled = enabled;
	}

	private final Runnable syncRunnable = () -> commentListener.onRequestSiblingsInvalidate(CommentTextView.this);

	private static final Pattern LIST_PATTERN = Pattern.compile("^(?:(?:\\d+[\\.\\)]|[\u2022-]) |>(?!>) ?)");

	private void selectNecessaryText(int start, int end) {
		CharSequence text = getText();
		if (!(text instanceof Spannable)) {
			return;
		}
		Spannable spannable = (Spannable) text;
		int length = spannable.length();
		if (lastStartSelectionCalled - lastXYSet <= getPreferredDoubleTapTimeout() &&
				(start < 0 || end < 0 || start > length || end > length || start >= end)) {
			start = 0;
			end = spannable.length();
			int offset = getOffsetForPosition(lastX, lastY);
			if (offset >= 0 && offset < length) {
				for (int i = offset; i >= 0; i--) {
					if (spannable.charAt(i) == '\n') {
						start = i + 1;
						break;
					}
				}
				for (int i = offset; i < length; i++) {
					if (spannable.charAt(i) == '\n') {
						end = i;
						break;
					}
				}
				if (end > start) {
					String part = spannable.subSequence(start, end).toString();
					Matcher matcher = LIST_PATTERN.matcher(part);
					if (matcher.find()) {
						start += matcher.group().length();
					}
				}
			}
		}
		if (end <= start) {
			start = 0;
			end = spannable.length();
		}
		Selection.setSelection(spannable, start, end);
	}

	public void setReplyable(Replyable replyable, String postNumber) {
		this.replyable = replyable;
		this.postNumber = postNumber;
	}

	@Override
	public void setCustomSelectionActionModeCallback(ActionMode.Callback actionModeCallback) {}

	private String getPartialCommentString(Spannable text, int start, int end) {
		return commentListener != null ? commentListener.onPrepareToCopy(CommentTextView.this, text, start, end)
				: text.subSequence(start, end).toString();
	}

	private Uri extractSelectedUri() {
		int start = getSelectionStart();
		int end = getSelectionEnd();
		if (end > start) {
			String text = getText().toString().substring(start, end);
			String fixedText = StringUtils.fixParsedUriString(text);
			if (text.equals(fixedText)) {
				if (!text.matches("[a-z]+:.*")) {
					text = "http://" + text.replaceAll("^/+", "");
				}
				Uri uri = Uri.parse(text);
				if (uri != null) {
					if (StringUtils.isEmpty(uri.getAuthority())) {
						uri = uri.buildUpon().scheme("http").build();
					}
					String host = uri.getHost();
					if (host != null && host.matches(".+\\..+") && ChanLocator.getDefault().isWebScheme(uri)) {
						return uri;
					}
				}
			}
		}
		return null;
	}

	private ActionMode currentActionMode;
	private Menu currentActionModeMenu;

	private class CustomSelectionCallback implements ActionMode.Callback, Runnable {
		private void addCopyMenuItemIfNotNull(Menu menu, MenuItem menuItem, int flags) {
			if (menuItem != null) {
				menu.add(0, menuItem.getItemId(), 0, menuItem.getTitle()).setIcon(menuItem.getIcon())
						.setShowAsAction(flags);
			}
		}

		@TargetApi(Build.VERSION_CODES.M)
		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			currentActionMode = mode;
			currentActionModeMenu = menu;
			int pasteResId = ResourceUtils.getSystemSelectionIcon(getContext(), "actionModePasteDrawable",
					"ic_menu_paste_holo_dark");
			SparseArray<MenuItem> validItems = new SparseArray<>();
			validItems.put(android.R.id.selectAll, menu.findItem(android.R.id.selectAll));
			validItems.put(android.R.id.copy, menu.findItem(android.R.id.copy));
			menu.clear();
			ActionIconSet set = new ActionIconSet(getContext());
			int flags = MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT;
			if (C.API_MARSHMALLOW && mode.getType() == ActionMode.TYPE_FLOATING) {
				if (replyable != null) {
					menu.add(0, android.R.id.button1, 0, R.string.action_quote).setIcon(pasteResId)
							.setShowAsAction(flags);
				}
				menu.add(0, android.R.id.button2, 0, R.string.action_browser).setIcon(set.getId(R.attr.actionForward))
						.setShowAsAction(flags);
				addCopyMenuItemIfNotNull(menu, validItems.get(android.R.id.copy), flags);
				addCopyMenuItemIfNotNull(menu, validItems.get(android.R.id.selectAll), flags);
			} else {
				addCopyMenuItemIfNotNull(menu, validItems.get(android.R.id.selectAll), flags);
				addCopyMenuItemIfNotNull(menu, validItems.get(android.R.id.copy), flags);
				if (replyable != null) {
					menu.add(0, android.R.id.button1, 0, R.string.action_quote).setIcon(pasteResId)
							.setShowAsAction(flags);
				}
				menu.add(0, android.R.id.button2, 0, R.string.action_browser).setIcon(set.getId(R.attr.actionForward))
						.setShowAsAction(flags);
			}
			return true;
		}

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			Uri uri = extractSelectedUri();
			menu.findItem(android.R.id.button2).setVisible(uri != null);
			return true;
		}

		@Override
		public void onDestroyActionMode(ActionMode mode) {
			if (currentActionMode == mode) {
				currentActionMode = null;
				currentActionModeMenu = null;
			}
			post(this);
		}

		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			Spannable text = getText() instanceof Spannable ? (Spannable) getText() : null;
			if (text != null) {
				int selStart = getSelectionStart(), selEnd = getSelectionEnd();
				int min = Math.max(0, Math.min(selStart, selEnd)), max = Math.max(0, Math.max(selStart, selEnd));
				switch (item.getItemId()) {
					case android.R.id.selectAll: {
						Selection.setSelection(text, 0, text.length());
						break;
					}
					case android.R.id.copy: {
						StringUtils.copyToClipboard(getContext(), getPartialCommentString(text, min, max));
						mode.finish();
						break;
					}
					case android.R.id.button1: {
						if (replyable != null) {
							replyable.onRequestReply(new Replyable.ReplyData(postNumber,
									getPartialCommentString(text, min, max)));
						}
						mode.finish();
						break;
					}
					case android.R.id.button2: {
						Uri uri = extractSelectedUri();
						if (uri != null) {
							getLinkListener().onLinkClick(CommentTextView.this, null, uri, true);
						}
						mode.finish();
						break;
					}
				}
			}
			return true;
		}

		@Override
		public void run() {
			setTextIsSelectable(false);
		}
	}

	@Override
	protected void onSelectionChanged(int selStart, int selEnd) {
		super.onSelectionChanged(selStart, selEnd);
		if (currentActionMode != null) {
			// This works better than simple "invalidate" call
			// because "invalidate" can cause action mode resizing bug in Android 5
			getCustomSelectionActionModeCallback().onPrepareActionMode(currentActionMode, currentActionModeMenu);
		}
	}

	@Override
	public void setMaxLines(int maxlines) {
		reservedMax = 0;
		currentMax = maxlines;
		maxModeLines = true;
		super.setMaxLines(maxlines);
	}

	@Override
	public void setMaxHeight(int maxHeight) {
		reservedMax = 0;
		currentMax = maxHeight;
		maxModeLines = false;
		super.setMaxHeight(maxHeight);
	}

	@Override
	public void setPadding(int left, int top, int right, int bottom) {
		if (useAdditionalPadding) {
			bottom += additionalPadding;
		}
		super.setPadding(left, top, right, bottom);
	}

	public void setAppendAdditionalPadding(boolean appendAdditionalPadding) {
		this.appendAdditionalPadding = appendAdditionalPadding;
		updateUseAdditionalPadding();
	}

	public int getAdditionalPadding() {
		return appendAdditionalPadding ? additionalPadding : 0;
	}

	private void updateUseAdditionalPadding() {
		boolean needUseAdditionalPadding = appendAdditionalPadding && isTextSelectable();
		if (needUseAdditionalPadding != useAdditionalPadding) {
			int bottom = getPaddingBottom();
			bottom = needUseAdditionalPadding ? bottom + additionalPadding : bottom - additionalPadding;
			// Set false to normal calculate padding (see setPadding method overriding)
			useAdditionalPadding = false;
			setPadding(getPaddingLeft(), getPaddingTop(), getPaddingRight(), bottom);
			useAdditionalPadding = needUseAdditionalPadding;
		}
	}

	private void updateSelectablePaddings(boolean selectable) {
		if (selectable) {
			reservedMax = currentMax;
			super.setMaxHeight(Integer.MAX_VALUE);
		} else if (reservedMax > 0) {
			// Also will reset reservedMax
			if (maxModeLines) {
				setMaxLines(reservedMax);
			} else {
				setMaxHeight(reservedMax);
			}
		}
	}

	@Override
	public void setTextIsSelectable(boolean selectable) {
		if (selectable == isTextSelectable()) {
			return;
		}
		updateSelectablePaddings(selectable);
		try {
			super.setTextIsSelectable(selectable);
		} catch (ArrayIndexOutOfBoundsException e) {
			// Fix spannable issue on Nougat
			super.setTextIsSelectable(!selectable);
			updateSelectablePaddings(!selectable);
			return;
		}
		updateUseAdditionalPadding();
	}

	private int setTextDepth = 0;

	@Override
	public void setText(CharSequence text, BufferType type) {
		// Some devices may call setText recursively; fix it
		if (setTextDepth > 0) {
			String className = getClass().getName();
			StackTraceElement[] elements = Thread.currentThread().getStackTrace();
			for (int i = 0; i < elements.length; i++) {
				StackTraceElement element = elements[i];
				if (className.equals(element.getClassName()) && "setText".equals(element.getMethodName())) {
					if (i + 1 < elements.length) {
						element = elements[i + 1];
						if (TextView.class.getName().equals(element.getClassName()) &&
									"getIterableTextForAccessibility".equals(element.getMethodName())) {
							// This will cause StackOverflowError
							return;
						}
					}
					break;
				}
			}
		}
		setTextDepth++;
		try {
			super.setText(text, type);
		} finally {
			setTextDepth--;
		}
	}

	private interface EditorCallable {
		public boolean call(Object editor, Class<?> editorClass) throws Exception;
	}

	private final EditorCallable editorPerformLongClick = (editor, editorClass) -> {
		Method method = editorClass.getDeclaredMethod("performLongClick", boolean.class);
		method.setAccessible(true);
		return (boolean) method.invoke(editor, false);
	};

	private final EditorCallable editorStartSelectionActionMode = (editor, editorClass) -> {
		Method method = editorClass.getDeclaredMethod("startSelectionActionMode");
		method.setAccessible(true);
		return (boolean) method.invoke(editor);
	};

	private boolean callEditor(EditorCallable editorCallable) {
		try {
			Object editor;
			Class<?> editorClass;
			if (EDITOR_FIELD != null) {
				editor = EDITOR_FIELD.get(CommentTextView.this);
				editorClass = EDITOR_FIELD.getType();
			} else {
				editor = CommentTextView.this;
				editorClass = TextView.class;
			}
			return editorCallable.call(editor, editorClass);
		} catch (InvocationTargetException e) {
			Log.persistent().write(e.getCause());
			return false;
		} catch (Exception e) {
			Log.persistent().write(e);
			return false;
		}
	}

	public void startSelection() {
		startSelection(-1, -1);
	}

	public void startSelection(int start, int end) {
		lastStartSelectionCalled = System.currentTimeMillis();
		setTextIsSelectable(true);
		if (ENABLE_HTC_TEXT_SELECTION_METHOD != null) {
			try {
				ENABLE_HTC_TEXT_SELECTION_METHOD.invoke(this, false, 0);
			} catch (Exception e) {
				// Ignore exception
			}
		}
		selectNecessaryText(start, end);
		if (C.API_NOUGAT) {
			post(() -> {
				if (callEditor(editorPerformLongClick)) {
					post(() -> {
						if (!callEditor(editorStartSelectionActionMode)) {
							setTextIsSelectable(false);
						}
					});
				} else {
					setTextIsSelectable(false);
				}
			});
		} else {
			if (!callEditor(editorStartSelectionActionMode)) {
				post(() -> {
					selectNecessaryText(start, end);
					if (!callEditor(editorStartSelectionActionMode)) {
						setTextIsSelectable(false);
					}
				});
			}
		}
	}

	public boolean isSelectionEnabled() {
		return isTextSelectable();
	}

	public long getPreferredDoubleTapTimeout() {
		return Math.max(ViewConfiguration.getDoubleTapTimeout(), 500);
	}

	private Uri createUri(String uriString) {
		if (chanName != null) {
			ChanLocator locator = ChanLocator.get(chanName);
			return locator.validateClickedUriString(uriString, boardName, threadNumber);
		} else {
			return Uri.parse(uriString);
		}
	}

	private final Runnable linkLongClickRunnable = () -> {
		if (spanToClick instanceof LinkSpan) {
			Uri uri = createUri(((LinkSpan) spanToClick).getUriString());
			spanToClick.setClicked(false);
			spanToClick = null;
			invalidate();
			if (uri != null) {
				getLinkListener().onLinkLongClick(CommentTextView.this, chanName, uri);
			}
		}
	};

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (isTextSelectable()) {
			return super.onTouchEvent(event);
		}
		if (!isEnabled()) {
			return false;
		}
		int action = event.getAction();
		float x = event.getX();
		float y = event.getY();
		int ix = (int) x;
		int iy = (int) y;
		ix -= getTotalPaddingLeft();
		iy -= getTotalPaddingTop();
		ix += getScrollX();
		iy += getScrollY();
		lastX = x;
		lastY = y;
		lastXYSet = System.currentTimeMillis();
		if (action != MotionEvent.ACTION_DOWN && spanToClick != null) {
			boolean insideTouchSlop = Math.abs(x - startX) <= touchSlop && Math.abs(y - startY) <= touchSlop;
			if (action == MotionEvent.ACTION_MOVE) {
				if (!insideTouchSlop) {
					removeCallbacks(linkLongClickRunnable);
				}
			} else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
				removeCallbacks(linkLongClickRunnable);
				if (action == MotionEvent.ACTION_UP) {
					boolean accept = insideTouchSlop;
					if (!accept) {
						// Check is finger still under initial span
						Layout layout = getLayout();
						if (layout != null && getText() instanceof Spanned) {
							Spanned spanned = (Spanned) getText();
							ArrayList<SpanHolder<Object>> spans = findSpansToClick(layout, spanned,
									Object.class, ix, iy);
							for (SpanHolder<Object> holder : spans) {
								if (holder.span == spanToClick) {
									accept = true;
									break;
								}
							}
						}
					}
					if (accept) {
						if (spanToClick instanceof LinkSpan) {
							Uri uri = createUri(((LinkSpan) spanToClick).getUriString());
							if (uri != null) {
								getLinkListener().onLinkClick(this, chanName, uri, false);
							}
						} else if (spanToClick instanceof SpoilerSpan) {
							SpoilerSpan spoilerSpan = ((SpoilerSpan) spanToClick);
							spoilerSpan.setVisible(!spoilerSpan.isVisible());
							post(syncRunnable);
						}
					}
				}
				spanToClick.setClicked(false);
				spanToClick = null;
				invalidate();
			}
			return true;
		}
		if (action == MotionEvent.ACTION_DOWN && getText() instanceof Spanned) {
			Layout layout = getLayout();
			if (layout != null) {
				Spanned spanned = (Spanned) getText();
				if (spanToClick != null) {
					spanToClick.setClicked(false);
					spanToClick = null;
					invalidate();
				}
				// 1st priority: show spoiler
				ArrayList<SpanHolder<SpoilerSpan>> spoilerSpans = null;
				if (spoilersEnabled) {
					spoilerSpans = findSpansToClick(layout, spanned, SpoilerSpan.class, ix, iy);
					for (SpanHolder<SpoilerSpan> spanHolder : spoilerSpans) {
						if (!spanHolder.span.isVisible()) {
							setSpanToClick(spanHolder);
							invalidate();
							return true;
						}
					}
				}
				// 2nd priority: open link
				ArrayList<SpanHolder<LinkSpan>> linkSpans = findSpansToClick(layout, spanned, LinkSpan.class, ix, iy);
				if (linkSpans.size() > 0) {
					SpanHolder<LinkSpan> spanHolder = linkSpans.get(0);
					setSpanToClick(spanHolder);
					invalidate();
					postDelayed(linkLongClickRunnable, ViewConfiguration.getLongPressTimeout());
					return true;
				}
				// 3rd priority: hide spoiler
				if (spoilersEnabled) {
					for (SpanHolder<SpoilerSpan> spanHolder : spoilerSpans) {
						if (spanHolder.span.isVisible()) {
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

	private <T extends ClickableSpan> void setSpanToClick(SpanHolder<T> spanHolder) {
		spanToClick = spanHolder.span;
		spanToClick.setClicked(true);
		startX = spanHolder.startX;
		startY = spanHolder.startY;
	}

	private static class SpanHolder<T> {
		public final T span;
		public final int startX, startY;

		public SpanHolder(T span, int startX, int startY) {
			this.span = span;
			this.startX = startX;
			this.startY = startY;
		}
	}

	private <T> ArrayList<SpanHolder<T>> findSpansToClick(Layout layout, Spanned spanned, Class<T> type, int x, int y) {
		ArrayList<SpanHolder<T>> result = new ArrayList<>();
		// Find spans around touch point for better click treatment
		for (int[] deltaAttempt : deltaAttempts) {
			int startX = x + deltaAttempt[0], startY = y + deltaAttempt[1];
			T[] spans = findSpansToClickSingle(layout, spanned, type, startX, startY);
			if (spans != null) {
				for (T span : spans) {
					if (span != null) {
						result.add(new SpanHolder<>(span, startX, startY));
					}
				}
			}
		}
		return result;
	}

	private <T> T[] findSpansToClickSingle(Layout layout, Spanned spanned, Class<T> type, int x, int y) {
		int line = layout.getLineForVertical(y);
		int off = layout.getOffsetForHorizontal(line, x);
		T[] spans = spanned.getSpans(off, off, type);
		if (spans != null) {
			for (int i = 0; i < spans.length; i++) {
				int end = spanned.getSpanEnd(spans[i]);
				if (off >= end) {
					spans[i] = null;
				}
			}
		}
		return spans;
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		OverlineSpan.draw(this, canvas);
	}

	private static final Field EDITOR_FIELD;
	private static final Method ENABLE_HTC_TEXT_SELECTION_METHOD;

	static {
		Field editorField;
		try {
			editorField = TextView.class.getDeclaredField("mEditor");
			editorField.setAccessible(true);
		} catch (Exception e) {
			editorField = null;
		}
		EDITOR_FIELD = editorField;
		Method enableHtcTextSelectionMethod = null;
		if (!C.API_LOLLIPOP) {
			try {
				enableHtcTextSelectionMethod = TextView.class.getDeclaredMethod("enableHtcTextSelection",
						boolean.class, int.class);
			} catch (Exception e) {
				enableHtcTextSelectionMethod = null;
			}
		}
		ENABLE_HTC_TEXT_SELECTION_METHOD = enableHtcTextSelectionMethod;
	}

	public static class ListSelectionKeeper implements Runnable {
		public interface Holder {
			public CommentTextView getCommentTextView();
		}

		private final ListView listView;
		private int postCount;

		private String text;
		private int selectionStart;
		private int selectionEnd;
		private int position;

		public ListSelectionKeeper(ListView listView) {
			this.listView = listView;
		}

		public void onBeforeNotifyDataSetChanged() {
			listView.removeCallbacks(this);
			int position = -1;
			for (int i = 0, count = listView.getChildCount(); i < count; i++) {
				View view = listView.getChildAt(i);
				Object tag = view.getTag();
				if (tag instanceof Holder) {
					Holder holder = (Holder) tag;
					CommentTextView textView = holder.getCommentTextView();
					if (textView.isSelectionEnabled()) {
						position = listView.getPositionForView(view);
						text = textView.getText().toString();
						selectionStart = textView.getSelectionStart();
						selectionEnd = textView.getSelectionEnd();
						break;
					}
				}
			}
			this.position = position;
		}

		public void onAfterNotifyDataSetChanged() {
			postCount = 2;
			listView.removeCallbacks(this);
			listView.post(this);
		}

		public void run() {
			if (postCount-- > 0) {
				listView.post(this);
				return;
			}
			if (position >= 0) {
				int index = position - listView.getFirstVisiblePosition();
				if (index >= 0 && index < listView.getChildCount()) {
					Holder holder = (Holder) listView.getChildAt(index).getTag();
					if (holder != null) {
						CommentTextView textView = holder.getCommentTextView();
						String text = textView.getText().toString();
						if (text.equals(this.text)) {
							textView.startSelection(selectionStart, selectionEnd);
							position = -1;
						}
					}
				}
			}
		}
	}
}