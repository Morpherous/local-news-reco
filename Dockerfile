# ---- build stage ----
FROM maven:3.8.8-eclipse-temurin-11 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

# ---- run stage ----
FROM eclipse-temurin:11-jre
WORKDIR /app
COPY --from=build /src/target/local-news-reco-0.0.1.jar app.jar
ENV JAVA_OPTS="-Xms128m -Xmx384m"
EXPOSE 8080
CMD ["sh","-c","java $JAVA_OPTS -jar app.jar --server.port=${PORT:-8080} --server.address=0.0.0.0"]
