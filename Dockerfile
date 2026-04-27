# Use an official OpenJDK runtime as a parent image
FROM eclipse-temurin:17-jdk-alpine

# Set the working directory in the container
WORKDIR /app

# Copy the entire project into the container
COPY . .

# Set working directory to the Java project folder for compilation
WORKDIR "/app/Hotel Reservation System"

# Compile the Java application with the MySQL connector
RUN javac -cp "mysql-connector-j-9.6.0.jar" HotelReservationApiServer.java

# Back to root app directory
WORKDIR /app

# Expose the port the app runs on (defaults to 8080 if not set by environment)
EXPOSE 8080

# Run the server
# Note: We run from the "Hotel Reservation System" directory so the StaticFileHandler 
# can correctly find the "../web" directory.
WORKDIR "/app/Hotel Reservation System"
CMD ["java", "-cp", ".:mysql-connector-j-9.6.0.jar", "HotelReservationApiServer"]
