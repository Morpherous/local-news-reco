# Runbook

## Install Java and Maven
Install Java 11 and Maven 3.8

## Login cloud machine
```bash
ssh root@139.9.118.62
jrt!fW4shY88FBv
```
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
```

