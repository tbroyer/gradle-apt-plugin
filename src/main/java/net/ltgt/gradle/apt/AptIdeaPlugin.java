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

import groovy.util.Node;
import groovy.util.NodeList;
import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.XmlProvider;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.HasConvention;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.plugins.ide.idea.GenerateIdeaModule;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.gradle.plugins.ide.idea.model.IdeaModule;
import org.gradle.plugins.ide.idea.model.IdeaProject;

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
                    ((HasConvention) sourceSet.getOutput())
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
                  ((HasConvention) mainSourceSet.getOutput())
                      .getConvention()
                      .getPlugin(AptPlugin.AptSourceSetOutputConvention.class)
                      .getGeneratedSourcesDir();
              File testGeneratedSourcesDir =
                  ((HasConvention) testSourceSet.getOutput())
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

            if (apt.isAddAptDependencies()) {
              final Configuration annotationProcessor =
                  project
                      .getConfigurations()
                      .getByName(
                          AptPlugin.IMPL.getAnnotationProcessorConfigurationName(mainSourceSet));
              final Configuration testAnnotationProcessor =
                  project
                      .getConfigurations()
                      .getByName(
                          AptPlugin.IMPL.getAnnotationProcessorConfigurationName(testSourceSet));
              getScope(apt.getMainDependenciesScope(), "plus").add(annotationProcessor);
              getScope("TEST", "plus").add(testAnnotationProcessor);
              AptPlugin.IMPL.configureTasks(
                  project,
                  GenerateIdeaModule.class,
                  new Action<GenerateIdeaModule>() {
                    @Override
                    public void execute(GenerateIdeaModule generateIdeaModule) {
                      generateIdeaModule.dependsOn(annotationProcessor, testAnnotationProcessor);
                    }
                  });
            }
          }

          private Set<File> addToSet(Set<File> sourceDirs, File... dirs) {
            Set<File> newSet = new LinkedHashSet<>(sourceDirs);
            newSet.addAll(Arrays.asList(dirs));
            return newSet;
          }

          @SuppressWarnings("NullAway")
          private Collection<Configuration> getScope(String scope, String plusOrMinus) {
            return ideaModule.getScopes().get(scope).get(plusOrMinus);
          }
        });
  }

  private void configureIdeaProject(final Project project) {
    if (project.getParent() == null) {
      final IdeaProject ideaProject =
          project.getExtensions().getByType(IdeaModel.class).getProject();
      final ProjectAptConvention apt = new ProjectAptConvention();
      ((HasConvention) ideaProject).getConvention().getPlugins().put("net.ltgt.apt-idea", apt);
      ideaProject
          .getIpr()
          .withXml(
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
                    for (Object n : (NodeList) compilerConfiguration.get("annotationProcessing")) {
                      compilerConfiguration.remove((Node) n);
                    }
                    Node annotationProcessing =
                        compilerConfiguration.appendNode("annotationProcessing");
                    Map<String, Object> profileAttributes = new LinkedHashMap<>();
                    profileAttributes.put("name", "Default");
                    profileAttributes.put("enabled", true);
                    profileAttributes.put("default", true);
                    Node profile = annotationProcessing.appendNode("profile", profileAttributes);
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
              });
    }
  }

  public static class ModuleApt {
    private boolean addGeneratedSourcesDirs = true;
    private boolean addAptDependencies = true;
    // Gradle integration in IDEA uses COMPILE scope
    private String mainDependenciesScope = isIdeaImport ? "COMPILE" : "PROVIDED";

    public boolean isAddGeneratedSourcesDirs() {
      return addGeneratedSourcesDirs;
    }

    public void setAddGeneratedSourcesDirs(boolean addGeneratedSourcesDirs) {
      this.addGeneratedSourcesDirs = addGeneratedSourcesDirs;
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
