import net.ltgt.gradle.errorprone.javacplugin.errorprone
import java.util.concurrent.Callable
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    `java-gradle-plugin`
    `maven-publish`
    groovy
    id("com.gradle.plugin-publish") version "0.10.0"
    id("com.github.sherter.google-java-format") version "0.7.1"
    id("net.ltgt.errorprone-javacplugin") version "0.5"
}

googleJavaFormat {
    toolVersion = "1.6"
}

group = "net.ltgt.gradle"

if (JavaVersion.current().isJava9Compatible) {
    tasks.withType<JavaCompile>().configureEach { options.compilerArgs.addAll(arrayOf("--release", "7")) }
    tasks.withType<GroovyCompile>().configureEach { options.compilerArgs.addAll(arrayOf("--release", "7")) }
}

gradle.taskGraph.whenReady {
    if (hasTask(":publishPlugins")) {
        check(JavaVersion.current().isJava9Compatible) { "Releases must be built with JDK 9" }

        check("git diff --quiet --exit-code".execute(null, rootDir).waitFor() == 0) { "Working tree is dirty" }
        val process = "git describe --exact-match".execute(null, rootDir)
        check(process.waitFor() == 0) { "Version is not tagged" }
        version = process.text.trim().removePrefix("v")
    }
}

repositories {
    jcenter()
}

dependencies {
    errorprone("com.google.errorprone:error_prone_core:2.3.1")
    errorproneJavac("com.google.errorprone:javac:9+181-r4173-1")

    annotationProcessor("com.uber.nullaway:nullaway:0.5.4")

    testImplementation(localGroovy())
    testImplementation("com.netflix.nebula:nebula-test:6.7.1")
    testImplementation("org.spockframework:spock-core:1.1-groovy-2.4") {
        exclude(group = "org.codehaus.groovy")
    }
}

// See https://github.com/gradle/kotlin-dsl/issues/492
publishing {
    repositories {
        maven(url = "$buildDir/repository") {
            name = "test"
        }
    }
}

tasks {
    withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(arrayOf("-Xlint:all", "-Werror"))
        options.errorprone.option("NullAway:AnnotatedPackages", "net.ltgt.gradle.apt")
    }

    "jar"(Jar::class) {
        from(Callable { project(":kotlin-extensions").sourceSets["main"].output })
    }

    val publishPluginsToTestRepository by registering {
        dependsOn("publishPluginMavenPublicationToTestRepository")
        dependsOn("publishAptPluginMarkerMavenPublicationToTestRepository")
        dependsOn("publishAptEclipsePluginMarkerMavenPublicationToTestRepository")
        dependsOn("publishAptIdeaPluginMarkerMavenPublicationToTestRepository")
    }

    "test"(Test::class) {
        dependsOn(publishPluginsToTestRepository)

        val testGradleVersion = project.findProperty("test.gradle-version")
        testGradleVersion?.also { systemProperty("test.gradle-version", testGradleVersion) }

        systemProperty("plugin.version", version)

        testLogging {
            showExceptions = true
            showStackTraces = true
            exceptionFormat = TestExceptionFormat.FULL
        }
    }
}

gradlePlugin {
    plugins {
        register("apt") {
            id = "net.ltgt.apt"
            displayName = "Gradle APT plugin"
            implementationClass = "net.ltgt.gradle.apt.AptPlugin"
        }
        register("aptEclipse") {
            id = "net.ltgt.apt-eclipse"
            displayName = "Gradle APT plugin (Eclipse integration)"
            implementationClass = "net.ltgt.gradle.apt.AptEclipsePlugin"
        }
        register("aptIdea") {
            id = "net.ltgt.apt-idea"
            displayName = "Gradle APT plugin (IDEA integration)"
            implementationClass = "net.ltgt.gradle.apt.AptIdeaPlugin"
        }
    }
}

pluginBundle {
    website = "https://github.com/tbroyer/gradle-apt-plugin"
    vcsUrl = "https://github.com/tbroyer/gradle-apt-plugin"
    description = "Gradle plugin making it easier/safer to use Java annotation processors"
    tags = listOf("annotation-processing", "annotation-processors", "apt")

    mavenCoordinates {
        groupId = project.group.toString()
        artifactId = project.name
    }
}

val ktlint by configurations.creating

dependencies {
    ktlint("com.github.shyiko:ktlint:0.27.0")
}

tasks {
    val verifyKtlint by registering(JavaExec::class) {
        description = "Check Kotlin code style."
        classpath = ktlint
        main = "com.github.shyiko.ktlint.Main"
        args("**/*.gradle.kts", "**/*.kt")
    }
    "check" {
        dependsOn(verifyKtlint)
    }

    register("ktlint", JavaExec::class) {
        description = "Fix Kotlin code style violations."
        classpath = ktlint
        main = "com.github.shyiko.ktlint.Main"
        args("-F", "**/*.gradle.kts", "**/*.kt")
    }
}

fun String.execute(envp: Array<String>?, workingDir: File?) =
    Runtime.getRuntime().exec(this, envp, workingDir)

val Process.text: String
    get() = inputStream.bufferedReader().readText()

apply(from = "gradle/circleci.gradle.kts")
