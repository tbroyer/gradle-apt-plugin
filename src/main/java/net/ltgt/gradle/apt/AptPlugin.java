package net.ltgt.gradle.apt;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.HasConvention;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.GroovyBasePlugin;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.compile.GroovyCompile;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.util.GradleVersion;

public class AptPlugin implements Plugin<Project> {

  static final String PLUGIN_ID = "net.ltgt.apt";

  static final Impl IMPL = Impl.newInstance();

  @Override
  public void apply(final Project project) {
    configureCompileTasks(
        project,
        JavaCompile.class,
        new GetCompileOptions<JavaCompile>() {
          @Override
          public CompileOptions getCompileOptions(JavaCompile task) {
            return task.getOptions();
          }
        });
    configureCompileTasks(
        project,
        GroovyCompile.class,
        new GetCompileOptions<GroovyCompile>() {
          @Override
          public CompileOptions getCompileOptions(GroovyCompile task) {
            return task.getOptions();
          }
        });

    project
        .getPlugins()
        .withType(
            JavaBasePlugin.class,
            new Action<JavaBasePlugin>() {
              @Override
              public void execute(JavaBasePlugin javaBasePlugin) {
                final JavaPluginConvention javaConvention =
                    project.getConvention().getPlugin(JavaPluginConvention.class);
                javaConvention
                    .getSourceSets()
                    .all(
                        new Action<SourceSet>() {
                          @Override
                          public void execute(final SourceSet sourceSet) {
                            AptSourceSetConvention convention =
                                IMPL.createAptSourceSetConvention(project, sourceSet);
                            ((HasConvention) sourceSet)
                                .getConvention()
                                .getPlugins()
                                .put(PLUGIN_ID, convention);

                            AptSourceSetOutputConvention outputConvention =
                                new AptSourceSetOutputConvention(project);
                            outputConvention.setGeneratedSourcesDir(
                                new File(
                                    project.getBuildDir(),
                                    "generated/source/apt/" + sourceSet.getName()));
                            ((HasConvention) sourceSet.getOutput())
                                .getConvention()
                                .getPlugins()
                                .put(PLUGIN_ID, outputConvention);

                            ensureConfigurations(project, sourceSet, convention);

                            configureCompileTaskForSourceSet(
                                project,
                                sourceSet,
                                sourceSet.getCompileJavaTaskName(),
                                JavaCompile.class,
                                new GetCompileOptions<JavaCompile>() {
                                  @Override
                                  public CompileOptions getCompileOptions(JavaCompile task) {
                                    return task.getOptions();
                                  }
                                });
                          }
                        });
              }
            });
    project
        .getPlugins()
        .withType(
            GroovyBasePlugin.class,
            new Action<GroovyBasePlugin>() {
              @Override
              public void execute(GroovyBasePlugin groovyBasePlugin) {
                JavaPluginConvention javaConvention =
                    project.getConvention().getPlugin(JavaPluginConvention.class);
                javaConvention
                    .getSourceSets()
                    .all(
                        new Action<SourceSet>() {
                          @Override
                          public void execute(SourceSet sourceSet) {
                            AptSourceSetConvention convention =
                                ((HasConvention) sourceSet)
                                    .getConvention()
                                    .getPlugin(AptSourceSetConvention.class);
                            configureCompileTaskForSourceSet(
                                project,
                                sourceSet,
                                sourceSet.getCompileTaskName("groovy"),
                                GroovyCompile.class,
                                new GetCompileOptions<GroovyCompile>() {
                                  @Override
                                  public CompileOptions getCompileOptions(GroovyCompile task) {
                                    return task.getOptions();
                                  }
                                });
                          }
                        });
              }
            });
  }

  private <T extends AbstractCompile> void configureCompileTasks(
      final Project project,
      Class<T> compileTaskClass,
      final GetCompileOptions<T> getCompileOptions) {
    IMPL.configureTasks(
        project,
        compileTaskClass,
        new Action<T>() {
          @Override
          public void execute(T task) {
            CompileOptions compileOptions = getCompileOptions.getCompileOptions(task);
            task.getConvention()
                .getPlugins()
                .put(PLUGIN_ID, IMPL.createAptConvention(project, task, compileOptions));
            IMPL.addExtension(
                task.getExtensions(),
                AptOptions.class,
                "aptOptions",
                IMPL.createAptOptions(project, task, compileOptions));
            IMPL.configureCompileTask(project, task, compileOptions);
          }
        });
  }

