package com.bbbjam.core.ui.text

import com.bbbjam.core.ui.R
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Resolves a [UiText] against the real `src/main/res/values/strings.xml`, with no Android: the
 * resource ids come from the generated `R` classes by reflection and the wording from the XML file
 * (the module directory is the working directory of a unit test). A test therefore still pins the
 * shipped Spanish wording, and a copy change in `strings.xml` fails it.
 */
internal object TestStrings {
    private val document by lazy {
        DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/values/strings.xml")).documentElement
    }

    private val stringNames: Map<Int, String> by lazy { idsOf(R.string::class.java) }
    private val pluralNames: Map<Int, String> by lazy { idsOf(R.plurals::class.java) }

    private fun idsOf(type: Class<*>): Map<Int, String> =
        type.fields.filter { it.type == Int::class.javaPrimitiveType }.associate { it.getInt(null) to it.name }

    private fun children(tag: String): List<Element> {
        val nodes = document.getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private val strings: Map<String, String> by lazy {
        children("string").associate { it.getAttribute("name") to unescape(it.textContent) }
    }

    private val plurals: Map<String, Map<String, String>> by lazy {
        children("plurals").associate { plural ->
            val items = plural.getElementsByTagName("item")
            plural.getAttribute("name") to (0 until items.length).associate {
                val item = items.item(it) as Element
                item.getAttribute("quantity") to unescape(item.textContent)
            }
        }
    }

    private fun unescape(raw: String): String = raw.replace("\\'", "'").replace("\\\"", "\"").replace("\\n", "\n")

    fun resolve(text: UiText): String = when (text) {
        is UiText.Raw -> text.value

        is UiText.Res -> {
            val name = requireNotNull(stringNames[text.id]) { "unknown string id ${text.id}" }
            format(requireNotNull(strings[name]) { "$name is not in strings.xml" }, text.args)
        }

        is UiText.Plural -> {
            val name = requireNotNull(pluralNames[text.id]) { "unknown plurals id ${text.id}" }
            val quantity = if (text.count == 1) "one" else "other"
            val items = requireNotNull(plurals[name]) { "$name is not in strings.xml" }
            format(requireNotNull(items[quantity]) { "$name has no $quantity" }, text.args)
        }
    }

    private fun format(pattern: String, args: List<Any>): String =
        String.format(Locale.ROOT, pattern, *args.map { if (it is UiText) resolve(it) else it }.toTypedArray())
}

/** This text as the user reads it, resolved against the shipped `strings.xml`. */
internal fun UiText.text(): String = TestStrings.resolve(this)
