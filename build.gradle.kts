import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsTask
import net.ltgt.gradle.errorprone.errorprone
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    java
    jacoco
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

    // Plain JDBC DataSource autoconfig so Flyway has a target to migrate against.
    // Spring Data JDBC / jOOQ proper arrive in Fase 1; the baseline needs only a DataSource.
    implementation(libs.spring.boot.starter.jdbc)

    // Flyway 11.x (BOM-managed). Boot 4 moved Flyway autoconfig into its own module, so the
    // starter (not bare flyway-core) is required to run migrations on startup. The postgresql
    // module is mandatory since Flyway 10 split out database-specific support.
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.database.postgresql)

    // PostgreSQL JDBC driver (BOM-managed), needed only at runtime.
    runtimeOnly(libs.postgresql)

    errorprone(libs.errorprone.core)
    errorprone(libs.nullaway)

    // FindSecBugs security detectors plug into SpotBugs analysis only (not compile/runtime).
    spotbugsPlugins(libs.findsecbugs)

    testImplementation(libs.spring.boot.starter.test)

    // Spring Modulith BOM aligns the release train to Spring Boot 4.0 (2.0.x line).
    // Only the test source set consumes Modulith for now: boundary verification via
    // ApplicationModules. The runtime starter-core lands later with the event registry.
    testImplementation(platform(libs.spring.modulith.bom))
    testImplementation(libs.spring.modulith.starter.test)

    // ArchUnit (JUnit 5 engine) drives the architecture fitness rules.
    testImplementation(libs.archunit.junit5)

    // Testcontainers + @ServiceConnection: a real Postgres 17 for integration tests (NUNCA H2).
    // Versions are BOM-managed (Testcontainers 2.0.5 via the Boot 4 BOM) so the
    // spring-boot-testcontainers <-> testcontainers pairing stays compatible.
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
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

// Coverage tooling version is pinned: a floating JaCoCo can break a JDK upgrade
// (instrumentation lags new class-file major versions). See 04-testing-quality.md.
jacoco {
    toolVersion = libs.versions.jacoco.get()
}

// Classes excluded from BOTH the report and the verification: the Spring Boot
// entrypoint, configuration, DTOs, and generated jOOQ code (the generated dirs
// do not exist yet; excluding them now is forward-looking and matches the plan).
// Domain packages are intentionally NOT excluded.
val coverageExclusions =
    listOf(
        "**/*Application.*",
        "**/LedgerForgeApplication.*",
        "**/config/**",
        "**/dto/**",
        "**/generated/**",
        "**/jooq/**",
    )

fun JacocoReportBase.applyCoverageExclusions() {
    classDirectories.setFrom(
        files(
            classDirectories.files.map { dir ->
                fileTree(dir) { exclude(coverageExclusions) }
            },
        ),
    )
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
    applyCoverageExclusions()
}

// Coverage floor (a minimum, NOT a target): ~80% line / ~70% branch at the
// bundle level. With *Application excluded and no domain code yet, the measured
// set is effectively empty, so the rule passes trivially; the gate bites once
// Fase 1 adds domain code. NUNCA perseguir 100% nem padding sem assercao.
tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.70".toBigDecimal()
            }
        }
    }
    applyCoverageExclusions()
}

// Wire the coverage gate into the local build (Definicao de pronto lists the
// JaCoCo gate as part of `./gradlew build`). SpotBugs stays decoupled from check
// (handled above); only the coverage verification is added here.
tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
