package org.robolectric.gradle

class RobolectricTestExtension {
    private String maxHeapSize
    private final List<String> includePatterns = new LinkedList<>()
    private final List<String> excludePatterns = new LinkedList<>()
    private boolean ignoreFailures
    private def afterTest

    String getMaxHeapSize() {
        return maxHeapSize
    }

    void setAfterTest(def afterTest) {
      this.afterTest = afterTest
    }

    def getAfterTest() {
      return this.afterTest
    }

    void setMaxHeapSize(String maxHeapSize) {
        this.maxHeapSize = maxHeapSize
    }

    List<String> getIncludePatterns() {
        return this.includePatterns
    }

    void include(String includePattern) {
        this.includePatterns.add includePattern
    }

    List<String> getExcludePatterns() {
        return this.excludePatterns
    }

    void exclude(String excludePattern) {
        this.excludePatterns.add excludePattern
    }

    boolean getIgnoreFailures() {
        return this.ignoreFailures;
    }

    void ignoreFailures(boolean ignoreFailures) {
        this.ignoreFailures = ignoreFailures;
    }
}
