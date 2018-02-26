package net.ltgt.gradle.apt;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.Nullable;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.compile.CompilerArgumentProvider;

class AptPlugin45 extends AptPlugin.Impl {

  static final String GENERATED_SOURCES_DESTINATION_DIR_DEPRECATION_MESSAGE =
      "The generatedSourcesDestinationDir property has been deprecated. Please use the options.annotationProcessorGeneratedSourcesDirectory property instead.";
  static final String APT_OPTIONS_PROCESSORPATH_DEPRECATION_MESSAGE =
      "The aptOptions.processorpath property has been deprecated. Please use the options.annotationProcessorPath property instead.";

  @Override
  protected AptPlugin.AptConvention createAptConvention(
      Project project, AbstractCompile task, CompileOptions compileOptions) {
    return new AptConvention45(project, task, compileOptions);
  }

  @Override
  protected void configureCompileTask(
      Project project, AbstractCompile task, CompileOptions compileOptions) {
    compileOptions
        .getCompilerArgumentProviders()
        .add(task.getConvention().getPlugin(AptConvention45.class).getAptOptions());
  }

  @Override
  protected AptPlugin.AptSourceSetConvention createAptSourceSetConvention(
      Project project, SourceSet sourceSet) {
    return new AptSourceSetConvention45(project, sourceSet);
  }

  @Override
  protected void ensureCompileOnlyConfiguration(
      Project project, SourceSet sourceSet, AptPlugin.AptSourceSetConvention convention) {
    // no-op
  }

  @Override
  protected Configuration ensureAnnotationProcessorConfiguration(
      Project project, SourceSet sourceSet, AptPlugin.AptSourceSetConvention convention) {
    // Gradle 4.6 will create such a configuration already; let's be future-proof.
    String annotationProcessorConfigurationName =
        convention.getAnnotationProcessorConfigurationName();
    Configuration annotationProcessorConfiguration =
        project.getConfigurations().findByName(annotationProcessorConfigurationName);
    if (annotationProcessorConfiguration == null) {
      annotationProcessorConfiguration =
          project.getConfigurations().create(annotationProcessorConfigurationName);
      annotationProcessorConfiguration.setVisible(false);
      annotationProcessorConfiguration.setDescription(
          "Annotation processors and their dependencies for " + sourceSet.getName() + ".");
    }
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
                @Override
                public FileCollection call() {
                  return new DslObject(sourceSet)
                      .getConvention()
                      .getPlugin(AptPlugin.AptSourceSetConvention.class)
                      .getAnnotationProcessorPath();
                }
              }));
    }
    compileOptions.setAnnotationProcessorGeneratedSourcesDirectory(
        project.provider(
            new Callable<File>() {
              @Override
              public File call() {
                return new DslObject(sourceSet.getOutput())
                    .getConvention()
                    .getPlugin(AptPlugin.AptSourceSetOutputConvention.class)
                    .getGeneratedSourcesDir();
              }
            }));
  }

  private static class AptSourceSetConvention45 extends AptPlugin.AptSourceSetConvention {
    private FileCollection annotationProcessorPath;

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
    public String getCompileOnlyConfigurationName() {
      return sourceSet.getCompileOnlyConfigurationName();
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

    private final AptOptions45 aptOptions;

    AptConvention45(Project project, AbstractCompile task, CompileOptions compileOptions) {
      this.project = project;
      this.task = task;
      this.compileOptions = compileOptions;
      this.aptOptions = new AptOptions45(project, task, compileOptions);
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

    @Override
    public AptOptions45 getAptOptions() {
      return aptOptions;
    }
  }

  private static class AptOptions45 extends AptPlugin.AptOptions
      implements CompilerArgumentProvider {
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
