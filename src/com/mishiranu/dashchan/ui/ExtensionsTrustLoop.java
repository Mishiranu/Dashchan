package com.mishiranu.dashchan.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.style.ReplacementSpan;
import androidx.annotation.NonNull;
import chan.content.ChanManager;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.text.style.MonospaceSpan;
import java.lang.ref.WeakReference;

public class ExtensionsTrustLoop {
	public static final class State {
		private WeakReference<AlertDialog> currentDialog;
	}

	// Allows dots to break lines
	private static class DotSpan extends ReplacementSpan {
		@Override
		public int getSize(@NonNull Paint paint, CharSequence text, int start, int end,
				Paint.FontMetricsInt fontMetricsInt) {
			return (int) (paint.measureText(".") + 0.5f);
		}

		@Override
		public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end,
				float x, int top, int y, int bottom, @NonNull Paint paint) {
			canvas.drawText(".", x, y, paint);
		}
	}

	public static void handleUntrustedExtensions(Context context, State state) {
		if (state.currentDialog != null) {
			AlertDialog currentDialog = state.currentDialog.get();
			if (currentDialog != null) {
				currentDialog.dismiss();
			}
		}
		ChanManager.ExtensionItem extensionItem = ChanManager.getInstance().getFirstUntrustedExtension();
		if (extensionItem != null) {
			SpannableStringBuilder message = new SpannableStringBuilder();
			message.append(context.getString(R.string.allow_this_extension__sentence));
			message.append("\n\n");
			SpannableStringBuilder packageName = new SpannableStringBuilder(extensionItem.packageName);
			int index = -1;
			while (true) {
				index = extensionItem.packageName.indexOf('.', index + 1);
				if (index < 0) {
					break;
				}
				packageName.setSpan(new DotSpan(), index, index + 1, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
			}
			message.append("Package name:\n").append(packageName);
			message.append("\n\n");
			SpannableStringBuilder fingerprints = new SpannableStringBuilder();
			for (String fingerprint : extensionItem.fingerprints.fingerprints) {
				if (fingerprints.length() > 0) {
					fingerprints.append(" /");
				}
				for (int i = 0; i < fingerprint.length() / 2; i++) {
					if (fingerprints.length() > 0) {
						fingerprints.append(' ');
					}
					fingerprints.append(Character.toUpperCase(fingerprint.charAt(i)));
					fingerprints.append(Character.toUpperCase(fingerprint.charAt(i + 1)));
				}
			}
			fingerprints.setSpan(new MonospaceSpan(false), 0, fingerprints.length(),
					SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
			message.append("SHA-256 fingerprint:\n").append(fingerprints);
			AlertDialog dialog = new AlertDialog.Builder(context)
					.setTitle(extensionItem.title).setMessage(message)
					.setCancelable(false)
					.setPositiveButton(android.R.string.ok, (d, w) -> {
						ChanManager.getInstance().changeUntrustedExtensionState(extensionItem.name, true);
						handleUntrustedExtensions(context, state);
					})
					.setNegativeButton(android.R.string.cancel, (d, w) -> {
						ChanManager.getInstance().changeUntrustedExtensionState(extensionItem.name, false);
						handleUntrustedExtensions(context, state);
					})
					.setNeutralButton(R.string.details, (d, w) -> {
						context.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
								.setData(Uri.parse("package:" + extensionItem.packageName)));
						handleUntrustedExtensions(context, state);
					})
					.show();
			state.currentDialog = new WeakReference<>(dialog);
			dialog.setOnDismissListener(d -> {
				if (state.currentDialog != null && state.currentDialog.get() == dialog) {
					state.currentDialog = null;
				}
			});
		}
	}
}
