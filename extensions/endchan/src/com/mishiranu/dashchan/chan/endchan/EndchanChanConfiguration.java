package com.mishiranu.dashchan.chan.endchan;

import android.content.res.Resources;
import android.util.Pair;
import chan.content.ChanConfiguration;
import chan.util.CommonUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class EndchanChanConfiguration extends ChanConfiguration {
	private static final String KEY_NAMES_ENABLED = "names_enabled";
	private static final String KEY_FLAGS_ENABLED = "flags_enabled";
	private static final String KEY_DELETE_ENABLED = "delete_enabled";
	private static final String KEY_CODE_ENABLED = "code_enabled";

	public EndchanChanConfiguration() {
		request(OPTION_READ_USER_BOARDS);
		setDefaultName("Anonymous");
		addCaptchaType("endchan");
	}

	@Override
	public Board obtainBoardConfiguration(String boardName) {
		Board board = new Board();
		board.allowCatalog = true;
		board.allowPosting = true;
		board.allowDeleting = get(boardName, KEY_DELETE_ENABLED, false) || boardName == null;
		board.allowReporting = true;
		return board;
	}

	@Override
	public Captcha obtainCustomCaptchaConfiguration(String captchaType) {
		if ("endchan".equals(captchaType)) {
			Captcha captcha = new Captcha();
			captcha.title = "Endchan";
			captcha.input = Captcha.Input.ALL;
			captcha.validity = Captcha.Validity.IN_BOARD_SEPARATELY;
			return captcha;
		}
		return null;
	}

	@Override
	public Posting obtainPostingConfiguration(String boardName, boolean newThread) {
		Posting posting = new Posting();
		posting.allowName = posting.allowTripcode = get(boardName, KEY_NAMES_ENABLED, true);
		posting.allowEmail = true;
		posting.allowSubject = true;
		posting.optionSage = true;
		posting.attachmentCount = 5;
		posting.attachmentMimeTypes.add("image/*");
		posting.attachmentMimeTypes.add("video/*");
		posting.attachmentMimeTypes.add("audio/*");
		posting.attachmentMimeTypes.add("text/plain");
		posting.attachmentMimeTypes.add("application/pdf");
		posting.attachmentMimeTypes.add("application/x-shockwave-flash");
		posting.attachmentMimeTypes.add("application/vnd.adobe.flash.movie");
		posting.attachmentMimeTypes.add("application/epub+zip");
		posting.attachmentMimeTypes.add("application/zip");
		posting.attachmentMimeTypes.add("application/x-7z-compressed");
		posting.attachmentMimeTypes.add("application/x-rar-compressed");
		posting.attachmentMimeTypes.add("application/x-tar");
		posting.attachmentMimeTypes.add("application/x-gzip");
		posting.attachmentMimeTypes.add("application/x-bzip2");
		posting.attachmentMimeTypes.add("application/download");
		posting.attachmentSpoiler = true;
		posting.hasCountryFlags = get(boardName, KEY_FLAGS_ENABLED, false);
		return posting;
	}

	@Override
	public Deleting obtainDeletingConfiguration(String boardName) {
		Deleting deleting = new Deleting();
		deleting.password = true;
		deleting.multiplePosts = true;
		deleting.optionFilesOnly = true;
		return deleting;
	}

	@Override
	public Reporting obtainReportingConfiguration(String boardName) {
		Resources resources = getResources();
		Reporting reporting = new Reporting();
		reporting.comment = true;
		reporting.options.add(new Pair<>("global", resources.getString(R.string.text_global_report)));
		return reporting;
	}

	public boolean isTagSupported(String boardName, int tag) {
		if (tag == EndchanChanMarkup.TAG_CODE) {
			return get(boardName, KEY_CODE_ENABLED, false);
		}
		return false;
	}

	public void updateFromThreadsJson(String boardName, JSONObject jsonObject, boolean updateTitle) {
		if (updateTitle) {
			try {
				String title = CommonUtils.getJsonString(jsonObject, "boardName");
				String description = CommonUtils.optJsonString(jsonObject, "boardDescription");
				storeBoardTitle(boardName, title);
				storeBoardDescription(boardName, description);
			} catch (JSONException e) {
				// Ignore exception
			}
		}
		boolean namesEnabled = true;
		boolean flagsEnabled = false;
		boolean deleteEnabled = true;
		boolean codeEnabled = false;
		JSONArray jsonArray = jsonObject.optJSONArray("settings");
		if (jsonArray != null) {
			for (int i = 0; i < jsonArray.length(); i++) {
				String setting = jsonArray.optString(i);
				if (setting != null) {
					switch (setting) {
						case "forceAnonymity": {
							namesEnabled = false;
							break;
						}
						case "locationFlags": {
							flagsEnabled = true;
							break;
						}
						case "blockDeletion": {
							deleteEnabled = false;
							break;
						}
						case "allowCode": {
							codeEnabled = true;
							break;
						}
					}
				}
			}
		}
		set(boardName, KEY_NAMES_ENABLED, namesEnabled);
		set(boardName, KEY_FLAGS_ENABLED, flagsEnabled);
		set(boardName, KEY_DELETE_ENABLED, deleteEnabled);
		set(boardName, KEY_CODE_ENABLED, codeEnabled);
	}
}
