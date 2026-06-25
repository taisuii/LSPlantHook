package com.android.bridge;

import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.android.engine.HookEngine;
import com.android.engine.HookEntry;

/**
 * 业务侧统一 hook 门面（包名/类名与原 Tine 版完全一致，业务代码零改动）。
 *
 * <p>底层引擎已从 Tine 切换为 LSPlant（{@link HookEngine} / libsongkit.so）。本类把业务的
 * {@link TsMethodHook} 回调适配成引擎的 {@link HookEntry} 回调后转发，多回调/嵌套调用由
 * 每个适配器的 ThreadLocal 栈保证 before/after 配平与递归正确。
 */
public final class TsBridge {

    /**
     * The system class loader which can be used to locate Android framework classes.
     * Application classes cannot be retrieved from it.
     */
    public static final ClassLoader BOOTCLASSLOADER = ClassLoader.getSystemClassLoader();

    /** @hide */
    public static final String TAG = "songkit-bridge";

    private static final Object[] EMPTY_ARRAY = new Object[0];

    /** @deprecated Use {@link #getBridgeVersion()} instead. */
    @Deprecated
    public static int BRIDGE_VERSION = 90;

    // 全局开关：置 true 时所有 hook 回调短路（保留 before/after 配平）。业务默认不使用。
    public static volatile boolean disableHooks = false;

    // 一个 TsMethodHook 复用一个适配器（hookAllMethods 会用同一个 callback 命中多个方法）。
    private static final Map<TsMethodHook, EngineAdapter> adapters = new HashMap<>();

    private TsBridge() {}

    /**
     * 触发底层引擎（LSPlant / libsongkit.so）加载并做早期配置：
     * 首次引用 {@link HookEngine} 即触发其静态块 System.loadLibrary("songkit") + JNI_OnLoad(LSPlant init)；
     * 随后解除 hidden API 限制 + 关闭 profile saver（减少 AOT 内联导致目标方法不可 hook）。
     * 必须在安装任何业务 hook 之前调用一次。
     */
    public static void initEngine() {
        HookEngine.disableHiddenApiRestrictions();
        HookEngine.disableProfileSaver();
    }

    /** Returns the currently installed bridge version. */
    public static int getBridgeVersion() {
        return BRIDGE_VERSION;
    }

    public static void setBridgeVersion(int version) {
        BRIDGE_VERSION = version;
    }

    /** Writes a message to the log. <b>DON'T FLOOD THE LOG!!!</b> */
    public static synchronized void log(String text) {
        Log.i(TAG, text);
    }

    /** Logs a stack trace. <b>DON'T FLOOD THE LOG!!!</b> */
    public static synchronized void log(Throwable t) {
        Log.e(TAG, Log.getStackTraceString(t));
    }

    /**
     * Deoptimize a method to avoid callee being inlined.
     *
     * @param method The method to deoptimize. Generally it should be a caller of a method that is inlined.
     */
    public static void deoptimizeMethod(Member method) {
        HookEngine.deoptimizeMethod(method);
    }

    /**
     * Hook any method (or constructor) with the specified callback.
     *
     * @param hookMethod The method to be hooked.
     * @param callback The callback to be executed when the hooked method is called.
     * @return An object that can be used to remove the hook.
     */
    public static TsMethodHook.Unhook hookMethod(Member hookMethod, TsMethodHook callback) {
        if (!(hookMethod instanceof Method) && !(hookMethod instanceof Constructor<?>)) {
            throw new IllegalArgumentException("Only methods and constructors can be hooked: " + hookMethod.toString());
        } else if (Modifier.isAbstract(hookMethod.getModifiers())) {
            throw new IllegalArgumentException("Cannot hook abstract methods: " + hookMethod.toString());
        }

        EngineAdapter adapter;
        synchronized (adapters) {
            adapter = adapters.get(callback);
            if (adapter == null) {
                adapter = new EngineAdapter(callback);
                adapters.put(callback, adapter);
            }
        }

        HookEngine.hookMethod(hookMethod, adapter);
        return callback.new Unhook(hookMethod);
    }

    /**
     * Removes the callback for a hooked method/constructor.
     *
     * @deprecated Use {@link TsMethodHook.Unhook#unhook} instead.
     */
    @Deprecated
    public static void unhookMethod(Member hookMethod, TsMethodHook callback) {
        EngineAdapter adapter;
        synchronized (adapters) {
            adapter = adapters.get(callback);
        }
        if (adapter != null) {
            HookEngine.unhookMethod(hookMethod, adapter);
        }
    }

