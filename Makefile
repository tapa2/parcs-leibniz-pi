JAVAC = javac -encoding UTF-8 -cp out/parcs.jar -d build

all: run

build/Main.class build/SeriesWorker.class: src/Main.java src/SeriesWorker.java out/parcs.jar
	@mkdir -p build
	@$(JAVAC) src/Main.java src/SeriesWorker.java

# SeriesWorker calls Main.partialSum, so both jars contain both classes
out/Main.jar out/SeriesWorker.jar: build/Main.class build/SeriesWorker.class
	@jar cf out/Main.jar -C build .
	@jar cf out/SeriesWorker.jar -C build .

build: out/Main.jar out/SeriesWorker.jar

run: out/Main.jar out/SeriesWorker.jar
	@cd out && java -cp 'parcs.jar:Main.jar' Main

clean:
	rm -rf build out/Main.jar out/SeriesWorker.jar

.PHONY: all build run clean
