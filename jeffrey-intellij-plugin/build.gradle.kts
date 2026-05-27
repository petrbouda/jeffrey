/*
 * Jeffrey IntelliJ Plugin — STANDALONE Gradle project.
 *
 * Intentionally NOT part of Jeffrey's root Maven reactor: it pulls the IntelliJ Platform SDK and
 * produces a plugin zip. Build it on its own:  cd jeffrey-intellij-plugin && ./gradlew buildPlugin
 *
 * Java level is 21 because the IntelliJ Platform (2025.1+) runs on JetBrains Runtime 21 — a
 * newer-bytecode jar would not load. (Jeffrey's own backend is Java 25; unrelated — this plugin
 * runs in IntelliJ's JVM.)
 *
 * Build coordinates (group/version), the target SDK (platformType/platformVersion) and the
 * compatibility floor (pluginSinceBuild) all come from gradle.properties — the single source of
 * truth, following the IntelliJ Platform Plugin Template layout.
 */
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.16.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // SDK type + version come from gradle.properties (platformType=IC, platformVersion=2025.1.2).
        // Building against the 2025.1 SDK is deliberate: the platform APIs this plugin calls
        // (ReadAction.compute, com.intellij.ide.impl.TrustedProjects.isTrusted) are stable there —
        // not deprecated/experimental as they became in 2026.1 — so the verifier reports no API
        // warnings, while sinceBuild=251 keeps the plugin installable on 2025.* and every newer IDE.
        create(
            providers.gradleProperty("platformType"),
            providers.gradleProperty("platformVersion"),
        )

        // Java PSI: ClassUtil, JavaPsiFacade, PsiMethod, ... (bundled in Community; also <depends> in plugin.xml).
        bundledPlugin("com.intellij.java")

        // In-process IDE test fixtures (BasePlatformTestCase, myFixture, ...). JUnit 3/4-based.
        testFramework(TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            // From gradle.properties (pluginSinceBuild=251 → installable on 2025.1 and newer).
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            // Open-ended: no upper bound (the default would cap untilBuild at the compile-SDK branch).
            untilBuild = provider { null }
        }
    }
}

// BasePlatformTestCase is JUnit 3/4-based, so the test task uses the default JUnit 4 runner
// (no useJUnitPlatform()).
