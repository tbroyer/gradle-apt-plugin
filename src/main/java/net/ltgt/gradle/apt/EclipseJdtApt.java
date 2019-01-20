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

import groovy.lang.Closure;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.plugins.ide.api.PropertiesFileContentMerger;
import org.gradle.util.ConfigureUtil;

public class EclipseJdtApt {
  private final Project project;
  private final PropertiesFileContentMerger file;

  @SuppressWarnings("unchecked")
  public EclipseJdtApt(Project project, PropertiesFileContentMerger file) {
    this.project = project;
    this.file = file;
    this.aptEnabled = project.getObjects().property(Boolean.class);
    aptEnabled.set(true);
    this.reconcileEnabled = project.getObjects().property(Boolean.class);
    reconcileEnabled.set(true);
    this.genSrcDir = project.getObjects().property(File.class);
    genSrcDir.set(project.file(".apt_generated"));
    this.genTestSrcDir = project.getObjects().property(File.class);
    genTestSrcDir.set(project.file(".apt_generated_tests"));
    this.processorOptionsSupplier = LinkedHashMap::new;
  }

  private final Property<Boolean> aptEnabled;

  public boolean isAptEnabled() {
    return aptEnabled.get();
  }

  public void setAptEnabled(boolean aptEnabled) {
    this.aptEnabled.set(aptEnabled);
  }

  public void setAptEnabled(Provider<Boolean> aptEnabled) {
    this.aptEnabled.set(aptEnabled);
  }

  private final Property<Boolean> reconcileEnabled;

  public boolean isReconcileEnabled() {
    return reconcileEnabled.get();
  }

  public void setReconcileEnabled(boolean reconcileEnabled) {
    this.reconcileEnabled.set(reconcileEnabled);
  }

  public void setReconcileEnabled(Provider<Boolean> reconcileEnabled) {
    this.reconcileEnabled.set(reconcileEnabled);
  }

  private final Property<File> genSrcDir;

  public File getGenSrcDir() {
    return project.file(genSrcDir);
  }

  public void setGenSrcDir(File genSrcDir) {
    this.genSrcDir.set(Objects.requireNonNull(genSrcDir));
  }

  public void setGenSrcDir(Object genSrcDir) {
    Objects.requireNonNull(genSrcDir);
    this.genSrcDir.set(project.provider(() -> project.file(genSrcDir)));
  }

  private final Property<File> genTestSrcDir;

  public File getGenTestSrcDir() {
    return project.file(genTestSrcDir);
  }

  public void setGenTestSrcDir(File genTestSrcDir) {
    this.genTestSrcDir.set(Objects.requireNonNull(genTestSrcDir));
  }

  public void setGenTestSrcDir(Object genTestSrcDir) {
    Objects.requireNonNull(genTestSrcDir);
    this.genTestSrcDir.set(project.provider(() -> project.file(genTestSrcDir)));
  }

  // XXX: this is actually either a Property<Map> or a MapProperty depending on Gradle version
  private @Nullable Supplier<Map<String, ?>> processorOptionsSupplier;
  private @Nullable Map<String, ?> processorOptions;

  @Nullable
  public Map<String, ?> getProcessorOptions() {
    if (processorOptions == null && processorOptionsSupplier != null) {
      processorOptions = processorOptionsSupplier.get();
      processorOptionsSupplier = null;
      if (processorOptions != null) {
        processorOptions = new LinkedHashMap<>(processorOptions);
      }
    }
    return processorOptions;
  }

  public void setProcessorOptions(@Nullable Map<String, ?> processorOptions) {
    setProcessorOptions(() -> processorOptions);
  }

  void setProcessorOptions(Supplier<Map<String, ?>> processorOptions) {
    this.processorOptionsSupplier = processorOptions;
    this.processorOptions = null;
  }

  public PropertiesFileContentMerger getFile() {
    return this.file;
  }

  public void file(Closure<? super PropertiesFileContentMerger> closure) {
    ConfigureUtil.configure(closure, this.file);
  }

  public void file(Action<? super PropertiesFileContentMerger> action) {
    action.execute(this.file);
  }
}
