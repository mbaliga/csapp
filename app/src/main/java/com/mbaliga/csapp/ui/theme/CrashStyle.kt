package com.mbaliga.csapp.ui.theme

import dev.aarso.crashrecovery.CrashRecoveryStyle

/**
 * csapp's own accent, translated for the design-system-agnostic crash-recovery module
 * (`CrashRecoveryStyle` is deliberately plain `@ColorInt Int` accent pairs with zero Hyle/
 * Compose dependency, so any constellation app can supply its own look without pulling in a
 * design system) — so a crash still reads as csapp's green, not the module's neutral default.
 *
 * csapp doesn't consume Hyle (`docs/PRODUCT-AND-ARCHITECTURE.md` §2 — it predates the
 * constellation's spatial shell and uses standard Material 3 navigation), so this reuses the
 * same primary/secondary values already declared in `Theme.kt` (`CsAppLightColors` /
 * `CsAppDarkColors`) rather than reaching for a token type this app doesn't have.
 */
val CsAppCrashRecoveryStyle: CrashRecoveryStyle = CrashRecoveryStyle.accent(
    light = 0xFF1B5E20.toInt(),
    onLight = 0xFFFFFFFF.toInt(),
    dark = 0xFF81C784.toInt(),
    onDark = 0xFF1B2E1B.toInt(),
)
