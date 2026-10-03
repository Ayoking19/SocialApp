# THE FIX: Using the modern, supported Eclipse Temurin Java 17 environment
FROM eclipse-temurin:17-jdk-jammy

# Set the working directory inside the cloud server
WORKDIR /app

# Copy all your local project files into the cloud server
COPY . .

# THE FIX: Find all Java files, save their locations to a text file, and compile them
RUN find src/main/java -name "*.java" > sources.txt && javac -cp "lib/*" @sources.txt

# Expose Port 8080 so the server can receive incoming web traffic
EXPOSE 8080

# The strict JVM Heap Boundary to prevent memory crashes!
CMD ["java", "-Xmx256M", "-cp", "src:lib/*", "main.java.Main"]