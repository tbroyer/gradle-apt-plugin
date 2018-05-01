package net.ltgt.gradle.apt

import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetOutput
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.getPlugin
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withConvention
import org.gradle.plugins.ide.eclipse.model.EclipseJdt
import org.gradle.plugins.ide.eclipse.model.EclipseModel
import org.gradle.plugins.ide.idea.model.IdeaModule
import org.gradle.plugins.ide.idea.model.IdeaProject
import java.io.File

val JavaCompile.aptOptions: AptPlugin.AptOptions
    get() = convention.getPlugin<AptPlugin.AptConvention>().aptOptions

val GroovyCompile.aptOptions: AptPlugin.AptOptions
    get() = convention.getPlugin<AptPlugin.AptConvention>().aptOptions

var AptPlugin.AptOptions.annotationProcessing: Boolean
    get() = isAnnotationProcessing
    set(value) {
        isAnnotationProcessing = value
    }

val SourceSet.annotationProcessorConfigurationName: String
    get() = withConvention(AptPlugin.AptSourceSetConvention::class) { annotationProcessorConfigurationName }

var SourceSet.annotationProcessorPath: FileCollection?
    get() = withConvention(AptPlugin.AptSourceSetConvention::class) { annotationProcessorPath }
    set(value) = withConvention(AptPlugin.AptSourceSetConvention::class) { annotationProcessorPath = value }

var SourceSetOutput.generatedSourcesDir: File?
    get() = withConvention(AptPlugin.AptSourceSetOutputConvention::class) { generatedSourcesDir }
    set(value) = withConvention(AptPlugin.AptSourceSetOutputConvention::class) { setGeneratedSourcesDir(value) }

// Eclipse

val EclipseJdt.apt: EclipseJdtApt
    get() = (this as ExtensionAware).the()

fun EclipseJdt.apt(configure: EclipseJdtApt.() -> Unit): Unit =
    (this as ExtensionAware).configure(configure)

val EclipseModel.factorypath: EclipseFactorypath
    get() = (this as ExtensionAware).the()

fun EclipseModel.factorypath(configure: EclipseFactorypath.() -> Unit): Unit =
    (this as ExtensionAware).configure(configure)

// IDEA

val IdeaModule.apt: AptIdeaPlugin.ModuleApt
    get() = (this as ExtensionAware).the()

fun IdeaModule.apt(configure: AptIdeaPlugin.ModuleApt.() -> Unit): Unit =
    (this as ExtensionAware).configure(configure)

inline
var AptIdeaPlugin.ModuleApt.addGeneratedSourcesDirs: Boolean
    get() = isAddGeneratedSourcesDirs
    set(value) {
        isAddGeneratedSourcesDirs = value
    }
inline
var AptIdeaPlugin.ModuleApt.addCompileOnlyDependencies: Boolean
    get() = isAddCompileOnlyDependencies
    set(value) {
        isAddCompileOnlyDependencies = value
    }
inline
var AptIdeaPlugin.ModuleApt.addAptDependencies: Boolean
    get() = isAddAptDependencies
    set(value) {
        isAddAptDependencies = value
    }

var IdeaProject.configureAnnotationProcessing: Boolean
    get() = withConvention(AptIdeaPlugin.ProjectAptConvention::class) { isConfigureAnnotationProcessing }
    set(value) = withConvention(AptIdeaPlugin.ProjectAptConvention::class) { isConfigureAnnotationProcessing = value }
