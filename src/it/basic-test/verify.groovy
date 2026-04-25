File generatedFile = new File(basedir, "target/generated-sources/qute/test.txt")
assert generatedFile.exists()

String content = generatedFile.text
assert content.contains("Message: Hello, Qute!")
assert content.contains("User: Test User")
assert content.contains("Test Property: Custom Maven Property Value")
assert content.contains("Compiler Source: 17")
