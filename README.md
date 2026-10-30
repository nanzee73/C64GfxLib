# C64GfxLib

[![Release](https://img.shields.io/jitpack/version/com.github.nanzee73/C64GfxLib)](https://jitpack.io/#nanzee73/C64GfxLib)
[![Downloads per month](https://img.shields.io/jitpack/dm/github/nanzee73/C64GfxLib)](https://jitpack.io/#nanzee73/C64GfxLib)
[![Downloads per week](https://img.shields.io/jitpack/dw/github/nanzee73/C64GfxLib)](https://jitpack.io/#nanzee73/C64GfxLib)

Shared Commodore 64 graphics code for [FlipperJ](https://github.com/nanzee73/FlipperJ) and
[MuleJ](https://github.com/nanzee73/MuleJ). Noncommercial use only: [PolyForm Noncommercial License 1.0.0](LICENSE).
Version 0.1.0 was published under the MIT License.

| Module | Package | Contents | Depends on |
|---|---|---|---|
| `c64gfx-core` | `de.c64gfx.*` | palettes (Pepto, Colodore, … + .vpl/.gpl/hex), colour matching (Euclid/RGB/YUV/HSL), dithering with per-cell colour restrictions, reading C64 pictures (`C64Formats`, see below), Koala and Art Studio writing, 6502 assembler | nothing (no AWT/Swing) |
| `c64gfx-swing` | `de.c64gfx.swing` | `I18n` (EN/DE/PL/SV/NL, system language, live switching), `ImagePanel` (zoom, pan, crop mode), `ImageFitter`, `ImageAdjust`, `FileDialogs` (native on macOS), `SliderControls`, `PaletteEditorDialog`, `HelpDialog` (HTML user guide per language), `Icons`, `Platform`, `WrapLayout` | `c64gfx-core` |

`c64gfx-core` deliberately avoids AWT, so it can also be used on Android later. Pictures are passed as plain arrays:
`float[w*h*3]` RGB for input, `int[]` palette indices or `0xRRGGBB` pixels for output; `de.c64gfx.swing.Images`
converts from and to `BufferedImage`.

## C64 picture formats

`C64Formats.read(fileName, bytes)` returns a `C64Picture` (320×200 palette indices, a second frame for interlace
pictures); `render(palette)` gives the RGB pixels with both interlace frames mixed. In Swing,
`de.c64gfx.swing.Images.read(file, palette)` reads C64 pictures and everything ImageIO knows, and the open dialog
of `FileDialogs` has a "C64 pictures" filter.

| Format | Extensions |
|---|---|
| Koala Painter | `.kla .koa .gig` |
| Art Studio (hires) | `.art .aas .hpi` |
| Advanced Art Studio | `.ocp .mpic` |
| Drazpaint (also compressed) | `.drz .drp` |
| Drazlace, interlace (also compressed) | `.drl .dlp` |
| FLI, FLI Designer, Flimatic | `.fli .fd2` |
| FLI Graph, FLI Editor (background per line) | `.bml .flg .fli .fed` |
| AFLI | `.afl` |
| Gunpaint, interlace FLI | `.gun .ifl` |
| Funpaint II, interlace FLI (also compressed) | `.fun .fp2` |
| FlipperJ raw files (load address $3C00) | `.fli .afl .ifl` |

The format is recognised by extension and file size. The leftmost three character columns of FLI pictures
(the "FLI bug") show the background. File layouts as documented by [RECOIL](https://recoil.sourceforge.net);
the code is an independent implementation.

## Using it in an application

The library is published through [JitPack](https://jitpack.io/#nanzee73/C64GfxLib): no account or token needed.

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io") { content { includeGroupByRegex("com\\.github\\.nanzee73.*") } }
}
dependencies {
    implementation("com.github.nanzee73.C64GfxLib:c64gfx-swing:0.1.0")   // brings c64gfx-core along
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

### Working on the library and an app at the same time

Check out C64GfxLib next to the app and start Gradle with `-Pc64gfxLocal` (or put `c64gfxLocal=true` into the app's
`gradle.properties` locally): the app's `settings.gradle.kts` then includes `../C64GfxLib` as a composite build,
so library changes are used immediately without publishing.

## Releasing a new version

1. Change the library, push to `main` (GitHub Actions runs the tests).
2. *Actions ▸ Release ▸ Run workflow*, enter the version, e.g. `0.2.0`. The workflow runs the tests, sets the git
   tag `0.2.0` and waits until JitPack has built it. (A tag pushed from IntelliJ – *Git ▸ New Tag…*, then
   *Push Tags* – works as well; JitPack then builds on the first download.) Tags have no leading `v`.
3. In the apps raise `c64gfxVersion` in `build.gradle.kts`.

## Building

```
./gradlew build                 # compile + tests
./gradlew publishToMavenLocal   # into ~/.m2 for local experiments
```
