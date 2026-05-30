import java.util.Collections;
import java.util.List;

/**
 * @author hulei
 * @since 2026/5/30 16:16
 */

public class Test {

    public static void addNumbers(
            List<? super Integer> dest) {

        dest.add(1);      // ✅ Integer 安全写入
        dest.add(2);      // ✅ Integer 安全写入
        dest.add(3);      // ✅ Integer 安全写入
    }
}
