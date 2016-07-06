package net.ltgt.gradle.apt

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.GroovyBasePlugin
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.plugins.ide.eclipse.EclipsePlugin
import org.gradle.plugins.ide.idea.IdeaPlugin

class AptPlugin implements Plugin<Project> {
  @Override
  void apply(Project project) {
    project.plugins.withType(JavaBasePlugin) {
      def javaConvention = project.convention.getPlugin(JavaPluginConvention)
      javaConvention.sourceSets.all { SourceSet sourceSet ->
        def compileOnlyConfigurationName = getCompileOnlyConfigurationName(sourceSet);
        // Gradle 2.12 already creates such a configuration in the JavaBasePlugin
        def configuration = project.configurations.findByName(compileOnlyConfigurationName)
        if (configuration == null) {
          configuration = project.configurations.create(compileOnlyConfigurationName)
          configuration.visible = false
          configuration.description = "Compile-only classpath for ${sourceSet}."
          configuration.extendsFrom project.configurations.findByName(sourceSet.compileConfigurationName)

          sourceSet.compileClasspath = configuration

          // Special-case the JavaPlugin's 'test' source set, only if we created the testCompileOnly configuration
          // Note that Gradle 2.12 actually creates a testCompilationClasspath configuration that extends testCompileOnly
          // and sets it as sourceSets.test.compileClasspath; rather than directly using the testCompileOnly configuration.
          if (sourceSet.name == SourceSet.TEST_SOURCE_SET_NAME) {
            project.plugins.withType(JavaPlugin) {
              sourceSet.compileClasspath = project.files(javaConvention.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).output, configuration)
            }
          }
        }

        def aptConfiguration = project.configurations.create(getAptConfigurationName(sourceSet))
        aptConfiguration.visible = false
        aptConfiguration.description = "Processor path for ${sourceSet}"
        configureCompileTask(project, sourceSet.compileJavaTaskName, getGeneratedSourceDir(project, sourceSet.name), aptConfiguration)
      }
    }
    project.plugins.withType(JavaPlugin) {
      def javaConvention = project.convention.getPlugin(JavaPluginConvention)

      def mainSourceSet = javaConvention.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
      def compileOnlyConfiguration = project.configurations.getByName(getCompileOnlyConfigurationName(mainSourceSet));
      def aptConfiguration = project.configurations.getByName(getAptConfigurationName(mainSourceSet))

      def testSourceSet = javaConvention.sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME)
      def testCompileOnlyConfiguration = project.configurations.getByName(getCompileOnlyConfigurationName(testSourceSet));
      def testAptConfiguration = project.configurations.getByName(getAptConfigurationName(testSourceSet))

      configureEclipse(project, compileOnlyConfiguration, aptConfiguration, testCompileOnlyConfiguration, testAptConfiguration)

