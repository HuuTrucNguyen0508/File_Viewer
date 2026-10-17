"""Generate phase-two fixtures without office applications or network access."""
from pathlib import Path
from io import BytesIO
import csv
import json
import shutil
import sqlite3
import subprocess
import zipfile
from PIL import Image, ImageDraw
from fontTools.ttLib import TTFont

OUT = Path('/tmp/fv-verify/fixtures')
OUT.mkdir(parents=True, exist_ok=True)

def write(name, text):
    (OUT / name).write_text(text)

def package(name, parts):
    with zipfile.ZipFile(OUT / name, 'w', zipfile.ZIP_DEFLATED) as archive:
        for path, body in parts.items():
            archive.writestr(path, body)

def rels(*items):
    return '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">' + ''.join(
        f'<Relationship Id="{i}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/{kind}" Target="{target}"/>'
        for i, kind, target in items) + '</Relationships>'

img = Image.new('RGB', (480, 160), '#215d67')
ImageDraw.Draw(img).text((24, 70), 'EMBEDDED IMAGE / PHASE 2', fill='white')
image = BytesIO()
img.save(image, format='PNG')
png = image.getvalue()
(OUT / 'phase2-image.png').write_bytes(png)
W = 'xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"'
R = 'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"'
A = 'xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"'
P = 'xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"'
S = 'xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"'
word = f'''<w:document {W} {R} {A}><w:body>
<w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Phase 2 field notes</w:t></w:r></w:p>
<w:p><w:r><w:rPr><w:b/></w:rPr><w:t>Bold field note</w:t></w:r><w:r><w:t> with plain text.</w:t></w:r></w:p>
<w:p><w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr><w:r><w:t>A list item</w:t></w:r></w:p>
<w:tbl><w:tr><w:tc><w:p><w:r><w:t>Species</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>Count</w:t></w:r></w:p></w:tc></w:tr>
<w:tr><w:tc><w:p><w:r><w:t>Heron</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>12</w:t></w:r></w:p></w:tc></w:tr></w:tbl>
<w:p><w:r><w:drawing><a:blip r:embed="image"/></w:drawing></w:r></w:p></w:body></w:document>'''
word_parts = {'_rels/.rels': rels(('main','officeDocument','word/document.xml')), 'word/document.xml': word,
 'word/_rels/document.xml.rels': rels(('image','image','media/image.png'),('style','styles','styles.xml')),
 'word/styles.xml': f'<w:styles {W}><w:style w:styleId="Heading1"><w:name w:val="heading 1"/></w:style></w:styles>',
 'word/media/image.png': png, '[Content_Types].xml': '<Types/>'}
package('phase2.docx', word_parts)
xxe = '<!DOCTYPE document [<!ENTITY xxe SYSTEM "file:///sdcard/Download/fv-fixtures/phase2-secret.txt">]>'
write('phase2-secret.txt', 'XXE_SECRET_MUST_NEVER_APPEAR')
package('phase2-xxe.docx', {**word_parts, 'word/document.xml': xxe + f'<w:document {W}><w:body><w:p><w:r><w:t>&xxe;</w:t></w:r></w:p></w:body></w:document>'})
package('phase2-traversal.docx', {**word_parts, 'word/_rels/document.xml.rels': rels(('image','image','../../phase2-image.png'))})
rows = ''.join(f'<row r="{i}"><c r="A{i}" t="inlineStr"><is><t>{"Date" if i == 1 else "Observation " + str(i)}</t></is></c><c r="B{i}" s="0"><v>{45292+i}</v></c><c r="C{i}"><v>{i}</v></c></row>' for i in range(1, 2010))
package('phase2.xlsx', {'xl/workbook.xml': f'<workbook {S} {R}><sheets><sheet name="Observations" r:id="s1"/><sheet name="Summary" r:id="s2"/></sheets></workbook>',
 'xl/_rels/workbook.xml.rels': rels(('s1','worksheet','worksheets/sheet1.xml'),('s2','worksheet','worksheets/sheet2.xml'),('st','styles','styles.xml')),
 'xl/styles.xml': f'<styleSheet {S}><cellXfs><xf numFmtId="14"/></cellXfs></styleSheet>',
 'xl/worksheets/sheet1.xml': f'<worksheet {S}><sheetData>{rows}</sheetData></worksheet>',
 'xl/worksheets/sheet2.xml': f'<worksheet {S}><sheetData><row r="1"><c t="inlineStr"><is><t>Second sheet verified</t></is></c></row></sheetData></worksheet>'})
