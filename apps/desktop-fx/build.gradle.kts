import org.gradle.api.attributes.Usage
import org.gradle.nativeplatform.MachineArchitecture
import org.gradle.nativeplatform.OperatingSystemFamily

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
val fxModulePath = fxRuntime.joinToString(";") { it.absolutePath }

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

