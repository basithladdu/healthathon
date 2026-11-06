# Open-source libraries (round 3)

Added for familiar, everyday Android UI. Each one is permissively licensed and pulled from Maven Central.
Lottie (Apache-2.0) is listed in `../lottie/README.md`.

| Library | Version | Licence | Where it is used |
| --- | --- | --- | --- |
| [kizitonwose/Calendar](https://github.com/kizitonwose/Calendar) `com.kizitonwose.calendar:compose` (+ `core`, `data`) | 2.9.1 | MIT, `MIT-kizitonwose-Calendar.txt` | `CareCalendarGrid.kt`: `SaharaMonthPager` (Calendar, Month view: swipe left/right for the next or previous month) and `SaharaWeekPager` (Calendar, Week view; doctor Schedule week strip in `DoctorScheduleUi.kt`). Arrows, colours, dots and legend are unchanged. |
| [Coil 3](https://github.com/coil-kt/coil) `io.coil-kt.coil3:coil-compose` (+ `coil`, `coil-core`, `coil-compose-core`) | 3.3.0 | Apache-2.0, `Apache-2.0.txt` | `MedicineStripPhotos.kt`: `StripThumb` (strip photos on Today's doses, the medicine form and medicine history). Decodes off the main thread at the drawn size, keeps a memory cache, fades in. Local files only: no network module, and Coil keeps no disk cache for local files. |
| Material 3 pull-to-refresh (`androidx.compose.material3.pulltorefresh`, already in the app) | Compose BOM 2025.09.01 (material3 1.4.0) | Apache-2.0 | `SaharaPullRefresh.kt`: Today (signed in), doctor Home and doctor Patients. The Refresh buttons stay for TalkBack. |

Transitive additions (all Apache-2.0): `com.google.accompanist:accompanist-drawablepainter` 0.37.3, `androidx.appcompat:appcompat-resources` 1.7.1,
`androidx.exifinterface:exifinterface` 1.4.1 (through Coil); `com.squareup.okio:okio` moves to 3.15.0 (it was already in the app through OkHttp).
Calendar's POM lists `androidx.compose.ui:ui-tooling` at runtime (only for its previews); it is excluded in `app/build.gradle.kts` so the release app does not ship it.

## Versions

Newer releases exist (Calendar 2.10.1, Coil 3.6.3) but are built with Kotlin 2.3/2.4 and, for Calendar, Compose 1.10.
The app builds with Kotlin 2.2.20 and Compose BOM 2025.09.01, so these are the newest releases that match it. Move both up together with Kotlin and the Compose BOM.

## Size (estimate, release build after R8)

| Library | Download (AAR/JAR) | Estimated added to the app |
| --- | --- | --- |
| Calendar (compose 201 KB, core 16 KB, data 20 KB) | 237 KB | about 60-100 KB (year and heat-map calendars are unused and removed by R8) |
| Coil (core 460 KB, compose-core 131 KB, coil + compose 22 KB, exifinterface 74 KB, appcompat-resources 62 KB, drawablepainter 11 KB) | 760 KB | about 150-250 KB |
| Material 3 pull-to-refresh | already in the app | about 0 KB |

Not measured: no Gradle run in this lane. The coordinator's build can confirm with `apkanalyzer` or the AAB size.
