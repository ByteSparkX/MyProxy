package hev.sockstun;

/**
 * HevSocks5Tunnel 的 JNI 桥接类。
 *
 * <p>上游 native 库使用包名 hev.sockstun 和类名 TProxyService 编译 JNI 符号，
 * 因此这里保留同名类，只暴露给本项目的 Tun2SocksManager 调用。</p>
 */
public final class TProxyService {
    static {
        System.loadLibrary("hev-socks5-tunnel");
    }

    private TProxyService() {
    }

    private static native void TProxyStartService(String configPath, int fd);

    private static native void TProxyStopService();

    private static native long[] TProxyGetStats();

    public static void startService(String configPath, int fd) {
        TProxyStartService(configPath, fd);
    }

    public static void stopService() {
        TProxyStopService();
    }

    public static long[] getStats() {
        return TProxyGetStats();
    }
}
