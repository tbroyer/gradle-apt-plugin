package net.ltgt.gradle.apt

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.util.GradleVersion

class AptIdeaPlugin implements Plugin<Project> {
  @Override
  void apply(Project project) {
    project.plugins.apply(AptPlugin)
    project.plugins.apply(IdeaPlugin)
    project.plugins.withType(JavaPlugin) {
      def javaConvention = project.convention.getPlugin(JavaPluginConvention)
      def mainSourceSet = javaConvention.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
      def testSourceSet = javaConvention.sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME)

      configureIdeaModule(project, mainSourceSet, testSourceSet)
    }
    configureIdeaProject(project)
  }


  private void configureIdeaModule(Project project, SourceSet mainSourceSet, SourceSet testSourceSet) {
    project.afterEvaluate {
      project.idea.module {
        def excl = [mainSourceSet, testSourceSet].collect { it.output.generatedSourcesDir }
            .collect {
          def ancestors = []
          for (File f = it; f != null && f != project.projectDir; f = f.parentFile) {
            ancestors.add(f)
          }
          return ancestors
        }
        .flatten()

        if (excl.contains(project.buildDir) && excludeDirs.contains(project.buildDir)) {
          excludeDirs -= project.buildDir
          // Race condition: many of these will actually be created afterwards…
          def subdirs = project.buildDir.listFiles({ f -> f.directory } as FileFilter)
          if (subdirs != null) {
            excludeDirs += subdirs as List
          }
        }
        excludeDirs -= excl

        sourceDirs += mainSourceSet.output.generatedSourcesDir
        testSourceDirs += testSourceSet.output.generatedSourcesDir
        generatedSourceDirs += [mainSourceSet.output.generatedSourcesDir, testSourceSet.output.generatedSourcesDir]

        if (GradleVersion.current() >= GradleVersion.version("3.4")) {
          // Gradle 3.4 changed IDEA mappings
          // See https://docs.gradle.org/3.4/release-notes.html#idea-mapping-has-been-simplified
          scopes.PROVIDED.plus += [project.configurations.getByName(mainSourceSet.aptConfigurationName)]
          scopes.TEST.plus += [project.configurations.getByName(testSourceSet.aptConfigurationName)]
        } else {
          // NOTE: ideally we'd use PROVIDED for both, but then every transitive dependency in
          // compile or testCompile configurations that would also be in compileOnly and
          // testCompileOnly would end up in PROVIDED.
          scopes.COMPILE.plus += [
              project.configurations.getByName(mainSourceSet.compileOnlyConfigurationName),
              project.configurations.getByName(mainSourceSet.aptConfigurationName)
          ]
          scopes.TEST.plus += [
              project.configurations.getByName(testSourceSet.compileOnlyConfigurationName),
              project.configurations.getByName(testSourceSet.aptConfigurationName)
          ]
        }
      }
    }
  }

  private void configureIdeaProject(Project project) {
    if (project.parent == null) {
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
