package com.example.demo;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

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

    static JavaSymbolSolver symbolResolver;

    private static void setupJavaparser(Path sourceDirectory) {
        symbolResolver = new JavaSymbolSolver(new CombinedTypeSolver(
                new JavaParserTypeSolver(sourceDirectory),
                new ReflectionTypeSolver()
        ));

        JavaParser.getStaticConfiguration()
                .setSymbolResolver(symbolResolver);
    }

    // mvn exec:exec -Dexec.executable=java -Dexec.args="-classpath target/classes com.example.demo.App"
    public static void main(String[] args) throws IOException {
        String projectDir = System.getProperty("user.dir");
        Path javaFolder = Paths.get(projectDir, "src", "main", "java");

        setupJavaparser(javaFolder);

        try (Stream<Path> paths = Files.walk(javaFolder).filter(matcher::matches)) {
            List<CompilationUnit> cus = new ArrayList<>();
            for (Path path : paths.collect(toList())) {
                cus.add(parseFileToCU(path));
            }

            Set<ClassOrInterfaceDeclaration> implFuns = new HashSet<>();
            traverse(cus, implFuns);

            List<FtypFunction> ftypFunctions = new ArrayList<>();
            for (ClassOrInterfaceDeclaration implFun : implFuns) {
                ftypFunctions.add(convert(implFun));
            }

            System.out.println(ftypFunctions);
        } catch (IOException e) {
            throw new RuntimeException("Failed to traverse source folder");
        }
    }

    private static FtypFunction convert(ClassOrInterfaceDeclaration implFun) {
        MethodDeclaration applyMethod = findMainApplyMethod(implFun);

        if (applyMethod == null) {
            throw new RuntimeException("Could not find main apply method");
        }

        if (applyMethod.getParameters().size() != 1) {
            throw new RuntimeException("Function's arity is not supported");
        }

        BlockStmt block = inline(applyMethod);

        Type inType = applyMethod.getParameter(0).getType();
        Type outType = applyMethod.getType();

        Optional<BlockStmt> bodyOpt = applyMethod.getBody();

        if (!bodyOpt.isPresent()) {
            throw new RuntimeException("Failed to find #apply method");
        }

        LambdaExpr lambdaExpr = new LambdaExpr(applyMethod.getParameters(), bodyOpt.get(), true);

        return new FtypFunction(inType.asString(), outType.asString(), lambdaExpr.toString());
    }

    private static BlockStmt inline(MethodDeclaration applyMethod) {
        CompilationUnit compilationUnit = applyMethod.findCompilationUnit().get();

        List<MethodCallExpr> usedMethods = applyMethod.findAll(MethodCallExpr.class);

        return null;
    }

    private static MethodDeclaration findMainApplyMethod(ClassOrInterfaceDeclaration implFun) {
        Optional<CompilationUnit> compilationUnitOpt = implFun.findCompilationUnit();
        if (!compilationUnitOpt.isPresent()) {
            throw new RuntimeException("CompilationUnit was not found for " + implFun);
        }

        CompilationUnit compilationUnit = compilationUnitOpt.get();
        MethodDeclaration result = null;

        List<MethodDeclaration> applyMethods = implFun.getMethodsByName("apply");
        for (MethodDeclaration applyMethod : applyMethods) {
            if (applyMethod.getParameters().size() == 1) {
                result = applyMethod;
                break;
            }
        }

        return result;
    }

    private static void traverse(List<CompilationUnit> cus, Set<ClassOrInterfaceDeclaration> implFuns) {
        Map<ClassOrInterfaceDeclaration, Set<ClassOrInterfaceDeclaration>> extendsRels = new HashMap<>();
        Map<ClassOrInterfaceDeclaration, ClassOrInterfaceDeclaration> superRels = new HashMap<>();
        List<ClassOrInterfaceDeclaration> baseTypes = new ArrayList<>(); // that extend java's Function
        Map<String, ClassOrInterfaceDeclaration> typeDeclDictionary = generateTypeDeclDictionary(cus);

        for (ClassOrInterfaceDeclaration currentTypeDecl : typeDeclDictionary.values()) {
            ClassOrInterfaceDeclaration coid = currentTypeDecl.asClassOrInterfaceDeclaration();
            NodeList<ClassOrInterfaceType> extendedTypes = coid.getExtendedTypes();
            NodeList<ClassOrInterfaceType> implementedTypes = coid.getImplementedTypes();

            if (extendsJavaFunction(extendedTypes) || extendsJavaFunction(implementedTypes)) {
                baseTypes.add(currentTypeDecl);
            }

            for (ClassOrInterfaceType extendedType : extendedTypes) {
                String nameAsString = extendedType.getNameAsString();
                ClassOrInterfaceDeclaration extendedTypeDecl = typeDeclDictionary.get(nameAsString);

                extendsRels.computeIfAbsent(currentTypeDecl, $_ -> new HashSet<>())
                        .add(extendedTypeDecl);
                superRels.put(extendedTypeDecl, currentTypeDecl);
            }

            for (ClassOrInterfaceType implementedType : implementedTypes) {
                String nameAsString = implementedType.getNameAsString();
                ClassOrInterfaceDeclaration implementedTypeDecl = typeDeclDictionary.get(nameAsString);

                extendsRels.computeIfAbsent(currentTypeDecl, $_ -> new HashSet<>())
                        .add(implementedTypeDecl);
                superRels.put(implementedTypeDecl, currentTypeDecl);
            }
        }

        implFuns.addAll(findFunctionImpls(baseTypes, superRels));
    }

    private static Set<ClassOrInterfaceDeclaration> findFunctionImpls(List<ClassOrInterfaceDeclaration> baseTypes, Map<ClassOrInterfaceDeclaration, ClassOrInterfaceDeclaration> superRels) {
        Set<ClassOrInterfaceDeclaration> result = new HashSet<>();
        for (ClassOrInterfaceDeclaration baseType : baseTypes) {
            findFunctionImpl(baseType, superRels, result);
        }

        return result;
    }

    private static void findFunctionImpl(ClassOrInterfaceDeclaration baseType, Map<ClassOrInterfaceDeclaration, ClassOrInterfaceDeclaration> superRels, Set<ClassOrInterfaceDeclaration> acc) {
        if (baseType == null) {
            return;
        }

        boolean isImpl = baseType.isPublic();
        isImpl &= !baseType.isInterface();
        isImpl &= !baseType.isNestedType();
        isImpl &= !baseType.isStatic();
        isImpl &= !baseType.isAbstract();

        if (isImpl) {
            acc.add(baseType);
        }

        findFunctionImpl(superRels.get(baseType), superRels, acc);
    }

    private static Map<String, ClassOrInterfaceDeclaration> generateTypeDeclDictionary(List<CompilationUnit> cus) {
        Map<String, ClassOrInterfaceDeclaration> result = new HashMap<>();
        for (CompilationUnit cu : cus) {
            NodeList<TypeDeclaration<?>> types = cu.getTypes();
            NodeList<TypeDeclaration<?>> _types = types == null ? new NodeList<>() : types;

            for (TypeDeclaration<?> typeDecl : _types) {
                // put into accumulator primary and nested types;
                if (typeDecl instanceof ClassOrInterfaceDeclaration) {
                    result.put(typeDecl.getNameAsString(), (ClassOrInterfaceDeclaration) typeDecl);
                } else {
                    System.out.println("skipping " + typeDecl);
                }
            }
        }

        return result;
    }


    private static boolean extendsJavaFunction(NodeList<ClassOrInterfaceType> types) {
        boolean result = false;
        for (ClassOrInterfaceType type : types) {
            // TODO add package test
            if (Objects.equals(type.getNameAsString(), "Function")) {
                result = true;
                break;
            }
        }
        return result;
    }

    private static CompilationUnit parseFileToCU(Path javaFile) throws RuntimeException {
        try {
            return JavaParser.parse(javaFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse " + javaFile.toString());
        }
    }

    private static class FtypFunction {

        private final String inType;

        private final String outType;

        private final String lambda;

        public FtypFunction(String inType, String outType, String lambda) {
            this.inType = inType;
            this.outType = outType;
            this.lambda = lambda;
        }
    }

}
