pluginManagement {
    repositories {
        // Google Maven is only needed for Android modules (see `domain.only` below).
        if (providers.gradleProperty("domain.only").orNull != "true") google()
        mavenCentral()
        gradlePluginPortal()
    }
}

val domainOnly = providers.gradleProperty("domain.only").orNull == "true"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (!domainOnly) google()
        mavenCentral()
    }
}

rootProject.name = "drivesync"

/*
 * Pure-Kotlin (JVM) domain modules: no Android SDK needed, fully unit-tested.
 * They hold every rule the contest judges on (safety policy, session state
 * machine, 6-state action approval), so they are verified on any machine.
 */
include(
    ":core:model",
    ":core:vehicle",
    ":core:safety",
    ":core:session",
    ":core:action",
    ":core:ai",
    // Checks design/tokens.json == app theme, so a Figma change is applied in one place.
    ":tools:design-tokens",
)

/*
 * Android modules need the Android SDK + Google Maven.
 * `./gradlew -Pdomain.only=true test` skips them (used where no SDK is installed).
 */
if (!domainOnly) {
    include(
        ":core:data",
        ":core:voice",
        ":app",
    )
}
