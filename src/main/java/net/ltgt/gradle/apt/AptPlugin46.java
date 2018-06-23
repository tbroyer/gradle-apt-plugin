package net.ltgt.gradle.apt;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;
import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.HasConvention;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.process.CommandLineArgumentProvider;

class AptPlugin46 extends AptPlugin.Impl {

  static final String GENERATED_SOURCES_DESTINATION_DIR_DEPRECATION_MESSAGE =
      "The generatedSourcesDestinationDir property has been deprecated. Please use the options.annotationProcessorGeneratedSourcesDirectory property instead.";
  static final String APT_OPTIONS_PROCESSORPATH_DEPRECATION_MESSAGE =
      "The aptOptions.processorpath property has been deprecated. Please use the options.annotationProcessorPath property instead.";

  @Override
  protected <T> void addExtension(
      ExtensionContainer extensionContainer, Class<T> publicType, String name, T extension) {
    extensionContainer.add(publicType, name, extension);
  }

  @Override
  protected AptPlugin.AptConvention createAptConvention(
      Project project, AbstractCompile task, CompileOptions compileOptions) {
    return new AptConvention46(project, task, compileOptions);
  }

  @Override
  protected AptPlugin.AptOptions createAptOptions(
      Project project, AbstractCompile task, CompileOptions compileOptions) {
    return new AptOptions46(project, task, compileOptions);
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
    return new AptSourceSetConvention46(project, sourceSet);
  }

  @Override
  protected void ensureCompileOnlyConfiguration(
      Project project, SourceSet sourceSet, AptPlugin.AptSourceSetConvention convention) {
    // no-op
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

  @Override
  String getCompileOnlyConfigurationName(SourceSet sourceSet) {
    return sourceSet.getCompileOnlyConfigurationName();
  }

  private static class AptSourceSetConvention46 extends AptPlugin.AptSourceSetConvention {
    private AptSourceSetConvention46(Project project, SourceSet sourceSet) {
      super(project, sourceSet);
    }

    @Override
    public FileCollection getAnnotationProcessorPath() {
      return sourceSet.getAnnotationProcessorPath();
    }

    @Override
    public void setAnnotationProcessorPath(FileCollection annotationProcessorPath) {
      sourceSet.setAnnotationProcessorPath(annotationProcessorPath);
    }

    @Override
    public String getCompileOnlyConfigurationName() {
      return sourceSet.getCompileOnlyConfigurationName();
    }

    @Override
    public String getAnnotationProcessorConfigurationName() {
      return sourceSet.getAnnotationProcessorConfigurationName();
    }
  }

  private static class AptConvention46 extends AptPlugin.AptConvention {
    private final Project project;
    private final AbstractCompile task;
    private final CompileOptions compileOptions;

    AptConvention46(Project project, AbstractCompile task, CompileOptions compileOptions) {
      this.project = project;
      this.task = task;
      this.compileOptions = compileOptions;
    }

    @Override
    public File getGeneratedSourcesDestinationDir() {
      DeprecationLogger.nagUserWith(task, GENERATED_SOURCES_DESTINATION_DIR_DEPRECATION_MESSAGE);
      return compileOptions.getAnnotationProcessorGeneratedSourcesDirectory();
    }

    @Override
    public void setGeneratedSourcesDestinationDir(final Object generatedSourcesDestinationDir) {
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

  private static class AptOptions46 extends AptPlugin.AptOptions
      implements CommandLineArgumentProvider, Named {
    private final Project project;
    private final AbstractCompile task;
    private final CompileOptions compileOptions;

    private AptOptions46(Project project, AbstractCompile task, CompileOptions compileOptions) {
      this.project = project;
      this.task = task;
      this.compileOptions = compileOptions;
    }

    @Override
    public String getName() {
      return "apt";
    }

    @Internal
    @Override
    public FileCollection getProcessorpath() {
      DeprecationLogger.nagUserWith(task, APT_OPTIONS_PROCESSORPATH_DEPRECATION_MESSAGE);
      return compileOptions.getAnnotationProcessorPath();
    }

    @Override
    public void setProcessorpath(final Object processorpath) {
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
