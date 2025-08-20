# Stage 1: Build the application using Gradle
FROM eclipse-temurin:17-jdk-jammy as builder

WORKDIR /workspace/app

# Copy gradle wrapper and build files
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Copy the source code
COPY src src

# Grant execution rights and build the application
RUN chmod +x ./gradlew
RUN ./gradlew build -x test

# Stage 2: Create the final, smaller image
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Copy the built JAR from the builder stage
COPY --from=builder /workspace/app/build/libs/*.jar app.jar

# Expose the port the app runs on
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
