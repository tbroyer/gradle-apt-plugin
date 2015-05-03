package net.ltgt.gradle.apt

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
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
    def outputDir = project.file("$project.buildDir/generated/source/apt/$SourceSet.MAIN_SOURCE_SET_NAME")
    def testOutputDir = project.file("$project.buildDir/generated/source/apt/$SourceSet.TEST_SOURCE_SET_NAME")

    testCompileOnlyConfiguration.extendsFrom compileOnlyConfiguration

    project.plugins.withType(JavaPlugin) {
      def javaConvention = project.convention.plugins.get("java") as JavaPluginConvention
      javaConvention.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME) { SourceSet sourceSet ->
        compileOnlyConfiguration.extendsFrom project.configurations[sourceSet.compileConfigurationName]
        sourceSet.compileClasspath = compileOnlyConfiguration
        configureCompileTask(project, sourceSet.compileJavaTaskName, outputDir, aptConfiguration)
      }
      javaConvention.sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME) { SourceSet sourceSet ->
        testCompileOnlyConfiguration.extendsFrom project.configurations[sourceSet.compileConfigurationName]
        sourceSet.compileClasspath = testCompileOnlyConfiguration
        configureCompileTask(project, sourceSet.compileJavaTaskName, testOutputDir, testAptConfiguration)
      }

      configureEclipse(project, compileOnlyConfiguration, aptConfiguration, testCompileOnlyConfiguration, testAptConfiguration)
      configureIdeaModule(project, outputDir, compileOnlyConfiguration, testOutputDir, testCompileOnlyConfiguration)
    }
    configureIdeaProject(project)
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

      project.tasks.eclipseJdt.doLast {
        def aptPrefs = project.file('.settings/org.eclipse.jdt.apt.core.prefs')
        project.mkdir(aptPrefs.parentFile)
        aptPrefs.text = """\
          eclipse.preference.version=1
          org.eclipse.jdt.apt.aptEnabled=true
          org.eclipse.jdt.apt.genSrcDir=.apt_generated
          org.eclipse.jdt.apt.reconcileEnabled=true
        """.stripIndent()

        project.file('.factorypath').withWriter {
          new groovy.xml.MarkupBuilder(it).'factorypath' {
            [ aptConfiguration, testAptConfiguration ]*.each {
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
  }

  private void configureIdeaModule(Project project, File outputDir, Configuration compileOnlyConfiguration,
      File testOutputDir, Configuration testCompileOnlyConfiguration) {
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
          scopes.COMPILE.plus += [ compileOnlyConfiguration ]
          scopes.TEST.plus += [ testCompileOnlyConfiguration ]
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
            profile(name: 'Default', enabled: false, default: true)

            project.rootProject.allprojects.findAll {
              it.plugins.hasPlugin(IdeaPlugin) && it.plugins.hasPlugin(JavaPlugin) &&
                  (!it.configurations.apt.empty || !it.configurations.testApt.empty)
            }.each { p ->
              profile(name: p.idea.module.name, enabled: true, default: false) {
                sourceOutputDir(name: "${p.relativePath(p.buildDir)}/generated/source/apt/$SourceSet.MAIN_SOURCE_SET_NAME")
                sourceTestOutputDir(name: "${p.relativePath(p.buildDir)}/generated/source/apt/$SourceSet.TEST_SOURCE_SET_NAME")
                outputRelativeToContentRoot(value: true)
                processorPath(useClasspath: false) {
                  [ p.configurations.apt, p.configurations.testApt ]*.each {
                    entry(name: it.path)
                  }
                }
                module(name: p.idea.module.name)
              }
            }
          })
        }
      }
    }
  }
}
