import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.jetbrains.compose)
}

group = "com.myproxy"
version = providers.gradleProperty("myproxy.versionName").get()

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.jna)
    implementation(libs.jna.platform)

    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "com.myproxy.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi)
            packageName = "MyProxy"
            packageVersion = providers.gradleProperty("myproxy.versionName").get()
            description = "MyProxy desktop client powered by Xray-core"
            vendor = "ByteSparkX"
            copyright = "Copyright 2026 ByteSparkX contributors"
            licenseFile.set(rootProject.file("LICENSE"))
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))
            modules("java.base", "java.desktop", "java.logging", "java.naming", "jdk.unsupported")

            windows {
                iconFile.set(project.file("icons/myproxy.ico"))
                perUserInstall = true
                dirChooser = true
                menuGroup = "MyProxy"
                upgradeUuid = "b85f6671-3e6e-4e3e-9701-4545f235d4aa"
            }

            macOS {
                iconFile.set(project.file("icons/myproxy.icns"))
                bundleID = "com.myproxy.desktop"
                dockName = "MyProxy"
                minimumSystemVersion = "12.0"
            }
        }
    }
}
