package com.bbbjam.feature.info

/** The Bahía Blanca Blues social links shown on Info, from `docs/info-content.md`. */
enum class SocialLink(val url: String) {
    INSTAGRAM("https://www.instagram.com/bahiablancablues/"),

    // The www form of the channel link on the Linktree (which uses m.youtube.com).
    YOUTUBE("https://www.youtube.com/channel/UCayS6srPr0FQ2FF4XoEZ-8w"),
    LINKTREE("https://linktr.ee/bahiablancablues"),
}
