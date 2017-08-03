package net.ltgt.gradle.apt;

import org.gradle.api.Project;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

public class EclipseJdtApt {
  private final Project project;

  public EclipseJdtApt(Project project) {
    this.project = project;
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
    if (genSrcDir == null) {
      return null;
    }
    return project.file(genSrcDir);
  }

  public void setGenSrcDir(Object genSrcDir) {
    this.genSrcDir = genSrcDir;
  }

  private Map<String, ?> processorOptions = new LinkedHashMap<>();

  public Map<String, ?> getProcessorOptions() {
    return processorOptions;
  }

  public void setProcessorOptions(Map<String, ?> processorOptions) {
    this.processorOptions = processorOptions;
  }
}
