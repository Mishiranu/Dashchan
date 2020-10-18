package com.mishiranu.dashchan.content.async;

import android.graphics.Bitmap;
import chan.content.ChanConfiguration;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.http.HttpException;
import chan.http.HttpHolder;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.net.RecaptchaReader;
import com.mishiranu.dashchan.util.GraphicsUtils;

public class ReadCaptchaTask extends HttpHolderTask<Void, Long, Boolean> {
	public static final String RECAPTCHA_SKIP_RESPONSE = "recaptcha_skip_response";

	private final Callback callback;
	private final CaptchaReader captchaReader;

	private final String captchaType;
	private final String[] captchaPass;
	private final boolean mayShowLoadButton;
	private final String requirement;
	private final String chanName;
	private final String boardName;
	private final String threadNumber;

	private ChanPerformer.CaptchaState captchaState;
	private ChanPerformer.CaptchaData captchaData;
	private String loadedCaptchaType;
	private ChanConfiguration.Captcha.Input input;
	private ChanConfiguration.Captcha.Validity validity;
	private Bitmap image;
	private boolean large;
	private boolean blackAndWhite;
	private ErrorItem errorItem;

	public interface Callback {
		void onReadCaptchaSuccess(ChanPerformer.CaptchaState captchaState, ChanPerformer.CaptchaData captchaData,
				String captchaType, ChanConfiguration.Captcha.Input input, ChanConfiguration.Captcha.Validity validity,
				Bitmap image, boolean large, boolean blackAndWhite);
		void onReadCaptchaError(ErrorItem errorItem);
	}

	public static class Result {
		public final ChanPerformer.ReadCaptchaResult result;
		public final Object challengeExtra;

		public Result(ChanPerformer.ReadCaptchaResult result, Object challengeExtra) {
			this.result = result;
			this.challengeExtra = challengeExtra;
		}
	}

	public interface CaptchaReader {
		Result onReadCaptcha(ChanPerformer.ReadCaptchaData data)
				throws ExtensionException, HttpException, InvalidResponseException;
	}

	private static class ChanCaptchaReader implements CaptchaReader {
		private final String chanName;

		public ChanCaptchaReader(String chanName) {
			this.chanName = chanName;
		}

		@Override
		public Result onReadCaptcha(ChanPerformer.ReadCaptchaData data)
				throws ExtensionException, HttpException, InvalidResponseException {
			ChanPerformer.ReadCaptchaResult result = ChanPerformer.get(chanName).safe().onReadCaptcha(data);
			if (result == null) {
				throw new ExtensionException(new RuntimeException("Captcha result is null"));
			}
			return new Result(result, null);
		}
	}

	public ReadCaptchaTask(Callback callback, CaptchaReader captchaReader,
			String captchaType, String requirement, String[] captchaPass, boolean mayShowLoadButton,
			String chanName, String boardName, String threadNumber) {
		if (captchaReader == null) {
			captchaReader = new ChanCaptchaReader(chanName);
		}
		this.callback = callback;
		this.captchaReader = captchaReader;
		this.captchaType = captchaType;
		this.captchaPass = captchaPass;
		this.mayShowLoadButton = mayShowLoadButton;
		this.requirement = requirement;
		this.chanName = chanName;
		this.boardName = boardName;
		this.threadNumber = threadNumber;
	}

	@Override
	protected Boolean doInBackground(HttpHolder holder, Void... params) {
		Result result;
		try {
			result = captchaReader.onReadCaptcha(new ChanPerformer.ReadCaptchaData(captchaType,
					captchaPass, mayShowLoadButton, requirement, boardName, threadNumber, holder));
		} catch (ExtensionException | HttpException | InvalidResponseException e) {
			errorItem = e.getErrorItemAndHandle();
			return false;
		} finally {
			ChanConfiguration.get(chanName).commit();
		}
		captchaState = result.result.captchaState;
		captchaData = result.result.captchaData;
		loadedCaptchaType = result.result.captchaType;
		input = result.result.input;
		validity = result.result.validity;
		String captchaType = loadedCaptchaType != null ? loadedCaptchaType : this.captchaType;
		boolean recaptcha2 = ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2.equals(captchaType);
		boolean recaptcha2Invisible = ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2_INVISIBLE.equals(captchaType);
		boolean hcaptcha = ChanConfiguration.CAPTCHA_TYPE_HCAPTCHA.equals(captchaType);
		if (captchaState != ChanPerformer.CaptchaState.CAPTCHA) {
			return true;
		}
		if (isCancelled()) {
			return null;
		}
		if (recaptcha2 || recaptcha2Invisible || hcaptcha) {
			if (mayShowLoadButton) {
				captchaState = ChanPerformer.CaptchaState.NEED_LOAD;
				return true;
			}
			String apiKey = captchaData.get(ChanPerformer.CaptchaData.API_KEY);
			String referer = captchaData.get(ChanPerformer.CaptchaData.REFERER);
			try {
				RecaptchaReader recaptchaReader = RecaptchaReader.getInstance();
				String response;
				RecaptchaReader.ChallengeExtra challengeExtra = result.challengeExtra instanceof
						RecaptchaReader.ChallengeExtra ? (RecaptchaReader.ChallengeExtra) result.challengeExtra : null;
				if (challengeExtra == null) {
					if (hcaptcha) {
						challengeExtra = recaptchaReader.getChallengeHcaptcha(apiKey, referer, false);
					} else {
						challengeExtra = recaptchaReader.getChallenge2(holder, apiKey, recaptcha2Invisible, referer,
								Preferences.isRecaptchaJavascript(), false);
					}
				}
				response = challengeExtra.getResponse(holder);
				captchaData.put(RECAPTCHA_SKIP_RESPONSE, response);
				captchaState = ChanPerformer.CaptchaState.SKIP;
				return true;
			} catch (RecaptchaReader.CancelException e) {
				captchaState = ChanPerformer.CaptchaState.NEED_LOAD;
				return true;
			} catch (HttpException e) {
				errorItem = e.getErrorItemAndHandle();
				return false;
			}
		} else if (result.result.image != null) {
			Bitmap image = result.result.image;
			blackAndWhite = GraphicsUtils.isBlackAndWhiteCaptchaImage(image);
			this.image = blackAndWhite ? GraphicsUtils.handleBlackAndWhiteCaptchaImage(image).first : image;
			large = result.result.large;
			return true;
		}
		errorItem = new ErrorItem(ErrorItem.Type.UNKNOWN);
		return false;
	}

	@Override
	protected void onPostExecute(Boolean result) {
		if (result) {
			callback.onReadCaptchaSuccess(captchaState, captchaData, loadedCaptchaType, input, validity,
					image, large, blackAndWhite);
		} else {
			callback.onReadCaptchaError(errorItem);
		}
	}
}
