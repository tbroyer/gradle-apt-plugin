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

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.Internal;
import org.gradle.plugins.ide.api.XmlGeneratorTask;

public class GenerateEclipseFactorypath extends XmlGeneratorTask<Factorypath> {
  @SuppressWarnings("NullAway.Init") // will be initialized by setFactorypath right after creation
  private EclipseFactorypath factorypath;

  @SuppressWarnings(
      "NullAway") // factorypath will be initialized by setFactorypath right after creation
  public GenerateEclipseFactorypath() {
    this.getXmlTransformer().setIndentation("\t");
  }

  @SuppressWarnings("unchecked")
  @Override
  protected void configure(Factorypath factorypath) {
    EclipseFactorypath factorypathModel = getFactorypath();
    factorypathModel.getFile().getBeforeMerged().execute(factorypath);
    Set<File> entries = new LinkedHashSet<>();
    for (Configuration configuration : factorypathModel.getPlusConfigurations()) {
      entries.addAll(configuration.getFiles());
    }
    for (Configuration configuration : factorypathModel.getMinusConfigurations()) {
      entries.removeAll(configuration.getFiles());
    }
    factorypath.mergeEntries(entries);
    factorypathModel.getFile().getWhenMerged().execute(factorypath);
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
