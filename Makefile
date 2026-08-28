all: build run

.PHONY: all build run clean gather validate-data test-python test train

GATHER_COUNT := $(word 2,$(MAKECMDGOALS))
GATHER_EXTRA := $(wordlist 3,$(words $(MAKECMDGOALS)),$(MAKECMDGOALS))
DATABASE ?= data/training.db
MODEL ?= data/chess_model.pt
DEPTH ?= 4
MIN_OPENING_PLIES ?= 4
MAX_OPENING_PLIES ?= 8
THREADS ?= $(shell sysctl -n hw.ncpu 2>/dev/null || nproc 2>/dev/null || echo 4)

build:
	mvn compile

run: build
	mvn javafx:run

clean:
	mvn clean

gather:
	@if [ -z "$(GATHER_COUNT)" ]; then \
		echo "Usage: make gather 100 DATABASE=data/training-depth8.db DEPTH=8 MIN_OPENING_PLIES=8 MAX_OPENING_PLIES=20 THREADS=8"; \
		exit 2; \
	fi
	@case "$(GATHER_COUNT)" in \
		*[!0-9]*|0) echo "Count must be a positive whole number."; exit 2 ;; \
	esac
	@TRAINING_DATABASE_PATH="$(DATABASE)" mvn -q compile exec:java \
		-Dexec.mainClass=com.becker.DataCollector \
		-Dexec.args="$(GATHER_COUNT) $(DEPTH) $(MIN_OPENING_PLIES) $(MAX_OPENING_PLIES) $(THREADS)"

validate-data:
	@TRAINING_DATABASE_PATH="$(DATABASE)" mvn -q compile exec:java \
		-Dexec.mainClass=com.becker.DataValidator

test-python:
	@PYTHONPATH=src/main/python .venv/bin/python -m unittest discover -s src/test/python -v

test: test-python
	mvn test

train:
	@PYTHONPATH=src/main/python .venv/bin/python src/main/python/train.py \
		--database "$(DATABASE)" --output "$(MODEL)"

$(filter-out all build run clean gather validate-data test-python test train,$(MAKECMDGOALS)):
	@:
