FROM gradle:8.9-jdk21-alpine AS build
WORKDIR /workspace
COPY . /workspace
RUN gradle :api-gateway:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/api-gateway/build/libs/*.jar /app/app.jar
EXPOSE 8443
ENTRYPOINT ["java","-jar","/app/app.jar"]
