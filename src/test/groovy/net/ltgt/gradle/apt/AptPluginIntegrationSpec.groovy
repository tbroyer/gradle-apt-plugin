package net.ltgt.gradle.apt

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Unroll

class AptPluginIntegrationSpec extends Specification {
  @Rule TemporaryFolder testProjectDir = new TemporaryFolder()
  File buildFile

  def setup() {
    buildFile = testProjectDir.newFile('build.gradle')
    buildFile << """\
      buildscript {
        dependencies {
          classpath files(\$/${System.getProperty('plugin')}/\$)
        }
      }
    """.stripIndent()
  }

  @Unroll
  def "simple non-java project, with Gradle #gradleVersion"() {
    given:
    buildFile << """\
      apply plugin: 'net.ltgt.apt'

      task javaCompilationTask(type: JavaCompile) {
        source 'src/'
        include '**/*.java'
        classpath = project.files()
        destinationDir = project.file('build/classes')
        sourceCompatibility = org.gradle.api.JavaVersion.current()
        targetCompatibility = org.gradle.api.JavaVersion.current()
        dependencyCacheDir = project.file('build/dependency-cache')
      }
    """.stripIndent()

    def f = new File(testProjectDir.newFolder('src', 'simple'), 'HelloWorld.java')
    f.createNewFile()
    f << """\
      package simple;

      public class HelloWorld {
        public String sayHello(String name) {
          return "Hello, " + name + "!";
        }
      }
    """.stripIndent()

    when:
    def result = GradleRunner.create()
            .withGradleVersion(gradleVersion)
            .withProjectDir(testProjectDir.root)
            .withArguments('javaCompilationTask')
            .build()

    then:
    result.task(':javaCompilationTask').outcome == TaskOutcome.SUCCESS
    new File(testProjectDir.root, 'build/classes/simple/HelloWorld.class').exists()

    where:
    gradleVersion << IntegrationTestHelper.GRADLE_VERSIONS
  }

  @Unroll
  def "simple java project, with Gradle #gradleVersion"() {
    given:
    buildFile << """\
      apply plugin: 'net.ltgt.apt'
      apply plugin: 'java'
    """.stripIndent()

    def f = new File(testProjectDir.newFolder('src', 'main', 'java', 'simple'), 'HelloWorld.java')
    f.createNewFile()
    f << """\
      package simple;

      public class HelloWorld {
        public String sayHello(String name) {
          return "Hello, " + name + "!";
        }
      }
    """.stripIndent()

    f = new File(testProjectDir.newFolder('src', 'test', 'java', 'simple'), 'HelloWorldTest.java')
    f.createNewFile()
    f << """\
      package simple;

      public class HelloWorldTest {
        // Not a real unit-test
        public static void main(String[] args) {
          System.out.println(new HelloWorld().sayHello("World"));
        }
      }
    """.stripIndent()

    when:
    def result = GradleRunner.create()
        .withGradleVersion(gradleVersion)
        .withProjectDir(testProjectDir.root)
        .withArguments('compileTestJava')
        .build()

    then:
    result.task(':compileJava').outcome == TaskOutcome.SUCCESS
    result.task(':compileTestJava').outcome == TaskOutcome.SUCCESS

    where:
    gradleVersion << IntegrationTestHelper.GRADLE_VERSIONS
  }

