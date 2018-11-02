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
import javax.annotation.Nullable;
import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.HasConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.process.CommandLineArgumentProvider;

class AptPlugin46to48 extends AptPlugin.Impl {

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
    return new AptOptions46to48();
  }

  @Override
  protected void configureCompileTask(
      Project project, AbstractCompile task, CompileOptions compileOptions) {
    compileOptions
        .getCompilerArgumentProviders()
        .add(
            (CommandLineArgumentProvider)
                task.getExtensions().getByType(AptPlugin.AptOptions.class));
  }

  @Override
  protected AptPlugin.AptSourceSetConvention createAptSourceSetConvention(
      Project project, SourceSet sourceSet) {
    return new AptSourceSetConvention46to48(project, sourceSet);
  }

  @Override
  protected Configuration ensureAnnotationProcessorConfiguration(
      Project project, SourceSet sourceSet, AptPlugin.AptSourceSetConvention convention) {
    return project
        .getConfigurations()
        .getByName(sourceSet.getAnnotationProcessorConfigurationName());
  }

  @Override
  protected void configureCompileTaskForSourceSet(
      Project project,
      final SourceSet sourceSet,
      AbstractCompile task,
      CompileOptions compileOptions) {
    compileOptions.setAnnotationProcessorGeneratedSourcesDirectory(
        project.provider(
            new Callable<File>() {
              @Nullable
              @Override
              public File call() {
                return ((HasConvention) sourceSet.getOutput())
                    .getConvention()
                    .getPlugin(AptPlugin.AptSourceSetOutputConvention.class)
                    .getGeneratedSourcesDir();
              }
            }));
  }

  @Override
  String getAnnotationProcessorConfigurationName(SourceSet sourceSet) {
    return sourceSet.getAnnotationProcessorConfigurationName();
  }

  private static class AptSourceSetConvention46to48 extends AptPlugin.AptSourceSetConvention {
    private AptSourceSetConvention46to48(Project project, SourceSet sourceSet) {
      super(project, sourceSet);
    }

    @Override
    public FileCollection getAnnotationProcessorPath() {
      return sourceSet.getAnnotationProcessorPath();
    }

    @Override
    public void setAnnotationProcessorPath(@Nullable FileCollection annotationProcessorPath) {
      sourceSet.setAnnotationProcessorPath(annotationProcessorPath);
    }

    @Override
    public String getAnnotationProcessorConfigurationName() {
      return sourceSet.getAnnotationProcessorConfigurationName();
    }
  }

  private static class AptOptions46to48 extends AptPlugin.AptOptions
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
