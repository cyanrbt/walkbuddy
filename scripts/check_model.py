#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""只读检查精确权重、官方许可记录、输入输出、元数据及90槽标签。"""
from pathlib import Path
import hashlib,json,struct,zipfile
root=Path(__file__).resolve().parents[1]
p=root/'app/src/main/assets/efficientdet_lite0_v1.tflite';b=p.read_bytes()
expected='2e04c53bfeac0ac2a30c057c7e2a777594ce39baaac35a92f74fb1e8c4fc4e0b'
assert len(b)==4563519 and hashlib.sha256(b).hexdigest()==expected
card=json.loads((root/'docs/证据/官方模型发行.json').read_text())
assert card['licenseName']=='Apache 2.0' and card['id']==992 and card['versionId']==1146 and card['versionNumber']==1
labels=zipfile.ZipFile(p).read('labelmap.txt').decode().splitlines()
assert len(labels)==90 and labels[0]=='person'
assert (root/'app/src/main/assets/labelmap.txt').read_text().splitlines()==labels
def u32(buf,pos):return struct.unpack_from('<I',buf,pos)[0]
def field(buf,pos,i):
    vt=pos-struct.unpack_from('<i',buf,pos)[0];entry=vt+4+2*i
    off=struct.unpack_from('<H',buf,entry)[0] if entry<vt+struct.unpack_from('<H',buf,vt)[0] else 0
    return pos+off if off else None
def indirect(buf,pos):return pos+u32(buf,pos)
def vector(buf,pos):
    v=indirect(buf,pos);return [v+4+i*4 for i in range(u32(buf,v))]
def text(buf,pos):
    if pos is None:return None
    q=indirect(buf,pos);return buf[q+4:q+4+u32(buf,q)].decode()
r=u32(b,0);sg=indirect(b,vector(b,field(b,r,2))[0]);tensors=vector(b,field(b,sg,0))
def tensor(index):
    t=indirect(b,tensors[index]);shape=[struct.unpack_from('<i',b,pos)[0] for pos in vector(b,field(b,t,0))];typ=field(b,t,1)
    return {'index':index,'name':text(b,field(b,t,3)),'shape':shape,'type':{0:'FLOAT32',3:'UINT8'}.get(b[typ] if typ else 0,'其他')}
inputs=[tensor(u32(b,pos)) for pos in vector(b,field(b,sg,1))];outputs=[tensor(u32(b,pos)) for pos in vector(b,field(b,sg,2))]
assert inputs[0]['shape']==[1,320,320,3] and inputs[0]['type']=='UINT8'
assert len(outputs)==4  # 本模型序列化输出shape为空；分配后的真实shape见Android RuntimeProbe
assert all(x['type']=='FLOAT32' for x in outputs)
metadata={};buffers=vector(b,field(b,r,4))
for pos in vector(b,field(b,r,6)):
    m=indirect(b,pos)
    if text(b,field(b,m,0))=='TFLITE_METADATA':
        buf=indirect(b,buffers[u32(b,field(b,m,1))]);v=indirect(b,field(b,buf,0));data=b[v+4:v+4+u32(b,v)];mr=u32(data,0)
        metadata={key:text(data,field(data,mr,i)) for key,i in [('name',0),('version',2),('author',4),('license',5)]}
assert 'Apache' in metadata['license']
print(json.dumps({'sha256':expected,'bytes':len(b),'license_evidence':{'official_license':card['licenseName'],'instance_id':992,'versionId':1146,'versionNumber':1},'input':inputs,'outputs':outputs,'metadata':metadata,'label_slots':len(labels),'labels':labels},ensure_ascii=False,indent=2))
