package net.ltgt.gradle.apt;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskInputs;
import org.gradle.api.tasks.TaskOutputs;
import org.gradle.plugins.ide.api.FileContentMerger;

/**
 * Some methods have changed signature across Gradle versions, this class uses reflection to
 * mitigate this and ensure compatibility.
 */
class CompatibilityUtils {

  private static final Method taskGetInputsMethod;
  private static final Method taskGetOutputsMethod;
  private static final Method taskInputsFilesMethod;
  private static final Method taskOutputsDirMethod;
  private static final Method taskInputFilePropertyBuilderWithPropertyNameMethod;
  private static final Method taskOutputFilePropertyBuilderWithPropertyNameMethod;
  private static final Method taskOutputFilePropertyBuilderOptionalMethod;
  private static final Method fileContentMergerGetBeforeMergedMethod;
  private static final Method fileContentMergerGetWhenMergedMethod;

  static {
    taskGetInputsMethod = getMethod(Task.class, "getInputs");
    taskGetOutputsMethod = getMethod(Task.class, "getOutputs");

    taskInputsFilesMethod = getMethod(TaskInputs.class, "files", Object[].class);
    taskOutputsDirMethod = getMethod(TaskOutputs.class, "dir", Object.class);

    Class<?> taskInputFilePropertyBuilderClass =
        classForName("org.gradle.api.tasks.TaskInputFilePropertyBuilder");
    taskInputFilePropertyBuilderWithPropertyNameMethod =
        taskInputFilePropertyBuilderClass == null
            ? null
            : getMethod(taskInputFilePropertyBuilderClass, "withPropertyName", String.class);

    Class<?> taskOutputFilePropertyBuilderClass =
        classForName("org.gradle.api.tasks.TaskOutputFilePropertyBuilder");
    taskOutputFilePropertyBuilderWithPropertyNameMethod =
        taskOutputFilePropertyBuilderClass == null
            ? null
            : getMethod(taskOutputFilePropertyBuilderClass, "withPropertyName", String.class);
    taskOutputFilePropertyBuilderOptionalMethod =
        taskOutputFilePropertyBuilderClass == null
            ? null
            : getMethod(taskOutputFilePropertyBuilderClass, "optional");

    fileContentMergerGetBeforeMergedMethod = getMethod(FileContentMerger.class, "getBeforeMerged");
    fileContentMergerGetWhenMergedMethod = getMethod(FileContentMerger.class, "getWhenMerged");
  }

  private static Class<?> classForName(String className) {
    try {
      return Class.forName(className);
    } catch (ClassNotFoundException e) {
      return null;
    }
  }

  private static Method getMethod(Class<?> klass, String methodName, Class<?>... parameterTypes) {
    try {
      return klass.getMethod(methodName, parameterTypes);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  /** {@link Task#getInputs()} changed return type in Gradle 3.0. */
  static TaskInputs getInputs(Task task) {
    try {
      return (TaskInputs) taskGetInputsMethod.invoke(task);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  /** {@link Task#getOutputs()} changed return type in Gradle 3.0. */
  static TaskOutputs getOutputs(Task task) {
    try {
      return (TaskOutputs) taskGetOutputsMethod.invoke(task);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  /** {@link TaskInputs#files(Object...)} changed return type in Gradle 3.0. */
  static TaskInputs files(TaskInputs inputs, Object... args) {
    try {
      return (TaskInputs) taskInputsFilesMethod.invoke(inputs, new Object[] {args});
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  /** {@link TaskOutputs#dir(Object)} changed return type in Gradle 3.0. */
  static TaskOutputs dir(TaskOutputs outputs, Object dir) {
    try {
      return (TaskOutputs) taskOutputsDirMethod.invoke(outputs, dir);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  /** {@link org.gradle.api.tasks.TaskInputFilePropertyBuilder} was introduced in Gradle 3.0. */
  static void withPropertyName(TaskInputs inputs, String propertyName) {
    if (taskInputFilePropertyBuilderWithPropertyNameMethod == null) {
      return;
    }
    try {
      taskInputFilePropertyBuilderWithPropertyNameMethod.invoke(inputs, propertyName);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  /** {@link org.gradle.api.tasks.TaskOutputFilePropertyBuilder} was introduced in Gradle 3.0. */
  static void withPropertyName(TaskOutputs outputs, String propertyName) {
    if (taskOutputFilePropertyBuilderWithPropertyNameMethod == null) {
      return;
    }
    try {
      taskOutputFilePropertyBuilderWithPropertyNameMethod.invoke(outputs, propertyName);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  /** {@link org.gradle.api.tasks.TaskOutputFilePropertyBuilder} was introduced in Gradle 3.0. */
  static void optional(TaskOutputs outputs) {
    if (taskOutputFilePropertyBuilderOptionalMethod == null) {
      return;
    }
    try {
      taskOutputFilePropertyBuilderOptionalMethod.invoke(outputs);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  /** {@link FileContentMerger#getBeforeMerged()} changed return type in Gradle 3.5. */
  @SuppressWarnings("unchecked")
  static Action<Object> getBeforeMerged(FileContentMerger merger) {
    try {
      return (Action<Object>) fileContentMergerGetBeforeMergedMethod.invoke(merger);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  /** {@link FileContentMerger#getWhenMerged()} changed return type in Gradle 3.5. */
  @SuppressWarnings("unchecked")
  static Action<Object> getWhenMerged(FileContentMerger merger) {
    try {
      return (Action<Object>) fileContentMergerGetWhenMergedMethod.invoke(merger);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }
}
