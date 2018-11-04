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
package net.ltgt.gradle.apt;

import java.util.ArrayList;
import java.util.Arrays;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.PropertiesTransformer;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.SourceSet;
import org.gradle.internal.xml.XmlTransformer;
import org.gradle.plugins.ide.api.PropertiesFileContentMerger;
import org.gradle.plugins.ide.api.XmlFileContentMerger;
import org.gradle.plugins.ide.eclipse.EclipsePlugin;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;

public class AptEclipsePlugin implements Plugin<Project> {

  private static Action<Task> dependsOn(final Object taskDependency) {
    return task -> task.dependsOn(taskDependency);
  }

  @Override
  public void apply(final Project project) {
    project.getPlugins().apply(AptPlugin.class);
    project.getPlugins().apply(EclipsePlugin.class);

    project
        .getPlugins()
        .withType(
            JavaPlugin.class,
            javaPlugin -> {
              JavaPluginConvention javaConvention =
                  project.getConvention().getPlugin(JavaPluginConvention.class);
              SourceSet mainSourceSet =
                  javaConvention.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
              SourceSet testSourceSet =
                  javaConvention.getSourceSets().getByName(SourceSet.TEST_SOURCE_SET_NAME);

              configureEclipse(project, mainSourceSet, testSourceSet);
            });
  }

  /**
   * Inspired by
   * https://github.com/mkarneim/pojobuilder/wiki/Enabling-PojoBuilder-for-Eclipse-Using-Gradle
   */
  private void configureEclipse(
      final Project project, final SourceSet mainSourceSet, final SourceSet testSourceSet) {
    final EclipseModel eclipseModel = project.getExtensions().getByType(EclipseModel.class);
    configureJdtApt(project, eclipseModel, mainSourceSet);
    configureFactorypath(project, eclipseModel, mainSourceSet, testSourceSet);
  }

  private void configureJdtApt(
      final Project project, EclipseModel eclipseModel, final SourceSet mainSourceSet) {
    final EclipseJdtApt jdtApt =
        ((ExtensionAware) eclipseModel.getJdt())
            .getExtensions()
            .create(
                "apt",
                EclipseJdtApt.class,
                project,
                new PropertiesFileContentMerger(new PropertiesTransformer()));
    jdtApt.setAptEnabled(
        project.provider(
            () ->
                project
                    .getTasks()
                    .getByName(mainSourceSet.getCompileJavaTaskName())
                    .getExtensions()
                    .getByType(AptPlugin.AptOptions.class)
                    .isAnnotationProcessing()));
    jdtApt.setProcessorOptions(
        project.provider(
            () ->
                project
                    .getTasks()
                    .getByName(mainSourceSet.getCompileJavaTaskName())
                    .getExtensions()
                    .getByType(AptPlugin.AptOptions.class)
                    .getProcessorArgs()));

    eclipseModel
        .getJdt()
        .getFile()
        .withProperties(
            properties ->
                properties.setProperty(
                    "org.eclipse.jdt.core.compiler.processAnnotations",
                    jdtApt.isAptEnabled() ? "enabled" : "disabled"));

    final Object task =
        AptPlugin.IMPL.createTask(
            project,
            "eclipseJdtApt",
            GenerateEclipseJdtApt.class,
            generateEclipseJdtApt -> {
              generateEclipseJdtApt.setDescription("Generates the Eclipse JDT APT settings file.");
              generateEclipseJdtApt.setInputFile(
                  project.file(".settings/org.eclipse.jdt.apt.core.prefs"));
              generateEclipseJdtApt.setOutputFile(
                  project.file(".settings/org.eclipse.jdt.apt.core.prefs"));

              generateEclipseJdtApt.setJdtApt(jdtApt);
            });
    AptPlugin.IMPL.configureTask(project, Task.class, "eclipse", dependsOn(task));
    final Object cleanTask =
        AptPlugin.IMPL.createTask(
            project,
            "cleanEclipseJdtApt",
            Delete.class,
            cleanEclipseJdtApt -> cleanEclipseJdtApt.delete(task));
    AptPlugin.IMPL.configureTask(project, Task.class, "cleanEclipse", dependsOn(cleanTask));
  }

  private void configureFactorypath(
      final Project project,
      EclipseModel eclipseModel,
      SourceSet mainSourceSet,
      SourceSet testSourceSet) {
    final EclipseFactorypath factorypath =
        ((ExtensionAware) eclipseModel)
            .getExtensions()
            .create(
                "factorypath",
                EclipseFactorypath.class,
                new XmlFileContentMerger(new XmlTransformer()));
    factorypath.setPlusConfigurations(
        new ArrayList<>(
            Arrays.asList(
                project
                    .getConfigurations()
                    .getByName(
                        AptPlugin.IMPL.getAnnotationProcessorConfigurationName(mainSourceSet)),
                project
                    .getConfigurations()
                    .getByName(
                        AptPlugin.IMPL.getAnnotationProcessorConfigurationName(testSourceSet)))));
    final Object task =
        AptPlugin.IMPL.createTask(
            project,
            "eclipseFactorypath",
            GenerateEclipseFactorypath.class,
            generateEclipseFactorypath -> {
              generateEclipseFactorypath.setDescription("Generates the Eclipse factorypath file.");
              generateEclipseFactorypath.setInputFile(project.file(".factorypath"));
              generateEclipseFactorypath.setOutputFile(project.file(".factorypath"));

              generateEclipseFactorypath.setFactorypath(factorypath);
              generateEclipseFactorypath.dependsOn(factorypath.getPlusConfigurations().toArray());
            });
    AptPlugin.IMPL.configureTask(project, Task.class, "eclipse", dependsOn(task));
    final Object cleanTask =
        AptPlugin.IMPL.createTask(
            project,
            "cleanEclipseFactorypath",
            Delete.class,
            cleanEclipseFactorypath -> cleanEclipseFactorypath.delete(task));
    AptPlugin.IMPL.configureTask(project, Task.class, "cleanEclipse", dependsOn(cleanTask));
  }
}
