"""Run from any directory: python3 工具/verify.py. Standard library only."""
from pathlib import Path
import hashlib,json,sys

root=Path(__file__).resolve().parents[1]
errors=[]
manifest=json.loads((root/'交付文件清单.json').read_text(encoding='utf-8'))
for row in manifest['files']:
    p=root/row['path']
    if not p.is_file():
        errors.append('missing: '+row['path']); continue
    b=p.read_bytes()
    if hashlib.sha256(b).hexdigest()!=row['sha256']:
        errors.append('hash mismatch: '+row['path'])
    if len(b)!=row['bytes']: errors.append('size mismatch: '+row['path'])
for p in root.rglob('*.json'):
    try: json.loads(p.read_text(encoding='utf-8'))
    except Exception as e: errors.append(str(p.relative_to(root))+': '+str(e))
entry=json.loads((root/'数据库入口.json').read_text(encoding='utf-8'))
for key in ['state_path','protocol_path','manual_path','roles_dir','tasks_dir','approved_dir','data_dir','bootstrap_task']:
    if not (root/entry[key]).exists(): errors.append('bad entry: '+key)
state=json.loads((root/'当前进度.json').read_text(encoding='utf-8'))
task=json.loads((root/state['task_path']).read_text(encoding='utf-8'))
if task['task_id']!=state['current_task']:errors.append('current task mismatch')
budget=json.loads((root/'系统/初始预算.json').read_text(encoding='utf-8'))
if sum(budget['pools_cny'].values())!=budget['capital_limit_cny']:errors.append('budget sum mismatch')
if budget['authorized_spend_cny']!=0:errors.append('unexpected initial spend authorization')
roles=list((root/'角色').glob('*.txt'))
if len(roles)!=10:errors.append('role count mismatch')
for p in roles:
    txt=p.read_text(encoding='utf-8')
    for marker in ['【共同执行协议','【本角色具体职责与验收】','【允许写入】','下一步发送给','1464356758/xiaohongshu']:
        if marker not in txt:errors.append(p.name+' missing '+marker)
print(json.dumps({'ok':not errors,'verified_files':len(manifest['files']),'roles':len(roles),'errors':errors},ensure_ascii=False,indent=2))
sys.exit(1 if errors else 0)
