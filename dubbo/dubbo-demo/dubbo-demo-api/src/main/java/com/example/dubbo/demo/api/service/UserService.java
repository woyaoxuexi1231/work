package com.example.dubbo.demo.api.service;

import com.example.dubbo.demo.api.model.User;

import java.util.List;

/**
 * <h3>用户服务接口（Dubbo 服务契约）</h3>
 *
 * <p>
 * 本接口是 <b>Provider 与 Consumer 之间的共享契约</b>。
 * Provider 模块提供实现类（{@code UserServiceImpl}），
 * Consumer 模块通过 Dubbo 远程代理调用该接口。
 * </p>
 *
 * <h4>Dubbo 核心概念映射</h4>
 * <table border="1">
 *   <tr><th>概念</th><th>对应的 Dubbo 行为</th></tr>
 *   <tr>
 *     <td><b>服务接口</b></td>
 *     <td>Consumer 端生成 RPC 代理对象，拦截接口方法调用并转发到远程 Provider</td>
 *   </tr>
 *   <tr>
 *     <td><b>Provider 暴露</b></td>
 *     <td>Provider 将此接口的实现注册到注册中心，形如：
 *         {@code dubbo://192.168.1.100:20880/com.example...UserService}</td>
 *   </tr>
 *   <tr>
 *     <td><b>Consumer 引用</b></td>
 *     <td>Consumer 从注册中心发现该接口的 Provider 地址列表，创建动态代理</td>
 *   </tr>
 * </table>
 *
 * <h4>RPC 调用链路</h4>
 * <pre>
 * Consumer                         Registry                      Provider
 *    |                                |                              |
 *    |-- 1. 订阅 UserService -------->|                              |
 *    |<-- 2. 返回 Provider 地址列表 --|                              |
 *    |                                |                              |
 *    |-- 3. TCP 长连接 + 序列化请求 -------------------------------->|
 *    |                                |   4. 反序列化 → 反射调用 →    |
 *    |                                |      返回结果                 |
 *    |<-- 5. 序列化响应 ----------------------------------------------|
 * </pre>
 *
 * @author Dubbo Demo Team
 * @version 1.0.0
 * @see com.example.dubbo.demo.api.model.User
 */
public interface UserService {

    /**
     * 根据用户 ID 查询用户信息。
     * <p>
     * 这是一个典型的<b>同步 RPC 调用</b>：
     * Consumer 线程阻塞等待 Provider 返回结果。
     * </p>
     *
     * @param id 用户唯一标识
     * @return 用户对象；若不存在返回 {@code null}（实际生产中建议使用 Optional）
     */
    User getUserById(Long id);

    /**
     * 查询所有用户列表。
     * <p>
     * 演示 Dubbo 对 {@link List} 等集合类型的序列化支持。
     * </p>
     *
     * @return 用户列表（可能为空列表）
     */
    List<User> listAllUsers();

    /**
     * 创建新用户并返回生成的用户 ID。
     * <p>
     * 演示带有<b>副作用</b>的写操作 RPC 调用。
     * </p>
     *
     * @param user 待创建的用户对象（不含 ID，由服务端生成）
     * @return 新创建用户的 ID
     */
    Long createUser(User user);

    /**
     * 根据 ID 删除用户。
     *
     * @param id 用户 ID
     * @return {@code true} 删除成功；{@code false} 用户不存在
     */
    boolean deleteUser(Long id);
}
