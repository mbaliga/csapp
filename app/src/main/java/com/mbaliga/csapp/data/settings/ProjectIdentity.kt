package com.mbaliga.csapp.data.settings

/**
 * CSApp's own stable identifier for "the project" an `issues-manifest` export concerns -
 * `projectRef.externalId` in the Fonebrew hub contract
 * (`docs/ratified/CSAPP_ISSUES_MANIFEST_V1.md` in `mbaliga/Android-IDE-core`). CSApp V1 tracks
 * exactly one monitored GitHub repo and/or Play package at a time (see [AppSettingsStore]); that
 * pairing - not a made-up id - is the natural stable identity, so this derives from settings
 * rather than inventing a separate project-id concept CSApp doesn't otherwise have (INT-002:
 * this file lane must not change how CSApp manages things internally).
 */
fun AppSettingsStore.resolveProjectExternalId(): String {
    val owner = githubOwner?.trim()?.takeIf { it.isNotEmpty() }
    val repo = githubRepo?.trim()?.takeIf { it.isNotEmpty() }
    if (owner != null && repo != null) return "github:$owner/$repo"

    val playPackage = playPackageName?.trim()?.takeIf { it.isNotEmpty() }
    if (playPackage != null) return "play:$playPackage"

    return "csapp:unconfigured-project"
}
