package com.mishiranu.dashchan.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Canvas;
import android.graphics.Point;
import android.net.Uri;
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
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;
import chan.content.Chan;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.text.style.LinkSpan;
import com.mishiranu.dashchan.text.style.OverlineSpan;
import com.mishiranu.dashchan.text.style.SpoilerSpan;
import com.mishiranu.dashchan.util.AndroidUtils;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.NavigationUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * TextView with LinkSpan feedback ability without conflict with selection MovementMethod.
 * This class has method to start text selection directly.
 */
public class CommentTextView extends TextView {
	private final int[][] deltaAttempts;
	private final int touchSlop;

	private SelectionMode selectionMode;
	private ClickableSpan spanToClick;
	private float spanStartX, spanStartY;
	private float lastX, lastY;
	private long lastXYSet;
	private int linesLimit;
	private int linesLimitAdditionalHeight;
	private View selectionPaddingView;
	private boolean useAdditionalPadding;

	private LimitListener limitListener;
	private SpanStateListener spanStateListener;
	private PrepareToCopyListener prepareToCopyListener;
	private LinkListener linkListener;
	private LinkConfiguration linkConfiguration;
	private List<ExtraButton> extraButtons;
	private boolean spoilersEnabled;

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
		super(C.API_LOLLIPOP && AndroidUtils.IS_MIUI ? new MiuiContext(context) : context, attrs, defStyleAttr);
		ThemeEngine.applyStyle(this);
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
		MiuiContext miuiContext = getMiuiContext();
		if (miuiContext != null) {
			miuiContext.setTextView(this);
			super.setCustomSelectionActionModeCallback(miuiContext);
		} else {
			super.setCustomSelectionActionModeCallback(new CustomSelectionCallback(this));
		}
		super.setTextIsSelectable(true);
	}

	private interface SelectionMode {
		SelectionMode INITIAL = new SelectionMode() {
			@Override
			public boolean isActive() {
				return false;
			}

			@Override
			public void invalidateMenu() {}
		};

		boolean isActive();
		void invalidateMenu();
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

	public interface LinkConfiguration {
		String getChanName();
		String getBoardName();
		String getThreadNumber();
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

	public void setLinkListener(LinkListener listener, LinkConfiguration configuration) {
		linkListener = listener;
		linkConfiguration = configuration;
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

	private Spanned getSpannedText() {
		CharSequence text = getText();
		return text instanceof Spanned ? (Spanned) text : null;
	}

	private Spannable getSpannableText() {
		CharSequence text = getText();
		return text instanceof Spannable ? (Spannable) text : null;
	}

	private MiuiContext getMiuiContext() {
		Context context = getContext();
		return context instanceof MiuiContext ? (MiuiContext) context : null;
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
					if (!isSelectionMode()) {
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

	@Override
	public void setCustomSelectionActionModeCallback(ActionMode.Callback actionModeCallback) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setTextIsSelectable(boolean selectable) {
		throw new UnsupportedOperationException();
	}

	public boolean isSelectionMode() {
		return selectionMode != null;
	}

	private void setSelectionMode(SelectionMode selectionMode) {
		boolean oldSelection = isSelectionMode();
		this.selectionMode = selectionMode;
		boolean newSelection = isSelectionMode();
		if (!newSelection || selectionMode.isActive()) {
			// Stop selection fixing when selection is disabled or becomes active
			restoreSelectionRunnable = null;
		}
		if (oldSelection != newSelection) {
			updateUseAdditionalPadding(false);
			requestLayout();
			if (!newSelection && isFocused()) {
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

	private void removeSelection() {
		Spannable text = getSpannableText();
		if (text != null) {
			Selection.removeSelection(text);
		}
	}

	private Runnable restoreSelectionRunnable;

	private final Runnable resetSelectionRunnable = () -> {
		if (isSelectionMode() && !selectionMode.isActive()) {
			restoreSelectionRunnable = null;
			removeSelection();
			setSelectionMode(null);
		}
	};

	private static final Pattern LIST_PATTERN = Pattern.compile("^(?:(?:\\d+[.)]|[\u2022-]) |>(?!>) ?)");

	private void sendFakeMotionEvent(int action, int x, int y) {
		MotionEvent motionEvent = MotionEvent.obtain(0, SystemClock.uptimeMillis(), action, x, y, 0);
		onTouchEvent(motionEvent);
		motionEvent.recycle();
	}

	private void startSelection(int x, int y, int start, int end) {
		Spannable text = getSpannableText();
		if (text == null) {
			return;
		}
		int length = text.length();
		Layout layout = getLayout();
		if (x != Integer.MAX_VALUE && y != Integer.MAX_VALUE &&
				(start < 0 || end < 0 || end > length || start >= end)) {
			start = 0;
			end = text.length();
			int lx = x - getTotalPaddingLeft();
			int ly = y - getTotalPaddingTop();
			if (lx >= 0 && ly >= 0 && lx < getWidth() - getTotalPaddingRight() &&
					ly < getHeight() - getTotalPaddingBottom()) {
				int offset = layout.getOffsetForHorizontal(layout.getLineForVertical(ly), lx);
				if (offset >= 0 && offset < length) {
					for (int i = offset; i >= 0; i--) {
						if (text.charAt(i) == '\n') {
							start = i + 1;
							break;
						}
					}
					for (int i = offset; i < length; i++) {
						if (text.charAt(i) == '\n') {
							end = i;
							break;
						}
					}
					if (end > start) {
						String part = text.subSequence(start, end).toString();
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
			end = text.length();
		}
		x = getTotalPaddingLeft();
		y = getTotalPaddingRight();
		setSelectionMode(SelectionMode.INITIAL);
		int finalX = x;
		int finalY = y;
		int finalStart = start;
		int finalEnd = end;
		post(() -> {
			removeCallbacks(resetSelectionRunnable);
			Spannable newText = getSpannableText();
			if (newText == null) {
				resetSelectionRunnable.run();
				return;
			}
			int max = newText.length();
			Runnable restoreSelectionRunnable = () -> Selection.setSelection(newText,
					Math.min(finalStart, max), Math.min(finalEnd, max));
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

	private String getPartialCommentString(Spannable text, int start, int end) {
		return prepareToCopyListener != null ? prepareToCopyListener
				.onPrepareToCopy(CommentTextView.this, text, start, end) : text.subSequence(start, end).toString();
	}

	private static class CustomSelectionCallback implements ActionMode.Callback {
		private final WeakReference<CommentTextView> textView;
		private ActionMode currentActionMode;

		public CustomSelectionCallback(CommentTextView textView) {
			this.textView = new WeakReference<>(textView);
		}

		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			SelectionMode selectionMode = new SelectionMode() {
				@Override
				public boolean isActive() {
					return true;
				}

				@Override
				public void invalidateMenu() {
					// Call "onPrepareActionMode" instead of "invalidate"
					// to fix action mode resizing bug in Android 5
					onPrepareActionMode(mode, menu);
				}
			};
			CommentTextView textView = this.textView.get();
			textView.setSelectionMode(selectionMode);
			currentActionMode = mode;
			boolean floating = C.API_MARSHMALLOW && mode.getType() == ActionMode.TYPE_FLOATING;
			// Only "cut" menu item uses this order "1" which doesn't present in non-editable TextView
			textView.onCreateSelectionMenu(menu, floating ? 1 : 0);
			return true;
		}

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			if (currentActionMode == mode) {
				textView.get().onPrepareSelectionMenu(menu);
			}
			return true;
		}

		@Override
		public void onDestroyActionMode(ActionMode mode) {
			if (currentActionMode == mode) {
				currentActionMode = null;
				textView.get().setSelectionMode(null);
			}
		}

		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			boolean result = textView.get().onSelectionItemClicked(item.getItemId());
			if (result) {
				mode.finish();
			}
			return result;
		}
	}

	private static class MiuiContext extends ContextWrapper implements ActionMode.Callback,
			View.OnKeyListener, View.OnAttachStateChangeListener {
		private WeakReference<CommentTextView> textView;
		private WindowManager windowManagerProxy;
		private WeakReference<Menu> actionModeMenu;

		private WeakHashMap<View, Object> addedViews;
		private boolean hasAttachedViews;
		private boolean selectionMode;

		private final CommentTextView.SelectionMode activeSelectionMode = new CommentTextView.SelectionMode() {
			@Override
			public boolean isActive() {
				return true;
			}

			@Override
			public void invalidateMenu() {
				updateMenu(false);
			}
		};

		public MiuiContext(Context base) {
			super(base);
		}

		public void setTextView(CommentTextView textView) {
			if (this.textView != null || textView == null) {
				throw new IllegalStateException();
			}
			this.textView = new WeakReference<>(textView);
			textView.setOnKeyListener(this);
		}

		private static WindowManager createWindowManagerProxy(WindowManager windowManager,
				WeakHashMap<View, Object> addedViews, OnAttachStateChangeListener listener) {
			Class<?>[] instances = {WindowManager.class};
			InvocationHandler handler = (proxy, method, args) -> {
				if (method.getName().equals("addView")) {
					View view = (View) args[0];
					if (!addedViews.containsKey(view)) {
						addedViews.put(view, view);
						view.addOnAttachStateChangeListener(listener);
					}
				}
				return method.invoke(windowManager, args);
			};
			return (WindowManager) Proxy.newProxyInstance(MiuiContext.class.getClassLoader(), instances, handler);
		}

		@Override
		public Object getSystemService(String name) {
			if (WINDOW_SERVICE.equals(name)) {
				// Return tracking WindowManager for Editor inner classes
				String editorClass = "android.widget.Editor";
				for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
					String className = StringUtils.emptyIfNull(element.getClassName());
					if (className.equals(editorClass) || className.startsWith(editorClass) &&
							className.charAt(editorClass.length()) == '$') {
						if (windowManagerProxy == null) {
							addedViews = new WeakHashMap<>();
							WindowManager windowManager = (WindowManager) super.getSystemService(name);
							windowManagerProxy = createWindowManagerProxy(windowManager, addedViews, this);
						}
						return windowManagerProxy;
					}
				}
			}
			return super.getSystemService(name);
		}

		public boolean onTextContextMenuItem(int id) {
			CommentTextView textView = this.textView.get();
			if (textView.onSelectionItemClicked(id)) {
				stopAndRemoveSelection(textView);
				return true;
			}
			return false;
		}

		private static void stopAndRemoveSelection(CommentTextView textView) {
			// onVisibilityChanged causes stopTextActionMode call
			int visibility = textView.getVisibility();
			if (visibility == View.VISIBLE) {
				textView.onVisibilityChanged(textView, View.INVISIBLE);
				textView.onVisibilityChanged(textView, View.VISIBLE);
			}
			textView.removeSelection();
		}

		private void updateMenu(boolean reset) {
			Menu menu = actionModeMenu != null ? actionModeMenu.get() : null;
			if (menu != null) {
				CommentTextView textView = this.textView.get();
				if (reset) {
					menu.clear();
					textView.onCreateSelectionMenu(menu, 0);
				}
				textView.onPrepareSelectionMenu(menu);
			}
		}

		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			// Fake action mode is created only once
			actionModeMenu = new WeakReference<>(menu);
			updateMenu(true);
			return true;
		}

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			// Prepare is never called by MIUI, but this may be changed in the future
			return onCreateActionMode(mode, menu);
		}

		@Override
		public void onDestroyActionMode(ActionMode mode) {}

		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			CommentTextView textView = this.textView.get();
			textView.onSelectionItemClicked(item.getItemId());
			stopAndRemoveSelection(textView);
			return true;
		}

		@Override
		public boolean onKey(View v, int keyCode, KeyEvent event) {
			CommentTextView textView = this.textView.get();
			if (keyCode == KeyEvent.KEYCODE_BACK && v == textView && textView.isSelectionMode()) {
				// MIUI ignores KEYCODE_BACK key events
				if (event.getAction() == KeyEvent.ACTION_UP && !event.isLongPress()) {
					stopAndRemoveSelection(textView);
				}
				return true;
			}
			return false;
		}

		@Override
		public void onViewAttachedToWindow(View v) {
			if (!hasAttachedViews) {
				hasAttachedViews = true;
				CommentTextView textView = this.textView.get();
				textView.setSelectionMode(activeSelectionMode);
				selectionMode = true;
				updateMenu(true);
			}
		}

		@Override
		public void onViewDetachedFromWindow(View v) {
			if (hasAttachedViews) {
				boolean hasAttachedViews = false;
				for (View view : addedViews.keySet()) {
					if (view != v && ViewCompat.isAttachedToWindow(view)) {
						hasAttachedViews = true;
						break;
					}
				}
				if (!hasAttachedViews) {
					this.hasAttachedViews = false;
					if (selectionMode) {
						selectionMode = false;
						CommentTextView textView = this.textView.get();
						textView.setSelectionMode(null);
						textView.removeSelection();
					}
				}
			}
		}
	}

	private static final int[] EXTRA_BUTTON_IDS = {android.R.id.button1, android.R.id.button2, android.R.id.button3};

	private ExtraButton getExtraButton(int index) {
		return extraButtons != null && index < extraButtons.size() && index < EXTRA_BUTTON_IDS.length
				? extraButtons.get(index) : null;
	}

	private ExtraButton.Text getExtraButtonText() {
		int start = getSelectionStart();
		int end = getSelectionEnd();
		int min = Math.max(0, Math.min(start, end));
		int max = Math.max(0, Math.max(start, end));
		return new ExtraButton.Text(getText(), min, max);
	}

	private void onCreateSelectionMenu(Menu menu, int order) {
		for (int i = 0; i < EXTRA_BUTTON_IDS.length; i++) {
			ExtraButton extraButton = getExtraButton(i);
			if (extraButton != null) {
				menu.add(0, EXTRA_BUTTON_IDS[i], order, extraButton.title)
						.setIcon(ResourceUtils.getDrawable(getContext(), extraButton.iconAttr, 0))
						.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
			}
		}
	}

	private void onPrepareSelectionMenu(Menu menu) {
		ExtraButton.Text text = getExtraButtonText();
		for (int i = 0; i < EXTRA_BUTTON_IDS.length; i++) {
			ExtraButton extraButton = getExtraButton(i);
			if (extraButton != null) {
				menu.findItem(EXTRA_BUTTON_IDS[i]).setVisible(extraButton.callback
						.handle(CommentTextView.this, text, false));
			}
		}
	}

	private boolean onSelectionItemClicked(int id) {
		ExtraButton.Text text = getExtraButtonText();
		switch (id) {
			case android.R.id.copy: {
				if (text.text instanceof Spannable) {
					StringUtils.copyToClipboard(getContext(),
							getPartialCommentString((Spannable) text.text, text.start, text.end));
				}
				return true;
			}
		}
		for (int i = 0; i < EXTRA_BUTTON_IDS.length; i++) {
			if (EXTRA_BUTTON_IDS[i] == id) {
				ExtraButton extraButton = getExtraButton(i);
				if (extraButton != null) {
					extraButton.callback.handle(CommentTextView.this, text, true);
				}
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean onTextContextMenuItem(int id) {
		MiuiContext miuiContext = getMiuiContext();
		if (miuiContext != null && miuiContext.onTextContextMenuItem(id)) {
			return true;
		}
		return super.onTextContextMenuItem(id);
	}

	@Override
	protected void onSelectionChanged(int selStart, int selEnd) {
		super.onSelectionChanged(selStart, selEnd);
		if (isSelectionMode()) {
			if (restoreSelectionRunnable != null) {
				// Fix selection during selection mode initialization
				restoreSelectionRunnable.run();
			}
			selectionMode.invalidateMenu();
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
		boolean useAdditionalPadding = selectionPaddingView != null && isSelectionMode();
		if (this.useAdditionalPadding != useAdditionalPadding || force) {
			if (selectionPaddingView != null) {
				selectionPaddingView.setVisibility(useAdditionalPadding ? View.VISIBLE : View.GONE);
			}
			this.useAdditionalPadding = useAdditionalPadding;
		}
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
		return super.hasExplicitFocusable() && isSelectionMode();
	}

	@Override
	public boolean hasFocusable() {
		return super.hasFocusable() && isSelectionMode();
	}

	public long getPreferredDoubleTapTimeout() {
		return Math.max(ViewConfiguration.getDoubleTapTimeout(), 500);
	}

	private Uri createUri(String uriString) {
		LinkConfiguration configuration = linkConfiguration;
		String chanName = configuration != null ? configuration.getChanName() : null;
		if (chanName != null) {
			Chan chan = Chan.get(chanName);
			return chan.locator.validateClickedUriString(uriString,
					configuration.getBoardName(), configuration.getThreadNumber());
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
		Spanned text = getSpannedText();
		if (layout != null && text != null) {
			ArrayList<Object> spans = findSpansToClick(layout, text, Object.class, layoutPosition);
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
				String chanName = linkConfiguration != null ? linkConfiguration.getChanName() : null;
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
				String chanName = linkConfiguration != null ? linkConfiguration.getChanName() : null;
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
		if (isSelectionMode()) {
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
		if (action == MotionEvent.ACTION_DOWN) {
			Layout layout = getLayout();
			Spanned text = getSpannedText();
			if (layout != null && text != null) {
				if (spanToClick != null) {
					setSpanToClick(null, x, y);
				}
				// 1st priority: show spoiler
				ArrayList<SpoilerSpan> spoilerSpans = null;
				if (spoilersEnabled) {
					spoilerSpans = findSpansToClick(layout, text, SpoilerSpan.class, layoutPosition);
					for (SpoilerSpan span : spoilerSpans) {
						if (!span.isVisible()) {
							setSpanToClick(span, x, y);
							return true;
						}
					}
				}
				// 2nd priority: open link
				ArrayList<LinkSpan> linkSpans = findSpansToClick(layout, text, LinkSpan.class, layoutPosition);
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

	private static SpanWatcher getSpanWatcher(Spannable text) {
		SpanWatcher[] watchers = text.getSpans(0, text.length(), SpanWatcher.class);
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
		Spannable text = getSpannableText();
		if (text != null) {
			int start = text.getSpanStart(spanToClick);
			int end = text.getSpanEnd(spanToClick);
			if (start >= 0 && end >= start) {
				SpanWatcher watcher = getSpanWatcher(text);
				if (watcher != null) {
					// Notify span changed to redraw it
					watcher.onSpanChanged(text, spanToClick, start, end, start, end);
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
		Spannable text = getSpannableText();
		if (text != null) {
			ClickableSpan[] spans = text.getSpans(0, text.length(), ClickableSpan.class);
			if (spans != null && spans.length > 0) {
				SpanWatcher watcher = getSpanWatcher(text);
				for (ClickableSpan span : spans) {
					int start = text.getSpanStart(span);
					int end = text.getSpanEnd(span);
					watcher.onSpanChanged(text, span, start, end, start, end);
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
