# Launcher artwork

`artwork.png` is the transparent source generated with the built-in image-generation tool on
20 October 2026, editing the previous `launcher_brand.png` at the user's request. The yellow
enclosing ring is removed; the guitar, setlist and JAM de BLUES lettering retain the brand's identity.

Run `./scripts/update-launcher-icons.ps1` on Windows to reproduce the adaptive foreground and both
legacy launcher variants at mdpi through xxxhdpi. The adaptive artwork fits inside the 66/108 safe
circle; its background remains `#111318`. Legacy icons use a larger rendition on the same indigo
background. The round variant has transparent corners.

## Generation prompt

Use case: precise-object-edit / logo-brand refinement. Edit target: the supplied BB Blues Jam Android launcher artwork. Remove the entire thick yellow circular ring and the black circular badge behind it. Redraw and polish the central artwork: retain the recognizable left-leaning hollow-body blues electric guitar, setlist paper at the lower right and the exact cream lettering 'JAM de BLUES' in the upper right. The overall compact arrangement should closely follow the original. Professional sharp flat illustration, elegant bold cream contours, simplified guitar details that survive small icon sizes, clean fretboard, balanced symmetrical hardware, tasteful tiny warm amber accents only on guitar knobs and two checkmarks. Strong bold slab lettering with consistent shapes and spacing, no fine noise, no faux distressed texture. Guitar dark indigo #111318 with cream #FFF9EB outlines, setlist cream with dark lines, lettering cream. Make the dark guitar silhouette opaque so it is visible on light backgrounds as well. Entire exterior is genuinely transparent, including the space formerly filled by the ring. NO circles, no circular outline, no enclosing badge, no yellow background or yellow perimeter, no sticker edge around the full composition, no shadow or glow. Keep the full guitar including headstock, whole paper and all lettering fully visible with generous transparent margin; centered compact cohesive emblem. This is actual production launcher art, not a mockup. Output one clean square transparent image.
