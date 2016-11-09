package com.mishiranu.dashchan.ui.posting.text;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;

import com.mishiranu.dashchan.graphics.ColorScheme;
import com.mishiranu.dashchan.text.style.LinkSpan;
import com.mishiranu.dashchan.text.style.QuoteSpan;

public class QuoteEditWatcher implements TextWatcher {
	private final ColorScheme colorScheme;

	public QuoteEditWatcher(Context context) {
		colorScheme = new ColorScheme(context);
	}

	private boolean updateQuotes = false;
	private boolean updateLinks = false;

	private boolean listen = true;

	private boolean isNumber(char c) {
		return c >= '0' && c <= '9';
	}

	private boolean contains(CharSequence s, int start, int end, char what) {
		for (int i = start; i < end; i++) {
			if (s.charAt(i) == what) {
				return true;
			}
		}
		return false;
	}

	private void beforeOrOnTextChanged(CharSequence s, int start, int count) {
		int end = start + count;
		if (start > 0) {
			start--;
		}
		if (end < s.length() - 1) {
			end++;
		}
		int wordStart = start;
		int wordEnd = end - 1;
		for (int i = wordStart - 1; i >= 0; i--) {
			char c = s.charAt(i);
			if (c == ' ' || c == '\n') {
				break;
			}
			wordStart = i;
		}
		for (int i = wordEnd >= 0 ? wordEnd : 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == ' ' || c == '\n') {
				break;
			}
			wordEnd = i;
		}
		wordEnd++;
		if (wordEnd - wordStart >= 2) {
			char p = '\0';
			for (int i = wordStart; i < wordEnd; i++) {
				char c = s.charAt(i);
				if (c == '>' && p == '>') {
					updateLinks = true;
					break;
				}
				p = c;
			}
		}
		if (contains(s, start, end, '>') || contains(s, start, end, '\n')) {
			updateQuotes = true;
		}
	}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count, int after) {
		if (!listen) {
			return;
		}
		beforeOrOnTextChanged(s, start, count);
	}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count) {
		if (!listen) {
			return;
		}
		beforeOrOnTextChanged(s, start, count);
	}

	@Override
	public void afterTextChanged(Editable s) {
		if (!listen) {
			return;
		}
		listen = false;
		String text = null;
		if (updateQuotes) {
			for (QuoteSpan quoteSpan : s.getSpans(0, s.length(), QuoteSpan.class)) {
				s.removeSpan(quoteSpan);
			}
			if (text == null) {
				text = s.toString();
			}
			int index = 0;
			while (true) {
				int start = text.indexOf('>', index);
				if (start == -1) {
					break;
				}
				if (start > 0 && text.charAt(start - 1) != '\n') {
					index++;
				} else {
					int end = text.indexOf('\n', start);
					boolean finish = end == -1;
					if (finish) {
						end = text.length();
					} else {
						index = end;
					}
					// Check link in the start of line instead quote
					if (!(end - start >= 3 && text.charAt(start) == '>' && text.charAt(start + 1) == '>'
							&& isNumber(text.charAt(start + 2)))) {
						QuoteSpan quoteSpan = new QuoteSpan();
						quoteSpan.applyColorScheme(colorScheme);
						s.setSpan(quoteSpan, start, end, Editable.SPAN_EXCLUSIVE_INCLUSIVE);
					}
					if (finish) {
						break;
					}
				}
			}
		}
		if (updateLinks) {
			for (LinkSpan linkSpan : s.getSpans(0, s.length(), LinkSpan.class)) {
				s.removeSpan(linkSpan);
			}
			if (text == null) {
				text = s.toString();
			}
			int index = 0;
			while (true) {
				int start = text.indexOf(">>", index);
				if (start == -1) {
					break;
				}
				int numbers = 0;
				for (int i = start + 2; i < text.length(); i++) {
					if (isNumber(text.charAt(i))) {
						numbers++;
					} else {
						break;
					}
				}
				if (numbers == 0) {
					index++;
				} else {
					if (start == 0 || text.charAt(start - 1) == '\n') {
						// Remove quote span in the start of line
						for (QuoteSpan quoteSpan : s.getSpans(start, start, QuoteSpan.class)) {
							s.removeSpan(quoteSpan);
						}
					}
					index = start + 2 + numbers;
					LinkSpan linkSpan = new LinkSpan(null, null);
					linkSpan.applyColorScheme(colorScheme);
					s.setSpan(linkSpan, start, index, Editable.SPAN_EXCLUSIVE_EXCLUSIVE);
				}
			}
		} else if (updateQuotes) {
			// Reorder spans
			for (LinkSpan linkSpan : s.getSpans(0, s.length(), LinkSpan.class)) {
				int start = s.getSpanStart(linkSpan);
				int end = s.getSpanEnd(linkSpan);
				int flags = s.getSpanFlags(linkSpan);
				s.removeSpan(linkSpan);
				s.setSpan(linkSpan, start, end, flags);
			}
		}
		updateQuotes = false;
		updateLinks = false;
		listen = true;
	}
}