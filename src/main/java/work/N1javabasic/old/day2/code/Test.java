package work.N1javabasic.old.day2.code;

import lombok.Data;
import lombok.SneakyThrows;

import java.lang.reflect.Field;

/**
 * @author hulei
 * @since 2026/4/26 11:03
 */

public class Test {

    @SneakyThrows
    public static void main(String[] args) {
        Class<TestObject> testObjectClass = TestObject.class;
        TestObject testObject = testObjectClass.getDeclaredConstructor().newInstance();
        Field[] fields = testObjectClass.getDeclaredFields();
        for (Field field : fields) {
            // 这里如果不设置, 在访问私有成员变量的时候, 会抛出异常
            // field.setAccessible( true);
            // field.set(testObject, "1");
        }

        Field[] fields2 = testObjectClass.getDeclaredFields();
        Field[] fields3 = testObjectClass.getDeclaredFields();
    }
}

@Data
class TestObject{
    private String string;
    private Integer integer;
}