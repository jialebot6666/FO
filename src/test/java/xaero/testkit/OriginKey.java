package xaero.testkit;

/**
 * 模仿 {@code net.minecraft.util.Identifier} 的「形状」——真实 Identifier 在生产环境叫
 * {@code net.minecraft.class_2960}，所以桥不能硬编码类名，只能从方法签名反推类型再找工厂。
 * 这个替身的作用就是验证那条推导逻辑，同时故意提供一个 {@code ofVanilla} 干扰项：
 * 如果桥没优先挑 {@code of}，命名空间就会变成 minecraft，测试会立刻发现。
 */
public final class OriginKey {
    private final String namespace;
    private final String path;

    private OriginKey(String namespace, String path) {
        this.namespace = namespace;
        this.path = path;
    }

    public static OriginKey of(String namespace, String path) {
        return new OriginKey(namespace, path);
    }

    public static OriginKey ofVanilla(String namespace, String path) {
        return new OriginKey("minecraft", path);
    }

    public static OriginKey of(String id) {
        int i = id.indexOf(':');
        return i < 0 ? new OriginKey("minecraft", id) : new OriginKey(id.substring(0, i), id.substring(i + 1));
    }

    public static OriginKey tryParse(String id) {
        return of(id);
    }

    public String getNamespace() {
        return namespace;
    }

    public String getPath() {
        return path;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof OriginKey other)) {
            return false;
        }
        return namespace.equals(other.namespace) && path.equals(other.path);
    }

    @Override
    public int hashCode() {
        return namespace.hashCode() * 31 + path.hashCode();
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
