#!/usr/bin/env python3
"""Read official public portal metadata; never downloads/imports location payloads."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import json
from html.parser import HTMLParser
from pathlib import Path
import re
import urllib.parse
import urllib.request
from audit_smoking_sources import norm

class Links(HTMLParser):
    def __init__(self):
        super().__init__(); self.current=None; self.links={}
    def handle_starttag(self,tag,attrs):
        if tag=='a':
            href=dict(attrs).get('href','')
            match=re.search(r'/data/(\d+)/fileData.do',href)
            self.current=[match[1],[]] if match else None
    def handle_data(self,data):
        if self.current: self.current[1].append(data)
    def handle_endtag(self,tag):
        if tag=='a' and self.current:
            self.links[self.current[0]]=norm(' '.join(self.current[1]));self.current=None

def get(url):
    with urllib.request.urlopen(url,timeout=30) as response: return response.read()

def key(value):
    return re.sub(r'[\s_.]+','',re.sub(r'_?20\d{6}.*$','',norm(value).removesuffix('.csv')))

def resolve(data_dir,checked_on):
    parser=Links()
    url='https://www.data.go.kr/tcs/dss/selectDataSetList.do?'+urllib.parse.urlencode(dict(dType='FILE',keyword='흡연구역',perPage=100,currentPage=1))
    parser.feed(get(url).decode('utf-8'))
    paths=[p for p in data_dir.rglob('*.csv') if '흡연' in norm(p.name)]
    needed={i for i,title in parser.links.items() if any(key(p.name)==key(title) for p in paths)}
    needed.add('15142571') # Separate current Jongno dataset, no coordinate inference.
    def metadata(i):
        try:
            data=json.loads(get(f'https://www.data.go.kr/catalog/{i}/fileData.json'))
            return i,{k:data.get(k) for k in ('name','alternateName','url','license','dateCreated','dateModified','datePublished','creator','spatialCoverage','encodingFormat')}|{'checked_on':checked_on,'metadata_url':f'https://www.data.go.kr/catalog/{i}/fileData.json'}
        except Exception as error: return i,{'error':type(error).__name__,'checked_on':checked_on}
    with ThreadPoolExecutor(max_workers=4) as pool: metas=dict(pool.map(metadata,sorted(needed)))
    catalog={norm(p.name):{'match_basis':'exact dataset title ignoring whitespace, underscores, filename date', 'datasets':[metas[i] for i,title in parser.links.items() if key(p.name)==key(title)]} for p in paths}
    return catalog,metas['15142571']

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--data-dir',type=Path,required=True);p.add_argument('--output-dir',type=Path,required=True);p.add_argument('--checked-on',required=True);a=p.parse_args()
    catalog,jongno=resolve(a.data_dir,a.checked_on);a.output_dir.mkdir(parents=True,exist_ok=True)
    for name,data in [('official-source-catalog.json',catalog),('jongno-current-dataset-metadata.json',jongno)]:
        (a.output_dir/name).write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(f'Resolved {sum(bool(x["datasets"]) for x in catalog.values())}/{len(catalog)} distinct municipal filenames; metadata only, no location import.')