  private void ensureConfigurations(
      Project project, SourceSet sourceSet, AptSourceSetConvention convention) {
    IMPL.ensureCompileOnlyConfiguration(project, sourceSet, convention);
    Configuration annotationProcessorConfiguration =
        IMPL.ensureAnnotationProcessorConfiguration(project, sourceSet, convention);
    convention.setAnnotationProcessorPath(annotationProcessorConfiguration);
    createAptConfiguration(project, sourceSet, convention, annotationProcessorConfiguration);
  }

  private void createAptConfiguration(
      final Project project,
      SourceSet sourceSet,
      AptSourceSetConvention convention,
      final Configuration annotationProcessorConfiguration) {
    final Configuration aptConfiguration =
        project.getConfigurations().create(convention.getAptConfigurationName());
    aptConfiguration.setVisible(false);
    aptConfiguration.setDescription(
        "Processor path for "
            + sourceSet.getName()
            + ". Deprecated, please use the "
            + annotationProcessorConfiguration.getName()
            + " configuration instead.");
    aptConfiguration
        .getDependencies()
        .whenObjectAdded(
            new Action<Dependency>() {
              @Override
              public void execute(Dependency dependency) {
                DeprecationLogger.nagUserWith(
                    project,
                    "The "
                        + aptConfiguration.getName()
                        + " configuration has been deprecated. Please use the "
                        + annotationProcessorConfiguration.getName()
                        + " configuration instead.");
              }
            });
    annotationProcessorConfiguration.extendsFrom(aptConfiguration);
  }

  private <T extends AbstractCompile> void configureCompileTaskForSourceSet(
      final Project project,
      final SourceSet sourceSet,
      String compileTaskName,
      Class<T> compileTaskClass,
      final GetCompileOptions<T> getCompileOptions) {
    IMPL.configureTask(
        project,
        compileTaskClass,
        compileTaskName,
        new Action<T>() {
          @Override
          public void execute(T task) {
            IMPL.configureCompileTaskForSourceSet(
                project, sourceSet, task, getCompileOptions.getCompileOptions(task));
          }
        });
  }

  private interface GetCompileOptions<T extends AbstractCompile> {
    CompileOptions getCompileOptions(T task);
  }

  abstract static class Impl {
    static Impl newInstance() {
      if (GradleVersion.current().compareTo(GradleVersion.version("4.9")) >= 0) {
        return new AptPlugin49();
      } else if (GradleVersion.current().compareTo(GradleVersion.version("4.6")) >= 0) {
        return new AptPlugin46to48();
      } else if (GradleVersion.current().compareTo(GradleVersion.version("4.5")) >= 0) {
        return new AptPlugin45();
      } else if (GradleVersion.current().compareTo(GradleVersion.version("4.3")) >= 0) {
        return new AptPlugin43to44();
      } else if (GradleVersion.current().compareTo(GradleVersion.version("3.5")) >= 0) {
        return new AptPlugin35to42();
      } else if (GradleVersion.current().compareTo(GradleVersion.version("3.4")) >= 0) {
        return new AptPlugin34();
      } else if (GradleVersion.current().compareTo(GradleVersion.version("3.0")) >= 0) {
        return new AptPlugin30to33();
      } else if (GradleVersion.current().compareTo(GradleVersion.version("2.12")) >= 0) {
        return new AptPlugin212to214();
      } else if (GradleVersion.current().compareTo(GradleVersion.version("2.5")) >= 0) {
        return new AptPlugin25to211();
      } else {
        throw new UnsupportedOperationException();
      }
    }

    protected abstract <T extends Task> void configureTasks(
        Project project, Class<T> taskClass, Action<T> configure);

    protected abstract <T extends Task> void configureTask(
        Project project, Class<T> taskClass, String taskName, Action<T> configure);

    protected abstract <T> void addExtension(
        ExtensionContainer extensionContainer, Class<T> publicType, String name, T extension);

    protected abstract AptConvention createAptConvention(
        Project project, AbstractCompile task, CompileOptions compileOptions);

    protected abstract AptOptions createAptOptions(
        Project project, AbstractCompile task, CompileOptions compileOptions);

    protected abstract void configureCompileTask(
        Project project, AbstractCompile task, CompileOptions compileOptions);

    protected abstract AptSourceSetConvention createAptSourceSetConvention(
        Project project, SourceSet sourceSet);

    protected abstract void ensureCompileOnlyConfiguration(
        Project project, SourceSet sourceSet, AptSourceSetConvention convention);

    protected abstract Configuration ensureAnnotationProcessorConfiguration(
        Project project, SourceSet sourceSet, AptSourceSetConvention convention);

