# Third-party notices

DialerEnhancements is licensed under the GNU General Public License v3.0 or later (see `LICENSE`).
It uses the following libraries, all pulled in as Gradle dependencies (no copies of their source are kept in this repository).

## Shipped inside the APK

| Library | Version | Licence | Source |
|---|---|---|---|
| DexKit (Kotlin/Java part) | 2.3.0 | Apache License 2.0 | https://github.com/LuckyPray/DexKit |
| DexKit native core (`libdexkit.so`) | 2.3.0 | GNU Lesser General Public License v3.0 | https://github.com/LuckyPray/DexKit/tree/master/Core |
| libxposed service | 102.0.0 | Apache License 2.0 | https://github.com/libxposed/service |
| AndroidX (Activity, Compose, Material 3 and their dependencies) | see `app/build.gradle.kts` | Apache License 2.0 | https://developer.android.com/jetpack/androidx |
| Kotlin standard library and kotlinx.coroutines | bundled by the Kotlin and Compose dependencies | Apache License 2.0 | https://github.com/JetBrains/kotlin |

DexKit's native core is linked dynamically as a separate shared library (`lib/arm64-v8a/libdexkit.so`).
Under the LGPL-3.0 you may replace it with a modified build of the same DexKit version.
The complete source of DialerEnhancements is published in this repository, and DexKit's source is available at the address above.

## Used only at build time

| Component | Licence | Source |
|---|---|---|
| libxposed API 102.0.0 (compile-only; provided by LSPosed at runtime) | Apache License 2.0 | https://github.com/libxposed/api |
| Gradle wrapper | Apache License 2.0 | https://gradle.org |
| Android Gradle Plugin | Apache License 2.0 | https://developer.android.com/build |
