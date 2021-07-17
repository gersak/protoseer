proto_repo=proto

# Install protoc-gen-grpc-java
# Follow instructions on https://github.com/grpc/grpc-java/tree/master/compiler
# Run steps 1..3, and download file that matches target arch
# Set file executable, rename it and put on PATH
# If that doesn't work than set --plugin=/abs_path_to_executable/
proto.java:
	protoc \
		--proto_path="$(proto_repo)" \
		--grpc-java_out="src/java"  \
		--java_out="src/java" \
		$(proto_repo)/eywa/tasks.proto \
		$(proto_repo)/eywa/tasks/service.proto


compile.grpc.java:
	javac -cp $(shell clj -A:java -Spath) --source-path src/java -d src/classes $(shell find src/java -name "*.java")

dev.clj:
	clj -A:hr:dev:cider -M:nrepl -m nrepl.cmdline \
		--middleware "[cider.nrepl/cider-middleware]"

.PHONY: proto
