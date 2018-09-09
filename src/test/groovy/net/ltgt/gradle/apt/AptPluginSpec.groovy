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

import nebula.test.PluginProjectSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import nebula.test.dependencies.ModuleBuilder
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.compile.JavaCompile

class AptPluginSpec extends PluginProjectSpec {
  @Override String getPluginName() {
    return 'net.ltgt.apt'
  }

  def 'empty project'() {
    when:
    project.apply plugin: pluginName
    project.evaluate()

    then:
    project.configurations.empty
  }

  def 'empty java project'() {
    when:
    project.apply plugin: pluginName
    project.apply plugin: 'java'
    project.evaluate()

    then:
    project.configurations.findByName('annotationProcessor')
    project.configurations.findByName('testAnnotationProcessor')
    project.configurations.findByName('apt')
    project.configurations.findByName('testApt')
    project.configurations.findByName('compileOnly')
    project.configurations.findByName('testCompileOnly')
    with(project.tasks.compileJava) { JavaCompile task ->
      task.effectiveAnnotationProcessorPath.empty
      task.options.annotationProcessorGeneratedSourcesDirectory == new File(project.buildDir, 'generated/source/apt/main')
      task.options.allCompilerArgs.empty
    }
    with(project.tasks.compileTestJava) { JavaCompile task ->
      task.effectiveAnnotationProcessorPath.empty
      task.options.annotationProcessorGeneratedSourcesDirectory == new File(project.buildDir, 'generated/source/apt/test')
      task.options.allCompilerArgs.empty
    }
  }

  def 'empty groovy project'() {
    when:
    project.apply plugin: pluginName
    project.apply plugin: 'groovy'
    project.evaluate()

    then:
    project.configurations.findByName('annotationProcessor')
    project.configurations.findByName('testAnnotationProcessor')
    project.configurations.findByName('apt')
    project.configurations.findByName('testApt')
    project.configurations.findByName('compileOnly')
    project.configurations.findByName('testCompileOnly')
    with(project.tasks.compileJava) { JavaCompile task ->
      task.effectiveAnnotationProcessorPath.empty
      task.options.annotationProcessorGeneratedSourcesDirectory == new File(project.buildDir, 'generated/source/apt/main')
      task.options.allCompilerArgs.empty
    }
    with(project.tasks.compileGroovy) { GroovyCompile task ->
      task.options.annotationProcessorPath.empty
      task.options.annotationProcessorGeneratedSourcesDirectory == new File(project.buildDir, 'generated/source/apt/main')
      task.options.allCompilerArgs.empty
    }
    with(project.tasks.compileTestJava) { JavaCompile task ->
      task.effectiveAnnotationProcessorPath.empty
      task.options.annotationProcessorGeneratedSourcesDirectory == new File(project.buildDir, 'generated/source/apt/test')
      task.options.allCompilerArgs.empty
    }
    with(project.tasks.compileTestGroovy) { GroovyCompile task ->
      task.options.annotationProcessorPath.empty
      task.options.annotationProcessorGeneratedSourcesDirectory == new File(project.buildDir, 'generated/source/apt/test')
      task.options.allCompilerArgs.empty
    }
  }

