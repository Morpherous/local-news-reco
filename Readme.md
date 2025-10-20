# Runbook

## Install Java and Maven
Install Java 11 and Maven 3.8

## Package the JAR
```bash
mvn clean package -DskipTests
```

## Run
```bash
java -jar target/local-news-reco-0.0.1.jar
```

## API
```bash
curl http://localhost:8080/api/articles
curl http://localhost:8080/api/users/u1/recommendations?limit=1

https://local-news-reco.onrender.com/api/articles
https://local-news-reco.onrender.com/api/users/u1/recommendations?limit=1
```

