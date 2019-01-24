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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import javax.annotation.Nullable;
import org.gradle.api.internal.PropertiesTransformer;
import org.gradle.plugins.ide.internal.generator.PropertiesPersistableConfigurationObject;

public class JdtApt extends PropertiesPersistableConfigurationObject {

  private static final String GEN_SRC_DIR_KEY = "org.eclipse.jdt.apt.genSrcDir";
  private static final String GEN_TEST_SRC_DIR_KEY = "org.eclipse.jdt.apt.genTestSrcDir";
  private static final String RECONCILE_ENABLED_KEY = "org.eclipse.jdt.apt.reconcileEnabled";
  private static final String PROCESSOR_OPTIONS_KEY_PREFIX =
      "org.eclipse.jdt.apt.processorOptions/";
  private static final String PROCESSOR_OPTION_NULLVALUE = "org.eclipse.jdt.apt.NULLVALUE";

  private boolean aptEnabled;
  @Nullable private String genSrcDir;
  @Nullable private String genTestSrcDir;
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
  protected void load(Properties properties) {
    // Ignore aptEnabled when loading, see comment about storing it
    genSrcDir = properties.getProperty(GEN_SRC_DIR_KEY);
    genTestSrcDir = properties.getProperty(GEN_TEST_SRC_DIR_KEY);
    reconcileEnabled = Boolean.parseBoolean(RECONCILE_ENABLED_KEY);
    for (String name : properties.stringPropertyNames()) {
      if (name.startsWith(PROCESSOR_OPTIONS_KEY_PREFIX)) {
        final String value = properties.getProperty(name);
        processorOptions.put(
            name.substring(PROCESSOR_OPTIONS_KEY_PREFIX.length()),
            PROCESSOR_OPTION_NULLVALUE.equals(value) ? null : value);
      }
    }
  }

  @Override
  protected void store(Properties properties) {
    // This property is actually only for Eclipse versions prior to 3.3,
    // and is included here for backwards compatibility only.
    // Eclipse 3.3 uses "org.eclipse.jdt.core.compiler.processAnnotations"
    // in ".settings/org.eclipse.jdt.core.prefs", configured by AptEclipsePlugin.
    properties.setProperty("org.eclipse.jdt.apt.aptEnabled", Boolean.toString(isAptEnabled()));

    properties.setProperty(GEN_SRC_DIR_KEY, getGenSrcDir());
    properties.setProperty(GEN_TEST_SRC_DIR_KEY, getGenTestSrcDir());
    properties.setProperty(RECONCILE_ENABLED_KEY, Boolean.toString(isReconcileEnabled()));
    for (String name : properties.stringPropertyNames()) {
      if (name.startsWith(PROCESSOR_OPTIONS_KEY_PREFIX)
          && !getProcessorOptions()
              .containsKey(name.substring(PROCESSOR_OPTIONS_KEY_PREFIX.length()))) {
        properties.remove(name);
      }
    }
    for (Map.Entry<String, String> option : getProcessorOptions().entrySet()) {
      properties.setProperty(
          PROCESSOR_OPTIONS_KEY_PREFIX + option.getKey(),
          option.getValue() == null ? PROCESSOR_OPTION_NULLVALUE : option.getValue());
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

  @Nullable
  public String getGenTestSrcDir() {
    return genTestSrcDir;
  }

  public void setGenTestSrcDir(@Nullable String genTestSrcDir) {
    this.genTestSrcDir = genTestSrcDir;
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
