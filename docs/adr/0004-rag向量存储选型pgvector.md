# RAG 向量存储选型 PgVector

知识库向量存储用 PostgreSQL + pgvector 扩展（agentscope 现成 `PgVectorStore` 有一手代码实证），不引入专用向量库（Milvus/Qdrant/ES）。M2 纯向量近邻检索起步；混合检索（PG 原生全文检索 + 向量、RRF 融合）作为后续增强，同样不引入 ES。理由：企业知识库规模（单租户万级文档）在 PgVector 舒适区内，且零新增运维组件；`VDBStoreBase` 接口保留未来更换向量库的演进路径。

## Considered Options

- PgVector（选定）
- 专用向量库（规模化上限高，但当前规模不需要且新增运维面，放弃）
