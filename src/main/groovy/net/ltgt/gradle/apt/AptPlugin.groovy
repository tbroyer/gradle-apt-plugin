package net.ltgt.gradle.apt

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.plugins.ide.eclipse.EclipsePlugin
import org.gradle.plugins.ide.idea.IdeaPlugin

class AptPlugin implements Plugin<Project> {
  @Override
  void apply(Project project) {
    def aptConfiguration = project.configurations.maybeCreate("apt")
    def compileOnlyConfiguration = project.configurations.maybeCreate("compileOnly")
    def testAptConfiguration = project.configurations.maybeCreate("testApt")
    def testCompileOnlyConfiguration = project.configurations.maybeCreate("testCompileOnly")
    def outputDir = getGeneratedSourceDir(project, SourceSet.MAIN_SOURCE_SET_NAME)
    def testOutputDir = getGeneratedSourceDir(project, SourceSet.TEST_SOURCE_SET_NAME)

    testCompileOnlyConfiguration.extendsFrom compileOnlyConfiguration

    project.plugins.withType(JavaBasePlugin) {
      def javaConvention = project.convention.plugins.get("java") as JavaPluginConvention
      javaConvention.sourceSets.all { SourceSet sourceSet ->
        def configuration = project.configurations.maybeCreate(sourceSet.compileConfigurationName + "Only")
        configuration.visible = false
        configuration.description = "Compile-only classpath for ${sourceSet}."
        configuration.extendsFrom project.configurations.findByName(sourceSet.compileConfigurationName)
        // NOTE: must be done when JavaBasePlugin is applied such that the javadoc task configured
        // in the JavaPlugin picks the appropriate compileClasspath.
        sourceSet.compileClasspath = configuration

        configureCompileTask(project, sourceSet.compileJavaTaskName, getGeneratedSourceDir(project, sourceSet.name), getAptConfiguration(project, sourceSet))
      }
    }
    project.plugins.withType(JavaPlugin) {
      def javaConvention = project.convention.plugins.get("java") as JavaPluginConvention
      javaConvention.sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME) { SourceSet sourceSet ->
        sourceSet.compileClasspath = project.files(
            javaConvention.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).output,
            testCompileOnlyConfiguration)
      }

      configureEclipse(project, compileOnlyConfiguration, aptConfiguration, testCompileOnlyConfiguration, testAptConfiguration)
      configureIdeaModule(project, outputDir, compileOnlyConfiguration, aptConfiguration, testOutputDir, testCompileOnlyConfiguration, testAptConfiguration)
    }
    configureIdeaProject(project)
  }

  private File getGeneratedSourceDir(Project project, String sourceSetName) {
    return project.file("${project.buildDir}/generated/source/apt/${sourceSetName}")
  }

  private Configuration getAptConfiguration(Project project, SourceSet sourceSet) {
    // HACK: we use the same naming logic/scheme as for tasks, so just use SourceSet#getTaskName
    return project.configurations.maybeCreate(sourceSet.getTaskName("", "apt"))
  }

  private void configureCompileTask(Project project, String taskName, File outputDir, Configuration aptConfiguration) {
    def task = project.tasks.withType(JavaCompile).getByName(taskName)

    task.inputs.files aptConfiguration
    task.outputs.dir outputDir

    project.afterEvaluate {
      task.options.compilerArgs += [
          "-s", outputDir.path,
          "-processorpath", aptConfiguration.asPath ?: ':',
      ]
    }

    task.doFirst {
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
          noExportConfigurations += [ compileOnlyConfiguration, testCompileOnlyConfiguration ]
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
      // .ipr format
      project.plugins.withType(IdeaPlugin) {
        project.idea.project.ipr.withXml {
          updateIdeaProjectConfiguration(project, it.node)
        }
      }
      // Directory-based format
      File compilerXml = project.file('.idea/compiler.xml')
      if (compilerXml.isFile()) {
        Node config = (new XmlParser()).parse(compilerXml)
        updateIdeaProjectConfiguration(project, config)
        compilerXml.withWriter { writer ->
          XmlNodePrinter printer = new XmlNodePrinter(new PrintWriter(writer))
          printer.setPreserveWhitespace(true)
          printer.print(config)
        }
      }
    }
  }

  private void updateIdeaProjectConfiguration(Project project, Node config) {
    def compilerConfiguration = config.component.find { it.@name == 'CompilerConfiguration' }
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
