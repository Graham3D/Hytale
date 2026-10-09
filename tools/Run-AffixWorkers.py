"""Launch bounded ephemeral implementation workers; never starts a game or native server."""
import json, os, subprocess, sys
from pathlib import Path
root=Path(__file__).resolve().parents[1]
folder=root/'evidence/master-affix/workers'
exe=Path(r'C:/Users/Zemio/AppData/Local/OpenAI/Codex/bin/db24ee4aeff81dee/codex.exe')
processes=json.loads((folder/'processes.json').read_text()) if (folder/'processes.json').exists() else {}
for name in (sys.argv[1:] or ['catalog','combat','status','resources','summons','native']):
    with (folder/(name+'-prompt.txt')).open('rb') as prompt, (folder/(name+'-log.txt')).open('wb') as output:
        p=subprocess.Popen([str(exe),'exec','--ephemeral','--color','never','-C',str(root),'-s','danger-full-access','-c','approval_policy="never"','-o',str(folder/(name+'-final.txt')),'-'],stdin=prompt,stdout=output,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
        processes[name]=p.pid
(folder/'processes.json').write_text(json.dumps(processes,indent=2))
print(json.dumps(processes))
