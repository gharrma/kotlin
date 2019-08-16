/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.perf

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.LanguageAnnotators
import com.intellij.lang.LanguageExtensionPoint
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiElement
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.EditorTestFixture
import com.intellij.testFramework.propertyBased.MadTestingUtil
import org.jetbrains.kotlin.idea.core.script.ScriptDependenciesManager
import org.jetbrains.kotlin.idea.highlighter.KotlinPsiChecker
import org.jetbrains.kotlin.idea.highlighter.KotlinPsiCheckerAndHighlightingUpdater
import org.jetbrains.kotlin.idea.perf.WholeProjectPerformanceTest.Companion.nsToMs
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.assertNotEquals

class PerformanceProjectsTest : AbstractPerformanceProjectsTest() {

    companion object {

        @JvmStatic
        var warmedUp: Boolean = false

        @JvmStatic
        val hwStats: Stats = Stats("helloWorld project")

        @JvmStatic
        val timer: AtomicLong = AtomicLong()

        init {
            // there is no @AfterClass for junit3.8
            Runtime.getRuntime().addShutdownHook(Thread(Runnable { hwStats.close() }))
        }

        fun resetTimestamp() {
            timer.set(0)
        }

        fun markTimestamp() {
            timer.set(System.nanoTime())
        }
    }

    override fun setUp() {
        super.setUp()
        // warm up: open simple small project
        if (!warmedUp) {
            warmUpProject(hwStats)

            warmedUp = true
        }
    }

    fun testHelloWorldProject() {
        tcSuite("Hello world project") {
            perfOpenProject("helloKotlin", hwStats)

            // highlight
            perfHighlightFile("src/HelloMain.kt", hwStats)
            perfHighlightFile("src/HelloMain2.kt", hwStats)
        }
    }

    fun testKotlinProject() {
        tcSuite("Kotlin project") {
            val stats = Stats("kotlin project")
            stats.use {
                perfOpenKotlinProject(it)

                perfHighlightFile("compiler/psi/src/org/jetbrains/kotlin/psi/KtFile.kt", stats = it)

//                typeAndCheckLookup(
//                    myProject!!,
//                    "compiler/psi/src/org/jetbrains/kotlin/psi/KtFile.kt",
//                    "override fun getDeclarations(): List<KtDeclaration> {",
//                    "val q = import",
//                    lookupElements = listOf("importDirectives")
//                )

                perfHighlightFile("compiler/psi/src/org/jetbrains/kotlin/psi/KtElement.kt", stats = it)
            }
        }
    }

    fun testKotlinProjectCompletionBuildGradle() {
        tcSuite("Kotlin completion gradle.kts") {
            val stats = Stats("kotlin completion gradle.kts")
            stats.use { stat ->
                perfOpenKotlinProject(stat)

                perfTypeAndAutocomplete(
                    stat,
                    "build.gradle.kts",
                    "tasks {",
                    "crea",
                    lookupElements = listOf("create"),
                    note = "tasks-create"
                )
            }
        }
    }

    fun testKotlinProjectScriptDependenciesBuildGradle() {
        tcSuite("Kotlin scriptDependencies gradle.kts") {
            val stats = Stats("kotlin scriptDependencies gradle.kts")
            stats.use { stat ->
                perfOpenKotlinProject(stat)

                perfScriptDependenciesBuildGradleKts(stat)
                perfScriptDependenciesIdeaBuildGradleKts(stat)
                perfScriptDependenciesJpsGradleKts(stat)
                perfScriptDependenciesVersionGradleKts(stat)
            }
        }
    }

    fun testKotlinProjectBuildGradle() {
        tcSuite("Kotlin gradle.kts") {
            val stats = Stats("kotlin gradle.kts")
            stats.use { stat ->
                perfOpenKotlinProject(stat)

                perfFileAnalysisBuildGradleKts(stat)
                perfFileAnalysisIdeaBuildGradleKts(stat)
                perfFileAnalysisJpsGradleKts(stat)
                perfFileAnalysisVersionGradleKts(stat)
            }
        }
    }

    private fun perfScriptDependenciesBuildGradleKts(it: Stats) {
        perfScriptDependencies("build.gradle.kts", stats = it)
    }

    private fun perfScriptDependenciesIdeaBuildGradleKts(it: Stats) {
        perfScriptDependencies("idea/build.gradle.kts", stats = it, note = "idea/")
    }

    private fun perfScriptDependenciesJpsGradleKts(it: Stats) {
        perfScriptDependencies("gradle/jps.gradle.kts", stats = it, note = "gradle/")
    }

    private fun perfScriptDependenciesVersionGradleKts(it: Stats) {
        perfScriptDependencies("gradle/versions.gradle.kts", stats = it, note = "gradle/")
    }

    private fun perfFileAnalysisBuildGradleKts(it: Stats) {
        perfKtsFileAnalysis("build.gradle.kts", stats = it)
    }

