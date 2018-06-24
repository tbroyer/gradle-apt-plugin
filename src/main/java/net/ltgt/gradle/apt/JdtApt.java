package net.ltgt.gradle.apt;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import javax.annotation.Nullable;
import org.gradle.api.internal.PropertiesTransformer;
import org.gradle.plugins.ide.internal.generator.PropertiesPersistableConfigurationObject;

public class JdtApt extends PropertiesPersistableConfigurationObject {

  private boolean aptEnabled;
  @Nullable private String genSrcDir;
  private boolean reconcileEnabled;
  private Map<String, String> processorOptions = new LinkedHashMap<>();

  JdtApt(PropertiesTransformer transformer) {
    super(transformer);
  }

  @Override
  protected String getDefaultResourceName() {
    return "defaultJdtAptPrefs.properties";
  }

  @Override
  protected void load(Properties properties) {}

  @Override
  protected void store(Properties properties) {
    // This property is actually only for Eclipse versions prior to 3.3,
    // and is included here for backwards compatibility only.
    // Eclipse 3.3 uses "org.eclipse.jdt.core.compiler.processAnnotations"
    // in ".settings/org.eclipse.jdt.core.prefs", configured by AptEclipsePlugin.
    properties.setProperty("org.eclipse.jdt.apt.aptEnabled", Boolean.toString(isAptEnabled()));

    properties.setProperty("org.eclipse.jdt.apt.genSrcDir", getGenSrcDir());
    properties.setProperty(
        "org.eclipse.jdt.apt.reconcileEnabled", Boolean.toString(isReconcileEnabled()));
    for (Map.Entry<String, String> option : getProcessorOptions().entrySet()) {
      properties.setProperty(
          "org.eclipse.jdt.apt.processorOptions/" + option.getKey(),
          option.getValue() == null ? "org.eclipse.jdt.apt.NULLVALUE" : option.getValue());
    }
  }

  public boolean isAptEnabled() {
    return aptEnabled;
  }

  public void setAptEnabled(boolean aptEnabled) {
    this.aptEnabled = aptEnabled;
  }

  @Nullable
  public String getGenSrcDir() {
    return genSrcDir;
  }

  public void setGenSrcDir(@Nullable String genSrcDir) {
    this.genSrcDir = genSrcDir;
  }

  public boolean isReconcileEnabled() {
    return reconcileEnabled;
  }

  public void setReconcileEnabled(boolean reconcileEnabled) {
    this.reconcileEnabled = reconcileEnabled;
  }

  public Map<String, String> getProcessorOptions() {
    return processorOptions;
  }
}
