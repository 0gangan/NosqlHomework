# MongoDB 数据库设计文档

## 1. 文档概述

### 1.1 文档目的
本文档详细描述 GitHub 开源项目分析平台的 MongoDB 数据库设计方案，包括数据模型设计、集合结构、索引策略、数据关系和爬虫方案，为开发团队提供完整的数据库设计参考。

### 1.2 设计原则
- **高扩展性**：支持数据量增长和功能扩展
- **高性能**：通过索引优化查询性能
- **数据完整性**：保证数据一致性和准确性
- **科研级质量**：支持学术研究的数据追溯和验证需求

---

## 2. 数据库环境配置

### 2.1 连接配置
```yaml
# MongoDB Atlas 连接配置
spring.mongodb.uri=mongodb+srv://1042562086_db_user:ddgyc2005@teamdb.51ocmo9.mongodb.net/nosql_homework
spring.data.mongodb.database=nosql_homework
```

### 2.2 数据库名称
- **数据库名**：`nosql_homework`
- **说明**：存储所有项目数据和分析结果

---

## 3. 集合设计

### 3.1 集合总览

| 集合名称 | 中文名称 | 主要用途 | 数据量级 |
|---------|---------|---------|---------|
| `projects` | 项目集合 | 存储 GitHub 项目基本信息 | 10,000+ |
| `owners` | 所有者集合 | 存储项目所有者信息 | 5,000+ |
| `organizations` | 组织集合 | 存储组织信息 | 1,000+ |
| `commits` | 提交集合 | 存储项目提交记录 | 100,000+ |
| `contributors` | 贡献者集合 | 存储项目贡献者信息 | 20,000+ |
| `analysis_results` | 分析结果集合 | 存储数据分析结果 | 1,000+ |
| `search_records` | 检索记录集合 | 存储自然语言检索记录 | 动态增长 |
| `crawler_tasks` | 爬虫任务集合 | 存储爬虫任务状态 | 动态增长 |

---

### 3.2 项目集合 (projects)

**功能描述**：存储 GitHub 开源项目的基本信息

**字段结构**：

| 字段名 | 数据类型 | 是否必填 | 索引 | 描述 |
|-------|---------|---------|------|------|
| `_id` | ObjectId | 是 | 主键 | 项目唯一标识 |
| `name` | String | 是 | 单字段 | 项目名称 |
| `full_name` | String | 是 | 单字段+唯一 | 完整名称（owner/repo） |
| `owner_login` | String | 是 | 单字段 | 所有者登录名 |
| `owner_type` | String | 是 | - | 所有者类型（User/Organization） |
| `description` | String | 否 | 全文索引 | 项目描述 |
| `language` | String | 否 | 单字段 | 主要编程语言 |
| `languages` | Array<String> | 否 | - | 所有编程语言列表 |
| `stars_count` | Integer | 是 | 单字段 | 星标数量 |
| `forks_count` | Integer | 是 | - | 分叉数量 |
| `open_issues_count` | Integer | 是 | - | 开放问题数量 |
| `created_at` | Date | 是 | 单字段 | 创建时间 |
| `updated_at` | Date | 是 | 单字段 | 更新时间 |
| `pushed_at` | Date | 否 | - | 最后推送时间 |
| `topics` | Array<String> | 否 | 多键索引 | 项目主题标签 |
| `license` | String | 否 | - | 许可证类型 |
| `watchers_count` | Integer | 是 | - | 观察者数量 |
| `size` | Integer | 是 | - | 项目大小（KB） |
| `default_branch` | String | 否 | - | 默认分支名 |
| `html_url` | String | 是 | - | GitHub页面URL |
| `category` | String | 否 | 单字段 | 软件品类 |
| `quality_score` | Double | 否 | - | 项目质量评分（0-1） |
| `long_term_value` | Double | 否 | - | 长期价值评分（0-1） |
| `health_score` | Double | 否 | - | 健康度评分（0-1） |
| `tech_stack` | Array<String> | 否 | 多键索引 | 技术栈标签 |
| `last_crawled_at` | Date | 是 | - | 最后爬取时间 |
| `is_active` | Boolean | 是 | 单字段 | 是否活跃 |

