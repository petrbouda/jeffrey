/*
 * Jeffrey IntelliJ Plugin — STANDALONE Gradle project.
 *
 * Intentionally NOT part of Jeffrey's root Maven reactor: it pulls the IntelliJ Platform SDK and
 * produces a plugin zip. Build it on its own:  cd jeffrey-intellij-plugin && ./gradlew buildPlugin
 *
 * Java level is 21 because IntelliJ IDEA 2026.1 runs on JetBrains Runtime 21 — a newer-bytecode jar
 * would not load. (Jeffrey's own backend is Java 25; unrelated — this plugin runs in IntelliJ's JVM.)
 */
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.16.0"
}

group = "cafe.jeffrey"
version = "0.1.0-SNAPSHOT"

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
        // Downloads & extracts the IntelliJ IDEA 2026.1.2 SDK and builds the compile classpath
        // from its lib/*.jar — what plain Maven could not do.
        //
        // NOTE: JetBrains no longer publishes the standalone Community (ideaIC) installer for the
        // 2025.3+/2026.1 line — only Ultimate (ideaIU) — so we build against the Ultimate SDK. It is
        // a superset; the plugin still runs on Community IDEs at runtime because it depends only on
        // com.intellij.modules.platform + com.intellij.java. (runIde launches Ultimate, which needs
        // a license/trial in the sandbox; building/verifying/installing are unaffected.)
        intellijIdeaUltimate("2026.1.2")

        // Java PSI: ClassUtil, JavaPsiFacade, PsiMethod, ... (also declared as <depends> in plugin.xml).
        bundledPlugin("com.intellij.java")

        // In-process IDE test fixtures (BasePlatformTestCase, myFixture, ...). JUnit 3/4-based.
        testFramework(TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "261"
            // untilBuild left at the plugin default (current branch); open it up when targeting
            // newer IDE majors.
        }
    }
}

// BasePlatformTestCase is JUnit 3/4-based, so the test task uses the default JUnit 4 runner
// (no useJUnitPlatform()).
