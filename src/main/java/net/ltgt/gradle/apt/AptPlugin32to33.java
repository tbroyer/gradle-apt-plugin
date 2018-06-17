package net.ltgt.gradle.apt;

import static net.ltgt.gradle.apt.CompatibilityUtils.optionalProperty;
import static net.ltgt.gradle.apt.CompatibilityUtils.property;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.Nullable;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.HasConvention;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.CompileOptions;

class AptPlugin32to33 extends AptPlugin.Impl {

  @Override
  protected <T> void addExtension(
      ExtensionContainer extensionContainer, Class<T> publicType, String name, T extension) {
    extensionContainer.add(name, extension);
  }

  @Override
  protected AptPlugin.AptConvention createAptConvention(
      Project project, AbstractCompile task, CompileOptions compileOptions) {
    return new AptConvention32to33(project);
  }

  @Override
  protected AptPlugin.AptOptions createAptOptions(
      Project project, AbstractCompile task, CompileOptions compileOptions) {
    return new AptOptions32to33(project);
  }

  @Override
  protected void configureCompileTask(
      Project project, final AbstractCompile task, final CompileOptions compileOptions) {
    property(
        task.getInputs(),
        "aptOptions.annotationProcessing",
        new Callable<Object>() {
          @Override
          public Object call() {
            return task.getExtensions()
                .getByType(AptPlugin.AptOptions.class)
                .isAnnotationProcessing();
          }
        });
    optionalProperty(
        task.getInputs(),
        "aptOptions.processors",
        new Callable<Object>() {
          @Override
          public Object call() {
            return task.getExtensions().getByType(AptPlugin.AptOptions.class).getProcessors();
          }
        });
    optionalProperty(
        task.getInputs(),
        "aptOptions.processorArgs",
        new Callable<Object>() {
          @Override
          public Object call() {
            return task.getExtensions().getByType(AptPlugin.AptOptions.class).getProcessorArgs();
          }
        });

    task.getInputs()
        .files(
            new Callable<Object>() {
              @Override
              public Object call() {
                return task.getExtensions()
                    .getByType(AptPlugin.AptOptions.class)
                    .getProcessorpath();
              }
            })
        .withPropertyName("aptOptions.processorpath")
        .optional();

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
            AptConvention32to33 convention =
                task.getConvention().getPlugin(AptConvention32to33.class);
            convention.makeDirectories();
            compileOptions.getCompilerArgs().addAll(convention.asArguments());
            compileOptions
                .getCompilerArgs()
                .addAll(task.getExtensions().getByType(AptPlugin.AptOptions.class).asArguments());
          }
        });
  }

  @Override
  protected AptPlugin.AptSourceSetConvention createAptSourceSetConvention(
      Project project, SourceSet sourceSet) {
    return new AptSourceSetConvention32to33(project, sourceSet);
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
    task.getExtensions()
        .getByType(AptPlugin.AptOptions.class)
        .setProcessorpath(
            new Callable<FileCollection>() {
              @Override
              public FileCollection call() {
                return ((HasConvention) sourceSet)
                    .getConvention()
                    .getPlugin(AptPlugin.AptSourceSetConvention.class)
                    .getAnnotationProcessorPath();
              }
            });
    final AptPlugin.AptConvention convention =
        task.getConvention().getPlugin(AptPlugin.AptConvention.class);
    convention.setGeneratedSourcesDestinationDir(
        new Callable<File>() {
          @Override
          public File call() {
            return ((HasConvention) sourceSet.getOutput())
                .getConvention()
                .getPlugin(AptPlugin.AptSourceSetOutputConvention.class)
                .getGeneratedSourcesDir();
          }
        });
    sourceSet
        .getAllJava()
        .srcDir(
            project
                .files(
                    new Callable<File>() {
                      @Override
                      public File call() {
                        return convention.getGeneratedSourcesDestinationDir();
                      }
                    })
                .builtBy(task));
    sourceSet
        .getAllSource()
        .srcDir(
            project
                .files(
                    new Callable<File>() {
                      @Override
                      public File call() {
                        return convention.getGeneratedSourcesDestinationDir();
                      }
                    })
                .builtBy(task));
  }

  @Override
  String getAnnotationProcessorConfigurationName(SourceSet sourceSet) {
    return ((HasConvention) sourceSet)
        .getConvention()
        .getPlugin(AptPlugin.AptSourceSetConvention.class)
        .getAnnotationProcessorConfigurationName();
  }

  @Override
  String getCompileOnlyConfigurationName(SourceSet sourceSet) {
    return sourceSet.getCompileOnlyConfigurationName();
  }

  private static class AptSourceSetConvention32to33 extends AptPlugin.AptSourceSetConvention {
    private FileCollection annotationProcessorPath;

    private AptSourceSetConvention32to33(Project project, SourceSet sourceSet) {
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

  private static class AptConvention32to33 extends AptPlugin.AptConvention {
    private final Project project;

    private Object generatedSourcesDestinationDir;

    AptConvention32to33(Project project) {
      this.project = project;
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

    void makeDirectories() {
      if (generatedSourcesDestinationDir != null) {
        project.mkdir(generatedSourcesDestinationDir);
      }
    }

    List<String> asArguments() {
      if (generatedSourcesDestinationDir == null) {
        return Collections.emptyList();
      }
      List<String> result = new ArrayList<>();
      result.add("-s");
      result.add(getGeneratedSourcesDestinationDir().getPath());
      return result;
    }
  }

  private static class AptOptions32to33 extends AptPlugin.AptOptions {
    private final Project project;

    private Object processorpath;

    private AptOptions32to33(Project project) {
      this.project = project;
    }

    @Nullable
    @Override
    public FileCollection getProcessorpath() {
      if (processorpath == null) {
        return null;
      }
      return project.files(processorpath);
    }

    @Override
    public void setProcessorpath(@Nullable Object processorpath) {
      this.processorpath = processorpath;
    }

    @Override
    protected List<String> asArguments() {
      if (processorpath == null || getProcessorpath().isEmpty()) {
        return super.asArguments();
      }
      List<String> result = new ArrayList<>();
      result.add("-processorpath");
      result.add(getProcessorpath().getAsPath());
      result.addAll(super.asArguments());
      return result;
    }
  }
}
