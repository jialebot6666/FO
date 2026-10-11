package com.fo.addon.ancient.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 古城结构数据源抽象（纯逻辑，可单测）。
 *
 * <p>原始实现的加载器直接吃 {@code Path} 目录，但 mod 运行时结构数据在 <b>jar 内的 classpath</b>，
 * 没有真实目录。这层抽象把「从哪读字节」与「怎么解析」解耦，避免运行时解压临时目录：
 * <ul>
 *   <li>{@link Directory} —— 测试或已解压目录用，保留原有 {@code Path} 语义；</li>
 *   <li>{@link Classpath} —— mod 运行时从 jar 内读，走 {@code ClassLoader.getResourceAsStream}。</li>
 * </ul>
 *
 * <p>{@link #id()} 参与缓存键：不同数据源下的同名资源不得互相串用。
 */
public interface AncientResourceSource {

    /** mod 内古城结构数据的资源根。 */
    String STRUCTURE_PREFIX = "fo/ancient/structure/";

    /** mod 内拼图池数据的资源根。 */
    String TEMPLATE_POOL_PREFIX = "fo/ancient/template_pool/";

    /** mod 内古城全部数据（结构 / 拼图池 / 战利品表 / 附魔 / 标签）的资源根。 */
    String ANCIENT_PREFIX = "fo/ancient/";

    /** 数据源身份，用于缓存键隔离。 */
    String id();

    /** 按相对路径打开资源；不存在返回 {@code null}。 */
    InputStream open(String relativePath) throws IOException;

    /** 目录数据源。 */
    static AncientResourceSource directory(Path root) {
        return new Directory(root);
    }

    /** classpath 数据源。 */
    static AncientResourceSource classpath(ClassLoader loader, String prefix) {
        return new Classpath(loader, prefix);
    }

    /** mod 内置的古城结构模板数据源（jar 内 {@code fo/ancient/structure/}）。 */
    static AncientResourceSource bundledStructures() {
        return classpath(AncientResourceSource.class.getClassLoader(), STRUCTURE_PREFIX);
    }

    /** mod 内置的拼图池数据源（jar 内 {@code fo/ancient/template_pool/}）。 */
    static AncientResourceSource bundledPools() {
        return classpath(AncientResourceSource.class.getClassLoader(), TEMPLATE_POOL_PREFIX);
    }

    /**
     * mod 内置的古城全量数据源（jar 内 {@code fo/ancient/}）。
     *
     * <p>相对路径形如 {@code loot_table/chests/ancient_city.json}、
     * {@code enchantment/swift_sneak.json}、{@code tags/enchantment/on_random_loot.json}、
     * {@code tags/item/hoes.json}、{@code index.json}。
     */
    static AncientResourceSource bundledAncient() {
        return classpath(AncientResourceSource.class.getClassLoader(), ANCIENT_PREFIX);
    }

    /** 读一个 UTF-8 文本资源；不存在返回 {@code null}。 */
    default String readText(String relativePath) throws IOException {
        try (InputStream in = open(relativePath)) {
            if (in == null) return null;
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    /** 目录实现。 */
    final class Directory implements AncientResourceSource {
        private final Path root;

        public Directory(Path root) {
            this.root = root;
        }

        public Path root() {
            return root;
        }

        @Override
        public String id() {
            return "dir:" + root.toAbsolutePath().normalize();
        }

        @Override
        public InputStream open(String relativePath) throws IOException {
            Path path = root.resolve(relativePath);
            return Files.exists(path) ? Files.newInputStream(path) : null;
        }
    }

    /** classpath 实现（jar 内资源）。 */
    final class Classpath implements AncientResourceSource {
        private final ClassLoader loader;
        private final String prefix;

        public Classpath(ClassLoader loader, String prefix) {
            this.loader = loader == null ? AncientResourceSource.class.getClassLoader() : loader;
            this.prefix = prefix == null ? "" : prefix;
        }

        @Override
        public String id() {
            return "classpath:" + prefix;
        }

        @Override
        public InputStream open(String relativePath) {
            return loader.getResourceAsStream(prefix + relativePath);
        }
    }
}