package('phase2.pptx', {'ppt/presentation.xml': f'<p:presentation {P} {R}><p:sldIdLst><p:sldId r:id="s1"/><p:sldId r:id="s2"/></p:sldIdLst></p:presentation>',
 'ppt/_rels/presentation.xml.rels': rels(('s1','slide','slides/slide1.xml'),('s2','slide','slides/slide2.xml')),
 **{f'ppt/slides/slide{i}.xml': f'<p:sld {P} {A} {R}><p:sp><p:ph type="title"/><a:p><a:r><a:t>Field slide {i}</a:t></a:r></a:p></p:sp><p:sp><a:p><a:r><a:t>Observation with embedded image</a:t></a:r></a:p></p:sp><p:pic><a:blip r:embed="image"/></p:pic></p:sld>' for i in (1,2)},
 **{f'ppt/slides/_rels/slide{i}.xml.rels': rels(('image','image','../media/image.png')) for i in (1,2)}, 'ppt/media/image.png': png})
ODF = ' '.join(f'xmlns:{prefix}="{uri}"' for prefix,uri in {
 'office':'urn:oasis:names:tc:opendocument:xmlns:office:1.0','text':'urn:oasis:names:tc:opendocument:xmlns:text:1.0',
 'table':'urn:oasis:names:tc:opendocument:xmlns:table:1.0','draw':'urn:oasis:names:tc:opendocument:xmlns:drawing:1.0',
 'presentation':'urn:oasis:names:tc:opendocument:xmlns:presentation:1.0','xlink':'http://www.w3.org/1999/xlink'}.items())
for ext,body,kind in [
 ('odt','<office:text><text:h text:outline-level="1">OpenDocument notes</text:h><text:p>A portable field notebook.</text:p><draw:frame><draw:image xlink:href="Pictures/image.png"/></draw:frame></office:text>','text'),
 ('ods','<office:spreadsheet><table:table table:name="Field data"><table:table-row><table:table-cell><text:p>Species</text:p></table:table-cell><table:table-cell><text:p>Count</text:p></table:table-cell></table:table-row><table:table-row table:number-rows-repeated="3"><table:table-cell><text:p>Heron</text:p></table:table-cell><table:table-cell office:value-type="float" office:value="12"/></table:table-row></table:table></office:spreadsheet>','spreadsheet'),
 ('odp','<office:presentation><draw:page><draw:frame presentation:class="title"><text:p>OpenDocument slide</text:p></draw:frame><text:p>A picture from the field</text:p><draw:frame><draw:image xlink:href="Pictures/image.png"/></draw:frame></draw:page></office:presentation>','presentation')]:
 package('phase2.'+ext, {'mimetype':'application/vnd.oasis.opendocument.'+kind,'content.xml':f'<office:document-content {ODF}><office:body>{body}</office:body></office:document-content>','Pictures/image.png':png})
epub = {'mimetype':'application/epub+zip','META-INF/container.xml':'<container><rootfiles><rootfile full-path="OPS/book.opf"/></rootfiles></container>',
 'OPS/book.opf':'<package><metadata><title>Field guide</title></metadata><manifest><item id="c1" href="one.xhtml" media-type="application/xhtml+xml"/><item id="c2" href="two.xhtml" media-type="application/xhtml+xml"/><item id="nav" href="nav.xhtml" properties="nav"/></manifest><spine><itemref idref="c1"/><itemref idref="c2"/></spine></package>',
 'OPS/nav.xhtml':'<html><body><nav epub:type="toc"><ol><li><a href="one.xhtml">First walk</a></li><li><a href="two.xhtml">Second walk</a></li></ol></nav></body></html>',
 'OPS/one.xhtml':'<!DOCTYPE html><html><head><title>First</title></head><body><h1>First walk</h1><p>A quiet morning by the river.</p><img src="images/image.png"/><img src="../../phase2-image.png"/><script>document.body.textContent="SCRIPT RAN"</script></body></html>',
 'OPS/two.xhtml':'<html><body><h1>Second walk</h1><p>The herons returned.</p></body></html>', 'OPS/images/image.png':png}
package('phase2.epub', epub)
package('phase2-xxe.epub', {**epub, 'OPS/book.opf': '<!DOCTYPE package [<!ENTITY xxe SYSTEM "file:///sdcard/Download/fv-fixtures/phase2-secret.txt">]>'+epub['OPS/book.opf'].replace('Field guide','&xxe;')})
write('phase2.rtf', r'{\rtf1\ansi Field notes\par \b Bold heron\b0\par Caf\'e9 \u937?\par\trowd Species\cell Count\cell\row}')
(OUT/'phase2.doc').write_bytes(bytes.fromhex('d0cf11e0a1b11ae1')+b'\x00'*504+b'Legacy field notes: twelve herons at sunrise.\x00')
for ext in ['xls','ppt']:
 shutil.copyfile(OUT/'phase2.doc', OUT/('phase2.'+ext))
