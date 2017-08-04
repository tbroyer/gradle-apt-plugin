package net.ltgt.gradle.apt

import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import nebula.test.dependencies.ModuleBuilder
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.eclipse.EclipseProject
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Unroll

class EclipseIntegrationSpec extends Specification {
  @Rule TemporaryFolder testProjectDir = new TemporaryFolder()
  File buildFile

  def setup() {
    buildFile = testProjectDir.newFile('build.gradle')
    buildFile << """\
      buildscript {
        dependencies {
          classpath files(\$/${System.getProperty('plugin')}/\$)
        }
      }
      apply plugin: 'net.ltgt.apt-eclipse'
    """.stripIndent()
  }

  @Unroll
  def "eclipse without java, with Gradle #gradleVersion"() {
    when:
    def result = GradleRunner.create()
        .withGradleVersion(gradleVersion)
        .withProjectDir(testProjectDir.root)
        .withArguments('eclipse')
        .build()

    then:
    result.task(':eclipse').outcome == TaskOutcome.SUCCESS
    result.task(':eclipseJdtApt') == null
    result.task(':eclipseFactorypath') == null
    !new File(testProjectDir.root, '.factorypath').exists()

    where:
    gradleVersion << IntegrationTestHelper.GRADLE_VERSIONS
  }

  @Unroll
  def "eclipse task, with Gradle #gradleVersion"() {
    given:
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
        testProjectDir.newFolder('repo').path)
      .generateTestMavenRepo()

    buildFile << """\
      apply plugin: 'java'
      repositories {
        maven { url file(\$/${mavenRepo}/\$) }
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
    def result = GradleRunner.create()
        .withGradleVersion(gradleVersion)
        .withProjectDir(testProjectDir.root)
        .withArguments('eclipse')
        .build()

    then:
    result.task(':eclipse').outcome == TaskOutcome.SUCCESS
    result.task(':eclipseJdtApt').outcome == TaskOutcome.SUCCESS
    result.task(':eclipseFactorypath').outcome == TaskOutcome.SUCCESS
    def factorypath = new File(testProjectDir.root, '.factorypath')
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
    ].collect { it.replace('/', File.separator) }.toSet())

    def jdtSettings = loadProperties('.settings/org.eclipse.jdt.core.prefs')
    jdtSettings.getProperty('org.eclipse.jdt.core.compiler.processAnnotations') == 'enabled'

    def aptSettings = loadProperties('.settings/org.eclipse.jdt.apt.core.prefs')
    aptSettings.getProperty('org.eclipse.jdt.apt.aptEnabled') == 'true'
    aptSettings.getProperty('org.eclipse.jdt.apt.genSrcDir') == '.apt_generated'
    aptSettings.getProperty('org.eclipse.jdt.apt.reconcileEnabled') == 'true'

    when:
    def result2 = GradleRunner.create()
        .withGradleVersion(gradleVersion)
        .withProjectDir(testProjectDir.root)
        .withArguments('cleanEclipse')
        .build()

    then:
    result2.task(':cleanEclipse').outcome == TaskOutcome.SUCCESS
    result2.task(':cleanEclipseJdtApt').outcome == TaskOutcome.SUCCESS
    result2.task(':cleanEclipseFactorypath').outcome == TaskOutcome.SUCCESS
    !factorypath.exists()
    !new File(testProjectDir.root, '.settings/org.eclipse.jdt.apt.core.prefs').exists()

    where:
    gradleVersion << IntegrationTestHelper.GRADLE_VERSIONS
  }

  def loadProperties(String path) {
    def props = new Properties()
    new File(testProjectDir.root, path).withInputStream {
      props.load(it)
    }
    props
  }

  @Unroll
  def "eclipse task custom config, with Gradle #gradleVersion"() {
    given:
    buildFile << """\
      apply plugin: 'java'
      compileJava {
        aptOptions.processorArgs = [
          'foo': 'bar',
          'baz': 'willBeOverwritten',
          'hasNullValue': null,
        ]
      }
      compileTestJava {
        aptOptions.processorArgs = [ 'ignoredOption': 'from compileTestJava' ]
      }
      eclipse {
        jdt {
          apt {
            aptEnabled = false
            genSrcDir = file('whatever')
            reconcileEnabled = false
            file.whenMerged {
              processorOptions.baz = 'qux'
            }

            factorypath {
              file.whenMerged {
                entries << file('some/processor.jar')
              }
            }
          }
        }
      }
    """.stripIndent()

    when:
    def result = GradleRunner.create()
        .withGradleVersion(gradleVersion)
        .withProjectDir(testProjectDir.root)
        .withArguments('eclipse')
        .build()

    then:
    result.task(':eclipse').outcome == TaskOutcome.SUCCESS
    result.task(':eclipseJdtApt').outcome == TaskOutcome.SUCCESS
    result.task(':eclipseFactorypath').outcome == TaskOutcome.SUCCESS
    def factorypath = new File(testProjectDir.root, '.factorypath')
    factorypath.exists()
    def entries = new XmlSlurper().parse(factorypath).factorypathentry
    entries.size() == 1
    entries.every { it.@kind == 'EXTJAR' && it.@enabled == true && it.@runInBatchMode == false }
    (entries.@id as Set).equals([
        "${testProjectDir.root}/some/processor.jar",
    ].collect { it.replace('/', File.separator) }.toSet())

    def jdtSettings = loadProperties('.settings/org.eclipse.jdt.core.prefs')
    jdtSettings.getProperty('org.eclipse.jdt.core.compiler.processAnnotations') == 'disabled'

    def aptSettings = loadProperties('.settings/org.eclipse.jdt.apt.core.prefs')
    aptSettings.getProperty('org.eclipse.jdt.apt.aptEnabled') == 'false'
    aptSettings.getProperty('org.eclipse.jdt.apt.genSrcDir') == 'whatever'
    aptSettings.getProperty('org.eclipse.jdt.apt.reconcileEnabled') == 'false'
    aptSettings.getProperty('org.eclipse.jdt.apt.processorOptions/foo') == 'bar'
    aptSettings.getProperty('org.eclipse.jdt.apt.processorOptions/baz') == 'qux'
    aptSettings.getProperty('org.eclipse.jdt.apt.processorOptions/hasNullValue') == 'org.eclipse.jdt.apt.NULLVALUE'
    !aptSettings.containsKey('org.eclipse.jdt.apt.processorOptions/ignoredOption')

    where:
    gradleVersion << IntegrationTestHelper.GRADLE_VERSIONS
  }

  @Unroll
  def "tooling api, with Gradle #gradleVersion"() {
    given:
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
        testProjectDir.newFolder('repo').path)
        .generateTestMavenRepo()

    buildFile << """\
      apply plugin: 'java'
      repositories {
        maven { url file(\$/${mavenRepo}/\$) }
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
        .forProjectDirectory(testProjectDir.root)
        .useGradleVersion(gradleVersion)
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

    where:
    gradleVersion << IntegrationTestHelper.GRADLE_VERSIONS
  }
}
