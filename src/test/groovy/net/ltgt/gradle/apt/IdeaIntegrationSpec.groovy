package net.ltgt.gradle.apt

import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import nebula.test.dependencies.ModuleBuilder
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.idea.IdeaProject
import org.gradle.util.GradleVersion
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Unroll

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
        dependencies {
          classpath files(\$/${System.getProperty('plugin')}/\$)
        }
      }
      apply plugin: 'net.ltgt.apt-idea'
    """.stripIndent()
  }

  @Unroll
  def "idea without java, with Gradle #gradleVersion"() {
    when:
    def result = GradleRunner.create()
        .withGradleVersion(gradleVersion)
        .withProjectDir(testProjectDir.root)
        .withArguments('idea')
        .build()

    then:
    result.task(':idea').outcome == TaskOutcome.SUCCESS
    result.task(':ideaProject').outcome == TaskOutcome.SUCCESS
    result.task(':ideaModule').outcome == TaskOutcome.SUCCESS
    hasAnnotationProcessingConfigured(true)

    where:
    gradleVersion << IntegrationTestHelper.GRADLE_VERSIONS
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

  @Unroll
  def "idea without java, configureAnnotationProcessing = false, with Gradle #gradleVersion"() {
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
        .withGradleVersion(gradleVersion)
        .withProjectDir(testProjectDir.root)
        .withArguments('idea')
        .build()

    then:
    result.task(':idea').outcome == TaskOutcome.SUCCESS
    result.task(':ideaProject').outcome == TaskOutcome.SUCCESS
    result.task(':ideaModule').outcome == TaskOutcome.SUCCESS
    hasAnnotationProcessingConfigured(false)

    where:
    gradleVersion << IntegrationTestHelper.GRADLE_VERSIONS
  }

  @Unroll
  def "idea task, with Gradle #gradleVersion"() {
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
        .withGradleVersion(gradleVersion)
        .withProjectDir(testProjectDir.root)
        .withArguments('idea')
        .build()

    then:
    result.task(':idea').outcome == TaskOutcome.SUCCESS
    result.task(':ideaModule').outcome == TaskOutcome.SUCCESS
    hasAnnotationProcessingConfigured(true)
    // TODO: check IML for content roots and dependencies

    where:
    gradleVersion << IntegrationTestHelper.GRADLE_VERSIONS
  }

  @Unroll
  def "idea task, all configurations disabled, with Gradle #gradleVersion"() {
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
        .withGradleVersion(gradleVersion)
        .withProjectDir(testProjectDir.root)
        .withArguments('idea')
        .build()

    then:
    result.task(':idea').outcome == TaskOutcome.SUCCESS
    result.task(':ideaModule').outcome == TaskOutcome.SUCCESS
    hasAnnotationProcessingConfigured(false)
    // TODO: check IML for content roots and dependencies

    where:
    gradleVersion << IntegrationTestHelper.GRADLE_VERSIONS
  }

  @Unroll
  def "tooling api, with Gradle #gradleVersion"() {
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
        .useGradleVersion(gradleVersion)
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
    if (GradleVersion.version(gradleVersion) >= GradleVersion.version("3.4")) {
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

    where:
    gradleVersion << IntegrationTestHelper.GRADLE_VERSIONS
  }

  @Unroll
  def "tooling api, all configurations disabled, with Gradle #gradleVersion"() {
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
        .useGradleVersion(gradleVersion)
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
    if (GradleVersion.version(gradleVersion) >= GradleVersion.version("3.4")) {
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

    where:
    gradleVersion << IntegrationTestHelper.GRADLE_VERSIONS
  }
}
