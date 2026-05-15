FROM eclipse-temurin:25-jdk AS build

RUN apt-get update && \
    apt-get install -y --no-install-recommends cmake make gcc g++ git maven && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY . .

RUN ./gradlew :computation:cloneTalibNative --no-daemon && \
    ./gradlew :computation:buildTalibNative --no-daemon && \
    ./gradlew :computation:cloneTalibJna --no-daemon && \
    ./gradlew :computation:buildTalibJna --no-daemon && \
    ./gradlew :computation:copyTalibNative --no-daemon && \
    ./gradlew :app:bootJar --no-daemon -x test

FROM eclipse-temurin:25-jre

RUN addgroup --system tickonomics && \
    adduser --system --ingroup tickonomics tickonomics

WORKDIR /app
COPY --from=build /app/app/build/libs/app-0.0.1-SNAPSHOT.jar app.jar

USER tickonomics
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
