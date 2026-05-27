package work.N6spring;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.JdbcProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author hulei
 * @since 2026/5/27 13:40
 */

@RequestMapping("/properties-source")
@RestController
public class PropertySourceTest {

    @Autowired
    MyJdbcProperties myJdbcProperties;

    @GetMapping("/get")
    public void test() {
        String url = myJdbcProperties.getUrl();
        System.out.println(url);
    }
}


@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "jdbc")
@PropertySource("classpath:jdbc.properties")
class MyJdbcProperties {
    private String url;
    private String username;
    private String password;
    // get/set
}
