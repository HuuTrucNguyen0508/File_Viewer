"""Drive the real phase-two viewers. Set FV_DEVICE_FLAGS from T3 device_open first."""
import sys
import subprocess
import time
from pathlib import Path
from emulator import adb, open_file, shot, tap, snapshot, back, OUT, DEVICE, FLAGS, ADB


def opened(file, name):
    open_file(file)
    time.sleep(1)
    return shot('phase2-final-' + name)


def swipe(x1, y1, x2, y2):
    adb('shell', 'input', 'swipe', str(x1), str(y1), str(x2), str(y2), '400')
    time.sleep(.5)


def gallery():
    for ext in ['docx','xlsx','pptx','odt','ods','odp','epub','rtf','doc','xls','ppt','otf','ttf','woff2','apk','md','html','csv','tsv','json','jsonl']:
        opened('phase2.' + ext, ext + '-light')
    for name in ['semicolon.csv','deep.json','huge.json','invalid.json','chain.pem','expired.pem','private.pem','leaf.der','public-key.pem','xxe.docx','xxe.epub','traversal.docx']:
        opened('phase2-' + name, name.replace('.', '-') + '-light')


def interactions():
    opened('phase2.xlsx', 'xlsx-tab1')
    adb('shell','input','tap','460','1734')
    time.sleep(.5)
    shot('phase2-final-xlsx-tab2')
    opened('phase2.epub', 'epub-chapter1')
    tap('Next chapter')
    shot('phase2-final-epub-chapter2')
    tap('Second walk')
    shot('phase2-final-epub-nav')
    tap('First walk')
    opened('phase2.csv', 'csv-initial')
    tap('Quoted')
    shot('phase2-final-csv-quoted-row')
    back()
    swipe(900, 1150, 200, 1150)
    shot('phase2-final-csv-horizontal')
    swipe(500, 1600, 500, 850)
    shot('phase2-final-csv-both-scrolls')
    opened('phase2-normal.db', 'sqlite-objects')
    tap('observations')
    shot('phase2-final-sqlite-rows')
    swipe(900, 1150, 200, 1150)
    swipe(500, 1500, 500, 800)
    shot('phase2-final-sqlite-both-scrolls')
    tap('Next page')
    shot('phase2-final-sqlite-page2')
    tap('Schema')
    shot('phase2-final-sqlite-schema')
    opened('phase2-blob.db', 'sqlite-blob-objects')
    tap('payloads')
    shot('phase2-final-sqlite-blob')
    wal()


def wal():
    wal_hashes = adb('shell','sha256sum','/sdcard/Download/fv-fixtures/phase2-wal.db','/sdcard/Download/fv-fixtures/phase2-wal.db-wal')
    wal_files = adb('shell','ls','-la','/sdcard/Download/fv-fixtures/phase2-wal.db','/sdcard/Download/fv-fixtures/phase2-wal.db-wal')
    opened('phase2-wal.db', 'sqlite-wal-objects')
    tap('observations')
    tap('Next page')
    tap('Next page')
    swipe(500, 1500, 500, 750)
    swipe(500, 1500, 500, 750)
    shot('phase2-final-sqlite-wal-row')
    open_file('phase2.json')
    after = adb('shell','sha256sum','/sdcard/Download/fv-fixtures/phase2-wal.db','/sdcard/Download/fv-fixtures/phase2-wal.db-wal')
    assert wal_hashes == after, (wal_hashes, after)
    (OUT/'phase2-wal-original.txt').write_text(wal_files + '\nBEFORE\n' + wal_hashes + '\nAFTER\n' + after)


def dark():
    adb('shell','cmd','uimode','night','yes')
    try:
        for ext in ['docx','xlsx','epub','ttf','apk','md','html','csv','json']:
            opened('phase2.'+ext, ext+'-dark')
        opened('phase2-chain.pem', 'certificate-dark')
        opened('phase2-normal.db', 'sqlite-dark-objects')
        tap('observations')
        shot('phase2-final-sqlite-dark')
        adb('shell','settings','put','system','accelerometer_rotation','0')
        adb('shell','settings','put','system','user_rotation','1')
        time.sleep(1)
        shot('phase2-final-sqlite-landscape')
    finally:
        adb('shell','settings','put','system','user_rotation','0')
        adb('shell','settings','put','system','accelerometer_rotation','1')
        adb('shell','cmd','uimode','night','no')