  def 'project with annotation processors'() {
    setup:
    def mavenRepo = new GradleDependencyGenerator(
        new DependencyGraphBuilder()
            .addModule('leaf:compile:1.0')
            .addModule('leaf:testCompile:1.0')
            .addModule(new ModuleBuilder('compile:compile:1.0')
                .addDependency('leaf:compile:1.0')
                .build())
            .addModule(new ModuleBuilder('testCompile:testCompile:1.0')
                .addDependency('leaf:testCompile:1.0')
                .build())
            .addModule(new ModuleBuilder('processor:compile:1.0')
                .addDependency('leaf:compile:2.0')
                .build())
            .addModule(new ModuleBuilder('processor:testCompile:1.0')
                .addDependency('leaf:testCompile:2.0')
                .build())
            .build(),
        project.mkdir('repo').path)
      .generateTestMavenRepo()

    when:
    project.apply plugin: pluginName
    project.apply plugin: 'groovy'
    project.repositories {
      maven { url mavenRepo }
    }
    project.dependencies {
      compile                 'compile:compile:1.0'
      annotationProcessor     'processor:compile:1.0'
      testCompile             'testCompile:testCompile:1.0'
      testAnnotationProcessor 'processor:testCompile:1.0'
    }
    project.evaluate()

    then:
    with(project.tasks.compileJava) { JavaCompile task ->
      !task.effectiveAnnotationProcessorPath.empty
      task.effectiveAnnotationProcessorPath.asPath == project.configurations.annotationProcessor.asPath
      task.options.annotationProcessorGeneratedSourcesDirectory == new File(project.buildDir, 'generated/source/apt/main')
      task.options.allCompilerArgs.empty
    }
    with(project.tasks.compileGroovy) { GroovyCompile task ->
      !task.options.annotationProcessorPath.empty
      task.options.annotationProcessorPath.asPath == project.configurations.annotationProcessor.asPath
      task.options.annotationProcessorGeneratedSourcesDirectory == new File(project.buildDir, 'generated/source/apt/main')
      task.options.allCompilerArgs.empty
    }
    with(project.tasks.compileTestJava) { JavaCompile task ->
      !task.effectiveAnnotationProcessorPath.empty
      task.effectiveAnnotationProcessorPath.asPath == project.configurations.testAnnotationProcessor.asPath
      task.options.annotationProcessorGeneratedSourcesDirectory == new File(project.buildDir, 'generated/source/apt/test')
      task.options.allCompilerArgs.empty
    }
    with(project.tasks.compileTestGroovy) { GroovyCompile task ->
      !task.options.annotationProcessorPath.empty
      task.options.annotationProcessorPath.asPath == project.configurations.testAnnotationProcessor.asPath
      task.options.annotationProcessorGeneratedSourcesDirectory == new File(project.buildDir, 'generated/source/apt/test')
      task.options.allCompilerArgs.empty
    }
    project.configurations.annotationProcessor.resolvedConfiguration.resolvedArtifacts*.moduleVersion.id.collect { "$it.group:$it.name:$it.version" as String }.toSet()
        .equals([ 'processor:compile:1.0', 'leaf:compile:2.0' ] as Set)
    project.configurations.testAnnotationProcessor.resolvedConfiguration.resolvedArtifacts*.moduleVersion.id.collect { "$it.group:$it.name:$it.version" as String }.toSet()
        .equals([ 'processor:testCompile:1.0', 'leaf:testCompile:2.0' ] as Set)
  }

  def 'project with annotation processors through old configurations'() {
    setup:
    def mavenRepo = new GradleDependencyGenerator(
        new DependencyGraphBuilder()
            .addModule('leaf:compile:1.0')
            .addModule('leaf:testCompile:1.0')
            .addModule(new ModuleBuilder('compile:compile:1.0')
                .addDependency('leaf:compile:1.0')
                .build())
            .addModule(new ModuleBuilder('testCompile:testCompile:1.0')
                .addDependency('leaf:testCompile:1.0')
                .build())
            .addModule(new ModuleBuilder('processor:compile:1.0')
                .addDependency('leaf:compile:2.0')
                .build())
            .addModule(new ModuleBuilder('processor:testCompile:1.0')
                .addDependency('leaf:testCompile:2.0')
                .build())
            .build(),
        project.mkdir('repo').path)
      .generateTestMavenRepo()

    when:
    project.apply plugin: pluginName
    project.apply plugin: 'groovy'
    project.repositories {
      maven { url mavenRepo }
    }
    project.dependencies {
      compile     'compile:compile:1.0'
      apt         'processor:compile:1.0'
      testCompile 'testCompile:testCompile:1.0'
      testApt     'processor:testCompile:1.0'
    }
    project.evaluate()

    then:
    project.configurations.apt.asPath == project.configurations.annotationProcessor.asPath
    project.configurations.testApt.asPath == project.configurations.testAnnotationProcessor.asPath
    with(project.tasks.compileJava) { JavaCompile task ->
      !task.effectiveAnnotationProcessorPath.empty
      task.effectiveAnnotationProcessorPath.asPath == project.configurations.annotationProcessor.asPath
      task.options.annotationProcessorGeneratedSourcesDirectory == new File(project.buildDir, 'generated/source/apt/main')
      task.options.allCompilerArgs.empty
    }
    with(project.tasks.compileGroovy) { GroovyCompile task ->
      !task.options.annotationProcessorPath.empty
      task.options.annotationProcessorPath.asPath == project.configurations.annotationProcessor.asPath
      task.options.annotationProcessorGeneratedSourcesDirectory == new File(project.buildDir, 'generated/source/apt/main')
      task.options.allCompilerArgs.empty
    }
    with(project.tasks.compileTestJava) { JavaCompile task ->
      !task.effectiveAnnotationProcessorPath.empty
      task.effectiveAnnotationProcessorPath.asPath == project.configurations.testAnnotationProcessor.asPath
      task.options.annotationProcessorGeneratedSourcesDirectory == new File(project.buildDir, 'generated/source/apt/test')
      task.options.allCompilerArgs.empty
    }
    with(project.tasks.compileTestGroovy) { GroovyCompile task ->
      !task.options.annotationProcessorPath.empty
      task.options.annotationProcessorPath.asPath == project.configurations.testAnnotationProcessor.asPath
      task.options.annotationProcessorGeneratedSourcesDirectory == new File(project.buildDir, 'generated/source/apt/test')
      task.options.allCompilerArgs.empty
    }
    project.configurations.annotationProcessor.resolvedConfiguration.resolvedArtifacts*.moduleVersion.id.collect { "$it.group:$it.name:$it.version" as String }.toSet()
        .equals([ 'processor:compile:1.0', 'leaf:compile:2.0' ] as Set)
    project.configurations.testAnnotationProcessor.resolvedConfiguration.resolvedArtifacts*.moduleVersion.id.collect { "$it.group:$it.name:$it.version" as String }.toSet()
        .equals([ 'processor:testCompile:1.0', 'leaf:testCompile:2.0' ] as Set)
  }

