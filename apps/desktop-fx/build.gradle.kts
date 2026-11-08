import org.gradle.api.attributes.Usage
import org.gradle.nativeplatform.MachineArchitecture
import org.gradle.nativeplatform.OperatingSystemFamily
import java.nio.file.Paths

plugins {
    java
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "dev.momentum"
version = "1.0.0"

repositories {
    mavenCentral()
}

javafx {
    version = "21.0.4"
    modules("javafx.controls", "javafx.fxml")
}

dependencies {
    // SQLite driver for SqliteRepository (same runtime the local service uses).
    implementation("org.xerial:sqlite-jdbc:3.45.1.0")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

application {
    mainClass.set("dev.momentum.desktop.MomentumApp")
}

// The desktop app reuses the service layer sources directly — one implementation,
// consumed by MomentumServer (HTTP) and MomentumApp (JavaFX). No business logic is duplicated.
sourceSets {
    main {
        java {
            srcDir(file("../../services/api/src/main/java"))
        }
    }
}

// JavaFX runtime jars for module path (Windows x86-64)
val fxRuntime = configurations.detachedConfiguration(
    dependencies.create("org.openjfx:javafx-controls:21.0.4"),
    dependencies.create("org.openjfx:javafx-graphics:21.0.4"),
    dependencies.create("org.openjfx:javafx-base:21.0.4"),
    dependencies.create("org.openjfx:javafx-fxml:21.0.4")
).also { configuration ->
    configuration.attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
        attribute(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, objects.named("windows"))
        attribute(MachineArchitecture.ARCHITECTURE_ATTRIBUTE, objects.named("x86-64"))
    }
}

val fxModulePath by lazy { fxRuntime.joinToString(File.pathSeparator) { it.absolutePath } }

// ---------------------------------------------------------------------------
// Smoke test (existing)
// ---------------------------------------------------------------------------

tasks.register<JavaExec>("smoke") {
    group = "verification"
    description = "Runs the headless JavaFX smoke test (renders + screenshots, verifies nav/modal/toast/theme/db)."
    mainClass.set("dev.momentum.desktop.SmokeTest")
    classpath(sourceSets["test"].runtimeClasspath, sourceSets["main"].runtimeClasspath)
    jvmArgs(
        "--module-path", fxModulePath,
        "--add-modules", "javafx.controls,javafx.fxml",
        "-Dprism.order=sw",
        "-Dmomentum.smoke=1",
        "-Dmomentum.data.dir=${project.findProperty("smokeDataDir") ?: layout.buildDirectory.dir("smoke-data").get().asFile.absolutePath}"
    )
}

// ---------------------------------------------------------------------------
// Native packaging: jlink + jpackage
// ---------------------------------------------------------------------------

val jdkHome = System.getProperty("java.home")
val jlinkExecutable = file("${jdkHome}/bin/jlink.exe")
val jpackageExecutable = file("${jdkHome}/bin/jpackage.exe")

val jlinkRuntimeDir = file("build/custom-jre")
val jpackageAppImageDir = file("build/jpackage/image")
val jpackageExeDir = file("build/jpackage/exe")
val jpackageMsiDir = file("build/jpackage/msi")
val iconFile = file("src/main/resources/dev/momentum/desktop/icon.ico")

// The app image directory name matches the app name ("MOMENTUM")
val appImageSubDir = file("${jpackageAppImageDir.absolutePath}/MOMENTUM")

/**
 * Creates a minimized custom JRE via jlink containing only the modules
 * MOMENTUM actually needs (JavaFX + JDBC + core JDK modules).
 */
tasks.register<Exec>("jlinkRuntime") {
    group = "packaging"
    description = "Builds a minimized custom Java runtime via jlink."
    dependsOn("jar")

    doFirst {
        if (!jlinkExecutable.exists()) {
            logger.error("jlink not found at $jlinkExecutable")
            return@doFirst
        }
        // jlink fails if output dir already exists
        if (jlinkRuntimeDir.exists()) {
            jlinkRuntimeDir.deleteRecursively()
        }
    }

    commandLine = listOf(
        jlinkExecutable.absolutePath,
        "--module-path", listOf(
            Paths.get(jdkHome, "jmods").toString(),
            fxModulePath
        ).joinToString(File.pathSeparator),
        "--add-modules",
            "java.base,javafx.controls,javafx.fxml,java.sql,java.net.http," +
            "java.logging,java.management,java.naming,java.prefs,java.xml," +
            "jdk.unsupported,jdk.crypto.ec",
        "--output", jlinkRuntimeDir.absolutePath,
        "--compress", "2",
        "--no-header-files",
        "--no-man-pages"
    )
}

/**
 * Creates the native application image via jpackage --type app-image.
 * Output: build/jpackage/image/MOMENTUM/ (contains MOMENTUM.exe launcher)
 */
tasks.register<Exec>("jpackageAppImage") {
    group = "packaging"
    description = "Creates the native MOMENTUM application image via jpackage."
    dependsOn("jlinkRuntime", "installDist")

    doFirst {
        if (!jpackageExecutable.exists()) {
            logger.error("jpackage not found at $jpackageExecutable")
            return@doFirst
        }
        if (!iconFile.exists()) {
            logger.error("Icon not found at $iconFile")
            return@doFirst
        }
        if (jpackageAppImageDir.exists()) {
            jpackageAppImageDir.deleteRecursively()
        }
    }

    val appLibDir = file("build/install/${project.name}/lib").absolutePath
    val mainJar = "momentum-desktop-${project.version}.jar"

    commandLine = listOf(
        jpackageExecutable.absolutePath,
        "--type", "app-image",
        "--input", appLibDir,
        "--main-jar", mainJar,
        "--main-class", "dev.momentum.desktop.MomentumApp",
        "--name", "MOMENTUM",
        "--app-version", project.version.toString(),
        "--vendor", "MOMENTUM",
        "--description", "MOMENTUM - Native desktop goal tracking application",
        "--icon", iconFile.absolutePath,
        "--runtime-image", jlinkRuntimeDir.absolutePath,
        "--module-path", appLibDir,
        "--dest", jpackageAppImageDir.absolutePath
    )
}

/**
 * Creates the EXE installer via jpackage --type exe.
 * Requires the app image from jpackageAppImage.
 * Output: build/jpackage/exe/MOMENTUM-1.0.0.exe
 */
tasks.register<Exec>("jpackageExe") {
    group = "packaging"
    description = "Creates the EXE installer for MOMENTUM via jpackage."
    dependsOn("jpackageAppImage")

    doFirst {
        if (!jpackageExecutable.exists()) {
            logger.error("jpackage not found at $jpackageExecutable")
            return@doFirst
        }
        val exeDir = file("build/jpackage/exe")
        if (exeDir.exists()) {
            exeDir.deleteRecursively()
        }
    }

    commandLine = listOf(
        jpackageExecutable.absolutePath,
        "--type", "exe",
        "--app-image", appImageSubDir.absolutePath,
        "--name", "MOMENTUM",
        "--app-version", project.version.toString(),
        "--vendor", "MOMENTUM",
        "--description", "MOMENTUM - Native desktop goal tracking application",
        "--icon", iconFile.absolutePath,
        "--dest", file("build/jpackage/exe").absolutePath,
        "--win-menu",
        "--win-menu-group", "MOMENTUM",
        "--win-shortcut"
    )
}

/**
 * Creates the MSI installer via jpackage --type msi.
 * Requires WiX toolset installed on the system.
 * Output: build/jpackage/msi/MOMENTUM-1.0.0.msi
 */
tasks.register<Exec>("jpackageMsi") {
    group = "packaging"
    description = "Creates the MSI installer for MOMENTUM via jpackage (requires WiX)."
    dependsOn("jpackageAppImage")

    doFirst {
        if (!jpackageExecutable.exists()) {
            logger.error("jpackage not found at $jpackageExecutable")
            return@doFirst
        }
        val msiDir = file("build/jpackage/msi")
        if (msiDir.exists()) {
            msiDir.deleteRecursively()
        }
    }

    commandLine = listOf(
        jpackageExecutable.absolutePath,
        "--type", "msi",
        "--app-image", appImageSubDir.absolutePath,
        "--name", "MOMENTUM",
        "--app-version", project.version.toString(),
        "--vendor", "MOMENTUM",
        "--description", "MOMENTUM - Native desktop goal tracking application",
        "--icon", iconFile.absolutePath,
        "--dest", file("build/jpackage/msi").absolutePath,
        "--win-menu",
        "--win-menu-group", "MOMENTUM",
        "--win-shortcut",
        "--win-dirchooser"
    )
}

/**
 * Convenience task: builds app image + EXE + MSI installers.
 */
tasks.register("jpackageAll") {
    group = "packaging"
    description = "Builds app image, EXE installer, and MSI installer."
    dependsOn("jpackageAppImage", "jpackageExe", "jpackageMsi")
}
