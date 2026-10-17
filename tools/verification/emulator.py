"""Small emulator driver used by the phase-one review. Run from the repository root."""
import json
import os
import shlex
from pathlib import Path
import subprocess
import time

ADB = [str(Path.home()/'Android/Sdk/platform-tools/adb'), '-s', 'emulator-5554']
DEVICE = [str(Path.home()/'.t3/userdata/device/bin/agent-device')]
FLAGS = shlex.split(os.environ.get('FV_DEVICE_FLAGS', '--platform android --serial emulator-5554 --config '+str(Path.home()/'.t3/userdata/device/hosts/25bf8e1a2393f1108d37029b.json')+' --session t3-454e437a389063f801888754'))
OUT = Path('/tmp/fv-verify')

def adb(*args):
    command = ['shell', shlex.join(args[1:])] if args and args[0] == 'shell' else list(args)
    return subprocess.check_output(ADB + command).decode(errors='replace')

def open_file(name, mime='application/octet-stream'):
    print(adb('shell','am','start','-W','-a','android.intent.action.VIEW','-d','file:///sdcard/Download/fv-fixtures/'+name,'-t',mime,'-n','com.fileviewer.app/.MainActivity'))
    time.sleep(0.8)

def snapshot():
    result = subprocess.check_output(DEVICE + ['snapshot','-i','--json'] + FLAGS)
    return json.loads(result)['data']

def shot(name):
    time.sleep(0.4)
    data = snapshot()
    (OUT/(name+'.json')).write_text(json.dumps(data, indent=2))
    with (OUT/(name+'.png')).open('wb') as output:
        subprocess.run(ADB+['exec-out','screencap','-p'], stdout=output, check=True)
    print(name, [(n.get('label','')[:100], n.get('selected')) for n in data['nodes'] if n.get('label')])
    return data

def tap(label):
    nodes = snapshot()['nodes']
    found = [n for n in nodes if n.get('label') == label or n.get('value') == label]
    if not found:
        found = [n for n in nodes if label in n.get('label','')]
    if not found:
        raise ValueError((label, [n.get('label') for n in nodes]))
    node = found[-1]
    while not node.get('hittable') and 'parentIndex' in node:
        node = next(n for n in nodes if n['index'] == node['parentIndex'])
    rect = node['rect']
    adb('shell','input','tap',str(int(rect['x']+rect['width']/2)),str(int(rect['y']+rect['height']/2)))
    time.sleep(0.3)

def fill(text):
    node = next(n for n in snapshot()['nodes'] if n.get('editable'))
    rect = node['rect']
    adb('shell','input','tap',str(int(rect['x']+rect['width']/2)),str(int(rect['y']+rect['height']/2)))
    adb('shell','input','keyevent','123')
    adb('shell','input','keyevent',*(['67']*max(1,len(node.get('value','')))))
    adb('shell','input','text',text.replace(' ','%s'))
    time.sleep(0.4)

def back():
    adb('shell','input','keyevent','4')
    time.sleep(0.3)

def pinch(factor='2'):
    print(adb('shell','am','instrument','-w','-e','mode','pinch','-e','factor',str(factor),'com.fileviewer.fixture/.DeviceProbe'))
    time.sleep(0.5)
