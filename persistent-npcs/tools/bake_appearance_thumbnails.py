"""Deterministic, offline cosmetic contact cards from pinned Hytale blockymodels.

No player data, game execution, model service, or runtime installation dependency.
Pillow/numpy software orthographic rasterizer; reference colors, not live-draft portraits.
"""
import argparse
import hashlib
import io
import json
import math
from pathlib import Path
import zipfile
import numpy as np
import PIL
from PIL import Image, ImageDraw

CATEGORIES = dict(BODY_CHARACTERISTIC="BodyCharacteristics", UNDERWEAR="Underwear",
    SKIN_FEATURE="SkinFeatures", FACE="Faces", EARS="Ears", MOUTH="Mouths",
    EYEBROWS="Eyebrows", FACIAL_HAIR="FacialHair", HAIRCUT="Haircuts", EYES="Eyes",
    PANTS="Pants", OVERPANTS="Overpants", UNDERTOP="Undertops", OVERTOP="Overtops",
    SHOES="Shoes", GLOVES="Gloves", CAPE="Capes", HEAD_ACCESSORY="HeadAccessory",
    FACE_ACCESSORY="FaceAccessory", EAR_ACCESSORY="EarAccessory")
W, H = 184, 298

def vec(d, default=0):
    return np.array([d.get(k, default) for k in "xyz"], dtype=float)

def rotation(q):
    x,y,z,w = (q.get(k, 1 if k == "w" else 0) for k in "xyzw")
    n = x*x+y*y+z*z+w*w
    if n < 1e-12: return np.eye(3)
    s = 2/n
    return np.array([[1-s*(y*y+z*z),s*(x*y-z*w),s*(x*z+y*w)],
        [s*(x*y+z*w),1-s*(x*x+z*z),s*(y*z-x*w)],
        [s*(x*z-y*w),s*(y*z+x*w),1-s*(x*x+y*y)]])

