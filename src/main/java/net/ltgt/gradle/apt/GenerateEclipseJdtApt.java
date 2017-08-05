package net.ltgt.gradle.apt;

import static net.ltgt.gradle.apt.CompatibilityUtils.getBeforeMerged;
import static net.ltgt.gradle.apt.CompatibilityUtils.getWhenMerged;

import java.util.Map;
import org.gradle.api.tasks.Internal;
import org.gradle.plugins.ide.api.PropertiesFileContentMerger;
import org.gradle.plugins.ide.api.PropertiesGeneratorTask;

public class GenerateEclipseJdtApt extends PropertiesGeneratorTask<JdtApt> {
  private EclipseJdtApt jdtApt =
      getInstantiator()
          .newInstance(
              EclipseJdtApt.class, getProject(), new PropertiesFileContentMerger(getTransformer()));

  @Override
  protected void configure(JdtApt jdtApt) {
    EclipseJdtApt jdtAptModel = getJdtApt();
    getBeforeMerged(jdtAptModel.getFile()).execute(jdtApt);
    jdtApt.setAptEnabled(jdtAptModel.isAptEnabled());
    jdtApt.setGenSrcDir(getProject().relativePath(jdtAptModel.getGenSrcDir()));
    jdtApt.setReconcileEnabled(jdtAptModel.isReconcileEnabled());
    jdtApt.getProcessorOptions().clear();
    if (jdtAptModel.getProcessorOptions() != null) {
      for (Map.Entry<String, ?> entry : jdtAptModel.getProcessorOptions().entrySet()) {
        jdtApt
            .getProcessorOptions()
            .put(entry.getKey(), entry.getValue() == null ? null : entry.getValue().toString());
      }
    }
    getWhenMerged(jdtAptModel.getFile()).execute(jdtApt);
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
