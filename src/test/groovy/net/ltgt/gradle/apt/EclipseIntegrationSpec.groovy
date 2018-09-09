/*
 * Copyright © 2018 Thomas Broyer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.ltgt.gradle.apt

import static net.ltgt.gradle.apt.IntegrationTestHelper.TEST_GRADLE_VERSION

import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import nebula.test.dependencies.ModuleBuilder
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.util.TextUtil
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class EclipseIntegrationSpec extends Specification {
  @Rule TemporaryFolder testProjectDir = new TemporaryFolder()
  File settingsFile
  File buildFile

  def setup() {
    settingsFile = testProjectDir.newFile('settings.gradle')
    buildFile = testProjectDir.newFile('build.gradle')
    buildFile << """\
      buildscript {
        repositories {
          maven { url = uri("${TextUtil.normaliseFileSeparators(new File('build/repository').absolutePath)}") }
        }
        dependencies {
          classpath 'net.ltgt.gradle:gradle-apt-plugin:${System.getProperty('plugin.version')}'
        }
      }
      apply plugin: 'net.ltgt.apt-eclipse'
    """.stripIndent()
  }

  def "eclipse without java"() {
    when:
    def result = GradleRunner.create()
        .withGradleVersion(TEST_GRADLE_VERSION)
        .withProjectDir(testProjectDir.root)
        .withArguments('eclipse')
        .build()

    then:
    result.task(':eclipseJdtApt') == null
    result.task(':eclipseFactorypath') == null
    !new File(testProjectDir.root, '.factorypath').exists()
  }

  def "eclipse task"() {
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
        compile                 'compile:compile:1.0'
        compileOnly             'annotations:compile:1.0'
        annotationProcessor     'processor:compile:1.0'
        testCompile             'testCompile:testCompile:1.0'
        testCompileOnly         'annotations:testCompile:1.0'
        testAnnotationProcessor 'processor:testCompile:1.0'
      }
    """.stripIndent()

    when:
    def result = GradleRunner.create()
        .withGradleVersion(TEST_GRADLE_VERSION)
        .withProjectDir(testProjectDir.root)
        .withArguments('eclipse')
        .build()

    then:
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
        .withGradleVersion(TEST_GRADLE_VERSION)
        .withProjectDir(testProjectDir.root)
        .withArguments('cleanEclipse')
        .build()

    then:
    result2.task(':cleanEclipseJdtApt').outcome == TaskOutcome.SUCCESS
    result2.task(':cleanEclipseFactorypath').outcome == TaskOutcome.SUCCESS
    !factorypath.exists()
    !new File(testProjectDir.root, '.settings/org.eclipse.jdt.apt.core.prefs').exists()
  }

  def loadProperties(String path) {
    def props = new Properties()
    new File(testProjectDir.root, path).withInputStream {
      props.load(it)
    }
    props
  }

  def "eclipseFactorypath task with project dependency"() {
    given:
    settingsFile << """\
      include 'processor'
    """.stripIndent()
    buildFile << """\
      allprojects {
        apply plugin: 'java'
      }
      dependencies {
        annotationProcessor project(':processor')
      }
    """.stripIndent()

    when:
    def result = GradleRunner.create()
        .withGradleVersion(TEST_GRADLE_VERSION)
        .withProjectDir(testProjectDir.root)
        .withArguments(':eclipseFactorypath')
        .build()

    then:
    result.task(':eclipseFactorypath').outcome == TaskOutcome.SUCCESS
    result.task(':processor:jar').outcome == TaskOutcome.SUCCESS
    def factorypath = new File(testProjectDir.root, '.factorypath')
    factorypath.exists()
    def entries = new XmlSlurper().parse(factorypath).factorypathentry
    entries.size() == 1
    entries.every { it.@kind == 'EXTJAR' && it.@enabled == true && it.@runInBatchMode == false }
    (entries.@id as Set).equals([
        "${testProjectDir.root}/processor/build/libs/processor.jar",
    ].collect { it.replace('/', File.separator) }.toSet())
  }

  def "eclipse task custom config"() {
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
          }
        }

        factorypath {
          file.whenMerged {
            entries << file('some/processor.jar')
          }
        }
      }
    """.stripIndent()

    when:
    def result = GradleRunner.create()
        .withGradleVersion(TEST_GRADLE_VERSION)
        .withProjectDir(testProjectDir.root)
        .withArguments('eclipse')
        .build()

    then:
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
  }

  def "tooling api"() {
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
        compile                 'compile:compile:1.0'
        compileOnly             'annotations:compile:1.0'
        annotationProcessor     'processor:compile:1.0'
        testCompile             'testCompile:testCompile:1.0'
        testCompileOnly         'annotations:testCompile:1.0'
        testAnnotationProcessor 'processor:testCompile:1.0'
      }
    """.stripIndent()

    when:
    ProjectConnection connection = GradleConnector.newConnector()
        .forProjectDirectory(testProjectDir.root)
        .useGradleVersion(TEST_GRADLE_VERSION)
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
