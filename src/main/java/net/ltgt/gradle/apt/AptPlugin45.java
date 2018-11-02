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
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.HasConvention;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.CompileOptions;

class AptPlugin45 extends AptPlugin.Impl {

  static final String GENERATED_SOURCES_DESTINATION_DIR_DEPRECATION_MESSAGE =
      "The generatedSourcesDestinationDir property has been deprecated. Please use the options.annotationProcessorGeneratedSourcesDirectory property instead.";
  static final String APT_OPTIONS_PROCESSORPATH_DEPRECATION_MESSAGE =
      "The aptOptions.processorpath property has been deprecated. Please use the options.annotationProcessorPath property instead.";

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
  protected AptPlugin.AptConvention createAptConvention(
      Project project, AbstractCompile task, CompileOptions compileOptions) {
    return new AptConvention45(project, task, compileOptions);
  }

  @Override
  protected AptPlugin.AptOptions createAptOptions(
      Project project, AbstractCompile task, CompileOptions compileOptions) {
    return new AptOptions45(project, task, compileOptions);
  }

  @SuppressWarnings("deprecation")
  @Override
  protected void configureCompileTask(
      Project project, AbstractCompile task, CompileOptions compileOptions) {
    compileOptions
        .getCompilerArgumentProviders()
        .add(
            (org.gradle.api.tasks.compile.CompilerArgumentProvider)
                task.getExtensions().getByType(AptPlugin.AptOptions.class));
  }

  @Override
  protected AptPlugin.AptSourceSetConvention createAptSourceSetConvention(
      Project project, SourceSet sourceSet) {
    return new AptSourceSetConvention45(project, sourceSet);
  }

  @Override
  protected Configuration ensureAnnotationProcessorConfiguration(
      Project project, SourceSet sourceSet, AptPlugin.AptSourceSetConvention convention) {
    Configuration annotationProcessorConfiguration =
        project.getConfigurations().create(convention.getAnnotationProcessorConfigurationName());
    annotationProcessorConfiguration.setVisible(false);
    annotationProcessorConfiguration.setDescription(
        "Annotation processors and their dependencies for " + sourceSet.getName() + ".");
    return annotationProcessorConfiguration;
  }

  @Override
  protected void configureCompileTaskForSourceSet(
      Project project,
      final SourceSet sourceSet,
      AbstractCompile task,
      CompileOptions compileOptions) {
    if (compileOptions.getAnnotationProcessorPath() == null) {
      compileOptions.setAnnotationProcessorPath(
          project.files(
              new Callable<FileCollection>() {
                @Nullable
                @Override
                public FileCollection call() {
                  return ((HasConvention) sourceSet)
                      .getConvention()
                      .getPlugin(AptPlugin.AptSourceSetConvention.class)
                      .getAnnotationProcessorPath();
                }
              }));
    }
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
    return ((HasConvention) sourceSet)
        .getConvention()
        .getPlugin(AptPlugin.AptSourceSetConvention.class)
        .getAnnotationProcessorConfigurationName();
  }

  private static class AptSourceSetConvention45 extends AptPlugin.AptSourceSetConvention {
    @Nullable private FileCollection annotationProcessorPath;

    private AptSourceSetConvention45(Project project, SourceSet sourceSet) {
      super(project, sourceSet);
    }

    @Nullable
    @Override
    public FileCollection getAnnotationProcessorPath() {
      return annotationProcessorPath;
    }

    @Override
    public void setAnnotationProcessorPath(@Nullable FileCollection annotationProcessorPath) {
      this.annotationProcessorPath = annotationProcessorPath;
    }

    @Override
    public String getAnnotationProcessorConfigurationName() {
      // HACK: we use the same naming logic/scheme as for tasks, so just use SourceSet#getTaskName
      return sourceSet.getTaskName("", "annotationProcessor");
    }
  }

  private static class AptConvention45 extends AptPlugin.AptConvention {
    private final Project project;
    private final AbstractCompile task;
    private final CompileOptions compileOptions;

    AptConvention45(Project project, AbstractCompile task, CompileOptions compileOptions) {
      this.project = project;
      this.task = task;
      this.compileOptions = compileOptions;
    }

    @Nullable
    @Override
    public File getGeneratedSourcesDestinationDir() {
      DeprecationLogger.nagUserWith(task, GENERATED_SOURCES_DESTINATION_DIR_DEPRECATION_MESSAGE);
      return compileOptions.getAnnotationProcessorGeneratedSourcesDirectory();
    }

    @Override
    public void setGeneratedSourcesDestinationDir(
        @Nullable final Object generatedSourcesDestinationDir) {
      DeprecationLogger.nagUserWith(task, GENERATED_SOURCES_DESTINATION_DIR_DEPRECATION_MESSAGE);
      if (generatedSourcesDestinationDir == null) {
        compileOptions.setAnnotationProcessorGeneratedSourcesDirectory((File) null);
      } else {
        compileOptions.setAnnotationProcessorGeneratedSourcesDirectory(
            project.provider(
                new Callable<File>() {
                  @Override
                  public File call() {
                    return project.file(generatedSourcesDestinationDir);
                  }
                }));
      }
    }
  }

  @SuppressWarnings("deprecation")
  private static class AptOptions45 extends AptPlugin.AptOptions
      implements org.gradle.api.tasks.compile.CompilerArgumentProvider {
    private final Project project;
    private final AbstractCompile task;
    private final CompileOptions compileOptions;

    private AptOptions45(Project project, AbstractCompile task, CompileOptions compileOptions) {
      this.project = project;
      this.task = task;
      this.compileOptions = compileOptions;
    }

    @Nullable
    @Internal
    @Override
    public FileCollection getProcessorpath() {
      DeprecationLogger.nagUserWith(task, APT_OPTIONS_PROCESSORPATH_DEPRECATION_MESSAGE);
      return compileOptions.getAnnotationProcessorPath();
    }

    @Override
    public void setProcessorpath(@Nullable final Object processorpath) {
      DeprecationLogger.nagUserWith(task, APT_OPTIONS_PROCESSORPATH_DEPRECATION_MESSAGE);
      if (processorpath == null || processorpath instanceof FileCollection) {
        compileOptions.setAnnotationProcessorPath((FileCollection) processorpath);
      } else {
        compileOptions.setAnnotationProcessorPath(project.files(processorpath));
      }
    }

    @Override
    public List<String> asArguments() {
      return super.asArguments();
    }
  }
}
