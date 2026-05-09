show engines ;

show variables like 'default_storage_engine';

show variables like 'max_connections';

show variables like 'innodb_buffer_pool_size' ;
# 默认单位是 字节 (Bytes)
select *, VARIABLE_VALUE/1024/1024 as MB from performance_schema.global_variables where VARIABLE_NAME = 'innodb_buffer_pool_size';