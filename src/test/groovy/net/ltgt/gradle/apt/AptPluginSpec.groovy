package net.ltgt.gradle.apt

import nebula.test.PluginProjectSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import nebula.test.dependencies.ModuleBuilder

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
    project.configurations.findByName('apt')
    project.configurations.findByName('testApt')
    project.configurations.findByName('compileOnly')
    project.configurations.findByName('testCompileOnly')
    with(project.tasks.compileJava.convention.getPlugin(AptPlugin.AptConvention).buildCompilerArgs()) { compilerArgs ->
      compilerArgs.containsAll(['-s', new File(project.buildDir, 'generated/source/apt/main').path])
      !compilerArgs.contains('-processorpath')
    }
    with (project.tasks.compileTestJava.convention.getPlugin(AptPlugin.AptConvention).buildCompilerArgs()) { compilerArgs ->
      compilerArgs.containsAll(['-s', new File(project.buildDir, 'generated/source/apt/test').path])
      !compilerArgs.contains('-processorpath')
    }
  }

  def 'empty groovy project'() {
    when:
    project.apply plugin: pluginName
    project.apply plugin: 'groovy'
    project.evaluate()

    then:
    project.configurations.findByName('apt')
    project.configurations.findByName('testApt')
    project.configurations.findByName('compileOnly')
    project.configurations.findByName('testCompileOnly')
    with(project.tasks.compileJava.convention.getPlugin(AptPlugin.AptConvention).buildCompilerArgs()) { compilerArgs ->
      compilerArgs.containsAll([ '-s', new File(project.buildDir, 'generated/source/apt/main').path ])
      !compilerArgs.contains('-processorpath')
    }
    with(project.tasks.compileGroovy.convention.getPlugin(AptPlugin.AptConvention).buildCompilerArgs()) { compilerArgs ->
      compilerArgs.containsAll([ '-s', new File(project.buildDir, 'generated/source/apt/main').path ])
      !compilerArgs.contains('-processorpath')
    }
    with(project.tasks.compileTestJava.convention.getPlugin(AptPlugin.AptConvention).buildCompilerArgs()) { compilerArgs ->
      compilerArgs.containsAll([ '-s', new File(project.buildDir, 'generated/source/apt/test').path ])
      !compilerArgs.contains('-processorpath')
    }
    with(project.tasks.compileTestGroovy.convention.getPlugin(AptPlugin.AptConvention).buildCompilerArgs()) { compilerArgs ->
      compilerArgs.containsAll([ '-s', new File(project.buildDir, 'generated/source/apt/test').path ])
      !compilerArgs.contains('-processorpath')
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
      compile     'compile:compile:1.0'
      apt         'processor:compile:1.0'
      testCompile 'testCompile:testCompile:1.0'
      testApt     'processor:testCompile:1.0'
    }
    project.evaluate()

    then:
    with(project.tasks.compileJava.convention.getPlugin(AptPlugin.AptConvention).buildCompilerArgs()) { compilerArgs ->
      compilerArgs.containsAll([ '-s', new File(project.buildDir, 'generated/source/apt/main').path, '-processorpath', project.configurations.apt.asPath ])
      !compilerArgs.any { arg -> project.configurations.compile.files.any { arg.contains(it.path) } }
    }
    with(project.tasks.compileGroovy.convention.getPlugin(AptPlugin.AptConvention).buildCompilerArgs()) { compilerArgs ->
      compilerArgs.containsAll([ '-s', new File(project.buildDir, 'generated/source/apt/main').path, '-processorpath', project.configurations.apt.asPath ])
      !compilerArgs.any { arg -> project.configurations.compile.files.any { arg.contains(it.path) } }
    }
    with(project.tasks.compileTestJava.convention.getPlugin(AptPlugin.AptConvention).buildCompilerArgs()) { compilerArgs ->
      compilerArgs.containsAll([ '-s', new File(project.buildDir, 'generated/source/apt/test').path, '-processorpath', project.configurations.testApt.asPath ])
      !compilerArgs.any { arg -> project.configurations.testCompile.files.any { arg.contains(it.path) } }
    }
    with(project.tasks.compileTestGroovy.convention.getPlugin(AptPlugin.AptConvention).buildCompilerArgs()) { compilerArgs ->
      compilerArgs.containsAll([ '-s', new File(project.buildDir, 'generated/source/apt/test').path, '-processorpath', project.configurations.testApt.asPath ])
      !compilerArgs.any { arg -> project.configurations.testCompile.files.any { arg.contains(it.path) } }
    }
    project.configurations.compile.resolvedConfiguration.resolvedArtifacts*.moduleVersion.id.collect { "$it.group:$it.name:$it.version" as String }.toSet()
        .equals([ 'compile:compile:1.0', 'leaf:compile:1.0' ] as Set)
    project.configurations.testCompile.resolvedConfiguration.resolvedArtifacts*.moduleVersion.id.collect { "$it.group:$it.name:$it.version" as String }.toSet()
        .equals([ 'compile:compile:1.0', 'leaf:compile:1.0', 'testCompile:testCompile:1.0', 'leaf:testCompile:1.0' ] as Set)
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
      annotationProcessor
      testAnnotationProcessor
    }
    project.dependencies {
      compile                 'compile:compile:1.0'
      annotationProcessor     'processor:compile:1.0'
      testCompile             'testCompile:testCompile:1.0'
      testAnnotationProcessor 'processor:testCompile:1.0'
    }
    project.tasks.compileJava {
      generatedSourcesDestinationDir = 'src/main/generated'
      aptOptions.processorpath = project.configurations.annotationProcessor
    }
    project.tasks.compileGroovy {
      generatedSourcesDestinationDir = 'src/main/generated'
      aptOptions.processorpath = project.configurations.annotationProcessor
    }
    project.tasks.compileTestJava {
      generatedSourcesDestinationDir = 'src/test/generated'
      aptOptions.processorpath = project.configurations.testAnnotationProcessor
    }
    project.tasks.compileTestGroovy {
      generatedSourcesDestinationDir = 'src/test/generated'
      aptOptions.processorpath = project.configurations.testAnnotationProcessor
    }
    project.evaluate()

    then:
    project.tasks.compileJava.convention.getPlugin(AptPlugin.AptConvention).buildCompilerArgs()
            .containsAll([ '-s', new File(project.projectDir, 'src/main/generated').path, '-processorpath', project.configurations.annotationProcessor.asPath ])
    project.tasks.compileGroovy.convention.getPlugin(AptPlugin.AptConvention).buildCompilerArgs()
            .containsAll([ '-s', new File(project.projectDir, 'src/main/generated').path, '-processorpath', project.configurations.annotationProcessor.asPath ])
    project.tasks.compileTestJava.convention.getPlugin(AptPlugin.AptConvention).buildCompilerArgs()
            .containsAll([ '-s', new File(project.projectDir, 'src/test/generated').path, '-processorpath', project.configurations.testAnnotationProcessor.asPath ])
    project.tasks.compileTestGroovy.convention.getPlugin(AptPlugin.AptConvention).buildCompilerArgs()
            .containsAll([ '-s', new File(project.projectDir, 'src/test/generated').path, '-processorpath', project.configurations.testAnnotationProcessor.asPath ])
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
      annotationProcessor
      testAnnotationProcessor
    }
    project.dependencies {
      compile                 'compile:compile:1.0'
      annotationProcessor     'processor:compile:1.0'
      testCompile             'testCompile:testCompile:1.0'
      testAnnotationProcessor 'processor:testCompile:1.0'
    }
    project.sourceSets.main {
      output.generatedSourcesDir = 'src/main/generated'
      processorpath = project.configurations.annotationProcessor
    }
    project.sourceSets.test {
      output.generatedSourcesDir = 'src/test/generated'
      processorpath = project.configurations.testAnnotationProcessor
    }
    project.evaluate()

    then:
    project.tasks.compileJava.convention.getPlugin(AptPlugin.AptConvention).buildCompilerArgs()
            .containsAll([ '-s', new File(project.projectDir, 'src/main/generated').path, '-processorpath', project.configurations.annotationProcessor.asPath ])
    project.tasks.compileGroovy.convention.getPlugin(AptPlugin.AptConvention).buildCompilerArgs()
            .containsAll([ '-s', new File(project.projectDir, 'src/main/generated').path, '-processorpath', project.configurations.annotationProcessor.asPath ])
    project.tasks.compileTestJava.convention.getPlugin(AptPlugin.AptConvention).buildCompilerArgs()
            .containsAll([ '-s', new File(project.projectDir, 'src/test/generated').path, '-processorpath', project.configurations.testAnnotationProcessor.asPath ])
    project.tasks.compileTestGroovy.convention.getPlugin(AptPlugin.AptConvention).buildCompilerArgs()
            .containsAll([ '-s', new File(project.projectDir, 'src/test/generated').path, '-processorpath', project.configurations.testAnnotationProcessor.asPath ])
  }

  def 'project with compilation-only dependencies'() {
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
            .addModule(new ModuleBuilder('annotations:compile:1.0')
                .addDependency('leaf:compile:2.0')
                .build())
            .addModule(new ModuleBuilder('annotations:testCompile:1.0')
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
      compile         'compile:compile:1.0'
      compileOnly     'annotations:compile:1.0'
      testCompile     'testCompile:testCompile:1.0'
      testCompileOnly 'annotations:testCompile:1.0'
    }
    project.evaluate()

    then:
    project.sourceSets.main.compileClasspath.files == project.configurations.compileOnly.files
    project.sourceSets.test.compileClasspath.files == project.files(project.sourceSets.main.output, project.configurations.testCompileOnly).files
    project.tasks.compileJava.classpath == project.sourceSets.main.compileClasspath
    project.tasks.compileGroovy.classpath == project.sourceSets.main.compileClasspath
    project.tasks.compileTestJava.classpath == project.sourceSets.test.compileClasspath
    project.tasks.compileTestGroovy.classpath == project.sourceSets.test.compileClasspath
    project.tasks.javadoc.classpath.files == project.sourceSets.main.output.plus(project.configurations.compileOnly).files
    project.tasks.groovydoc.classpath.files == project.sourceSets.main.output.plus(project.configurations.compileOnly).files
    project.configurations.compile.resolvedConfiguration.resolvedArtifacts*.moduleVersion.id.collect { "$it.group:$it.name:$it.version" as String }.toSet()
        .equals([ 'compile:compile:1.0', 'leaf:compile:1.0' ] as Set)
    project.configurations.compileOnly.resolvedConfiguration.resolvedArtifacts*.moduleVersion.id.collect { "$it.group:$it.name:$it.version" as String }.toSet()
        .equals([ 'compile:compile:1.0', 'leaf:compile:2.0', 'annotations:compile:1.0' ] as Set)
    project.configurations.runtime.resolvedConfiguration.resolvedArtifacts*.moduleVersion.id.collect { "$it.group:$it.name:$it.version" as String }.toSet()
        .equals([ 'compile:compile:1.0', 'leaf:compile:1.0' ] as Set)
    project.configurations.testCompile.resolvedConfiguration.resolvedArtifacts*.moduleVersion.id.collect { "$it.group:$it.name:$it.version" as String }.toSet()
        .equals([ 'compile:compile:1.0', 'leaf:compile:1.0', 'testCompile:testCompile:1.0', 'leaf:testCompile:1.0' ] as Set)
    project.configurations.testCompileOnly.resolvedConfiguration.resolvedArtifacts*.moduleVersion.id.collect { "$it.group:$it.name:$it.version" as String }.toSet()
        .equals([ 'compile:compile:1.0', 'leaf:compile:1.0',
                  'testCompile:testCompile:1.0', 'leaf:testCompile:2.0', 'annotations:testCompile:1.0' ] as Set)
    project.configurations.testRuntime.resolvedConfiguration.resolvedArtifacts*.moduleVersion.id.collect { "$it.group:$it.name:$it.version" as String }.toSet()
        .equals([ 'compile:compile:1.0', 'leaf:compile:1.0', 'testCompile:testCompile:1.0', 'leaf:testCompile:1.0' ] as Set)
  }
}
