plugins {
    id("fabric-loom") version "1.15.5"
}

val archivesBaseName = providers.gradleProperty("archives_base_name").get()
val mavenGroup = providers.gradleProperty("maven_group").get()

// 命名准则（见 AGENTS.md「命名规范」）：
//   构建产物名 = FO-<mod版本>-<MC版本>.jar，例如 FO-6.25-1.21.11.jar
//   mod 版本号不带 V 前缀；MC 版本随 libs.versions.toml 的 minecraft 自动跟随，升 MC 不用改两处。
val modVersion = libs.versions.mod.version.get()
val minecraftVersion = libs.versions.minecraft.get()

base {
    archivesName = archivesBaseName
    version = "$modVersion-$minecraftVersion"
    group = mavenGroup
}

loom {
    accessWidenerPath = file("src/main/resources/fo.accesswidener")
}

repositories {
    maven {
        name = "meteor-maven"
        url = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "meteor-maven-snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
    maven {
        name = "seedfinding"
        url = uri("https://maven.seedfinding.com/")
    }
    mavenCentral()
}

// Configuration that holds jars to include in the final jar
val extraLibs: Configuration by configurations.creating

dependencies {
    // Fabric
    minecraft("com.mojang:minecraft:1.21.11")
    mappings("net.fabricmc:yarn:1.21.11+build.3:v2")
    modImplementation("net.fabricmc:fabric-loader:0.18.2")

    // Meteor (modImplementation 让 loom remap 到 yarn 命名空间)
    modImplementation("meteordevelopment:meteor-client:1.21.11-SNAPSHOT")

    // Baritone API（icehack-2 鞘翅套件编译期依赖；modCompileOnly 让 loom remap 到 yarn 命名空间，运行时由 baritone mod 提供）
    modCompileOnly(files("libs/baritone-api-fabric-1.21.11-SNAPSHOT.jar"))

    // V6.8 起古城模块已全程自写（纯 Java），不再打包 libs/fo-ancient-blackbox.jar。
    // 该黑盒里的 657 个类已被 ancient/ 下的自写实现替代。
    //
    // ⚠️ 为什么 libs/ 与 natives/ 里的二进制必须入库（而不是改成 Maven 依赖）：
    //   1. libs/baritone-api-fabric-1.21.11-SNAPSHOT.jar —— Baritone 未发布到任何公开 Maven 仓库，
    //      只能以文件依赖引入；SNAPSHOT 也没有可锁定的版本号。
    //   2. src/main/resources/natives/windows/*.dll —— CubiomesJNI 的原生库，且 JNI 导出符号是
    //      Java_com_custom_addon_util_CubiomesJNI_*，与 com.custom.addon.util 包名硬绑定。
    //      换成 Maven 坐标无法保留这个符号约定，改包名则链接失败。
    // 这两类是「源码级依赖」，不是「构建产物」，与 releases/*.jar 性质不同，不要混淆。
    // 见 docs/ci.md「仓库里为什么仍然有二进制文件」。

    // Seedfinding (世界种子/结构定位计算库，ElytraCollector 依赖)
    extraLibs("com.seedfinding:mc_biome:1.171.1") { isTransitive = false }
    extraLibs("com.seedfinding:mc_core:1.210.0") { isTransitive = false }
    extraLibs("com.seedfinding:mc_feature:1.171.10") { isTransitive = false }
    extraLibs("com.seedfinding:mc_math:1.171.0") { isTransitive = false }
    extraLibs("com.seedfinding:mc_noise:1.171.1") { isTransitive = false }
    extraLibs("com.seedfinding:mc_seed:1.171.2") { isTransitive = false }
    extraLibs("com.seedfinding:mc_terrain:1.171.1") { isTransitive = false }

    // 测试
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    configurations.implementation.get().extendsFrom(extraLibs)
}

tasks.test {
    useJUnitPlatform()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.jdk.get().toInt()))
    }
}

fun toMinecraftCompat(version: String): String {
    val stable = Regex("""^(\d{2})\.([1-9]\d*)(?:\.(\d+))?$""")

    stable.matchEntire(version)?.let {
        val (year, drop, _) = it.destructured
        return "~$year.$drop"
    }

    val pre = Regex("""^(\d{2})\.([1-9]\d*)-pre[-.](\d+)$""")
    pre.matchEntire(version)?.let {
        return version.replace("-pre-", "-pre.")
    }

    val rc = Regex("""^(\d{2})\.([1-9]\d*)-rc[-.](\d+)$""")
    rc.matchEntire(version)?.let {
        return version.replace("-rc-", "-rc.")
    }

    return version
}

tasks {
    processResources {
        // fabric.mod.json 里的版本号用纯 mod 版本（不带 MC 后缀）：
        // 带 "-1.21.11" 会被 Fabric 的 semver 解析成 6.25 的预发布版，影响依赖判定，没必要。
        val propertyMap = mapOf(
            "version" to modVersion,
            "minecraft_version" to toMinecraftCompat(minecraftVersion),
            "jdk_version" to libs.versions.jdk.get(),
        )

        inputs.properties(propertyMap)
        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(
            listOf(
                "-Xlint:deprecation",
                "-Xlint:unchecked"
            )
        )
    }
}

tasks.jar {
    inputs.property("archivesName", archivesBaseName)

    // 打入 seedfinding 库 (ElytraCollector 运行时需要)
    from(extraLibs.map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/**", "module-info.class")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from("LICENSE") {
        rename { "${it}_$archivesBaseName" }
    }
}
