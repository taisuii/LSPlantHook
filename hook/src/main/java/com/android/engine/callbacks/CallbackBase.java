/*
 * This file is part of AliuHook, a library providing XposedAPI bindings to LSPlant
 * Copyright (c) 2021 Juby210 & Vendicated
 * Licensed under the Open Software License version 3.0
 *
 * Originally written by rovo89 as part of the original Xposed
 * Copyright 2013 rovo89, Tungstwenty
 * Licensed under the Apache License, Version 2.0, see http://www.apache.org/licenses/LICENSE-2.0
 */

package com.android.engine.callbacks;

/**
 * Base class for hook callbacks. Keeps a priority for ordering multiple callbacks.
 * The actual (abstract) callback methods are added by subclasses.
 */
@SuppressWarnings({"unused", "JavaDoc"})
public abstract class CallbackBase implements Comparable<CallbackBase> {
    /**
     * Callback priority, higher number means earlier execution.
     */
    public final int priority;

    /**
     * @deprecated This constructor can't be hidden for technical reasons. Nevertheless, don't use it!
     */
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated
    public CallbackBase() {
        this.priority = PRIORITY_DEFAULT;
    }

    /** @hide */
    public CallbackBase(int priority) {
        this.priority = priority;
    }

    /** Base class for callback parameters. */
    public static abstract class Param {
        protected Param() {
        }
    }

    /** @hide */
    @Override
    public int compareTo(CallbackBase other) {
        if (this == other)
            return 0;

        // order descending by priority
        if (other.priority != this.priority)
            return other.priority - this.priority;
            // then randomly
        else if (System.identityHashCode(this) < System.identityHashCode(other))
            return -1;
        else
            return 1;
    }

    /** The default priority. */
    public static final int PRIORITY_DEFAULT = 50;

    /** Execute this callback late. */
    public static final int PRIORITY_LOWEST = -10000;

    /** Execute this callback early. */
    public static final int PRIORITY_HIGHEST = 10000;
}
