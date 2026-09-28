package com.bbbjam.konsist

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.provider.KoModuleProvider
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import java.io.File
import org.junit.Assert.fail
import org.junit.Test

/**
 * Executable form of the Dependency Rules in `.claude/skills/architecture/SKILL.md` (D-02, D-03).
 *
 * Module groups are read from each file's module path (`core/…`, `feature/…`), never from a
 * hard-coded list, so a new `:feature:*` module is covered without editing this class.
 *
 * Known limit: Konsist sees imports, not resolved references. A fully qualified reference with no
 * import slips past the import rules; the build-file rule closes that gap, because a cross-module
 * reference cannot compile without a Gradle `project(":…")` dependency.
 */
class ModuleIsolationTest {

    @Test
    fun `Konsist scope covers every included module`() {
        val settings = File(rootDir, "settings.gradle.kts")
        val included = INCLUDE_REGEX.findAll(settings.readText())
            .map { it.groupValues[1].removePrefix(":").replace(':', '/') }
            .toList()
        val scannedModules = scope.files.map { it.modulePath }.toSet()
        val missing = included.filterNot { it in scannedModules }
        if (included.isEmpty() || missing.isNotEmpty()) {
            fail(
                "Assert 'scope-sanity' was violated (${missing.size} times). " +
                    "Included modules with no file in the Konsist scope: $missing " +
                    "(included: $included, scanned: $scannedModules)",
            )
        }
    }

    @Test
    fun `core and feature files declare a package under their module root`() {
        scope.files
            .filter { it.modulePath.startsWith(CORE) || it.modulePath.startsWith(FEATURE) }
            .assertTrue(testName = "package-under-module-root") { file ->
                val root = packageRootOf(file.modulePath)
                val pkg = file.packagee?.name
                pkg != null && (pkg == root || pkg.startsWith("$root."))
            }
    }

    @Test
    fun `feature modules do not import other feature modules`() {
        featureFiles().flatMap { it.imports }
            .assertFalse(testName = "feature-imports-feature") { import ->
                val ownRoot = packageRootOf(import.modulePath)
                import.name.startsWith(FEATURE_PACKAGE) && !import.name.isUnder(ownRoot)
            }
    }

    @Test
    fun `feature modules do not import the app module`() {
        featureFiles().flatMap { it.imports }
            .assertFalse(testName = "feature-imports-app") { import ->
                import.name.startsWith(PROJECT_PACKAGE) &&
                    !import.name.startsWith(CORE_PACKAGE) &&
                    !import.name.startsWith(FEATURE_PACKAGE)
            }
    }

    @Test
    fun `core modules import only their own root and core model`() {
        scope.files.filter { it.modulePath.startsWith(CORE) }.flatMap { it.imports }
            .assertFalse(testName = "core-import-allowlist") { import ->
                val allowed = buildList {
                    add(packageRootOf(import.modulePath))
                    if (import.modulePath != CORE_MODEL) add(packageRootOf(CORE_MODEL))
                }
                import.name.startsWith(PROJECT_PACKAGE) && allowed.none { import.name.isUnder(it) }
            }
    }

    @Test
    fun `core model does not touch Android`() {
        scope.files.filter { it.modulePath == CORE_MODEL }.flatMap { it.imports }
            .assertFalse(testName = "core-model-no-android") { import ->
                import.name.startsWith("android.") || import.name.startsWith("androidx.")
            }
    }

    @Test
    fun `no ViewModel is imported or subclassed`() {
        scope.files.flatMap { it.imports }
            .assertFalse(testName = "no-viewmodel-import") { import ->
                VIEWMODEL_IMPORT_PREFIXES.any { import.name.startsWith(it) }
            }
        scope.classes()
            .assertFalse(testName = "no-viewmodel-subclass") { declaration ->
                declaration.parents().any { it.name.substringAfterLast('.') in VIEWMODEL_NAMES }
            }
    }

    @Test
    fun `module build files declare only allowed project dependencies`() {
        val violations = listOf(CORE, FEATURE)
            .flatMap { group -> File(rootDir, group).listFiles().orEmpty().toList() }
            .map { File(it, "build.gradle.kts") }
            .filter { it.isFile }
            .flatMap { buildFile -> buildFileViolations(buildFile) }
        if (violations.isNotEmpty()) {
            fail(
                "Assert 'build-file-project-deps' was violated (${violations.size} times). " +
                    "Invalid build files:\n" + violations.joinToString("\n"),
            )
        }
    }

    private fun buildFileViolations(buildFile: File): List<String> {
        val modulePath = buildFile.parentFile.relativeTo(rootDir).path.replace('\\', '/')
        val display = "$modulePath/build.gradle.kts"
        return buildFile.readLines().flatMapIndexed { index, line ->
            val location = "$display:${index + 1}"
            val accessor = if (TYPE_SAFE_ACCESSOR.containsMatchIn(line)) {
                listOf("$location type-safe project accessor, use project(\":…\"): ${line.trim()}")
            } else {
                emptyList()
            }
            val projects = PROJECT_DEPENDENCY.findAll(line)
                .map { it.groupValues[1] }
                .filterNot { isAllowedProjectDependency(modulePath, it) }
                .map { "$location $modulePath may not depend on $it: ${line.trim()}" }
                .toList()
            accessor + projects
        }
    }

    private fun isAllowedProjectDependency(modulePath: String, dependency: String): Boolean = when {
        modulePath == CORE_MODEL -> false
        modulePath.startsWith(CORE) -> dependency == ":core:model"
        modulePath.startsWith(FEATURE) -> dependency.startsWith(":core:")
        else -> true
    }

    private fun featureFiles(): List<KoFileDeclaration> =
        scope.files.filter { it.modulePath.startsWith(FEATURE) }

    private fun String.isUnder(root: String): Boolean = this == root || startsWith("$root.")

    private companion object {
        const val CORE = "core/"
        const val FEATURE = "feature/"
        const val CORE_MODEL = "core/model"
        const val PROJECT_PACKAGE = "com.bbbjam."
        const val CORE_PACKAGE = "com.bbbjam.core."
        const val FEATURE_PACKAGE = "com.bbbjam.feature."

        val VIEWMODEL_IMPORT_PREFIXES = listOf(
            "androidx.lifecycle.ViewModel",
            "androidx.lifecycle.AndroidViewModel",
            "androidx.lifecycle.viewmodel",
        )
        val VIEWMODEL_NAMES = setOf("ViewModel", "AndroidViewModel")

        val INCLUDE_REGEX = Regex("""include\(\s*"(:[^"]+)"\s*\)""")
        val PROJECT_DEPENDENCY = Regex("""project\(\s*"(:[^"]+)"\s*\)""")
        val TYPE_SAFE_ACCESSOR = Regex("""\bprojects\.""")

        val rootDir: File by lazy {
            File(requireNotNull(System.getProperty("bbbjam.rootDir")) { "bbbjam.rootDir is not set" })
        }

        val scope: KoScope by lazy { Konsist.scopeFromProject() }

        /** Konsist reports the module with the OS separator (`core\model` on Windows). */
        val KoModuleProvider.modulePath: String
            get() = moduleName.replace('\\', '/')

        /** `feature/next-jam` → `com.bbbjam.feature.nextjam`. */
        fun packageRootOf(modulePath: String): String =
            "com.bbbjam." + modulePath.split('/').joinToString(".") { it.replace("-", "").lowercase() }
    }
}
