package com.mishiranu.dashchan.util;

import chan.util.StringUtils;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class Hasher {
	private final MessageDigest digest;
	private final byte[] buffer = new byte[8192];

	private Hasher(String algorithm) {
		try {
			digest = MessageDigest.getInstance(algorithm);
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	public byte[] calculate(InputStream inputStream) throws IOException {
		digest.reset();
		int count;
		while ((count = inputStream.read(buffer, 0, buffer.length)) >= 0) {
			digest.update(buffer, 0, count);
		}
		return digest.digest();
	}

	public byte[] calculate(byte[] bytes) {
		digest.reset();
		return digest.digest(bytes);
	}

	public byte[] calculate(String string) {
		return calculate(StringUtils.emptyIfNull(string).getBytes());
	}

	private static final ThreadLocal<Hasher> HASHER_SHA_256 = new ThreadLocal<Hasher>() {
		@Override
		protected Hasher initialValue() {
			return new Hasher("SHA-256");
		}
	};

	public static Hasher getInstanceSha256() {
		return HASHER_SHA_256.get();
	}
}
