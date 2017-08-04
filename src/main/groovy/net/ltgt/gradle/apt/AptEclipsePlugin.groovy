package net.ltgt.gradle.apt

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.SourceSet
import org.gradle.plugins.ide.eclipse.EclipsePlugin
import org.gradle.util.ConfigureUtil

class AptEclipsePlugin implements Plugin<Project> {

  @Override
  void apply(Project project) {
    project.plugins.apply(AptPlugin)
    project.plugins.apply(EclipsePlugin)

    project.plugins.withType(JavaPlugin) {
      def javaConvention = project.convention.getPlugin(JavaPluginConvention)
      def mainSourceSet = javaConvention.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
      def testSourceSet = javaConvention.sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME)

      configureEclipse(project, mainSourceSet, testSourceSet)
    }
  }

  /**
   * Inspired by https://github.com/mkarneim/pojobuilder/wiki/Enabling-PojoBuilder-for-Eclipse-Using-Gradle
   */
  private void configureEclipse(Project project, SourceSet mainSourceSet, SourceSet testSourceSet) {
    project.afterEvaluate {
      project.eclipse.classpath {
        plusConfigurations += [
            project.configurations.getByName(mainSourceSet.compileOnlyConfigurationName),
            project.configurations.getByName(testSourceSet.compileOnlyConfigurationName)
        ]
      }
    }
    if (!project.tasks.findByName('eclipseJdtApt')) {
      def task = project.tasks.create('eclipseJdtApt', GenerateEclipseJdtApt) {
        it.description = 'Generates the Eclipse JDT APT settings file.'
        it.inputFile = it.outputFile = project.file('.settings/org.eclipse.jdt.apt.core.prefs')

        def jdtApt = it.jdtApt
        project.eclipse.jdt.convention.plugins.put("net.ltgt.apt-eclipse", new JdtAptConvention(jdtApt))
        jdtApt.conventionMapping.map("aptEnabled", {
          project.tasks.findByName(mainSourceSet.compileJavaTaskName).aptOptions.annotationProcessing
        })
        jdtApt.conventionMapping.map("genSrcDir", { project.file('.apt_generated') })
        jdtApt.conventionMapping.map("processorOptions", {
          project.tasks.findByName(mainSourceSet.compileJavaTaskName).aptOptions.processorArgs
        })

        project.eclipse.jdt.file.withProperties {
          it.'org.eclipse.jdt.core.compiler.processAnnotations' = jdtApt.aptEnabled ? 'enabled' : 'disabled'
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
        inputs.files project.configurations.getByName(mainSourceSet.aptConfigurationName),
            project.configurations.getByName(testSourceSet.aptConfigurationName)
        outputs.file factorypath
        doLast {
          factorypath.withWriter {
            new groovy.xml.MarkupBuilder(it).'factorypath' {
              [project.configurations.getByName(mainSourceSet.aptConfigurationName),
               project.configurations.getByName(testSourceSet.aptConfigurationName)]*.each {
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

  class JdtAptConvention {
    final EclipseJdtApt apt

    JdtAptConvention(EclipseJdtApt apt) {
      this.apt = apt
    }

    void apt(Closure<? super EclipseJdtApt> closure) {
      ConfigureUtil.configure(closure, apt)
    }

    void apt(Action<? super EclipseJdtApt> action) {
      action.execute(apt)
    }
  }
}
