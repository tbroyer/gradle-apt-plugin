package net.ltgt.gradle.apt

import nebula.test.IntegrationSpec

class AptPluginIntegrationSpec extends IntegrationSpec {

  def 'simple java project'() {
    buildFile << """\
      buildscript {
        dependencies {
          classpath files('${System.getProperty('plugin')}')
        }
      }
      apply plugin: 'net.ltgt.apt'
      apply plugin: 'java'
    """.stripIndent()

    createFile('src/main/java/simple/HelloWorld.java') << """\
      package simple;

      public class HelloWorld {
        public String sayHello(String name) {
          return "Hello, " + name + "!";
        }
      }
    """.stripIndent()

    createFile('src/test/java/simple/HelloWorldTest.java') << """\
      package simple;

      public class HelloWorldTest {
        // Not a real unit-test
        public static void main(String[] args) {
          System.out.println(new HelloWorld().sayHello("World"));
        }
      }
    """.stripIndent()

    expect:
    runTasksSuccessfully('compileTestJava')
  }

  def 'simple java project with compile-only dependency'() {
    settingsFile << """\
      include 'annotations'
      include 'core'
    """.stripIndent()

    buildFile << """\
      subprojects {
        apply plugin: 'java'
      }
      project('core') {
        apply plugin: 'net.ltgt.apt'

        dependencies {
          compileOnly project(':annotations')
        }
      }
    """.stripIndent()

    createFile('annotations/src/main/java/annotations/MyAnnotation.java') << """\
      package annotations;

      import java.lang.annotation.Documented;

      public @interface MyAnnotation {
      }
    """.stripIndent()

    createFile('core/src/main/java/core/HelloWorld.java') << """\
      package core;

      import annotations.MyAnnotation;

      @MyAnnotation
      public class HelloWorld {
        public String sayHello(String name) {
          return "Hello, " + name + "!";
        }
      }
    """.stripIndent()

    expect:
    runTasksSuccessfully("javadoc")
  }
}
