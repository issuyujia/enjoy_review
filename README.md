# 相关代码在master分支中
# enjoy_review 互娱点评项目
## 介绍
基于SpringBoot+Redis+MyBatisPlus+MySQL的仿大众点评系统构建的后端项目。通过本项目，模拟展示了用户点评、商家信息展示、优惠卷秒杀等核心功能,展示Redis在实践开发场景中的应用
## 核心技术栈
springboot、redis、mysql、mybatisPlus、luna脚本
## 模块介绍
* 商户查询缓存
* 短信登录
* 达人探店
* 好友关注
* 用户签到
* 优惠卷秒杀
* 附近商户
* 用户签到
## 后端技术选型
* Web框架：SpringBoot
* 持久层：MyBatis-Plus
* 接口文档：Swagger2
* 缓存服务：Redis
* 短信服务：阿里云短信服务
## 代码结构
```
D:.
│  .gitignore
│  enjoy_review.iml
│  pom.xml
│  
├─.idea
│      .gitignore
│      compiler.xml
│      encodings.xml
│      jarRepositories.xml
│      misc.xml
│      vcs.xml
│      workspace.xml
│      
├─src
│  ├─main
│  │  ├─java
│  │  │  └─com
│  │  │      └─suyujia
│  │  │          │  EnjoyReviewApplication.java
│  │  │          │  
│  │  │          ├─config
│  │  │          │      MvcConfig.java
│  │  │          │      MybatisConfig.java
│  │  │          │      RedissonConfig.java
│  │  │          │      WebExceptionAdvice.java
│  │  │          │      
│  │  │          ├─controller
│  │  │          │      BlogCommentsController.java
│  │  │          │      BlogController.java
│  │  │          │      FollowController.java
│  │  │          │      ShopController.java
│  │  │          │      ShopTypeController.java
│  │  │          │      UploadController.java
│  │  │          │      UserController.java
│  │  │          │      VoucherController.java
│  │  │          │      VoucherOrderController.java
│  │  │          │      
│  │  │          ├─dto
│  │  │          │      LoginFormDTO.java
│  │  │          │      Result.java
│  │  │          │      ScrollResult.java
│  │  │          │      UserDTO.java
│  │  │          │      
│  │  │          ├─entity
│  │  │          │      Blog.java
│  │  │          │      BlogComments.java
│  │  │          │      Follow.java
│  │  │          │      SeckillVoucher.java
│  │  │          │      Shop.java
│  │  │          │      ShopType.java
│  │  │          │      User.java
│  │  │          │      UserInfo.java
│  │  │          │      Voucher.java
│  │  │          │      VoucherOrder.java
│  │  │          │      
│  │  │          ├─mapper
│  │  │          │      BlogCommentsMapper.java
│  │  │          │      BlogMapper.java
│  │  │          │      FollowMapper.java
│  │  │          │      SeckillVoucherMapper.java
│  │  │          │      ShopMapper.java
│  │  │          │      ShopTypeMapper.java
│  │  │          │      UserInfoMapper.java
│  │  │          │      UserMapper.java
│  │  │          │      VoucherMapper.java
│  │  │          │      VoucherOrderMapper.java
│  │  │          │      
│  │  │          ├─service
│  │  │          │  │  IBlogCommentsService.java
│  │  │          │  │  IBlogService.java
│  │  │          │  │  IFollowService.java
│  │  │          │  │  ISeckillVoucherService.java
│  │  │          │  │  IShopService.java
│  │  │          │  │  IShopTypeService.java
│  │  │          │  │  IUserInfoService.java
│  │  │          │  │  IUserService.java
│  │  │          │  │  IVoucherOrderService.java
│  │  │          │  │  IVoucherService.java
│  │  │          │  │  
│  │  │          │  └─impl
│  │  │          │          BlogCommentsServiceImpl.java
│  │  │          │          BlogServiceImpl.java
│  │  │          │          FollowServiceImpl.java
│  │  │          │          SeckillVoucherServiceImpl.java
│  │  │          │          ShopServiceImpl.java
│  │  │          │          ShopTypeServiceImpl.java
│  │  │          │          UserInfoServiceImpl.java
│  │  │          │          UserServiceImpl.java
│  │  │          │          VoucherOrderServiceImpl.java
│  │  │          │          VoucherServiceImpl.java
│  │  │          │          
│  │  │          └─utils
│  │  │                  CacheClient.java
│  │  │                  ILock.java
│  │  │                  LoginInterceptor.java
│  │  │                  PasswordEncoder.java
│  │  │                  RedisConstants.java
│  │  │                  RedisData.java
│  │  │                  RedisIdWorker.java
│  │  │                  RefreshTokenInterceptor.java
│  │  │                  RegexPatterns.java
│  │  │                  RegexUtils.java
│  │  │                  SimpleRedisLock.java
│  │  │                  SystemConstants.java
│  │  │                  UserHolder.java
│  │  │                  
│  │  └─resources
│  │      │  application.yaml
│  │      │  seckill.lua
│  │      │  unlock.lua
│  │      │  
│  │      ├─db
│  │      │      hmdp.sql
│  │      │      
│  │      └─mapper
│  │              VoucherMapper.xml
│  │              
│  └─test
│      ├─java
│      │  └─com
│      │      └─suyujia
│      │              EnjoyReviewApplicationTests.java
│      │              
│      └─resources
└─target
```
## 实现效果
### 登录页面
![image](https://github.com/issuyujia/enjoy_review/assets/155513491/57b919bf-f2fe-4aec-9d86-b910389cd01d)
### 主体展示页面
![image](https://github.com/issuyujia/enjoy_review/assets/155513491/d9160bd3-28e1-442b-a05d-ed0b376519a1)
![image](https://github.com/issuyujia/enjoy_review/assets/155513491/1dc8b917-60ec-40f3-90b4-c43e5d8901cf)
### 优惠卷抢购页面
![image](https://github.com/issuyujia/enjoy_review/assets/155513491/3c62e21f-346d-4ffa-a282-c66ae1264788)
### 博客发表
![image](https://github.com/issuyujia/enjoy_review/assets/155513491/605dca67-ee6b-430c-8810-76fb614cfbd8)
## 安装教程
1. 将git上的前后端代码拉取到本地环境
2. 拉取后记得在idea中修改application.yml文件中的mysql、redis地址信息
3. 在本地环境中导入sql脚本
4. 导入前端项目，双击nginx.exe启动前端页面
5. 启动tomcat,输入http://127.0.0.1:8080就可以启动项目


