all: build run

build:
	mvn compile

run: build
	mvn javafx:run

clean:
	mvn clean
