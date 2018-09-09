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
package net.ltgt.gradle.apt

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.TextUtil
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class KotlinDslIntegrationSpec extends Specification {
  @Rule TemporaryFolder testProjectDir = new TemporaryFolder()
  File settingsFile
  File buildFile

  def setup() {
    settingsFile = testProjectDir.newFile('settings.gradle.kts') << """\
      pluginManagement {
        repositories {
          maven { url = uri("${TextUtil.normaliseFileSeparators(new File('build/repository').absolutePath)}") }
        }
        resolutionStrategy {
          eachPlugin {
            if (requested.id.id.startsWith("net.ltgt.apt")) {
              useVersion("${System.getProperty('plugin.version')}")
            }
          }
        }
      }
    """.stripIndent()
    buildFile = testProjectDir.newFile('build.gradle.kts')
  }

  def "simple java project using kotlin-dsl"() {
    given:
    buildFile << """\
      import net.ltgt.gradle.apt.*

      plugins {
        java
        id("net.ltgt.apt")
      }

      val compileJava by tasks.getting(JavaCompile::class) {
        aptOptions.run {
          processors = listOf("processor")
          processorArgs = mapOf("foo" to "bar", "baz" to 5)
        }
      }
      val compileTestJava by tasks.getting(JavaCompile::class) {
        aptOptions.run {
          annotationProcessing = false
        }
      }
    """.stripIndent()

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.root)
        .withArguments(':javadoc')
        .build()

    then:
    println(result.output)
    result.task(":compileJava").outcome == TaskOutcome.NO_SOURCE
  }

  def "simple groovy project using kotlin-dsl"() {
    given:
    buildFile << """\
      import net.ltgt.gradle.apt.*

      plugins {
        groovy
        id("net.ltgt.apt")
      }

      val compileGroovy by tasks.getting(GroovyCompile::class) {
        aptOptions.run {
          processors = listOf("processor")
          processorArgs = mapOf("foo" to "bar", "baz" to 5)
        }
      }
      val compileTestGroovy by tasks.getting(GroovyCompile::class) {
        aptOptions.run {
          annotationProcessing = false
        }
      }
    """.stripIndent()

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.root)
        .withArguments(':groovydoc')
        .build()

    then:
    println(result.output)
    result.task(":compileGroovy").outcome == TaskOutcome.NO_SOURCE
  }

  def "simple java project using kotlin-dsl, configuring Eclipse"() {
    given:
    buildFile << """\
      import net.ltgt.gradle.apt.*

      plugins {
        java
        id("net.ltgt.apt-eclipse")
      }

      eclipse {
        factorypath {
          minusConfigurations.add(configurations["apt"])
        }
        jdt {
          apt {
            isAptEnabled = false
          }
        }
      }
    """.stripIndent()

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.root)
        .withArguments(':eclipse')
        .build()

    then:
    println(result.output)
    result.task(":eclipse").outcome == TaskOutcome.SUCCESS
    result.task(":eclipseJdtApt").outcome == TaskOutcome.SUCCESS
    result.task(":eclipseFactorypath").outcome == TaskOutcome.SUCCESS
  }

  def "simple java project using kotlin-dsl, configuring IDEA"() {
    given:
    buildFile << """\
      import net.ltgt.gradle.apt.*

      plugins {
        java
        id("net.ltgt.apt-idea")
      }

      idea {
        project {
          configureAnnotationProcessing = false
        }
        module {
          apt {
            addAptDependencies = false
          }
        }
      }
    """.stripIndent()

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.root)
        .withArguments(':idea')
        .build()

    then:
    println(result.output)
    result.task(":idea").outcome == TaskOutcome.SUCCESS
  }
}
