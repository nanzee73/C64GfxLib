// C64GfxLib – shared C64 graphics code for FlipperJ and MuleJ.
//   c64gfx-core  : palettes, colour matching, dithering, C64 formats, 6502 assembler (no AWT/Swing)
//   c64gfx-swing : I18n, image panel, file dialogs, slider controls, palette editor, image fitting
//
// Version: -PlibVersion=1.2.3 (the publish workflow passes the tag without "v"), default 0.0.0-SNAPSHOT.
// Publish: ./gradlew publish  → GitHub Packages of nanzee73/C64GfxLib (needs GITHUB_ACTOR/GITHUB_TOKEN or
//          gpr.user/gpr.key in ~/.gradle/gradle.properties)
// Local:   ./gradlew publishToMavenLocal → apps can use it with -PuseMavenLocal or a composite build.

val libVersion = (findProperty("libVersion") as String?) ?: "0.0.0-SNAPSHOT"

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    group = "de.c64gfx"
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
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/nanzee73/C64GfxLib")
                credentials {
                    username = (findProperty("gpr.user") as String?) ?: System.getenv("GITHUB_ACTOR")
                    password = (findProperty("gpr.key") as String?) ?: System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }
}

project(":c64gfx-swing") {
    dependencies { "api"(project(":c64gfx-core")) }
}
