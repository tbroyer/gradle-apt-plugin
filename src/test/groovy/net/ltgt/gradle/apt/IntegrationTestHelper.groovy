package net.ltgt.gradle.apt

import org.gradle.util.GradleVersion

class IntegrationTestHelper {
  static final TEST_GRADLE_VERSION = System.getProperty("test.gradle-version", GradleVersion.current().version)
}
