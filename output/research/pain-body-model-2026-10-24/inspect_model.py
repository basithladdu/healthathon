from pathlib import Path
import struct,json,hashlib,sys
import numpy as np
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.collections import PolyCollection
base=Path('output/research/pain-body-model-2026-10-24')
b=(Path(sys.argv[1]) if len(sys.argv)>1 else base/'human_posed-original.glb').read_bytes()
n,t=struct.unpack_from('<II',b,12);j=json.loads(b[20:20+n]);bo=20+n+8
ct={5123:'<u2',5125:'<u4',5126:'<f4'}
def acc(i):
 a=j['accessors'][i];v=j['bufferViews'][a['bufferView']];d=np.frombuffer(b,dtype=ct[a['componentType']],count=a['count']*({'VEC3':3,'VEC2':2,'SCALAR':1}[a['type']]),offset=bo+v.get('byteOffset',0)+a.get('byteOffset',0));return d.reshape((-1,3)) if a['type']=='VEC3' else d
p=j['meshes'][0]['primitives'][0];pos=acc(p['attributes']['POSITION']);tri=acc(p['indices']).reshape((-1,3));pts=pos[tri]
fig,axes=plt.subplots(1,4,figsize=(12,9),facecolor='#f5f1e9')
for ax,(title,h,dep,sgn) in zip(axes,[('View from +Z',0,2,1),('View from -Z',0,2,-1),('View from +X',2,0,1),('View from -X',2,0,-1)]):
 order=np.argsort(pts[:,:,dep].mean(1)*sgn);polys=pts[order][:,:,[h,1]];norm=np.cross(pts[:,1]-pts[:,0],pts[:,2]-pts[:,0]);norm/=np.maximum(np.linalg.norm(norm,axis=1,keepdims=True),1e-8);light=np.array([.4,.5,1]);light/=np.linalg.norm(light);shade=.45+.45*np.abs(norm@light);colors=np.stack([shade*.89,shade*.96,shade],1)[order];ax.add_collection(PolyCollection(polys,facecolors=colors,edgecolors='none'));ax.autoscale();ax.set_aspect('equal');ax.set_title(title);ax.set_facecolor('#f5f1e9');ax.axis('off');
fig.tight_layout();fig.savefig(base/(sys.argv[2] if len(sys.argv)>2 else 'human-posed-orthographic-check.png'),dpi=150)
print(json.dumps({'vertices':len(pos),'triangles':len(tri),'actualMin':pos.min(0).tolist(),'actualMax':pos.max(0).tolist(),'sha256':hashlib.sha256(b).hexdigest().upper()},indent=2))
