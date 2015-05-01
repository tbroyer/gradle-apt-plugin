package net.ltgt.gradle.apt

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.compile.JavaCompile

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
    }
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
}
