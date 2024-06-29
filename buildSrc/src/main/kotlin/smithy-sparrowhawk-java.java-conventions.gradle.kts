import com.github.spotbugs.snom.Effort
import java.util.regex.Pattern
import org.gradle.api.Project
import org.gradle.kotlin.dsl.the

plugins {
    `java-library`
    id("com.adarshr.test-logger")
    id("com.github.spotbugs")
    id("com.diffplug.spotless")
}

// Workaround per: https://github.com/gradle/gradle/issues/15383
val Project.libs get() = the<org.gradle.accessors.dm.LibrariesForLibs>()

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

/*
 * Common test configuration
 * ===============================
 */
dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.hamcrest)
    testImplementation(libs.assertj.core)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

testlogger {
    showExceptions = true
    showStackTraces = true
    showFullStackTraces = false
    showCauses = true
    showSummary = true
    showPassed = true
    showSkipped = true
    showFailed = true
    showOnlySlow = false
    showStandardStreams = true
    showPassedStandardStreams = false
    showSkippedStandardStreams = false
    showFailedStandardStreams = true
    logLevel = LogLevel.LIFECYCLE
}

/*
 * Formatting
 * ==================
 * see: https://github.com/diffplug/spotless/blob/main/plugin-gradle/README.md#java
 */
spotless {
    java {
        // Enforce a common license header on all files
        //licenseHeaderFile("${project.rootDir}/config/spotless/license-header.txt")
        indentWithSpaces()
        endWithNewline()

        eclipse().configFile("${project.rootDir}/config/spotless/formatting.xml")

        // Fixes for some strange formatting applied by eclipse:
        // see: https://github.com/kamkie/demo-spring-jsf/blob/bcacb9dc90273a5f8d2569470c5bf67b171c7d62/build.gradle.kts#L159
        custom("Lambda fix") { it.replace("} )", "})").replace("} ,", "},") }
        custom("Long literal fix") { Pattern.compile("([0-9_]+) [Ll]").matcher(it).replaceAll("\$1L") }

        // Static first, then everything else alphabetically
        removeUnusedImports()
        importOrder("\\#", "")

        // Ignore generated generated code for formatter check
        targetExclude("**/build/**/*.*")
    }
}

/*
 * Spotbugs
 * ====================================================
 *
 * Run spotbugs against source files and configure suppressions.
 */
// Configure the spotbugs extension.
spotbugs {
    effort = Effort.MAX
    excludeFilter = file("${project.rootDir}/config/spotbugs/filter.xml")
}

// We don't need to lint tests.
tasks.named("spotbugsTest") {
    enabled = false
}

/*
 * Repositories
 * ================================
 */
repositories {
    mavenLocal()
    mavenCentral()
}
