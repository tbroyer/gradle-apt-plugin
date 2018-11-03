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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.HasConvention;
import org.gradle.api.plugins.GroovyBasePlugin;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;
import org.gradle.api.tasks.Input;
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
    configureCompileTasks(project, JavaCompile.class, JavaCompile::getOptions);
    configureCompileTasks(project, GroovyCompile.class, GroovyCompile::getOptions);

    project
        .getPlugins()
        .withType(
            JavaBasePlugin.class,
            javaBasePlugin -> {
              final JavaPluginConvention javaConvention =
                  project.getConvention().getPlugin(JavaPluginConvention.class);
              javaConvention
                  .getSourceSets()
                  .all(
                      sourceSet -> {
                        IMPL.ensureConfigurations(project, sourceSet);

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

                        configureCompileTaskForSourceSet(
                            project,
                            sourceSet,
                            sourceSet.getCompileJavaTaskName(),
                            JavaCompile.class,
                            JavaCompile::getOptions);
                      });
            });
    project
        .getPlugins()
        .withType(
            GroovyBasePlugin.class,
            groovyBasePlugin -> {
              JavaPluginConvention javaConvention =
                  project.getConvention().getPlugin(JavaPluginConvention.class);
              javaConvention
                  .getSourceSets()
                  .all(
                      sourceSet ->
                          configureCompileTaskForSourceSet(
                              project,
                              sourceSet,
                              sourceSet.getCompileTaskName("groovy"),
                              GroovyCompile.class,
                              GroovyCompile::getOptions));
            });
  }

  private <T extends AbstractCompile> void configureCompileTasks(
      final Project project,
      Class<T> compileTaskClass,
      final Function<T, CompileOptions> getCompileOptions) {
    IMPL.configureTasks(
        project,
        compileTaskClass,
        task -> {
          CompileOptions compileOptions = getCompileOptions.apply(task);
          task.getExtensions().add(AptOptions.class, "aptOptions", IMPL.createAptOptions());
          IMPL.configureCompileTask(project, task, compileOptions);
        });
  }

  private <T extends AbstractCompile> void configureCompileTaskForSourceSet(
      final Project project,
      final SourceSet sourceSet,
      String compileTaskName,
      Class<T> compileTaskClass,
      final Function<T, CompileOptions> getCompileOptions) {
    IMPL.configureTask(
        project,
        compileTaskClass,
        compileTaskName,
        task ->
            IMPL.configureCompileTaskForSourceSet(
                project, sourceSet, task, getCompileOptions.apply(task)));
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
      } else {
        throw new UnsupportedOperationException();
      }
    }

    protected abstract <T extends Task> Object createTask(
        Project project, String taskName, Class<T> taskClass, Action<T> configure);

    protected abstract <T extends Task> void configureTasks(
        Project project, Class<T> taskClass, Action<T> configure);

    protected abstract <T extends Task> void configureTask(
        Project project, Class<T> taskClass, String taskName, Action<T> configure);

    protected abstract AptOptions createAptOptions();

    protected abstract void configureCompileTask(
        Project project, AbstractCompile task, CompileOptions compileOptions);

    abstract void ensureConfigurations(Project project, SourceSet sourceSet);

    protected abstract void configureCompileTaskForSourceSet(
        Project project, SourceSet sourceSet, AbstractCompile task, CompileOptions compileOptions);

    abstract String getAnnotationProcessorConfigurationName(SourceSet sourceSet);
  }

  public static class AptOptions implements HasPublicType {
    private boolean annotationProcessing = true;
    @Nullable private List<?> processors = new ArrayList<>();
    @Nullable private Map<String, ?> processorArgs = new LinkedHashMap<>();

    @Override
    public TypeOf<?> getPublicType() {
      return TypeOf.typeOf(AptOptions.class);
    }

    @Input
    public boolean isAnnotationProcessing() {
      return annotationProcessing;
    }

    public void setAnnotationProcessing(boolean annotationProcessing) {
      this.annotationProcessing = annotationProcessing;
    }

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

  public static final class AptSourceSetConvention {
    protected final Project project;
    protected final SourceSet sourceSet;

    @Nullable private FileCollection annotationProcessorPath;

    AptSourceSetConvention(
        Project project, SourceSet sourceSet, Configuration annotationProcessorPath) {
      this.project = project;
      this.sourceSet = sourceSet;
      this.annotationProcessorPath = annotationProcessorPath;
    }

    @Nullable
    public FileCollection getAnnotationProcessorPath() {
      return annotationProcessorPath;
    }

    public void setAnnotationProcessorPath(@Nullable FileCollection annotationProcessorPath) {
      this.annotationProcessorPath = annotationProcessorPath;
    }

    public String getAnnotationProcessorConfigurationName() {
      return IMPL.getAnnotationProcessorConfigurationName(sourceSet);
    }
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
