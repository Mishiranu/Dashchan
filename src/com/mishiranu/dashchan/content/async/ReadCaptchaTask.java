package com.mishiranu.dashchan.content.async;

import android.graphics.Bitmap;
import chan.content.Chan;
import chan.content.ChanConfiguration;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.util.CommonUtils;
import chan.util.StringUtils;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.net.RecaptchaReader;
import com.mishiranu.dashchan.util.GraphicsUtils;
import java.util.List;
import java.util.Set;

public class ReadCaptchaTask extends ExecutorTask<Void, Boolean> {
	public static final String RECAPTCHA_SKIP_RESPONSE = "recaptcha_skip_response";

	private final HttpHolder chanHolder;
	private final HttpHolder fallbackHolder = new HttpHolder(Chan.getFallback());

	private final Callback callback;
	private final CaptchaReader captchaReader;

	private final String captchaType;
	private final List<String> captchaPass;
	private final boolean mayShowLoadButton;
	private final String requirement;
	private final Chan chan;
	private final String boardName;
	private final String threadNumber;

	private CaptchaState captchaState;
	private ChanPerformer.CaptchaData captchaData;
	private String loadedCaptchaType;
	private ChanConfiguration.Captcha.Input input;
	private ChanConfiguration.Captcha.Validity validity;
	private Bitmap image;
	private boolean large;
	private boolean blackAndWhite;
	private ErrorItem errorItem;

	public interface Callback {
		void onReadCaptchaSuccess(CaptchaState captchaState, ChanPerformer.CaptchaData captchaData,
				String captchaType, ChanConfiguration.Captcha.Input input, ChanConfiguration.Captcha.Validity validity,
				Bitmap image, boolean large, boolean blackAndWhite);
		void onReadCaptchaError(ErrorItem errorItem);
	}

	public enum CaptchaState {CAPTCHA, SKIP, PASS, NEED_LOAD, MAY_LOAD}

	public static class Result {
		public final ChanPerformer.ReadCaptchaResult result;
		public final Object challengeExtra;
		public final boolean allowSolveAutomatically;

		public Result(ChanPerformer.ReadCaptchaResult result, Object challengeExtra, boolean allowSolveAutomatically) {
			this.result = result;
			this.challengeExtra = challengeExtra;
			this.allowSolveAutomatically = allowSolveAutomatically;
		}
	}

	public interface CaptchaReader {
		Result onReadCaptcha(ChanPerformer.ReadCaptchaData data)
				throws ExtensionException, HttpException, InvalidResponseException;
	}

	private static class ChanCaptchaReader implements CaptchaReader {
		private final Chan chan;

		public ChanCaptchaReader(Chan chan) {
			this.chan = chan;
		}

		@Override
		public Result onReadCaptcha(ChanPerformer.ReadCaptchaData data)
				throws ExtensionException, HttpException, InvalidResponseException {
			ChanPerformer.ReadCaptchaResult result = chan.performer.safe().onReadCaptcha(data);
			if (result == null) {
				throw new ExtensionException(new RuntimeException("Captcha result is null"));
			}
			return new Result(result, null, allowSolveAutomatically(chan.name));
		}
	}

	private enum ForegroundCaptcha {RECAPTCHA_2, RECAPTCHA_2_INVISIBLE, HCAPTCHA}

	public ReadCaptchaTask(Callback callback, CaptchaReader captchaReader,
			String captchaType, String requirement, List<String> captchaPass, boolean mayShowLoadButton,
			Chan chan, String boardName, String threadNumber) {
		chanHolder = new HttpHolder(chan);
		if (captchaReader == null) {
			captchaReader = new ChanCaptchaReader(chan);
		}
		this.callback = callback;
		this.captchaReader = captchaReader;
		this.captchaType = captchaType;
		this.captchaPass = captchaPass;
		this.mayShowLoadButton = mayShowLoadButton;
		this.requirement = requirement;
		this.chan = chan;
		this.boardName = boardName;
		this.threadNumber = threadNumber;
	}

	private static boolean allowSolveAutomatically(String chanName) {
		Set<String> chanNames = Preferences.getCaptchaSolvingChans();
		return chanNames.isEmpty() || chanNames.contains(chanName);
	}

	public static ForegroundCaptcha checkForegroundCaptcha(String captchaType) {
		switch (StringUtils.emptyIfNull(captchaType)) {
			case ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2: return ForegroundCaptcha.RECAPTCHA_2;
			case ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2_INVISIBLE: return ForegroundCaptcha.RECAPTCHA_2_INVISIBLE;
			case ChanConfiguration.CAPTCHA_TYPE_HCAPTCHA: return ForegroundCaptcha.HCAPTCHA;
			default: return null;
		}
	}

