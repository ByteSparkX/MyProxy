package com.myproxy.desktop.data

import com.myproxy.desktop.model.DesktopState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission

class StateStore {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun load(): DesktopState {
        val file = DesktopPaths.stateFile
        if (!Files.isRegularFile(file)) return DesktopState()
        return runCatching { json.decodeFromString<DesktopState>(Files.readString(file)) }
            .getOrDefault(DesktopState())
    }

    @Synchronized
    fun save(state: DesktopState) {
        val target = DesktopPaths.stateFile
        val temporary = target.resolveSibling("${target.fileName}.tmp")
        Files.writeString(temporary, json.encodeToString(state))
        restrictToCurrentUser(temporary)
        runCatching {
            Files.move(
                temporary,
                target,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        }.getOrElse {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
        }
        restrictToCurrentUser(target)
    }

    private fun restrictToCurrentUser(path: java.nio.file.Path) {
        runCatching {
            Files.setPosixFilePermissions(
                path,
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        }
    }
}