**索引设计**：

| 索引名称 | 索引字段 | 索引类型 | 用途 |
|---------|---------|---------|------|
| `idx_full_name` | `full_name` | 唯一索引 | 快速定位项目 |
| `idx_language` | `language` | 单字段 | 按语言筛选 |
| `idx_stars` | `stars_count` | 单字段 | 按星级排序 |
| `idx_created_at` | `created_at` | 单字段 | 按时间筛选 |
| `idx_updated_at` | `updated_at` | 单字段 | 增量爬取 |
| `idx_topics` | `topics` | 多键索引 | 按主题筛选 |
| `idx_description` | `description` | 全文索引 | 文本搜索 |
| `idx_category` | `category` | 单字段 | 按品类筛选 |

---

### 3.3 所有者集合 (owners)

**功能描述**：存储项目所有者（用户或组织）的基本信息

**字段结构**：

| 字段名 | 数据类型 | 是否必填 | 索引 | 描述 |
|-------|---------|---------|------|------|
| `_id` | ObjectId | 是 | 主键 | 所有者唯一标识 |
| `login` | String | 是 | 唯一索引 | 登录名 |
| `type` | String | 是 | 单字段 | 类型（User/Organization） |
| `name` | String | 否 | - | 显示名称 |
| `avatar_url` | String | 否 | - | 头像URL |
| `html_url` | String | 是 | - | GitHub页面URL |
| `repos_count` | Integer | 是 | - | 仓库数量 |
| `followers_count` | Integer | 是 | - | 关注者数量 |
| `following_count` | Integer | 否 | - | 关注的用户数量 |
| `bio` | String | 否 | - | 个人简介 |
| `company` | String | 否 | - | 公司/机构 |
| `location` | String | 否 | - | 位置 |
| `email` | String | 否 | - | 邮箱 |
| `blog` | String | 否 | - | 个人博客 |
| `created_at` | Date | 是 | - | 创建时间 |
| `updated_at` | Date | 是 | - | 更新时间 |

**索引设计**：

| 索引名称 | 索引字段 | 索引类型 | 用途 |
|---------|---------|---------|------|
| `idx_login` | `login` | 唯一索引 | 快速定位所有者 |
| `idx_type` | `type` | 单字段 | 按类型筛选 |

---

### 3.4 组织集合 (organizations)

**功能描述**：存储组织/机构的详细信息，支持活跃度和技术深度分析

**字段结构**：

| 字段名 | 数据类型 | 是否必填 | 索引 | 描述 |
|-------|---------|---------|------|------|
| `_id` | ObjectId | 是 | 主键 | 组织唯一标识 |
| `login` | String | 是 | 唯一索引 | 组织登录名 |
| `name` | String | 是 | - | 组织名称 |
| `description` | String | 否 | - | 组织描述 |
| `avatar_url` | String | 否 | - | 头像URL |
| `html_url` | String | 是 | - | GitHub页面URL |
| `repos_count` | Integer | 是 | - | 仓库数量 |
| `members_count` | Integer | 是 | - | 成员数量 |
| `created_at` | Date | 是 | - | 创建时间 |
| `updated_at` | Date | 是 | - | 更新时间 |
| `activity_score` | Double | 否 | 单字段 | 活跃度评分（0-1） |
| `tech_depth` | Double | 否 | 单字段 | 技术应用深度评分（0-1） |
| `main_tech_stacks` | Array<String> | 否 | 多键索引 | 主流技术栈 |
| `industry` | String | 否 | 单字段 | 所属行业 |
| `location` | String | 否 | - | 地理位置 |

**索引设计**：

