package site.hanschen.openharmony.cmake;

import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;

public class Generator {

    private final File sourceDir;
    private final File cmakeDir;
    private final String productName;

    public Generator(File sourceDir, File cmakeDir, String productName) {
        this.sourceDir = sourceDir;
        this.cmakeDir = cmakeDir;
        this.productName = productName;
    }

    public void generate() throws Exception {
        Log.println("Generate start", Log.GREEN);
        Log.println("sourceDir   : " + sourceDir.getCanonicalPath());
        Log.println("cmakeDir    : " + cmakeDir.getCanonicalPath());
        Log.println("productName : " + productName + "\n");

        File ninjaRoot = new File(sourceDir, "out/" + productName + "/obj");
        if (!ninjaRoot.isDirectory() || !ninjaRoot.exists()) {
            throw new IllegalArgumentException("Incorrect sourceDir, select the OpenHarmony source dir and compile first");
        }
        deleteExclude(cmakeDir, file -> file.getName().equals(".idea"));

        final Map<String, StringBuilder> cmakeBuilders = new LinkedHashMap<>();
        StringBuilderGetter stringBuilderGetter = path -> {
            StringBuilder builder = cmakeBuilders.get(path);
            if (builder == null) {
                builder = new StringBuilder();
                cmakeBuilders.put(path, builder);
            }
            return builder;
        };
        forAllFile(ninjaRoot, new NinjaToCmakeHandler(ninjaRoot, cmakeDir, stringBuilderGetter));

        String cPath = new File(sourceDir, "prebuilts/clang/ohos/linux-x86_64/llvm/bin/clang").getAbsolutePath();
        String cppPath = new File(sourceDir, "prebuilts/clang/ohos/linux-x86_64/llvm/bin/clang++").getAbsolutePath();
        String head = "# THIS FILE WAS AUTOMATICALY GENERATED, DO NOT MODIFY!\n" + "cmake_minimum_required(VERSION 3.6)\n";
        for (Map.Entry<String, StringBuilder> entry : cmakeBuilders.entrySet()) {
            File file = new File(entry.getKey());
            FileUtils.forceMkdirParent(file);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(head);
            String projectName = file.getParentFile().getName() + "-" + Integer.toHexString(entry.getKey().hashCode());
            stringBuilder.append("project(").append(projectName).append(")\n\n");
            stringBuilder.append(String.format("set(CMAKE_C_COMPILER \"%s\")\n", cPath));
            stringBuilder.append(String.format("set(CMAKE_CXX_COMPILER \"%s\")\n\n", cppPath));

            stringBuilder.append(entry.getValue().toString());
            stringBuilder.append(String.format("\nadd_executable(%s ${SOURCE_FILES})\n", projectName));
            FileUtils.writeStringToFile(file, stringBuilder.toString(), StandardCharsets.UTF_8);
        }

        StringBuilder rootCmakeList = new StringBuilder();
        rootCmakeList.append("cmake_minimum_required(VERSION 3.6)\n" + "project(OpenHarmony)\n\n");
        for (Map.Entry<String, StringBuilder> entry : cmakeBuilders.entrySet()) {
            File file = new File(entry.getKey());
            String relative = relative(cmakeDir, file);
            rootCmakeList.append(String.format("# add_subdirectory(%s)\n", relative));
        }
        FileUtils.writeStringToFile(new File(cmakeDir, "CMakeLists.txt"), rootCmakeList.toString(), StandardCharsets.UTF_8);

        Log.println("\nSuccessful : " + cmakeDir.getCanonicalPath(), Log.GREEN);
    }

