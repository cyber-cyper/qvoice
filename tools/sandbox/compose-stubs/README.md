# Compose API stubs (authoring sandbox only)

The authoring sandbox can't reach Google Maven, so the real Compose, Material 3,
activity and lifecycle libraries aren't available to compile the five screens
(`ui/HomeScreen.kt`, `ui/VoicesScreen.kt`, `ui/AboutScreen.kt`, `ui/Theme.kt`,
`ui/MainActivity.kt`). These files declare the part of those libraries the
screens use, with the exact names, parameters, defaults, nullability and
opt-in or deprecation levels of the published API, so that
`tools/sandbox/verify.sh` (step 4) can compile the screens and catch what a
Gradle build would: unresolved names, wrong parameter names or types, missing
`@OptIn`, deprecated-to-error overloads, non-exhaustive `when`s, and (with the
Compose compiler plugin) composable calls outside composition. None of this is
shipped; the app builds against the real libraries.

## Where each file's signatures come from

The API signature files that AndroidX publishes per release (`api/current.txt`),
as mirrored in JetBrains' Compose fork
(https://github.com/JetBrains/compose-multiplatform-core), at the tags matching
the versions in `gradle/libs.versions.toml` (Compose BOM 2025.08.00):

| Stubs | API file | Tag | Library version |
|---|---|---|---|
| `m3.kt` | `compose/material3/material3` | v1.8.2 | Material 3 1.3.2 |
| `foundation*.kt` | `compose/foundation/foundation`, `foundation-layout` | v1.9.0 | Compose 1.9.0 |
| `ui_*.kt` | `compose/ui/ui`, `ui-graphics`, `ui-unit`, `ui-geometry`, `ui-text` | v1.9.0 | Compose 1.9.0 |
| `runtime.kt`, `saveable.kt` | `compose/runtime/runtime`, `runtime-saveable` | v1.9.0 | Compose 1.9.0 |
| `lifecycle_compose.kt`, `lifecycle_vm_compose.kt` | `lifecycle/lifecycle-runtime-compose`, `lifecycle-viewmodel-compose` | v1.9.0 | lifecycle 2.9.2 |
| `icons*.kt` | `compose/material/material-icons-core` | v1.7.3 | icons-core 1.7.x (frozen) |
| `activity*.kt` | `activity/activity`, `activity-compose` | v1.9.0 | activity 1.10 (the four calls used are the same in 1.10.1) |
| `lifecycle.kt`, `lifecycle_viewmodel.kt` | — | — | bare types only |

## Checking and extending

When a screen starts using an API that has no stub yet, step 4 fails with
"unresolved reference". Add the declaration to the matching file, copied from
the API file (parameter names, order, defaults and annotations exactly), then
check every stub against the real signatures:

```
bash tools/sandbox/compose-stubs/fetch_api.sh /tmp/qvoice-api
python3 tools/sandbox/compose-stubs/check_stubs.py /tmp/qvoice-api
```

The check was proven on slices 4-7 by breaking copies of the screens on purpose
(15 kinds of error, from a missing `@OptIn` to a colour-role typo); each was
reported.

What it can't cover: the Compose plugin's code generation (it needs the real
runtime; step 4b stops there on purpose), lint, R8 and resource merging.
