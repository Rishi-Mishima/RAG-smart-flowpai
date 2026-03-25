派聪明（PaiSmart）是一个企业级的 AI 知识库管理系统，采用检索增强生成（RAG）技术，提供智能文档处理和检索能力。

核心技术栈包括 ElasticSearch、Kafka、WebSocket、Spring Security、Docker、MySQL 和 Redis。

它的目标是帮助企业和个人更高效地管理和利用知识库中的信息，支持多租户架构，允许用户通过自然语言查询知识库，并获得基于自身文档的 AI 生成响应。

![派聪明多模块架构](https://cdn.tobebetterjavaer.com/stutymore/README-20250730102133.png)

系统允许用户：

- 上传和管理各种类型的文档
- 自动处理和索引文档内容
- 使用自然语言查询知识库
- 接收基于自身文档的 AI 生成响应

用到的技术栈包括，先说后端的：

+ 框架 : Spring Boot 3.4.2 (Java 17)
+ 数据库 : MySQL 8.0
+ ORM : Spring Data JPA
+ 缓存 : Redis
+ 搜索引擎 : Elasticsearch 8.10.0
+ 消息队列 : Apache Kafka
+ 文件存储 : MinIO
+ 文档解析 : Apache Tika
+ 安全认证 : Spring Security + JWT
+ AI集成 : DeepSeek API/本地 Ollama+豆包 Embedding
+ 实时通信 : WebSocket
+ 依赖管理 : Maven
+ 响应式编程 : WebFlux

后端的整体项目结构：

```bash
src/main/java/com/yizhaoqi/smartpai/
├── SmartPaiApplication.java      # 主应用程序入口
├── client/                       # 外部API客户端
├── config/                       # 配置类
├── consumer/                     # Kafka消费者
├── controller/                   # REST API端点
├── entity/                       # 数据实体
├── exception/                    # 自定义异常
├── handler/                      # WebSocket处理器
├── model/                        # 领域模型
├── repository/                   # 数据访问层
├── service/                      # 业务逻辑
└── utils/                        # 工具类
```

再说前端的，包括：

+ 框架 : Vue 3 + TypeScript
+ 构建工具 : Vite
+ UI组件 : Naive UI
+ 状态管理 : Pinia
+ 路由 : Vue Router
+ 样式 : UnoCSS + SCSS
+ 图标 : Iconify
+ 包管理 : pnpm

前端的整体项目结构：

```bash
frontend/
├── packages/           # 可重用模块
├── public/             # 静态资源
├── src/                # 主应用程序代码
│   ├── assets/         # SVG图标，图片
│   ├── components/     # Vue组件
│   ├── layouts/        # 页面布局
│   ├── router/         # 路由配置
│   ├── service/        # API集成
│   ├── store/          # 状态管理
│   ├── views/          # 页面组件
│   └── ...            # 其他工具和配置
└── ...               # 构建配置文件
```

## 派聪明的成绩

派聪明是 9 月份上线的，截止到目前，已经取得了非常瞩目的成绩，我这里晒一下哈。


![面渣逆袭+派聪明 拿下招银网络+科大讯飞](https://cdn.tobebetterjavaer.com/paicoding/fb5db62ab92092e2d74a4916b6a45710.png)


![派聪明拿到的日常实习](https://cdn.tobebetterjavaer.com/paicoding/da01a535b091c5ebeed70bf9a08a90c6.png)


![派聪明拿下合合信息](https://cdn.tobebetterjavaer.com/paicoding/3518a76f439c325de8df763482cbebc4.png)


![派聪明拿下小红书](https://cdn.tobebetterjavaer.com/paicoding/7bed4d34460749d68db9c0fcbc4621a8.png)


![派聪明拿下网易](https://cdn.tobebetterjavaer.com/paicoding/5f227edcb38ffe41aea8fc0880f64cad.png)

说句真心话，看到这，就可以无脑冲这个项目了，因为这些，还只是冰山一角。扫下面的优惠券（或者长按自动识别）解锁派聪明源码和教程吧，[星球](https://javabetter.cn/zhishixingqiu/)目前定价 159 元/年，优惠完只需要 129 元，每天不到 0.35 元，绝对的超值。

![派聪明优惠券](https://cdn.tobebetterjavaer.com/paicoding/97601d7a337d7d944b02bb4a79cd6430.png)

>派聪明如何写到简历上：[https://paicoding.com/column/10/2](https://paicoding.com/column/10/2)



## 核心功能

这里我先带大家了解一下什么是派聪明，我为什么要做派聪明这个企业级的 RAG 知识库？派聪明这个 AI 项目能让大家学到什么？以及如何解锁派聪明的源码仓库和教程？

![派聪明的聊天助手：会依据知识库进行问答](https://cdn.tobebetterjavaer.com/paicoding/2550c873a349d8bee29d46400f12ce76.png)

![派聪明的架构概览](https://cdn.tobebetterjavaer.com/stutymore/README-20250730101618.png)

### 知识库管理

派聪明提供了完整的文档上传与解析功能，支持文件分片上传和断点续传，并支持标签进行组织管理。文档可以是公开的，也可以是私有的，并且可以与特定的组织标签关联，以便更好地进行权限分类。

![派聪明文档处理](https://cdn.tobebetterjavaer.com/stutymore/README-20250730102808.png)

### AI驱动的RAG实现

派聪明的核心是 RAG 实现：

![派聪明聊天交互](https://cdn.tobebetterjavaer.com/stutymore/README-20250730102837.png)

- 将上传的文档进行语义分块
- 调用豆包 Embedding 模型为每个文本块生成高维向量
- 将向量存储到 ElasticSearch 以支持语义搜索和关键词搜索
- 可以根据用户的查询检索相关文档
- 为 LLM 提供完整的上下文，从而生成更准确、基于文档的响应内容

### 企业级多租户

派聪明通过组织标签支持多租户架构。每个用户可以创建或加入一个或多个组织，每个组织可以拥有独立的知识库和文档管理。这样，企业可以在同一系统中管理多个团队或部门的知识库，而无需担心数据混淆或权限问题。

![派聪明的安全架构](https://cdn.tobebetterjavaer.com/stutymore/README-20250730103118.png)

### 实时通信

系统采用 WebSocket 技术，提供用户与 AI 系统之间的实时交互，支持响应式聊天界面，便于知识检索和 AI 互动。

## 前置环境

在开始之前，请确保已安装以下软件：

- Java 17
- Maven 3.8.6 或更高版本
- Node.js 18.20.0 或更高版本
- pnpm 8.7.0 或更高版本
- MySQL 8.0
- Elasticsearch 8.10.0
- MinIO 8.5.12
- Kafka 3.2.1
- Redis 7.0.11
- Docker（可选，用于运行 Redis、MinIO、Elasticsearch 和 Kafka 等服务）

## 架构设计

派聪明的架构具备一个现代化的、云原生应用程序的特点，具有清晰的关注点分离、可扩展的组件和与 AI 技术的集成。模块化设计允许随着技术的发展，特别是快速变化的 AI 集成领域，未来可以扩展和替换单个组件。

![派聪明的系统概述](https://cdn.tobebetterjavaer.com/stutymore/README-20250730102655.png)

控制层用于处理 HTTP 请求，验证输入，管理请求/响应格式化，并将业务逻辑委托给服务层。控制器按领域功能组织。遵循 RESTful 设计原则，集成了性能监控和日志记录，用于跟踪 API 使用和故障排除。

```java
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {
    @Autowired
    private DocumentService documentService;
    
    @DeleteMapping("/{fileMd5}")
    public ResponseEntity<?> deleteDocument(
            @PathVariable String fileMd5,
            @RequestAttribute("userId") String userId,
            @RequestAttribute("role") String role) {
        // 参数验证和委托给服务
        documentService.deleteDocument(fileMd5);
        // 响应处理
    }
}
```

服务层主要用来处理应用的业务逻辑，具有事务感知能力，能够处理跨越多个数据源的操作。

```java
@Service
public class DocumentService {
    @Autowired
    private FileUploadRepository fileUploadRepository;
    
    @Autowired
    private MinioClient minioClient;
    
    @Autowired
    private ElasticsearchService elasticsearchService;
    
    @Transactional
    public void deleteDocument(String fileMd5) {
        // 文档删除的业务逻辑
        // 协调多个仓储和系统
    }
}
```

数据访问层使用 Spring Data JPA 进行数据库操作，提供了对 MySQL 的 CRUD 操作。

```java
@Repository
public interface FileUploadRepository extends JpaRepository<FileUpload, Long> {
    Optional<FileUpload> findByFileMd5(String fileMd5);
    
    @Query("SELECT f FROM FileUpload f WHERE f.userId = :userId OR f.isPublic = true OR (f.orgTag IN :orgTagList AND f.isPublic = false)")
    List<FileUpload> findAccessibleFilesWithTags(@Param("userId") String userId, @Param("orgTagList") List<String> orgTagList);
}
```

实体层由映射到数据库表的 JPA 实体以及用于 API 请求和响应的 DTO（数据传输对象）组成。

```java
@Entity
public class FileUpload {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String fileMd5;
    private String fileName;
    private String userId;
    private boolean isPublic;
    private String orgTag;
    // 其他字段和方法
}
```

## 前端启动

```bash
# 进入前端项目目录
cd frontend

# 安装依赖
pnpm install

# 启动项目
pnpm run dev
```

## 环境变量与脚本

项目根目录的 `.env` 用于保存本地运行和部署相关配置。该文件已在 `.gitignore` 中忽略，不会进入版本库；首次使用时可参考 `.env.example` 填写真实值。

当前 `.env` 里主要包含两类配置：

- 应用运行配置：如 MySQL、Redis、Kafka、MinIO、Elasticsearch、JWT、AI Provider 等
- 前端部署配置：如 `DEPLOY_SERVER_HOST`、`DEPLOY_SERVER_USER`、`DEPLOY_SERVER_KEY`、`DEPLOY_TARGET_DIR`、`DEPLOY_HEALTHCHECK_URL`

常用脚本如下。

### `infra.sh`

用于在本机启动、停止和查看基础依赖服务，目前支持 `minio`、`kafka`、`elasticsearch`。

```bash
# 启动全部基础服务
./infra.sh start

# 启动指定服务
./infra.sh start minio kafka

# 查看状态
./infra.sh status

# 查看某个服务日志
./infra.sh logs elasticsearch

# 输出本地访问地址
./infra.sh urls
```

### `deploy-front.sh`

用于构建前端、打 zip 包、上传到服务器，并在远端替换 `/home/www/PaiSmart-Front/dist`。脚本会自动读取根目录 `.env` 中的部署配置。

```bash
# 直接构建并部署前端
./deploy-front.sh
```

部署脚本默认会执行这些步骤：

- 进入 `frontend` 执行 `pnpm build`
- 打包 `dist` 为 zip 文件并上传到服务器
- 删除远端旧的 `dist` 目录并解压新包
- 检查远端 `dist/index.html` 是否存在
- 请求 `DEPLOY_HEALTHCHECK_URL` 做健康检查

如果只想复用已有的前端构建产物，可以在执行时跳过构建：

```bash
DEPLOY_SKIP_BUILD=1 ./deploy-front.sh
```

## 八、解锁派聪明源码+教程

那这次为了避免盗版，这次的代码仓库采用的是邀请制，加入星球后，在星球第一个置顶帖【球友必看】中获取邀请链接，审核通过后即可查看。

![派聪明的源码申请](https://cdn.tobebetterjavaer.com/paicoding/0abd7b441b744b33d48277be776e58cc.png)

派聪明的教程，这次托管在技术派教程上，之前只要在技术派上绑定过星球的成员编号，均可以解锁查看。

>派聪明教程地址：https://paicoding.com/column/10/1

![派聪明教程](https://cdn.tobebetterjavaer.com/paicoding/a157a62358a6b3c2dab478988143271a.png)

并且了照顾大家的阅读习惯，我们也会在星球里第一时间同步。

![星球付费专栏](https://cdn.tobebetterjavaer.com/paicoding/d2c867d82d57ef1560fed6267eb02590.png)


加入[「二哥的编程星球」](https://javabetter.cn/zhishixingqiu/)后，你还可以享受以下专属内容服务：

- 1、**付费文档:** 派聪明 RAG、[微服务 PmHub](https://laigeoffer.cn/pmhub/learn/)、[前后端分离技术派](https://javabetter.cn/zhishixingqiu/paicoding.html)、轮子 MYDB、入门编程喵、AI+MCP 的校招派等项目配套的 60 万+ 字教程查看权限
- 2、**简历修改**: 提供价值超 600 元的[简历修改服务](https://javabetter.cn/zhishixingqiu/jianli.html)，附赠星球 5000+优质简历模板可供参考
- 3、**专属问答**: 向二哥和星球嘉宾发起 1v1 提问，内容不限于 offer 选择、学习路线、职业规划等
- 4、**面试指南**: 获取针对校招、社招的 40 万+字面试求职攻略《[Java 面试指南](https://javabetter.cn/zhishixingqiu/mianshi.html)》，以及二哥的 LeetCode 刷题笔记、一灰的职场进阶之路、华为 OD 题库
- 5、**学习环境:** 打造一个沉浸式的学习环境，有一种高考冲刺、大学考研的氛围

截止到 2025 年 07 月 31 日，已经有 9000+ 球友加入星球了，很多小伙伴在认真学习项目之后，都成功拿到了心仪的校招或者社招 offer，我就随便举两个例子。

![美团快手 TP-LINK 拼多多](https://cdn.tobebetterjavaer.com/stutymore/readme-20250703180225.png)

![阿里云荣耀字节](https://cdn.tobebetterjavaer.com/stutymore/readme-20250703180738.png)


目前，派聪明这个项目也收尾了，大家可以放心冲 😊。并且一次购买不需要额外付费，即可获取星球的所有付费资料，帮助你少走弯路，提高学习的效率。直接微信扫下面这个优惠券即可加入。

![派聪明优惠券](https://cdn.tobebetterjavaer.com/paicoding/97601d7a337d7d944b02bb4a79cd6430.png)

> 步骤 ①：微信扫描上方二维码，点击「加入知识星球」按钮

> 步骤 ②：访问星球置顶帖球友必看：[https://t.zsxq.com/11rEo9Pdu](https://t.zsxq.com/11rEo9Pdu)，获取项目的源码和配套教程

加入星球需要多少钱呢？星球目前定价 159 元，限时优惠 30 元，目前只需要 129 元就可以加入。

0 人的时候优惠完 69 元，1000 人的时候 79 元，2000 人的时候 89 元，3000 人的时候 99 元，5000 人的时候是 119 元，后面肯定还会继续涨。

付费社群我加入了很多，但从未见过比这更低价格，提供更多服务的社群，光派聪明这个项目的就能让你值回票价。

多说一句，任何时候，技术都是我们程序员的安身立命之本，如果你能认认真真跟完派聪明的源码和教程，相信你的编程功底会提升一大截。

再给大家展示一下派聪明教程的部分目录吧，真的是满满的诚意和干货。

![派聪明整体设计方案](https://cdn.tobebetterjavaer.com/paicoding/6b670c22740e9e7b3dfae35fd646196e.png)

![派聪明 prompt](https://cdn.tobebetterjavaer.com/paicoding/1e5e0055300a70a4cb83791f889bec20.png)

![派聪明教程目录](https://cdn.tobebetterjavaer.com/stutymore/readme-20250106103555.png)


之前就有球友反馈说，“**二哥，你这套教程如果让培训机构来卖，1999 元都算少！**

讲真心话，这个价格也不会持续很久，星球已经 9000 人了，马上 10000 人会迎来一波新的涨价（169 元），所以早买早享受，不要等，想好了就去冲，错过不能说后悔一辈子，但至少会有遗憾。


![球友们加入星球后的真实反馈](https://cdn.tobebetterjavaer.com/paicoding/0d2b52387576b0884e832c05594fc9de.png)

我们的代码，严格按照大厂的标准来，无论是整体的架构，还是具体的细节，都是无可挑剔的学习对象。

![派聪明的代码细节](https://cdn.tobebetterjavaer.com/paicoding/e946bb63f1fe5279888bb7f1fcb649b0.png)

之前曾有球友问我：“二哥，你的星球怎么不定价 199、299、399 啊，我感觉星球提供的价值远超这个价格啊。”

答案很明确，我有自己的原则，**拒绝割韭菜，用心做内容，能帮一个是一个**。

![我愿意给大家最真诚的服务](https://cdn.tobebetterjavaer.com/paicoding/e946bb63f1fe5279888bb7f1fcb649b0.png)

不为别的，为的就是给所有人提供一个可持续的学习环境。当然了，随着人数的增多，二哥付出的精力越来越多，星球也会涨价，今天这批 30 元的优惠券不仅 2025 年最大的优惠力度，也是 2026 年最大的优惠力度，现在入手就是最划算的，再犹豫就只能等着涨价了。

想想，QQ 音乐听歌连续包年需要 **88 元**，腾讯视频连续包年需要 **178 元**，腾讯体育包年 **233 元**。我相信，二哥编程星球回馈给你的，将是 10 倍甚至百倍的价值。

最后，希望小伙伴们，能紧跟我们的步伐！不要掉队。今年，和二哥一起翻身、一起逆袭、一起晋升、一起拿高薪 offer！

那无论你是社招还是校招，我们都希望你通过派聪明这个项目，能提升自己的简历含金量，拿到更好的 offer，也能更加从容的应对面试中各种 AI 相关的考察。

冲。
