豆包，豆包，咱们聊一点 Spring MVC 的问题吧。

好，那先从核心流程切入：SpringMVC 的 DispatcherServlet 初始化时，会把哪些关键组件注册到容器里？少一个会怎么样？

就说 spring boot 这种环境下的话，首先它会注册这个 hello mapping。第二个呢是 hand adapted。第三个呢就是这个参数的这个转换这个解析器。第四个呢就是最后一个就是结果的转化。那首先这个一个一个请求进来之后，它首先会找到对应的这个需要调用哪个控制层的这个并。第二个呢就是判断这个并的类型是什么，通过不同的并类型，这个 Controller 的类型去调用不同的这个 Controler 的适配器。第三个呢就是把这个请求里面的这个参数，通过转换器去转换成咱们接口可以接收的形式。第四个呢就是把接口返回的数据，通过结果转换器转换成这个前端需要的格式进行返回。

梳理得挺顺，补充下还有 ViewResolver 这些。那问个实际踩坑的：如果自定义了参数解析器，却不生效，最可能是哪步没做对？

大概率是没有把这个参数解析器给塞到 Spring 的容器中，这样 Spring 没管理它也不会生效了。

算一个点，还有可能是没通过 WebMvcConfigurer 的 addArgumentResolvers 把它注册进去，光加 @Component 有时候不够。那再问：@ResponseBody 和 HttpMessageConverter 的绑定关系，是在哪个组件里完成的？

一般是得自己包装线程池，把主线程的上下文信息复制到异步线程里，比如用 TransmittableThreadLocal，不然异步线程拿不到父线程的上下文数据，排查问题就麻烦了。