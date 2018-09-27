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

import groovy.util.Node;
import groovy.util.NodeList;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.gradle.internal.xml.XmlTransformer;
import org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject;

public class Factorypath extends XmlPersistableConfigurationObject {
  private List<File> entries = new ArrayList<>();

  Factorypath(XmlTransformer xmlTransformer) {
    super(xmlTransformer);
  }

  @Override
  protected String getDefaultResourceName() {
    return "defaultFactorypath.xml";
  }

  @Override
  protected void load(Node xml) {
    for (Object e : (NodeList) xml.get("factorypathentry")) {
      Node entryNode = (Node) e;
      if (isFileEntry(entryNode)) {
        this.entries.add(new File((String) entryNode.attribute("id")));
      }
    }
  }

  void mergeEntries(Collection<File> newEntries) {
    Set<File> updatedEntries = new LinkedHashSet<>();
    for (File f : entries) {
      updatedEntries.add(f.getAbsoluteFile());
    }
    for (File f : newEntries) {
      updatedEntries.add(f.getAbsoluteFile());
    }
    entries = new ArrayList<>(updatedEntries);
  }

  @Override
  protected void store(Node xml) {
    for (Object e : (NodeList) xml.get("factorypathentry")) {
      Node entryNode = (Node) e;
      if (isFileEntry(entryNode)) {
        xml.remove(entryNode);
      }
    }

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

  private static boolean isFileEntry(Node entryNode) {
    return "EXTJAR".equals(entryNode.attribute("kind"))
        && Boolean.valueOf(String.valueOf(entryNode.attribute("enabled")));
  }
}
