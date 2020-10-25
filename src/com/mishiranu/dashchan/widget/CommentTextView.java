package com.mishiranu.dashchan.widget;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.text.Layout;
import android.text.Selection;
import android.text.SpanWatcher;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.TypefaceSpan;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import chan.content.Chan;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.text.style.LinkSpan;
import com.mishiranu.dashchan.text.style.OverlineSpan;
import com.mishiranu.dashchan.text.style.SpoilerSpan;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.NavigationUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * TextView with LinkSpan feedback ability without conflict with selection MovementMethod.
 * This class has method to start text selection directly.
 */
public class CommentTextView extends TextView {
	private final int[][] deltaAttempts;
	private final int touchSlop;

	private boolean selectionMode;
	private ClickableSpan spanToClick;
	private float spanStartX, spanStartY;
	private float lastX, lastY;
	private long lastXYSet;
	private int linesLimit;
	private int linesLimitAdditionalHeight;
	private View selectionPaddingView;
	private boolean useAdditionalPadding;

	private ActionMode currentActionMode;
	private Menu currentActionModeMenu;

	private LimitListener limitListener;
	private SpanStateListener spanStateListener;
	private PrepareToCopyListener prepareToCopyListener;
	private LinkListener linkListener;
	private List<ExtraButton> extraButtons;
	private boolean spoilersEnabled;

	private String chanName;
	private String boardName;
	private String threadNumber;

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
		public void onLinkClick(CommentTextView view, Uri uri, Extra extra, boolean confirmed) {
			NavigationUtils.handleUri(view.getContext(), extra.chanName, uri, NavigationUtils.BrowserType.AUTO);
		}

