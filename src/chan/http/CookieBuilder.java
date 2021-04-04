package chan.http;

import androidx.annotation.NonNull;
import chan.annotation.Public;
import chan.util.StringUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Public
public final class CookieBuilder {
	private ArrayList<String> list = null;

	@Public
	public CookieBuilder() {}

	public CookieBuilder(CookieBuilder builder) {
		append(builder);
	}

	@Public
	public CookieBuilder append(String name, String value) {
		if (!StringUtils.isEmpty(value)) {
			if (list == null) {
				list = new ArrayList<>();
			}
			list.add(name);
			list.add(value);
		}
		return this;
	}

	public CookieBuilder append(String cookie) {
		if (!StringUtils.isEmpty(cookie)) {
			String[] nameValues = cookie.split("; *");
			for (String nameValue : nameValues) {
				int index = nameValue.indexOf("=");
				if (index > 0 && index + 1 < nameValue.length()) {
					String name = nameValue.substring(0, index);
					String value = nameValue.substring(index + 1);
					if (list == null) {
						list = new ArrayList<>();
					}
					list.add(name);
					list.add(value);
				}
			}
		}
		return this;
	}

	public CookieBuilder append(CookieBuilder builder) {
		if (builder != null && builder.list != null && !builder.list.isEmpty()) {
			if (list == null) {
				list = new ArrayList<>();
			}
			list.addAll(builder.list);
		}
		return this;
	}

	public List<String> getKeys() {
		ArrayList<String> list = this.list;
		if (list == null || list.isEmpty()) {
			return Collections.emptyList();
		} else {
			ArrayList<String> keys = new ArrayList<>(list.size() / 2);
			for (int i = 0; i < list.size() / 2; i++) {
				keys.add(list.get(2 * i));
			}
			return keys;
		}
	}

	public boolean isEmpty() {
		return list == null || list.isEmpty();
	}

	@Public
	public String build() {
		ArrayList<String> list = this.list;
		if (list == null || list.isEmpty()) {
			return "";
		} else {
			StringBuilder builder = new StringBuilder();
			for (int i = 0; i < list.size() / 2; i++) {
				if (builder.length() > 0) {
					builder.append("; ");
				}
				builder.append(list.get(2 * i)).append('=').append(list.get(2 * i + 1));
			}
			return builder.toString();
		}
	}

	@NonNull
	@Override
	public String toString() {
		return build();
	}
}