    /**
     * Hooks all methods with a certain name that were declared in the specified class.
     */
    @SuppressWarnings("UnusedReturnValue")
    public static Set<TsMethodHook.Unhook> hookAllMethods(Class<?> hookClass, String methodName, TsMethodHook callback) {
        Set<TsMethodHook.Unhook> unhooks = new HashSet<>();
        for (Member method : hookClass.getDeclaredMethods())
            if (method.getName().equals(methodName))
                unhooks.add(hookMethod(method, callback));
        return unhooks;
    }

    /**
     * Hook all constructors of the specified class.
     */
    @SuppressWarnings("UnusedReturnValue")
    public static Set<TsMethodHook.Unhook> hookAllConstructors(Class<?> hookClass, TsMethodHook callback) {
        Set<TsMethodHook.Unhook> unhooks = new HashSet<>();
        for (Member constructor : hookClass.getDeclaredConstructors())
            unhooks.add(hookMethod(constructor, callback));
        return unhooks;
    }

    /**
     * Calls the original method as it was before the interception. Access permissions are not checked.
     */
    public static Object invokeOriginalMethod(Member method, Object thisObject, Object[] args)
            throws NullPointerException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        return HookEngine.invokeOriginalMethod(method, thisObject, args);
    }

    /**
     * 适配器：把引擎的 {@link HookEntry} 回调桥接到业务的 {@link TsMethodHook}。
     * 每次调用在 before 入栈一个 {@link MethodHookParam}，after 出栈，保证嵌套/多回调下
     * before/after 一一对应，并把字段（thisObject/args/result/throwable/returnEarly）在两侧同步。
     */
    private static final class EngineAdapter extends HookEntry {
        private final TsMethodHook delegate;
        private final ThreadLocal<ArrayDeque<TsMethodHook.MethodHookParam>> stack =
                ThreadLocal.withInitial(ArrayDeque::new);

        EngineAdapter(TsMethodHook delegate) {
            super(delegate.priority);
            this.delegate = delegate;
        }

        @Override
        protected void beforeHookedMethod(HookEntry.MethodHookParam ep) throws Throwable {
            TsMethodHook.MethodHookParam tp = new TsMethodHook.MethodHookParam();
            tp.method = ep.method;
            tp.thisObject = ep.thisObject;
            tp.args = ep.args;
            stack.get().push(tp);

            if (disableHooks) return;

            try {
                delegate.beforeHookedMethod(tp);
            } finally {
                ep.thisObject = tp.thisObject;
                ep.args = tp.args;
                // 仅当业务确实提前返回时才回灌引擎 param（否则会误触发跳过原方法）
                if (tp.hasThrowable()) ep.setThrowable(tp.getThrowable());
                else if (tp.returnEarly) ep.setResult(tp.getResult());
            }
        }

        @Override
        protected void afterHookedMethod(HookEntry.MethodHookParam ep) throws Throwable {
            TsMethodHook.MethodHookParam tp = stack.get().poll();
            if (disableHooks || tp == null) return;

            tp.thisObject = ep.thisObject;
            tp.args = ep.args;
            if (ep.hasThrowable()) tp.setThrowable(ep.getThrowable());
            else tp.setResult(ep.getResult());

            try {
                delegate.afterHookedMethod(tp);
            } finally {
                ep.thisObject = tp.thisObject;
                ep.args = tp.args;
                if (tp.hasThrowable()) ep.setThrowable(tp.getThrowable());
                else ep.setResult(tp.getResult());
            }
        }
    }

    /** @hide 供 {@link com.android.bridge.callbacks.TsLoadPackage.LoadPackageParam} 等使用。 */
    public static final class CopyOnWriteSortedSet<E> {
        private transient volatile Object[] elements = EMPTY_ARRAY;

        @SuppressWarnings("UnusedReturnValue")
        public synchronized boolean add(E e) {
            int index = indexOf(e);
            if (index >= 0)
                return false;

            Object[] newElements = new Object[elements.length + 1];
            System.arraycopy(elements, 0, newElements, 0, elements.length);
            newElements[elements.length] = e;
            java.util.Arrays.sort(newElements);
            elements = newElements;
            return true;
        }

        @SuppressWarnings("UnusedReturnValue")
        public synchronized boolean remove(E e) {
            int index = indexOf(e);
            if (index == -1)
                return false;

            Object[] newElements = new Object[elements.length - 1];
            System.arraycopy(elements, 0, newElements, 0, index);
            System.arraycopy(elements, index + 1, newElements, index, elements.length - index - 1);
            elements = newElements;
            return true;
        }

        private int indexOf(Object o) {
            for (int i = 0; i < elements.length; i++) {
                if (o.equals(elements[i]))
                    return i;
            }
            return -1;
        }

        public Object[] getSnapshot() {
            return elements;
        }
    }
}
