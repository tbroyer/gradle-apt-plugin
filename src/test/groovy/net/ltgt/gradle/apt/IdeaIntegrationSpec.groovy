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
import org.gradle.tooling.model.idea.IdeaProject
import org.gradle.util.GradleVersion
import org.gradle.util.TextUtil
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class IdeaIntegrationSpec extends Specification {
  @Rule TemporaryFolder testProjectDir = new TemporaryFolder()
  File settingsFile, buildFile
  String moduleName = 'testProject'

  def setup() {
    settingsFile = testProjectDir.newFile('settings.gradle')
    settingsFile << """\
      rootProject.name = '${moduleName}'
    """.stripIndent()
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
      apply plugin: 'net.ltgt.apt-idea'
    """.stripIndent()
  }

  def "idea without java"() {
    when:
    def result = GradleRunner.create()
        .withGradleVersion(TEST_GRADLE_VERSION)
        .withProjectDir(testProjectDir.root)
        .withArguments('idea')
        .build()

    then:
    result.task(':ideaProject').outcome == TaskOutcome.SUCCESS
    result.task(':ideaModule').outcome == TaskOutcome.SUCCESS
    hasAnnotationProcessingConfigured(true)
  }

  void hasAnnotationProcessingConfigured(boolean expected) {
    with (new XmlSlurper().parse(new File(testProjectDir.root, "${moduleName}.ipr")).component.find { it.@name == 'CompilerConfiguration' }
              .annotationProcessing.profile) {
      assert expected == (it.size() == 1)
      assert expected == (it.@default == true)
      assert expected == (it.@enabled == true)
      assert expected == (it.sourceOutputDir.@name == 'build/generated/source/apt/main')
      assert expected == (it.sourceTestOutputDir.@name == 'build/generated/source/apt/test')
      assert expected == (it.outputRelativeToContentRoot.@value == true)
      assert expected == (it.processorPath.@useClasspath == true)
    }
  }

  def "idea without java, configureAnnotationProcessing = false"() {
    setup:
    buildFile << """
      idea {
        project {
          configureAnnotationProcessing = false
        }
      }
    """.stripIndent()

    when:
    def result = GradleRunner.create()
        .withGradleVersion(TEST_GRADLE_VERSION)
        .withProjectDir(testProjectDir.root)
        .withArguments('idea')
        .build()

    then:
    result.task(':ideaProject').outcome == TaskOutcome.SUCCESS
    result.task(':ideaModule').outcome == TaskOutcome.SUCCESS
    hasAnnotationProcessingConfigured(false)
  }

  def "idea task"() {
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
        .withArguments('idea')
        .build()

    then:
    result.task(':ideaModule').outcome == TaskOutcome.SUCCESS
    hasAnnotationProcessingConfigured(true)
    // TODO: check IML for content roots and dependencies
  }

  def "idea task, all configurations disabled"() {
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
        idea {
          project {
            configureAnnotationProcessing = false
          }
          module {
            apt {
              addGeneratedSourcesDirs = false
              addAptDependencies = false
            }
          }
        }
    """.stripIndent()

    when:
    def result = GradleRunner.create()
        .withGradleVersion(TEST_GRADLE_VERSION)
        .withProjectDir(testProjectDir.root)
        .withArguments('idea')
        .build()

    then:
    result.task(':ideaModule').outcome == TaskOutcome.SUCCESS
    hasAnnotationProcessingConfigured(false)
    // TODO: check IML for content roots and dependencies
  }

  def "ideaModule task with project dependency"() {
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
        .withArguments(':ideaModule')
        .build()

    then:
    result.task(':ideaModule').outcome == TaskOutcome.SUCCESS
    result.task(':processor:jar').outcome == TaskOutcome.SUCCESS
    // TODO: check IML for content roots and dependencies
  }

  def "ideaModule task with project dependency, all configurations disabled"() {
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
      idea {
        module {
          apt {
            addAptDependencies = false
          }
        }
      }
    """.stripIndent()

    when:
    def result = GradleRunner.create()
        .withGradleVersion(TEST_GRADLE_VERSION)
        .withProjectDir(testProjectDir.root)
        .withArguments(':ideaModule')
        .build()

    then:
    result.task(':ideaModule').outcome == TaskOutcome.SUCCESS
    result.task(':processor:jar') == null
    // TODO: check IML for content roots and dependencies
  }

  def "tooling api"() {
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
    def ideaModule = connection.getModel(IdeaProject).modules[0]

    then:
    ideaModule.contentRoots*.generatedSourceDirectories*.directory.flatten()
        .contains(new File(testProjectDir.root, 'build/generated/source/apt/main'))
    ideaModule.contentRoots*.generatedTestDirectories*.directory.flatten()
        .contains(new File(testProjectDir.root, 'build/generated/source/apt/test'))
    !ideaModule.contentRoots*.excludeDirectories.flatten()
        .contains(new File(testProjectDir.root, 'build'))
    // XXX: We can't test buildDir subdirectories unless we also build the project, should we?

    def dependencies = ideaModule.dependencies.collect {
      "${it.gradleModuleVersion.group}:${it.gradleModuleVersion.name}:${it.gradleModuleVersion.version}:${it.scope.scope}" as String
    }
    // XXX: it's unfortunate that we have both versions of "leaf" artifacts, but we can't easily do otherwise
    if (GradleVersion.version(TEST_GRADLE_VERSION) >= GradleVersion.version("3.4")) {
      dependencies.contains('leaf:compile:1.0:PROVIDED')
      dependencies.contains('compile:compile:1.0:PROVIDED')
      dependencies.contains('annotations:compile:1.0:PROVIDED')
      dependencies.contains('leaf:compile:2.0:PROVIDED')
      dependencies.contains('processor:compile:1.0:PROVIDED')
      dependencies.contains('leaf:compile:1.0:RUNTIME')
      dependencies.contains('compile:compile:1.0:RUNTIME')
      dependencies.contains('leaf:testCompile:1.0:TEST')
      dependencies.contains('testCompile:testCompile:1.0:TEST')
      dependencies.contains('annotations:testCompile:1.0:TEST')
      dependencies.contains('leaf:testCompile:2.0:TEST')
      dependencies.contains('processor:testCompile:1.0:TEST')
    } else {
      dependencies.contains('leaf:compile:1.0:COMPILE')
      dependencies.contains('compile:compile:1.0:COMPILE')
      dependencies.contains('annotations:compile:1.0:COMPILE')
      dependencies.contains('leaf:compile:2.0:COMPILE')
      dependencies.contains('processor:compile:1.0:COMPILE')
      dependencies.contains('leaf:testCompile:1.0:TEST')
      dependencies.contains('testCompile:testCompile:1.0:TEST')
      dependencies.contains('annotations:testCompile:1.0:TEST')
      dependencies.contains('leaf:testCompile:2.0:TEST')
      dependencies.contains('processor:testCompile:1.0:TEST')
    }

    cleanup:
    connection.close()
  }

  def "tooling api, all configurations disabled"() {
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
        idea {
          project {
            configureAnnotationProcessing = false
          }
          module {
            apt {
              addGeneratedSourcesDirs = false
              addAptDependencies = false
            }
          }
        }
    """.stripIndent()

    when:
    ProjectConnection connection = GradleConnector.newConnector()
        .forProjectDirectory(testProjectDir.root)
        .useGradleVersion(TEST_GRADLE_VERSION)
        .connect()
    def ideaModule = connection.getModel(IdeaProject).modules[0]

    then:
    !ideaModule.contentRoots*.generatedSourceDirectories*.directory.flatten()
        .contains(new File(testProjectDir.root, 'build/generated/source/apt/main'))
    !ideaModule.contentRoots*.generatedTestDirectories*.directory.flatten()
        .contains(new File(testProjectDir.root, 'build/generated/source/apt/test'))
    ideaModule.contentRoots*.excludeDirectories.flatten()
        .contains(new File(testProjectDir.root, 'build'))

    def dependencies = ideaModule.dependencies.collect {
      "${it.gradleModuleVersion.group}:${it.gradleModuleVersion.name}:${it.gradleModuleVersion.version}:${it.scope.scope}" as String
    }
    if (GradleVersion.version(TEST_GRADLE_VERSION) >= GradleVersion.version("3.4")) {
      dependencies.contains('leaf:compile:1.0:PROVIDED')
      dependencies.contains('compile:compile:1.0:PROVIDED')
      dependencies.contains('annotations:compile:1.0:PROVIDED')
      !dependencies.any { dep -> dep.startsWith('leaf:compile:2.0:') }
      !dependencies.any { dep -> dep.startsWith('processor:compile:1.0:') }
      dependencies.contains('leaf:compile:1.0:RUNTIME')
      dependencies.contains('compile:compile:1.0:RUNTIME')
      dependencies.contains('leaf:testCompile:1.0:TEST')
      dependencies.contains('testCompile:testCompile:1.0:TEST')
      dependencies.contains('annotations:testCompile:1.0:TEST')
      !dependencies.any { dep -> dep.startsWith('leaf:testCompile:2.0:') }
      !dependencies.any { dep -> dep.startsWith('processor:testCompile:1.0:') }
    } else {
      dependencies.contains('leaf:compile:1.0:COMPILE')
      dependencies.contains('compile:compile:1.0:COMPILE')
      dependencies.contains('annotations:compile:1.0:COMPILE')
      !dependencies.any { dep -> dep.startsWith('leaf:compile:2.0:') }
      !dependencies.any { dep -> dep.startsWith('processor:compile:1.0:') }
      dependencies.contains('leaf:testCompile:1.0:TEST')
      dependencies.contains('testCompile:testCompile:1.0:TEST')
      dependencies.contains('annotations:testCompile:1.0:TEST')
      !dependencies.any { dep -> dep.startsWith('leaf:testCompile:2.0:') }
      !dependencies.any { dep -> dep.startsWith('processor:testCompile:1.0:') }
    }

    cleanup:
    connection.close()
  }
}
