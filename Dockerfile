# syntax=docker/dockerfile:1
# 호스트에 Java·Gradle이 없어도 같은 Wrapper로 빌드한다.
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
COPY src/main ./src/main
RUN chmod +x gradlew
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon bootJar

# 실행 이미지에는 소스·빌드 도구·로컬 비밀값을 넣지 않는다.
FROM eclipse-temurin:21-jre-jammy AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 nerdnam \
    && useradd --uid 10001 --gid nerdnam --no-create-home --shell /usr/sbin/nologin nerdnam
WORKDIR /app
COPY --from=build --chown=nerdnam:nerdnam /workspace/build/libs/*.jar ./app.jar
USER nerdnam
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=6 \
    CMD curl --fail --silent http://localhost:8080/actuator/health > /dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
