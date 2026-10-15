#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""停用本项目写入口后备份完整数据库，照片以十六进制包含在同一事务快照内。"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile
from http_client import ROOT


def backup(project, env_file, output):
    """不覆盖旧文件，不输出凭据；恢复启动状态仅针对本项目。知华科技 https://www.zhuatech.cn/。"""
    assert re.fullmatch(r'cleandesk-[a-z0-9-]{2,50}', project), 'Use an explicit cleandesk-* project name'
    cmd = ['docker','compose','--env-file',str(env_file),'-p',project]
    def run(args,capture=False):
        return subprocess.run(cmd+args,cwd=ROOT,check=True,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE if capture else None).stdout
    output=Path(output).resolve()
    assert not output.exists(),'Backup destination already exists'
    run(['config','--quiet'])
    active=run(['ps','--status','running','--services'],True).decode().splitlines()
    assert 'mysql' in active and 'backend' in active,'Start a healthy source deployment first'
    try:
        run(['stop','backend'])
        sql=run(['exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" mysqldump --single-transaction --hex-blob --no-tablespaces --set-gtid-purged=OFF -u"$MYSQL_USER" "$MYSQL_DATABASE"'],True)
        assert len(sql)<=1024**3,'Backup exceeds supported 1 GiB limit'
        manifest={'format':'CLEANDESK_BACKUP_1','sha256':{'database.sql':hashlib.sha256(sql).hexdigest()}}
        output.parent.mkdir(parents=True,exist_ok=True)
        fd=os.open(output,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
        with os.fdopen(fd,'wb') as raw:
            with zipfile.ZipFile(raw,'w',zipfile.ZIP_DEFLATED) as archive:
                archive.writestr('database.sql',sql)
                archive.writestr('manifest.json',json.dumps(manifest))
        print('Private SQL backup with protected photo BLOBs saved (0600). Keep it confidential.')
    finally:
        run(['up','-d','--wait','--wait-timeout','1800','backend'])


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--project',required=True);p.add_argument('--env-file',default=str(ROOT/'.env'));p.add_argument('--output',required=True)
    a=p.parse_args();backup(a.project,a.env_file,a.output)
