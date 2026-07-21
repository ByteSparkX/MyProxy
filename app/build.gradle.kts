plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val appVersionCode = providers.gradleProperty("myproxy.versionCode").map(String::toInt).get()
val appVersionName = providers.gradleProperty("myproxy.versionName").get()
val releaseSigningEnvNames = listOf(
    "KEYSTORE_FILE",
    "KEYSTORE_PASSWORD",
    "KEY_ALIAS",
    "KEY_PASSWORD",
)

fun releaseSigningEnv(name: String): String? {
    return providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }
}

fun validateReleaseSigningEnvironment() {
    val missingKeys = releaseSigningEnvNames.filter { releaseSigningEnv(it) == null }
    if (missingKeys.isNotEmpty()) {
        throw GradleException(
            "Release 签名环境变量缺失：${missingKeys.joinToString()}. " +
                "请在 GitHub Actions 中由 KEYSTORE_BASE64 生成 KEYSTORE_FILE，并设置 KEYSTORE_PASSWORD、KEY_ALIAS、KEY_PASSWORD。",
        )
    }

    val keystoreFile = file(requireNotNull(releaseSigningEnv("KEYSTORE_FILE")))
    if (!keystoreFile.isFile) {
        throw GradleException("Release 签名文件不存在：KEYSTORE_FILE=${keystoreFile.absolutePath}")
    }
}

android {
    namespace = "com.myproxy.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.myproxy.app"
        minSdk = 24
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    signingConfigs {
        create("release") {
            val keystorePath = releaseSigningEnv("KEYSTORE_FILE")
            val keystorePassword = releaseSigningEnv("KEYSTORE_PASSWORD")
            val alias = releaseSigningEnv("KEY_ALIAS")
            val keyPass = releaseSigningEnv("KEY_PASSWORD")

            if (
                keystorePath != null &&
                keystorePassword != null &&
                alias != null &&
                keyPass != null
            ) {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                keyAlias = alias
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            buildConfigField("Boolean", "ENABLE_DEBUG_ENTRY", "true")
            buildConfigField("Boolean", "VERBOSE_LOGGING", "true")
        }

        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("Boolean", "ENABLE_DEBUG_ENTRY", "false")
            buildConfigField("Boolean", "VERBOSE_LOGGING", "false")
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
            )
        }
    }

    lint {
        // 当前 lifecycle lint 与 Kotlin 2.0/AGP 8.7 的 UAST 分析存在崩溃，先禁用该单项规则。
        disable += "NullSafeMutableLiveData"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

}

dependencies {
    implementation(files("libs/libv2ray.aar"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.okhttp)

    ksp(libs.androidx.room.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

val validateReleaseSigningEnvironmentTask = tasks.register("validateReleaseSigningEnvironment") {
    group = "verification"
    description = "Validate release signing environment variables."

    doLast {
        validateReleaseSigningEnvironment()
    }
}

tasks.matching { task ->
    task.name == "preReleaseBuild"
}.configureEach {
    dependsOn(validateReleaseSigningEnvironmentTask)
}
