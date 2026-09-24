# Imagen de producción del backend Hawkify (Render, Railway, Fly.io, Cloud Run...).
#   docker build -t hawkify-backend .
#   docker run -p 8080:8080 -e DB_URL=... -e DB_USERNAME=... -e DB_PASSWORD=... -e JWT_SECRET=... hawkify-backend

# ---- compilación ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
# El wrapper puede llegar con finales de línea de Windows.
RUN sed -i 's/\r$//' mvnw .mvn/wrapper/maven-wrapper.properties && chmod +x mvnw \
    && ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B package -DskipTests

# ---- ejecución ----
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 1001 hawkify && mkdir -p /app/uploads && chown hawkify /app/uploads
COPY --from=build /app/target/hawkify-backend-*.jar app.jar
USER hawkify
# Heap ajustado al contenedor. TieredStopAtLevel=1 (solo compilador C1): con 0.1 CPU
# (plan gratis de Render) el arranque baja de ~180 s a ~97 s y la RAM de ~345 a ~245 MB.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -XX:TieredStopAtLevel=1" \
    DEMO_SEED=false
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