| 索引名称 | 索引字段 | 索引类型 | 用途 |
|---------|---------|---------|------|
| `idx_org_login` | `login` | 唯一索引 | 快速定位组织 |
| `idx_activity_score` | `activity_score` | 单字段 | 活跃度排序 |
| `idx_tech_depth` | `tech_depth` | 单字段 | 技术深度排序 |
| `idx_main_tech_stacks` | `main_tech_stacks` | 多键索引 | 技术栈筛选 |

---

### 3.5 提交集合 (commits)

**功能描述**：存储项目的提交记录，支持活跃度分析

**字段结构**：

| 字段名 | 数据类型 | 是否必填 | 索引 | 描述 |
|-------|---------|---------|------|------|
| `_id` | ObjectId | 是 | 主键 | 提交唯一标识 |
| `project_full_name` | String | 是 | 单字段 | 项目完整名称 |
| `sha` | String | 是 | 唯一索引 | 提交哈希值 |
| `author_login` | String | 是 | 单字段 | 作者登录名 |
| `author_name` | String | 否 | - | 作者显示名称 |
| `author_email` | String | 否 | - | 作者邮箱 |
| `message` | String | 是 | 全文索引 | 提交消息 |
| `date` | Date | 是 | 复合索引 | 提交时间 |
| `additions` | Integer | 是 | - | 添加代码行数 |
| `deletions` | Integer | 是 | - | 删除代码行数 |
| `files_changed` | Integer | 否 | - | 修改文件数量 |
| `branch` | String | 否 | - | 分支名称 |

**索引设计**：

| 索引名称 | 索引字段 | 索引类型 | 用途 |
|---------|---------|---------|------|
| `idx_sha` | `sha` | 唯一索引 | 防止重复提交 |
| `idx_project_commits` | `project_full_name` | 单字段 | 按项目查询提交 |
| `idx_author_commits` | `author_login` | 单字段 | 按作者查询提交 |
| `idx_date_commits` | `date` | 单字段 | 按时间查询 |
| `idx_project_date` | `project_full_name, date` | 复合索引 | 项目时间序列分析 |

---

### 3.6 贡献者集合 (contributors)

**功能描述**：存储项目贡献者信息，支持贡献度分析

**字段结构**：

| 字段名 | 数据类型 | 是否必填 | 索引 | 描述 |
|-------|---------|---------|------|------|
| `_id` | ObjectId | 是 | 主键 | 贡献者唯一标识 |
| `project_full_name` | String | 是 | 单字段 | 项目完整名称 |
| `login` | String | 是 | - | 贡献者登录名 |
| `avatar_url` | String | 否 | - | 头像URL |
| `contributions` | Integer | 是 | - | 贡献次数 |
| `first_contribution_at` | Date | 否 | - | 首次贡献时间 |
| `last_contribution_at` | Date | 否 | - | 最后贡献时间 |
| `contribution_percentage` | Double | 否 | - | 贡献占比 |

**索引设计**：

| 索引名称 | 索引字段 | 索引类型 | 用途 |
|---------|---------|---------|------|
| `idx_project_contributor` | `project_full_name, login` | 复合唯一索引 | 唯一标识项目-贡献者 |
| `idx_contributions` | `contributions` | 单字段 | 按贡献数排序 |

---

### 3.7 分析结果集合 (analysis_results)

**功能描述**：存储数据分析结果，支持科研级分析

**字段结构**：

| 字段名 | 数据类型 | 是否必填 | 索引 | 描述 |
|-------|---------|---------|------|------|
| `_id` | ObjectId | 是 | 主键 | 分析结果唯一标识 |
| `analysis_type` | String | 是 | 单字段 | 分析类型 |
| `period` | String | 是 | 单字段 | 分析周期 |
| `period_start` | Date | 是 | - | 周期开始时间 |
| `period_end` | Date | 是 | - | 周期结束时间 |
| `analysis_data` | Object | 是 | - | 分析数据内容 |
| `accuracy` | Double | 否 | - | 分析准确率 |
| `sample_count` | Integer | 否 | - | 样本数量 |
| `created_at` | Date | 是 | 单字段 | 创建时间 |
| `version` | String | 否 | - | 分析版本 |

