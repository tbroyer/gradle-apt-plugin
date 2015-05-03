package net.ltgt.gradle.apt

import nebula.test.IntegrationSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import nebula.test.dependencies.ModuleBuilder
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.idea.IdeaProject

class IdeaIntegrationSpec extends IntegrationSpec {

  def 'idea without java'() {
    setup:
    buildFile << """\
      buildscript {
        dependencies {
          classpath files('${System.getProperty('plugin')}')
        }
      }
      apply plugin: 'net.ltgt.apt'
      apply plugin: 'idea'
    """.stripIndent()

    when:
    runTasksSuccessfully('idea')

    then:
    hasAnnotationProcessingConfigured()
  }

  void hasAnnotationProcessingConfigured() {
    with (new XmlSlurper().parse(new File(projectDir, "${moduleName}.ipr")).component.find { it.@name == 'CompilerConfiguration' }
              .annotationProcessing.profile) {
      assert it.size() == 1
      assert it.@default == true
      assert it.@enabled == true
      assert it.sourceOutputDir.@name == 'build/generated/source/apt/main'
      assert it.sourceTestOutputDir.@name == 'build/generated/source/apt/test'
      assert it.outputRelativeToContentRoot.@value == true
      assert it.processorPath.@useClasspath == true
    }
  }

  def 'idea task'() {
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
        apply plugin: 'idea'
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
    runTasksSuccessfully('idea')

    then:
    hasAnnotationProcessingConfigured()
    // TODO: check IML for content roots and dependencies
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
        apply plugin: 'idea'
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
    def ideaModule = connection.getModel(IdeaProject).modules[0]

    then:
    ideaModule.contentRoots*.generatedSourceDirectories*.directory.flatten()
        .contains(new File(projectDir, 'build/generated/source/apt/main'))
    ideaModule.contentRoots*.generatedTestDirectories*.directory.flatten()
        .contains(new File(projectDir, 'build/generated/source/apt/test'))
    !ideaModule.contentRoots*.excludeDirectories.flatten()
        .contains(new File(projectDir, 'build'))
    // XXX: We can't test buildDir subdirectories unless we also build the project, should we?

    def dependencies = ideaModule.dependencies.collect {
      "${it.gradleModuleVersion.group}:${it.gradleModuleVersion.name}:${it.gradleModuleVersion.version}:${it.scope.scope}" as String
    }
    // XXX: it's unfortunate that we have both versions of "leaf" artifacts, but we can't easily do otherwise
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

    cleanup:
    connection.close()
  }
}