def font_keyboard():
    previous = adb('shell','settings','get','secure','show_ime_with_hard_keyboard').strip()
    previous_ime = adb('shell','settings','get','secure','default_input_method').strip()
    try:
        adb('shell','settings','put','secure','show_ime_with_hard_keyboard','1')
        opened('phase2.ttf', 'font-keyboard-initial')
        for _ in range(8):
            if any(n.get('editable') and n.get('hittable') and n['rect']['y'] + n['rect']['height'] < 1700 for n in snapshot()['nodes']): break
            swipe(500, 1550, 500, 650)
        field = next(n for n in snapshot()['nodes'] if n.get('editable') and n.get('hittable') and n['rect']['y'] + n['rect']['height'] < 1700)
        subprocess.run(DEVICE + ['fill', '@' + field['ref'], 'River heron 2026', '--settle'] + FLAGS, check=True)
        adb('shell','ime','set','com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME')
        time.sleep(1)
        entry = shot('phase2-final-font-keyboard')
        assert any(n.get('value') == 'River heron 2026' for n in entry['nodes'])
        back()
        adb('shell','settings','put','system','accelerometer_rotation','0')
        adb('shell','settings','put','system','user_rotation','1')
        time.sleep(1)
        rotated = shot('phase2-final-font-rotation')
        assert any(n.get('value') == 'River heron 2026' for n in rotated['nodes'])
    finally:
        adb('shell','settings','put','system','user_rotation','0')
        adb('shell','settings','put','system','accelerometer_rotation','1')
        adb('shell','ime','set',previous_ime)
        if previous == 'null': adb('shell','settings','delete','secure','show_ime_with_hard_keyboard')
        else: adb('shell','settings','put','secure','show_ime_with_hard_keyboard',previous)


def uri():
    adb('shell','cmd','appops','set','--uid','com.fileviewer.app','MANAGE_EXTERNAL_STORAGE','deny')
    try:
        for name in ['phase2-normal.db','phase2-wal.db','phase2.ttf','phase2.apk','phase2.docx']:
            adb('shell','am','start','-W','-n','com.fileviewer.fixture/.DeviceProbe$Sender','--es','mode','send','--es','files',name,'--es','delivery','new')
            time.sleep(2)
            shot('phase2-final-uri-'+name.replace('.','-'))
    finally:
        adb('shell','cmd','appops','set','--uid','com.fileviewer.app','MANAGE_EXTERNAL_STORAGE','allow')


def extras():
    try:
        opened('phase2-over32mb.json','json-over32mb')
    except subprocess.CalledProcessError as error:
        (OUT/'phase2-final-json-over32mb.json').write_bytes(error.output)
        with (OUT/'phase2-final-json-over32mb.png').open('wb') as output:
            subprocess.run(ADB+['exec-out','screencap','-p'], stdout=output, check=True)
    files=['/sdcard/Download/fv-fixtures/phase2-journal.db','/sdcard/Download/fv-fixtures/phase2-journal.db-journal']
    before=adb('shell','sha256sum',*files)
    opened('phase2-journal.db','sqlite-journal')
    tap('evidence')
    shot('phase2-final-sqlite-journal-row')
    opened('phase2-chain.pem','chain-scroll-initial')
    after=adb('shell','sha256sum',*files)
    assert before==after
    (OUT/'phase2-journal-original.txt').write_text('BEFORE\n'+before+'AFTER\n'+after)
    for _ in range(5): swipe(500,1500,500,600)
    shot('phase2-final-chain-second-cert')


def rotations():
    adb('shell','settings','put','system','accelerometer_rotation','0')
    try:
        for ext in ['docx','xlsx','epub','apk','md','html','csv','json']:
            adb('shell','settings','put','system','user_rotation','0')
            time.sleep(.4)
            open_file('phase2.'+ext)
            time.sleep(1)
            if ext == 'epub': tap('Next chapter')
            adb('shell','settings','put','system','user_rotation','1')
            time.sleep(1)
            shot('phase2-final-'+ext+'-rotation')
    finally:
        adb('shell','settings','put','system','user_rotation','0')
        adb('shell','settings','put','system','accelerometer_rotation','1')


if __name__ == '__main__':
    {'gallery':gallery, 'interactions':interactions, 'dark':dark, 'font':font_keyboard, 'wal':wal, 'uri':uri, 'extras':extras, 'rotations':rotations}[sys.argv[1]]()
