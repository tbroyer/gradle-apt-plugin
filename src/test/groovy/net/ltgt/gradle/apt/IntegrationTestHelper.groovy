package net.ltgt.gradle.apt

class IntegrationTestHelper {
  static final GRADLE_VERSIONS = System.getProperty("test.gradle-versions", "2.5,2.6,2.7,2.8,2.9,2.10,2.11,2.12,2.13,2.14.1,3.0,3.1,3.2.1,3.3,3.4.1,3.5.1,4.0.2,4.1,4.2.1,4.3")
      .tokenize(',');
}
