# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

苍穹外卖(黑马)课程后端练习项目,Spring Boot **2.7.3** 多模块 Maven 工程,运行于 JDK 8/11(当前机器是 JDK 25,能启动但会有旧依赖原生访问告警,属无害)。代码中大量中文注释,是跟着教程逐日开发的教学项目(截止现在已做:员工登录/新增/分页/启禁用/编辑、分类管理)。

三个模块:
- **sky-common** — 通用基础:常量 `constant/`、`MessageConstant`、`BaseContext`(ThreadLocal 存当前登录员工 id)、异常体系 `exception/`(`BaseException` 及子类)、`result/`(`Result<T>`、`PageResult`)、`properties/`(JWT 等 `@ConfigurationProperties`)、`utils/`(`JwtUtil`)、`enumeration/`、`json/`。
- **sky-pojo** — 纯数据对象:`entity/`(对应表)、`dto/`(入参)、`vo/`(出参)。
- **sky-server** — 启动入口 `com.sky.SkyApplication` + `controller/admin/`、`service/`、`service/impl/`、`mapper/`、`config/`、`interceptor/`、`handler/`。

## 常用命令

无 mvnw wrapper;在 IDEA 里直接运行 `com.sky.SkyApplication` 即可(默认激活 `dev` profile,端口 8080)。命令行等价方式:

```bash
mvn -pl sky-server -am spring-boot:run     # 启动
mvn clean package                          # 打包(整工程)
```

项目目前**没有任何测试代码**,也没有前端源码,前端是外部提供的页面直接连后端接口联调。

## 数据库与环境

- MySQL 本机 `localhost:3306`,库名 `sky_take_out`,账号密码见 `sky-server/src/main/resources/application-dev.yml`(`root`/`123456`)。正式库名/端口等通过 `sky.datasource.*` 覆盖,`application.yml` 引用。
- 表 `employee`、`category`、`dish`、`setmeal` 等初始数据由课程 SQL 导入。注意:初始 SQL 里 `employee` 表 admin 密码是**明文 `123456`**,而登录代码是 MD5 比对,首次使用需执行 `UPDATE employee SET password = MD5('123456') WHERE username='admin';`(或重新导入后都要做一次)。

## 请求/鉴权链路(重要)

- 所有管理端接口位于 `/admin/**`。`WebMvcConfiguration extends WebMvcConfigurationSupport`,注册了 `JwtTokenAdminInterceptor` 拦截 `/admin/**`,**仅放行 `/admin/employee/login`**。其它 `/admin/**` 请求都必须带请求头 `token`(=登录返回的 JWT),否则返回 401。
- token 请求头名/密钥/有效期在 `application.yml` 的 `sky.jwt`(admin-token-name: `token`,secret: `itcast`,ttl: 7200000ms)。
- 拦截器校验通过后把员工 id 放入 `BaseContext.setCurrentId(...)`;service 层新增/更新时用它填 `create_user`/`update_user`(方法结束记得清理 BaseContext,课程未做,注意别在后续功能里跨线程漏清理)。
- 接口文档 = **Knife4j**,访问 `http://localhost:8080/doc.html`。在页面右上角"Authorize/全局参数"里配置请求头 `token` 才能调试受保护接口。
- 接口文档由 springfox Docket 生成,`WebMvcConfiguration` 里手动映射了 `/doc.html` 与 `/webjars/**` 静态资源(因为继承了 `WebMvcConfigurationSupport`,Boot 的 MVC 自动配置会退位)。

## 分层与调用约定

典型链:Controller(`@RestController`,`Result<T>` 返回)→ Service 接口 → `service/impl` 实现 → Mapper(接口+可选 XML)→ 表。Controller 用 `Result.success(...)` 包返回;业务异常统一抛 `BaseException` 子类(如 `AccountNotFoundException`、`DeletionNotAllowedException`),由 `handler/GlobalExceptionHandler` 捕获转成 `Result.error`。

- 新增分类/员工类功能:service 里 `BeanUtils.copyProperties(dto, entity)` 拷贝字段,再手动 set 状态/时间/审计人,Mapper 用 `@Insert` 注解 SQL 或 XML。
- 启禁用/编辑:Mapper 提供动态 `update`(XML `<set>`+`<if>`),只更新非空字段,`where id = #{id}`。

## MyBatis 约定(易踩坑)

- `application.yml` 开启 `map-underscore-to-camel-case: true`,但**只作用于"查询结果 → 实体"的自动映射**。凡是写在 SQL 里引用数据库列的位置(select 列表、where、order by、insert/set 的列名),必须用下划线真名如 `create_time`、`id_number`;`#{}` 里才写 Java 驼峰属性。
- `type-aliases-package: com.sky.entity`,故 XML 里 `parameterType/resultType="Category"` 能直接解析到实体。
- mapper XML 放 `sky-server/src/main/resources/mapper/*.xml`(EmployeeMapper.xml、CategoryMapper.xml);同时也可在 Mapper 接口上用 `@Select/@Insert/@Delete`。
- **分页**:用 `PageHelper.startPage(page, pageSize)` 后紧跟一条 mapper 查询,拦截器自动补 `count(0)` 计数并给原 SQL 加 `LIMIT`;查询返回的 `Page<T>` 里 `getTotal()`/`getResult()` 就是总数和当前页数据。startPage 只作用于它之后同线程的第一条 SQL。

## BeanUtils 拷贝的类型坑

`org.springframework.beans.BeanUtils.copyProperties` 同名字段**类型不兼容时静默跳过**(不报错)。例如 `EmployeeDTO.id` 是 `Integer` 而 `Employee.id` 是 `Long`,会导致主键拷不过去、update `where id = null` 影响 0 行。新增字段或排查"改了没生效"时,先核对 DTO 与实体字段类型一致(`Long` 与 `Integer`、数字与包装类型都要注意)。
