package com.myproxy.desktop.data

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object DesktopPaths {
    val dataDirectory: Path by lazy {
        val os = System.getProperty("os.name").lowercase()
        val path = when {
            os.contains("win") -> {
                val appData = System.getenv("APPDATA")?.takeIf(String::isNotBlank)
                    ?: System.getProperty("user.home")
                Paths.get(appData, "MyProxy")
            }
            os.contains("mac") -> Paths.get(
                System.getProperty("user.home"),
                "Library",
                "Application Support",
                "MyProxy",
            )
            else -> Paths.get(System.getProperty("user.home"), ".local", "share", "MyProxy")
        }
        Files.createDirectories(path)
        path
    }

    val stateFile: Path get() = dataDirectory.resolve("desktop-state.json")
    val coreConfigFile: Path get() = dataDirectory.resolve("xray-config.json")
    val proxySnapshotFile: Path get() = dataDirectory.resolve("system-proxy-snapshot.json")
}
