package work.N1javabasic.old.day2.code.normal;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import work.N1javabasic.old.day2.code.MySQLService;

/**
 * MySQL 服务代理类（静态代理）
 * <p>
 * 用于演示代理模式的静态代理实现。
 * </p>
 *
 * @author hulei
 * @since 2021-10-12
 */
@Slf4j
@Data
public class MySQLServiceProxy implements MySQLService {

    /**
     * 被代理的对象
     */
    private MySQLService mySqlService;

    /**
     * 无参构造函数
     */
    public MySQLServiceProxy() {
    }

    /**
     * 有参构造函数
     *
     * @param mySqlService 被代理的 MySQL 服务对象
     */
    public MySQLServiceProxy(MySQLService mySqlService) {
        this.mySqlService = mySqlService;
    }

    /**
     * 更新数据（代理方法）
     *
     * @param arg 更新的数据
     */
    @Override
    public void update(String arg) {
        log.info("MySQL 已连接，开始事务...");
        mySqlService.update(arg);
        log.info("事务已提交");
    }
}
