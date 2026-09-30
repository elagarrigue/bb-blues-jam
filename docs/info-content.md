# Info Screen Content

Source of truth for the facts shown on the Info tab. The copy itself is written in Rioplatense
Spanish with *vos* (D-12) by the `info-screen` slice; this file holds only facts and where each one
came from, so no agent has to invent them. Collected 30 September 2026.

| Fact | Value | Source |
|---|---|---|
| Organizer | **Bahía Blanca Blues**, the group of people who organize the jam and the community's other activities | The user |
| What the organizer is | "Comunidad de amantes del blues. Jams, festivales, conciertos, programas de radio." | Instagram bio of @bahiablancablues |
| The jam | A monthly blues jam in Bahía Blanca | The user; project docs |
| Venue | **Not shown in Info.** It can change; each jam carries its own venue (`Jams.lugar`), shown in the next-jam header (D-19) | The user |
| Radio program | **Hideaway**, a radio program by Bahía Blanca Blues, Tuesdays 20:00–22:00 | The user (it is a radio program); schedule from the @bahiablancablues Instagram ("MARTES DE 20 A 22HS") |
| Radio station | Unknown — not in the Instagram or Linktree | Open: ask the user before showing a station or frequency |
| How to join as a musician | Come to the jam and sign up there; the organizer adds you to a song. No account, no sign-up in the app (D-05) | The user |
| Instagram | https://www.instagram.com/bahiablancablues/ | The user |
| YouTube | https://m.youtube.com/channel/UCayS6srPr0FQ2FF4XoEZ-8w (use the non-mobile `www.youtube.com` form in the app). Opens a channel named "Radio Hideaway" (@radiohideaway9182) that carries Bahía Blanca Blues Festival videos — **user to confirm** it is the channel to show | Linktree of @bahiablancablues; channel name seen on the Pixel 5 |
| Linktree | https://linktr.ee/bahiablancablues | Instagram bio |
| Festival | Bahía Blanca Blues Festival, 5th edition (2025/2026 listing) | Linktree — **not** for Info unless the user asks; it dates quickly |

The Instagram content was read through an automated fetch that summarizes the page, not by a
person; the Hideaway schedule should be confirmed by the user before release.