  def 'project with annotation processors through AptOptions'() {
    setup:
    def mavenRepo = new GradleDependencyGenerator(
        new DependencyGraphBuilder()
            .addModule('leaf:compile:1.0')
            .addModule('leaf:testCompile:1.0')
            .addModule(new ModuleBuilder('compile:compile:1.0')
                .addDependency('leaf:compile:1.0')
                .build())
            .addModule(new ModuleBuilder('testCompile:testCompile:1.0')
                .addDependency('leaf:testCompile:1.0')
                .build())
            .addModule(new ModuleBuilder('processor:compile:1.0')
                .addDependency('leaf:compile:2.0')
                .build())
            .addModule(new ModuleBuilder('processor:testCompile:1.0')
                .addDependency('leaf:testCompile:2.0')
                .build())
            .build(),
        project.mkdir('repo').path)
      .generateTestMavenRepo()

    when:
    project.apply plugin: pluginName
    project.apply plugin: 'groovy'
    project.repositories {
      maven { url mavenRepo }
    }
    project.configurations {
      annotationProcessing
      testAnnotationProcessing
    }
    project.dependencies {
      compile                  'compile:compile:1.0'
      annotationProcessing     'processor:compile:1.0'
      testCompile              'testCompile:testCompile:1.0'
      testAnnotationProcessing 'processor:testCompile:1.0'
    }
    project.tasks.compileJava {
      generatedSourcesDestinationDir = 'src/main/generated'
      aptOptions.processorpath = project.configurations.annotationProcessing
    }
    project.tasks.compileGroovy {
      generatedSourcesDestinationDir = 'src/main/generated'
      aptOptions.processorpath = project.configurations.annotationProcessing
    }
    project.tasks.compileTestJava {
      generatedSourcesDestinationDir = 'src/test/generated'
      aptOptions.processorpath = project.configurations.testAnnotationProcessing
    }
    project.tasks.compileTestGroovy {
      generatedSourcesDestinationDir = 'src/test/generated'
      aptOptions.processorpath = project.configurations.testAnnotationProcessing
    }
    project.evaluate()

    then:
    with(project.tasks.compileJava) { JavaCompile task ->
      !task.effectiveAnnotationProcessorPath.empty
      task.effectiveAnnotationProcessorPath.asPath == project.configurations.annotationProcessing.asPath
      task.options.annotationProcessorGeneratedSourcesDirectory == new File(project.projectDir, 'src/main/generated')
      task.options.allCompilerArgs.empty
    }
    with(project.tasks.compileGroovy) { GroovyCompile task ->
      !task.options.annotationProcessorPath.empty
      task.options.annotationProcessorPath.asPath == project.configurations.annotationProcessing.asPath
      task.options.annotationProcessorGeneratedSourcesDirectory == new File(project.projectDir, 'src/main/generated')
      task.options.allCompilerArgs.empty
    }
    with(project.tasks.compileTestJava) { JavaCompile task ->
      !task.effectiveAnnotationProcessorPath.empty
      task.effectiveAnnotationProcessorPath.asPath == project.configurations.testAnnotationProcessing.asPath
      task.options.annotationProcessorGeneratedSourcesDirectory == new File(project.projectDir, 'src/test/generated')
      task.options.allCompilerArgs.empty
    }
    with(project.tasks.compileTestGroovy) { GroovyCompile task ->
      !task.options.annotationProcessorPath.empty
      task.options.annotationProcessorPath.asPath == project.configurations.testAnnotationProcessing.asPath
      task.options.annotationProcessorGeneratedSourcesDirectory == new File(project.projectDir, 'src/test/generated')
      task.options.allCompilerArgs.empty
    }
  }

