package com.mishiranu.dashchan.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SearchHelper {
	private enum Search {NONE, INCLUDE, EXCLUDE}

	private static final Pattern PATTERN_LONG_QUERY_PART = Pattern.compile("(?:^| )(-)?\"(.*?)\"(?= |$)");

	private final boolean advancedMode;
	private final HashMap<String, Search> flags = new HashMap<>();
	private final HashSet<String> resultInclude = new HashSet<>();
	private final HashSet<String> resultExclude = new HashSet<>();

	public SearchHelper(boolean advancedMode) {
		this.advancedMode = advancedMode;
	}

	public void setFlags(String... flags) {
		this.flags.clear();
		for (String flag : flags) {
			this.flags.put(flag, Search.NONE);
		}
	}

	public HashSet<String> handleQueries(Locale locale, String query) {
		StringBuilder queryBuilder = null;
		int shift = 0;
		HashSet<String> queries = new HashSet<>();
		HashSet<String> include = resultInclude;
		HashSet<String> exclude = resultExclude;
		include.clear();
		exclude.clear();
		if (advancedMode) {
			Matcher matcher = PATTERN_LONG_QUERY_PART.matcher(query);
			while (matcher.find()) {
				int start = matcher.start(), end = matcher.end();
				int remove = end - start;
				if (queryBuilder == null) {
					queryBuilder = new StringBuilder(query);
				}
				queryBuilder.delete(start - shift, end - shift);
				shift += remove;
				String excludePart = matcher.group(1);
				String queryPart = matcher.group(2);
				if (queryPart.length() > 0) {
					String lowQueryPart = queryPart.toLowerCase(locale);
					if ("-".equals(excludePart)) {
						exclude.add(lowQueryPart);
					} else {
						include.add(lowQueryPart);
						queries.add(queryPart);
					}
				}
			}
			if (queryBuilder != null) {
				query = queryBuilder.toString();
			}
			String[] splitted = query.split(" +");
			OUTER: for (String queryPart : splitted) {
				String lowQueryPart = queryPart.toLowerCase(locale);
				for (String flag : flags.keySet()) {
					if ((":" + flag).equals(lowQueryPart)) {
						flags.put(flag, Search.INCLUDE);
						continue OUTER;
					} else if ((":-" + flag).equals(lowQueryPart)) {
						flags.put(flag, Search.EXCLUDE);
						continue OUTER;
					}
				}
				if (lowQueryPart.startsWith("-")) {
					lowQueryPart = lowQueryPart.substring(1);
					if (lowQueryPart.length() > 0) {
						exclude.add(lowQueryPart);
					}
				} else if (lowQueryPart.length() > 0) {
					include.add(lowQueryPart);
					queries.add(queryPart);
				}
			}
		} else {
			queries.add(query);
			include.add(query.toLowerCase(locale));
		}
		return queries;
	}

	private final HashMap<String, Boolean> flagsState = new HashMap<>();

	// flag-state (String-Boolean) alternation
	public boolean checkFlags(Object... alernation) {
		for (int i = 0; i < alernation.length; i += 2) {
			flagsState.put((String) alernation[i], (Boolean) alernation[i + 1]);
		}
		return checkFlags(flagsState);
	}

	private boolean checkFlags(HashMap<String, Boolean> flagsState) {
		if (!advancedMode) {
			return true;
		}
		OUTER: for (HashMap.Entry<String, Boolean> flagState : flagsState.entrySet()) {
			String flag = flagState.getKey();
			boolean fulfilled = flagState.getValue();
			Search value = flags.get(flag);
			if (fulfilled && value == Search.EXCLUDE) {
				return false;
			}
			if (!fulfilled && value == Search.INCLUDE) {
				for (HashMap.Entry<String, Boolean> checkFlagState : flagsState.entrySet()) {
					String checkFlag = checkFlagState.getKey();
					if (flag.equals(checkFlag)) {
						continue;
					}
					boolean checkFulfilled = checkFlagState.getValue();
					Search checkValue = flags.get(checkFlag);
					if (checkFulfilled && checkValue == Search.INCLUDE) {
						continue OUTER;
					}
				}
				return false;
			}
		}
		return true;
	}

	public boolean hasIncluded() {
		return resultInclude.size() > 0;
	}

	public Iterable<String> getIncluded() {
		return resultInclude;
	}

	public Iterable<String> getExcluded() {
		return resultExclude;
	}
}