**分析类型枚举**：
- `language_trend`：编程语言趋势分析
- `organization_activity`：组织活跃度分析
- `category_distribution`：软件品类分布分析
- `project_value`：项目价值评估
- `tech_stack_analysis`：技术栈分析
- `community_health`：社区健康度分析

**索引设计**：

| 索引名称 | 索引字段 | 索引类型 | 用途 |
|---------|---------|---------|------|
| `idx_analysis_type` | `analysis_type` | 单字段 | 按类型查询 |
| `idx_period` | `period` | 单字段 | 按周期查询 |
| `idx_created_at` | `created_at` | 单字段 | 按时间查询 |

---

### 3.8 检索记录集合 (search_records)

**功能描述**：存储自然语言检索记录，支持内核校验

**字段结构**：

| 字段名 | 数据类型 | 是否必填 | 索引 | 描述 |
|-------|---------|---------|------|------|
| `_id` | ObjectId | 是 | 主键 | 检索记录唯一标识 |
| `query` | String | 是 | 全文索引 | 用户查询语句 |
| `query_type` | String | 是 | 单字段 | 查询类型 |
| `results` | Array<Object> | 是 | - | 检索结果列表 |
| `match_score` | Double | 否 | - | 平均匹配度评分 |
| `validated` | Boolean | 是 | 单字段 | 是否已校验 |
| `validation_result` | Object | 否 | - | 校验结果详情 |
| `feedback_rating` | Integer | 否 | - | 用户反馈评分（1-5） |
| `created_at` | Date | 是 | 单字段 | 创建时间 |
| `response_time_ms` | Integer | 否 | - | 响应时间（毫秒） |

**查询类型枚举**：
- `natural_language`：自然语言查询
- `keyword`：关键词查询
- `code_search`：代码检索
- `project_search`：项目检索

**索引设计**：

| 索引名称 | 索引字段 | 索引类型 | 用途 |
|---------|---------|---------|------|
| `idx_query` | `query` | 全文索引 | 查询历史检索 |
| `idx_query_type` | `query_type` | 单字段 | 按类型统计 |
| `idx_validated` | `validated` | 单字段 | 筛选未校验记录 |

---

### 3.9 爬虫任务集合 (crawler_tasks)

**功能描述**：存储爬虫任务状态，支持任务调度和监控

**字段结构**：

| 字段名 | 数据类型 | 是否必填 | 索引 | 描述 |
|-------|---------|---------|------|------|
| `_id` | ObjectId | 是 | 主键 | 任务唯一标识 |
| `task_type` | String | 是 | 单字段 | 任务类型 |
| `status` | String | 是 | 单字段 | 任务状态 |
| `start_time` | Date | 是 | - | 开始时间 |
| `end_time` | Date | 否 | - | 结束时间 |
| `total_items` | Integer | 否 | - | 总任务数 |
| `completed_items` | Integer | 否 | - | 已完成数 |
| `failed_items` | Integer | 否 | - | 失败数 |
| `error_message` | String | 否 | - | 错误信息 |
| `params` | Object | 否 | - | 任务参数 |
| `created_at` | Date | 是 | 单字段 | 创建时间 |

**任务类型枚举**：
- `project_crawl`：项目爬取
- `organization_crawl`：组织爬取
- `commit_crawl`：提交记录爬取
- `contributor_crawl`：贡献者爬取
- `incremental_update`：增量更新

**任务状态枚举**：
- `pending`：待执行
- `running`：执行中
- `completed`：已完成
- `failed`：失败
- `cancelled`：已取消

**索引设计**：

| 索引名称 | 索引字段 | 索引类型 | 用途 |
|---------|---------|---------|------|
| `idx_task_type` | `task_type` | 单字段 | 按类型查询 |
| `idx_status` | `status` | 单字段 | 按状态筛选 |
| `idx_start_time` | `start_time` | 单字段 | 按时间排序 |

