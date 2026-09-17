pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// dev.aarso:crash-recovery is single-sourced from mbaliga/Shared-Libraries-asoc, pinned as a
// git submodule at ./shared-libraries and composited in via includeBuild — the same idiom
// Android-IDE-core uses for the same repo (see that repo's settings.gradle.kts). The explicit
// dependencySubstitution below is scoped to exactly what csapp consumes today (:crash-recovery
// only): declaring it disables *automatic* group:name substitution for the rest of this
// included build, which is fine here since csapp doesn't (yet) consume any of the shared
// libraries' other modules (:search-core, :interaction-mode, :diagnostics-*, …) — see that
// repo's README for the full module list if a future lane adopts one of those too.
// Update the pin with:
//   git -C shared-libraries fetch && git -C shared-libraries checkout <sha> && git add shared-libraries
includeBuild("shared-libraries") {
    dependencySubstitution {
        substitute(module("dev.aarso:crash-recovery")).using(project(":crash-recovery"))
    }
}

rootProject.name = "csapp"
include(":app")
