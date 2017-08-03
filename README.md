# gradle-apt-plugin

This plugin does a few things to make it easier/safer to use Java annotation processors in a Gradle build:

* it adds configurations for your compile-time only dependencies (annotations, generally) and annotation processors;
* automatically configures the corresponding `JavaCompile` and `GroovyCompile` tasks to make use of these configurations, when the `java` or `groovy` plugin is applied;
* automatically configures IntelliJ IDEA and/or Eclipse when the `idea` or `eclipse` plugins are applied.

## Using the plugin

The plugin is published to the Plugin Portal; see instructions there: https://plugins.gradle.org/plugin/net.ltgt.apt

You can try snapshots using JitPack: https://jitpack.io/#tbroyer/gradle-apt-plugin

## Added configurations

For each `SourceSet`, two configurations are added:

* for Gradle ≥ 2.12: `<sourceSet>CompileOnly`, extends `<sourceSet>Compile` (Gradle ≥ 2.12 already provides those configurations; note that this plugin doesn't provide a `<sourceSet>CompileClasspath` like Gradle ≥ 2.12)
* `<sourceSet>Apt`

As a result, the following configurations are added to any Java project:

* `compileOnly`, extends `compile`
* `apt`
* `testCompileOnly`, extends `testCompile`
* `testApt`

The `*Only` configurations are used to specify compile-time only dependencies such as annotations that will be processed by annotation processors. Annotation processors themselves are to be added to the `apt` and `testApt` configurations.

The `*Only` configurations are part of the `classpath` of the `JavaCompile` and `GroovyCompile` tasks, whereas the `apt` and `testApt` configurations are turned into `-processorpath` compiler arguments.
Note that up until version 0.7, if those configurations were empty, an empty processor path (`-processorpath :`) would be passed to `javac`; this was a breaking change compared to the normal behavior of Gradle, as it meant annotation processors wouldn't be looked up in the tasks' `classpath`.
Starting with version 0.8, no `-processorpath` will be passed if the `<sourceSet>Apt` configuration is empty; this is to follow the [proposal to add first-class support for annotation processing to Gradle proper](https://github.com/gradle/gradle/blob/master/design-docs/java-annotation-processing.md)

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
To process annotations on Groovy sources, you'll have to configure your `GroovyCompile` tasks; e.g.

```gradle
compileGroovy {
  groovyOptions.javaAnnotationProcessing = true
}
```

## Usage with IDEs

IDE configuration is provided on a best-effort basis.

### Eclipse

Starting with version 0.11, applying the `net.ltgt.apt-eclipse` plugin will auto-configure the generated files to enable annotation processing in Eclipse.
In prior versions (until 0.10), that configuration would automatically happen whenever both the `net.ltgt.apt` and `eclipse` were applied (the new `net.ltgt.apt-eclipse` plugin will also automatically apply the `net.ltgt.apt` and `eclipse` plugins).

From version 0.11 onwards, Eclipse annotation processing can be configured through a DSL, as an extension to the Eclipse JDT DSL (presented here with the default values):
```gradle
eclipse {
  jdt {
    apt {
      // whether annotation processing is enabled in Eclipse
      aptEnabled = compileJava.aptOptions.annotationProcessing
      // where Eclipse will output the generated sources; value is interpreted as per project.file()
      genSrcDir = file('.apt_generated')
      // whether annotation processing is enabled in the editor
      reconcileEnabled = true
      // a map of annotation processor options; a null value will pass the argument as -Akey rather than -Akey=value
      processorOptions = compileJava.aptOptions.processorArgs
    }
  }
}
```

When using Buildship, you'll have to manually run the `eclipseJdtApt` and `eclipseFactorypath` tasks to generate the Eclipse configuration files, then either run the `eclipseJdt` task or manually enable annotation processing: in the project properties → Java Compiler → Annotation Processing, check `Enable Annotation Processing`. Note that while all those tasks are depended on by the `eclipse` task, that one is incompatible with Buildship, so you have to explicitly run the two or three aforementioned tasks and _not_ run the `eclipse` task.

Note that Eclipse does not distinguish main and test sources, and will process all of them using the same factory path and processor options, and the same generated source directory.

In any case, the `net.ltgt.apt-eclipse` plugin (or simply `eclipse` plugin up until version 0.10) has to be applied to the project.

### IntelliJ IDEA

Starting with version 0.11, applying the `net.ltgt.apt-idea` plugin will auto-configure the generated files to enable annotation processing in IntelliJ IDEA.
In prior versions (until 0.10), that configuration would automatically happen whenever both the `net.ltgt.apt` and `idea` were applied (the new `net.ltgt.apt-idea` plugin will also automatically apply the `net.ltgt.apt` and `idea` plugins).

When using the Gradle integration in IntelliJ IDEA (rather than the `ida` task), it is recommended to delegate the IDE build actions to Gradle itself starting with IDEA 2016.3: https://www.jetbrains.com/idea/whatsnew/#v2016-3-gradle
Otherwise, you'll have to manually enable annotation processing: in Settings… → Build, Execution, Deployment → Compiler → Annotation Processors, check `Enable annotation processing` and `Obtain processors from project classpath`. To mimic the Gradle behavior and generated files behavior, you can configure the production and test sources directories to `build/generated/source/apt/main` and `build/generated/source/apt/test` respectively and choose to `Store generated sources relative to:` `Module content root`.

Note that starting with IntelliJ IDEA 2016.1, and unless you delegate build actions to Gradle, you'll have to uncheck `Create separate module per source set` when importing the project.

In any case, the `net.ltgt.apt-idea` plugin (or simply `idea` plugin up until version 0.10) has to be applied to the project.

## Configuration

Starting with version 0.8, the plugin follows the [proposal to add first-class support for annotation processing to Gradle proper](https://github.com/gradle/gradle/blob/master/design-docs/java-annotation-processing.md), making many things configurable by enhancing source sets and tasks.
One notable exception is that the proposed new `CompileOptions` properties are actually available on an `aptOptions` object, as the `CompileOptions` cannot actually be enhanced by plugins.

Each source set gains a few properties:

* for Gradle ≥ 2.12: `compileOnlyConfigurationName` (read-only `String`) returning the `<sourceSet>CompileOnly` configuration name; Gradle ≥ 2.12 already provides that property
* `aptConfigurationName` (read-only `String`) returning the `<sourceSet>Apt` configuration name
* `processorpath`, a `FileCollection` defaulting to the `<sourceSet>Apt` configuration

Each source set `output` gains a `generatedSourcesDir` property, a `File` defaulting to `${project.buildDir}/generated/source/apt/${sourceSet.name}`.

Each `JavaCompile` and `GroovyCompile` task gains a couple properties:

* `generatedSourcesDestinationDir`, corresponding to the `-s` compiler argument, i.e. whether (if set) and where to write sources files generated by annotation processors
* `aptOptions` (read-only), itself with 4 properties:
  * `annotationProcessing`, a `boolean` setting whether annotation processing is enabled or not; this maps to the `-proc:none` compiler argument, and defaults to `true` (meaning that argument is not passed in, and annotation processing is enabled)
  * `processorpath`, a `FileCollection` corresponding to the `-processorpath` compiler argument
  * `processors`, a list of annotation processor class names, mapping to the `-processor` compiler argument
  * `processorArgs`, a map of annotation processor options, each entry mapping to a `-Akey=value` compiler argument

For each source set, the corresponding `JavaCompile` and `GroovyCompile` tasks are configured such that:

* `generatedSourcesDestinationDir` maps to the source set's `output.generatedSourcesDir`
* `aptOptions.processorpath` maps to the source set's `processorpath`
