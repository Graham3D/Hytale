"""Read-only PE/native-code inspection. Never loads or executes game code."""
import bisect, hashlib, re, struct, sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent / 'analysis-libs'))
from capstone import Cs, CS_ARCH_X86, CS_MODE_64

binary = Path(r'C:\Users\Zemio\AppData\Roaming\Hytale\install\release\package\game\latest\Client\HytaleClient.exe')
data = binary.read_bytes()
pe = struct.unpack_from('<I', data, 0x3c)[0]
count, opt_size = struct.unpack_from('<H',data,pe+6)[0], struct.unpack_from('<H',data,pe+20)[0]
opt = pe+24
base = struct.unpack_from('<Q',data,opt+24)[0]
sections=[]
for i in range(count):
    p=opt+opt_size+40*i
    name=data[p:p+8].rstrip(b'\0').decode()
    vsize,rva,size,off=struct.unpack_from('<IIII',data,p+8)
    sections.append((name,rva,vsize,off,size))
def offset(va):
    r=va-base
    for n,v,vs,o,s in sections:
        if v<=r<v+s:return o+r-v
    raise ValueError(hex(va))
def address(off):
    for n,v,vs,o,s in sections:
        if o<=off<o+s:return base+v+off-o
    raise ValueError(off)
text=next(s for s in sections if s[0]=='.text')
code=data[text[3]:text[3]+text[4]]
codebase=base+text[1]
pdata=next(s for s in sections if s[0]=='.pdata')
functions=[]
for p in range(pdata[3],pdata[3]+pdata[4]-11,12):
    a,b,u=struct.unpack_from('<III',data,p)
    if a and b>a:functions.append((base+a,base+b))
functions.sort()
starts=[f[0] for f in functions]
def function(va):
    i=bisect.bisect_right(starts,va)-1
    return functions[i] if i>=0 and va<functions[i][1] else (va,va+160)
md=Cs(CS_ARCH_X86,CS_MODE_64)
def disasm(va,limit=400):
    a,b=function(va)
    print('FUNCTION',hex(a),hex(b),'TARGET',hex(va))
    for i,ins in enumerate(md.disasm(data[offset(a):offset(a)+(b-a)],a)):
        if i>=limit: print('...');break
        print(hex(ins.address),ins.mnemonic,ins.op_str)
def refs(targets):
    # RIP-relative LEA of managed string objects; data starts after the object header.
    for m in re.finditer(rb'[\x48\x4c]\x8d[\x05\x0d\x15\x1d\x25\x2d\x35\x3d]',code):
        p=m.start(); dest=codebase+p+7+struct.unpack_from('<i',code,p+3)[0]
        if dest in targets: yield codebase+p,dest
if sys.argv[1]=='strings':
    print('SHA256',hashlib.sha256(data).hexdigest(),'BASE',hex(base),'SECTIONS',sections)
    for query in sys.argv[2:]:
        print('QUERY',query)
        for enc in ['utf-16le','ascii']:
            needle=query.encode(enc)
            for m in re.finditer(re.escape(needle),data):
                va=address(m.start()); print('STRING',enc,hex(va))
                for ref,dest in refs(set(range(va-24,va+1))): print('REF',hex(ref),'OBJECT',hex(dest),'FUNCTION',tuple(hex(x) for x in function(ref)))
elif sys.argv[1]=='disasm':
    for va in sys.argv[2:]:disasm(int(va,16))
elif sys.argv[1]=='callers':
    targets={int(x,16) for x in sys.argv[2:]}
    for m in re.finditer(rb'\xe8',code):
        p=m.start()
        if p+5<=len(code):
            dest=codebase+p+5+struct.unpack_from('<i',code,p+1)[0]
            if dest in targets: print('CALL',hex(codebase+p),'TO',hex(dest),'FUNCTION',tuple(hex(x) for x in function(codebase+p)))
elif sys.argv[1]=='window':
    for value in sys.argv[2:]:
        va=int(value,16)
        a,b=function(va)
        for ins in md.disasm(data[offset(a):offset(a)+b-a],a):
            if va-180<=ins.address<va+300:
                print(hex(ins.address),ins.mnemonic,ins.op_str)
elif sys.argv[1]=='range':
    lo,hi=(int(x,16) for x in sys.argv[2:4])
    for a,b in functions:
        if lo<=a<hi:print(hex(a),hex(b),b-a)
elif sys.argv[1]=='pointers':
    for value in sys.argv[2:]:
        va=int(value,16)
        for i in range(0,256,8):
            p=struct.unpack_from('<Q',data,offset(va)+i)[0]
            print(hex(va+i),hex(p),'FUNCTION',tuple(hex(x) for x in function(p)) if codebase<=p<codebase+len(code) else '')
elif sys.argv[1]=='managedstring':
    for value in sys.argv[2:]:
        va=int(value,16); p=offset(va)
        length=struct.unpack_from('<I',data,p+8)[0]
        print(hex(va),length,data[p+12:p+12+min(length,1000)*2].decode('utf-16le',errors='replace'))
elif sys.argv[1]=='refs':
    for ref,dest in refs({int(x,16) for x in sys.argv[2:]}):
        print(hex(ref),hex(dest),tuple(hex(x) for x in function(ref)))
elif sys.argv[1]=='calls':
    for value in sys.argv[2:]:
        a,b=function(int(value,16))
        for ins in md.disasm(data[offset(a):offset(a)+b-a],a):
            if ins.mnemonic in ('call','jmp'):print(hex(ins.address),ins.mnemonic,ins.op_str)
