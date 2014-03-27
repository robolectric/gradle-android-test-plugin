package org.robolectric.gradle

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.BasePlugin
import com.android.build.gradle.LibraryPlugin
import com.android.builder.BuilderConstants
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestReport

class AndroidTestPlugin implements Plugin<Project> {
    private static final String[] TEST_DIRS = ['test', 'androidTest']
    private static final String TEST_TASK_NAME = 'test'
    private static final String TEST_CLASSES_DIR = "test-classes"
    private static final String TEST_REPORT_DIR = "test-report"

    void apply(Project project) {
        def extension = project.extensions.create('androidTest', RobolectricTestExtension)

        def hasAppPlugin = false, hasLibraryPlugin = false;
        def thePlugin = null;
        project.plugins.each { plugin ->
            if (plugin instanceof AppPlugin) hasAppPlugin = true
            else if (plugin instanceof LibraryPlugin) hasLibraryPlugin = true
            if (hasAppPlugin || hasLibraryPlugin) {
                thePlugin = plugin
                return true
            }
        }
        def log = project.logger

        // Ensure the Android plugin has been added in app or library form, but not both.
        if (!hasAppPlugin && !hasLibraryPlugin) {
            throw new IllegalStateException("The 'android' or 'android-library' plugin is required.")
        } else if (hasAppPlugin && hasLibraryPlugin) {
            throw new IllegalStateException(
                    "Having both 'android' and 'android-library' plugin is not supported.")
        }

        // Create the configuration for test-only dependencies.
        def testConfiguration = project.configurations.create(TEST_TASK_NAME + 'Compile')
        // Make the 'test' configuration extend from the normal 'compile' configuration.
        testConfiguration.extendsFrom project.configurations.getByName('compile')

        // Apply the base of the 'java' plugin so source set and java compilation is easier.
        project.plugins.apply JavaBasePlugin
        JavaPluginConvention javaConvention = project.convention.getPlugin JavaPluginConvention

        // Create a root 'test' task for running all unit tests.
        def testTask = project.tasks.create(TEST_TASK_NAME, TestReport)
        testTask.destinationDir = project.file("$project.buildDir/$TEST_REPORT_DIR")
        testTask.description = 'Runs all unit tests.'
        testTask.group = JavaBasePlugin.VERIFICATION_GROUP
        // Add our new task to Gradle's standard "check" task.
        project.tasks.check.dependsOn testTask

        BasePlugin plugin = thePlugin;
        def variants = hasAppPlugin ? project.android.applicationVariants :
                project.android.libraryVariants

        variants.all { variant ->
            if (variant.buildType.name.equals(BuilderConstants.RELEASE)) {
                log.debug("Skipping release build type.")
                return;
            }

            // Get the build type name (e.g., "Debug", "Release").
            def buildTypeName = variant.buildType.name.capitalize()
            def projectFlavorNames = [""]
            if (hasAppPlugin) {
                // Flavors are only available for the app plugin (e.g., "Free", "Paid").
                projectFlavorNames = variant.productFlavors.collect { it.name.capitalize() }
                // TODO support flavor groups... ugh
                if (projectFlavorNames.isEmpty()) {
                    projectFlavorNames = [""]
                }
            }
            def projectFlavorName = projectFlavorNames.join()

            // The combination of flavor and type yield a unique "variation". This value is used for
            // looking up existing associated tasks as well as naming the task we are about to create.
            def variationName = "$projectFlavorName$buildTypeName"
            // Grab the task which outputs the merged manifest, resources, and assets for this flavor.
            def processedManifestPath = variant.processManifest.manifestOutputFile
            def processedResourcesPath = variant.mergeResources.outputDir
            def processedAssetsPath = variant.mergeAssets.outputDir

            def javaCompile = variant.javaCompile;

            // Add the corresponding java compilation output to the 'testCompile' configuration to
            // create the classpath for the test file compilation.
            def robolectricTestConfig = project.configurations.getByName("androidTestCompile")

            def testCompileClasspath = testConfiguration.plus project.files(javaCompile.destinationDir,
                    javaCompile.classpath)
            testCompileClasspath.add robolectricTestConfig

            def testSrcDirs = []
            SourceSet variationSources = javaConvention.sourceSets.create "$TEST_TASK_NAME$variationName"
            def testDestinationDir = project.files("$project.buildDir/$TEST_CLASSES_DIR/$variant.dirName")
            def testRunClasspath = testCompileClasspath.plus testDestinationDir

            TEST_DIRS.each { testDir ->
                testSrcDirs.add(project.file("src/$testDir/java"))
                testSrcDirs.add(project.file("src/$testDir$buildTypeName/java"))
                testSrcDirs.add(project.file("src/$testDir$projectFlavorName/java"))
                projectFlavorNames.each { flavor ->
                    testSrcDirs.add project.file("src/$testDir$flavor/java")
                }
                variationSources.resources.srcDirs project.file("src/$testDir/resources")

                // Add the output of the test file compilation to the existing test classpath to create
                // the runtime classpath for test execution.
                testRunClasspath.add project.files("$project.buildDir/resources/$testDir$variationName")
            };

            variationSources.java.setSrcDirs testSrcDirs

            log.debug("----------------------------------------")
            log.debug("build type name: $buildTypeName")
            log.debug("project flavor name: $projectFlavorName")
            log.debug("variation name: $variationName")
            log.debug("manifest: $processedManifestPath")
            log.debug("resources: $processedResourcesPath")
            log.debug("assets: $processedAssetsPath")
            log.debug("test sources: $variationSources.java.asPath")
            log.debug("test resources: $variationSources.resources.asPath")
            log.debug("----------------------------------------")

            // Create a task which compiles the test sources.
            def testCompileTask = project.tasks.getByName variationSources.compileJavaTaskName
            // Depend on the project compilation (which itself depends on the manifest processing task).
            testCompileTask.dependsOn javaCompile
            testCompileTask.group = null
            testCompileTask.description = null
            testCompileTask.classpath = testCompileClasspath
            testCompileTask.source = variationSources.java
            testCompileTask.destinationDir = testDestinationDir.getSingleFile()
            testCompileTask.doFirst {
                testCompileTask.options.bootClasspath = plugin.getRuntimeJarList().join(File.pathSeparator)
            }

            // Clear out the group/description of the classes plugin so it's not top-level.
            def testClassesTask = project.tasks.getByName variationSources.classesTaskName
            testClassesTask.group = null
            testClassesTask.description = null
            testClassesTask.destinationDir = testDestinationDir

            def testClasses = project.tasks.create("$projectFlavorName$buildTypeName" + 'TestClasses')
            testClasses.dependsOn testClassesTask

            // don't leave test resources behind
            def processResourcesTask = project.tasks.getByName variationSources.processResourcesTaskName
            processResourcesTask.destinationDir = testDestinationDir.getSingleFile()

            // Create a task which runs the compiled test classes.
            def taskRunName = "$TEST_TASK_NAME$variationName"
            def testRunTask = project.tasks.create(taskRunName, Test)
            testRunTask.dependsOn testClassesTask
            testRunTask.inputs.sourceFiles.from.clear()
            testRunTask.classpath = testRunClasspath
            testRunTask.testClassesDir = testCompileTask.destinationDir
            testRunTask.group = JavaBasePlugin.VERIFICATION_GROUP
            testRunTask.description = "Run unit tests for Build '$variationName'."
            // TODO Gradle 1.7: testRunTask.reports.html.destination =
            testRunTask.testReportDir =
                    project.file("$project.buildDir/$TEST_REPORT_DIR/$variant.dirName")
            testRunTask.doFirst {
                // Prepend the Android runtime onto the classpath.
                def androidRuntime = project.files(plugin.getRuntimeJarList().join(File.pathSeparator))
                testRunTask.classpath = testRunClasspath.plus project.files(androidRuntime)
            }

            // Work around http://issues.gradle.org/browse/GRADLE-1682
            testRunTask.scanForTestClasses = false

            // Add the path to the correct manifest, resources, assets as a system property.
            testRunTask.systemProperties.put('android.manifest', processedManifestPath)
            testRunTask.systemProperties.put('android.resources', processedResourcesPath)
            testRunTask.systemProperties.put('android.assets', processedAssetsPath)
            testRunTask.setMaxHeapSize(extension.maxHeapSize)

            String includePattern = extension.includePattern ? extension.includePattern : '**/*Test.class'
            testRunTask.include(includePattern)
            if (extension.excludePattern) {
                testRunTask.exclude(extension.excludePattern)
            }

            testTask.reportOn testRunTask
        }
    }
}
