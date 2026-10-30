# C64GfxLib

Shared Commodore 64 graphics code for [FlipperJ](https://github.com/nanzee73/FlipperJ) and
[MuleJ](https://github.com/nanzee73/MuleJ). MIT licensed.

| Module | Package | Contents | Depends on |
|---|---|---|---|
| `c64gfx-core` | `de.c64gfx.*` | palettes (Pepto, Colodore, … + .vpl/.gpl/hex), colour matching (Euclid/RGB/YUV/HSL), dithering with per-cell colour restrictions, Koala and Art Studio hires files, 6502 assembler | nothing (no AWT/Swing) |
| `c64gfx-swing` | `de.c64gfx.swing` | `I18n` (EN/DE/PL/SV/NL, system language, live switching), `ImagePanel` (zoom, pan, crop mode), `ImageFitter`, `ImageAdjust`, `FileDialogs` (native on macOS), `SliderControls`, `PaletteEditorDialog`, `Icons`, `Platform`, `WrapLayout` | `c64gfx-core` |

`c64gfx-core` deliberately avoids AWT, so it can also be used on Android later. Pictures are passed as plain arrays:
`float[w*h*3]` RGB for input, `int[]` palette indices or `0xRRGGBB` pixels for output; `de.c64gfx.swing.Images`
converts from and to `BufferedImage`.

## Using it in an application

```kotlin
repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/nanzee73/C64GfxLib")
        credentials {
            username = (findProperty("gpr.user") as String?) ?: System.getenv("GITHUB_ACTOR")
            password = (findProperty("gpr.key") as String?) ?: System.getenv("GITHUB_TOKEN")
        }
    }
}
dependencies {
    implementation("de.c64gfx:c64gfx-swing:0.1.0")   // brings c64gfx-core along
}
```

At start-up, before anything else of the library is used:

```java
AppConfig.install(new AppConfig("FlipperJ", "de/flipperj", "/de/flipperj/icons/flipperj-",
        "de.flipperj.i18n.messages", AppConfig.LIBRARY_LANGUAGES, true));
Platform.initBeforeAwt("FlipperJ");
I18n.init();
```

The application's bundle is searched first, then the library's bundle
(`de/c64gfx/swing/i18n/messages*.properties`) with the shared texts: OK/Cancel, colour names, dither names,
scale/aspect names, file filters, palette editor, slider tips. An app can override any of these keys.

### Access to the package

GitHub Packages always needs a login, also for reading.

* **GitHub Actions of the apps:** on GitHub open *C64GfxLib ▸ Packages ▸ c64gfx-core ▸ Package settings ▸
  Manage Actions access ▸ Add repository* and add FlipperJ and MuleJ with role *Read*; same for `c64gfx-swing`.
  The workflows then use their automatic `GITHUB_TOKEN`.
* **Your computer (IntelliJ, Gradle):** create a *classic* personal access token with the scope `read:packages`
  (GitHub ▸ Settings ▸ Developer settings ▸ Personal access tokens ▸ Tokens (classic)) and put it into
  `~/.gradle/gradle.properties` (not into a project!):
  ```
  gpr.user=nanzee73
  gpr.key=ghp_…
  ```

### Working on the library and an app at the same time

Check out C64GfxLib next to the app and start Gradle with `-Pc64gfxLocal` (or put `c64gfxLocal=true` into the app's
`gradle.properties` locally): the app's `settings.gradle.kts` then includes `../C64GfxLib` as a composite build,
so library changes are used immediately without publishing.

## Releasing a new version

1. Change the library, push to `main` (GitHub Actions runs the tests).
2. Create a tag `vX.Y.Z`, e.g. in IntelliJ *Git ▸ New Tag…* and push it (*Push Tags*), or on GitHub
   *Releases ▸ Draft a new release*.
3. The workflow publishes `de.c64gfx:c64gfx-core:X.Y.Z` and `de.c64gfx:c64gfx-swing:X.Y.Z`.
4. In the apps raise the version in `build.gradle.kts`.

## Building

```
./gradlew build                 # compile + tests
./gradlew publishToMavenLocal   # into ~/.m2 for local experiments
```