    private void deleteExclude(File file, FileFilter fileFilter) throws IOException {
        if (fileFilter.accept(file)) {
            return;
        }
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    deleteExclude(f, fileFilter);
                }
            }
            files = file.listFiles();
            if (files == null || files.length == 0) {
                FileUtils.deleteDirectory(file);
            }
        } else {
            if (file.exists()) {
                FileUtils.forceDelete(file);
            }
        }
    }

    private static String relative(File base, File target) throws IOException {
        return Paths.get(base.getCanonicalPath()).relativize(Paths.get(target.getParentFile().getCanonicalPath())).toString();
    }

    private interface StringBuilderGetter {

        StringBuilder get(String path);
    }

    private static class NinjaToCmakeHandler implements FileHandler {

        private final List<LineParser> parsers = new ArrayList<>();
        private final File ninjaRoot;
        private final StringBuilderGetter stringBuilderGetter;
        private final File cmakeRoot;

        public NinjaToCmakeHandler(File ninjaRoot, File cmakeRoot, StringBuilderGetter stringBuilderGetter) {
            this.ninjaRoot = ninjaRoot;
            this.cmakeRoot = cmakeRoot;
            this.stringBuilderGetter = stringBuilderGetter;
            parsers.add(new VariableParser("defines"));
            parsers.add(new VariableParser("include_dirs"));
            parsers.add(new VariableParser("cflags"));
            parsers.add(new VariableParser("cflags_cc"));
            parsers.add(new VariableParser("label_name"));
            parsers.add(new VariableParser("root_out_dir"));
            parsers.add(new VariableParser("target_output_name"));
            parsers.add(new BuildParser());
        }

        @Override
        public void handle(File file) throws Exception {
            if (!file.getName().endsWith(".ninja")) {
                return;
            }
            Log.println("Process: " + file.getCanonicalPath());

            String relativize = relative(ninjaRoot, file);
            File CMakeLists = new File(cmakeRoot, relativize + "/CMakeLists.txt");
            FileUtils.forceMkdirParent(CMakeLists);

            StringBuilder cmakeBuilder = stringBuilderGetter.get(CMakeLists.getCanonicalPath());

            Map<String, Object> result = new HashMap<>();
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                handleLine(result, line);
            }
            reader.close();

            cmakeBuilder.append("\n# CMakeLists rule for: ").append(file.getCanonicalPath()).append("\n");

            // src
            if (result.containsKey("build")) {
                cmakeBuilder.append("\n# src: \nlist(APPEND\n    SOURCE_FILES\n");
                @SuppressWarnings("unchecked") ArrayList<String> builds = (ArrayList<String>) result.get("build");
                for (String b : builds) {
                    addSourceFile(cmakeBuilder, b);
                }
                cmakeBuilder.append(")\n");
            }

            // include
            if (result.containsKey("include_dirs")) {
                String include = (String) result.get("include_dirs");
                cmakeBuilder.append("\n# include:\n# ").append(include).append("\n");
                cmakeBuilder.append("include_directories(\n");
                StringTokenizer st = new StringTokenizer(include);
                while (st.hasMoreTokens()) {
                    File header = new File(ninjaRoot.getParentFile().getCanonicalPath() + "/" + st.nextToken().replaceFirst("-I", ""));
                    if (header.exists()) {
                        cmakeBuilder.append("    \"").append(header.getCanonicalPath()).append("\"\n");
                    } else {
                        cmakeBuilder.append("#   \"").append(header.getCanonicalPath()).append("\"\n");
                        Log.println("Ignored includeDir: " + header.getCanonicalPath(), Log.YELLOW);
                    }
                }
                cmakeBuilder.append(")\n");
            }

            // define
            if (result.containsKey("defines")) {
                addFlags(cmakeBuilder, "defines", (String) result.get("defines"), true);
            }

            // cflags
            if (result.containsKey("cflags")) {
                addFlags(cmakeBuilder, "cflags", (String) result.get("cflags"), false);
            }

            // cflags
            if (result.containsKey("cflags_cc")) {
                addFlags(cmakeBuilder, "cflags_cc", (String) result.get("cflags_cc"), false);
            }
        }

        private void addFlags(StringBuilder builder, String name, String line, boolean enable) {
            line = line.replace("\\$ =\\$ ", "=");
            builder.append("\n# ").append(name).append(":\n# ").append(line).append("\n");
            StringTokenizer st = new StringTokenizer(line);
            String[] defineArray = new String[st.countTokens()];
            for (int i = 0; st.hasMoreTokens(); i++) {
                defineArray[i] = st.nextToken();
            }
            for (String d : defineArray) {
                String content = String.format("set(CMAKE_C_FLAGS \"${CMAKE_C_FLAGS} %s\")\n", d);
                if (!enable) {
                    content = "# " + content;
                }
                if (!builder.toString().contains(content)) {
                    builder.append(content);
                }

            }
            for (String d : defineArray) {
                String content = String.format("set(CMAKE_CXX_FLAGS \"${CMAKE_CXX_FLAGS} %s\")\n", d);
                if (!enable) {
                    content = "# " + content;
                }
                if (!builder.toString().contains(content)) {
                    builder.append(content);
                }
            }
        }

        private void addSourceFile(StringBuilder builder, String build) throws IOException {
            int index = build.indexOf(":");
            if (index < 0) {
                return;
            }
            build = build.substring(index + 1);
            build = build.replaceAll("\\|\\|", "");
            build = build.replaceAll("cxx", "");
            build = build.replaceAll("cc", "");

            StringTokenizer st = new StringTokenizer(build);
            while (st.hasMoreTokens()) {
                String src = st.nextToken();
                if (src.endsWith(".cpp") || src.endsWith(".c")) {
                    File srcFile = new File(ninjaRoot.getParentFile().getCanonicalPath() + "/" + src);
                    if (srcFile.exists()) {
                        builder.append("    ").append(srcFile.getCanonicalPath()).append("\n");
                    } else {
                        builder.append("#   ").append(srcFile.getCanonicalPath()).append("\n");
                        Log.println("Ignored srcFile: " + srcFile.getCanonicalPath(), Log.YELLOW);
                    }
                }
            }
        }

        private void handleLine(Map<String, Object> map, String line) {
            for (LineParser parser : parsers) {
                String result = parser.parse(line);
                if (result != null) {
                    if (parser instanceof VariableParser) {
                        map.put(parser.getName(), result);
                    } else if (parser instanceof BuildParser) {
                        @SuppressWarnings("unchecked") List<String> builds = (List<String>) map.get(parser.getName());
                        if (builds == null) {
                            builds = new ArrayList<>();
                            map.put(parser.getName(), builds);
                        }
                        builds.add(result);
                    }
                    return;
                }
            }
        }
    }

    private void forAllFile(File file, FileHandler handler) throws Exception {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File f : children) {
                    forAllFile(f, handler);
                }
            }
        } else {
            handler.handle(file);
        }
    }

    private interface FileHandler {

        void handle(File file) throws Exception;
    }

    private interface LineParser {

        String parse(String line);

        String getName();
    }

    private static class BuildParser implements LineParser {

        @Override
        public String parse(String line) {
            if (line.startsWith("build ") && (line.contains(".cpp") || line.contains(".c"))) {
                return line;
            }
            return null;
        }

        @Override
        public String getName() {
            return "build";
        }
    }

    private static class VariableParser implements LineParser {

        private final String name;

        public VariableParser(String name) {
            this.name = name;
        }

        @Override
        public String parse(String line) {
            String prefix = name + " = ";
            if (!line.startsWith(prefix)) {
                return null;
            }
            return line.substring(prefix.length());
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
