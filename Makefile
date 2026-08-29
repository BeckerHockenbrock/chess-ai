ifeq ($(OS),Windows_NT)
    PYTHON := $(if $(wildcard .venv/Scripts/python.exe),.venv\Scripts\python.exe,python)
    THREADS ?= $(or $(NUMBER_OF_PROCESSORS),4)
    RUN_PYTHON = set "PYTHONPATH=src/main/python;%PYTHONPATH%" && $(PYTHON)
else
    PYTHON := $(if $(wildcard .venv/bin/python),.venv/bin/python,python3)
    THREADS ?= $(shell sysctl -n hw.ncpu 2>/dev/null || nproc 2>/dev/null || echo 4)
    RUN_PYTHON = PYTHONPATH=src/main/python $(PYTHON)
endif

all: build run

.PHONY: all build run clean gather validate-data test-python test train

GATHER_COUNT := $(word 2,$(MAKECMDGOALS))
DATABASE ?= data/training.db
MODEL ?= data/chess_model.pt
DEPTH ?= 4
MIN_OPENING_PLIES ?= 4
MAX_OPENING_PLIES ?= 8
EPOCHS ?= 30
BATCH_SIZE ?= 512
LR ?= 0.001
BLOCKS ?= 6
CHANNELS ?= 128
PATIENCE ?= 6

build:
	mvn compile

run: build
	mvn javafx:run

clean:
	mvn clean

gather:
ifeq ($(GATHER_COUNT),)
	$(error Usage: make gather 100 [DATABASE=data/training-depth8.db] [DEPTH=8] [MIN_OPENING_PLIES=8] [MAX_OPENING_PLIES=20] [THREADS=8])
endif
	mvn -q compile exec:java -Dtraining.database.path="$(DATABASE)" -Dexec.mainClass=com.becker.DataCollector -Dexec.args="$(GATHER_COUNT) $(DEPTH) $(MIN_OPENING_PLIES) $(MAX_OPENING_PLIES) $(THREADS)"

validate-data:
	mvn -q compile exec:java -Dtraining.database.path="$(DATABASE)" -Dexec.mainClass=com.becker.DataValidator

test-python:
	$(RUN_PYTHON) -m unittest discover -s src/test/python -v

test: test-python
	mvn test

train:
	$(RUN_PYTHON) src/main/python/train.py --database "$(DATABASE)" --output "$(MODEL)" --epochs $(EPOCHS) --batch-size $(BATCH_SIZE) --lr $(LR) --blocks $(BLOCKS) --channels $(CHANNELS) --patience $(PATIENCE)

$(filter-out all build run clean gather validate-data test-python test train,$(MAKECMDGOALS)):
	@:
