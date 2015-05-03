package net.ltgt.gradle.apt

import nebula.test.IntegrationSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import nebula.test.dependencies.ModuleBuilder
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.eclipse.EclipseProject

class EclipseIntegrationSpec extends IntegrationSpec {

  def 'eclipse without java'() {
    setup:
    buildFile << """\
      buildscript {
        dependencies {
          classpath files('${System.getProperty('plugin')}')
        }
      }
      apply plugin: 'net.ltgt.apt'
      apply plugin: 'eclipse'
    """.stripIndent()

    when:
    runTasksSuccessfully('eclipse')

    then:
    !fileExists('.factorypath')
  }

  def 'eclipse task'() {
    setup:
    def mavenRepo = new GradleDependencyGenerator(
        new DependencyGraphBuilder()
            .addModule(new ModuleBuilder('compile:compile:1.0')
                .addDependency('leaf:compile:1.0')
                .build())
            .addModule(new ModuleBuilder('testCompile:testCompile:1.0')
                .addDependency('leaf:testCompile:1.0')
                .build())
            .addModule('annotations:compile:1.0')
            .addModule('annotations:testCompile:1.0')
            .addModule(new ModuleBuilder('processor:compile:1.0')
                .addDependency('annotations:compile:1.0')
                .addDependency('leaf:compile:2.0')
                .build())
            .addModule(new ModuleBuilder('processor:testCompile:1.0')
                .addDependency('annotations:testCompile:1.0')
                .addDependency('leaf:testCompile:2.0')
                .build())
            .build(),
        directory('repo').path)
      .generateTestMavenRepo()

    buildFile << """\
      buildscript {
        dependencies {
          classpath files('${System.getProperty('plugin')}')
        }
      }
      apply plugin: 'net.ltgt.apt'
      apply plugin: 'java'
      apply plugin: 'eclipse'
      repositories {
        maven { url file('${mavenRepo}') }
      }
      dependencies {
        compile         'compile:compile:1.0'
        compileOnly     'annotations:compile:1.0'
        apt             'processor:compile:1.0'
        testCompile     'testCompile:testCompile:1.0'
        testCompileOnly 'annotations:testCompile:1.0'
        testApt         'processor:testCompile:1.0'
      }
    """.stripIndent()

    when:
    runTasksSuccessfully('eclipse')

    then:
    def factorypath = new File(projectDir, '.factorypath')
    factorypath.exists()
    def entries = new XmlSlurper().parse(factorypath).factorypathentry
    entries.size() == 6
    entries.every { it.@kind == 'EXTJAR' && it.@enabled == true && it.@runInBatchMode == false }
    (entries.@id as Set).equals([
        "$mavenRepo/leaf/compile/2.0/compile-2.0.jar",
        "$mavenRepo/annotations/compile/1.0/compile-1.0.jar",
        "$mavenRepo/processor/compile/1.0/compile-1.0.jar",
        "$mavenRepo/leaf/testCompile/2.0/testCompile-2.0.jar",
        "$mavenRepo/annotations/testCompile/1.0/testCompile-1.0.jar",
        "$mavenRepo/processor/testCompile/1.0/testCompile-1.0.jar",
    ] as Set)

    def jdtSettings = loadProperties('.settings/org.eclipse.jdt.core.prefs')
    jdtSettings.getProperty('org.eclipse.jdt.core.compiler.processAnnotations') == 'enabled'

    def aptSettings = loadProperties('.settings/org.eclipse.jdt.apt.core.prefs')
    aptSettings.getProperty('org.eclipse.jdt.apt.aptEnabled') == 'true'
    aptSettings.getProperty('org.eclipse.jdt.apt.genSrcDir') == '.apt_generated'
    aptSettings.getProperty('org.eclipse.jdt.apt.reconcileEnabled') == 'true'
  }

  def loadProperties(String path) {
    def props = new Properties()
    new File(projectDir, path).withInputStream {
      props.load(it)
    }
    props
  }

  def 'tooling api'() {
    setup:
    def mavenRepo = new GradleDependencyGenerator(
        new DependencyGraphBuilder()
            .addModule(new ModuleBuilder('compile:compile:1.0')
                .addDependency('leaf:compile:1.0')
                .build())
            .addModule(new ModuleBuilder('testCompile:testCompile:1.0')
                .addDependency('leaf:testCompile:1.0')
                .build())
            .addModule('annotations:compile:1.0')
            .addModule('annotations:testCompile:1.0')
            .addModule(new ModuleBuilder('processor:compile:1.0')
                .addDependency('annotations:compile:1.0')
                .addDependency('leaf:compile:2.0')
                .build())
            .addModule(new ModuleBuilder('processor:testCompile:1.0')
                .addDependency('annotations:testCompile:1.0')
                .addDependency('leaf:testCompile:2.0')
                .build())
            .build(),
        directory('repo').path)
        .generateTestMavenRepo()

    buildFile << """\
      buildscript {
        dependencies {
          classpath files('${System.getProperty('plugin')}')
        }
      }
      apply plugin: 'net.ltgt.apt'
      apply plugin: 'java'
      apply plugin: 'eclipse'
      repositories {
        maven { url file('${mavenRepo}') }
      }
      dependencies {
        compile         'compile:compile:1.0'
        compileOnly     'annotations:compile:1.0'
        apt             'processor:compile:1.0'
        testCompile     'testCompile:testCompile:1.0'
        testCompileOnly 'annotations:testCompile:1.0'
        testApt         'processor:testCompile:1.0'
      }
    """.stripIndent()

    when:
    ProjectConnection connection = GradleConnector.newConnector()
        .forProjectDirectory(projectDir)
        .connect()
    def classpath = connection.getModel(EclipseProject).classpath.collect {
      "${it.gradleModuleVersion.group}:${it.gradleModuleVersion.name}:${it.gradleModuleVersion.version}" as String
    }.toSet()

    then:
    classpath.contains('leaf:compile:1.0')
    classpath.contains('compile:compile:1.0')
    classpath.contains('annotations:compile:1.0')
    classpath.contains('leaf:testCompile:1.0')
    classpath.contains('testCompile:testCompile:1.0')
    classpath.contains('annotations:testCompile:1.0')
    !classpath.contains('leaf:compile:2.0')
    !classpath.contains('processor:compile:1.0')
    !classpath.contains('leaf:testCompile:2.0')
    !classpath.contains('processor:testCompile:1.0')

    cleanup:
    connection.close()
  }
}
