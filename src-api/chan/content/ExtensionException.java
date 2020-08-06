package chan.content;

import com.mishiranu.dashchan.content.MainApplication;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.util.Log;
import com.mishiranu.dashchan.util.ToastUtils;

public class ExtensionException extends Exception implements ErrorItem.Holder {
	public ExtensionException(Throwable throwable) {
		super(throwable);
	}

	@Override
	public ErrorItem getErrorItemAndHandle() {
		logException(getCause(), false);
		return new ErrorItem(ErrorItem.Type.EXTENSION);
	}

	public static void logException(Throwable t, boolean showToast) {
		if (t instanceof LinkageError || t instanceof RuntimeException) {
			Log.persistent().stack(t);
			if (showToast) {
				ToastUtils.show(MainApplication.getInstance(), new ErrorItem(ErrorItem.Type.EXTENSION));
			}
		}
	}
}
