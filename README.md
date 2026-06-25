# LSPlantHook

![license](https://img.shields.io/badge/license-LGPL--3.0-orange.svg)
![android](https://img.shields.io/badge/Android-7.0%20--%2015-blue.svg)
![abi](https://img.shields.io/badge/abi-arm64--v8a%20%7C%20armeabi--v7a-brightgreen.svg)

**可复用、防检测的 Android method hook 库** = 源码级去特征的 [LSPlant](https://github.com/LSPosed/LSPlant)（静态嵌入）+ Xposed 风格的 method hook API。

为反检测场景设计：把 LSPlant 从源码静态编进单一 `libsongkit.so`，去除一切对外可检测的引擎指纹（库名 / 导出符号 / log tag / 运行时生成类名等），对接方拿到的只是一个普通命名的 hook 库。

---

## 这是什么

- **底层引擎**：LSPlant（ART method hook，基于替换 ArtMethod entrypoint，**不依赖 moving-GC 可见对象**，在 Android 15 上稳定，规避了 Pine/Tine 系框架的 moving-GC 崩溃）。inline hook 用 [Dobby](https://github.com/jmpews/Dobby)。
- **门面 API**：`com.android.bridge.*`，与经典 Xposed `XposedHelpers` / `XC_MethodHook` 同形，迁移成本接近零。
- **去特征**：见下文「去特征清单」。产物里搜不到 `lsplant` / `LSPHooker` / `LSPlant` 等特征串，导出符号只有中性 JNI 入口。

> 适用于注入式免 root 方案（自行解决加载与 hook 安装时机，本库不含注入器）。

---

## 产物

`:hook:assembleRelease` 产出单个 AAR，内含：

| 文件 | 说明 |
|------|------|
| `classes.jar` | `com.android.bridge.*`（门面）+ `com.android.engine.*`（引擎）+ 精简 commons-lang |
| `libsongkit.so` | 静态嵌入 lsplant + dobby，**无 `liblsplant.so`** |

运行期注入只需 **2 个 so**：`libsongkit.so` + `libc++_shared.so`（NDK STL）。

---

## 接入

### 1) 取 AAR

从 [Releases](../../releases) 下载 `LSPlantHook-release.aar`（push `v*` tag 由 CI 自动发布），放进你工程的 `libs/`：

```kotlin
dependencies {
    implementation(files("libs/LSPlantHook-release.aar"))
}
```

AAR 已带 `libsongkit.so`（arm64-v8a / armeabi-v7a）。确保打包时附带 `libc++_shared.so`（用 `c++_shared` STL 的工程自动具备）。

### 2) 初始化引擎（进程内尽早，需可访问 hidden API 的环境）

```java
import com.android.bridge.TsBridge;

TsBridge.initEngine();   // 加载 libsongkit.so + LSPlant init + 关闭 hidden API 限制 / profile saver
```

### 3) Hook 一个方法（Xposed 风格）

```java
import com.android.bridge.TsHelpers;
import com.android.bridge.TsMethodHook;

TsHelpers.findAndHookMethod(
    "com.target.app.Foo", classLoader, "bar",
    String.class, int.class,           // 参数类型
    new TsMethodHook() {               // 回调
        @Override protected void beforeHookedMethod(MethodHookParam param) {
            // param.thisObject / param.args / param.setResult(...) / param.method
            param.args[1] = 42;
        }
        @Override protected void afterHookedMethod(MethodHookParam param) {
            Object ret = param.getResult();
        }
    });
```

### 常用 API

- `TsBridge.initEngine()` — 初始化引擎（幂等）
- `TsBridge.hookMethod(Member, TsMethodHook)` / `unhookMethod(...)` — 直接对 `Member` 下钩
- `TsBridge.hookAllMethods(Class, name, cb)` / `hookAllConstructors(Class, cb)`
- `TsBridge.invokeOriginalMethod(Member, thisObj, args)` — 调原方法
- `TsBridge.deoptimizeMethod(Member)` — 反内联（短方法被内联导致 hook 不触发时用）
- `TsHelpers.findAndHookMethod(...)` / `findAndHookConstructor(...)`
- `TsHelpers.findClass / findMethodExact / findField / setObjectField ...` — Xposed 同名工具

回调对象 `TsMethodHook` 覆写 `beforeHookedMethod` / `afterHookedMethod`；`MethodHookParam` 提供 `method` / `thisObject` / `args` / `getResult` / `setResult` / `getThrowable` / `setThrowable`。

---

## 去特征清单

只针对**产物可检测项**（源码内部 namespace / 许可证注释保留，不影响合规且不进可检测面）：

| 项 | 原 | 现 |
|----|----|----|
| native 库名 | `liblsplant.so`（独立） | 静态嵌入，仅 `libsongkit.so` |
| 导出符号 | lsplant `Init/Hook/...` 默认导出 | version-script 仅留 `JNI_OnLoad` + `Java_com_android_engine_HookEngine_*`，`--exclude-libs,ALL` |
| 运行时生成类名 | `LSPHooker_` | 中性单字母（头默认 + glue InitInfo 双重覆盖） |
| 生成字段 / 源名 | `hooker` / `LSP` / `lsplant` | 中性 |
| native log tag | `LSPlant` | 中性 |
| ART suspend 标签 | `LSPlant Hook` | 中性 |
| JNI 符号 | `Java_de_robv_..._XposedBridge_*` | `Java_com_android_engine_HookEngine_*` |

可见性默认 hidden + release strip：`nm -D libsongkit.so` 仅见中性 JNI 入口。

---

## 构建

> ⚠️ LSPlant 使用 **C++23 + C++ Modules**，需 CMake ≥ 3.28 + Ninja + 新版 clang。**Windows 原生构建 modules 易失败**，请用 **GitHub Actions（ubuntu）** 或 Linux/WSL。

工具链（见 `gradle/libs.versions.toml` 与 `.github/workflows/release.yml`）：

- Gradle 8.14.2 / AGP 8.9.3 / JDK 21
- NDK r29-beta2（`ndk;29.0.13599879`）/ CMake 4.0.2（`org.lsposed.lsplugin.cmaker` 插件）
- Ninja 1.12.1（modules 扫描必需）

CI：

- push 任意分支 / PR / 手动 → 构建校验 `:hook:assembleRelease`，AAR 作为 artifact 上传
- push `v*` tag → 额外发 GitHub Release（附 `LSPlantHook-release.aar`）

本地（Linux/WSL，工具链就绪时）：

```bash
./gradlew :hook:assembleRelease
# 产物：hook/build/outputs/aar/hook-release.aar
```

---

## 工程结构

```
LSPlantHook/
├── hook/                         唯一消费模块 → AAR
│   ├── build.gradle.kts          agp.lib + cmaker；prefab dobby；minSdk 24
│   └── src/main/
│       ├── cpp/                  glue：aliuhook.cpp(JNI) / elf_img / hidden_api / profile_saver
│       │   ├── CMakeLists.txt    add_subdirectory(lsplant) 静态链 lsplant_static + dobby
│       │   └── songkit.map       version-script（只导出中性 JNI）
│       ├── java/com/android/     bridge（门面）+ engine（LSPlant 适配）
│       └── apacheCommonsLang/    TsHelpers 依赖的精简 commons-lang
└── lsplant/src/main/jni/         LSPlant 源码（已去特征）；非独立 Gradle 模块，
                                  由 hook 的 CMake add_subdirectory 直接编静态
```

调用链：`你的代码 → com.android.bridge.* → com.android.engine.HookEngine → libsongkit.so(LSPlant + Dobby)`。

---

## 致谢与许可

- [LSPlant](https://github.com/LSPosed/LSPlant) / [Aliucord/LSPlant fork](https://github.com/Aliucord/LSPlant) — LGPL-3.0（见 [LICENSE](LICENSE)）
- [AliuHook](https://github.com/Aliucord/AliuHook) — Xposed→LSPlant 绑定层来源（OSL-3.0），glue 与门面思路移植自此
- [Dobby](https://github.com/jmpews/Dobby) — inline hook
- [DexBuilder](https://github.com/LSPosed/DexBuilder) / [parallel-hashmap](https://github.com/greg7mdp/parallel-hashmap) — lsplant 依赖

本库为 LSPlant 的衍生作品，遵循 **LGPL-3.0**。源码内的上游版权 / 许可注释均予保留。
