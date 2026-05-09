package work.N1javabasic.old.day2.code;

import lombok.extern.slf4j.Slf4j;

/**
 * MySQL 服务实现类
 * <p>
 * 用于演示代理模式的目标对象。
 * </p>
 *
 * @author hulei
 * @since 2021-10-12
 */
@Slf4j
public class MySQLServiceImpl implements MySQLService {

    /**
     * 更新数据
     *
     * @param arg 更新的数据
     */
    @Override
    public void update(String arg) {
        log.info("数据已更改为 '{}'，准备提交", arg);
    }
}
