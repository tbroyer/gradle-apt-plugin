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

## Usage with IDEs

When the `idea` or `eclipse` plugins are applied, the `idea` and `eclipse` tasks will auto-configure the generated files to enable annotation processing in the corresponding IDE. IntelliJ's Gradle integration will also work automatically if the project format is directory-based (the default).

