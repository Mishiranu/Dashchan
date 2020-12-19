package com.mishiranu.dashchan.ui;

import android.app.Dialog;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import chan.content.Chan;
import chan.content.ChanLocator;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.util.NavigationUtils;

public class SearchImageDialog extends DialogFragment {
	private static final String EXTRA_CHAN_NAME = "chanName";
	private static final String EXTRA_URI = "uri";

	public SearchImageDialog() {}

	public SearchImageDialog(String chanName, Uri uri) {
		Bundle args = new Bundle();
		args.putString(EXTRA_CHAN_NAME, chanName);
		args.putParcelable(EXTRA_URI, uri);
		setArguments(args);
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		Context context = requireContext();
		String chanName = requireArguments().getString(EXTRA_CHAN_NAME);
		Uri uri = requireArguments().getParcelable(EXTRA_URI);
		ChanLocator locator = Chan.getFallback().locator;
		String imageUriString = Chan.get(chanName).locator.convert(uri).toString();
		return new DialogMenu(new ContextThemeWrapper(context, R.style.Theme_Gallery))
				.add("Google", () -> searchImageUri(locator.buildQueryWithHost("www.google.com",
						"searchbyimage", "image_url", imageUriString)))
				.add("Yandex", () -> searchImageUri(locator.buildQueryWithHost("www.yandex.ru",
						"images/search", "rpt", "imageview", "url", imageUriString)))
				.add("TinEye", () -> searchImageUri(locator.buildQueryWithHost("www.tineye.com",
						"search", "url", imageUriString)))
				.add("SauceNAO", () -> searchImageUri(locator.buildQueryWithHost("saucenao.com",
						"search.php", "url", imageUriString)))
				.add("iqdb.org", () -> searchImageUri(locator.buildQueryWithHost("iqdb.org",
						"/", "url", imageUriString)))
				.add("trace.moe", () -> searchImageUri(locator.buildQueryWithHost("trace.moe",
						"/", "url", imageUriString)))
				.create();
	}

	private void searchImageUri(Uri searchUri) {
		NavigationUtils.handleUri(requireContext(), null, searchUri, NavigationUtils.BrowserType.EXTERNAL);
	}
}
