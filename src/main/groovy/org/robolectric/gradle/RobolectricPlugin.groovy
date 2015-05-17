package org.robolectric.gradle

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.api.BaseVariant
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test

/**
 * Robolectric gradle plugin.
 */
class RobolectricPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        def hasAppPlugin = project.plugins.find { p -> p instanceof AppPlugin }
        def hasLibPlugin = project.plugins.find { p -> p instanceof LibraryPlugin }
        if (!hasAppPlugin && !hasLibPlugin) {
            throw new IllegalStateException("robolectric-gradle-plugin: The 'com.android.application' or 'com.android.library' plugin is required.")
        }

        project.afterEvaluate {
            // Configure the test tasks
            project.android.(hasAppPlugin ? "applicationVariants" : "libraryVariants").all { BaseVariant variant ->
                def taskName = "test${variant.name.capitalize()}"
                def assets = variant.mergeAssets.outputDir
                def manifest = variant.outputs.first().processManifest.manifestOutputFile
                def resources = variant.mergeResources.outputDir
                def packageName = project.android.defaultConfig.applicationId

                // Set RobolectricTestRunner properties
                def task = project.tasks.findByName(taskName) as Test
                task.systemProperty "android.assets", assets
                task.systemProperty "android.manifest", manifest
                task.systemProperty "android.resources", resources
                task.systemProperty "android.package", packageName

                project.logger.info("Configuring task: ${taskName}")
                project.logger.info("Robolectric assets: ${assets}")
                project.logger.info("Robolectric manifest: ${manifest}")
                project.logger.info("Robolectric resources: ${resources}")
                project.logger.info("Robolectric package: ${packageName}")
            }
        }
    }
}
