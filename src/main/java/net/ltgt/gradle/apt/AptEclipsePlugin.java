package net.ltgt.gradle.apt;

import static net.ltgt.gradle.apt.CompatibilityUtils.getOutputs;

import groovy.lang.Closure;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import org.codehaus.groovy.runtime.MethodClosure;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.SourceSet;
import org.gradle.plugins.ide.eclipse.EclipsePlugin;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.util.ConfigureUtil;

public class AptEclipsePlugin implements Plugin<Project> {

  @Override
  public void apply(final Project project) {
    project.getPlugins().apply(AptPlugin.class);
    project.getPlugins().apply(EclipsePlugin.class);

    project
        .getPlugins()
        .withType(
            JavaPlugin.class,
            new Action<JavaPlugin>() {
              @Override
              public void execute(JavaPlugin javaPlugin) {
                JavaPluginConvention javaConvention =
                    project.getConvention().getPlugin(JavaPluginConvention.class);
                SourceSet mainSourceSet =
                    javaConvention.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
                SourceSet testSourceSet =
                    javaConvention.getSourceSets().getByName(SourceSet.TEST_SOURCE_SET_NAME);

                configureEclipse(project, mainSourceSet, testSourceSet);
              }
            });
  }

  /**
   * Inspired by
   * https://github.com/mkarneim/pojobuilder/wiki/Enabling-PojoBuilder-for-Eclipse-Using-Gradle
   */
  private void configureEclipse(
      final Project project, final SourceSet mainSourceSet, final SourceSet testSourceSet) {
    final EclipseModel eclipseModel = project.getExtensions().getByType(EclipseModel.class);

    project.afterEvaluate(
        new Action<Project>() {
          @Override
          public void execute(final Project project) {
            eclipseModel
                .getClasspath()
                .getPlusConfigurations()
                .addAll(
                    Arrays.asList(
                        project
                            .getConfigurations()
                            .getByName(
                                new DslObject(mainSourceSet)
                                    .getConvention()
                                    .getPlugin(AptPlugin.AptSourceSetConvention.class)
                                    .getCompileOnlyConfigurationName()),
                        project
                            .getConfigurations()
                            .getByName(
                                new DslObject(testSourceSet)
                                    .getConvention()
                                    .getPlugin(AptPlugin.AptSourceSetConvention.class)
                                    .getCompileOnlyConfigurationName())));
          }
        });
    if (project.getTasks().findByName("eclipseJdtApt") == null) {
      GenerateEclipseJdtApt task =
          project
              .getTasks()
              .create(
                  "eclipseJdtApt",
                  GenerateEclipseJdtApt.class,
                  new Action<GenerateEclipseJdtApt>() {
                    @Override
                    public void execute(GenerateEclipseJdtApt generateEclipseJdtApt) {
                      generateEclipseJdtApt.setDescription(
                          "Generates the Eclipse JDT APT settings file.");
                      generateEclipseJdtApt.setInputFile(
                          project.file(".settings/org.eclipse.jdt.apt.core.prefs"));
                      generateEclipseJdtApt.setOutputFile(
                          project.file(".settings/org.eclipse.jdt.apt.core.prefs"));

                      final EclipseJdtApt jdtApt = generateEclipseJdtApt.getJdtApt();
                      new DslObject(eclipseModel.getJdt())
                          .getConvention()
                          .getPlugins()
                          .put("net.ltgt.apt-eclipse", new JdtAptConvention(jdtApt));
                      ConventionMapping conventionMapping =
                          new DslObject(jdtApt).getConventionMapping();
                      conventionMapping.map(
                          "aptEnabled",
                          new Callable<Boolean>() {
                            @Override
                            public Boolean call() throws Exception {
                              return project
                                  .getTasks()
                                  .findByName(mainSourceSet.getCompileJavaTaskName())
                                  .getConvention()
                                  .getPlugin(AptPlugin.AptConvention.class)
                                  .getAptOptions()
                                  .isAnnotationProcessing();
                            }
                          });
                      conventionMapping.map(
                          "genSrcDir",
                          new Callable<File>() {
                            @Override
                            public File call() throws Exception {
                              return project.file(".apt_generated");
                            }
                          });
                      conventionMapping.map(
                          "processorOptions",
                          new Callable<Map<String, ?>>() {
                            @Override
                            public Map<String, ?> call() throws Exception {
                              return project
                                  .getTasks()
                                  .findByName(mainSourceSet.getCompileJavaTaskName())
                                  .getConvention()
                                  .getPlugin(AptPlugin.AptConvention.class)
                                  .getAptOptions()
                                  .getProcessorArgs();
                            }
                          });

                      eclipseModel
                          .getJdt()
                          .getFile()
                          .withProperties(
                              // withProperties(Action) overload was added in Gradle 2.14
                              new MethodClosure(
                                  new Action<Properties>() {
                                    @Override
                                    public void execute(Properties properties) {
                                      properties.setProperty(
                                          "org.eclipse.jdt.core.compiler.processAnnotations",
                                          jdtApt.isAptEnabled() ? "enabled" : "disabled");
                                    }
                                  },
                                  "execute"));
                    }
                  });
      project.getTasks().getByName("eclipse").dependsOn(task);
      Delete cleanTask = project.getTasks().create("cleanEclipseJdtApt", Delete.class);
      cleanTask.delete(getOutputs(task));
      project.getTasks().getByName("cleanEclipse").dependsOn(cleanTask);
    }
    if (project.getTasks().findByName("eclipseFactorypath") == null) {
      GenerateEclipseFactorypath task =
          project
              .getTasks()
              .create(
                  "eclipseFactorypath",
                  GenerateEclipseFactorypath.class,
                  new Action<GenerateEclipseFactorypath>() {
                    @Override
                    public void execute(GenerateEclipseFactorypath generateEclipseFactorypath) {
                      generateEclipseFactorypath.setDescription(
                          "Generates the Eclipse factorypath file.");
                      generateEclipseFactorypath.setInputFile(project.file(".factorypath"));
                      generateEclipseFactorypath.setOutputFile(project.file(".factorypath"));

                      EclipseFactorypath factorypath = generateEclipseFactorypath.getFactorypath();
                      new DslObject(eclipseModel)
                          .getConvention()
                          .getPlugins()
                          .put("net.ltgt.apt-eclipse", new FactorypathConvention(factorypath));
                      factorypath.setPlusConfigurations(
                          new ArrayList<>(
                              Arrays.asList(
                                  project
                                      .getConfigurations()
                                      .getByName(
                                          new DslObject(mainSourceSet)
                                              .getConvention()
                                              .getPlugin(AptPlugin.AptSourceSetConvention.class)
                                              .getAnnotationProcessorConfigurationName()),
                                  project
                                      .getConfigurations()
                                      .getByName(
                                          new DslObject(testSourceSet)
                                              .getConvention()
                                              .getPlugin(AptPlugin.AptSourceSetConvention.class)
                                              .getAnnotationProcessorConfigurationName()))));
                    }
                  });
      project.getTasks().getByName("eclipse").dependsOn(task);
      Delete cleanTask = project.getTasks().create("cleanEclipseFactorypath", Delete.class);
      cleanTask.delete(getOutputs(task));
      project.getTasks().getByName("cleanEclipse").dependsOn(cleanTask);
    }
  }

  public static class JdtAptConvention {
    private final EclipseJdtApt apt;

    public JdtAptConvention(EclipseJdtApt apt) {
      this.apt = apt;
    }

    public EclipseJdtApt getApt() {
      return apt;
    }

    public void apt(Closure<? super EclipseJdtApt> closure) {
      ConfigureUtil.configure(closure, apt);
    }

    public void apt(Action<? super EclipseJdtApt> action) {
      action.execute(apt);
    }
  }

  public static class FactorypathConvention {
    private final EclipseFactorypath factorypath;

    public FactorypathConvention(EclipseFactorypath factorypath) {
      this.factorypath = factorypath;
    }

    public EclipseFactorypath getFactorypath() {
      return factorypath;
    }

    public void factorypath(Closure<? super EclipseFactorypath> closure) {
      ConfigureUtil.configure(closure, factorypath);
    }

    public void factorypath(Action<? super EclipseFactorypath> action) {
      action.execute(factorypath);
    }
  }
}
