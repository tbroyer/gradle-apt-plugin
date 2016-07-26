# gradle-apt-plugin

This plugin does a few things to make it easier/safer to use Java annotation processors in a Gradle build:

* it adds configurations for your compile-time only dependencies (annotations, generally) and annotation processors;
* automatically configures the corresponding `JavaCompile` and `GroovyCompile` tasks to make use of these configurations, when the `java` or `groovy` plugin is applied;
* automatically configures IntelliJ IDEA and/or Eclipse when the `idea` or `eclipse` plugins are applied.

## Using the plugin

The plugin is published to the Plugin Portal; see instructions there: https://plugins.gradle.org/plugin/net.ltgt.apt

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
  compile "com.google.dagger:dagger:2.0.2"
  apt     "com.google.dagger:dagger-compiler:2.0.2"

  // auto-factory contains both annotations and their processor, neither is needed at runtime
  compileOnly "com.google.auto.factory:auto-factory:1.0-beta3"
  apt         "com.google.auto.factory:auto-factory:1.0-beta3"
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

## Eclipse

When the `eclipse` plugin is applied, the `eclipse` task will auto-configure the generated Eclipse projects to enable annotation processing.

## IntelliJ

If you are using [Gradle's IDEA plugin][] in your gradle.build, just run `gradle idea` as usual to generate your IntelliJ project; annotation processing will be automatically configured. If you are using [IntelliJ's Gradle plugin][], however, or if you want to import individual modules into another project, you'll have to manually enable annotation processing: in Settings… → Build, Execution, Deployment → Compiler → Annotation Processors, check `Enable annotation processing` and `Obtain processors from project classpath`. To mimic the Gradle behavior and generated files behavior, you can configure the production and test sources directories to `build/generated/source/apt/main` and `build/generated/source/apt/test` respectively and choose to `Store generated sources relative to:` `Module content root`.

[Gradle's IDEA plugin]: https://docs.gradle.org/current/userguide/idea_plugin.html
[IntelliJ's Gradle plugin]: https://www.jetbrains.com/idea/help/gradle.html
