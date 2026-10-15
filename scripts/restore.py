#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""只将受信任私有备份恢复到独立、空白、绑定本机的项目；不覆盖原环境。"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import zipfile
from http_client import ROOT,env
from readiness import wait_for_ready


def read_backup(path):
    """恢复前检查归档成员、限额及校验和；校验和不代表来源可信。知华科技 https://www.zhuatech.cn/。"""
    with zipfile.ZipFile(path) as archive:
        assert len(archive.namelist())==2 and set(archive.namelist())=={'database.sql','manifest.json'},'Unexpected archive members'
        assert archive.getinfo('manifest.json').file_size<=4096,'Manifest too large'
        assert archive.getinfo('database.sql').file_size<=1024**3,'SQL exceeds supported 1 GiB limit'
        manifest=json.loads(archive.read('manifest.json'))
        assert manifest['format']=='CLEANDESK_BACKUP_1','Wrong backup format'
        sql=archive.read('database.sql')
        assert hashlib.sha256(sql).hexdigest()==manifest['sha256']['database.sql'],'Backup checksum mismatch'
        return sql


def restore(path,project,env_file):
    """目标容器和卷均须不存在，旧数据保持原状。知华科技 https://www.zhuatech.cn/。"""
    assert re.fullmatch(r'cleandesk-[a-z0-9-]{2,50}',project),'Use a separate cleandesk-* project'
    values=env(env_file)
    assert values.get('BIND_ADDRESS') in ['127.0.0.1','localhost'],'Restore to localhost first'
    assert values['WEB_PORT']!=env()['WEB_PORT'],'Use a separate restore port'
    assert not subprocess.check_output(['docker','ps','-a','--filter','label=com.docker.compose.project='+project,'--format','{{.ID}}']).strip(),'Target project already has containers'
    assert subprocess.run(['docker','volume','inspect',project+'_mysql-data'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL).returncode!=0,'Target volume already exists'
    sql=read_backup(path)
    cmd=['docker','compose','--env-file',str(env_file),'-p',project]
    def run(args,data=None):
        return subprocess.run(cmd+args,cwd=ROOT,check=True,**({'input':data} if data is not None else {'stdin':subprocess.DEVNULL}))
    run(['config','--quiet'])
    run(['build','backend','frontend'])
    run(['up','-d','--wait','--wait-timeout','3600','mysql'])
    # Import occurs before the application can bootstrap an administrator or create business data.
    run(['exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" mysql --binary-mode -h127.0.0.1 -u"$MYSQL_USER" "$MYSQL_DATABASE"'],sql)
    run(['up','-d','--wait','--wait-timeout','3600'])
    wait_for_ready('http://127.0.0.1:'+values['WEB_PORT'])
    print('Trusted SQL and protected photos restored into a new isolated localhost project.')


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('backup');p.add_argument('--project',required=True);p.add_argument('--env-file',required=True)
    a=p.parse_args();restore(a.backup,a.project,Path(a.env_file))
