"""Check the installable mod contains its API dependencies, recursively."""
from io import BytesIO
from pathlib import Path
import json
from zipfile import ZipFile

properties = dict(line.split('=', 1) for line in Path('gradle.properties').read_text().splitlines() if '=' in line)
jar = Path('build/libs') / f"minemap-{properties['version']}.jar"
mods = {}
classes = set()


def inspect(data):
    with ZipFile(BytesIO(data)) as archive:
        metadata = json.loads(archive.read('fabric.mod.json'))
        mods[metadata['id']] = metadata
        classes.update(name for name in archive.namelist() if name.endswith('.class'))
        for entry in metadata.get('jars', []):
            inspect(archive.read(entry['file']))


inspect(jar.read_bytes())
assert mods['minemap']['version'] == properties['version']
assert mods['minemap']['environment'] == 'client'
assert mods['fabric-api']['version'] == properties['fabric_api_version']
for required in (
    'dev/minemap/MineMapClient.class',
    'dev/minemap/PairingScreen.class',
    'dev/minemap/core/PhoneServer.class',
    'net/fabricmc/fabric/api/client/keymapping/v1/KeyMappingHelper.class',
    'net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientTickEvents.class',
    'net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientLifecycleEvents.class',
    'com/google/zxing/qrcode/QRCodeWriter.class',
):
    assert required in classes, f'Missing bundled class: {required}'
print(f'{jar.name}: bundled Fabric API {mods["fabric-api"]["version"]}, {len(mods)} mod entries; all required classes present.')
