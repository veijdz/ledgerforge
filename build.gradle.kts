import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsTask
import net.ltgt.gradle.errorprone.errorprone
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spotless)
    alias(libs.plugins.errorprone)
    alias(libs.plugins.spotbugs)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))

    implementation(libs.spring.boot.starter)
    implementation(libs.jspecify)

    errorprone(libs.errorprone.core)
    errorprone(libs.nullaway)

    // FindSecBugs security detectors plug into SpotBugs analysis only (not compile/runtime).
    spotbugsPlugins(libs.findsecbugs)

    testImplementation(libs.spring.boot.starter.test)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        // NullAway: null-safety violations in our code are compile errors.
        error("NullAway")
        option("NullAway:AnnotatedPackages", "com.ledgerforge")
        option("NullAway:JSpecifyMode", "true")
    }
    // JDK 16+ strongly encapsulates jdk.compiler internals (JEP 396); Error Prone
    // needs the compiler forked with these exports/opens to reach javac internals.
    options.isFork = true
    options.forkOptions.jvmArgs!!.addAll(
        listOf(
            "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
        ),
    )
}

spotless {
    java {
        target("src/**/*.java")
        googleJavaFormat("1.28.0")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

spotbugs {
    // Plan target 4.10.x: SpotBugs tool that can analyze Java 25 bytecode (class major 69).
    toolVersion = "4.10.2"
    effort = Effort.MAX
    reportLevel = Confidence.DEFAULT
}

// SpotBugs is a CI gate, not part of the fast local `build`/`check` (see 04-testing-quality.md).
// Run it explicitly via `./gradlew spotbugsMain`.
tasks.withType<SpotBugsTask>().configureEach {
    reports.create("html") {
        required = true
    }
}

// The plugin auto-wires its spotbugs tasks into `check` (as a TaskCollection dependency).
// Detach that collection so `./gradlew build`/`check` stays the fast gate; SpotBugs runs
// only when invoked explicitly via `./gradlew spotbugsMain`.
tasks.named("check") {
    setDependsOn(
        dependsOn.filterNot { dep ->
            dep is TaskCollection<*> &&
                dep.map { it.name }.all { it.startsWith("spotbugs") }
        },
    )
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
