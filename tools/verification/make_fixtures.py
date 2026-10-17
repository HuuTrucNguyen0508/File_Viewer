#!/usr/bin/env python3
"""Create the phase-one emulator fixtures. Requires Pillow, ffmpeg and 7z."""
import binascii
import urllib.request
import os
from pathlib import Path
import subprocess
import tarfile
import zipfile
from PIL import Image, ImageDraw

root = Path('/tmp/fv-verify/fixtures')
root.mkdir(parents=True, exist_ok=True)
texts = {
    'sample.kt': 'package sample\n\nfun main() {\n    val greeting = "Hello file viewer"\n    println(greeting)\n}\n' + ''.join(f'// verification line {i}\n' for i in range(7, 201)),
    'sample.py': '#!/usr/bin/env python3\nfor number in range(10):\n    print(number)\n',
    'sample.rs': 'fn main() {\n    println!("Hello Rust");\n}\n',
    'sample.json': '{"viewer": "File Viewer", "readOnly": true}\n',
    'script': '#!/usr/bin/env python3\nprint("Extensionless script")\n',
}
for name, text in texts.items():
    (root / name).write_text(text)
(root / 'utf16.txt').write_bytes('UTF-16 fixture\nCafé, 日本語, Ελληνικά\n'.encode('utf-16'))
(root / 'windows1252.txt').write_bytes('Café costs €5\n'.encode('cp1252'))
frames = []
for color, label in [('#3c7a46', 'FRAME 1'), ('#2b45c4', 'FRAME 2')]:
    im = Image.new('RGB', (1000, 700), '#eff1ec')
    d = ImageDraw.Draw(im)
    for x in range(0, 1000, 50):
        d.line((x, 0, x, 700), fill='#d6dbd3')
    d.rectangle((150, 120, 850, 580), fill=color)
    d.text((350, 300), label + ' / File Viewer', fill='white', font_size=32)
    frames.append(im)
frames[0].save(root / '01-photo.jpg')
frames[1].save(root / '02-grid.png')
frames[0].save(root / '03-animation.gif', save_all=True, append_images=frames[1:], duration=500, loop=0)
frames[0].save(root / '04-animation.webp', save_all=True, append_images=frames[1:], duration=500, loop=0)
(root / '05-vector.svg').write_text('<svg xmlns="http://www.w3.org/2000/svg" width="600" height="400"><rect width="600" height="400" fill="#eff1ec"/><circle cx="300" cy="200" r="130" fill="#2b45c4"/><text x="180" y="210" font-size="30" fill="white">SVG fixture</text></svg>')
subprocess.run(['convert', str(root/'01-photo.jpg'), str(root/'06-photo.heic')], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

def pdf(path, pages):
    objects = [b'<< /Type /Catalog /Pages 2 0 R >>', b'']
    font = 3
    objects.append(b'<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>')
    kids = []
    for i in range(pages):
        page_id = len(objects) + 1
        kids.append(f'{page_id} 0 R')
        content_id = page_id + 1
        objects.append(f'<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 {font} 0 R >> >> /Contents {content_id} 0 R >>'.encode())
        content = f'BT /F1 28 Tf 50 720 Td (File Viewer - page {i+1}) Tj /F1 14 Tf 0 -40 Td (Pinch, scroll and jump verification.) Tj ET'.encode()
        objects.append(f'<< /Length {len(content)} >>\nstream\n'.encode() + content + b'\nendstream')
    objects[1] = f'<< /Type /Pages /Count {pages} /Kids [{" ".join(kids)}] >>'.encode()
    data = bytearray(b'%PDF-1.4\n')
    offsets = [0]
    for n, obj in enumerate(objects, 1):
        offsets.append(len(data))
        data.extend(f'{n} 0 obj\n'.encode() + obj + b'\nendobj\n')
    xref = len(data)
    data.extend(f'xref\n0 {len(offsets)}\n0000000000 65535 f \n'.encode())
    for offset in offsets[1:]:
        data.extend(f'{offset:010} 00000 n \n'.encode())
    data.extend(f'trailer\n<< /Size {len(offsets)} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n'.encode())
    path.write_bytes(data)
pdf(root / 'long.pdf', 120)
(root/'window.pdf').write_bytes(b'x'*512+(root/'long.pdf').read_bytes()+b'y'*512)
rar_url = 'https://raw.githubusercontent.com/libarchive/libarchive/master/libarchive/test/test_read_format_rar.rar.uu'
rar_lines = urllib.request.urlopen(rar_url, timeout=20).read().decode().splitlines()
rar_start = next(i for i,line in enumerate(rar_lines) if line.startswith('begin '))+1
(root/'sample.rar').write_bytes(b''.join(binascii.a2b_uu(line) for line in rar_lines[rar_start:] if line and line != 'end'))
with zipfile.ZipFile(root / 'bundle.zip', 'w', zipfile.ZIP_DEFLATED) as z:
    for name in texts:
        z.write(root / name, 'src/' + name)
    z.writestr('../display-only.txt', 'Entry paths must never become disk paths.')
with tarfile.open(root / 'code.tar.gz', 'w:gz') as t:
    for name in texts:
        t.add(root / name, arcname='src/' + name)
with zipfile.ZipFile(root / 'document.docx', 'w') as z:
    z.writestr('[Content_Types].xml', '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"/>')
    z.writestr('word/document.xml', '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>Document fixture</w:t></w:r></w:p></w:body></w:document>')
for name, args in [('bundle.7z', ['-t7z']), ('encrypted.zip', ['-tzip', '-pfixture-password'])]:
    subprocess.run(['7z', 'a', *args, str(root/name), str(root/'sample.py')], check=True, stdout=subprocess.DEVNULL)

def ffmpeg(name, args):
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', *args, str(root/name)], check=True)
ffmpeg('video.mp4', ['-f','lavfi','-i','testsrc2=size=640x360:rate=24','-f','lavfi','-i','sine=frequency=440','-t','8','-c:v','libx264','-pix_fmt','yuv420p','-c:a','aac'])
ffmpeg('audio.wav', ['-f','lavfi','-i','sine=frequency=440','-t','8'])
ffmpeg('audio.mp3', ['-i',str(root/'audio.wav'),'-i',str(root/'01-photo.jpg'),'-map','0:a','-map','1:v','-c:a','libmp3lame','-c:v','copy','-id3v2_version','3','-metadata','title=Drafting soundtrack','-metadata','artist=File Viewer','-metadata','album=Phase one fixtures'])
ffmpeg('unsupported.mkv', ['-f','lavfi','-i','testsrc2=size=320x240:rate=8','-t','2','-c:v','ffv1'])
with (root / 'random-200mb.bin').open('wb') as f:
    for _ in range(200):
        f.write(os.urandom(1024*1024))
print(root)
