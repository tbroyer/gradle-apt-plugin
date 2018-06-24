package net.ltgt.gradle.apt;

import static net.ltgt.gradle.apt.AptPlugin.AptOptions;
import static net.ltgt.gradle.apt.CompatibilityUtils.dir;
import static net.ltgt.gradle.apt.CompatibilityUtils.files;
import static net.ltgt.gradle.apt.CompatibilityUtils.getInputs;
import static net.ltgt.gradle.apt.CompatibilityUtils.getOutputs;
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
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.CompileOptions;

class AptPlugin25to211 extends AptPlugin.Impl {

  @Override
  protected <T> void addExtension(
      ExtensionContainer extensionContainer, Class<T> publicType, String name, T extension) {
    extensionContainer.add(name, extension);
  }

  @Override
  protected AptPlugin.AptConvention createAptConvention(
      Project project, AbstractCompile task, CompileOptions compileOptions) {
    return new AptConvention25to211(project);
  }

  @Override
  protected AptOptions createAptOptions(
      Project project, AbstractCompile task, CompileOptions compileOptions) {
    return new AptOptions25to211(project);
  }

  @Override
  protected void configureCompileTask(
      Project project, final AbstractCompile task, final CompileOptions compileOptions) {
    property(
        getInputs(task),
        "aptOptions.annotationProcessing",
        new Callable<Object>() {
          @Override
          public Object call() {
            return task.getExtensions().getByType(AptOptions.class).isAnnotationProcessing();
          }
        });
    optionalProperty(
        getInputs(task),
        "aptOptions.processors",
        new Callable<Object>() {
          @Nullable
          @Override
          public Object call() {
            return task.getExtensions().getByType(AptOptions.class).getProcessors();
          }
        });
    optionalProperty(
        getInputs(task),
        "aptOptions.processorArgs",
        new Callable<Object>() {
          @Nullable
          @Override
          public Object call() {
            return task.getExtensions().getByType(AptOptions.class).getProcessorArgs();
          }
        });

    files(
        getInputs(task),
        new Callable<Object>() {
          @Nullable
          @Override
          public Object call() {
            return task.getExtensions().getByType(AptOptions.class).getProcessorpath();
          }
        });

    dir(
        getOutputs(task),
        new Callable<Object>() {
          @Nullable
          @Override
          public Object call() {
            return task.getConvention()
                .getPlugin(AptPlugin.AptConvention.class)
                .getGeneratedSourcesDestinationDir();
          }
        });

    task.doFirst(
        new Action<Task>() {
          @Override
          public void execute(Task task) {
            AptConvention25to211 convention =
                task.getConvention().getPlugin(AptConvention25to211.class);
            convention.makeDirectories();
            compileOptions.getCompilerArgs().addAll(convention.asArguments());
            compileOptions
                .getCompilerArgs()
                .addAll(task.getExtensions().getByType(AptOptions.class).asArguments());
          }
        });
  }

  @Override
  protected AptPlugin.AptSourceSetConvention createAptSourceSetConvention(
      Project project, SourceSet sourceSet) {
    return new AptSourceSetConvention25to211(project, sourceSet);
  }

  @Override
  protected void ensureCompileOnlyConfiguration(
      final Project project,
      final SourceSet sourceSet,
      AptPlugin.AptSourceSetConvention convention) {
    Configuration configuration =
        project.getConfigurations().create(convention.getCompileOnlyConfigurationName());
    configuration.setVisible(false);
    configuration.setDescription("Compile-only classpath for " + sourceSet.getName());
    configuration.extendsFrom(
        project.getConfigurations().findByName(sourceSet.getCompileConfigurationName()));

    sourceSet.setCompileClasspath(configuration);

    if (SourceSet.TEST_SOURCE_SET_NAME.equals(sourceSet.getName())) {
      final Configuration conf = configuration;
      project
          .getPlugins()
          .withType(
              JavaPlugin.class,
              new Action<JavaPlugin>() {
                @Override
                public void execute(JavaPlugin javaPlugin) {
                  sourceSet.setCompileClasspath(
                      project.files(
                          project
                              .getConvention()
                              .getPlugin(JavaPluginConvention.class)
                              .getSourceSets()
                              .getByName(SourceSet.MAIN_SOURCE_SET_NAME)
                              .getOutput(),
                          conf));
                }
              });
    }
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
              @Nullable
              @Override
              public FileCollection call() {
                return ((HasConvention) sourceSet)
                    .getConvention()
                    .getPlugin(AptPlugin.AptSourceSetConvention.class)
                    .getAnnotationProcessorPath();
              }
            });
    task.getConvention()
        .getPlugin(AptPlugin.AptConvention.class)
        .setGeneratedSourcesDestinationDir(
            new Callable<File>() {
              @Nullable
              @Override
              public File call() {
                return ((HasConvention) sourceSet.getOutput())
                    .getConvention()
                    .getPlugin(AptPlugin.AptSourceSetOutputConvention.class)
                    .getGeneratedSourcesDir();
              }
            });
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
    return ((HasConvention) sourceSet)
        .getConvention()
        .getPlugin(AptPlugin.AptSourceSetConvention.class)
        .getCompileOnlyConfigurationName();
  }

  private static class AptSourceSetConvention25to211 extends AptPlugin.AptSourceSetConvention {
    @Nullable private FileCollection annotationProcessorPath;

    private AptSourceSetConvention25to211(Project project, SourceSet sourceSet) {
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
      return sourceSet.getCompileConfigurationName() + "Only";
    }

    @Override
    public String getAnnotationProcessorConfigurationName() {
      // HACK: we use the same naming logic/scheme as for tasks, so just use SourceSet#getTaskName
      return sourceSet.getTaskName("", "annotationProcessor");
    }
  }

  private static class AptConvention25to211 extends AptPlugin.AptConvention {
    private final Project project;

    @Nullable private Object generatedSourcesDestinationDir;

    AptConvention25to211(Project project) {
      this.project = project;
    }

    @Override
    @Nullable
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
      File generatedSourcesDestinationDir = getGeneratedSourcesDestinationDir();
      if (generatedSourcesDestinationDir == null) {
        return Collections.emptyList();
      }
      List<String> result = new ArrayList<>();
      result.add("-s");
      result.add(generatedSourcesDestinationDir.getPath());
      return result;
    }
  }

  private static class AptOptions25to211 extends AptPlugin.AptOptions {
    private final Project project;

    @Nullable private Object processorpath;

    private AptOptions25to211(Project project) {
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
      FileCollection processorpath = getProcessorpath();
      if (processorpath == null || processorpath.isEmpty()) {
        return super.asArguments();
      }
      List<String> result = new ArrayList<>();
      result.add("-processorpath");
      result.add(processorpath.getAsPath());
      result.addAll(super.asArguments());
      return result;
    }
  }
}
