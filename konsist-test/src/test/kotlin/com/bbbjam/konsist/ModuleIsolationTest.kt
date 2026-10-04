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
 * reference cannot compile without a Gradle `project(":…")` dependency. For the data libraries,
 * which can reach another module transitively (kotlinx-serialization-core does), the text rule
 * `data-libraries-only-in-core-data-qualified` closes it.
 *
 * Two rules read build files as plain text rather than Kotlin declarations:
 * `build-file-project-deps` (allowed `project(":…")` dependencies) and
 * `build-file-applies-convention` (every module applies its `bluesjam.*` convention plugin and does
 * not copy back a setting the convention owns).
 *
 * Two rules guard D-17 in screens as text matches: `no-material-theme-outside-core-ui` (no
 * `MaterialTheme.colorScheme`/`.typography`/`.shapes` outside `:core:ui`) and
 * `amber-roles-allowlisted` (an amber role outside `:core:ui` only where `AMBER_ROLE_ALLOWLIST`
 * allows it for that module). `no-dp-literal-outside-core-ui` does the same for sizes: no `dp`,
 * `sp` or `em` built from a number literal outside `:core:ui`.
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
    fun `core model does not read the system clock`() {
        scope.files.filter { it.modulePath == CORE_MODEL }
            .assertFalse(testName = "core-model-no-system-clock") { file ->
                SYSTEM_CLOCK.containsMatchIn(file.text)
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
    fun `only core data imports the data libraries`() {
        scope.files.filter { it.modulePath != CORE_DATA }.flatMap { it.imports }
            .assertFalse(testName = "data-libraries-only-in-core-data") { import ->
                DATA_LIBRARY_PREFIXES.any { import.name.startsWith(it) }
            }
    }

    @Test
    fun `only core data references the data libraries, even fully qualified`() {
        scope.files
            .filter { it.modulePath != CORE_DATA && it.modulePath != KONSIST_TEST }
            .assertFalse(testName = "data-libraries-only-in-core-data-qualified") { file ->
                file.text.lineSequence()
                    .filterNot { it.trimStart().startsWith("import ") }
                    .any { QUALIFIED_DATA_LIBRARY.containsMatchIn(it) }
            }
    }

    @Test
    fun `colors outside core ui come from its tokens`() {
        scope.files
            .filter { it.modulePath != CORE_UI && it.modulePath != KONSIST_TEST }
            .assertFalse(testName = "no-color-literal-outside-core-ui") { file ->
                COLOR_LITERAL.containsMatchIn(file.text)
            }
    }

    @Test
    fun `dimensions outside core ui come from its tokens`() {
        scope.files
            .filter { it.modulePath != CORE_UI && it.modulePath != KONSIST_TEST }
            .assertFalse(testName = "no-dp-literal-outside-core-ui") { file ->
                DIMENSION_LITERAL.containsMatchIn(file.text)
            }
    }

    @Test
    fun `screens outside core ui do not read the Material theme`() {
        scope.files
            .filter { it.modulePath != CORE_UI && it.modulePath != KONSIST_TEST }
            .assertFalse(testName = "no-material-theme-outside-core-ui") { file ->
                MATERIAL_THEME_READ.containsMatchIn(file.text)
            }
    }

    @Test
    fun `amber roles outside core ui are allowlisted per module`() {
        scope.files
            .filter { it.modulePath != CORE_UI && it.modulePath != KONSIST_TEST }
            .assertFalse(testName = "amber-roles-allowlisted") { file ->
                val allowed = AMBER_ROLE_ALLOWLIST[file.modulePath].orEmpty()
                AMBER_ROLE_READ.findAll(file.text).any { it.groupValues[2] !in allowed }
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

    @Test
    fun `module build files apply their convention plugin`() {
        val settings = File(rootDir, "settings.gradle.kts")
        val violations = INCLUDE_REGEX.findAll(settings.readText())
            .map { it.groupValues[1].removePrefix(":").replace(':', '/') }
            .flatMap { conventionViolations(it) }
            .toList()
        if (violations.isNotEmpty()) {
            fail(
                "Assert 'build-file-applies-convention' was violated (${violations.size} times). " +
                    "Invalid build files:\n" + violations.joinToString("\n"),
            )
        }
    }

    private fun conventionViolations(modulePath: String): List<String> {
        val display = "$modulePath/build.gradle.kts"
        val buildFile = File(rootDir, display)
        if (!buildFile.isFile) return listOf("$display is missing")
        val lines = buildFile.readLines().map { it.substringBefore("//") }
        val applied = lines.flatMap { line -> CONVENTION_ID.findAll(line).map { it.groupValues[1] } }
        val required = when {
            modulePath == APP -> "bluesjam.android.application"
            modulePath.startsWith(FEATURE) -> "bluesjam.android.feature"
            else -> null
        }
        val pluginViolations = when {
            applied.isEmpty() -> listOf("$display applies no bluesjam.* convention plugin")
            required != null && required !in applied -> listOf("$display must apply $required (applies $applied)")
            else -> emptyList()
        }
        val lineViolations = lines.flatMapIndexed { index, line ->
            val location = "$display:${index + 1}"
            listOfNotNull(
                RAW_PLUGIN.find(line)?.let { "$location applies a plugin directly, use a convention: ${line.trim()}" },
                OWNED_SETTING.find(line)?.let {
                    "$location sets '${it.groupValues[1]}', owned by a convention: ${line.trim()}"
                },
            )
        }
        return pluginViolations + lineViolations
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

    private fun featureFiles(): List<KoFileDeclaration> = scope.files.filter { it.modulePath.startsWith(FEATURE) }

    private fun String.isUnder(root: String): Boolean = this == root || startsWith("$root.")

    private companion object {
        const val CORE = "core/"
        const val FEATURE = "feature/"
        const val CORE_MODEL = "core/model"
        const val CORE_UI = "core/ui"
        const val CORE_DATA = "core/data"
        const val KONSIST_TEST = "konsist-test"
        const val APP = "app"
        const val PROJECT_PACKAGE = "com.bbbjam."
        const val CORE_PACKAGE = "com.bbbjam.core."
        const val FEATURE_PACKAGE = "com.bbbjam.feature."

        val VIEWMODEL_IMPORT_PREFIXES = listOf(
            "androidx.lifecycle.ViewModel",
            "androidx.lifecycle.AndroidViewModel",
            "androidx.lifecycle.viewmodel",
        )
        val VIEWMODEL_NAMES = setOf("ViewModel", "AndroidViewModel")

        /**
         * HTTP, the Room cache and JSON belong to `:core:data`: Sheet I/O lives only in repositories
         * (D-13), so no other module may import them.
         */
        val DATA_LIBRARY_PREFIXES = listOf("okhttp3.", "androidx.room.", "kotlinx.serialization.")

        /**
         * A reference to a data library outside an import line, such as a fully qualified
         * `kotlinx.serialization.KSerializer`, which compiles in a feature because serialization-core
         * reaches it transitively. Import lines are `data-libraries-only-in-core-data`'s.
         *
         * Known limits: it is a line-based text match, so a qualified name split across lines at a
         * dot, or written with a backticked segment, is not caught. The compile classpath still
         * blocks okhttp3, Room and serialization-json outside `:core:data`, but not
         * serialization-core.
         */
        val QUALIFIED_DATA_LIBRARY = Regex("""\b(okhttp3|androidx\.room|kotlinx\.serialization)\.""")

        val INCLUDE_REGEX = Regex("""include\(\s*"(:[^"]+)"\s*\)""")
        val PROJECT_DEPENDENCY = Regex("""project\(\s*"(:[^"]+)"\s*\)""")
        val TYPE_SAFE_ACCESSOR = Regex("""\bprojects\.""")

        /** A convention plugin applied by id, such as `id("bluesjam.android.feature")`. */
        val CONVENTION_ID = Regex("""\bid\(\s*"(bluesjam\.[a-z.]+)"\s*\)""")

        /** A plugin applied without a convention: a catalog alias, another id, `kotlin("…")`, `apply(plugin…)`. */
        val RAW_PLUGIN = Regex(
            """\balias\(\s*libs\.plugins\.|\bid\(\s*"(?!bluesjam\.)|\bkotlin\(\s*"|\bapply\(\s*plugin""",
        )

        /** A setting a convention plugin owns, which a module build file never sets again. */
        val OWNED_SETTING = Regex(
            """\b(compileSdk|minSdk|targetSdk|JavaVersion|JvmTarget|jvmTarget|jvmToolchain|""" +
                """sourceCompatibility|targetCompatibility|buildFeatures|isReturnDefaultValues)\b""",
        )

        /**
         * A read of Material's theme values in a screen (D-17): screens read `BluesJamTheme.colors`,
         * `.typography` and `.shapes`. Known limits: a text match, so an import alias of
         * `MaterialTheme`, `with(MaterialTheme) { colorScheme }`, or a Material component left on its
         * default (amber `primary`) colors is not caught.
         */
        val MATERIAL_THEME_READ = Regex("""\bMaterialTheme\s*\.\s*(colorScheme|typography|shapes)\b""")

        /**
         * A read of an amber role (`DESIGN.md`: amber only for its five uses, plus their `on…` colors).
         * Group 2 is the role. Known limits: a text match, so an alias
         * (`val c = BluesJamTheme.colors; c.key`) escapes, and it cannot judge whether an allowed role
         * is drawn on the right element.
         */
        val AMBER_ROLE_READ = Regex(
            """\b(colors|BluesJamColors)\s*\.\s*""" +
                """(primaryAction|onPrimaryAction|slotOpen|key|published|onPublished|activeFilter|onActiveFilter)\b""",
        )

        /**
         * The amber roles each module outside `:core:ui` may read. A slice that adds an amber use
         * (`slotOpen`, `activeFilter`, `published`, `primaryAction`) adds it here in its own diff, so
         * every amber use is a reviewed decision. A module not listed may read none.
         */
        val AMBER_ROLE_ALLOWLIST: Map<String, Set<String>> = mapOf(
            "feature/next-jam" to setOf("key"),
        )

        /**
         * A dimension built from a number literal (`list-states`, K1): `4.dp`, `0.5f.dp`, `14.sp`,
         * `1.2.em` or `Dp(4…)`. Screens read `BluesJamTheme.spacing` and `.shapes`. Known limits: a
         * text match, so `n.dp` on a variable or a constant, and literals inside `:core:ui`
         * components, are not caught.
         */
        val DIMENSION_LITERAL = Regex("""\b\d+(\.\d+)?f?\s*\.\s*(dp|sp|em)\b|\bDp\(\s*\d""")

        /** A `Color(…)` built from a number, or an ARGB hex literal such as a preview `backgroundColor`. */
        val COLOR_LITERAL = Regex("""\bColor\(\s*(0x|\d)|\b0x[0-9A-Fa-f]{8}L?\b""")

        /**
         * A read of the system time: any `.now(`, `Clock.system…`, `System.currentTimeMillis(` or
         * `System.nanoTime(`. `:core:model` takes today's date from its caller.
         */
        val SYSTEM_CLOCK = Regex("""\.now\(|\bClock\.system|\bSystem\.currentTimeMillis\(|\bSystem\.nanoTime\(""")

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
