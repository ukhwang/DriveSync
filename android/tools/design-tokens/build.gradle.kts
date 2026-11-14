// Test-only module: keeps design/tokens.json (the design source of truth, mirrored in Figma)
// and the app theme (ui/theme/Theme.kt) in sync. Pure JVM, so it runs in the domain CI.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    testImplementation(libs.serialization.json)
    testImplementation(libs.kotlin.test)
}

tasks.test {
    // Inputs live outside this module; declare them so Gradle re-runs the test when they change.
    val tokens = rootProject.file("../design/tokens.json")
    val theme = rootProject.file("app/src/main/kotlin/com/drivesync/app/ui/theme/Theme.kt")
    inputs.file(tokens)
    inputs.file(theme)
    systemProperty("tokens.json", tokens.absolutePath)
    systemProperty("theme.kt", theme.absolutePath)
}
