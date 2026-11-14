// Plugins are put on the root classpath once so every module shares one
// classloader (avoids "Kotlin Gradle plugin loaded multiple times").
buildscript {
    val domainOnly = providers.gradleProperty("domain.only").orNull == "true"
    repositories {
        if (!domainOnly) google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
        classpath(libs.kotlin.serialization.plugin)
        if (!domainOnly) {
            classpath(libs.android.gradle.plugin)
            classpath(libs.kotlin.compose.plugin)
            classpath(libs.ksp.gradle.plugin)
        }
    }
}

subprojects {
    tasks.withType<Test>().configureEach {
        testLogging {
            events("failed")
            showStandardStreams = false
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}
