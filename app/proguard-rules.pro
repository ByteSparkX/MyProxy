# Xray-core Android AAR.
# app/libs/libv2ray.aar/classes.jar exports packages "go" and "libv2ray".
# Go mobile bridge classes are called from native libgojni.so and must keep names.
-keep class go.** { *; }
-keep class libv2ray.Libv2ray { *; }
-keep class libv2ray.CoreController { *; }
-keep interface libv2ray.CoreCallbackHandler { *; }
-keep interface libv2ray.ProcessFinder { *; }
-keep class libv2ray.Libv2ray$proxyCoreCallbackHandler { *; }
-keep class libv2ray.Libv2ray$proxyProcessFinder { *; }

# Project callback implementations passed into libv2ray.
-keep class com.myproxy.app.core.XrayCoreCallback { *; }
-keep class com.myproxy.app.core.XrayProcessFinderStub { *; }

# tun2socks JNI symbols are bound to hev.sockstun.TProxyService.
-keep class hev.sockstun.TProxyService { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# kotlinx.serialization models used for node data.
-keep @kotlinx.serialization.Serializable class ** { *; }
-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room persistence classes used by generated implementations.
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.TypeConverters class * { *; }
-keep class com.myproxy.app.model.ProxyNode { *; }
-keep class com.myproxy.app.model.ProtocolType { *; }
-keep class com.myproxy.app.data.NodeDao { *; }
-keep class com.myproxy.app.data.AppDatabase { *; }
-keep class com.myproxy.app.data.Converters { *; }

# Android components are instantiated by framework names from AndroidManifest.xml.
-keep class com.myproxy.app.MainActivity { *; }
-keep class com.myproxy.app.service.MyVpnService { *; }
-keep class com.myproxy.app.service.BootReceiver { *; }
