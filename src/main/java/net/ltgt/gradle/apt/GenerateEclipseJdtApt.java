package net.ltgt.gradle.apt;

import java.util.Map;

import org.gradle.api.tasks.Internal;
import org.gradle.plugins.ide.api.PropertiesFileContentMerger;
import org.gradle.plugins.ide.api.PropertiesGeneratorTask;

public class GenerateEclipseJdtApt extends PropertiesGeneratorTask<JdtApt> {
  private EclipseJdtApt jdtApt = getInstantiator().newInstance(EclipseJdtApt.class, getProject(), new PropertiesFileContentMerger(getTransformer()));

  @Override
  @SuppressWarnings("unchecked")
  protected void configure(JdtApt jdtApt) {
    EclipseJdtApt jdtAptModel = getJdtApt();
    jdtAptModel.getFile().getBeforeMerged().execute(jdtApt);
    jdtApt.setAptEnabled(jdtAptModel.isAptEnabled());
    jdtApt.setGenSrcDir(getProject().relativePath(jdtAptModel.getGenSrcDir()));
    jdtApt.setReconcileEnabled(jdtAptModel.isReconcileEnabled());
    jdtApt.getProcessorOptions().clear();
    if (jdtAptModel.getProcessorOptions() != null) {
      for (Map.Entry<String, ?> entry : jdtAptModel.getProcessorOptions().entrySet()) {
        jdtApt.getProcessorOptions().put(entry.getKey(), entry.getValue() == null ? null : entry.getValue().toString());
      }
    }
    jdtAptModel.getFile().getWhenMerged().execute(jdtApt);
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

}
