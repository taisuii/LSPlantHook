// LSPlantHook —— 可复用、防检测的 Android method hook 库。
//   Java：com.android.bridge.*（Xposed 风格门面 API）→ com.android.engine.*（LSPlant 引擎）
//   Native：libsongkit.so（lsplant 源码静态嵌入 + Dobby inline hook，已去特征）
// 单一 AAR，消费方只需 libsongkit.so + libc++_shared.so（无 liblsplant.so）。
plugins {
    alias(libs.plugins.agp.lib)
    alias(libs.plugins.lsplugin.cmaker)
}

val androidBuildToolsVersion: String by rootProject.extra
val androidCompileSdkVersion: Int by rootProject.extra
val androidNdkVersion: String by rootProject.extra
val androidCmakeVersion: String by rootProject.extra

android {
    namespace = "com.android.hook"
    compileSdk = androidCompileSdkVersion
    buildToolsVersion = androidBuildToolsVersion
    ndkVersion = androidNdkVersion

    defaultConfig {
        minSdk = 24
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildFeatures {
        prefab = true        // Dobby 以 prefab CONFIG 包引入
        buildConfig = false
        androidResources = false
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = androidCmakeVersion   // cmaker 插件据此自动拉取 cmake 4.0+
        }
    }

    sourceSets {
        getByName("main") {
            // TsHelpers 依赖的精简版 apache commons-lang（external.org.apache.commons.lang3.*）
            java.srcDirs("src/main/apacheCommonsLang")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

cmaker {
    default {
        abiFilters("arm64-v8a", "armeabi-v7a")
    }
    buildTypes {
        arguments += "-DANDROID_STL=c++_shared"
        arguments += "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
    }
}

dependencies {
    implementation(libs.dobby)
}
