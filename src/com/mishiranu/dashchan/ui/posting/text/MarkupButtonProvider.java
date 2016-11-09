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

package com.mishiranu.dashchan.ui.posting.text;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.Pair;
import android.widget.Button;

import chan.content.ChanMarkup;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.text.style.HeadingSpan;
import com.mishiranu.dashchan.text.style.MonospaceSpan;
import com.mishiranu.dashchan.text.style.OverlineSpan;
import com.mishiranu.dashchan.text.style.ScriptSpan;
import com.mishiranu.dashchan.util.FlagUtils;
import com.mishiranu.dashchan.util.ResourceUtils;

public class MarkupButtonProvider {
	public final int tag;
	public final int widthDp;
	public final int priority;

	public final String text;
	private final Object span;

	private MarkupButtonProvider(int tag, int widthDp, int priority, String text, Object span) {
		this.tag = tag;
		this.widthDp = widthDp;
		this.priority = priority;
		this.text = text;
		this.span = span;
	}

	public Button createButton(Context context, int defStyleAttr) {
		return new Button(context, null, defStyleAttr) {
			@Override
			protected void onDraw(Canvas canvas) {
				super.onDraw(canvas);
				OverlineSpan.draw(this, canvas);
			}
		};
	}

	public Object getSpan(Context context) {
		return span;
	}

	public void applyTextAndStyle(Button button) {
		Object span = getSpan(button.getContext());
		if (span != null) {
			SpannableString spannable = new SpannableString(text);
			spannable.setSpan(span, 0, spannable.length(), SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
			button.setText(spannable);
		} else {
			button.setText(text);
		}
	}

	public static Pair<Integer, Integer> obtainSupportedAndDisplayedTags(ChanMarkup markup, String boardName,
			float density, int maxButtonsWidth, int buttonMarginLeft) {
		int handledButtons = 0;
		int buttonsWidth = 0;
		int supportedTags = 0;
		int displayedTags = 0;
		for (int i = 0; handledButtons != PROVIDERS.size(); i++) {
			int futureTags = 0;
			int futureWidth = buttonsWidth;
			for (int j = 0; j < PROVIDERS.size(); j++) {
				MarkupButtonProvider provider = PROVIDERS.get(j);
				if (provider.priority == i) {
					if (markup.safe().isTagSupported(boardName, provider.tag) || provider.tag == ChanMarkup.TAG_QUOTE) {
						int width = (int) (provider.widthDp * density);
						if (futureWidth > 0) {
							width += buttonMarginLeft;
						}
						futureWidth += width;
						futureTags |= provider.tag;
						supportedTags |= provider.tag;
					}
					handledButtons++;
				}
			}
			if (futureWidth <= maxButtonsWidth) {
				buttonsWidth = futureWidth;
				displayedTags |= futureTags;
			}
		}
		return new Pair<>(supportedTags, displayedTags);
	}

	public static Iterable<MarkupButtonProvider> iterable(int forDisplayedTags) {
		return () -> new Iterator<MarkupButtonProvider>() {
			private int displayedTags = forDisplayedTags;
			private MarkupButtonProvider next;
			private int last = -1;

			@Override
			public boolean hasNext() {
				if (next == null) {
					for (int i = last + 1; i < PROVIDERS.size(); i++) {
						MarkupButtonProvider provider = PROVIDERS.get(i);
						if (FlagUtils.get(displayedTags, provider.tag)) {
							displayedTags = FlagUtils.set(displayedTags, provider.tag, false);
							last = i;
							next = provider;
							break;
						}
					}
				}
				return next != null;
			}

			@Override
			public MarkupButtonProvider next() {
				if (!hasNext()) {
					throw new NoSuchElementException();
				}
				MarkupButtonProvider provider = next;
				next = null;
				return provider;
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	private static final ArrayList<MarkupButtonProvider> PROVIDERS = new ArrayList<>();

	static {
		PROVIDERS.add(new MarkupButtonProvider(ChanMarkup.TAG_BOLD, 40, 0, "B", new StyleSpan(Typeface.BOLD)));
		PROVIDERS.add(new MarkupButtonProvider(ChanMarkup.TAG_ITALIC, 40, 1, "I", new StyleSpan(Typeface.ITALIC)));
		PROVIDERS.add(new MarkupButtonProvider(ChanMarkup.TAG_UNDERLINE, 40, 2, "U", new UnderlineSpan()));
		PROVIDERS.add(new MarkupButtonProvider(ChanMarkup.TAG_OVERLINE, 40, 9, "O", new OverlineSpan()));
		PROVIDERS.add(new MarkupButtonProvider(ChanMarkup.TAG_STRIKE, 40, 3, "S", new StrikethroughSpan()));
		PROVIDERS.add(new MarkupButtonProvider(ChanMarkup.TAG_CODE, 40, 6, "#", new MonospaceSpan(true)));
		PROVIDERS.add(new MarkupButtonProvider(ChanMarkup.TAG_ASCII_ART, 44, 10, "AA", new MonospaceSpan(false)));
		PROVIDERS.add(new MarkupButtonProvider(ChanMarkup.TAG_HEADING, 44, 7, "H", new HeadingSpan()));
		PROVIDERS.add(new MarkupButtonProvider(ChanMarkup.TAG_SUBSCRIPT, 44, 8, "SUB", new ScriptSpan(false)));
		PROVIDERS.add(new MarkupButtonProvider(ChanMarkup.TAG_SUPERSCRIPT, 44, 8, "SUP", new ScriptSpan(true)));
		PROVIDERS.add(new MarkupButtonProvider(ChanMarkup.TAG_SPOILER, 44, 5, "SP", null) {
			@Override
			public Object getSpan(Context context) {
				return new BackgroundColorSpan(ResourceUtils.getColor(context, R.attr.backgroundSpoiler));
			}
		});
		PROVIDERS.add(new MarkupButtonProvider(ChanMarkup.TAG_QUOTE, 40, 4, ">", null) {
			@Override
			public Object getSpan(Context context) {
				return C.API_LOLLIPOP ? null : new ForegroundColorSpan(ResourceUtils.getColor(context,
						R.attr.colorTextQuote));
			}
		});
	}
}