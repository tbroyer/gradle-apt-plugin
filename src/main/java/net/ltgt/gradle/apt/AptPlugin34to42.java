package net.ltgt.gradle.apt;

import static net.ltgt.gradle.apt.CompatibilityUtils.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.Nullable;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.compile.JavaCompile;

class AptPlugin34to42 extends AptPlugin.Impl {

  static final String APT_OPTIONS_PROCESSORPATH_DEPRECATION_MESSAGE =
      "The aptOptions.processorpath property has been deprecated on JavaCompile tasks. Please use the options.annotationProcessorPath property instead.";

  // options.annotationProcessorPath actually fails with GroovyCompile;
  // let's use it only where we know it works (JavaCompile),
  // and keep using the previous implementation otherwise.
  final AptPlugin.Impl prevImpl = new AptPlugin30to33();

  @Override
  protected AptPlugin.AptConvention createAptConvention(
      Project project, AbstractCompile task, CompileOptions compileOptions) {
    if (!(task instanceof JavaCompile)) {
      return prevImpl.createAptConvention(project, task, compileOptions);
    }
    return new AptConvention34to42(project, task, compileOptions);
  }

  @Override
  protected void configureCompileTask(
      Project project, final AbstractCompile task, final CompileOptions compileOptions) {
    if (!(task instanceof JavaCompile)) {
      prevImpl.configureCompileTask(project, task, compileOptions);
      return;
    }

    property(
        task.getInputs(),
        "aptOptions.annotationProcessing",
        new Callable<Object>() {
          @Override
          public Object call() {
            return task.getConvention()
                .getPlugin(AptConvention34to42.class)
                .getAptOptions()
                .isAnnotationProcessing();
          }
        });
    optionalProperty(
        task.getInputs(),
        "aptOptions.processors",
        new Callable<Object>() {
          @Override
          public Object call() {
            return task.getConvention()
                .getPlugin(AptConvention34to42.class)
                .getAptOptions()
                .getProcessors();
          }
        });
    optionalProperty(
        task.getInputs(),
        "aptOptions.processorArgs",
        new Callable<Object>() {
          @Override
          public Object call() {
            return task.getConvention()
                .getPlugin(AptConvention34to42.class)
                .getAptOptions()
                .getProcessorArgs();
          }
        });

    task.getOutputs()
        .dir(
            new Callable<Object>() {
              @Override
              public Object call() {
                return task.getConvention()
                    .getPlugin(AptPlugin.AptConvention.class)
                    .getGeneratedSourcesDestinationDir();
              }
            })
        .withPropertyName("generatedSourcesDestinationDir")
        .optional();

    task.doFirst(
        new Action<Task>() {
          @Override
          public void execute(Task task) {
            AptConvention34to42 convention =
                task.getConvention().getPlugin(AptConvention34to42.class);
            convention.makeDirectories();
            compileOptions.getCompilerArgs().addAll(convention.asArguments());
          }
        });
  }

  @Override
  protected AptPlugin.AptSourceSetConvention createAptSourceSetConvention(
      Project project, SourceSet sourceSet) {
    return new AptSourceSetConvention34to42(project, sourceSet);
  }

  @Override
  protected void ensureCompileOnlyConfiguration(
      Project project, SourceSet sourceSet, AptPlugin.AptSourceSetConvention convention) {
    // no-op
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
    if (!(task instanceof JavaCompile)) {
      prevImpl.configureCompileTaskForSourceSet(project, sourceSet, task, compileOptions);
      return;
    }

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
    task.getConvention()
        .getPlugin(AptPlugin.AptConvention.class)
        .setGeneratedSourcesDestinationDir(
            new Callable<File>() {
              @Override
              public File call() {
                return new DslObject(sourceSet.getOutput())
                    .getConvention()
                    .getPlugin(AptPlugin.AptSourceSetOutputConvention.class)
                    .getGeneratedSourcesDir();
              }
            });
  }

  private static class AptSourceSetConvention34to42 extends AptPlugin.AptSourceSetConvention {
    private FileCollection annotationProcessorPath;

    private AptSourceSetConvention34to42(Project project, SourceSet sourceSet) {
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

  private static class AptConvention34to42 extends AptPlugin.AptConvention {
    private final Project project;

    private final AptOptions34to42 aptOptions;

    private Object generatedSourcesDestinationDir;

    AptConvention34to42(Project project, AbstractCompile task, CompileOptions compileOptions) {
      this.project = project;
      this.aptOptions = new AptOptions34to42(project, task, compileOptions);
    }

    @Nullable
    @Override
    public File getGeneratedSourcesDestinationDir() {
      if (generatedSourcesDestinationDir == null) {
        return null;
      }
      return project.file(generatedSourcesDestinationDir);
    }

    @Override
    public void setGeneratedSourcesDestinationDir(@Nullable Object generatedSourcesDestinationDir) {
      this.generatedSourcesDestinationDir = generatedSourcesDestinationDir;
    }

    @Override
    public AptPlugin.AptOptions getAptOptions() {
      return aptOptions;
    }

    void makeDirectories() {
      if (generatedSourcesDestinationDir != null) {
        project.mkdir(generatedSourcesDestinationDir);
      }
    }

    List<String> asArguments() {
      List<String> result = new ArrayList<>();
      if (generatedSourcesDestinationDir != null) {
        result.add("-s");
        result.add(getGeneratedSourcesDestinationDir().getPath());
      }
      result.addAll(aptOptions.asArguments());
      return result;
    }
  }

  private static class AptOptions34to42 extends AptPlugin.AptOptions {
    private final Project project;
    private final AbstractCompile task;
    private final CompileOptions compileOptions;

    private AptOptions34to42(Project project, AbstractCompile task, CompileOptions compileOptions) {
      this.project = project;
      this.task = task;
      this.compileOptions = compileOptions;
    }

    @Nullable
    @Override
    public FileCollection getProcessorpath() {
      DeprecationLogger.nagUserWith(task, APT_OPTIONS_PROCESSORPATH_DEPRECATION_MESSAGE);
      return compileOptions.getAnnotationProcessorPath();
    }

    @Override
    public void setProcessorpath(@Nullable Object processorpath) {
      DeprecationLogger.nagUserWith(task, APT_OPTIONS_PROCESSORPATH_DEPRECATION_MESSAGE);
      if (processorpath == null || processorpath instanceof FileCollection) {
        compileOptions.setAnnotationProcessorPath((FileCollection) processorpath);
      } else {
        compileOptions.setAnnotationProcessorPath(project.files(processorpath));
      }
    }
  }
}