class Baker:
    def __init__(self, archive):
        self.z = zipfile.ZipFile(archive)
        self.sources = {}
        self.cache = {}
        self.gradients = {x["Id"]:x["Gradients"] for x in self.json("Cosmetics/CharacterCreator/GradientSets.json")}
        self.parts = {k:self.json("Cosmetics/CharacterCreator/"+v+".json") for k,v in CATEGORIES.items()}
        self.bones = {}
        self.geometry(self.parts["BODY_CHARACTERISTIC"][0], remember=True)

    def read(self, path):
        if path not in self.cache:
            data = self.z.read(path)
            self.sources[path] = hashlib.sha256(data).hexdigest()
            self.cache[path] = data
        return self.cache[path]

    def json(self, path): return json.loads(self.read(path))

    def texture(self, part):
        variants = part.get("Variants", {})
        variant = variants[sorted(variants)[0]] if variants else {}
        spec = {**part, **variant}
        textures = spec.get("Textures", {})
        if textures:
            t = textures[sorted(textures)[0]]
            path = t if isinstance(t,str) else t["Texture"]
            return np.array(Image.open(io.BytesIO(self.read("Common/"+path))).convert("RGBA")),spec
        path = spec.get("GreyscaleTexture") or spec.get("Texture")
        if not path: raise ValueError("No texture: "+part["Id"])
        pixels = np.array(Image.open(io.BytesIO(self.read("Common/"+path))).convert("RGBA"))
        gradient = self.gradients.get(spec.get("GradientSet"), {})
        if gradient and spec.get("GreyscaleTexture"):
            preferred = {"Skin":"02", "Hair":"BrownLight", "Colored_Cotton":"Blue"}.get(spec.get("GradientSet"))
            key = preferred if preferred in gradient else list(gradient)[min(3,len(gradient)-1)]
            lut = np.array(Image.open(io.BytesIO(self.read("Common/"+gradient[key]["Texture"]))).convert("RGB"))
            # Native greyscale tint gradients are horizontal lookup textures.
            gray = pixels[:,:,0].astype(float)/255
            pixels[:,:,:3] = lut[lut.shape[0]//2, np.minimum(lut.shape[1]-1,(gray*(lut.shape[1]-1)).astype(int))]
        return pixels,spec

    def geometry(self, part, remember=False):
        tex,spec = self.texture(part)
        model = self.json("Common/"+spec["Model"])
        result=[]
        attachment = spec["Model"] != "Characters/Player.blockymodel"
        def walk(nodes, parent_r, parent_p, root=False):
            for node in nodes:
                r = parent_r @ rotation(node.get("orientation",{}))
                p = parent_p + parent_r @ vec(node.get("position",{}))
                if attachment and not remember and node["name"] in self.bones:
                    r,p = self.bones[node["name"]]
                shape=node.get("shape",{})
                # Player.blockymodel's detached Head socket is at its box centre;
                # facial/hair pieces use that socket, not the neck pivot.
                socket_p = p + r @ vec(shape.get("offset",{})) if not attachment else p
                if remember: self.bones[node["name"]] = (r,socket_p)
                if shape.get("visible",True) and shape.get("type") in ("box","quad"):
                    size=vec(shape.get("settings",{}).get("size",{}))
                    stretch=vec(shape.get("stretch",{}),1)
                    offset=vec(shape.get("offset",{}))
                    x,y,z=size/2
                    faces={"front":[[-x,y,z],[-x,-y,z],[x,-y,z],[x,y,z]],
                        "back":[[x,y,-z],[x,-y,-z],[-x,-y,-z],[-x,y,-z]],
                        "left":[[-x,y,-z],[-x,-y,-z],[-x,-y,z],[-x,y,z]],
                        "right":[[x,y,z],[x,-y,z],[x,-y,-z],[x,y,-z]],
                        "top":[[-x,y,-z],[-x,y,z],[x,y,z],[x,y,-z]],
                        "bottom":[[-x,-y,z],[-x,-y,-z],[x,-y,-z],[x,-y,z]]}
                    for face,layout in shape.get("textureLayout",{}).items():
                        if face not in faces: continue
                        vertices=np.array(faces[face],float)
                        if shape["type"]=="quad":
                            if face!="front": continue
                            normal=shape.get("settings",{}).get("normal","+Z")
                            if normal in ("+X", "-X"):
                                vertices=vertices[:,[2,1,0]]*[1,1,-1 if normal=="+X" else 1]
                            elif normal in ("+Y", "-Y"):
                                vertices=vertices[:,[0,2,1]]*[1,1,-1 if normal=="+Y" else 1]
                            elif normal not in ("+Z","-Z"):
                                raise ValueError("Unsupported quad normal "+normal)
                        widths={"front":(size[0],size[1]),"back":(size[0],size[1]),
                            "left":(size[2],size[1]),"right":(size[2],size[1]),
                            "top":(size[0],size[2]),"bottom":(size[0],size[2])}
                        a,b=widths[face]
                        uv=np.array([[0,0],[0,1],[1,1],[1,0]],float)
                        angle=int(layout.get("angle",0))%360
                        for _ in range(angle//90): uv=np.column_stack((1-uv[:,1],uv[:,0]))
                        if angle%180: a,b=b,a
                        if layout.get("mirror",{}).get("x"): uv[:,0]=1-uv[:,0]
                        if layout.get("mirror",{}).get("y"): uv[:,1]=1-uv[:,1]
                        uv*=np.array([a,b])
                        uv+=np.array([layout.get("offset",{}).get("x",0),layout.get("offset",{}).get("y",0)])
                        world=(vertices*stretch+offset)@r.T+p
                        result.append((world,uv,tex))
                walk(node.get("children",[]),r,socket_p if not attachment and node["name"] == "Head" else p)
        walk(model["nodes"],np.eye(3),np.zeros(3),True)
        if not result: raise ValueError("No renderable geometry: "+part["Id"])
        return result

    def render(self, category, part):
        chosen=self.geometry(part)
        context=[]
        # Canonical mannequin only; never consume a player/NPC appearance file.
        for c in ("BODY_CHARACTERISTIC","FACE","EARS","MOUTH","EYES","EYEBROWS","UNDERWEAR"):
            if c!=category:
                neutral = next((p for p in self.parts[c] if c=="UNDERWEAR" and p["Id"]=="Boxer"),self.parts[c][0])
                context+=self.geometry(neutral)
        yaw=math.radians(165 if category=="CAPE" else -15)
        pitch=math.radians(5)
        camera=np.array([[math.cos(yaw),0,-math.sin(yaw)],
            [math.sin(yaw)*math.sin(pitch),math.cos(pitch),math.cos(yaw)*math.sin(pitch)],
            [math.sin(yaw)*math.cos(pitch),-math.sin(pitch),math.cos(yaw)*math.cos(pitch)]])
        points=np.concatenate([f[0]@camera.T for f in chosen])
        low,high=points.min(axis=0),points.max(axis=0)
        # Head accessories and small facial details need recognizable head context.
        if category in ("FACE","EARS","MOUTH","EYES","EYEBROWS","FACIAL_HAIR","HAIRCUT","HEAD_ACCESSORY","FACE_ACCESSORY","EAR_ACCESSORY"):
            head=np.array([[-23,69,-20],[23,111,20]])@camera.T
            low=np.minimum(low,head.min(axis=0)); high=np.maximum(high,head.max(axis=0))
        center=(low+high)/2
        scale=min((W-16)/max(1,high[0]-low[0]),(H-20)/max(1,high[1]-low[1]))
        image=np.zeros((H,W,4),dtype=np.uint8)
        depth=np.full((H,W),-np.inf)
        for world,uv,tex in context+chosen:
            p=world@camera.T
            normal=np.cross(p[1]-p[0],p[2]-p[0])
            normal=normal/max(np.linalg.norm(normal),1e-9)
            light=.80+.20*abs(normal[2])
            p[:,:2]=(p[:,:2]-center[:2])*[scale,-scale]+[W/2,H/2]
            for ids in ((0,1,2),(0,2,3)):
                a,b,c=p[list(ids)]; tuv=uv[list(ids)]
                xmin=max(0,int(np.floor(min(a[0],b[0],c[0])))); xmax=min(W-1,int(np.ceil(max(a[0],b[0],c[0]))))
                ymin=max(0,int(np.floor(min(a[1],b[1],c[1])))); ymax=min(H-1,int(np.ceil(max(a[1],b[1],c[1]))))
                if xmax<xmin or ymax<ymin: continue
                den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
                if abs(den)<1e-8: continue
                yy,xx=np.mgrid[ymin:ymax+1,xmin:xmax+1]; xx=xx+.5; yy=yy+.5
                wa=((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/den
                wb=((c[1]-a[1])*(xx-c[0])+(a[0]-c[0])*(yy-c[1]))/den
                wc=1-wa-wb
                z=wa*a[2]+wb*b[2]+wc*c[2]
                u=np.floor(wa*tuv[0,0]+wb*tuv[1,0]+wc*tuv[2,0]).astype(int)
                v=np.floor(wa*tuv[0,1]+wb*tuv[1,1]+wc*tuv[2,1]).astype(int)
                pixels=tex[np.clip(v,0,tex.shape[0]-1),np.clip(u,0,tex.shape[1]-1)].copy()
                pixels[:,:,:3]=(pixels[:,:,:3]*light).astype(np.uint8)
                dst=depth[ymin:ymax+1,xmin:xmax+1]
                mask=(wa>=-1e-7)&(wb>=-1e-7)&(wc>=-1e-7)&(z>=dst-1e-6)&(pixels[:,:,3]>127)
                image[ymin:ymax+1,xmin:xmax+1][mask]=pixels[mask]; dst[mask]=z[mask]
        if np.count_nonzero(image[:,:,3])<100: raise ValueError("Empty thumbnail "+part["Id"])
        return Image.fromarray(image)

def main():
    parser=argparse.ArgumentParser(); parser.add_argument("assets",type=Path); parser.add_argument("output",type=Path)
    parser.add_argument("--limit",type=int,default=0)
    parser.add_argument("--ui-index",type=Path); args=parser.parse_args()
    baker=Baker(args.assets); args.output.mkdir(parents=True,exist_ok=True)
    records=[]; samples=[]; failures=[]
    for category,parts in baker.parts.items():
        for part in (parts[:args.limit] if args.limit else parts):
            key=category+":"+part["Id"]; name=hashlib.sha256(key.encode()).hexdigest()[:24]+".png"
            try:
                im=baker.render(category,part); im.save(args.output/name)
                records.append((key,name,hashlib.sha256((args.output/name).read_bytes()).hexdigest()))
                if len(samples)<100 and (len(samples)<3 or part in parts[:3]): samples.append((category+" / "+part["Id"],im))
            except (KeyError,ValueError) as e: failures.append({"key":key,"error":str(e)})
        print(category,len(parts),"processed",flush=True)
    (args.output/"index.tsv").write_text("\n".join("\t".join(r) for r in sorted(records))+"\n",encoding="utf-8")
    if args.ui_index:
        args.ui_index.write_text("\n".join('@T'+name[:-4]+' = PatchStyle(TexturePath: "ImmersiveNpcAppearance/Thumbnails/'+name+'");'
            for key,name,sha in sorted(records))+"\n",encoding="utf-8")
    (args.output/"provenance.json").write_text(json.dumps({"renderer":"R151 orthographic software v1","size":[W,H],
        "rendererSha256":hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        "pythonLibraries":{"Pillow":PIL.__version__,"numpy":np.__version__},
        "referenceColors":True,"sourceHashes":dict(sorted(baker.sources.items())),"unavailable":failures},indent=2)+"\n")
    sheet=Image.new("RGB",(W*8,(H+32)*math.ceil(len(samples)/8)),"#2f3a4f"); draw=ImageDraw.Draw(sheet)
    for i,(name,im) in enumerate(samples):
        x=i%8*W;y=i//8*(H+32);sheet.paste(im,(x,y),im);draw.text((x+4,y+H+4),name[:27],fill="white")
    # QA output is deliberately outside packaged runtime resources.
    sheet.save(Path("build")/"thumbnail-contact-sheet.png")
    print("Baked",len(records),"unavailable",len(failures),failures[:10])

if __name__=="__main__": main()
