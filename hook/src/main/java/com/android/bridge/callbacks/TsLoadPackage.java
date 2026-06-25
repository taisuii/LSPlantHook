package com.android.bridge.callbacks;

import android.content.pm.ApplicationInfo;

import com.android.bridge.ILoadPackageHook;
import com.android.bridge.TsBridge.CopyOnWriteSortedSet;

/**
 * This class is only used for internal purposes, except for the {@link LoadPackageParam}
 * subclass.
 */
public abstract class TsLoadPackage extends BridgeCallback implements ILoadPackageHook {
	/**
	 * Creates a new callback with default priority.
	 * @hide
	 */
	@SuppressWarnings("deprecation")
	public TsLoadPackage() {
		super();
	}

	/**
	 * Creates a new callback with a specific priority.
	 *
	 * @param priority See {@link BridgeCallback#priority}.
	 * @hide
	 */
	public TsLoadPackage(int priority) {
		super(priority);
	}

	/**
	 * Wraps information about the app being loaded.
	 */
	public static final class LoadPackageParam extends Param {
		/** @hide */
		public LoadPackageParam(CopyOnWriteSortedSet<TsLoadPackage> callbacks) {
			super(callbacks);
		}

		/** The name of the package being loaded. */
		public String packageName;

		/** The process in which the package is executed. */
		public String processName;

		/** The ClassLoader used for this package. */
		public ClassLoader classLoader;

		/** More information about the application being loaded. */
		public ApplicationInfo appInfo;

		/** Set to {@code true} if this is the first (and main) application for this process. */
		public boolean isFirstApplication;
	}

	/** @hide */
	@Override
	protected void call(Param param) throws Throwable {
		if (param instanceof LoadPackageParam)
			handleLoadPackage((LoadPackageParam) param);
	}
}
