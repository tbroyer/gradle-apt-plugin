import net.ltgt.gradle.errorprone.javacplugin.errorprone
import java.util.concurrent.Callable
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    `java-gradle-plugin`
    groovy
    id("com.gradle.plugin-publish") version "0.9.10"
    id("com.github.sherter.google-java-format") version "0.6"

    id("net.ltgt.errorprone") version "0.0.13" apply false
    id("net.ltgt.errorprone-javacplugin") version "0.2" apply false
}

googleJavaFormat {
    toolVersion = "1.6"
}

group = "net.ltgt.gradle"

if (JavaVersion.current().isJava9Compatible) {
    apply(plugin = "net.ltgt.errorprone-javacplugin")
    tasks.getByName<JavaCompile>("compileJava").options.errorprone.option("NullAway:AnnotatedPackages", "net.ltgt.gradle.apt")

    tasks.withType<JavaCompile> { options.compilerArgs.addAll(arrayOf("--release", "7")) }
    tasks.withType<GroovyCompile> { options.compilerArgs.addAll(arrayOf("--release", "7")) }
} else {
    apply(plugin = "net.ltgt.errorprone")
    tasks.getByName<JavaCompile>("compileJava").options.compilerArgs.add("-XepOpt:NullAway:AnnotatedPackages=net.ltgt.gradle.apt")
}
gradle.taskGraph.whenReady {
    val publishPlugins by tasks.getting
    if (hasTask(publishPlugins)) {
        check(JavaVersion.current().isJava9Compatible, { "Releases must be built with JDK 9" })

        check("git diff --quiet --exit-code".execute(null, rootDir).waitFor() == 0, { "Working tree is dirty" })
        val process = "git describe --exact-match".execute(null, rootDir)
        check(process.waitFor() == 0, { "Version is not tagged" })
        version = process.text.trim().removePrefix("v")
    }
}

repositories {
    jcenter()
}

dependencies {
    "errorprone"("com.google.errorprone:error_prone_core:2.3.1")

    annotationProcessor("com.uber.nullaway:nullaway:0.4.7")

    testImplementation(localGroovy())
    testImplementation("com.netflix.nebula:nebula-test:6.4.2")
    testImplementation("org.spockframework:spock-core:1.1-groovy-2.4") {
        exclude(group = "org.codehaus.groovy")
    }
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(arrayOf("-Xlint:all", "-Werror"))
}

val jar by tasks.getting(Jar::class) {
    from(Callable { project(":kotlin-extensions").java.sourceSets["main"].output })
}

val test by tasks.getting(Test::class) {
    val testGradleVersion = project.findProperty("test.gradle-version")
    testGradleVersion?.also { systemProperty("test.gradle-version", testGradleVersion) }

    dependsOn(jar)
    inputs.file(jar.archivePath).withPathSensitivity(PathSensitivity.NONE)

    systemProperty("plugin", jar.archivePath)

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

val ktlint by configurations.creating

dependencies {
    ktlint("com.github.shyiko:ktlint:0.22.0")
}

val verifyKtlint by tasks.creating(JavaExec::class) {
    description = "Check Kotlin code style."
    classpath = ktlint
    main = "com.github.shyiko.ktlint.Main"
    args("**/*.gradle.kts", "**/*.kt")
}
tasks["check"].dependsOn(verifyKtlint)

task("ktlint", JavaExec::class) {
    description = "Fix Kotlin code style violations."
    classpath = verifyKtlint.classpath
    main = verifyKtlint.main
    args("-F")
    args(verifyKtlint.args)
}

fun String.execute(envp: Array<String>?, workingDir: File?) =
    Runtime.getRuntime().exec(this, envp, workingDir)

val Process.text: String
    get() = inputStream.bufferedReader().readText()

apply(from = "gradle/circleci.gradle.kts")
