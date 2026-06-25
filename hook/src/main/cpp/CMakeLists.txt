# songkit —— 免 root method hook 引擎（LSPlant + Dobby）的 native 产物。
# 与项目 A(third_party/aliuhook/engine) 的唯一区别：lsplant 改为「源码静态嵌入」，
# 不再依赖 prefab 的 liblsplant.so —— 最终注入产物只有 libsongkit.so + libc++_shared.so。
cmake_minimum_required(VERSION 3.28)
project("songkit")

# lsplant 使用 C++23 + C++ Modules；glue 自身只 #include <lsplant.hpp> 不 import，
# 但统一到 C++23 可消除「消费 module-导出 target」时的标准不匹配风险。
set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 去特征：除 JNIEXPORT 标记的导出符号外全部隐藏（含静态内联进来的 lsplant/dobby/ElfImg 内部符号），
# 配合 release strip + version-script，使 `nm -D` 仅见 JNI_OnLoad 与 Java_com_android_engine_HookEngine_*。
set(CMAKE_C_VISIBILITY_PRESET hidden)
set(CMAKE_CXX_VISIBILITY_PRESET hidden)
set(CMAKE_VISIBILITY_INLINES_HIDDEN ON)

# 静态嵌入 lsplant：只编 static 变体（关掉 shared，避免产出 liblsplant.so）。
# 必须在 add_subdirectory 之前以 CACHE 覆盖 lsplant 内部的 option()。
set(LSPLANT_BUILD_SHARED OFF CACHE INTERNAL "" FORCE)
get_filename_component(LSPLANT_JNI
        "${CMAKE_CURRENT_SOURCE_DIR}/../../../../lsplant/src/main/jni" ABSOLUTE)
add_subdirectory(${LSPLANT_JNI} lsplant_build)

# Dobby 走 prefab（静态归档，不产 libdobby.so）。
find_package(dobby REQUIRED CONFIG)

include_directories(xz/linux/lib/xz xz/linux/include/linux xz/userspace)

add_library(songkit SHARED
        aliuhook.cpp
        elf_img.cpp
        profile_saver.cpp
        hidden_api.cpp
        invoke_constructor.cpp
        xz/linux/lib/xz/xz_crc32.c
        xz/linux/lib/xz/xz_crc64.c
        xz/linux/lib/xz/xz_dec_bcj.c
        xz/linux/lib/xz/xz_dec_lzma2.c
        xz/linux/lib/xz/xz_dec_stream.c
)

# lsplant_static 以 PUBLIC 暴露 include/（lsplant.hpp）与 log，链接即得头路径。
target_link_libraries(songkit log lsplant_static dobby::dobby)

# 去特征：只导出 JNI 入口，隐藏一切静态链接进来的第三方符号（Dobby 的 DobbyHook/CodePatch、lsplant::Init/Hook 等）。
# --exclude-libs,ALL 再保险一层，避免静态归档(.a)的符号被提升为导出。
target_link_options(songkit PRIVATE
        "-Wl,--version-script=${CMAKE_CURRENT_SOURCE_DIR}/songkit.map"
        "-Wl,--exclude-libs,ALL")
