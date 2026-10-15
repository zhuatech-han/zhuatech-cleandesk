#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""测试私有恢复归档的损坏、错版及额外成员拒绝，不调用Docker。"""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
import zipfile
from restore import read_backup


class BackupValidationTest(unittest.TestCase):
    """恢复前必须先验证结构和完整性。知华科技 https://www.zhuatech.cn/。"""
    def archive(self, folder, *, sql=b'QA SQL only', digest=None, format_name='CLEANDESK_BACKUP_1', extra=False):
        path=Path(folder)/'qa.zip'
        with zipfile.ZipFile(path,'w') as z:
            z.writestr('database.sql',sql)
            z.writestr('manifest.json',json.dumps({'format':format_name,'sha256':{'database.sql':digest or hashlib.sha256(sql).hexdigest()}}))
            if extra:z.writestr('../outside','QA')
        return path

    def test_valid_archive_returns_exact_binary_sql(self):
        with tempfile.TemporaryDirectory() as folder:
            sql=b'QA hex blob 0x012300FF;\n';self.assertEqual(sql,read_backup(self.archive(folder,sql=sql)))

    def test_corrupted_sql_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaises(AssertionError):read_backup(self.archive(folder,digest='0'*64))

    def test_another_project_format_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaises(AssertionError):read_backup(self.archive(folder,format_name='UNRELATED_BACKUP'))

    def test_extra_or_traversal_members_are_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaises(AssertionError):read_backup(self.archive(folder,extra=True))


if __name__=='__main__':
    unittest.main()
