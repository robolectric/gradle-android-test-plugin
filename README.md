# Robolectric Gradle Plugin

[![Build Status](https://secure.travis-ci.org/robolectric/robolectric-gradle-plugin.png?branch=master)](http://travis-ci.org/robolectric/robolectric-gradle-plugin)

A Gradle plugin which enables Robolectric tests.

## Compatibility

Currently known to work with Gradle 1.11, Android Gradle Plugin 0.11.x, Android Studio 0.6.x, and IntelliJ IDEA 13.

## Getting Started

If you are starting a new app, or want to try this plugin in the simplest possible environment, the easiest way to start is to use [deckard-gradle](https://github.com/robolectric/deckard-gradle).

deckard-gradle illustrates how to run Robolectric and [Espresso](https://code.google.com/p/android-test-kit/wiki/Espresso) tests in Intellij, Android Studio or the command-line.

## Basic Usage for JUnit / Robolectric

Add the plugin to your `buildscript`'s `dependencies` section:
```groovy
classpath 'org.robolectric:robolectric-gradle-plugin:0.11.+'
```

Apply the `robolectric` plugin:
```groovy
apply plugin: 'robolectric'
```

Add test-only dependencies using the `androidTestCompile` configuration:
```groovy
androidTestCompile 'junit:junit:4.10'
androidTestCompile 'org.robolectric:robolectric:2.3.+'
androidTestCompile 'com.squareup:fest-android:1.0.+'
```

Write your tests in `src/test/java/`! You can also add per-build type and per-flavor tests by using
the same folder naming conventions (e.g., `src/testPaid/java/`, `src/testDebug/java/`).

Run your tests by calling `gradle clean test`.

## Configuration using DSL

```groovy
robolectric {
    // configure the set of classes for JUnit tests
    include '**/*Test.class'
    exclude '**/espresso/**/*.class'

    // configure max heap size of the test JVM
    maxHeapSize = '2048m'

    // configure whether failing tests should fail the build
    ignoreFailures true
}
```

## Importing into your IDE (IntelliJ or Android Studio)

In a nutshell, you should be able to import into these IDEs (and continuously sync when you change your build.gradle). 

It bears repeating, though: if you see the dreaded `Stub!` exception:

    !!! JUnit version 3.8 or later expected:

	java.lang.RuntimeException: Stub!
	at junit.runner.BaseTestRunner.<init>(BaseTestRunner.java:5)
	at junit.textui.TestRunner.<init>(TestRunner.java:54)
	at junit.textui.TestRunner.<init>(TestRunner.java:48)
	at junit.textui.TestRunner.<init>(TestRunner.java:41)

...you will have to hand-edit your dependencies (in the IDE for IntelliJ, or hand-editing your IML file in Studio). See [deckard-gradle](https://github.com/robolectric/deckard-gradle) for details.

```xml
 <!-- It was to hand-edit $project.iml sample-->
  <module ...>
    <component ...>
      ....
    </component>
    <component name="NewModuleRootManager" inherit-compiler-output="false">  
      <!-- You must add this!!!!-->
      <output-test url="file://$MODULE_DIR$/build/test-classes" />
      
      <orderEntry .../>
      <orderEntry .../>
      <orderEntry .../>
      <orderEntry .../>
      <orderEntry .../>
      <!-- It is may first order, but move to last order -->
      <orderEntry type="jdk" jdkName="Android API 19 Platform" jdkType="Android SDK" />
    </component>
    
  </module>
```

or

task is to automate for modifying IML file
```groovy

task initGradleTest << {

    def imlFile = project.name + '.iml'

    def parse = new XmlParser().parse(imlFile)

    def modulePath = parse.@'external.linked.project.path'

    // It's Robolectric Default ouputPath
    def outputTestPath = "file://$modulePath/build/test-classes"
    def moduleComponent = parse.component.find { it.@name == 'NewModuleRootManager' }
    def outputTest = moduleComponent.find {it.name() == 'output-test'}

    if (outputTest != null) {
        outputTest.@url = outputTestPath
    } else {
        moduleComponent.appendNode('output-test', [url : outputTestPath])
    }

    // jdk orderEntry must be last
    def orderEntry = moduleComponent.orderEntry
    def jdkOrderEntry = orderEntry.find { it.@type == 'jdk' }
    moduleComponent.remove(jdkOrderEntry)
    moduleComponent.append(jdkOrderEntry)

    // rewrite $project.iml file
    FileWriter fileWriter = new FileWriter(imlFile)
    new XmlNodePrinter(new PrintWriter(fileWriter)).print(parse)


}

tasks.preBuild.dependsOn initGradleTest
```

## Robolectric 2.2 or earlier

Version 2.3 of Robolectric will support this plugin out of the box ([see here](https://github.com/robolectric/robolectric/pull/744)).
Until then, you can use the following test runner:
```java
import org.junit.runners.model.InitializationError;
import org.robolectric.AndroidManifest;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.res.Fs;

public class RobolectricGradleTestRunner extends RobolectricTestRunner {
 public RobolectricGradleTestRunner(Class<?> testClass) throws InitializationError {
   super(testClass);
 }

 @Override protected AndroidManifest getAppManifest(Config config) {
   String manifestProperty = System.getProperty("android.manifest");
   if (config.manifest().equals(Config.DEFAULT) && manifestProperty != null) {
     String resProperty = System.getProperty("android.resources");
     String assetsProperty = System.getProperty("android.assets");
     return new AndroidManifest(Fs.fileFromPath(manifestProperty), Fs.fileFromPath(resProperty),
         Fs.fileFromPath(assetsProperty));
   }
   return super.getAppManifest(config);
 }
}
```

Just annotate your test classes with `@RunWith(RobolectricGradleTestRunner.class)` or subclass this
test runner if you have other customizations.

## Plugin Development

The `example/` dir contains a project which covers a few configurations for the plugin to work with.

 1. Run `./gradlew install` in the root. This will build the plugin and install it into a local Maven
    repository.
 2. In the `example/` folder, run `../gradlew clean check` to build and run the unit tests.
 3. Open `example/build/test-report/index.html` in the browser.

### Debugging

You can run the plugin in the debugger, but it takes some setup.

1. Execute this at the root of your project:
    ```
    GRADLE_OPTS="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5006" ./gradlew test --no-daemon -Dorg.gradle.debug=true
    ```
2. Point a remote debugger to port 5006. In Intellij set up 'Remote' Debug configuration and set the port (actual port can be changed)
3. In IntelliJ, to break at breakpoints inside of our plugin or other gradle code you will need to put the appropriate JARs on your classpath, and then attach the source. It's a bit of a hack but it is possible in IntelliJ, anyway :).

## License

    Copyright 2013 Square, Inc.
              2014 Pivotal Labs

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