  @Unroll
  def "simple java project with compile-only dependency, with Gradle #gradleVersion"() {
    given:
    def settingsFile = testProjectDir.newFile('settings.gradle')
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

    def f = new File(testProjectDir.newFolder('annotations', 'src', 'main', 'java', 'annotations'), 'MyAnnotation.java')
    f.createNewFile()
    f << """\
      package annotations;

      public @interface MyAnnotation {
      }
    """.stripIndent()

    f = new File(testProjectDir.newFolder('core', 'src', 'main', 'java', 'core'), 'HelloWorld.java')
    f.createNewFile()
    f << """\
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

    when:
    def result = GradleRunner.create()
        .withGradleVersion(gradleVersion)
        .withProjectDir(testProjectDir.root)
        .withArguments(':core:javadoc')
        .build()

    then:
    result.task(':annotations:compileJava').outcome == TaskOutcome.SUCCESS
    result.task(':core:compileJava').outcome == TaskOutcome.SUCCESS
    result.task(':core:javadoc').outcome == TaskOutcome.SUCCESS

    where:
    gradleVersion << IntegrationTestHelper.GRADLE_VERSIONS
  }

  @Unroll
  def "simple java project with annotation processor, with Gradle #gradleVersion"() {
    given:
    def settingsFile = testProjectDir.newFile('settings.gradle')
    settingsFile << """\
      include 'annotations'
      include 'processor'
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
          apt project(':processor')
        }
      }
    """.stripIndent()

    def f = new File(testProjectDir.newFolder('annotations', 'src', 'main', 'java', 'annotations'), 'MyAnnotation.java')
    f.createNewFile()
    f << """\
      package annotations;

      public @interface MyAnnotation {
      }
    """.stripIndent()

    f = new File(testProjectDir.newFolder('processor', 'src', 'main', 'java', 'processor'), 'MyAnnotationProcessor.java')
    f.createNewFile()
    f << """\
      package processor;

      import javax.annotation.processing.AbstractProcessor;
      import javax.annotation.processing.RoundEnvironment;
      import javax.annotation.processing.SupportedAnnotationTypes;
      import javax.lang.model.SourceVersion;
      import javax.lang.model.element.TypeElement;
      import javax.lang.model.util.ElementFilter;
      import javax.tools.Diagnostic;
      import javax.tools.FileObject;
      import javax.tools.StandardLocation;
      import java.io.IOException;
      import java.io.PrintWriter;
      import java.util.Set;
      import java.util.TreeSet;

      @SupportedAnnotationTypes(MyAnnotationProcessor.MY_ANNOTATION)
      public class MyAnnotationProcessor extends AbstractProcessor {

        static final String MY_ANNOTATION = "annotations.MyAnnotation";

        @Override
        public SourceVersion getSupportedSourceVersion() {
          return SourceVersion.latest();
        }

        private Set<String> annotatedElements = new TreeSet<>();

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
          for (TypeElement annotatedElement : ElementFilter.typesIn(roundEnv.getElementsAnnotatedWith(
              processingEnv.getElementUtils().getTypeElement(MY_ANNOTATION)))) {
            annotatedElements.add(annotatedElement.getQualifiedName().toString());
          }
          if (roundEnv.processingOver()) {
            try {
              FileObject f = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", "annotated-elements");
              try (PrintWriter w = new PrintWriter(f.openWriter())) {
                for (String annotatedElement : annotatedElements) {
                  w.println(annotatedElement);
                }
              }
            } catch (IOException e) {
              processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage());
            }
          }
          return false;
        }
      }
    """.stripIndent()
    f = new File(testProjectDir.newFolder('processor', 'src', 'main', 'resources', 'META-INF', 'services'), 'javax.annotation.processing.Processor')
    f.createNewFile()
    f << """\
      processor.MyAnnotationProcessor
    """.stripIndent()

    f = new File(testProjectDir.newFolder('core', 'src', 'main', 'java', 'core'), 'HelloWorld.java')
    f.createNewFile()
    f << """\
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

    when:
    def result = GradleRunner.create()
            .withGradleVersion(gradleVersion)
            .withProjectDir(testProjectDir.root)
            .withArguments(':core:javadoc')
            .build()

    then:
    result.task(':annotations:compileJava').outcome == TaskOutcome.SUCCESS
    result.task(':processor:compileJava').outcome == TaskOutcome.SUCCESS
    result.task(':core:compileJava').outcome == TaskOutcome.SUCCESS
    result.task(':core:javadoc').outcome == TaskOutcome.SUCCESS
    new File(testProjectDir.root, 'core/build/classes/main/annotated-elements').text.trim() == "core.HelloWorld"

    where:
    gradleVersion << IntegrationTestHelper.GRADLE_VERSIONS
  }

  @Unroll
  def "simple non-groovy project, with Gradle #gradleVersion"() {
    given:
    buildFile << """\
      apply plugin: 'net.ltgt.apt'

      configurations {
        groovy
      }
      dependencies {
        groovy localGroovy()
      }

      task groovyCompilationTask(type: GroovyCompile) {
        source 'src/'
        include '**/*.groovy'
        classpath = configurations.groovy
        destinationDir = project.file('build/classes')
        sourceCompatibility = org.gradle.api.JavaVersion.current()
        targetCompatibility = org.gradle.api.JavaVersion.current()
        groovyClasspath = configurations.groovy
      }
    """.stripIndent()

    def f = new File(testProjectDir.newFolder('src', 'simple'), 'HelloWorld.groovy')
    f.createNewFile()
    f << """\
      package simple;

      class HelloWorld {
        String sayHello(String name) {
          "Hello, \${name}!";
        }
      }
    """.stripIndent()

    when:
    def result = GradleRunner.create()
            .withGradleVersion(gradleVersion)
            .withProjectDir(testProjectDir.root)
            .withArguments('groovyCompilationTask')
            .build()

    then:
    result.task(':groovyCompilationTask').outcome == TaskOutcome.SUCCESS
    new File(testProjectDir.root, 'build/classes/simple/HelloWorld.class').exists()

    where:
    gradleVersion << IntegrationTestHelper.GRADLE_VERSIONS
  }

  @Unroll
  def "simple groovy project, with Gradle #gradleVersion"() {
    given:
    buildFile << """\
      apply plugin: 'net.ltgt.apt'
      apply plugin: 'groovy'

      dependencies {
        compile localGroovy()
      }
    """.stripIndent()

    def f = new File(testProjectDir.newFolder('src', 'main', 'groovy', 'simple'), 'HelloWorld.groovy')
    f.createNewFile()
    f << """\
      package simple;

      class HelloWorld {
        String sayHello(String name) {
          "Hello, \${name}!";
        }
      }
    """.stripIndent()

    f = new File(testProjectDir.newFolder('src', 'test', 'groovy', 'simple'), 'HelloWorldTest.groovy')
    f.createNewFile()
    f << """\
      package simple;

      class HelloWorldTest {
        // Not a real unit-test
        public static void main(String[] args) {
          System.out.println(new HelloWorld().sayHello("World"));
        }
      }
    """.stripIndent()

    when:
    def result = GradleRunner.create()
        .withGradleVersion(gradleVersion)
        .withProjectDir(testProjectDir.root)
        .withArguments('compileTestGroovy')
        .build()

    then:
    result.task(':compileGroovy').outcome == TaskOutcome.SUCCESS
    result.task(':compileTestGroovy').outcome == TaskOutcome.SUCCESS

    where:
    gradleVersion << IntegrationTestHelper.GRADLE_VERSIONS
  }

  @Unroll
  def "simple groovy project with compile-only dependency, with Gradle #gradleVersion"() {
    given:
    def settingsFile = testProjectDir.newFile('settings.gradle')
    settingsFile << """\
      include 'annotations'
      include 'core'
    """.stripIndent()

    buildFile << """\
      project('annotations') {
        apply plugin: 'java'
      }
      project('core') {
        apply plugin: 'groovy'
        apply plugin: 'net.ltgt.apt'

        dependencies {
          compile localGroovy()
          compileOnly project(':annotations')
        }
      }
    """.stripIndent()

    def f = new File(testProjectDir.newFolder('annotations', 'src', 'main', 'java', 'annotations'), 'MyAnnotation.java')
    f.createNewFile()
    f << """\
      package annotations;

      public @interface MyAnnotation {
      }
    """.stripIndent()

    f = new File(testProjectDir.newFolder('core', 'src', 'main', 'groovy', 'core'), 'HelloWorld.groovy')
    f.createNewFile()
    f << """\
      package core;

      import annotations.MyAnnotation;

      @MyAnnotation
      class HelloWorld {
        String sayHello(String name) {
          "Hello, \${name}!";
        }
      }
    """.stripIndent()

    expect:

    when:
    def result = GradleRunner.create()
        .withGradleVersion(gradleVersion)
        .withProjectDir(testProjectDir.root)
        .withArguments(':core:groovydoc')
        .build()

    then:
    result.task(':annotations:compileJava').outcome == TaskOutcome.SUCCESS
    result.task(':core:compileGroovy').outcome == TaskOutcome.SUCCESS
    result.task(':core:groovydoc').outcome == TaskOutcome.SUCCESS

    where:
    gradleVersion << IntegrationTestHelper.GRADLE_VERSIONS
  }

  @Unroll
  def "simple groovy project with annotation processor, with Gradle #gradleVersion"() {
    given:
    def settingsFile = testProjectDir.newFile('settings.gradle')
    settingsFile << """\
      include 'annotations'
      include 'processor'
      include 'core'
    """.stripIndent()

    buildFile << """\
      project('annotations') {
        apply plugin: 'java'
      }
      project('processor') {
        apply plugin: 'groovy'

        dependencies {
          compile localGroovy()
        }
      }
      project('core') {
        apply plugin: 'groovy'
        apply plugin: 'net.ltgt.apt'

        dependencies {
          compile localGroovy()
          compileOnly project(':annotations')
          apt project(':processor')
        }

        tasks.withType(GroovyCompile) {
          groovyOptions.javaAnnotationProcessing = true
        }
      }
    """.stripIndent()

    def f = new File(testProjectDir.newFolder('annotations', 'src', 'main', 'java', 'annotations'), 'MyAnnotation.java')
    f.createNewFile()
    f << """\
      package annotations;

      public @interface MyAnnotation {
      }
    """.stripIndent()

    f = new File(testProjectDir.newFolder('processor', 'src', 'main', 'groovy', 'processor'), 'MyAnnotationProcessor.groovy')
    f.createNewFile()
    f << """\
      package processor

      import javax.annotation.processing.AbstractProcessor
      import javax.annotation.processing.RoundEnvironment
      import javax.annotation.processing.SupportedAnnotationTypes
      import javax.lang.model.SourceVersion
      import javax.lang.model.element.TypeElement
      import javax.lang.model.util.ElementFilter
      import javax.tools.Diagnostic
      import javax.tools.FileObject
      import javax.tools.StandardLocation

      @SupportedAnnotationTypes(MyAnnotationProcessor.MY_ANNOTATION)
      class MyAnnotationProcessor extends AbstractProcessor {

        static final String MY_ANNOTATION = "annotations.MyAnnotation"

        @Override
        SourceVersion getSupportedSourceVersion() {
          return SourceVersion.latest()
        }

        private Set<String> annotatedElements = new TreeSet<>()

        @Override
        boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
          annotatedElements.addAll(ElementFilter.typesIn(roundEnv.getElementsAnnotatedWith(
              processingEnv.getElementUtils().getTypeElement(MY_ANNOTATION))).collect {
            it.qualifiedName.toString()
          })
          if (roundEnv.processingOver()) {
            try {
              FileObject f = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", "annotated-elements")
              f.openWriter().withPrintWriter { w ->
                annotatedElements.each {
                  w.println(it)
                }
              }
            } catch (IOException e) {
              processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage())
            }
          }
          return false
        }
      }
    """.stripIndent()
    f = new File(testProjectDir.newFolder('processor', 'src', 'main', 'resources', 'META-INF', 'services'), 'javax.annotation.processing.Processor')
    f.createNewFile()
    f << """\
      processor.MyAnnotationProcessor
    """.stripIndent()

    f = new File(testProjectDir.newFolder('core', 'src', 'main', 'groovy', 'core'), 'HelloWorld.groovy')
    f.createNewFile()
    f << """\
      package core;

      import annotations.MyAnnotation;

      @MyAnnotation
      class HelloWorld {
        String sayHello(String name) {
          "Hello, \${name}!";
        }
      }
    """.stripIndent()

    expect:

    when:
    def result = GradleRunner.create()
            .withGradleVersion(gradleVersion)
            .withProjectDir(testProjectDir.root)
            .withArguments(':core:groovydoc')
            .build()

    then:
    result.task(':annotations:compileJava').outcome == TaskOutcome.SUCCESS
    result.task(':processor:compileGroovy').outcome == TaskOutcome.SUCCESS
    result.task(':core:compileGroovy').outcome == TaskOutcome.SUCCESS
    result.task(':core:groovydoc').outcome == TaskOutcome.SUCCESS
    new File(testProjectDir.root, 'core/build/classes/main/annotated-elements').text.trim() == "core.HelloWorld"

    where:
    gradleVersion << IntegrationTestHelper.GRADLE_VERSIONS
  }
}
