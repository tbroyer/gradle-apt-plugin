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
import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.gradle.internal.xml.XmlTransformer;
import org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject;

public class Factorypath extends XmlPersistableConfigurationObject {
  @Nullable private List<File> entries;

  Factorypath(XmlTransformer xmlTransformer) {
    super(xmlTransformer);
  }

  @Override
  protected String getDefaultResourceName() {
    return "defaultFactorypath.xml";
  }

  @Override
  protected void load(Node xml) {}

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

  @Nullable
  public List<File> getEntries() {
    return entries;
  }

  public void setEntries(List<File> entries) {
    this.entries = entries;
  }
}
