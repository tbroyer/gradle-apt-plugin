plugins {
    `kotlin-dsl`
}
repositories {
    jcenter()
}
kotlinDslPluginOptions {
    experimentalWarning.set(false)
}

val compileJava by project(":").tasks.existing(JavaCompile::class)

dependencies {
    compile(files(compileJava.map { it.destinationDir }).builtBy(compileJava))
}
