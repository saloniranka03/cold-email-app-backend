# Use official OpenJDK runtime as parent image
FROM openjdk:17-jdk-slim

# Install wget for health checks (more lightweight than curl)
RUN apt-get update && apt-get install -y wget && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy the jar file
COPY target/*.jar app.jar

# Create non-root user for security
RUN addgroup --system spring && adduser --system spring --ingroup spring
USER spring

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run the application with JVM optimizations
ENTRYPOINT ["java", "-jar", "/app/app.jar"]