---

## 4. 数据关系设计

### 4.1 关系总览

```
┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│   projects   │◄─────│    owners    │─────►│organizations │
│              │      │              │      │              │
└──────┬───────┘      └──────────────┘      └──────────────┘
       │
       │ 1:N
       ▼
┌──────────────┐      ┌──────────────┐
│   commits    │◄─────│ contributors │
│              │      │              │
└──────────────┘      └──────────────┘
       │
       │ 生成
       ▼
┌──────────────┐      ┌──────────────┐
│analysis_rslts│◄─────│search_records│
│              │      │              │
└──────────────┘      └──────────────┘
```

### 4.2 关系说明

| 关系类型 | 源集合 | 目标集合 | 关联字段 | 说明 |
|---------|-------|---------|---------|------|
| 引用 | projects | owners | owner_login → login | 项目关联所有者 |
| 引用 | projects | organizations | owner_login → login | 项目关联组织（当owner是组织时） |
| 引用 | commits | projects | project_full_name → full_name | 提交关联项目 |
| 引用 | commits | contributors | author_login → login | 提交关联贡献者 |
| 引用 | contributors | projects | project_full_name → full_name | 贡献者关联项目 |
| 生成 | projects | analysis_results | project_full_name | 分析结果基于项目数据 |
| 生成 | analysis_results | search_records | - | 检索记录关联分析结果 |

---

## 5. 数据生命周期管理

### 5.1 TTL 索引策略

| 集合 | TTL字段 | 过期时间 | 说明 |
|-----|--------|---------|------|
| `search_records` | `created_at` | 90天 | 检索记录保留90天 |
| `crawler_tasks` | `created_at` | 30天 | 爬虫任务保留30天 |
| `analysis_results` | `created_at` | 永久 | 分析结果永久保存 |

### 5.2 数据归档策略
- **原始数据**：永久保存
- **分析结果**：永久保存，支持版本追溯
- **日志数据**：定期清理，保留最近30天

---

## 6. 爬虫方案

### 6.1 爬虫架构

```
┌─────────────────────────────────────────────────────────────────┐
│                      爬虫系统架构                               │
├─────────────────────────────────────────────────────────────────┤
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐     │
│  │  GitHub API  │    │  GitHub Web  │    │ 其他数据源   │     │
│  │   (Primary)  │    │   (Fallback) │    │              │     │
│  └──────┬───────┘    └──────┬───────┘    └──────┬───────┘     │
│         │                   │                   │              │
│         ▼                   ▼                   ▼              │
│  ┌───────────────────────────────────────────────┐            │
│  │              爬虫调度层 (Quartz)              │            │
│  │  • 定时任务调度    • 增量爬取控制             │            │
│  │  • 失败重试机制    • 速率限制管理             │            │
│  └──────────────┬───────────────────────────────┘            │
│                 │                                             │
│                 ▼                                             │
│  ┌───────────────────────────────────────────────┐            │
│  │              数据处理层                       │            │
│  │  • 数据清洗    • 数据转换    • 质量评估       │            │
│  └──────────────┬───────────────────────────────┘            │
│                 │                                             │
│                 ▼                                             │
│  ┌───────────────────────────────────────────────┐            │
│  │              MongoDB 存储层                   │            │
│  │  • 批量写入    • 索引维护    • 数据校验       │            │
│  └───────────────────────────────────────────────┘            │
└─────────────────────────────────────────────────────────────────┘
```

### 6.2 爬取数据源

