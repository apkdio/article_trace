<div align="center">
<img width="150" height="60" alt="logo2" src="https://github.com/user-attachments/assets/b70382ca-0577-49c3-b568-50e256632efc" />


# 文迹
一个基于SpringBoot + Vue3 的前后端分离式Web文章平台
</div>

# 简介
本系统是一个基于SpringBoot+MybatisPlus+Vue3分层的前后端分离Web平台，可供读者、作者、站长等不同用户层次的使用，涵盖文章分类、文章、审核、阅览、系统管理等多个方面的功能，同时也拥有文章评论区供读者和作者之间交流。

**基于黑马程序员Web教程大幅修改优化。**

# 涉及技术栈
1. SpringBoot
2. MybatisPlus
3. Vue
4. Axios
5. Element Plus
6. Pinia
7. RustFS
8. MySQL
9. Redis
10. Nginx
11. JWT
12. ThreadLocal
13. 全局异常捕获处理
14. Bcrypt
15. Aho-Corasick字符匹配
# 平台架构
系统采用前后端分离架构。后端采用`SpringBoot3`框架搭建，配合`MybatisPlus`高效开发，使用`MySQL`存储数据，并将密码等敏感字段通过`Bcrypt`加密存储，用户头像与文章内容等采用对象存储（上传至`RustFS`）,且图片与文章内容`json`文件分桶存储。`Redis`作为缓存层存储热点数据，减少数据库压力，同时通过定时任务完成`Redis`到`MySQL`的数据同步。编写自定义异常捕获器，重构部分异常抛出的逻辑，便于调试与信息友好展示。用户鉴权方面采用`JWT`对用户的`id`，`username`和`type`属性进行存储校验，便于不同用户的权限管理。并使用`Aho-Corasick`算法对文章标题和内容进行初步敏感词过滤，减少审核工作。

前端界面采用Vue3框架搭建，配合`Element Plus`进一步优化布局与样式(**Gemini神力**)，配合`Axios`实现从后端接口获取数据并使用Pinia进行数据存储管理，实现前后端分离操作，服务层将静态数据托管至`Nginx`，提高静态资源的访问性能。
# 项目展示
<div align="center">
<img width="568" height="261" alt="image" src="https://github.com/user-attachments/assets/428243bd-58d4-4237-9d83-cad1f2bd345e" />
  
  登录界面

<img width="622" height="285" alt="image" src="https://github.com/user-attachments/assets/e9b770d7-0d4e-4373-a6c4-5d54f2d9037c" />

  首页
  
<img width="599" height="280" alt="image" src="https://github.com/user-attachments/assets/a99aae6d-8e9a-416a-bac4-0a7d3d6686ce" />

  后台首页
  
</div>

# 项目部署
## 基础环境

1. MySQL 8.0
（Bash） Command: sudo apt install mysql-server -y

3. Redis
（Bash）Command: sudo apt install redis-server -y

4. RustFS
（Bash）Command: curl -O https://rustfs.com/install_rustfs.sh && bash install_rustfs.sh

5. Nginx
（Bash）Command: sudo apt install nginx -y

6. Java 21
（Bash）Command: sudo apt install openjdk-21-jdk -y

## 服务配置
- MySQL：使用root用户进入MySQL，使用source命令运行article_trace.sql文件完成基础数据库结构搭建。随后创建一个专门管理该服务器的用户（如articleManager）。
- Redis：配置位于/etc/redis/redis.conf，按需配置。
- RustFS：配置位于/etc/default/rustfs，按需修改用户名、密码等配置。配置完需访问该服务器IP:9001创建两个桶用于存储图片和文章内容，并设置为私密桶。
- Nginx：于/etc/nginx/conf.d 目录下新建myServiceExample.conf，配置相关配置。
- SpringBoot：重命名application.templete.yml为application.yml,修改其中`${}`的配置
## 其他
- 防火墙需要开放9001（配置完RustFS桶后可关闭）、9000端口。
- 外部敏感词库配置：
在服务启动的工作目录下创建res文件夹，在文件夹中创建sensitive_words.txt 
一行一词，默认30分钟检查一次词库并进行热更新。
