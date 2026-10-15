#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""重启或空库恢复后，验证原预约、历史照片、财务凭证和结算没有丢失。"""
import argparse
import hashlib
import json
from pathlib import Path
from http_client import Client,env


def verify(base,state_file):
    """使用原有独立QA账号检查，不重建业务记录。知华科技 https://www.zhuatech.cn/。"""
    s=json.loads(Path(state_file).read_text());cfg=env();c=Client(base)
    c.login(cfg['ADMIN_USERNAME'],cfg['ADMIN_PASSWORD']);ids=s['ids']
    assert c.request('/bookings/'+str(ids['booking']))['booking']['status']=='CONFIRMED'
    v=c.request('/visits/'+str(ids['visits'][0]));assert v['visit']['status']=='ACCEPTED' and len(v['rounds'])==2
    assert v['visit']['paid']==100 and v['visit']['due']==0 and len(v['money'])==1
    assert c.request('/settlements/'+str(ids['settlement']))['settlement']['status']=='PAID'
    assert c.request('/visits/'+str(ids['visits'][1]))['visit']['status']=='ASSIGNED'
    assert c.request('/visits/'+str(ids['visits'][2]))['visit']['status']=='SKIPPED'
    cancelled=c.request('/visits/'+str(ids['visits'][3]));assert cancelled['visit']['status']=='CANCELLED' and cancelled['visit']['refundDue']==0 and len(cancelled['money'])==2
    assert hashlib.sha256(c.request('/photos/'+str(s['photos'][0]),binary=True)).hexdigest()==s['photoHash']
    for role in ['client','worker']:
        a=s['accounts'][role];u=Client(base);u.login(a['username'],a['password']);assert u.request('/visits/'+str(ids['visits'][0]))['visit']['status']=='ACCEPTED'
    print(json.dumps({'persistenceAssertions':10,'status':'passed'}))


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--base',required=True);p.add_argument('--state',required=True);a=p.parse_args();verify(a.base,a.state)