| 数据源类型 | 网址/API | 爬取内容 | 频率 |
|-----------|---------|---------|------|
| GitHub API | `https://api.github.com/search/repositories` | 项目列表（按星标、语言筛选） | 每小时 |
| GitHub API | `https://api.github.com/repos/{owner}/{repo}` | 项目详细信息 | 按需 |
| GitHub API | `https://api.github.com/repos/{owner}/{repo}/commits` | 提交记录 | 每日 |
| GitHub API | `https://api.github.com/repos/{owner}/{repo}/contributors` | 贡献者列表 | 每日 |
| GitHub API | `https://api.github.com/users/{username}` | 用户信息 | 按需 |
| GitHub API | `https://api.github.com/orgs/{orgname}` | 组织信息 | 按需 |
| GitHub API | `https://api.github.com/search/users` | 用户搜索 | 每小时 |
| GitHub API | `https://api.github.com/search/issues` | 问题/PR数据 | 每日 |

### 6.3 爬取内容详细说明

#### 6.3.1 项目数据爬取

**爬取目标**：GitHub 热门项目

**爬取条件**：
- 星标数 ≥ 100
- 最近更新时间 ≤ 30天
- 主要语言：Python, Java, JavaScript, TypeScript, Go, Rust, C++, C#, PHP, Ruby, Swift, Kotlin

**爬取字段**：
| 字段 | 来源 | 说明 |
|-----|------|------|
| name | API | 项目名 |
| full_name | API | 完整名称 |
| description | API | 项目描述 |
| language | API | 主语言 |
| languages | API / 网页 | 语言分布 |
| stars_count | API | 星标数 |
| forks_count | API | 分叉数 |
| open_issues_count | API | 开放问题数 |
| created_at | API | 创建时间 |
| updated_at | API | 更新时间 |
| pushed_at | API | 推送时间 |
| topics | API | 主题标签 |
| license | API | 许可证 |
| watchers_count | API | 观察者数 |
| size | API | 项目大小 |
| default_branch | API | 默认分支 |
| html_url | API | 页面URL |

#### 6.3.2 组织数据爬取

**爬取目标**：活跃的技术组织

**爬取条件**：
- 仓库数 ≥ 10
- 成员数 ≥ 10

**爬取字段**：
| 字段 | 来源 | 说明 |
|-----|------|------|
| login | API | 组织登录名 |
| name | API | 组织名称 |
| description | API | 组织描述 |
| avatar_url | API | 头像URL |
| html_url | API | 页面URL |
| repos_count | API | 仓库数 |
| members_count | API | 成员数 |
| created_at | API | 创建时间 |

#### 6.3.3 提交记录爬取

**爬取目标**：项目提交历史

**爬取条件**：
- 最近1年的提交记录

**爬取字段**：
| 字段 | 来源 | 说明 |
|-----|------|------|
| sha | API | 提交哈希 |
| author_login | API | 作者登录名 |
| author_name | API | 作者名称 |
| message | API | 提交消息 |
| date | API | 提交时间 |
| additions | API | 添加行数 |
| deletions | API | 删除行数 |

#### 6.3.4 贡献者数据爬取

**爬取目标**：项目贡献者

**爬取条件**：
- 贡献次数 ≥ 1

**爬取字段**：
| 字段 | 来源 | 说明 |
|-----|------|------|
| login | API | 贡献者登录名 |
| avatar_url | API | 头像URL |
| contributions | API | 贡献次数 |

### 6.4 爬取策略

#### 6.4.1 初始爬取策略
1. **按语言筛选**：遍历主要编程语言，获取高星项目
2. **批量获取**：每次请求获取100个项目
3. **深度爬取**：对每个项目获取详细信息、提交记录、贡献者

#### 6.4.2 增量爬取策略
1. **时间范围筛选**：只获取 `updated_at` > 上次爬取时间的项目
2. **增量更新**：更新项目的最新状态和统计数据
3. **新增检测**：检测新增的高星项目

#### 6.4.3 速率限制处理
1. **API限流**：遵守GitHub API的速率限制（60次/小时未认证，5000次/小时认证）
2. **指数退避**：失败请求使用指数退避重试策略
3. **Token轮换**：使用多个GitHub Token轮换请求

#### 6.4.4 数据去重策略
1. **唯一索引**：通过 `full_name`（项目）、`sha`（提交）等唯一键去重
2. **更新时间比对**：相同数据只在更新时间更晚时更新
3. **哈希校验**：对内容进行哈希比对

