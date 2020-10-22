package com.mishiranu.dashchan.ui;

import java.util.Collection;

public interface ActivityHandler {
	default boolean isSearchMode() {
		return false;
	}

	default boolean onSearchRequested() {
		return false;
	}

	default boolean onHomePressed() {
		return onBackPressed();
	}

	default boolean onBackPressed() {
		return false;
	}

	default void onTerminate() {}
	default void onChansChanged(Collection<String> changed, Collection<String> removed) {}
	default void onStorageRequestResult() {}
}
