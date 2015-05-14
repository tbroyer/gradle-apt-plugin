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
}