		@Override
		public void onLinkLongClick(CommentTextView view, Uri uri, Extra extra) {}
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
		super.setCustomSelectionActionModeCallback(new CustomSelectionCallback());
		super.setTextIsSelectable(true);
	}

	/* init */ {
		ThemeEngine.applyStyle(this);
	}

	public interface ClickableSpan {
		void setClicked(boolean clicked);
	}

	public interface LimitListener {
		void onApplyLimit(boolean limited);
	}

	public interface SpanStateListener {
		void onSpanStateChanged(CommentTextView view);
	}

	public interface PrepareToCopyListener {
		String onPrepareToCopy(CommentTextView view, Spannable text, int start, int end);
	}

	public interface LinkListener {
		final class Extra {
			public static final Extra EMPTY = new Extra(null, false);

			public final String chanName;
			public final boolean inBoardLink;

			public Extra(String chanName, boolean inBoardLink) {
				this.chanName = chanName;
				this.inBoardLink = inBoardLink;
			}
		}

		void onLinkClick(CommentTextView view, Uri uri, Extra extra, boolean confirmed);
		void onLinkLongClick(CommentTextView view, Uri uri, Extra extra);
	}

	public static class ExtraButton {
		public static class Text {
			public final CharSequence text;
			public final int start;
			public final int end;

			public Text(CharSequence text, int start, int end) {
				this.text = text;
				this.start = start;
				this.end = end;
			}

			public String toPreparedString(CommentTextView view) {
				if (text instanceof Spannable) {
					return view.getPartialCommentString((Spannable) text, start, end);
				} else {
					return toString();
				}
			}

			@NonNull
			@Override
			public String toString() {
				return end > start ? text.toString().substring(start, end) : "";
			}
		}

		public interface Callback {
			boolean handle(CommentTextView view, Text text, boolean click);
		}

		private final String title;
		private final int iconAttr;
		private final Callback callback;

		public ExtraButton(String title, int iconAttr, Callback callback) {
			this.title = title;
			this.iconAttr = iconAttr;
			this.callback = callback;
		}
	}

	public void setLimitListener(LimitListener listener) {
		limitListener = listener;
	}

	public void setSpanStateListener(SpanStateListener listener) {
		spanStateListener = listener;
	}

	public void setPrepareToCopyListener(PrepareToCopyListener listener) {
		prepareToCopyListener = listener;
	}

	public void setLinkListener(LinkListener listener, String chanName, String boardName, String threadNumber) {
		linkListener = listener;
		this.chanName = chanName;
		this.boardName = boardName;
		this.threadNumber = threadNumber;
	}

	public void setExtraButtons(List<ExtraButton> extraButtons) {
		this.extraButtons = extraButtons;
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

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);

		boolean limited = false;
		if (linesLimit > 0) {
			Layout layout = getLayout();
			int count = layout.getLineCount();
			if (count > linesLimit) {
				int removeHeight = layout.getLineTop(count) - layout.getLineTop(linesLimit);
				if (removeHeight > linesLimitAdditionalHeight) {
					if (!selectionMode) {
						setMeasuredDimension(getMeasuredWidth(), getMeasuredHeight() - removeHeight);
					}
					limited = true;
				}
			}
		}
		if (limitListener != null) {
			limitListener.onApplyLimit(limited);
		}
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		super.onLayout(changed, left, top, right, bottom);

		// Cause mEditor.prepareCursorControllers() call to enabled selection controllers
		// mSelectionControllerEnabled can be set to false before view laid out
		setCursorVisible(false);
		setCursorVisible(true);
	}

	public void setSpoilersEnabled(boolean enabled) {
		// Must invalidate text after changing this field
		spoilersEnabled = enabled;
	}

	private void setSelectionMode(boolean selectionMode) {
		if (this.selectionMode != selectionMode) {
			this.selectionMode = selectionMode;
			updateUseAdditionalPadding(false);
			requestLayout();
			if (!selectionMode && isFocused()) {
				View rootView = ListViewUtils.getRootViewInList(this);
				if (rootView != null) {
					View listView = (View) rootView.getParent();
					if (listView != null) {
						// Move focus from this view to list
						listView.requestFocus();
					}
				}
			}
			requestLayout();
		}
	}

	private Runnable restoreSelectionRunnable;

	private final Runnable resetSelectionRunnable = () -> {
		if (selectionMode && currentActionMode == null) {
			CharSequence text = getText();
			if (text instanceof Spannable) {
				Selection.removeSelection((Spannable) text);
			}
			restoreSelectionRunnable = null;
			setSelectionMode(false);
		}
	};

	private static final Pattern LIST_PATTERN = Pattern.compile("^(?:(?:\\d+[.)]|[\u2022-]) |>(?!>) ?)");

	private void sendFakeMotionEvent(int action, int x, int y) {
		MotionEvent motionEvent = MotionEvent.obtain(0, SystemClock.uptimeMillis(), action, x, y, 0);
		onTouchEvent(motionEvent);
		motionEvent.recycle();
	}

	private void startSelection(int x, int y, int start, int end) {
		CharSequence text = getText();
		if (!(text instanceof Spannable)) {
			return;
		}
		Spannable spannable = (Spannable) text;
		int length = spannable.length();
		Layout layout = getLayout();
		if (x != Integer.MAX_VALUE && y != Integer.MAX_VALUE &&
				(start < 0 || end < 0 || end > length || start >= end)) {
			start = 0;
			end = spannable.length();
			int lx = x - getTotalPaddingLeft();
			int ly = y - getTotalPaddingTop();
			if (lx >= 0 && ly >= 0 && lx < getWidth() - getTotalPaddingRight() &&
					ly < getHeight() - getTotalPaddingBottom()) {
				int offset = layout.getOffsetForHorizontal(layout.getLineForVertical(ly), lx);
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
		}
		if (end <= start || start < 0) {
			start = 0;
			end = spannable.length();
		}
		x = getTotalPaddingLeft();
		y = getTotalPaddingRight();
		setSelectionMode(true);
		int finalX = x;
		int finalY = y;
		int finalStart = start;
		int finalEnd = end;
		post(() -> {
			removeCallbacks(resetSelectionRunnable);
			CharSequence newText = getText();
			if (!(newText instanceof Spannable)) {
				resetSelectionRunnable.run();
				return;
			}
			Spannable newSpannable = (Spannable) text;
			int max = newSpannable.length();
			Runnable restoreSelectionRunnable = () ->
					Selection.setSelection(newSpannable, Math.min(finalStart, max), Math.min(finalEnd, max));
			// restoreSelectionRunnable can be nullified during sending motion event
			this.restoreSelectionRunnable = restoreSelectionRunnable;
			sendFakeMotionEvent(MotionEvent.ACTION_DOWN, finalX, finalY);
			sendFakeMotionEvent(MotionEvent.ACTION_UP, finalX, finalY);
			sendFakeMotionEvent(MotionEvent.ACTION_DOWN, finalX, finalY);
			sendFakeMotionEvent(MotionEvent.ACTION_UP, finalX, finalY);
			restoreSelectionRunnable.run();
			postDelayed(resetSelectionRunnable, 500);
		});
	}

	public void startSelection() {
		int x = Integer.MAX_VALUE;
		int y = Integer.MAX_VALUE;
		if (SystemClock.elapsedRealtime() - lastXYSet <= getPreferredDoubleTapTimeout()) {
			x = (int) lastX;
			y = (int) lastY;
		}
		startSelection(x, y, -1, -1);
	}

	public void startSelection(int start, int end) {
		startSelection(Integer.MAX_VALUE, Integer.MAX_VALUE, start, end);
	}

	public boolean isSelectionMode() {
		return selectionMode;
	}

	@Override
	public void setCustomSelectionActionModeCallback(ActionMode.Callback actionModeCallback) {}

	private String getPartialCommentString(Spannable text, int start, int end) {
		return prepareToCopyListener != null ? prepareToCopyListener
				.onPrepareToCopy(CommentTextView.this, text, start, end) : text.subSequence(start, end).toString();
	}

	private static final int[] EXTRA_BUTTON_IDS = {android.R.id.button1, android.R.id.button2, android.R.id.button3};

	private class CustomSelectionCallback implements ActionMode.Callback {
		@TargetApi(Build.VERSION_CODES.M)
		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			currentActionMode = mode;
			currentActionModeMenu = menu;
			setSelectionMode(true);
			int order = 0;
			if (C.API_MARSHMALLOW && mode.getType() == ActionMode.TYPE_FLOATING) {
				order = 1; // Only "cut" menu item uses this order which doesn't present in non-editable TextView
			}
			for (int i = 0; i < EXTRA_BUTTON_IDS.length; i++) {
				ExtraButton extraButton = getExtraButton(i);
				if (extraButton != null) {
					menu.add(0, EXTRA_BUTTON_IDS[i], order, extraButton.title)
							.setIcon(ResourceUtils.getDrawable(getContext(), extraButton.iconAttr, 0))
							.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
				}
			}
			// Stop selection fixation after creating action mode
			restoreSelectionRunnable = null;
			return true;
		}

		private ExtraButton getExtraButton(int index) {
			return extraButtons != null && index < extraButtons.size() && index < EXTRA_BUTTON_IDS.length
					? extraButtons.get(index) : null;
		}

		private ExtraButton.Text getText() {
			int start = getSelectionStart();
			int end = getSelectionEnd();
			int min = Math.max(0, Math.min(start, end));
			int max = Math.max(0, Math.max(start, end));
			return new ExtraButton.Text(CommentTextView.this.getText(), min, max);
		}

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			ExtraButton.Text text = getText();
			for (int i = 0; i < EXTRA_BUTTON_IDS.length; i++) {
				ExtraButton extraButton = getExtraButton(i);
				if (extraButton != null) {
					menu.findItem(EXTRA_BUTTON_IDS[i]).setVisible(extraButton.callback
							.handle(CommentTextView.this, text, false));
				}
			}
			return true;
		}

		@Override
		public void onDestroyActionMode(ActionMode mode) {
			if (currentActionMode == mode) {
				currentActionMode = null;
				currentActionModeMenu = null;
				setSelectionMode(false);
			}
		}

		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			ExtraButton.Text text = getText();
			switch (item.getItemId()) {
				case android.R.id.copy: {
					if (text.text instanceof Spannable) {
						StringUtils.copyToClipboard(getContext(),
								getPartialCommentString((Spannable) text.text, text.start, text.end));
					}
					mode.finish();
					return true;
				}
			}
			for (int i = 0; i < EXTRA_BUTTON_IDS.length; i++) {
				if (EXTRA_BUTTON_IDS[i] == item.getItemId()) {
					ExtraButton extraButton = getExtraButton(i);
					if (extraButton != null) {
						extraButton.callback.handle(CommentTextView.this, text, true);
						mode.finish();
					}
					return true;
				}
			}
			return false;
		}
	}

	@Override
	protected void onSelectionChanged(int selStart, int selEnd) {
		super.onSelectionChanged(selStart, selEnd);
		if (selectionMode && restoreSelectionRunnable != null) {
			// Fixate selection while during selection mode initialization
			restoreSelectionRunnable.run();
		}
		if (currentActionMode != null) {
			// This works better than simple "invalidate" call
			// because "invalidate" can cause action mode resizing bug in Android 5
			getCustomSelectionActionModeCallback().onPrepareActionMode(currentActionMode, currentActionModeMenu);
		}
	}

	public void setLinesLimit(int limit, int additionalHeight) {
		this.linesLimit = limit;
		this.linesLimitAdditionalHeight = additionalHeight;
		requestLayout();
	}

	@Override
	public void setMaxLines(int maxLines) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setMaxHeight(int maxHeight) {
		throw new UnsupportedOperationException();
	}

	public void bindSelectionPaddingView(View selectionPaddingView) {
		if (this.selectionPaddingView != null) {
			this.selectionPaddingView.setVisibility(View.GONE);
		}
		boolean force = this.selectionPaddingView != selectionPaddingView;
		this.selectionPaddingView = selectionPaddingView;
		updateUseAdditionalPadding(force);
	}

	public int getSelectionPadding() {
		return selectionPaddingView != null ? Math.max(selectionPaddingView.getLayoutParams().height, 0) : 0;
	}

	private void updateUseAdditionalPadding(boolean force) {
		boolean useAdditionalPadding = selectionPaddingView != null && selectionMode;
		if (this.useAdditionalPadding != useAdditionalPadding || force) {
			if (selectionPaddingView != null) {
				selectionPaddingView.setVisibility(useAdditionalPadding ? View.VISIBLE : View.GONE);
			}
			this.useAdditionalPadding = useAdditionalPadding;
		}
	}

	@Override
	public void setTextIsSelectable(boolean selectable) {
		// Unsupported operation
	}

	@Override
	public void scrollTo(int x, int y) {
		// Ignore scrolling
	}

	@Override
	public void scrollBy(int x, int y) {
		// Ignore scrolling
	}

	@Override
	public boolean hasExplicitFocusable() {
		return super.hasExplicitFocusable() && selectionMode;
	}

	@Override
	public boolean hasFocusable() {
		return super.hasFocusable() && selectionMode;
	}

	public long getPreferredDoubleTapTimeout() {
		return Math.max(ViewConfiguration.getDoubleTapTimeout(), 500);
	}

	private Uri createUri(String uriString) {
		if (chanName != null) {
			Chan chan = Chan.get(chanName);
			return chan.locator.validateClickedUriString(uriString, boardName, threadNumber);
		} else {
			return Uri.parse(uriString);
		}
	}

	private final Point layoutPosition = new Point();

	private Point fillLayoutPosition(float x, float y) {
		int ix = (int) x;
		int iy = (int) y;
		ix -= getTotalPaddingLeft();
		iy -= getTotalPaddingTop();
		ix += getScrollX();
		iy += getScrollY();
		layoutPosition.set(ix, iy);
		return layoutPosition;
	}

	private boolean checkAcceptSpan(float x, float y, Point layoutPosition) {
		boolean insideTouchSlop = Math.abs(x - spanStartX) <= touchSlop && Math.abs(y - spanStartY) <= touchSlop;
		if (insideTouchSlop) {
			return true;
		}
		if (layoutPosition == null) {
			layoutPosition = fillLayoutPosition(x, y);
		}
		Layout layout = getLayout();
		if (layout != null && getText() instanceof Spanned) {
			Spanned spanned = (Spanned) getText();
			ArrayList<Object> spans = findSpansToClick(layout, spanned, Object.class, layoutPosition);
			for (Object span : spans) {
				if (span == spanToClick) {
					return true;
				}
			}
		}
		return false;
	}

	private final Runnable linkLongClickRunnable = () -> {
		if (spanToClick instanceof LinkSpan) {
			LinkSpan linkSpan = (LinkSpan) spanToClick;
			Uri uri = createUri(linkSpan.uriString);
			setSpanToClick(null, lastX, lastY);
			if (uri != null) {
				LinkListener.Extra extra = new LinkListener.Extra(chanName, linkSpan.inBoardLink());
				getLinkListener().onLinkLongClick(CommentTextView.this, uri, extra);
			}
		}
	};

	private void handleSpanClick() {
		if (spanToClick instanceof LinkSpan) {
			LinkSpan linkSpan = (LinkSpan) spanToClick;
			Uri uri = createUri(linkSpan.uriString);
			if (uri != null) {
				LinkListener.Extra extra = new LinkListener.Extra(chanName, linkSpan.inBoardLink());
				getLinkListener().onLinkClick(this, uri, extra, false);
			}
		} else if (spanToClick instanceof SpoilerSpan) {
			SpoilerSpan spoilerSpan = ((SpoilerSpan) spanToClick);
			spoilerSpan.setVisible(!spoilerSpan.isVisible());
			SpanStateListener listener = spanStateListener;
			if (listener != null) {
				post(() -> listener.onSpanStateChanged(CommentTextView.this));
			}
		}
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (!isEnabled()) {
			return false;
		}
		if (selectionMode) {
			return super.onTouchEvent(event);
		}
		int action = event.getAction();
		float x = event.getX();
		float y = event.getY();
		Point layoutPosition = fillLayoutPosition(x, y);
		lastX = x;
		lastY = y;
		lastXYSet = SystemClock.elapsedRealtime();
		if (action != MotionEvent.ACTION_DOWN && spanToClick != null) {
			if (action == MotionEvent.ACTION_MOVE) {
				if (!checkAcceptSpan(x, y, layoutPosition)) {
					removeCallbacks(linkLongClickRunnable);
					setSpanToClick(null, x, y);
				}
			}
			if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
				removeCallbacks(linkLongClickRunnable);
				if (action == MotionEvent.ACTION_UP) {
					handleSpanClick();
				}
				setSpanToClick(null, x, y);
			}
			return true;
		}
		if (action == MotionEvent.ACTION_DOWN && getText() instanceof Spanned) {
			Layout layout = getLayout();
			if (layout != null) {
				Spanned spanned = (Spanned) getText();
				if (spanToClick != null) {
					setSpanToClick(null, x, y);
				}
				// 1st priority: show spoiler
				ArrayList<SpoilerSpan> spoilerSpans = null;
				if (spoilersEnabled) {
					spoilerSpans = findSpansToClick(layout, spanned, SpoilerSpan.class, layoutPosition);
					for (SpoilerSpan span : spoilerSpans) {
						if (!span.isVisible()) {
							setSpanToClick(span, x, y);
							return true;
						}
					}
				}
				// 2nd priority: open link
				ArrayList<LinkSpan> linkSpans = findSpansToClick(layout, spanned, LinkSpan.class, layoutPosition);
				if (!linkSpans.isEmpty()) {
					setSpanToClick(linkSpans.get(0), x, y);
					postDelayed(linkLongClickRunnable, ViewConfiguration.getLongPressTimeout());
					return true;
				}
				// 3rd priority: hide spoiler
				if (spoilersEnabled) {
					for (SpoilerSpan span : spoilerSpans) {
						if (span.isVisible()) {
							setSpanToClick(span, x, y);
							invalidateSpanToClick();
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	private void setSpanToClick(ClickableSpan span, float x, float y) {
		if (spanToClick != null) {
			spanToClick.setClicked(false);
			invalidateSpanToClick();
		}
		spanToClick = span;
		if (spanToClick != null) {
			spanToClick.setClicked(true);
			invalidateSpanToClick();
		}
		spanStartX = x;
		spanStartY = y;
	}

	private <T> ArrayList<T> findSpansToClick(Layout layout, Spanned spanned, Class<T> type, Point layoutPosition) {
		ArrayList<T> result = new ArrayList<>();
		// Find spans around touch point for better click treatment
		for (int[] deltaAttempt : deltaAttempts) {
			int startX = layoutPosition.x + deltaAttempt[0], startY = layoutPosition.y + deltaAttempt[1];
			T[] spans = findSpansToClickSingle(layout, spanned, type, startX, startY);
			if (spans != null) {
				for (T span : spans) {
					if (span != null) {
						result.add(span);
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

	private static SpanWatcher getSpanWatcher(Spannable spannable) {
		SpanWatcher[] watchers = spannable.getSpans(0, spannable.length(), SpanWatcher.class);
		if (watchers != null && watchers.length > 0) {
			for (SpanWatcher watcher : watchers) {
				if (watcher.getClass().getName().equals("android.widget.TextView$ChangeWatcher")) {
					return watcher;
				}
			}
		}
		return null;
	}

	private void invalidateSpanToClick() {
		if (spanToClick == null) {
			return;
		}
		CharSequence text = getText();
		if (text instanceof Spannable) {
			Spannable spannable = (Spannable) text;
			int start = spannable.getSpanStart(spanToClick);
			int end = spannable.getSpanEnd(spanToClick);
			if (start >= 0 && end >= start) {
				SpanWatcher watcher = getSpanWatcher(spannable);
				if (watcher != null) {
					// Notify span changed to redraw it
					watcher.onSpanChanged(spannable, spanToClick, start, end, start, end);
				}
			}
		}
		invalidate();
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		OverlineSpan.draw(this, canvas);
	}

	public void invalidateAllSpans() {
		CharSequence text = getText();
		if (text instanceof Spannable) {
			Spannable spannable = (Spannable) text;
			ClickableSpan[] spans = spannable.getSpans(0, spannable.length(), ClickableSpan.class);
			if (spans != null && spans.length > 0) {
				SpanWatcher watcher = getSpanWatcher(spannable);
				for (ClickableSpan span : spans) {
					int start = spannable.getSpanStart(span);
					int end = spannable.getSpanEnd(span);
					watcher.onSpanChanged(spannable, span, start, end, start, end);
				}
			}
		}
	}

	public static class RecyclerKeeper extends RecyclerView.AdapterDataObserver implements Runnable {
		public interface Holder {
			CommentTextView getCommentTextView();
		}

		private final RecyclerView recyclerView;
		private int postCount;

		private String text;
		private int selectionStart;
		private int selectionEnd;
		private int position;

		public RecyclerKeeper(RecyclerView recyclerView) {
			this.recyclerView = recyclerView;
		}

		@Override
		public void onChanged() {
			for (int i = 0, count = recyclerView.getChildCount(); i < count; i++) {
				View view = recyclerView.getChildAt(i);
				Holder holder = ListViewUtils.getViewHolder(view, Holder.class);
				if (holder != null) {
					CommentTextView textView = holder.getCommentTextView();
					if (textView.isSelectionMode()) {
						int position = recyclerView.getChildLayoutPosition(view);
						if (position >= 0) {
							this.position = position;
							text = textView.getText().toString();
							selectionStart = textView.getSelectionStart();
							selectionEnd = textView.getSelectionEnd();
							postCount = 2;
							recyclerView.removeCallbacks(this);
							recyclerView.post(this);
						}
						break;
					}
				}
			}
		}

		public void run() {
			if (postCount-- > 0) {
				recyclerView.post(this);
				return;
			}
			int childCount = recyclerView.getChildCount();
			if (position >= 0 && childCount > 0) {
				int index = position - recyclerView.getChildLayoutPosition(recyclerView.getChildAt(0));
				if (index >= 0 && index < childCount) {
					View view = recyclerView.getChildAt(index);
					Holder holder = ListViewUtils.getViewHolder(view, Holder.class);
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
