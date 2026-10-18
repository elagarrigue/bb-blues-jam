from pathlib import Path
import html
import re

root = Path(__file__).resolve().parent
notes = (root / 'notas-presentador.md').read_text(encoding='utf-8')
sections = re.split(r'^## Slide \d+ · ', notes, flags=re.M)[1:]
blocks = []
for section in sections:
    title, body = section.split('\n', 1)
    paragraphs = re.split(r'\n\s*\n', body.strip())
    rendered = []
    for paragraph in paragraphs:
        value = html.escape(paragraph.replace('\n', ' '), quote=False)
        value = re.sub(r'\*\*(.+?)\*\*', r'<strong>\1</strong>', value)
        rendered.append('<p>' + value + '</p>')
    blocks.append('<aside class="speaker-notes" aria-label="Notas del presentador"><h3>' + html.escape(title) + '</h3>\n        ' + '\n        '.join(rendered) + '</aside>')
entry = root / 'dist/index.html'
source = entry.read_text(encoding='utf-8')
counter = iter(blocks)
updated, count = re.subn(r'<aside class="speaker-notes"[^>]*>.*?</aside>', lambda match: next(counter), source, flags=re.S)
assert count == len(blocks), (count, len(blocks))
entry.write_text(updated, encoding='utf-8')
print(f'Updated all {count} speaker notes from notas-presentador.md')
