# Use a base image with Scala and SBT
FROM hseeberger/scala-sbt:11.0.6_1.3.10_2.13.1

# Set the working directory in Docker
WORKDIR /app

# Copy the build.sbt and source code into the Docker image
COPY build.sbt .
COPY src/ src/

# Run sbt update to download all the dependencies
RUN sbt update

# Compile the code
RUN sbt compile

# Expose the port your app runs on
EXPOSE 8080

# Command to run your application
CMD ["sbt", "run"]
