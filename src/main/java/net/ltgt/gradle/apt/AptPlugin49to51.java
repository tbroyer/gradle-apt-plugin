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

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;
import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetOutput;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.process.CommandLineArgumentProvider;

class AptPlugin49to51 extends AptPlugin.Impl {

  private static final String SOURCE_SET_OUTPUT_GENERATED_SOURCES_DIRS = "generatedSourcesDirs";

  @Override
  protected <T extends Task> Object createTask(
      Project project, String taskName, Class<T> taskClass, Action<T> configure) {
    return project.getTasks().register(taskName, taskClass, configure);
  }

  @Override
  protected <T extends Task> void configureTasks(
      Project project, Class<T> taskClass, Action<T> configure) {
    project.getTasks().withType(taskClass).configureEach(configure);
  }

  @Override
  protected <T extends Task> TaskProvider<T> configureTask(
      Project project, Class<T> taskClass, String taskName, Action<T> configure) {
    TaskProvider<T> task = project.getTasks().withType(taskClass).named(taskName);
    task.configure(configure);
    return task;
  }

  @Override
  protected AptPlugin.AptOptions createAptOptions() {
    return new AptOptions49to51();
  }

  @Override
  protected void configureCompileTask(
      AbstractCompile task, CompileOptions compileOptions, AptPlugin.AptOptions aptOptions) {
    compileOptions.getCompilerArgumentProviders().add((CommandLineArgumentProvider) aptOptions);
  }

  @Override
  protected void ensureConfigurations(Project project, SourceSet sourceSet) {
    // no-op
  }

  @Override
  protected void configureCompileTaskForSourceSet(
      Project project,
      final SourceSet sourceSet,
      SourceDirectorySet sourceDirectorySet,
      CompileOptions compileOptions) {
    compileOptions.setAnnotationProcessorGeneratedSourcesDirectory(
        project.provider(
            () ->
                new File(
                    project.getBuildDir(),
                    "generated/sources/annotationProcessor/"
                        + sourceDirectorySet.getName()
                        + "/"
                        + sourceSet.getName())));
  }

  @Override
  String getAnnotationProcessorConfigurationName(SourceSet sourceSet) {
    return sourceSet.getAnnotationProcessorConfigurationName();
  }

  @Override
  <T extends AbstractCompile> void addSourceSetOutputGeneratedSourcesDir(
      Project project,
      SourceSetOutput sourceSetOutput,
      String compileTaskName,
      Class<T> compileTaskClass,
      Function<T, CompileOptions> getCompileOptions,
      Object taskOrProvider) {
    ((ExtensionAware) sourceSetOutput)
        .getExtensions()
        .<ConfigurableFileCollection>configure(
            SOURCE_SET_OUTPUT_GENERATED_SOURCES_DIRS,
            files ->
                files
                    .from(
                        (Callable<File>)
                            () ->
                                getCompileOptions
                                    .apply(
                                        project
                                            .getTasks()
                                            .withType(compileTaskClass)
                                            .getByName(compileTaskName))
                                    .getAnnotationProcessorGeneratedSourcesDirectory())
                    .builtBy(taskOrProvider));
  }

  @Override
  void setupGeneratedSourcesDirs(Project project, SourceSetOutput sourceSetOutput) {
    final FileCollection files = project.files();
    ((ExtensionAware) sourceSetOutput)
        .getExtensions()
        .add(FileCollection.class, SOURCE_SET_OUTPUT_GENERATED_SOURCES_DIRS, files);
  }

  @Override
  FileCollection getGeneratedSourcesDirs(SourceSetOutput sourceSetOutput) {
    return (FileCollection)
        ((ExtensionAware) sourceSetOutput)
            .getExtensions()
            .getByName(SOURCE_SET_OUTPUT_GENERATED_SOURCES_DIRS);
  }

  private static class AptOptions49to51 extends AptPlugin.AptOptions
      implements CommandLineArgumentProvider, Named {

    @Override
    public String getName() {
      return "apt";
    }

    @Override
    public List<String> asArguments() {
      return super.asArguments();
    }
  }
}
