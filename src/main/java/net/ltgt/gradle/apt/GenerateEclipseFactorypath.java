package net.ltgt.gradle.apt;

import static net.ltgt.gradle.apt.CompatibilityUtils.getBeforeMerged;
import static net.ltgt.gradle.apt.CompatibilityUtils.getWhenMerged;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.Internal;
import org.gradle.plugins.ide.api.XmlFileContentMerger;
import org.gradle.plugins.ide.api.XmlGeneratorTask;

public class GenerateEclipseFactorypath extends XmlGeneratorTask<Factorypath> {
  private EclipseFactorypath factorypath =
      getInstantiator()
          .newInstance(EclipseFactorypath.class, new XmlFileContentMerger(getXmlTransformer()));

  public GenerateEclipseFactorypath() {
    this.getXmlTransformer().setIndentation("\t");
  }

  @Override
  protected void configure(Factorypath factorypath) {
    EclipseFactorypath factorypathModel = getFactorypath();
    getBeforeMerged(factorypathModel.getFile()).execute(factorypath);
    Set<File> entries = new LinkedHashSet<>();
    for (Configuration configuration : factorypathModel.getPlusConfigurations()) {
      entries.addAll(configuration.getFiles());
    }
    for (Configuration configuration : factorypathModel.getMinusConfigurations()) {
      entries.removeAll(configuration.getFiles());
    }
    factorypath.setEntries(new ArrayList<>(entries));
    getWhenMerged(factorypathModel.getFile()).execute(factorypath);
  }

  @Internal
  public EclipseFactorypath getFactorypath() {
    return factorypath;
  }

  public void setFactorypath(EclipseFactorypath factorypath) {
    this.factorypath = factorypath;
  }

  @Override
  protected Factorypath create() {
    return new Factorypath(getXmlTransformer());
  }
}
