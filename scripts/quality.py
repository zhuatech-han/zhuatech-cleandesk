#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""全新私有验收库的真实 HTTP 三端流程。会创建 QA 记录，不能用于客户数据库。"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import secrets
import struct
import uuid
import zlib
from datetime import datetime, timedelta, timezone
from zoneinfo import ZoneInfo
from http_client import Client, env, ROOT


def png():
    """不含客户照片的可重现 QA 色块，仅供验收。官网 https://www.zhuatech.cn/。"""
    def chunk(t, data):
        return struct.pack('!I', len(data)) + t + data + struct.pack('!I', zlib.crc32(t + data) & 0xffffffff)
    rows = b''.join(b'\0' + b''.join(bytes((32 + (x // 40) * 20, 80 + (y // 40) * 15, 110)) for x in range(320)) for y in range(200))
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('!2I5B', 320, 200, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(rows)) + chunk(b'IEND', b'')


def verify_boundaries(admin, worker, username, visit_id):
    """确认管理员保留及员工动态脱敏，拒绝操作均须事务回滚。知华科技 https://www.zhuatech.cn/。"""
    user = next(a for a in admin.request('/admin/users') if a['username'] == username.lower())
    role = next(a for a in admin.request('/admin/roles') if a['id'] == user['roleId'])
    department = next(a for a in admin.request('/admin/departments') if a['id'] == user['departmentId'])
    attempts = [dict(user, kind='users', enabled=False), dict(role, kind='roles', permissions=[p for p in role['permissions'] if p != 'roles']), dict(department, kind='departments', enabled=False)]
    for body in attempts:
        body['requestKey'] = str(uuid.uuid4())
        assert admin.request('/commands/admin.save', body, status=409)['code'] == 'LAST_ADMIN'
    assert admin.request('/auth/me')['id'] == user['id']
    events = worker.request('/visits/' + str(visit_id))['events']
    assert not any(e['action'] in ['QUOTED', 'RECEIPT', 'REFUND', 'CUSTOMER_CONFIRMED'] for e in events)
    return 5


def run(base, output):
    """只输出通过数量；凭据和对象标识存入忽略且0600的私有文件。知华科技 https://www.zhuatech.cn/。"""
    cfg = env()
    clients = {}
    checks = []
    def check(name, value):
        assert value, name
        checks.append(name)
    def login(key, name, password):
        c = Client(base)
        c.login(name, password)
        clients[key] = c
        return c
    admin = login('admin', cfg.get('ADMIN_USERNAME', 'admin'), cfg['ADMIN_PASSWORD'])
    anon = Client(base)
    anon.request('/workspace', status=401)
    admin.request('/commands/customer.save', {}, status=403, csrf=False)
    ws = admin.request('/workspace')
    check('dedicated empty QA database', not ws['bookings'] and not ws['customers'] and not ws['workers'] and not ws['services'] and not ws['extras'])
    roles = {r['name']: r['id'] for r in admin.request('/admin/roles')}
    state = {'base': base, 'accounts': {}, 'ids': {}}
    zone_name = ws['timezone']
    def cmd(c, action, body, status=200):
        return c.request('/commands/' + action, dict(body, requestKey=str(uuid.uuid4())), status=status)
    def create(action, **body):
        return cmd(admin, action, body)['id']
    def detail(c, vid):
        return c.request('/visits/' + str(vid))
    def visit_cmd(c, vid, action, **body):
        v = detail(c, vid)['visit']
        return cmd(c, 'visit.' + action, dict(body, id=vid, version=v['version']))
    # Only the verified empty QA installation: avoid a midnight-crossing crew slot without altering the application clock.
    if datetime.now(ZoneInfo(zone_name)).hour >= 21:
        department = admin.request('/admin/departments')[0]
        cmd(admin, 'admin.save', dict(department, kind='departments', zone='UTC'))
        zone_name = 'UTC'
    state['timezone'] = zone_name
    for key, role, label in [('client', '客户', 'QA 客户一'), ('other', '客户', 'QA 客户二'), ('worker', '保洁人员', 'QA 保洁一'), ('worker2', '保洁人员', 'QA 保洁二')]:
        username = 'qa-' + key + '-' + secrets.token_hex(3)
        password = 'QAonly9!' + secrets.token_urlsafe(18)
        aid = create('admin.save', kind='users', username=username, displayName=label, password=password, departmentId=1, roleId=roles[role], enabled=True)
        state['accounts'][key] = {'username': username, 'password': password, 'id': aid}
    ids = state['ids']
    for key, name in [('client', 'QA 虚构客户 · 不是真实客户'), ('other', 'QA 隔离客户 · 不是真实客户')]:
        ids[key] = create('customer.save', accountId=state['accounts'][key]['id'], name=name, contact='QA 测试联系人 · 无真实电话', billingType='MONTHLY', enabled=True)
        ids[key+'Site'] = create('site.save', customerId=ids[key], name='QA 模拟办公场所', address='QA 虚构地址 · 不对应真实住址', contact='QA 测试联系人', instructions='仅验收演示，无门禁密码', enabled=True)
    ids['service'] = create('service.save', name='QA 日常办公室保洁', nameEn='QA routine office cleaning', categoryCode='OFFICE', unit='HOURLY', basePrice='20.00', unitPrice='100.00', minutesPerUnit=60, workersRequired=1, enabled=True)
    for key in ['worker', 'worker2']:
        wid = create('worker.save', accountId=state['accounts'][key]['id'], name=state['accounts'][key]['username'].split('-')[-2].replace('worker2', 'QA 保洁二').replace('worker', 'QA 保洁一'), hourlyRate='30.00', enabled=True)
        ids[key] = wid
        for day in range(1, 8):
            create('availability.save', workerId=wid, weekday=day, startTime='00:00', endTime='23:59')
    for key in state['accounts']:
        a = state['accounts'][key]
        login(key, a['username'], a['password'])
    customer, other, worker, worker2 = (clients[k] for k in ['client', 'other', 'worker', 'worker2'])
    # first and rework starts are within the documented early-start window.
    now = datetime.now(ZoneInfo(zone_name)).replace(second=0, microsecond=0)
    first = now + timedelta(minutes=15)
    assert first.hour < 22, 'Run this real-clock work scenario before 22:00 local time.'
    local = lambda x: x.strftime('%Y-%m-%dT%H:%M')
    request = dict(siteId=ids['clientSite'], serviceId=ids['service'], units='1.00', extraIds=[], localStart=local(first), occurrences=4, intervalWeeks=1, note='QA 四次周期保洁，仅用于验收')
    request['requestKey'] = str(uuid.uuid4())
    result = customer.request('/commands/booking.request', request)
    check('same-key exact retry returns same object', result == customer.request('/commands/booking.request', request))
    check('receipt recovery returns only object identifiers', result == customer.request('/command-receipts/'+request['requestKey']))
    other.request('/command-receipts/'+request['requestKey'], status=404)
    changed = dict(request, occurrences=2)
    check('same-key different payload is rejected', customer.request('/commands/booking.request', changed, status=409)['code'] == 'IDEMPOTENCY_CONFLICT')
    bid = result['id']; ids['booking'] = bid
    b = admin.request('/bookings/' + str(bid))['booking']
    q = cmd(admin, 'booking.quote', dict(id=bid, version=b['version'], discount='20.00', note='QA 每次100.00，四次分别履约'))
    cmd(customer, 'booking.confirm', dict(id=bid, version=q['version']))
    booking = customer.request('/bookings/' + str(bid))
    visits = booking['visits']; vids = [v['id'] for v in visits]; ids['visits'] = vids
    check('four independently identified occurrences', len(set(vids)) == 4)
    check('quote preserved per occurrence', all(v['charge'] == 100 for v in visits))
    cmd(admin, 'service.save', dict(ws['services'][0]) if ws['services'] else dict(id=ids['service'], version=1, name='QA 新价目（旧订单不变）', nameEn='QA updated price', categoryCode='OFFICE', unit='HOURLY', basePrice='20.00', unitPrice='200.00', minutesPerUnit=60, workersRequired=1, enabled=True))
    check('historical quote survives live price update', customer.request('/bookings/'+str(bid))['booking']['unitVisitPrice'] == 120)
    visit_cmd(admin, vids[0], 'assign', workerIds=[ids['worker']], leaderId=ids['worker'], note='QA 首次派工')
    # A concurrent reservation may be submitted, but overlapping actual crew allocation must fail.
    twin = cmd(customer, 'booking.request', dict(request, occurrences=1, intervalWeeks=0))['id']
    tb = admin.request('/bookings/'+str(twin))['booking']
    tq = cmd(admin, 'booking.quote', dict(id=twin, version=tb['version'], discount='0', note='QA 冲突检查'))
    cmd(customer, 'booking.confirm', dict(id=twin, version=tq['version']))
    tv = customer.request('/bookings/'+str(twin))['visits'][0]
    error = cmd(admin, 'visit.assign', dict(id=tv['id'], version=tv['version'], workerIds=[ids['worker']], leaderId=ids['worker'], note='QA conflict'), status=409)
    check('overlapping crew assignment blocked', error['code'] == 'TIME_CONFLICT')
    visit_cmd(admin, tv['id'], 'cancel', reason='QA 冲突验证后取消')
    visit_cmd(admin, vids[1], 'assign', workerIds=[ids['worker']], leaderId=ids['worker'], note='QA 待改期')
    old = detail(admin, vids[1])['rounds'][-1]['id']
    visit_cmd(customer, vids[1], 'reschedule', localStart=local(first+timedelta(days=8)), reason='QA 客户改期')
    check('reschedule releases the previous crew', any(r['id']==old and r['status']=='CANCELLED' for r in detail(admin, vids[1])['rounds']))
    visit_cmd(admin, vids[1], 'assign', workerIds=[ids['worker2']], leaderId=ids['worker2'], note='QA 替班安排')
    visit_cmd(customer, vids[2], 'cancel', reason='QA 本周跳过', skip=True)
    cmd(admin, 'money.record', dict(visitId=vids[3], kind='RECEIPT', amount='50', reference='QA-DEPOSIT-'+str(uuid.uuid4()), note='QA 外部已收凭证'))
    visit_cmd(customer, vids[3], 'cancel', reason='QA 取消，退款单独登记')
    check('cancelled deposit remains a refund liability', detail(admin, vids[3])['visit']['refundDue']==50)
    cmd(admin, 'money.record', dict(visitId=vids[3], kind='REFUND', amount='50', reference='QA-REFUND-'+str(uuid.uuid4()), note='QA 外部已退款凭证'))
    image = png()
    def upload(c, rid, phase):
        boundary = 'CleanDeskQA'+secrets.token_hex(8)
        body = ('--'+boundary+'\r\nContent-Disposition: form-data; name="file"; filename="qa-pattern.png"\r\nContent-Type: image/png\r\n\r\n').encode()+image+('\r\n--'+boundary+'--\r\n').encode()
        return c.request(f'/rounds/{rid}/photos?phase={phase}&caption=QA-only', body, content_type='multipart/form-data; boundary='+boundary)
    v = detail(worker, vids[0])['visit']
    check('before photo is enforced', cmd(worker, 'visit.start', dict(id=v['id'],version=v['version']),status=409)['code']=='BEFORE_PHOTO_REQUIRED')
    photoids = []
    for round_no in range(2):
        d = detail(worker, vids[0]); rid=d['visit']['roundId']
        p=upload(worker,rid,'BEFORE'); photoids.append(p['id'])
        check('duplicate photo does not add a second row '+str(round_no), upload(worker,rid,'BEFORE')['id']==p['id'])
        visit_cmd(worker, vids[0], 'start')
        upload(worker,rid,'AFTER')
        visit_cmd(worker, vids[0], 'complete', minutes={str(ids['worker']):60}, note='QA 作业完成，申报60分钟')
        if round_no==0:
            cmd(admin,'settlement.create',dict(workerId=ids['worker'],fromDate=now.date().isoformat(),toDate=now.date().isoformat()),status=409)
            visit_cmd(customer,vids[0],'rework', localStart=local(now+timedelta(minutes=20)), reason='QA 局部复洁')
            visit_cmd(admin,vids[0],'assign',workerIds=[ids['worker']],leaderId=ids['worker'],note='QA 免费返工')
    visit_cmd(customer,vids[0],'accept',note='QA 客户验收通过')
    d = detail(customer,vids[0])
    check('rework keeps both rounds and one customer charge',len(d['rounds'])==2 and d['visit']['charge']==100)
    check('customer cannot see worker cost',all('hourlyRate' not in a and 'cost' not in a for r in d['rounds'] for a in r['assignments']))
    cmd(admin,'money.record',dict(visitId=vids[0],kind='RECEIPT',amount='100',reference='QA-RECEIPT-'+str(uuid.uuid4()),note='QA 已收款'))
    cmd(admin,'money.record',dict(visitId=vids[0],kind='RECEIPT',amount='0.01',reference='QA-OVERPAY'),status=409)
    d=detail(worker,vids[0])
    check('worker cannot see customer receipts or charge',not d['money'] and 'charge' not in d['visit'] and 'unitVisitPrice' not in d['booking'])
    for c in [other,worker2]:
        c.request('/visits/'+str(vids[0]),status=403)
        c.request('/photos/'+str(photoids[0]),status=403)
    check('actual photo roundtrip',hashlib.sha256(customer.request('/photos/'+str(photoids[0]),binary=True)).hexdigest()==hashlib.sha256(admin.request('/photos/'+str(photoids[0]),binary=True)).hexdigest())
    cmd(customer,'money.record',dict(visitId=vids[0],kind='RECEIPT',amount='1',reference='QA-DENIED'),status=403)
    sid=cmd(admin,'settlement.create',dict(workerId=ids['worker'],fromDate=now.date().isoformat(),toDate=now.date().isoformat(),note='QA 含返工的已验收工时'))['id'];ids['settlement']=sid
    s=admin.request('/settlements/'+str(sid))
    check('two actual work rounds are settled once',s['settlement']['amount']==60 and len(s['lines'])==2)
    cmd(admin,'settlement.pay',dict(id=sid,version=s['settlement']['version'],reference='QA-EARLY'),status=409)
    sc=cmd(worker,'settlement.confirm',dict(id=sid,version=s['settlement']['version']))
    cmd(admin,'settlement.pay',dict(id=sid,version=sc['version'],reference='QA-WORKER-PAY-'+str(uuid.uuid4())))
    cmd(admin,'settlement.create',dict(workerId=ids['worker'],fromDate=now.date().isoformat(),toDate=now.date().isoformat()),status=409)
    check('paid statement cannot be allocated again',admin.request('/settlements/'+str(sid))['settlement']['status']=='PAID')
    month=now.strftime('%Y-%m'); state['month']=month
    report=customer.request('/reports?month='+month)
    check('monthly report contains own visits only',all(v['customerId']==ids['client'] for v in report['rows']))
    check('monthly charge/receipts/due are exact',report['charge']==100 + (100 if (first+timedelta(days=8)).strftime('%Y-%m')==month else 0) and report['paid']==100 and report['due']==report['charge']-100 and report['refundDue']==0)
    check('CSV has correct scope and header',b'Visit,Customer' in customer.request('/reports.csv?month='+month,binary=True))
    worker.request('/reports?month='+month,status=403)
    other.request('/bookings/'+str(bid),status=403)
    check('another customer workspace is isolated',not other.request('/workspace')['visits'])
    state['boundaryAssertions'] = verify_boundaries(admin, worker, cfg.get('ADMIN_USERNAME', 'admin'), vids[0])
    state['photos']=photoids
    state['photoHash']=hashlib.sha256(customer.request('/photos/'+str(photoids[0]),binary=True)).hexdigest()
    state['checks']=checks
    state['httpCount']=sum(len(c.checks) for c in clients.values())+len(anon.checks)
    output.parent.mkdir(parents=True,exist_ok=True)
    output.touch(mode=0o600)
    os.chmod(output,0o600)
    output.write_text(json.dumps(state,ensure_ascii=False,indent=2))
    print(json.dumps({'businessAssertions':len(checks),'httpChecks':state['httpCount'],'status':'passed'}))


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base',default='http://127.0.0.1:8174')
    parser.add_argument('--state',type=Path,default=ROOT/'output/qa-state.json')
    parser.add_argument('--isolated-test-database',action='store_true',required=True,help='Explicitly confirm a dedicated empty QA database; never run on customer data.')
    args=parser.parse_args()
    run(args.base,args.state)
