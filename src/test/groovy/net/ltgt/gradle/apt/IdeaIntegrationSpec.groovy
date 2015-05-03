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
    with (new XmlSlurper().parse(new File(projectDir, "${moduleName}.ipr")).component.find { it.@name == 'CompilerConfiguration' }
        .annotationProcessing.profile) {
      assert it.size() == 1
      assert it.@default == true
      assert it.@enabled == false
      assert it.empty
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
    with (new XmlSlurper().parse(new File(projectDir, "${moduleName}.ipr")).component.find { it.@name == 'CompilerConfiguration' }
        .annotationProcessing.profile) {
      assert it.size() == 2
      assert it.find { it.@default == true }.@enabled == false
      with (it.find { it.@enabled == true }) {
        assert it.size() == 1
        assert it.sourceOutputDir.@name == "build/generated/source/apt/main"
        assert it.sourceTestOutputDir.@name == "build/generated/source/apt/test"
        assert it.outputRelativeToContentRoot.@value == true
        assert it.module.size() == 1 // can't check name easily/reliably
        assert it.processorPath.@useClasspath == false
        assert (it.processorPath.entry.@name as Set).equals([
                "$mavenRepo/leaf/compile/2.0/compile-2.0.jar",
                "$mavenRepo/annotations/compile/1.0/compile-1.0.jar",
                "$mavenRepo/processor/compile/1.0/compile-1.0.jar",
                "$mavenRepo/leaf/testCompile/2.0/testCompile-2.0.jar",
                "$mavenRepo/annotations/testCompile/1.0/testCompile-1.0.jar",
                "$mavenRepo/processor/testCompile/1.0/testCompile-1.0.jar",
            ] as Set)
      }
    }
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

    def scopedDependencies = ideaModule.dependencies.collect {
      "${it.gradleModuleVersion.group}:${it.gradleModuleVersion.name}:${it.gradleModuleVersion.version}:${it.scope.scope}" as String
    }
    scopedDependencies.contains('leaf:compile:1.0:COMPILE')
    scopedDependencies.contains('compile:compile:1.0:COMPILE')
    scopedDependencies.contains('annotations:compile:1.0:COMPILE')
    scopedDependencies.contains('leaf:testCompile:1.0:TEST')
    scopedDependencies.contains('testCompile:testCompile:1.0:TEST')
    scopedDependencies.contains('annotations:testCompile:1.0:TEST')

    def dependencies = ideaModule.dependencies*.gradleModuleVersion.collect {
      "${it.group}:${it.name}:${it.version}" as String
    }
    !dependencies.contains('leaf:compile:2.0')
    !dependencies.contains('processor:compile:1.0')
    !dependencies.contains('leaf:testCompile:2.0')
    !dependencies.contains('processor:testCompile:1.0')

    cleanup:
    connection.close()
  }
}
