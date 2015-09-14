# gradle-apt-plugin

This plugin does a few things to make it easier/safer to use Java annotation processors in a Gradle build:

* it adds configurations for your compile-time only dependencies (annotations, generally) and annotation processors;
* automatically configures the corresponding `JavaCompile` tasks to make use of these configurations, when the `java` plugin is applied;
* automatically configures IntelliJ IDEA and/or Eclipse when the `idea` or `eclipse` plugins are applied.

## Using the plugin

The plugin is published to the Plugin Portal; see instructions there: https://plugins.gradle.org/plugin/net.ltgt.apt

## Added configurations

The following configurations are added:

* `compileOnly`, extends `compile`
* `apt`
* `testCompileOnly`, extends `testCompile`
* `testApt`

The `*Only` configurations are used to specificy compile-time only dependencies such as annotations that will be processed by annotation processors. Annotation processors themselves are to be added to the `apt` and `testApt` configurations.

The `*Only` configurations are part of the `classpath` of the `JavaCompile` tasks, whereas the `apt` and`testApt` configurations are turned into `-processorpath` compiler arguments. Note that if those configurations are empty, an empty processor path (`-processorpath :`) will be passed to `javac`; this is a breaking change compared to the normal behavior of Gradle, as it means annotation processors won't be looked up in the tasks' `classpath`.

## Eclipse

When the `eclipse` plugin is applied, the `eclipse` task will auto-configure the generated Eclipse projects to enable annotation processing.

## IntelliJ

Ideally, just apply [Gradle's IDEA plugin][] in your gradle.build, and run `gradle idea` to generate your IntelliJ project. If you would rather use [IntelliJ's Gradle plugin][] however, or if you want to import individual modules into another project, you'll have to manually enable annotation processing: in Settings… → Build, Execution, Deployment → Compiler → Annotation Processors, check `Enable annotation processing` and `Obtain processors from project classpath`. To mimic the Gradle behavior and generated files behavior, you can configure the production and test sources directories to `build/generated/source/apt/main` and `build/generated/source/apt/test` respectively and choose to `Store generated sources relative to:` `Module content root`.

[Gradle's IDEA plugin]: https://docs.gradle.org/current/userguide/idea_plugin.html
[IntelliJ's Gradle plugin]: https://www.jetbrains.com/idea/help/gradle.html
