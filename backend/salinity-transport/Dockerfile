FROM eclipse-temurin:17-jre-alpine AS runtime

WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

COPY salinity-transport/target/salt-damage-salinity-transport-*.jar app.jar

RUN chown -R app:app /app

USER app

EXPOSE 8082

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError -XX:MaxGCPauseMillis=50"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
