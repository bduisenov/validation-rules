package com.example.demo;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.*;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.stream.Stream;

import static java.text.MessageFormat.format;
import static java.util.stream.Collectors.toList;

public class App {

    static String template = "" +
            "    {0}: \n" +
            "      inputType: {1} \n" +
            "      outputType: {2} \n" +
            "      lambda: | \n" +
            "{3}";

    static PathMatcher matcher = FileSystems.getDefault()
            .getPathMatcher("glob:**.{java}");

    // mvn exec:exec -Dexec.executable=java -Dexec.args="-classpath target/classes com.example.demo.App"
    public static void main(String[] args) throws IOException {
        String projectDir = System.getProperty("user.dir");
        Path javaFolder = Paths.get(projectDir, "src", "main", "java");

        try (Stream<Path> paths = Files.walk(javaFolder).filter(matcher::matches)) {
            StringJoiner rules = new StringJoiner("\n");
            for (String rule : paths.map(App::parse).collect(toList())) {
                if (rule != null) {
                    rules.add(rule);
                }
            }

            String content = "spring.cloud.function:\n  compile:\n" + rules;

            try (PrintStream out = new PrintStream(new FileOutputStream("./application-validation.yml"))) {
                out.print(content);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    // @Nullable
    static String parse(Path javaFile) {
        try {
            CompilationUnit cu = JavaParser.parse(javaFile);
            Optional<TypeDeclaration<?>> publicType = cu.getPrimaryType();
            if (!publicType.isPresent()) {
                throw new IllegalArgumentException("Cannot proceed with " + javaFile.toAbsolutePath());
            }

            TypeDeclaration<?> td = publicType.get();

            NodeList<ClassOrInterfaceType> implementedTypes = td.asClassOrInterfaceDeclaration().getImplementedTypes();
            if (implementedTypes.isEmpty() || !Objects.equals("ValidationRule", implementedTypes.get(0).getName().asString())) {
                return null;
            }

            if (!td.isClassOrInterfaceDeclaration()) {
                throw new IllegalArgumentException("Cannot proceed with " + javaFile.toAbsolutePath());
            }

            List<MethodDeclaration> methods = td.getMethodsByName("apply");

            if (!(methods.size() == 1)) {
                throw new IllegalArgumentException("Cannot proceed with " + javaFile.toAbsolutePath());
            }

            MethodDeclaration mainMethod = methods.get(0);

            NodeList<Parameter> parameters = mainMethod.getParameters();
            Optional<BlockStmt> methodBody = mainMethod.getBody();

            String validationRuleName = td.getName().asString();

            validationRuleName = validationRuleName.substring(0, 1).toLowerCase()
                    + validationRuleName.substring(1);

            if (!(parameters.size() == 1)) {
                throw new IllegalArgumentException("Cannot proceed with " + javaFile.toAbsolutePath());
            }

            String in = parameters.get(0).getType().asString();
            String out = mainMethod.getType().asString();

            LambdaExpr lambda = new LambdaExpr(parameters, methodBody.get(), true);

            StringBuilder lambdaString = new StringBuilder();
            for (String line : lambda.toString().split("\n")) {
                lambdaString.append("        ").append(line).append('\n');
            }

            return format(template, validationRuleName, in, out, lambdaString);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