    protected abstract void configureCompileTaskForSourceSet(
        Project project, SourceSet sourceSet, AbstractCompile task, CompileOptions compileOptions);

    abstract String getAnnotationProcessorConfigurationName(SourceSet sourceSet);

    abstract String getCompileOnlyConfigurationName(SourceSet sourceSet);
  }

  public abstract static class AptConvention {
    @Nullable
    public abstract File getGeneratedSourcesDestinationDir();

    public abstract void setGeneratedSourcesDestinationDir(
        @Nullable Object generatedSourcesDestinationDir);
  }

  public abstract static class AptOptions {
    private boolean annotationProcessing = true;
    @Nullable private List<?> processors = new ArrayList<>();
    @Nullable private Map<String, ?> processorArgs = new LinkedHashMap<>();

    @Input
    public boolean isAnnotationProcessing() {
      return annotationProcessing;
    }

    public void setAnnotationProcessing(boolean annotationProcessing) {
      this.annotationProcessing = annotationProcessing;
    }

    @Internal
    @Nullable
    public abstract FileCollection getProcessorpath();

    public abstract void setProcessorpath(@Nullable Object processorpath);

    @Input
    @Optional
    @Nullable
    public List<?> getProcessors() {
      return processors;
    }

    public void setProcessors(@Nullable List<?> processors) {
      this.processors = processors;
    }

    @Input
    @Optional
    @Nullable
    public Map<String, ?> getProcessorArgs() {
      return processorArgs;
    }

    public void setProcessorArgs(@Nullable Map<String, ?> processorArgs) {
      this.processorArgs = processorArgs;
    }

    protected List<String> asArguments() {
      ArrayList<String> arguments = new ArrayList<>();
      if (!annotationProcessing) {
        arguments.add("-proc:none");
      }
      if (processors != null && !processors.isEmpty()) {
        arguments.add("-processor");
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Object processor : processors) {
          if (!first) {
            sb.append(',');
          } else {
            first = false;
          }
          sb.append(processor);
        }
        arguments.add(sb.toString());
      }
      if (processorArgs != null) {
        for (Map.Entry<String, ?> entry : processorArgs.entrySet()) {
          arguments.add("-A" + entry.getKey() + "=" + entry.getValue());
        }
      }
      return arguments;
    }
  }

  public abstract static class AptSourceSetConvention {
    static final String PROCESSORPATH_DEPRECATION_MESSAGE =
        "The processorpath property has been deprecated. Please use the annotationProcessorPath property instead.";

    protected final Project project;
    protected final SourceSet sourceSet;

    public AptSourceSetConvention(Project project, SourceSet sourceSet) {
      this.project = project;
      this.sourceSet = sourceSet;
    }

    @Nullable
    public abstract FileCollection getAnnotationProcessorPath();

    public abstract void setAnnotationProcessorPath(
        @Nullable FileCollection annotationProcessorPath);

    @Nullable
    @Deprecated
    public FileCollection getProcessorpath() {
      DeprecationLogger.nagUserWith(
          project, "sourceSets." + sourceSet.getName() + ": " + PROCESSORPATH_DEPRECATION_MESSAGE);
      return getAnnotationProcessorPath();
    }

    @Deprecated
    public void setProcessorpath(@Nullable Object processorpath) {
      DeprecationLogger.nagUserWith(
          project, "sourceSets." + sourceSet.getName() + ": " + PROCESSORPATH_DEPRECATION_MESSAGE);
      if (processorpath == null || processorpath instanceof FileCollection) {
        setAnnotationProcessorPath((FileCollection) processorpath);
      } else {
        setAnnotationProcessorPath(project.files(processorpath));
      }
    }

    public abstract String getCompileOnlyConfigurationName();

    @Deprecated
    public String getAptConfigurationName() {
      // HACK: we use the same naming logic/scheme as for tasks, so just use SourceSet#getTaskName
      return sourceSet.getTaskName("", "apt");
    }

    public abstract String getAnnotationProcessorConfigurationName();
  }

  public static class AptSourceSetOutputConvention {
    private final Project project;

    @Nullable private Object generatedSourcesDir;

    public AptSourceSetOutputConvention(Project project) {
      this.project = project;
    }

    @Nullable
    public File getGeneratedSourcesDir() {
      if (generatedSourcesDir == null) {
        return null;
      }
      return project.file(generatedSourcesDir);
    }

    public void setGeneratedSourcesDir(@Nullable Object generatedSourcesDir) {
      this.generatedSourcesDir = generatedSourcesDir;
    }
  }
}
