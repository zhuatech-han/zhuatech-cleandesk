# CleanDesk 第三方依赖 / Third-party notices
知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2

自有保洁业务代码独立实现。项目使用Spring Boot、Spring Security、Hibernate、Flyway、MariaDB Connector/J、Vue、Vite、Nginx、MySQL及构建工具；其各自许可证不改变本项目自有代码非商业源码许可。

- Vue与Vite版权/许可分别保留在`docs/licenses/vue.txt`、`docs/licenses/vite.txt`，Vite文件含其分发依赖声明。
- MariaDB Connector/J原始LGPL许可保留在`docs/licenses/mariadb-connector-j.txt`，原依赖通过Maven获取；发布者应保留原始许可并按适用条款提供库替换/源代码入口。
- Java依赖的准确版本由`backend/pom.xml`及Spring Boot依赖管理确定，可用`mvn dependency:tree`查看完整清单；前端锁定于`frontend/package-lock.json`。
- 基础镜像分别来自MySQL、Maven/Temurin、Node和Nginx官方镜像；镜像内各组件保留其原始条款。镜像本身不是全部由知华科技创作。
- 正式LOGO和两个官方二维码位于`frontend/public/brand/`；不得替换为虚构二维码或将品牌文件当作第三方商标授权。

English: original business code is source-available for non-commercial personal learning only. Dependency and container licenses remain independent. Preserve all notices and comply with each dependency's terms; this document does not relicense third-party work or represent all dependencies as proprietary ZhiHua code.
