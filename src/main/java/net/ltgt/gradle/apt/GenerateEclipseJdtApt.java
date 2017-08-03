package net.ltgt.gradle.apt;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import org.gradle.api.internal.PropertiesTransformer;
import org.gradle.api.tasks.Internal;
import org.gradle.plugins.ide.api.PropertiesGeneratorTask;
import org.gradle.plugins.ide.internal.generator.PropertiesPersistableConfigurationObject;

public class GenerateEclipseJdtApt extends PropertiesGeneratorTask<GenerateEclipseJdtApt.JdtApt> {
  private EclipseJdtApt jdtApt;

  @Override
  protected void configure(JdtApt jdtApt) {
    jdtApt.aptEnabled = this.jdtApt.isAptEnabled();
    jdtApt.genSrcDir = getProject().relativePath(this.jdtApt.getGenSrcDir());
    jdtApt.reconcileEnabled = this.jdtApt.isReconcileEnabled();
    jdtApt.processorOptions.clear();
    if (this.jdtApt.getProcessorOptions() != null) {
      for (Map.Entry<String, ?> entry : this.jdtApt.getProcessorOptions().entrySet()) {
        jdtApt.processorOptions.put(entry.getKey(), entry.getValue() == null ? null : entry.getValue().toString());
      }
    }
  }

  @Override
  protected JdtApt create() {
    return new JdtApt(getTransformer());
  }

  @Internal
  public EclipseJdtApt getJdtApt() {
    return this.jdtApt;
  }

  public void setJdtApt(EclipseJdtApt jdtApt) {
    this.jdtApt = jdtApt;
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
