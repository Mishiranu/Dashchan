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

import java.util.ArrayList;

import chan.util.StringUtils;

public class StringBlockBuilder
{
	private final ArrayList<String> mStrings = new ArrayList<>();

	public void appendLine(String line)
	{
		if (!StringUtils.isEmpty(line)) mStrings.add(line);
	}

	public void appendEmptyLine()
	{
		mStrings.add(null);
	}

	@Override
	public String toString()
	{
		StringBuilder builder = new StringBuilder();
		int linesToAppend = 0;
		for (int i = 0, size = mStrings.size(); i < size; i++)
		{
			String line = mStrings.get(i);
			if (line == null) linesToAppend++; else
			{
				if (builder.length() > 0)
				{
					if (linesToAppend > 2) linesToAppend = 2;
					for (int j = 0; j < linesToAppend; j++) builder.append('\n');
				}
				builder.append(line);
				linesToAppend = 1;
			}
		}
		return builder.toString();
	}
}