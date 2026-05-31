package com.example.dubbo.api.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 复杂泛型类型 — 专门演示 Hessian2 序列化的坑。
 *
 * =====================================================================
 * 【面试核心④：Hessian2 序列化常见坑】
 * =====================================================================
 *
 * 1. 【泛型类型擦除】
 *    Java 编译后泛型擦除，List<User> 运行时只是 List。
 *    Hessian2 反序列化时拿不到泛型信息，可能把 List<User> 变成 List<HashMap>
 *    → ClassCastException。
 *
 *    解决：
 *    - 避免深层嵌套泛型（如 Map<String, List<User>>）
 *    - 用具体 POJO 包装（如下面的 Department 类）
 *    - 或换 Kryo / Protostuff
 *
 * 2. 【Date 时区】
 *    Hessian2 序列化 Date 不携带时区，跨时区部署时注意偏移。
 *
 * 3. 【枚举兼容性】
 *    Hessian2 按名称序列化枚举。新增枚举值而调用方 jar 没更新 → 反序列化异常。
 *
 * 4. 【字段增减兼容】
 *    Hessian2 按字段顺序序列化。新增字段放末尾，不能删中间字段。
 *
 * 5. 【BigDecimal 精度】
 *    旧版 Hessian2 对 BigDecimal 可能丢精度，建议升级或换序列化方案。
 */
public class ComplexType implements Serializable {
    private static final long serialVersionUID = 1L;

    /** ⚠️ 嵌套泛型 — Hessian2 容易丢类型 */
    private Map<String, List<User>> departmentUsers;

    /** ⚠️ 深层嵌套泛型 — 最容易出问题 */
    private List<Map<String, Object>> extraAttributes;

    /** ✅ 安全的做法：具体 POJO 替代泛型嵌套 */
    private List<Department> departments;

    public ComplexType() {}

    public Map<String, List<User>> getDepartmentUsers() { return departmentUsers; }
    public void setDepartmentUsers(Map<String, List<User>> v) { this.departmentUsers = v; }

    public List<Map<String, Object>> getExtraAttributes() { return extraAttributes; }
    public void setExtraAttributes(List<Map<String, Object>> v) { this.extraAttributes = v; }

    public List<Department> getDepartments() { return departments; }
    public void setDepartments(List<Department> v) { this.departments = v; }

    /**
     * 推荐做法：把嵌套泛型拆成具体 POJO，Hessian2 零问题。
     */
    public static class Department implements Serializable {
        private static final long serialVersionUID = 1L;
        private String name;
        private List<User> members;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public List<User> getMembers() { return members; }
        public void setMembers(List<User> members) { this.members = members; }
    }
}
