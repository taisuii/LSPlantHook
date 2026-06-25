pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()   // io.github.vvb2060.ndk:dobby
    }
}

rootProject.name = "LSPlantHook"

// 唯一消费模块：产出 AAR（com.android.bridge/engine + libsongkit.so）。
// lsplant 源码不作为独立 Gradle 模块，由 :hook 的 CMake add_subdirectory 静态编入。
include(":hook")
