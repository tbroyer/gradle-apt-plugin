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

import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetOutput
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withConvention
import org.gradle.plugins.ide.eclipse.model.EclipseJdt
import org.gradle.plugins.ide.eclipse.model.EclipseModel
import org.gradle.plugins.ide.idea.model.IdeaModule
import org.gradle.plugins.ide.idea.model.IdeaProject

val JavaCompile.aptOptions: AptPlugin.AptOptions
    get() = the()

fun JavaCompile.aptOptions(configure: AptPlugin.AptOptions.() -> Unit) =
    configure(configure)

val GroovyCompile.aptOptions: AptPlugin.AptOptions
    get() = the()

fun GroovyCompile.aptOptions(configure: AptPlugin.AptOptions.() -> Unit) =
    configure(configure)

var AptPlugin.AptOptions.annotationProcessing: Boolean
    get() = isAnnotationProcessing
    set(value) {
        isAnnotationProcessing = value
    }

@Suppress("ConflictingExtensionProperty")
val SourceSet.annotationProcessorConfigurationName: String
    get() = withConvention(AptPlugin.AptSourceSetConvention::class) { annotationProcessorConfigurationName }

@Suppress("ConflictingExtensionProperty")
var SourceSet.annotationProcessorPath: FileCollection?
    get() = withConvention(AptPlugin.AptSourceSetConvention::class) { annotationProcessorPath }
    set(value) = withConvention(AptPlugin.AptSourceSetConvention::class) { annotationProcessorPath = value }

val SourceSetOutput.generatedSourcesDirs: FileCollection
    get() = AptPlugin.IMPL.getGeneratedSourcesDirs(this)

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
var AptIdeaPlugin.ModuleApt.addAptDependencies: Boolean
    get() = isAddAptDependencies
    set(value) {
        isAddAptDependencies = value
    }

var IdeaProject.configureAnnotationProcessing: Boolean
    get() = withConvention(AptIdeaPlugin.ProjectAptConvention::class) { isConfigureAnnotationProcessing }
    set(value) = withConvention(AptIdeaPlugin.ProjectAptConvention::class) { isConfigureAnnotationProcessing = value }