  def 'project with annotation processors through SourceSet'() {
    setup:
    def mavenRepo = new GradleDependencyGenerator(
        new DependencyGraphBuilder()
            .addModule('leaf:compile:1.0')
            .addModule('leaf:testCompile:1.0')
            .addModule(new ModuleBuilder('compile:compile:1.0')
                .addDependency('leaf:compile:1.0')
                .build())
            .addModule(new ModuleBuilder('testCompile:testCompile:1.0')
                .addDependency('leaf:testCompile:1.0')
                .build())
            .addModule(new ModuleBuilder('processor:compile:1.0')
                .addDependency('leaf:compile:2.0')
                .build())
            .addModule(new ModuleBuilder('processor:testCompile:1.0')
                .addDependency('leaf:testCompile:2.0')
                .build())
            .build(),
        project.mkdir('repo').path)
    .generateTestMavenRepo()

    when:
    project.apply plugin: pluginName
    project.apply plugin: 'groovy'
    project.repositories {
      maven { url mavenRepo }
    }
    project.configurations {
      annotationProcessing
      testAnnotationProcessing
    }
    project.dependencies {
      compile                  'compile:compile:1.0'
      annotationProcessing     'processor:compile:1.0'
      testCompile              'testCompile:testCompile:1.0'
      testAnnotationProcessing 'processor:testCompile:1.0'
    }
    project.sourceSets.main {
      output.generatedSourcesDir = 'src/main/generated'
      annotationProcessorPath = project.configurations.annotationProcessing
    }
    project.sourceSets.test {
      output.generatedSourcesDir = 'src/test/generated'
      annotationProcessorPath = project.configurations.testAnnotationProcessing
    }
    project.evaluate()

    then:
    with(project.tasks.compileJava) { JavaCompile task ->
      !task.effectiveAnnotationProcessorPath.empty
      task.effectiveAnnotationProcessorPath.asPath == project.configurations.annotationProcessing.asPath
      task.options.annotationProcessorGeneratedSourcesDirectory == new File(project.projectDir, 'src/main/generated')
      task.options.allCompilerArgs.empty
    }
    with(project.tasks.compileGroovy) { GroovyCompile task ->
      !task.options.annotationProcessorPath.empty
      task.options.annotationProcessorPath.asPath == project.configurations.annotationProcessing.asPath
      task.options.annotationProcessorGeneratedSourcesDirectory == new File(project.projectDir, 'src/main/generated')
      task.options.allCompilerArgs.empty
    }
    with(project.tasks.compileTestJava) { JavaCompile task ->
      !task.effectiveAnnotationProcessorPath.empty
      task.effectiveAnnotationProcessorPath.asPath == project.configurations.testAnnotationProcessing.asPath
      task.options.annotationProcessorGeneratedSourcesDirectory == new File(project.projectDir, 'src/test/generated')
      task.options.allCompilerArgs.empty
    }
    with(project.tasks.compileTestGroovy) { GroovyCompile task ->
      !task.options.annotationProcessorPath.empty
      task.options.annotationProcessorPath.asPath == project.configurations.testAnnotationProcessing.asPath
      task.options.annotationProcessorGeneratedSourcesDirectory == new File(project.projectDir, 'src/test/generated')
      task.options.allCompilerArgs.empty
    }
  }
}
