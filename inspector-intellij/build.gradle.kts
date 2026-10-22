import net.ltgt.gradle.errorprone.CheckSeverity
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    // Builds, runs and packages IntelliJ Platform plugins. Apache-2.0.
    id("org.jetbrains.intellij.platform") version "2.19.0"
    // Runs Error Prone and NullAway, as the Maven build does. Apache-2.0.
    id("net.ltgt.errorprone") version "5.1.1"
}

group = "io.github.fereshtehdev"
// Releases pass the tag's version with -PpluginVersion.
version = providers.gradleProperty("pluginVersion").getOrElse("0.1.0-SNAPSHOT")

// The plugin runs inside the IDE's own Java, which is Java 21 for the 2025.x IDEs (ADR-0004).
tasks.withType<JavaCompile>().configureEach {
    options.release = 21
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all,-processing,-serial", "-Werror"))
    options.errorprone {
        disableWarningsInGeneratedCode = true
        check("NullAway", CheckSeverity.ERROR)
        option("NullAway:AnnotatedPackages", "io.github.fereshtehdev.springinspector")
        option("NullAway:JSpecifyMode", "true")
    }
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

val localIde = providers.gradleProperty("localIde")

dependencies {
    intellijPlatform {
        if (localIde.isPresent) {
            local(localIde.get())
        } else {
            intellijIdea(providers.gradleProperty("platformVersion"))
        }
    }
    // Reads the command line's JSON report with the streaming parser only. Apache-2.0.
    implementation("tools.jackson.core:jackson-core:3.1.7")
    compileOnly("org.jspecify:jspecify:1.0.0")
    errorprone("com.google.errorprone:error_prone_core:2.50.0")
    errorprone("com.uber.nullaway:nullaway:0.14.2")

    testCompileOnly("org.jspecify:jspecify:1.0.0")
    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.27.7")
}

intellijPlatform {
    buildSearchableOptions = false
    instrumentCode = false
    // Checks binary compatibility with the oldest supported IDE, the last Community edition and the target; CI runs it.
    pluginVerification {
        ides {
            create(IntelliJPlatformType.IntellijIdeaCommunity, "2025.1")
            create(IntelliJPlatformType.IntellijIdeaCommunity, "2025.2")
            current()
        }
    }
    pluginConfiguration {
        changeNotes = providers.fileContents(layout.projectDirectory.file("../CHANGELOG.md")).asText.map { changelog ->
            // The newest section of CHANGELOG.md as simple HTML. Kept inside the lambda for the configuration cache.
            fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("**", "").replace("`", "")
            val section = changelog.substringAfter("\n## ", "").substringBefore("\n## ")
            val html = StringBuilder()
            var inList = false
            for (raw in section.lines().drop(1)) {
                val line = raw.trim()
                val item = line.startsWith("- ")
                val continuation = raw.startsWith("  ") && inList
                if (inList && (item || !continuation)) html.append("</li>")
                if (inList && !item && !continuation) {
                    html.append("</ul>")
                    inList = false
                }
                when {
                    line.startsWith("### ") -> html.append("<h3>").append(escape(line.removePrefix("### "))).append("</h3>")
                    item -> {
                        if (!inList) html.append("<ul>")
                        inList = true
                        html.append("<li>").append(escape(line.removePrefix("- ")))
                    }
                    continuation -> html.append(" ").append(escape(line))
                    line.isNotEmpty() -> html.append("<p>").append(escape(line)).append("</p>")
                }
            }
            if (inList) html.append("</li></ul>")
            html.toString()
        }
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }
}

// The plugin runs the command line in its own process; it ships inside the plugin as cli/spring-inspector.jar.
tasks.prepareSandbox {
    val cliJar = layout.projectDirectory.file("../inspector-cli/target/spring-inspector.jar").asFile
    from(cliJar) {
        into(intellijPlatform.projectName.map { "$it/cli" })
    }
    doFirst {
        check(cliJar.isFile) { "Build the command line first: ./mvnw package (from the repository root)" }
    }
}

tasks.test {
    useJUnitPlatform()
}
