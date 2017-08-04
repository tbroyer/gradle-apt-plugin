package net.ltgt.gradle.apt;

import groovy.util.Node;
import org.gradle.internal.xml.XmlTransformer;
import org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Factorypath extends XmlPersistableConfigurationObject {
  private List<File> entries;

  Factorypath(XmlTransformer xmlTransformer) {
    super(xmlTransformer);
  }

    @Override
  protected String getDefaultResourceName() {
    return "defaultFactorypath.xml";
  }

  @Override
  protected void load(Node xml) {
  }

  @Override
  protected void store(Node xml) {
    for (File entry : entries) {
      Map<String, Object> attributes = new LinkedHashMap<>();
      attributes.put("kind", "EXTJAR");
      attributes.put("id", entry.getAbsolutePath());
      attributes.put("enabled", true);
      attributes.put("runInBatchMode", false);
      xml.appendNode("factorypathentry", attributes);
    }
  }

  public List<File> getEntries() {
    return entries;
  }

  public void setEntries(List<File> entries) {
    this.entries = entries;
  }
}