	public static String readForegroundCaptcha(HttpHolder holder, String chanName,
			ChanPerformer.CaptchaData captchaData, String captchaType) throws HttpException {
		ForegroundCaptcha foregroundCaptcha = checkForegroundCaptcha(captchaType);
		if (foregroundCaptcha == null) {
			return null;
		}
		try {
			return readForegroundCaptcha(holder, captchaData, foregroundCaptcha, null,
					allowSolveAutomatically(chanName));
		} catch (RecaptchaReader.CancelException e) {
			return null;
		}
	}

	private static String readForegroundCaptcha(HttpHolder holder, ChanPerformer.CaptchaData captchaData,
			ForegroundCaptcha foregroundCaptcha, Object challengeExtra, boolean allowSolveAutomatically)
			throws HttpException, RecaptchaReader.CancelException {
		String apiKey = captchaData.get(ChanPerformer.CaptchaData.API_KEY);
		String referer = captchaData.get(ChanPerformer.CaptchaData.REFERER);
		RecaptchaReader recaptchaReader = RecaptchaReader.getInstance();
		RecaptchaReader.ChallengeExtra recaptchaChallengeExtra = challengeExtra instanceof
				RecaptchaReader.ChallengeExtra ? (RecaptchaReader.ChallengeExtra) challengeExtra : null;
		if (recaptchaChallengeExtra == null) {
			if (foregroundCaptcha == ForegroundCaptcha.HCAPTCHA) {
				recaptchaChallengeExtra = recaptchaReader.getChallengeHcaptcha(holder,
						apiKey, referer, false, allowSolveAutomatically);
			} else {
				boolean invisible = foregroundCaptcha == ForegroundCaptcha.RECAPTCHA_2_INVISIBLE;
				recaptchaChallengeExtra = recaptchaReader.getChallenge2(holder,
						apiKey, invisible, referer, Preferences.isRecaptchaJavascript(),
						false, allowSolveAutomatically);
			}
		}
		return recaptchaChallengeExtra.getResponse(holder);
	}

	@Override
	protected Boolean run() {
		Result result;
		try (HttpHolder.Use ignore = chanHolder.use()) {
			result = captchaReader.onReadCaptcha(new ChanPerformer.ReadCaptchaData(captchaType,
					CommonUtils.toArray(captchaPass, String.class), mayShowLoadButton,
					requirement, boardName, threadNumber, chanHolder));
		} catch (ExtensionException | HttpException | InvalidResponseException e) {
			errorItem = e.getErrorItemAndHandle();
			return false;
		} finally {
			chan.configuration.commit();
		}
		ChanPerformer.CaptchaState chanCaptchaState = result.result.captchaState;
		if (chanCaptchaState != null) {
			switch (chanCaptchaState) {
				case CAPTCHA: {
					captchaState = CaptchaState.CAPTCHA;
					break;
				}
				case SKIP: {
					captchaState = CaptchaState.SKIP;
					break;
				}
				case PASS: {
					captchaState = CaptchaState.PASS;
					break;
				}
				case NEED_LOAD: {
					captchaState = CaptchaState.NEED_LOAD;
					break;
				}
			}
		}
		captchaData = result.result.captchaData;
		loadedCaptchaType = result.result.captchaType;
		input = result.result.input;
		validity = result.result.validity;
		if (captchaState != CaptchaState.CAPTCHA) {
			return true;
		}
		if (isCancelled()) {
			return null;
		}
		String captchaType = loadedCaptchaType != null ? loadedCaptchaType : this.captchaType;
		ForegroundCaptcha foregroundCaptcha = checkForegroundCaptcha(captchaType);
		if (foregroundCaptcha != null) {
			if (mayShowLoadButton) {
				captchaState = CaptchaState.MAY_LOAD;
				return true;
			}
			try (HttpHolder.Use ignore = fallbackHolder.use()) {
				String response = readForegroundCaptcha(fallbackHolder, captchaData, foregroundCaptcha,
						result.challengeExtra, result.allowSolveAutomatically);
				captchaData.put(RECAPTCHA_SKIP_RESPONSE, response);
				captchaState = CaptchaState.SKIP;
				return true;
			} catch (RecaptchaReader.CancelException e) {
				captchaState = CaptchaState.MAY_LOAD;
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
	public void cancel() {
		super.cancel();
		chanHolder.interrupt();
		fallbackHolder.interrupt();
	}

	@Override
	protected void onComplete(Boolean success) {
		if (success) {
			callback.onReadCaptchaSuccess(captchaState, captchaData, loadedCaptchaType, input, validity,
					image, large, blackAndWhite);
		} else {
			callback.onReadCaptchaError(errorItem);
		}
	}
}
