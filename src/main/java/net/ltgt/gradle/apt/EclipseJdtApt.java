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
import javax.annotation.Nullable;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.plugins.ide.api.PropertiesFileContentMerger;
import org.gradle.util.ConfigureUtil;

public class EclipseJdtApt {
  private final Project project;
  private final PropertiesFileContentMerger file;

  public EclipseJdtApt(Project project, PropertiesFileContentMerger file) {
    this.project = project;
    this.file = file;
  }

  private boolean aptEnabled = true;

  public boolean isAptEnabled() {
    return aptEnabled;
  }

  public void setAptEnabled(boolean aptEnabled) {
    this.aptEnabled = aptEnabled;
  }

  private boolean reconcileEnabled = true;

  public boolean isReconcileEnabled() {
    return reconcileEnabled;
  }

  public void setReconcileEnabled(boolean reconcileEnabled) {
    this.reconcileEnabled = reconcileEnabled;
  }

  private Object genSrcDir = ".apt_generated";

  public File getGenSrcDir() {
    return project.file(genSrcDir);
  }

  public void setGenSrcDir(Object genSrcDir) {
    this.genSrcDir = Objects.requireNonNull(genSrcDir);
  }

  @Nullable private Map<String, ?> processorOptions = new LinkedHashMap<>();

  @Nullable
  public Map<String, ?> getProcessorOptions() {
    return processorOptions;
  }

  public void setProcessorOptions(@Nullable Map<String, ?> processorOptions) {
    this.processorOptions = processorOptions;
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
