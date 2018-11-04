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

import java.util.List;
import java.util.concurrent.Callable;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.HasConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.CompileOptions;

class AptPlugin45 extends AptPlugin.Impl {

  @Override
  protected <T extends Task> Object createTask(
      Project project, String taskName, Class<T> taskClass, Action<T> configure) {
    return project.getTasks().create(taskName, taskClass, configure);
  }

  @Override
  protected <T extends Task> void configureTasks(
      Project project, Class<T> taskClass, Action<T> configure) {
    project.getTasks().withType(taskClass, configure);
  }

  @Override
  protected <T extends Task> void configureTask(
      Project project, Class<T> taskClass, String taskName, Action<T> configure) {
    project.getTasks().withType(taskClass).getByName(taskName, configure);
  }

  @Override
  protected AptPlugin.AptOptions createAptOptions() {
    return new AptOptions45();
  }

  @SuppressWarnings("deprecation")
  @Override
  protected void configureCompileTask(
      AbstractCompile task, CompileOptions compileOptions, AptPlugin.AptOptions aptOptions) {
    compileOptions
        .getCompilerArgumentProviders()
        .add((org.gradle.api.tasks.compile.CompilerArgumentProvider) aptOptions);
  }

  @Override
  protected void ensureConfigurations(Project project, SourceSet sourceSet) {
    Configuration annotationProcessorConfiguration =
        project.getConfigurations().create(getAnnotationProcessorConfigurationName(sourceSet));
    annotationProcessorConfiguration.setVisible(false);
    annotationProcessorConfiguration.setDescription(
        "Annotation processors and their dependencies for " + sourceSet.getName() + ".");

    AptPlugin.AptSourceSetConvention convention =
        new AptPlugin.AptSourceSetConvention(project, sourceSet, annotationProcessorConfiguration);
    ((HasConvention) sourceSet).getConvention().getPlugins().put(AptPlugin.PLUGIN_ID, convention);
  }

  @Override
  protected void configureCompileTaskForSourceSet(
      Project project, final SourceSet sourceSet, CompileOptions compileOptions) {
    if (compileOptions.getAnnotationProcessorPath() == null) {
      compileOptions.setAnnotationProcessorPath(
          project.files(
              (Callable<FileCollection>)
                  () ->
                      ((HasConvention) sourceSet)
                          .getConvention()
                          .getPlugin(AptPlugin.AptSourceSetConvention.class)
                          .getAnnotationProcessorPath()));
    }
  }

  @Override
  String getAnnotationProcessorConfigurationName(SourceSet sourceSet) {
    // HACK: we use the same naming logic/scheme as for tasks, so just use SourceSet#getTaskName
    return sourceSet.getTaskName("", "annotationProcessor");
  }

  @SuppressWarnings("deprecation")
  private static class AptOptions45 extends AptPlugin.AptOptions
      implements org.gradle.api.tasks.compile.CompilerArgumentProvider {

    @Override
    public List<String> asArguments() {
      return super.asArguments();
    }
  }
}
