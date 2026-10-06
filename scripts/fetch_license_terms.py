from pathlib import Path
from urllib.request import urlopen, Request
from html.parser import HTMLParser
import re
class Article(HTMLParser):
    def __init__(self):super().__init__();self.active=False;self.parts=[];self.depth=0
    def handle_starttag(self,tag,attrs):
        a=dict(attrs)
        if tag=='article':self.active=True
        if self.active and tag in ('p','div','h1','h2','h3','li','br'):self.parts.append('\n')
    def handle_endtag(self,tag):
        if tag=='article':self.active=False
    def handle_data(self,data):
        if self.active:self.parts.append(data)
    def text(self):return re.sub(r'\n\s*\n+', '\n\n', ''.join(self.parts)).strip()
asset=Path(__file__).resolve().parents[1]/'app/src/main/assets/third-party-licenses.txt'
with asset.open('a',encoding='utf-8') as output:
    for name,url in [
        ('MaterialKolor MIT License','https://raw.githubusercontent.com/jordond/materialkolor/main/LICENSE'),
        ('libyuv BSD License','https://chromium.googlesource.com/libyuv/libyuv/+/refs/heads/main/LICENSE?format=TEXT'),
        ('ML Kit Terms & Privacy','https://developers.google.com/ml-kit/terms'),
        ('Google APIs Terms of Service','https://developers.google.com/terms'),
        ('Android SDK Terms','https://developer.android.com/studio/terms')]:
        raw=urlopen(Request(url,headers={'User-Agent':'Mozilla/5.0'}),timeout=30).read()
        if 'format=TEXT' in url:
            import base64
            raw=base64.b64decode(raw)
        text=raw.decode('utf-8')
        if '<html' in text.lower() or '<!doctype' in text.lower():
            parser=Article();parser.feed(text);text=parser.text()
        if len(text)<100:raise ValueError('Missing license body: '+name)
        output.write('\n\n'+name+'\nSource: '+url+'\nRetrieved: 2026-10-05\n\n'+text+'\n')
        print(name,len(text))
