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

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

class DeprecationLogger {
  private static final Logger logger = Logging.getLogger(DeprecationLogger.class);

  static void nagUserWith(Project project, String message) {
    nagUserWith(project.getPath(), message);
  }

  static void nagUserWith(Task task, String message) {
    nagUserWith(task.getPath(), message);
  }

  private static void nagUserWith(String path, String message) {
    if (path == null || path.isEmpty() || path.equals(Project.PATH_SEPARATOR)) {
      logger.warn(message);
    } else {
      logger.warn(path + ": " + message);
    }
  }

  private DeprecationLogger() {
    // non-instantiable
  }
}
