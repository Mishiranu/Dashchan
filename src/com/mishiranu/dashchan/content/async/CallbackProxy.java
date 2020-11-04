package com.mishiranu.dashchan.content.async;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class CallbackProxy<Callback> {
	public interface Handler<Callback> {
		void handle(CallbackProxy<Callback> proxy);
	}

	private final Method method;
	private final Object[] args;

	private CallbackProxy(Method method, Object[] args) {
		this.method = method;
		this.args = args;
	}

	public void invoke(Callback callback) {
		try {
			method.invoke(callback, args);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		} catch (InvocationTargetException e) {
			Throwable target = e.getTargetException();
			if (target instanceof RuntimeException) {
				throw (RuntimeException) target;
			} else if (target instanceof Error) {
				throw (Error) target;
			} else {
				throw new RuntimeException(target);
			}
		}
	}

	public static <Callback> Callback create(Class<Callback> callbackClass, Handler<Callback> handler) {
		Class<?>[] instances = {callbackClass};
		InvocationHandler invocationHandler = (proxy, method, args) -> {
			switch (method.getName()) {
				case "equals": {
					return args != null && args.length == 1 && args[0] == proxy;
				}
				case "hashCode": {
					return handler.hashCode();
				}
				case "toString": {
					return handler.toString();
				}
				default: {
					handler.handle(new CallbackProxy<>(method, args));
					return null;
				}
			}
		};
		@SuppressWarnings("unchecked")
		Callback callback = (Callback) Proxy.newProxyInstance
				(callbackClass.getClassLoader(), instances, invocationHandler);
		return callback;
	}
}
