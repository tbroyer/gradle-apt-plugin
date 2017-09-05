package net.ltgt.gradle.apt;

import groovy.util.Node;
import groovy.util.NodeList;
import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.codehaus.groovy.runtime.MethodClosure;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.XmlProvider;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.gradle.plugins.ide.idea.model.IdeaModule;
import org.gradle.plugins.ide.idea.model.IdeaProject;
import org.gradle.util.GradleVersion;

public class AptIdeaPlugin implements Plugin<Project> {
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
    project.afterEvaluate(
        new Action<Project>() {
          @Override
          public void execute(Project project) {
            final IdeaModule ideaModule =
                project.getExtensions().getByType(IdeaModel.class).getModule();
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
                && ideaModule.getExcludeDirs().contains(project.getBuildDir())) {
              ideaModule.getExcludeDirs().remove(project.getBuildDir());
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
            ideaModule.setSourceDirs(addToSet(ideaModule.getSourceDirs(), mainGeneratedSourcesDir));
            ideaModule.setTestSourceDirs(
                addToSet(ideaModule.getTestSourceDirs(), testGeneratedSourcesDir));
            ideaModule.setGeneratedSourceDirs(
                addToSet(
                    ideaModule.getGeneratedSourceDirs(),
                    mainGeneratedSourcesDir,
                    testGeneratedSourcesDir));

            final AptPlugin.AptSourceSetConvention mainSourceSetConvention =
                new DslObject(mainSourceSet)
                    .getConvention()
                    .getPlugin(AptPlugin.AptSourceSetConvention.class);
            final AptPlugin.AptSourceSetConvention testSourceSetConvention =
                new DslObject(testSourceSet)
                    .getConvention()
                    .getPlugin(AptPlugin.AptSourceSetConvention.class);
            if (GradleVersion.current().compareTo(GradleVersion.version("3.4")) >= 0) {
              // Gradle 3.4 changed IDEA mappings
              // See https://docs.gradle.org/3.4/release-notes.html#idea-mapping-has-been-simplified
              ideaModule
                  .getScopes()
                  .get("PROVIDED")
                  .get("plus")
                  .add(
                      project
                          .getConfigurations()
                          .getByName(mainSourceSetConvention.getAptConfigurationName()));
              ideaModule
                  .getScopes()
                  .get("TEST")
                  .get("plus")
                  .add(
                      project
                          .getConfigurations()
                          .getByName(testSourceSetConvention.getAptConfigurationName()));
            } else {
              // NOTE: ideally we'd use PROVIDED for both, but then every transitive dependency in
              // compile or testCompile configurations that would also be in compileOnly and
              // testCompileOnly would end up in PROVIDED.
              ideaModule
                  .getScopes()
                  .get("COMPILE")
                  .get("plus")
                  .addAll(
                      Arrays.asList(
                          project
                              .getConfigurations()
                              .getByName(mainSourceSetConvention.getCompileOnlyConfigurationName()),
                          project
                              .getConfigurations()
                              .getByName(mainSourceSetConvention.getAptConfigurationName())));
              ideaModule
                  .getScopes()
                  .get("TEST")
                  .get("plus")
                  .addAll(
                      Arrays.asList(
                          project
                              .getConfigurations()
                              .getByName(testSourceSetConvention.getCompileOnlyConfigurationName()),
                          project
                              .getConfigurations()
                              .getByName(testSourceSetConvention.getAptConfigurationName())));
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
      ideaProject
          .getIpr()
          .withXml(
              // withXml(Action) overload was added in Gradle 2.14
              new MethodClosure(
                  new Action<XmlProvider>() {
                    @Override
                    public void execute(XmlProvider xmlProvider) {
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
                        // XXX: this assumes that all subprojects use the same name for their buildDir
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
}
