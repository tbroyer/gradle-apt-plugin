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
