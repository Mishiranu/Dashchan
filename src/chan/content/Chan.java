package chan.content;

import android.graphics.drawable.Drawable;
import android.net.Uri;
import androidx.annotation.NonNull;

public final class Chan {
	public final String name;
	public final String packageName;

	public final ChanConfiguration configuration;
	public final ChanPerformer performer;
	public final ChanLocator locator;
	public final ChanMarkup markup;

	final Drawable icon;

	Chan(String name, String packageName,
			ChanConfiguration configuration, ChanPerformer performer,
			ChanLocator locator, ChanMarkup markup, Drawable icon) {
		this.name = name;
		this.packageName = packageName;
		this.configuration = configuration;
		this.performer = performer;
		this.locator = locator;
		this.markup = markup;
		this.icon = icon;
	}

	static final class Provider {
		private Chan chan;

		public Provider(Chan chan) {
			this.chan = chan;
		}

		public Chan get() {
			Chan chan = this.chan;
			if (chan == null) {
				synchronized (this) {
					chan = this.chan;
				}
			}
			if (chan == null) {
				throw new IllegalStateException();
			}
			return chan;
		}

		public void set(Chan chan) {
			synchronized (this) {
				if (this.chan != null) {
					throw new IllegalStateException();
				}
				this.chan = chan;
			}
		}
	}

	public interface Linked {
		void init();
		Chan get();
	}

	@NonNull
	public static Chan get(String chanName) {
		return ChanManager.getInstance().getChan(chanName);
	}

	@NonNull
	public static Chan getPreferred(String chanName, Uri uri) {
		return get(chanName != null ? chanName : uri != null
				? ChanManager.getInstance().getChanNameByHost(uri.getAuthority()) : null);
	}

	@NonNull
	public static Chan getFallback() {
		return ChanManager.getInstance().getFallbackChan();
	}
}
