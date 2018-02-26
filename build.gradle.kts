import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    `java-gradle-plugin`
    groovy
    id("com.gradle.plugin-publish") version "0.9.10"
    id("net.ltgt.errorprone") version "0.0.13"
    id("com.github.sherter.google-java-format") version "0.6"
}

googleJavaFormat {
    toolVersion = "1.5"
}

group = "net.ltgt.gradle"

if (JavaVersion.current().isJava9Compatible) {
    tasks.withType<JavaCompile> { options.compilerArgs.addAll(arrayOf("--release", "7")) }
    tasks.withType<GroovyCompile> { options.compilerArgs.addAll(arrayOf("--release", "7")) }
}
gradle.taskGraph.whenReady {
    if (hasTask("publishPlugins")) {
        assert(JavaVersion.current().isJava9Compatible, { "Releases must be built with JDK 9" })

        assert("git diff --quiet --exit-code".execute(null, rootDir).waitFor() == 0, { "Working tree is dirty" })
        val process = "git describe --exact-match".execute(null, rootDir)
        assert(process.waitFor() == 0, { "Version is not tagged" })
        version = process.text.trim().removePrefix("v")
    }
}

repositories {
    jcenter()
}

dependencies {
    errorprone("com.google.errorprone:error_prone_core:2.2.0")

    testImplementation(localGroovy())
    testImplementation("com.netflix.nebula:nebula-test:6.1.2")
    testImplementation("org.spockframework:spock-core:1.1-groovy-2.4") {
        exclude(group = "org.codehaus.groovy")
    }
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(arrayOf("-Xlint:all", "-Werror"))
}

val test by tasks.getting(Test::class) {
    val testGradleVersions = project.findProperty("test.gradle-versions") as? String
    val jar: Jar by tasks.getting

    dependsOn(jar)
    inputs.file(jar.archivePath).withPathSensitivity(PathSensitivity.NONE)
    inputs.property("test.gradle-versions", testGradleVersions).optional(true)

    systemProperty("plugin", jar.archivePath)
    if (!testGradleVersions.isNullOrBlank()) {
        systemProperty("test.gradle-versions", testGradleVersions!!)
    }

    testLogging {
        showExceptions = true
        showStackTraces = true
        exceptionFormat = TestExceptionFormat.FULL
    }
}

gradlePlugin {
    (plugins) {
        "aptPlugin" {
            id = "net.ltgt.apt"
            implementationClass = "net.ltgt.gradle.apt.AptPlugin"
        }
        "aptEclipsePlugin" {
            id = "net.ltgt.apt-eclipse"
            implementationClass = "net.ltgt.gradle.apt.AptEclipsePlugin"
        }
        "aptIdeaPlugin" {
            id = "net.ltgt.apt-idea"
            implementationClass = "net.ltgt.gradle.apt.AptIdeaPlugin"
        }
    }
}

pluginBundle {
    website = "https://github.com/tbroyer/gradle-apt-plugin"
    vcsUrl = "https://github.com/tbroyer/gradle-apt-plugin"
    description = "Gradle plugin making it easier/safer to use Java annotation processors"
    tags = listOf("annotation-processing", "annotation-processors", "apt")

    (plugins) {
        "aptPlugin" {
            id = "net.ltgt.apt"
            displayName = "Gradle APT plugin"
        }
        "aptEclipsePlugin" {
            id = "net.ltgt.apt-eclipse"
            displayName = "Gradle APT plugin (Eclipse integration)"
        }
        "aptIdeaPlugin" {
            id = "net.ltgt.apt-idea"
            displayName = "Gradle APT plugin (IDEA integration)"
        }
    }

    mavenCoordinates {
        groupId = project.group.toString()
        artifactId = project.name
    }
}

fun String.execute(envp: Array<String>?, workingDir: File?) =
    Runtime.getRuntime().exec(this, envp, workingDir)

val Process.text: String
    get() = inputStream.bufferedReader().readText()
