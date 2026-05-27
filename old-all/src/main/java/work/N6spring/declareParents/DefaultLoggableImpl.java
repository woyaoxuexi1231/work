package work.N6spring.declareParents;

// 日志接口的默认实现
public class DefaultLoggableImpl implements Loggable {
    @Override
    public void log(String message) {
        System.out.println("[LOG] " + message);
    }
}