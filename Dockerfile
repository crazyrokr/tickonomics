FROM eclipse-temurin:25-jdk AS build

RUN apt-get update && \
    apt-get install -y --no-install-recommends cmake make gcc g++ git maven && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY . .

RUN ./gradlew :computation:cloneTalibNative --no-daemon && \
    ./gradlew :computation:buildTalibNative --no-daemon && \
    ./gradlew :computation:cloneTalibJna --no-daemon && \
    ./gradlew :computation:pinTalibJna --no-daemon && \
    ./gradlew :computation:buildTalibJna --no-daemon && \
    ./gradlew :computation:copyTalibNative --no-daemon && \
    ./gradlew :app:bootJar --no-daemon -x test

FROM eclipse-temurin:25-jre

RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

RUN addgroup --system tickonomics && \
    adduser --system --ingroup tickonomics tickonomics

WORKDIR /app
COPY --from=build /app/app/build/libs/app-0.0.1-SNAPSHOT.jar app.jar
# FFM wrapper resolves the native library via System.loadLibrary; /usr/local/lib
# is one of its fallback search directories, so no env vars are needed.
COPY --from=build /app/native-libs/ta-lib/build/libta-lib.so /usr/local/lib/libta-lib.so

USER tickonomics
EXPOSE 8080
# FFM native access is restricted on Java 22+; Linker.nativeLinker() needs this flag.
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "app.jar"]
