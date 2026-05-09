## MyBatis 的核心组件有哪些？（SqlSessionFactoryBuilder, SqlSessionFactory, SqlSession, Executor）。

SqlSessionFactoryBuilder sqlsessionfactory的构造器，方便构造sqlsessionfactory

SqlSessionFactory session工厂，可以获取sqlsession

SqlSession 实际的session，可以通过这个拿到mapper，执行mappersql

Executor 最终拿到sql执行的执行器



## 为什么说 MyBatis 是“半自动”的 ORM 框架？它相比 Hibernate 有哪些优缺点？

ORM是**Object-Relational Mapping**（对象关系映射）

全自动的ORM框架仅通过约定的方法声明方法，框架可以自动生成sql，不再需要写sql了

mybatis不提供自动生成sql的能力， 但是提供写sql的模板，使用占位符

提供更灵活的sql编写能力，多表联查这些更方便，复杂sql更方便



## MyBatis 是如何实现接口绑定（Mapper 接口）的？

mybatis的xml文件中 ，xml文件会配置mapper的namespace，这个namespace对应的是接口的全限定类名，每个实际的方法通过每条sql标签的id绑定。



## `Configuration` 对象在 MyBatis 中扮演什么角色？

configuration 可以配置连接信息，xml的配置，mapper接口扫描的配置



## 在海量数据操作场景下，MyBatis 的性能瓶颈通常出现在哪个阶段？（参数解析、结果集映射还是 SQL 执行？）。

主要是参数解析和结果映射，sql执行在mysql侧。 这个答案是我瞎猜的