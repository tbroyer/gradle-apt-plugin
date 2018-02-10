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
  private static final Method taskInputsPropertyMethod;
  private static final Method taskOutputsDirMethod;
  private static final Method taskInputPropertyBuilderOptionalMethod;
  private static final Method fileContentMergerGetBeforeMergedMethod;
  private static final Method fileContentMergerGetWhenMergedMethod;

  static {
    taskGetInputsMethod = getMethod(Task.class, "getInputs");
    taskGetOutputsMethod = getMethod(Task.class, "getOutputs");

    taskInputsFilesMethod = getMethod(TaskInputs.class, "files", Object[].class);
    taskInputsPropertyMethod = getMethod(TaskInputs.class, "property", String.class, Object.class);
    taskOutputsDirMethod = getMethod(TaskOutputs.class, "dir", Object.class);

    Class<?> taskInputPropertyBuilderClass =
        classForName("org.gradle.api.tasks.TaskInputPropertyBuilder");
    taskInputPropertyBuilderOptionalMethod =
        taskInputPropertyBuilderClass == null
            ? null
            : getMethod(taskInputPropertyBuilderClass, "optional", boolean.class);

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

  private static Method findMethod(Class<?> klass, String methodName, Class<?>... parameterTypes) {
    try {
      return klass.getMethod(methodName, parameterTypes);
    } catch (NoSuchMethodException e) {
      return null;
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

  /** {@link TaskInputs#property(String, Object)} changed return type in Gradle 4.3. */
  static TaskInputs property(TaskInputs inputs, String name, Object value) {
    try {
      return (TaskInputs) taskInputsPropertyMethod.invoke(inputs, new Object[] {name, value});
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  /** {@link org.gradle.api.tasks.TaskInputPropertyBuilder} was introduced in Gradle 4.3. */
  static TaskInputs optionalProperty(TaskInputs inputs, String name, Object value) {
    inputs = property(inputs, name, value);
    if (taskInputPropertyBuilderOptionalMethod == null) {
      return inputs;
    }
    try {
      return (TaskInputs)
          taskInputPropertyBuilderOptionalMethod.invoke(inputs, new Object[] {true});
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  /** {@link TaskOutputs#dir(Object)} changed return type in Gradle 3.0. */
  static void dir(TaskOutputs outputs, Object dir) {
    try {
      taskOutputsDirMethod.invoke(outputs, dir);
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
