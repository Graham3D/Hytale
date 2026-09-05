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

# Pinned neutral Player.blockymodel landmarks: head centre y=88, neck=75,
# chest=71, pelvis=51, hands=45, thighs=39, feet=27. These are category
# composition constants, NEVER bounds measured from the selected cosmetic.
TORSO = ("Neck", "Chest", "Belly", "Pelvis", "R-Arm", "L-Arm",
    "R-Forearm", "L-Forearm", "R-Hand", "L-Hand", "R-Thigh", "L-Thigh")
LOWER = ("Pelvis", "R-Thigh", "L-Thigh", "R-Calf", "L-Calf", "R-Foot", "L-Foot")
FACE_CONTEXT = ("BODY_CHARACTERISTIC", "FACE", "EARS", "MOUTH", "EYES", "EYEBROWS")

def rig(yaw, pitch, target, vertical_span, body_nodes, context):
    return dict(yaw=yaw, pitch=pitch, target=target, verticalSpan=vertical_span,
        cropPixels=[0, 0, W, H], bodyNodes=body_nodes, context=context,
        pose="pinned-neutral-Player.blockymodel", safetyExpansion=0)

RIGS = {
    "head_shoulders": rig(-8, 0, [0, 86, 0], 70, ["Head", "Neck", "Chest", "R-Arm", "L-Arm"], FACE_CONTEXT),
    "face": rig(0, 0, [0, 88, 0], 55, ["Head", "Neck"], FACE_CONTEXT),
    "ear": rig(-35, 0, [0, 87, 0], 66, ["Head", "Neck"], FACE_CONTEXT),
    "torso": rig(0, 0, [0, 54, 0], 62, TORSO, ["BODY_CHARACTERISTIC", "UNDERWEAR"]),
    "lower_body": rig(-5, 0, [0, 36, 0], 54, LOWER, ["BODY_CHARACTERISTIC", "UNDERWEAR"]),
    "feet": rig(-20, 12, [0, 29, 3], 54, ["R-Calf", "L-Calf", "R-Foot", "L-Foot"], ["BODY_CHARACTERISTIC"]),
    "hands": rig(-8, 0, [0, 55, 0], 84, TORSO, ["BODY_CHARACTERISTIC", "UNDERWEAR"]),
    "rear_body": rig(165, 0, [0, 61, 0], 110, None, ["BODY_CHARACTERISTIC", "UNDERWEAR"]),
    "body": rig(-8, 0, [0, 62, 0], 100, None, [*FACE_CONTEXT, "UNDERWEAR"]),
}
CATEGORY_RIG = {
    "BODY_CHARACTERISTIC":"body", "UNDERWEAR":"lower_body", "SKIN_FEATURE":"body",
    "FACE":"face", "EARS":"ear", "MOUTH":"face", "EYEBROWS":"face",
    "FACIAL_HAIR":"face", "HAIRCUT":"head_shoulders", "EYES":"face",
    "PANTS":"lower_body", "OVERPANTS":"lower_body", "UNDERTOP":"torso", "OVERTOP":"torso",
    "SHOES":"feet", "GLOVES":"hands", "CAPE":"rear_body", "HEAD_ACCESSORY":"head_shoulders",
    "FACE_ACCESSORY":"face", "EAR_ACCESSORY":"ear",
}

def projection(category):
    """Pure category contract: no selected geometry or item identity accepted."""
    config = RIGS[CATEGORY_RIG[category]]
    yaw, pitch = math.radians(config["yaw"]), math.radians(config["pitch"])
    camera = np.array([[math.cos(yaw),0,-math.sin(yaw)],
        [math.sin(yaw)*math.sin(pitch),math.cos(pitch),math.cos(yaw)*math.sin(pitch)],
        [math.sin(yaw)*math.cos(pitch),-math.sin(pitch),math.cos(yaw)*math.cos(pitch)]])
    return camera, np.array(config["target"]) @ camera.T, H / config["verticalSpan"]

