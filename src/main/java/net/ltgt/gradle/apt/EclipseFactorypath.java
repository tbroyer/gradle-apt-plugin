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

import groovy.lang.Closure;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.plugins.ide.api.XmlFileContentMerger;
import org.gradle.util.ConfigureUtil;

public class EclipseFactorypath {
  private final XmlFileContentMerger file;

  public EclipseFactorypath(XmlFileContentMerger file) {
    this.file = file;
  }

  private Collection<Configuration> plusConfigurations = new ArrayList<>();
  private Collection<Configuration> minusConfigurations = new ArrayList<>();

  public Collection<Configuration> getPlusConfigurations() {
    return plusConfigurations;
  }

  public void setPlusConfigurations(Collection<Configuration> plusConfigurations) {
    this.plusConfigurations = Objects.requireNonNull(plusConfigurations);
  }

  public Collection<Configuration> getMinusConfigurations() {
    return minusConfigurations;
  }

  public void setMinusConfigurations(Collection<Configuration> minusConfigurations) {
    this.minusConfigurations = Objects.requireNonNull(minusConfigurations);
  }

  public XmlFileContentMerger getFile() {
    return this.file;
  }

  public void file(Closure<? super XmlFileContentMerger> closure) {
    ConfigureUtil.configure(closure, this.file);
  }

  public void file(Action<? super XmlFileContentMerger> action) {
    action.execute(this.file);
  }
}
