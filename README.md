# gradle-apt-plugin

This plugin does a few things to make it easier/safer to use Java annotation processors in a Gradle build:

* it adds configurations for your compile-time only dependencies (annotations, generally) and annotation processors;
* automatically configures the corresponding `JavaCompile` and `GroovyCompile` tasks to make use of these configurations, when the `java` or `groovy` plugin is applied;
* automatically configures IntelliJ IDEA and/or Eclipse when the `idea` or `eclipse` plugins are applied.

## Using the plugin

The plugin is published to the Plugin Portal; see instructions there: https://plugins.gradle.org/plugin/net.ltgt.apt

You can try snapshots using JitPack: https://jitpack.io/#tbroyer/gradle-apt-plugin

## Added configurations

The following configurations are added to any Java project:

* `compileOnly`, extends `compile`
* `apt`
* `testCompileOnly`, extends `testCompile`
* `testApt`

The `*Only` configurations are used to specify compile-time only dependencies such as annotations that will be processed by annotation processors. Annotation processors themselves are to be added to the `apt` and `testApt` configurations.

The `*Only` configurations are part of the `classpath` of the `JavaCompile` and `GroovyCompile` tasks, whereas the `apt` and `testApt` configurations are turned into `-processorpath` compiler arguments. Note that if those configurations are empty, an empty processor path (`-processorpath :`) will be passed to `javac`; this is a breaking change compared to the normal behavior of Gradle, as it means annotation processors won't be looked up in the tasks' `classpath`.

Finally, note that those configurations don't extend each others: `testCompileOnly` doesn't extend `compileOnly`, and `testApt` doesn't extend `apt`; those configurations are only use for their respective `JavaCompile` and `GroovyCompile` tasks.

### Example usage

After applying the plugin following the above instructions, those added configurations can be used when declaring dependencies:

```gradle
dependencies {
  compile "com.google.dagger:dagger:2.6"
  apt     "com.google.dagger:dagger-compiler:2.6"

  // auto-factory contains both annotations and their processor, neither is needed at runtime
  compileOnly "com.google.auto.factory:auto-factory:1.0-beta3"
  apt         "com.google.auto.factory:auto-factory:1.0-beta3"

  compileOnly "org.immutables:value:2.2.10:annotations"
  apt         "org.immutables:value:2.2.10"
}
```

## Groovy support

Starting with version 0.6, the plugin also configures `GroovyCompile` tasks added when the `groovy` plugin is applied.
It does not however configure annotation processing for Groovy sources, only for Java sources used in joint compilation.
Turn process annotations on Groovy sources, you'll have to configure your `GroovyCompile` tasks; e.g.

```gradle
compileGroovy {
  groovyOptions.javaAnnotationProcessing = true
}
```

## Usage with IDEs

IDE configuration is provided on a best-effort basis.

### Eclipse

When the `eclipse` plugin is applied, the `eclipse` task will auto-configure the generated files to enable annotation processing in Eclipse.

When using Buildship, you'll have to manually run the `eclipseJdtApt` and `eclipseFactorypath` tasks to generate the Eclipse configuration files, then manually enable annotation processing: in the project properties → Java Compiler → Annotation Processing, check `Enable Annotation Processing`.

In any case, the `eclipse` plugin has to be applied to the project.

### IntelliJ IDEA

When the `idea` plugin is applied, the `idea` task will auto-configure the generated files to enable annotation processing in intelliJ IDEA.

When using the Gradle integration in IntelliJ IDEA however, rather than the `idea` task, you'll have to manually enable annotation processing: in Settings… → Build, Execution, Deployment → Compiler → Annotation Processors, check `Enable annotation processing` and `Obtain processors from project classpath`. To mimic the Gradle behavior and generated files behavior, you can configure the production and test sources directories to `build/generated/source/apt/main` and `build/generated/source/apt/test` respectively and choose to `Store generated sources relative to:` `Module content root`.

Note that starting with IntelliJ IDEA 2016.1, you'll have to uncheck `Create separate module per source set` when importing the project.

In any case, the `idea` plugin has to be applied to the project.
