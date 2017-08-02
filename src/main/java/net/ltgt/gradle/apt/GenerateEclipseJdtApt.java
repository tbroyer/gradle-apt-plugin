package net.ltgt.gradle.apt;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import org.gradle.api.internal.PropertiesTransformer;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.plugins.ide.api.PropertiesGeneratorTask;
import org.gradle.plugins.ide.internal.generator.PropertiesPersistableConfigurationObject;

public class GenerateEclipseJdtApt extends PropertiesGeneratorTask<GenerateEclipseJdtApt.JdtApt> {
  private boolean aptEnabled;
  private File genSrcDir;
  private boolean reconcileEnabled;
  private Map<String, String> processorOptions;

  @Override
  protected void configure(JdtApt jdtApt) {
    jdtApt.aptEnabled = isAptEnabled();
    jdtApt.genSrcDir = getProject().relativePath(getGenSrcDir());
    jdtApt.reconcileEnabled = isReconcileEnabled();
    jdtApt.processorOptions.clear();
    if (getProcessorOptions() != null) {
      jdtApt.processorOptions.putAll(getProcessorOptions());
    }
  }

  @Override
  protected JdtApt create() {
    return new JdtApt(getTransformer());
  }

  @Input
  public boolean isAptEnabled() {
    return aptEnabled;
  }

  public void setAptEnabled(boolean aptEnabled) {
    this.aptEnabled = aptEnabled;
  }

  @Input
  public File getGenSrcDir() {
    return genSrcDir;
  }

  public void setGenSrcDir(File genSrcDir) {
    this.genSrcDir = genSrcDir;
  }

  @Input
  public boolean isReconcileEnabled() {
    return reconcileEnabled;
  }

  public void setReconcileEnabled(boolean reconcileEnabled) {
    this.reconcileEnabled = reconcileEnabled;
  }

  @Input @Optional
  public Map<String, String> getProcessorOptions() {
    return processorOptions;
  }

  public void setProcessorOptions(Map<String, String> processorOptions) {
    this.processorOptions = processorOptions;
  }

  static class JdtApt extends PropertiesPersistableConfigurationObject {

    boolean aptEnabled;
    String genSrcDir;
    boolean reconcileEnabled;
    Map<String, String> processorOptions = new LinkedHashMap<>();

    JdtApt(PropertiesTransformer transformer) {
      super(transformer);
    }

    @Override
    protected String getDefaultResourceName() {
      return "defaultJdtAptPrefs.properties";
    }

    @Override
    protected void load(Properties properties) {
    }

    @Override
    protected void store(Properties properties) {
      // This property is actually only for Eclipse versions prior to 3.3,
      // and is included here for backwards compatibility only.
      // Eclipse 3.3 uses "org.eclipse.jdt.core.compiler.processAnnotations"
      // in ".settings/org.eclipse.jdt.core.prefs", configured by AptEclipsePlugin.
      properties.setProperty("org.eclipse.jdt.apt.aptEnabled", Boolean.toString(aptEnabled));

      properties.setProperty("org.eclipse.jdt.apt.genSrcDir", genSrcDir);
      properties.setProperty("org.eclipse.jdt.apt.reconcileEnabled", Boolean.toString(reconcileEnabled));
      for (Map.Entry<String, String> option : processorOptions.entrySet()) {
        properties.setProperty("org.eclipse.jdt.apt.processorOptions/" + option.getKey(), option.getValue() == null
                ? "org.eclipse.jdt.apt.NULLVALUE" : option.getValue());
      }
    }
  }
}
