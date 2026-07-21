package com.myproxy.app.core

import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import libv2ray.ProcessFinder

@Suppress("unused")
internal object Libv2rayApiNotes {
    // 真实导出包名来自 app/libs/libv2ray.aar 中的 classes.jar：libv2ray。
    const val EXPORTED_PACKAGE = "libv2ray"

    // 当前 AAR 内置 native 库为 libgojni.so；项目只打包 ARM ABI，避免引入桌面模拟器 ABI。
    val nativeLibraries = listOf(
        "jni/arm64-v8a/libgojni.so",
        "jni/armeabi-v7a/libgojni.so",
        "jni/x86/libgojni.so",
        "jni/x86_64/libgojni.so",
    )

    // 当前 AAR 不存在 protect/setup/prepare 接口；后续只应调用下列真实导出方法。
    const val PROTECT_CALLBACK_SIGNATURE = "当前 libv2ray.aar 未导出 protect 回调"
    val callbackMethods = listOf("startup(): Long", "shutdown(): Long", "onEmitStatus(Long, String): Long")
    val processFinderMethods = listOf(
        "findProcessByConnection(String, String, Long, String, Long): Long",
    )
    val libv2rayMethods = listOf(
        "Libv2ray.touch()",
        "Libv2ray.checkVersionX(): String",
        "Libv2ray.fetchQuicCertSha256(String): String",
        "Libv2ray.fetchTlsCertSha256(String): String",
        "Libv2ray.initCoreEnv(String, String)",
        "Libv2ray.measureOutboundDelay(String, String): Long",
        "Libv2ray.newCoreController(CoreCallbackHandler): CoreController",
        "Libv2ray.reconcileBrowserDialer(String)",
    )
    val controllerMethods = listOf(
        "CoreController(CoreCallbackHandler)",
        "CoreController.getCallbackHandler(): CoreCallbackHandler",
        "CoreController.setCallbackHandler(CoreCallbackHandler)",
        "CoreController.getIsRunning(): Boolean",
        "CoreController.setIsRunning(Boolean)",
        "CoreController.measureDelay(String): Long",
        "CoreController.queryAllOutboundTrafficStats(): String",
        "CoreController.queryStats(String, String): Long",
        "CoreController.registerProcessFinder(ProcessFinder)",
        "CoreController.startLoop(String, Int)",
        "CoreController.stopLoop()",
    )

    // 编译期引用真实类型，避免后续接入时凭空猜测 AAR API。
    val exportedTypes = listOf(
        Libv2ray::class.java.name,
        CoreController::class.java.name,
        CoreCallbackHandler::class.java.name,
        ProcessFinder::class.java.name,
    )
}
