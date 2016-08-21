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

package com.mishiranu.dashchan.ui;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import android.content.Context;
import android.content.res.Configuration;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;

import com.mishiranu.dashchan.content.MainApplication;

public class ActionMenuConfigurator
{
	private static final Field SHOW_AS_ACTION_FIELD;
	private static final Object ACTION_BAR_POLICY;
	private static final Method GET_MAX_ACTION_BUTTONS_METHOD;
	private static final Method SHOWS_OVERFLOW_MENU_BUTTON_METHOD;
	
	static
	{
		Field showAsActionField;
		Object actionBarPolicy;
		Method getMaxActionButtonsMethod;
		Method showsOverflowMenuButtonMethod;
		try
		{
			Class<?> menuItemImplClass = Class.forName("com.android.internal.view.menu.MenuItemImpl");
			showAsActionField = menuItemImplClass.getDeclaredField("mShowAsAction");
			showAsActionField.setAccessible(true);
			Class<?> actionBarPolicyClass = Class.forName("com.android.internal.view.ActionBarPolicy");
			actionBarPolicy = actionBarPolicyClass.getDeclaredMethod("get", Context.class)
					.invoke(null, MainApplication.getInstance());
			getMaxActionButtonsMethod = actionBarPolicyClass.getMethod("getMaxActionButtons");
			showsOverflowMenuButtonMethod = actionBarPolicyClass.getMethod("showsOverflowMenuButton");
		}
		catch (Exception e)
		{
			showAsActionField = null;
			actionBarPolicy = null;
			getMaxActionButtonsMethod = null;
			showsOverflowMenuButtonMethod = null;
		}
		SHOW_AS_ACTION_FIELD = showAsActionField;
		ACTION_BAR_POLICY = actionBarPolicy;
		GET_MAX_ACTION_BUTTONS_METHOD = getMaxActionButtonsMethod;
		SHOWS_OVERFLOW_MENU_BUTTON_METHOD = showsOverflowMenuButtonMethod;
	}
	
	// True for SHOW_AS_ACTION_ALWAYS, false for SHOW_AS_ACTION_IF_ROOM
	private final SparseArray<Boolean> mDisplay = new SparseArray<>();
	private Menu mLastMenu;
	
	public void onConfigurationChanged(Configuration newConfig)
	{
		if (newConfig.orientation != Configuration.ORIENTATION_UNDEFINED && mLastMenu != null)
		{
			onAfterPrepareOptionsMenu(mLastMenu);
		}
	}
	
	public void onAfterCreateOptionsMenu(Menu menu)
	{
		mDisplay.clear();
		mLastMenu = menu;
		for (int i = 0; i < menu.size(); i++)
		{
			MenuItem menuItem = menu.getItem(i);
			int id = menuItem.getItemId();
			int showAsAction = getShowAsAction(menuItem);
			switch (showAsAction)
			{
				case MenuItem.SHOW_AS_ACTION_ALWAYS:
				{
					mDisplay.put(id, true);
					break;
				}
				case MenuItem.SHOW_AS_ACTION_IF_ROOM:
				{
					mDisplay.put(id, false);
					break;
				}
			}
		}
	}
	
	public void onAfterPrepareOptionsMenu(Menu menu)
	{
		int maxCount;
		boolean mayOverflow;
		try
		{
			maxCount = (int) GET_MAX_ACTION_BUTTONS_METHOD.invoke(ACTION_BAR_POLICY);
			mayOverflow = (boolean) SHOWS_OVERFLOW_MENU_BUTTON_METHOD.invoke(ACTION_BAR_POLICY);
		}
		catch (Exception e)
		{
			return;
		}
		int used = 0;
		for (int i = 0; i < menu.size(); i++)
		{
			MenuItem menuItem = menu.getItem(i);
			if (menuItem.isVisible())
			{
				Boolean display = mDisplay.get(menuItem.getItemId());
				if (display != null)
				{
					if (display) used++;
				}
				else if (mayOverflow)
				{
					mayOverflow = false;
					used++;
				}
			}
		}
		for (int i = 0; i < menu.size(); i++)
		{
			MenuItem menuItem = menu.getItem(i);
			if (menuItem.isVisible())
			{
				Boolean display = mDisplay.get(menuItem.getItemId());
				if (display != null && !display)
				{
					if (used < maxCount)
					{
						used++;
						menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
					}
					else menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
				}
			}
		}
	}
	
	private static int getShowAsAction(MenuItem menuItem)
	{
		try
		{
			return SHOW_AS_ACTION_FIELD.getInt(menuItem);
		}
		catch (Exception e)
		{
			return MenuItem.SHOW_AS_ACTION_NEVER;
		}
	}
}