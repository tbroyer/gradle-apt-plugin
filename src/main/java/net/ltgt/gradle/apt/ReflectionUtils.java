package net.ltgt.gradle.apt;

import java.lang.reflect.Method;

class ReflectionUtils {
  static Method getMethod(String className, String methodName, Class<?>... parameterTypes) {
    Class<?> klass = classForName(className);
    return klass == null ? null : getMethod(klass, methodName, parameterTypes);
  }

  static Method getMethod(Class<?> klass, String methodName, Class<?>... parameterTypes) {
    try {
      return klass.getMethod(methodName, parameterTypes);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  private static Class<?> classForName(String className) {
    try {
      return Class.forName(className);
    } catch (ClassNotFoundException e) {
      return null;
    }
  }
}
