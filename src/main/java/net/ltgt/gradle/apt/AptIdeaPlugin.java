package net.ltgt.gradle.apt;

import groovy.util.Node;
import groovy.util.NodeList;
import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.codehaus.groovy.runtime.MethodClosure;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.XmlProvider;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.plugins.ide.idea.GenerateIdeaModule;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.gradle.plugins.ide.idea.model.IdeaModule;
import org.gradle.plugins.ide.idea.model.IdeaProject;
import org.gradle.util.GradleVersion;

public class AptIdeaPlugin implements Plugin<Project> {
  private static final boolean isIdeaImport =
      Boolean.getBoolean("idea.active") && System.getProperty("idea.version") != null;

  private static boolean classExists(String name) {
    try {
      Class.forName(name);
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  @Override
  public void apply(final Project project) {
    project.getPlugins().apply(AptPlugin.class);
    project.getPlugins().apply(IdeaPlugin.class);
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

                configureIdeaModule(project, mainSourceSet, testSourceSet);
              }
            });
    configureIdeaProject(project);
  }

  private void configureIdeaModule(
      Project project, final SourceSet mainSourceSet, final SourceSet testSourceSet) {
    final IdeaModule ideaModule = project.getExtensions().getByType(IdeaModel.class).getModule();
    final ModuleApt apt = new ModuleApt();
    ((ExtensionAware) ideaModule).getExtensions().add("apt", apt);
    project.afterEvaluate(
        new Action<Project>() {
          @Override
          public void execute(Project project) {
            if (apt.isAddGeneratedSourcesDirs()) {
              Set<File> excl = new LinkedHashSet<>();
              for (SourceSet sourceSet : new SourceSet[] {mainSourceSet, testSourceSet}) {
                File generatedSourcesDir =
                    new DslObject(sourceSet.getOutput())
                        .getConvention()
                        .getPlugin(AptPlugin.AptSourceSetOutputConvention.class)
                        .getGeneratedSourcesDir();
                for (File f = generatedSourcesDir;
                    f != null && !f.equals(project.getProjectDir());
                    f = f.getParentFile()) {
                  excl.add(f);
                }
              }
              // For some reason, modifying the existing collections doesn't work.
              // We need to copy the values and then assign it back.
              Set<File> excludeDirs = new LinkedHashSet<>(ideaModule.getExcludeDirs());
              if (excl.contains(project.getBuildDir())
                  && excludeDirs.contains(project.getBuildDir())) {
                excludeDirs.remove(project.getBuildDir());
                // Race condition: many of these will actually be created afterwards…
                File[] subdirs =
                    project
                        .getBuildDir()
                        .listFiles(
                            new FileFilter() {
                              @Override
                              public boolean accept(File pathname) {
                                return pathname.isDirectory();
                              }
                            });
                if (subdirs != null) {
                  excludeDirs.addAll(Arrays.asList(subdirs));
                }
              }
              excludeDirs.removeAll(excl);
              ideaModule.setExcludeDirs(excludeDirs);

              File mainGeneratedSourcesDir =
                  new DslObject(mainSourceSet.getOutput())
                      .getConvention()
                      .getPlugin(AptPlugin.AptSourceSetOutputConvention.class)
                      .getGeneratedSourcesDir();
              File testGeneratedSourcesDir =
                  new DslObject(testSourceSet.getOutput())
                      .getConvention()
                      .getPlugin(AptPlugin.AptSourceSetOutputConvention.class)
                      .getGeneratedSourcesDir();
              // For some reason, modifying the existing collections doesn't work.
              // We need to copy the values and then assign it back.
              ideaModule.setSourceDirs(
                  addToSet(ideaModule.getSourceDirs(), mainGeneratedSourcesDir));
              ideaModule.setTestSourceDirs(
                  addToSet(ideaModule.getTestSourceDirs(), testGeneratedSourcesDir));
              ideaModule.setGeneratedSourceDirs(
                  addToSet(
                      ideaModule.getGeneratedSourceDirs(),
                      mainGeneratedSourcesDir,
                      testGeneratedSourcesDir));
            }

            if (apt.isAddCompileOnlyDependencies() || apt.isAddAptDependencies()) {
              final List<Configuration> mainConfigurations = new ArrayList<>();
              final List<Configuration> testConfigurations = new ArrayList<>();
              if (apt.isAddCompileOnlyDependencies()) {
                mainConfigurations.add(
                    project
                        .getConfigurations()
                        .getByName(AptPlugin.IMPL.getCompileOnlyConfigurationName(mainSourceSet)));
                testConfigurations.add(
                    project
                        .getConfigurations()
                        .getByName(AptPlugin.IMPL.getCompileOnlyConfigurationName(testSourceSet)));
              }
              if (apt.isAddAptDependencies()) {
                mainConfigurations.add(
                    project
                        .getConfigurations()
                        .getByName(
                            AptPlugin.IMPL.getAnnotationProcessorConfigurationName(mainSourceSet)));
                testConfigurations.add(
                    project
                        .getConfigurations()
                        .getByName(
                            AptPlugin.IMPL.getAnnotationProcessorConfigurationName(testSourceSet)));
              }
              ideaModule
                  .getScopes()
                  .get(apt.getMainDependenciesScope())
                  .get("plus")
                  .addAll(mainConfigurations);
              ideaModule.getScopes().get("TEST").get("plus").addAll(testConfigurations);
              project
                  .getTasks()
                  .withType(
                      GenerateIdeaModule.class,
                      new Action<GenerateIdeaModule>() {
                        @Override
                        public void execute(GenerateIdeaModule generateIdeaModule) {
                          generateIdeaModule.dependsOn(mainConfigurations.toArray());
                          generateIdeaModule.dependsOn(testConfigurations.toArray());
                        }
                      });
            }
          }

          private Set<File> addToSet(Set<File> sourceDirs, File... dirs) {
            Set<File> newSet = new LinkedHashSet<>(sourceDirs);
            newSet.addAll(Arrays.asList(dirs));
            return newSet;
          }
        });
  }

  private void configureIdeaProject(final Project project) {
    if (project.getParent() == null) {
      final IdeaProject ideaProject =
          project.getExtensions().getByType(IdeaModel.class).getProject();
      final ProjectAptConvention apt = new ProjectAptConvention();
      new DslObject(ideaProject).getConvention().getPlugins().put("net.ltgt.apt-idea", apt);
      ideaProject
          .getIpr()
          .withXml(
              // withXml(Action) overload was added in Gradle 2.14
              new MethodClosure(
                  new Action<XmlProvider>() {
                    @Override
                    public void execute(XmlProvider xmlProvider) {
                      if (!apt.isConfigureAnnotationProcessing()) {
                        return;
                      }

                      for (Object it : (NodeList) xmlProvider.asNode().get("component")) {
                        Node compilerConfiguration = (Node) it;
                        if (!Objects.equals(
                            compilerConfiguration.attribute("name"), "CompilerConfiguration")) {
                          continue;
                        }
                        for (Object n :
                            (NodeList) compilerConfiguration.get("annotationProcessing")) {
                          compilerConfiguration.remove((Node) n);
                        }
                        Node annotationProcessing =
                            compilerConfiguration.appendNode("annotationProcessing");
                        Map<String, Object> profileAttributes = new LinkedHashMap<>();
                        profileAttributes.put("name", "Default");
                        profileAttributes.put("enabled", true);
                        profileAttributes.put("default", true);
                        Node profile =
                            annotationProcessing.appendNode("profile", profileAttributes);
                        // XXX: this assumes that all subprojects use the same name for their
                        // buildDir
                        profile.appendNode(
                            "sourceOutputDir",
                            Collections.singletonMap(
                                "name",
                                project.relativePath(project.getBuildDir())
                                    + "/generated/source/apt/"
                                    + SourceSet.MAIN_SOURCE_SET_NAME));
                        profile.appendNode(
                            "sourceTestOutputDir",
                            Collections.singletonMap(
                                "name",
                                project.relativePath(project.getBuildDir())
                                    + "/generated/source/apt/"
                                    + SourceSet.TEST_SOURCE_SET_NAME));
                        profile.appendNode(
                            "outputRelativeToContentRoot", Collections.singletonMap("value", true));
                        profile.appendNode(
                            "processorPath", Collections.singletonMap("useClasspath", true));
                      }
                    }
                  },
                  "execute"));
    }
  }

  public static class ModuleApt {
    private boolean addGeneratedSourcesDirs = true;
    private boolean addCompileOnlyDependencies =
        GradleVersion.current().compareTo(GradleVersion.version("2.12")) < 0;
    private boolean addAptDependencies = true;
    private String mainDependenciesScope =
        isIdeaImport
            // Gradle integration in IDEA uses COMPILE scope
            ? "COMPILE"
            : (GradleVersion.current().compareTo(GradleVersion.version("3.4")) >= 0)
                // Gradle 3.4 changed IDEA mappings; see
                // https://docs.gradle.org/3.4/release-notes.html#idea-mapping-has-been-simplified
                ? "PROVIDED"
                // NOTE: ideally we'd use PROVIDED for both, but then every transitive dependency in
                // compile or testCompile configurations that would also be in compileOnly and
                // testCompileOnly would end up in PROVIDED.
                : "COMPILE";

    public boolean isAddGeneratedSourcesDirs() {
      return addGeneratedSourcesDirs;
    }

    public void setAddGeneratedSourcesDirs(boolean addGeneratedSourcesDirs) {
      this.addGeneratedSourcesDirs = addGeneratedSourcesDirs;
    }

    public boolean isAddCompileOnlyDependencies() {
      return addCompileOnlyDependencies;
    }

    public void setAddCompileOnlyDependencies(boolean addCompileOnlyDependencies) {
      this.addCompileOnlyDependencies = addCompileOnlyDependencies;
    }

    public boolean isAddAptDependencies() {
      return addAptDependencies;
    }

    public void setAddAptDependencies(boolean addAptDependencies) {
      this.addAptDependencies = addAptDependencies;
    }

    public String getMainDependenciesScope() {
      return mainDependenciesScope;
    }

    public void setMainDependenciesScope(String mainDependenciesScope) {
      this.mainDependenciesScope = Objects.requireNonNull(mainDependenciesScope);
    }
  }

  public static class ProjectAptConvention {
    private boolean configureAnnotationProcessing = true;

    public boolean isConfigureAnnotationProcessing() {
      return configureAnnotationProcessing;
    }

    public void setConfigureAnnotationProcessing(boolean configureAnnotationProcessing) {
      this.configureAnnotationProcessing = configureAnnotationProcessing;
    }
  }
}