def rig_hash(category):
    return hashlib.sha256(json.dumps(RIGS[CATEGORY_RIG[category]], sort_keys=True).encode()).hexdigest()

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
            # Installed native shader: only exact grayscale texels use the LUT.
            # Colored trim (leather, metal, embroidery) is not a tint mask.
            gray = (pixels[:,:,0] == pixels[:,:,1]) & (pixels[:,:,1] == pixels[:,:,2])
            if lut.shape[1] != 256: raise ValueError("Expected native 256-column gradient")
            pixels[:,:,:3][gray] = lut[lut.shape[0]//2, pixels[:,:,0][gray]]
        return pixels,spec

    def geometry(self, part, remember=False, body_nodes=None):
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
                if (body_nodes is None or node["name"] in body_nodes) and shape.get("visible",True) and shape.get("type") in ("box","quad"):
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
        config = RIGS[CATEGORY_RIG[category]]
        # Traverse the full skeleton for sockets, but emit ONLY masked context
        # shapes. No head, eyes, eyebrows, mouth or ears on clothing cards.
        for c in config["context"]:
            if c!=category:
                neutral = next((p for p in self.parts[c] if c=="UNDERWEAR" and p["Id"]=="Boxer"),self.parts[c][0])
                context+=self.geometry(neutral, body_nodes=config["bodyNodes"] if c=="BODY_CHARACTERISTIC" else None)
        camera,center,scale = projection(category)
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
                # Optional offline material capture; no change to projection/depth.
                capture = getattr(self, "capture_material", None)
                if capture is not None: capture(tex, u, v, light, mask, xmin, ymin)
        if np.count_nonzero(image[:,:,3])<100: raise ValueError("Empty thumbnail "+part["Id"])
        return Image.fromarray(image)

def main():
    parser=argparse.ArgumentParser(); parser.add_argument("assets",type=Path); parser.add_argument("output",type=Path)
    parser.add_argument("--limit",type=int,default=0)
    parser.add_argument("--ui-index",type=Path)
    parser.add_argument("--contact-dir",type=Path,default=Path("build/category-contact-sheets"))
    args=parser.parse_args()
    baker=Baker(args.assets); args.output.mkdir(parents=True,exist_ok=True)
    records=[]; samples=[]; failures=[]; entry_rigs={}; sheets=[]
    for category,parts in baker.parts.items():
        category_samples=[]
        for part in (parts[:args.limit] if args.limit else parts):
            key=category+":"+part["Id"]; name=hashlib.sha256(key.encode()).hexdigest()[:24]+".png"
            try:
                im=baker.render(category,part); im.save(args.output/name)
                records.append((key,name,hashlib.sha256((args.output/name).read_bytes()).hexdigest()))
                entry_rigs[key]=rig_hash(category)
                category_samples.append((part["Id"],im))
                if len(samples)<100 and (len(samples)<3 or part in parts[:3]): samples.append((category+" / "+part["Id"],im))
            except (KeyError,ValueError) as e: failures.append({"key":key,"error":str(e)})
        print(category,len(parts),"processed",flush=True)
        # Every baked entry, grouped and paginated. Native-resolution cards,
        # fixed cell origins, labels outside the crop; no per-card sheet fit.
        args.contact_dir.mkdir(parents=True,exist_ok=True)
        for start in range(0,len(category_samples),20):
            page=category_samples[start:start+20]
            sheet=Image.new("RGB",(W*5,(H+32)*math.ceil(len(page)/5)+32),"#2f3a4f")
            draw=ImageDraw.Draw(sheet)
            draw.text((8,8),category+" / "+CATEGORY_RIG[category]+" / "+str(start+1)+"-"+str(start+len(page)),fill="white")
            for i,(label,im) in enumerate(page):
                x=i%5*W; y=32+i//5*(H+32)
                sheet.paste(im,(x,y),im); draw.text((x+4,y+H+4),label[:28],fill="white")
            name=category+"-"+str(start//20+1).zfill(2)+".png"
            sheet.save(args.contact_dir/name)
            sheets.append(dict(file=name,sha256=hashlib.sha256((args.contact_dir/name).read_bytes()).hexdigest(),
                keys=[category+":"+label for label,im in page]))
    (args.output/"index.tsv").write_text("\n".join("\t".join(r) for r in sorted(records))+"\n",encoding="utf-8")
    if args.ui_index:
        args.ui_index.write_text("\n".join('@T'+name[:-4]+' = PatchStyle(TexturePath: "ImmersiveNpcAppearance/Thumbnails/'+name+'");'
            for key,name,sha in sorted(records))+"\n",encoding="utf-8")
    (args.output/"provenance.json").write_text(json.dumps({"renderer":"R152 fixed category rigs v2","size":[W,H],
        "rendererSha256":hashlib.sha256(Path(__file__).read_text(encoding="utf-8").encode()).hexdigest(),
        "rendererHashEncoding":"UTF-8 with LF newlines",
        "pythonLibraries":{"Pillow":PIL.__version__,"numpy":np.__version__},
        "referenceColors":True,"colorPolicy":"Baked representative native gradients, not active draft colors",
        "rigs":RIGS,"categoryRigs":CATEGORY_RIG,"entryRigHashes":dict(sorted(entry_rigs.items())),
        "sourceHashes":dict(sorted(baker.sources.items())),"unavailable":failures},indent=2)+"\n")
    (args.contact_dir/"index.json").write_text(json.dumps(sheets,indent=2)+"\n",encoding="utf-8")
    sheet=Image.new("RGB",(W*8,(H+32)*math.ceil(len(samples)/8)),"#2f3a4f"); draw=ImageDraw.Draw(sheet)
    for i,(name,im) in enumerate(samples):
        x=i%8*W;y=i//8*(H+32);sheet.paste(im,(x,y),im);draw.text((x+4,y+H+4),name[:27],fill="white")
    # QA output is deliberately outside packaged runtime resources.
    sheet.save(Path("build")/"thumbnail-contact-sheet.png")
    print("Baked",len(records),"unavailable",len(failures),failures[:10])

if __name__=="__main__": main()
