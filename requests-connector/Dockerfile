# Runtime Dockerfile for Requests Connector
# Prerequisite: mvn clean package -DskipTests -pl requests-connector -am
#
# Extensions (e.g., extension-campusapi) are loaded from target/extensions/
# via Spring Boot's PropertiesLauncher LOADER_PATH mechanism.
FROM amazoncorretto:17-alpine

WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring

# Create extensions directory (may be empty if no extensions are present)
RUN mkdir -p /app/extensions && chown spring:spring /app/extensions

USER spring:spring

COPY target/*.jar app.jar
# Copy extensions directory (contains extension JARs placed by build-and-push-images.ps1)
COPY target/extensions/ extensions/

EXPOSE 8086

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8086/actuator/health || exit 1

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0"
ENV LOADER_PATH="/app/extensions"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dloader.path=/app/extensions -cp app.jar org.springframework.boot.loader.launch.PropertiesLauncher"]