### 6.5 爬取任务调度

| 任务名称 | 调度频率 | 执行时间 | 说明 |
|---------|---------|---------|------|
| 项目增量爬取 | 每小时 | 整点 | 获取更新的项目 |
| 热门项目爬取 | 每日 | 凌晨2点 | 获取新增热门项目 |
| 提交记录爬取 | 每日 | 凌晨3点 | 获取前一天提交 |
| 贡献者爬取 | 每周 | 周日凌晨 | 更新贡献者数据 |
| 组织数据爬取 | 每周 | 周一凌晨 | 更新组织信息 |
| 数据清洗 | 每日 | 凌晨4点 | 清理无效数据 |
| 分析任务 | 每日 | 凌晨5点 | 生成日分析报告 |

### 6.6 数据质量保障

#### 6.6.1 数据清洗规则
1. **无效数据过滤**：过滤描述为空、名称异常的项目
2. **重复数据处理**：通过唯一索引自动去重
3. **格式标准化**：统一日期格式、字符串编码
4. **缺失值处理**：对缺失字段填充默认值

#### 6.6.2 质量评估指标
| 指标 | 目标值 | 说明 |
|-----|-------|------|
| 数据准确率 | ≥ 95% | 有效数据占比 |
| 爬取成功率 | ≥ 98% | 成功请求占比 |
| 重复率 | ≤ 1% | 重复数据占比 |
| 数据更新延迟 | ≤ 2小时 | 数据更新时间差 |

#### 6.6.3 监控告警
1. **爬取失败告警**：连续失败10次触发告警
2. **数据质量告警**：数据准确率低于90%触发告警
3. **API限流告警**：接近API速率限制时告警

---

## 7. 索引优化策略

### 7.1 索引创建时机
- **初始索引**：集合创建时创建核心索引
- **增量索引**：根据查询模式动态添加索引
- **索引清理**：定期清理无用索引

### 7.2 索引维护建议
1. **监控索引使用**：通过 MongoDB Compass 监控索引命中率
2. **定期重建索引**：数据量变化较大时重建索引
3. **复合索引顺序**：将选择性高的字段放在前面

### 7.3 查询性能优化
- **覆盖查询**：设计索引覆盖常用查询字段
- **避免全表扫描**：确保所有查询都使用索引
- **限制返回数量**：使用 limit() 限制返回条数

---

## 8. 数据备份与恢复

### 8.1 备份策略
- **自动备份**：MongoDB Atlas 自动每日备份
- **手动备份**：重要数据变更后手动备份
- **异地备份**：备份数据存储在不同地域

### 8.2 恢复流程
1. **定位备份**：选择目标时间点的备份
2. **恢复到测试环境**：先在测试环境验证
3. **数据验证**：验证恢复数据的完整性
4. **切换生产**：确认无误后切换到生产环境

---

## 9. 安全考虑

### 9.1 访问控制
- **IP白名单**：仅允许信任的IP访问数据库
- **用户权限**：根据角色分配最小权限
- **密钥管理**：使用环境变量管理数据库密码

### 9.2 数据加密
- **传输加密**：使用 TLS/SSL 加密数据传输
- **存储加密**：敏感字段加密存储

### 9.3 审计日志
- **操作日志**：记录所有数据库操作
- **访问日志**：记录访问来源和时间

---

## 10. 总结

本文档详细设计了 GitHub 开源项目分析平台的 MongoDB 数据库结构，包括：

1. **9个核心集合**：覆盖项目、所有者、组织、提交、贡献者、分析结果等数据
2. **完整索引策略**：为常用查询场景优化性能
3. **详细爬虫方案**：定义了爬取数据源、内容和策略
4. **数据质量保障**：包含数据清洗和质量评估机制
5. **安全和运维**：涵盖备份、恢复和安全措施

该设计支持科研级数据分析需求，具备良好的扩展性和性能优化能力。