// C64GfxLib – shared C64 graphics code for FlipperJ and MuleJ.
//   c64gfx-core  : palettes, colour matching, dithering, C64 formats, 6502 assembler (no AWT/Swing)
//   c64gfx-swing : I18n, image panel, file dialogs, slider controls, palette editor, image fitting
//
// Distribution: JitPack builds a git tag (e.g. 0.2.0) on first request, no login needed:
//   repositories { maven("https://jitpack.io") }
//   implementation("com.github.nanzee73.C64GfxLib:c64gfx-swing:0.2.0")
// JitPack passes the tag in the environment variable VERSION. Local default: 0.0.0-SNAPSHOT.
// The group must stay com.github.nanzee73.C64GfxLib: JitPack serves it under that name, and the apps'
// composite build (-Pc64gfxLocal) replaces the dependency only when group and name match.

val libVersion = System.getenv("VERSION")?.takeIf { System.getenv("JITPACK") == "true" }
    ?: (findProperty("libVersion") as String?) ?: "0.0.0-SNAPSHOT"

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    group = "com.github.nanzee73.C64GfxLib"
    version = libVersion

    repositories { mavenCentral() }

    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        withSourcesJar()
        withJavadocJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(17)
    }
    tasks.withType<Javadoc>().configureEach {
        (options as StandardJavadocDocletOptions).apply {
            encoding = "UTF-8"
            addStringOption("Xdoclint:none", "-quiet")
        }
    }

    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:5.11.3"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }
    tasks.withType<Test>().configureEach { useJUnitPlatform() }

    extensions.configure<PublishingExtension> {
        publications {
            create<MavenPublication>("lib") {
                from(components["java"])
                pom {
                    name.set(project.name)
                    description.set("Shared C64 graphics code for FlipperJ and MuleJ")
                    url.set("https://github.com/nanzee73/C64GfxLib")
                    licenses { license { name.set("MIT License"); url.set("https://opensource.org/licenses/MIT") } }
                }
            }
        }
    }
}

project(":c64gfx-swing") {
    dependencies { "api"(project(":c64gfx-core")) }
}