write('phase2.md', '''---
title: Field notes
author: Verification
---
# Field notebook

- [x] Find the heron
- [ ] Count again

| Species | Count |
| --- | ---: |
| Heron | 12 |

![Field image](phase2-image.png)

<div id="raw">Raw HTML stays literal</div>

```python
print("field notes")
```
''')
write('phase2.html', '''<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><style>body{font:20px sans-serif;background:#f5f6f3;color:#173943;padding:16px}img{max-width:100%}</style></head><body><h1>Offline field notes</h1><p id="status">SCRIPT BLOCKED</p><img src="phase2-image.png"><img src="../phase2-secret.png"><img src="https://example.invalid/network.png"><iframe src="file:///sdcard/Download/fv-fixtures/phase2-secret.txt"></iframe><script>document.getElementById('status').textContent='SCRIPT RAN';fetch('https://example.invalid/')</script></body></html>''')
for name,delimiter in [('phase2.csv',','),('phase2-semicolon.csv',';'),('phase2.tsv','\t')]:
 with (OUT/name).open('w',newline='') as f:
  writer=csv.writer(f,delimiter=delimiter)
  writer.writerow(['Species','Count','Location','Long notes','Observed','Observer','Status'])
  for i in range(1,220): writer.writerow(['Heron',i,'River bank','Quoted\nnewline, "observed"' if i==1 else 'Field note '+str(i),'2026-10-17','Alex','Confirmed'])
write('phase2.json', json.dumps({'observations':[{'species':'Heron','count':12,'seen':True,'notes':None}], 'location':{'river':'Seine'}}))
write('phase2-deep.json','['*205+'0'+']'*205)
write('phase2-huge.json','['+','.join('0' for _ in range(200010))+']')
write('phase2-invalid.json','{"bird": "heron",\n "count": }')
write('phase2.jsonl','{"species":"heron"}\n{"count":12}\n')
for mode in ('normal','wal'):
 file=OUT/f'phase2-{mode}.db'
 for suffix in ('','-wal','-shm'): Path(str(file)+suffix).unlink(missing_ok=True)
 db=sqlite3.connect(file)
 db.execute('CREATE TABLE observations (id INTEGER PRIMARY KEY, species TEXT, count INTEGER, place TEXT, notes TEXT, observed TEXT, observer TEXT, payload BLOB)')
 db.executemany('INSERT INTO observations VALUES (?,?,?,?,?,?,?,?)',[(i,'Heron',i,'River bank','Field note '+str(i),'2026-10-17','Alex',b'\x00'*32) for i in range(1,225)])
 db.execute('CREATE VIEW summary AS SELECT species, count(*) AS sightings FROM observations GROUP BY species')
 db.commit()
 if mode=='wal':
  db.execute('PRAGMA journal_mode=WAL')
  db.execute('PRAGMA wal_autocheckpoint=0')
  db.execute("INSERT INTO observations VALUES (225,'WAL_ONLY_HERON',225,'Delta','Uncheckpointed','2026-10-17','Sam',NULL)")
  db.commit()
  backup={suffix:Path(str(file)+suffix).read_bytes() for suffix in ('','-wal')}
 db.close()
 if mode=='wal':
  for suffix,data in backup.items(): Path(str(file)+suffix).write_bytes(data)
blob_file=OUT/'phase2-blob.db'
blob_file.unlink(missing_ok=True)
with sqlite3.connect(blob_file) as db:
 db.execute('CREATE TABLE payloads (name TEXT, data BLOB, long_text TEXT)')
 db.execute("INSERT INTO payloads VALUES ('large', zeroblob(5000000), ?)", ('x'*3000000,))
(OUT/'phase2-over32mb.json').write_text('[0,'+' '*((33<<20)-5)+'0]')
journal=OUT/'phase2-journal.db'
journal.unlink(missing_ok=True)
with sqlite3.connect(journal) as db:
 db.execute('CREATE TABLE evidence (value TEXT)')
 db.execute("INSERT INTO evidence VALUES ('Committed row')")
(OUT/'phase2-journal.db-journal').write_bytes(b'not a replayable journal')
font_path=Path('app/src/main/res/font/plex_sans.ttf')
shutil.copyfile(font_path,OUT/'phase2.ttf')
otf = Path('/usr/share/fonts/OTF/PowerlineSymbols.otf')
if otf.exists(): shutil.copyfile(otf, OUT/'phase2.otf')
font=TTFont(font_path)
font.flavor='woff2'
font.save(OUT/'phase2.woff2')
for name in ['chain.pem','expired.pem','leaf.der','public-key.pem']:
 shutil.copyfile(Path('app/src/test/resources/certs')/name, OUT/('phase2-'+name))
write('phase2-private.pem','-----BEGIN PRIVATE KEY-----\nPRIVATE_BODY_MUST_NOT_BE_DECODED\n-----END PRIVATE KEY-----\n')
adb=[str(Path.home()/'Android/Sdk/platform-tools/adb'),'-s','emulator-5554']
path=subprocess.check_output(adb+['shell','pm','path','com.android.settings'],text=True).splitlines()[0].removeprefix('package:').strip()
subprocess.run(adb+['pull',path,str(OUT/'phase2.apk')],check=True)
print('Phase 2 fixtures ready', len(list(OUT.glob('phase2*'))))
