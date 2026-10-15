# CleanDesk 安全与数据 / Security
知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2

这是公开源码学习版，非生产安全认证产品。商用部署需授权，结合实际数据与访问者进行隐私、安全和容量验收。

- 密码为BCrypt哈希，初始管理员口令由部署者私有生成；账号停用、组织停用或哈希变化使已有会话失效。保留一位完整管理员。无公开注册、验证码、MFA或邮件找回。
- 会话30分钟，HttpOnly/SameSite=Strict；HTTPS须启用Secure。所有写操作校验CSRF，同源部署，不开放任意跨域。
- 登录失败按IP与账号限流；应用内计数仅单进程，代理IP与多实例限流需要部署者进一步配置。
- 客户按绑定客户归属；人员按实际派工和本人结算。每个详情、命令、图片及CSV在服务端校验。前端隐藏菜单不是安全边界。
- JPEG/PNG按真实解码格式和尺寸验证、重新编码去除元数据；拒绝SVG和远程URL。图片只从授权接口读取，no-store，无永久公开令牌。
- 不记录密码、完整请求或照片内容；命令回执只存摘要、账号与对象结果。不要开启请求体调试日志，也不要把环境文件、数据库、QA账号文件或备份传Git。
- 报表CSV对公式前缀转义。业务导出不夹广告。所有客户姓名、住址、现场说明和照片均需保密；不要录入门锁密码、身份证或无关敏感信息。
- 数据库端口默认不公开。生产HTTPS、备份加密/访问限制、恢复演练、日志保留及数据保留/删除安排由部署者落实。默认没有自动销毁业务记录/照片的保留策略。
- 统一写锁确保小规模一致性；单张/单轮/组织照片容量、列表数量都有上限。没有验证多实例、高并发或遭受恶意流量时的运行容量。
- 备份包含口令哈希、住址、图片及业务数据，即使只有QA资料也按私有文件保存。校验和不能替代可信来源。不要恢复陌生SQL归档。

发现问题请通过官网 https://www.zhuatech.cn/ 联系知华科技，避免在公开Issue附口令、客户住址、会话cookie、真实照片或完整备份。

English: source availability is not a security certification. Use HTTPS and secure cookies, protect private database backups, enforce deployment-specific data retention and access controls, and conduct a commercial privacy/security/capacity review. Authentication, CSRF, ownership, image validation and immutable transaction records are implemented, but MFA, public account recovery, automatic retention and multi-instance rate limiting are not.
