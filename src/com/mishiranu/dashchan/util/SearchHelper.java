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

package com.mishiranu.dashchan.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SearchHelper
{
	private static final int SEARCH_NONE = 0;
	private static final int SEARCH_INCLUDE = 1;
	private static final int SEARCH_EXCLUDE = -1;
	
	private static final Pattern PATTERN_LONG_QUERY_PART = Pattern.compile("(?:^| )(-)?\"(.*?)\"(?= |$)");
	
	private final HashMap<String, Integer> mFlags = new HashMap<>();  
	private final HashSet<String> mResultInclude = new HashSet<>();  
	private final HashSet<String> mResultExclude = new HashSet<>();
	
	public void setFlags(String... flags)
	{
		mFlags.clear();
		for (String flag : flags) mFlags.put(flag, SEARCH_NONE);
	}
	
	public HashSet<String> handleQueries(Locale locale, String query)
	{
		StringBuilder queryBuilder = null;
		int shift = 0;
		HashSet<String> queries = new HashSet<>();
		HashSet<String> include = mResultInclude;
		HashSet<String> exclude = mResultExclude;
		include.clear();
		exclude.clear();
		Matcher matcher = PATTERN_LONG_QUERY_PART.matcher(query);
		while (matcher.find())
		{
			int start = matcher.start(), end = matcher.end();
			int remove = end - start;
			if (queryBuilder == null) queryBuilder = new StringBuilder(query);
			queryBuilder.delete(start - shift, end - shift);
			shift += remove;
			String excludePart = matcher.group(1);
			String queryPart = matcher.group(2);
			if (queryPart.length() > 0)
			{
				String lowQueryPart = queryPart.toLowerCase(locale);
				if ("-".equals(excludePart)) exclude.add(lowQueryPart); else
				{
					include.add(lowQueryPart);
					queries.add(queryPart);
				}
			}
		}
		if (queryBuilder != null) query = queryBuilder.toString();
		String[] splitted = query.split(" +");
		OUTER: for (String queryPart : splitted)
		{
			String lowQueryPart = queryPart.toLowerCase(locale);
			for (String flag : mFlags.keySet())
			{
				if ((":" + flag).equals(lowQueryPart))
				{
					mFlags.put(flag, SEARCH_INCLUDE);
					continue OUTER;
				}
				else if ((":-" + flag).equals(lowQueryPart))
				{
					mFlags.put(flag, SEARCH_EXCLUDE);
					continue OUTER;
				}
			}
			if (lowQueryPart.startsWith("-"))
			{
				lowQueryPart = lowQueryPart.substring(1);
				if (lowQueryPart.length() > 0) exclude.add(lowQueryPart);
			}
			else if (lowQueryPart.length() > 0)
			{
				include.add(lowQueryPart);
				queries.add(queryPart);
			}
		}
		return queries;
	}
	
	private final HashMap<String, Boolean> mFlagsState = new HashMap<>();
	
	/*
	 * flag-state (String-Boolean) alternation
	 */
	public boolean checkFlags(Object... alernation)
	{
		for (int i = 0; i < alernation.length; i += 2)
		{
			mFlagsState.put((String) alernation[i], (Boolean) alernation[i + 1]);
		}
		return checkFlags(mFlagsState);
		
	}
	
	private boolean checkFlags(HashMap<String, Boolean> flagsState)
	{
		OUTER: for (HashMap.Entry<String, Boolean> flagState : flagsState.entrySet())
		{
			String flag = flagState.getKey();
			boolean fulfilled = flagState.getValue();
			int value = mFlags.get(flag);
			if (fulfilled && value == SEARCH_EXCLUDE) return false;
			if (!fulfilled && value == SEARCH_INCLUDE)
			{
				for (HashMap.Entry<String, Boolean> checkFlagState : flagsState.entrySet())
				{
					String checkFlag = checkFlagState.getKey();
					if (flag.equals(checkFlag)) continue;
					boolean checkFulfilled = checkFlagState.getValue();
					int checkValue = mFlags.get(checkFlag);
					if (checkFulfilled && checkValue == SEARCH_INCLUDE) continue OUTER;
				}
				return false;
			}
		}
		return true;
	}
	
	public boolean hasIncluded()
	{
		return mResultInclude.size() > 0;
	}
	
	public Iterable<String> getIncluded()
	{
		return mResultInclude;
	}
	
	public Iterable<String> getExcluded()
	{
		return mResultExclude;
	}
}