    private fun perfFileAnalysisIdeaBuildGradleKts(it: Stats) {
        perfKtsFileAnalysis("idea/build.gradle.kts", stats = it, note = "idea/")
    }

    private fun perfFileAnalysisJpsGradleKts(it: Stats) {
        perfKtsFileAnalysis("gradle/jps.gradle.kts", stats = it, note = "gradle/")
    }

    private fun perfFileAnalysisVersionGradleKts(it: Stats) {
        perfKtsFileAnalysis("gradle/versions.gradle.kts", stats = it, note = "gradle/")
    }

    private fun perfKtsFileAnalysis(
        fileName: String,
        stats: Stats,
        note: String = ""
    ) {
        val project = myProject!!
        val disposable = Disposer.newDisposable("perfKtsFileAnalysis $fileName")

        MadTestingUtil.enableAllInspections(project, disposable)

        replaceWithCustomHighlighter()

        try {
            highlightFile {
                val testName = "fileAnalysis ${notePrefix(note)}${simpleFilename(fileName)}"
                val extraStats = Stats("${stats.name} $testName")
                val extraTimingsNs = mutableListOf<Long>()

                val warmUpIterations = 3
                val iterations = 10

                stats.perfTest(
                    warmUpIterations = warmUpIterations,
                    iterations = iterations,
                    testName = testName,
                    setUp = perfKtsFileAnalysisSetUp(project, fileName),
                    test = perfKtsFileAnalysisTest(),
                    tearDown = perfKtsFileAnalysisTearDown(extraTimingsNs, project)
                )

                for (timing in extraTimingsNs.take(warmUpIterations).withIndex()) {
                    val attempt = timing.index
                    val n = "${stats.name} $testName annotator warm-up #$attempt"
                    extraStats.printTestStarted(n)
                    extraStats.printTestFinished(n, timing.value.nsToMs)
                }

                val timings = extraTimingsNs.drop(warmUpIterations).toLongArray()
                extraStats.appendTimings(
                    "annotator",
                    Array(timings.size, init = { null }),
                    timings
                )
            }
        } finally {
            Disposer.dispose(disposable)
        }
    }

    private fun replaceWithCustomHighlighter() {
        val pointName = ExtensionPointName.create<LanguageExtensionPoint<Annotator>>(LanguageAnnotators.EP_NAME)
        val extensionPoint = pointName.getPoint(null)

        val point = LanguageExtensionPoint<Annotator>()
        point.language = "kotlin"
        point.implementationClass = TestKotlinPsiChecker::class.java.name

        val extensions = extensionPoint.extensions
        val filteredExtensions =
            extensions.filter { it.language != "kotlin" || it.implementationClass != KotlinPsiCheckerAndHighlightingUpdater::class.java.name }
                .toList()
        if (filteredExtensions.size < extensions.size) {
            PlatformTestUtil.maskExtensions(pointName, filteredExtensions + listOf(point), testRootDisposable)
        }
    }

    fun perfKtsFileAnalysisSetUp(
        project: Project,
        fileName: String
    ): (TestData<FixtureEditorFile, Pair<Long, List<HighlightInfo>>>) -> Unit {
        return {
            val fileInEditor = openFileInEditor(project, fileName)

            val file = fileInEditor.psiFile
            val virtualFile = file.virtualFile
            val editor = EditorFactory.getInstance().getEditors(fileInEditor.document, project)[0]
            val fixture = EditorTestFixture(project, editor, virtualFile)

            // Note: Kotlin scripts require dependencies to be loaded
            if (isAKotlinScriptFile(fileName)) {
                val vFile = fileInEditor.psiFile.virtualFile
                ScriptDependenciesManager.updateScriptDependenciesSynchronously(vFile, project)
            }

            resetTimestamp()
            it.setUpValue = FixtureEditorFile(file, editor.document, fixture)
        }
    }

    fun perfKtsFileAnalysisTest(): (TestData<FixtureEditorFile, Pair<Long, List<HighlightInfo>>>) -> Unit {
        return {
            it.value = it.setUpValue?.let { fef ->
                Pair(System.nanoTime(), fef.fixture.doHighlighting())
            }
        }
    }

    fun perfKtsFileAnalysisTearDown(
        extraTimingsNs: MutableList<Long>,
        project: Project
    ): (TestData<FixtureEditorFile, Pair<Long, List<HighlightInfo>>>) -> Unit {
        return {
            it.setUpValue?.let { fef ->
                it.value?.let { v ->
                    assertTrue(v.second.isNotEmpty())
                    assertNotEquals(0, timer.get())

                    extraTimingsNs.add(timer.get() - v.first)

                }
                cleanupCaches(project, fef.psiFile.virtualFile)
            }
        }
    }


    class TestKotlinPsiChecker : KotlinPsiChecker() {
        override fun annotate(
            element: PsiElement, holder: AnnotationHolder
        ) {
            super.annotate(element, holder)
            markTimestamp()
        }
    }
}