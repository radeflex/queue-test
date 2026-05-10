FROM gradle:9-jdk-21-and-24 AS build
WORKDIR /build
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
RUN gradle dependencies --no-daemon
COPY src ./src
RUN gradle clean build -x test --no-daemon

FROM amazoncorretto:21-alpine
ARG VERSION=1.0.0

ENV JAR_VERSION=${VERSION}
WORKDIR /app
COPY --from=build /build/build/libs/*.jar /app/
EXPOSE 8080
CMD java -jar queue-test-${JAR_VERSION}.jar