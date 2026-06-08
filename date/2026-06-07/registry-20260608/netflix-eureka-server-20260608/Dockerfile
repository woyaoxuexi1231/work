FROM eclipse-temurin:8-jre

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms256m -Xmx512m"

WORKDIR /app
COPY target/netflix-eureka-server.jar app.jar
EXPOSE 8761

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar /app/app.jar"]
