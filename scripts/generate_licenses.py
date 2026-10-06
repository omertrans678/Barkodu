from pathlib import Path
from xml.etree import ElementTree as ET
from zipfile import ZipFile
from io import BytesIO
root=Path(__file__).resolve().parents[1]
seen=set();groups={}; notices=[]
ns={'m':'http://maven.apache.org/POM/4.0.0'}
for row in (root/'app/build/license-artifacts.tsv').read_text().splitlines():
    group,name,version,artifact=row.split('\t')
    key=f'{group}:{name}:{version}'
    if key in seen:continue
    seen.add(key)
    poms=list(Path(artifact).parent.parent.rglob('*.pom'))
    licenses=[]
    if poms:
        pom=ET.parse(poms[0]).getroot()
        for lic in pom.findall('m:licenses/m:license',ns):
            licenses.append(((lic.findtext('m:name','',ns)),(lic.findtext('m:url','',ns))))
    groups[key]=licenses
    with ZipFile(artifact) as z:
        archives=[z]
        if 'classes.jar' in z.namelist():archives.append(ZipFile(BytesIO(z.read('classes.jar'))))
        for jar in archives:
            for f in jar.namelist():
                if not f.endswith('/') and any(t in f.upper() for t in ['LICENSE','NOTICE','COPYRIGHT']):
                    content=jar.read(f).decode('utf-8',errors='replace')
                    if len(content)>50:notices.append((key,f,content))
lines=['Third-party Licenses','Generated from resolved runtime artifacts and their published Maven POMs.','Barkodu is licensed under Apache-2.0; dependencies retain their own licenses.','']
for key,licenses in groups.items():
    lines += [key]+[f'  {name} — {url}' for name,url in licenses] if licenses else [key,'  License metadata not supplied in the published POM; see bundled notices below.']
    lines += ['']
lines+=['APACHE LICENSE 2.0','Used by Miuix, AndroidX, Compose, Kotlin and other dependencies identified above.',(root/'app/src/main/assets/miuix-license.txt').read_text()]
unique=set()
for key,f,content in notices:
    if content in unique:continue
    unique.add(content);lines += ['\nBUNDLED NOTICE: '+key+' / '+f,content]
(root/'app/src/main/assets/third-party-licenses.txt').write_text('\n'.join(lines),encoding='utf-8')
print(f'{len(groups)} runtime artifacts, {len(unique)} unique bundled license/notice texts')
print('License types:',sorted(set(name+' '+url for values in groups.values() for name,url in values)))
