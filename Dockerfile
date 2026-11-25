FROM eclipse-temurin:21-jre
WORKDIR /app
COPY backend/build/libs/dagong-auth.jar app.jar
EXPOSE 4840
ENTRYPOINT ["java","-jar","/app/app.jar"]