      def outputDir = getGeneratedSourceDir(project, SourceSet.MAIN_SOURCE_SET_NAME)
      def testOutputDir = getGeneratedSourceDir(project, SourceSet.TEST_SOURCE_SET_NAME)
      configureIdeaModule(project, outputDir, compileOnlyConfiguration, aptConfiguration, testOutputDir, testCompileOnlyConfiguration, testAptConfiguration)
    }
    project.plugins.withType(GroovyBasePlugin) {
      def javaConvention = project.convention.getPlugin(JavaPluginConvention)
      javaConvention.sourceSets.all { SourceSet sourceSet ->
        def aptConfiguration = project.configurations.getByName(getAptConfigurationName(sourceSet))
        configureCompileTask(project, sourceSet.getCompileTaskName("groovy"), getGeneratedSourceDir(project, sourceSet.name), aptConfiguration)
      }
    }
    configureIdeaProject(project)
    project.extensions.create('apt', AptExtension)
  }

  private File getGeneratedSourceDir(Project project, String sourceSetName) {
    return project.file("${project.buildDir}/generated/source/apt/${sourceSetName}")
  }

  private String getCompileOnlyConfigurationName(SourceSet sourceSet) {
    return sourceSet.compileConfigurationName + "Only"
  }

  private String getAptConfigurationName(SourceSet sourceSet) {
    // HACK: we use the same naming logic/scheme as for tasks, so just use SourceSet#getTaskName
    return sourceSet.getTaskName("", "apt")
  }

  private void configureCompileTask(Project project, String taskName, File outputDir, Configuration aptConfiguration) {
    def task = project.tasks.withType(AbstractCompile).getByName(taskName)

    task.inputs.files aptConfiguration
    task.outputs.dir outputDir

    project.afterEvaluate {
      task.options.compilerArgs += [
          "-s", outputDir.path,
          "-processorpath", aptConfiguration.asPath ?: ':',
      ]
    }

    task.doFirst {
      if (project.apt.cleanGeneratedSourceDir) {
        project.delete(outputDir)
      }
      project.mkdir(outputDir)
    }
  }

  /**
   * Inspired by https://github.com/mkarneim/pojobuilder/wiki/Enabling-PojoBuilder-for-Eclipse-Using-Gradle
   */
  private void configureEclipse(Project project, Configuration compileOnlyConfiguration, Configuration aptConfiguration,
      Configuration testCompileOnlyConfiguration, Configuration testAptConfiguration) {
    project.plugins.withType(EclipsePlugin) {
      project.eclipse.jdt.file.withProperties {
        it.'org.eclipse.jdt.core.compiler.processAnnotations' = 'enabled'
      }

      project.afterEvaluate {
        project.eclipse.classpath {
          plusConfigurations += [ compileOnlyConfiguration, testCompileOnlyConfiguration ]
        }
      }
      if (!project.tasks.findByName('eclipseJdtApt')) {
        def task = project.tasks.create('eclipseJdtApt') {
          ext.aptPrefs = project.file('.settings/org.eclipse.jdt.apt.core.prefs')
          outputs.file(aptPrefs)
          doLast {
            project.mkdir(aptPrefs.parentFile)
            aptPrefs.text = """\
              eclipse.preference.version=1
              org.eclipse.jdt.apt.aptEnabled=true
              org.eclipse.jdt.apt.genSrcDir=.apt_generated
              org.eclipse.jdt.apt.reconcileEnabled=true
            """.stripIndent()
          }
        }
        project.tasks.eclipse.dependsOn task
        def cleanTask = project.tasks.create('cleanEclipseJdtApt', Delete)
        cleanTask.delete task.outputs
        project.tasks.cleanEclipse.dependsOn cleanTask
      }
      if (!project.tasks.findByName('eclipseFactorypath')) {
        def task = project.tasks.create('eclipseFactorypath') {
          ext.factorypath = project.file('.factorypath')
          inputs.files aptConfiguration, testAptConfiguration
          outputs.file factorypath
          doLast {
            factorypath.withWriter {
              new groovy.xml.MarkupBuilder(it).'factorypath' {
                [aptConfiguration, testAptConfiguration]*.each {
                  factorypathentry(
                      kind: 'EXTJAR',
                      id: it.absolutePath,
                      enabled: true,
                      runInBatchMode: false,
                  )
                }
              }
            }
          }
        }
        project.tasks.eclipse.dependsOn task
        def cleanTask = project.tasks.create('cleanEclipseFactorypath', Delete)
        cleanTask.delete task.outputs
        project.tasks.cleanEclipse.dependsOn cleanTask
      }
    }
  }

  private void configureIdeaModule(Project project,
      File outputDir, Configuration compileOnlyConfiguration, Configuration aptConfiguration,
      File testOutputDir, Configuration testCompileOnlyConfiguration, Configuration testAptConfiguration) {
    project.plugins.withType(IdeaPlugin) {
      project.afterEvaluate {
        project.idea.module {
          if (excludeDirs.contains(project.buildDir)) {
            excludeDirs -= project.buildDir
            // Race condition: many of these will actually be created afterwards…
            def subdirs = project.buildDir.listFiles({ f -> f.directory && f.name != 'generated' } as FileFilter)
            if (subdirs != null) {
              excludeDirs += subdirs as List
            }
          } else {
            excludeDirs -= [
                project.file("$project.buildDir/generated"),
                project.file("$project.buildDir/generated/source"),
                project.file("$project.buildDir/generated/source/apt"),
                outputDir,
                testOutputDir
            ]
          }

          sourceDirs += outputDir
          testSourceDirs += testOutputDir
          generatedSourceDirs += [ outputDir, testOutputDir ]

          // NOTE: ideally we'd use PROVIDED for both, but then every transitive dependency in
          // compile or testCompile configurations that would also be in compileOnly and
          // testCompileOnly would end up in PROVIDED.
          scopes.COMPILE.plus += [ compileOnlyConfiguration, aptConfiguration ]
          scopes.TEST.plus += [ testCompileOnlyConfiguration, testAptConfiguration ]
        }
      }
    }
  }

  private void configureIdeaProject(Project project) {
    if (project.parent == null) {
      project.plugins.withType(IdeaPlugin) {
        project.idea.project.ipr.withXml {
          def compilerConfiguration = it.node.component.find { it.@name == 'CompilerConfiguration' }
          compilerConfiguration.remove(compilerConfiguration.annotationProcessing)
          compilerConfiguration.append(new NodeBuilder().annotationProcessing() {
            profile(name: 'Default', enabled: true, default: true) {
              // XXX: this assumes that all subprojects use the same name for their buildDir
              sourceOutputDir(name: "${project.relativePath(project.buildDir)}/generated/source/apt/$SourceSet.MAIN_SOURCE_SET_NAME")
              sourceTestOutputDir(name: "${project.relativePath(project.buildDir)}/generated/source/apt/$SourceSet.TEST_SOURCE_SET_NAME")
              outputRelativeToContentRoot(value: true)
              processorPath(useClasspath: true)
            }
          })
        }
      }
    }
  }
}
