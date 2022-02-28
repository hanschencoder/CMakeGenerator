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
    private final String targetPlatform;
    private final String transformDir;

    public Generator(File sourceDir, File cmakeDir, String productName, String targetPlatform, String transformDir) {
        this.sourceDir = sourceDir;
        this.cmakeDir = cmakeDir;
        this.productName = productName;
        this.targetPlatform = targetPlatform;
        this.transformDir = transformDir;
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

        NinjaToCmakeHandler handler = new NinjaToCmakeHandler(ninjaRoot, cmakeDir);
        forAllFile(ninjaRoot, handler);


        Log.println("\nBuilding CMakeLists.txt...");
        String cPath;
        String cppPath;
        if (targetPlatform.equals("windows")) {
            cPath = new File(sourceDir, "prebuilts/clang/ohos/windows-x86_64/llvm/bin/clang").getAbsolutePath();
            cppPath = new File(sourceDir, "prebuilts/clang/ohos/windows-x86_64/llvm/bin/clang++").getAbsolutePath();
        } else {
            cPath = new File(sourceDir, "prebuilts/clang/ohos/linux-x86_64/llvm/bin/clang").getAbsolutePath();
            cppPath = new File(sourceDir, "prebuilts/clang/ohos/linux-x86_64/llvm/bin/clang++").getAbsolutePath();
        }
        String head = "# THIS FILE WAS AUTOMATICALY GENERATED, DO NOT MODIFY!\n" + "cmake_minimum_required(VERSION 3.6)\n";
        for (Map.Entry<String, List<NinjaEntry>> entry : handler.getAllNinja().entrySet()) {
            File CMakeLists = new File(entry.getKey());
            FileUtils.forceMkdirParent(CMakeLists);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(head);
            String projectName = CMakeLists.getParentFile().getName() + "-" + Integer.toHexString(entry.getKey().hashCode());
            stringBuilder.append("project(").append(projectName).append(")\n\n");
            stringBuilder.append(String.format("set(CMAKE_C_COMPILER \"%s\")\n", cPath));
            stringBuilder.append(String.format("set(CMAKE_CXX_COMPILER \"%s\")\n\n", cppPath));

            for (NinjaEntry ninjaEntry : entry.getValue()) {
                stringBuilder.append("\n# CMakeLists rule for: ").append(ninjaEntry.ninjaFile.getCanonicalPath()).append("\n");

                // src
                stringBuilder.append("\n# src: \nlist(APPEND\n    SOURCE_FILES\n");
                for (File src : ninjaEntry.srcFiles) {
                    if (src.exists()) {
                        stringBuilder.append("    ").append(transFormPath(src.getCanonicalPath())).append("\n");
                    } else {
                        stringBuilder.append("#   ").append(transFormPath(src.getCanonicalPath())).append("\n");
                    }
                }
                stringBuilder.append(")\n");

                // include
                if (ninjaEntry.includeDirs != null) {
                    stringBuilder.append("\n# include:\n# ").append(ninjaEntry.includeDirs).append("\n");
                    stringBuilder.append("include_directories(\n");
                    StringTokenizer st = new StringTokenizer(ninjaEntry.includeDirs);
                    while (st.hasMoreTokens()) {
                        File header = new File(ninjaRoot.getParentFile().getCanonicalPath() + "/" + st.nextToken().replaceFirst("-I", ""));
                        if (header.exists()) {
                            stringBuilder.append("    \"").append(transFormPath(header.getCanonicalPath())).append("\"\n");
                        } else {
                            stringBuilder.append("#   \"").append(transFormPath(header.getCanonicalPath())).append("\"\n");
                        }
                    }
                    stringBuilder.append(")\n");
                }

                // define
                if (ninjaEntry.defines != null) {
                    addFlags(stringBuilder, "defines", ninjaEntry.defines, true);
                }

                // cflags
                if (ninjaEntry.cflags != null) {
                    addFlags(stringBuilder, "cflags", ninjaEntry.cflags, false);
                }

                // cflags
                if (ninjaEntry.ccflags != null) {
                    addFlags(stringBuilder, "cflags_cc", ninjaEntry.ccflags, false);
                }
            }

            stringBuilder.append(String.format("\nadd_executable(%s ${SOURCE_FILES})\n", projectName));
            FileUtils.writeStringToFile(CMakeLists, stringBuilder.toString(), StandardCharsets.UTF_8);
        }

        List<String> noSrcCMakeList = new ArrayList<>();
        List<String> testCMakeList = new ArrayList<>();
        List<String> normalCMakeList = new ArrayList<>();
        for (Map.Entry<String, List<NinjaEntry>> entry : handler.getAllNinja().entrySet()) {

            File CMakeLists = new File(entry.getKey());
            String relative = relative(cmakeDir, CMakeLists);

            int srcFileCount = 0;
            for (NinjaEntry ninjaEntry : entry.getValue()) {
                for (File f : ninjaEntry.srcFiles) {
                    if (f.exists()) {
                        srcFileCount++;
                    }
                }
            }
            if (srcFileCount == 0) {
                noSrcCMakeList.add(relative);
            } else if (CMakeLists.getParentFile().getName().endsWith("_test") ||
                       CMakeLists.getParentFile().getName().endsWith("tests") ||
                       CMakeLists.getParentFile().getName().endsWith("test") ||
                       CMakeLists.getCanonicalPath().contains("/test/") ||
                       CMakeLists.getCanonicalPath().contains("/tests/") ||
                       CMakeLists.getCanonicalPath().contains("/unittest/")) {
                testCMakeList.add(relative);
            } else {
                normalCMakeList.add(relative);
            }
        }

        StringBuilder rootCmakeList = new StringBuilder();
        rootCmakeList.append("cmake_minimum_required(VERSION 3.6)\n" + "project(OpenHarmony)\n\n");
        for (String path : normalCMakeList) {
            rootCmakeList.append(String.format("# add_subdirectory(%s)\n", path));
        }
        rootCmakeList.append("\n# test module\n");
        for (String path : testCMakeList) {
            rootCmakeList.append(String.format("# add_subdirectory(%s)\n", path));
        }
        rootCmakeList.append("\n# no src module\n");
        for (String path : noSrcCMakeList) {
            rootCmakeList.append(String.format("# add_subdirectory(%s)\n", path));
        }
        FileUtils.writeStringToFile(new File(cmakeDir, "CMakeLists.txt"), rootCmakeList.toString(), StandardCharsets.UTF_8);

        Log.println("\nSuccessful : " + cmakeDir.getCanonicalPath(), Log.GREEN);
    }

    private String transFormPath(String from) throws IOException {
        if (transformDir == null) {
            return from;
        }
        return transformDir + from.substring(sourceDir.getCanonicalPath().length());
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

    private static class NinjaToCmakeHandler implements FileHandler {

        private final List<LineParser> parsers = new ArrayList<>();
        private final File ninjaRoot;
        private final File cmakeRoot;
        private final Map<String, List<NinjaEntry>> allNinja = new LinkedHashMap<>();

        public NinjaToCmakeHandler(File ninjaRoot, File cmakeRoot) {
            this.ninjaRoot = ninjaRoot;
            this.cmakeRoot = cmakeRoot;
            parsers.add(new VariableParser("defines"));
            parsers.add(new VariableParser("include_dirs"));
            parsers.add(new VariableParser("cflags"));
            parsers.add(new VariableParser("cflags_cc"));
            parsers.add(new VariableParser("label_name"));
            parsers.add(new VariableParser("root_out_dir"));
            parsers.add(new VariableParser("target_output_name"));
            parsers.add(new BuildParser());
        }

        public Map<String, List<NinjaEntry>> getAllNinja() {
            return allNinja;
        }

        @Override
        public void handle(File file) throws Exception {
            if (!file.getName().endsWith(".ninja")) {
                return;
            }
            Log.println("Process: " + file.getCanonicalPath());

            String relativize = relative(ninjaRoot, file);
            File CMakeLists = new File(cmakeRoot, relativize + "/CMakeLists.txt");
            String key = CMakeLists.getCanonicalPath();
            List<NinjaEntry> ninjas = allNinja.computeIfAbsent(key, k -> new ArrayList<>());
            NinjaEntry entry = new NinjaEntry();
            ninjas.add(entry);
            entry.ninjaFile = file;

            Map<String, Object> result = new HashMap<>();
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                handleLine(result, line);
            }
            reader.close();

            // src
            if (result.containsKey("build")) {
                @SuppressWarnings("unchecked") ArrayList<String> builds = (ArrayList<String>) result.get("build");
                for (String b : builds) {
                    addSourceFile(entry.srcFiles, b);
                }
            }

            // include
            if (result.containsKey("include_dirs")) {
                entry.includeDirs = (String) result.get("include_dirs");
            }

            // define
            if (result.containsKey("defines")) {
                entry.defines = (String) result.get("defines");
            }

            // cflags
            if (result.containsKey("cflags")) {
                entry.cflags = (String) result.get("cflags");
            }

            // cflags
            if (result.containsKey("cflags_cc")) {
                entry.ccflags = (String) result.get("cflags_cc");
            }
        }

        private void addSourceFile(List<File> srcFiles, String build) throws IOException {
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
                    srcFiles.add(new File(ninjaRoot.getParentFile().getCanonicalPath() + "/" + src));
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
                Arrays.sort(children, Comparator.comparing(File::getName));
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

    private static class NinjaEntry {

        private File ninjaFile;
        private String defines;
        private String includeDirs;
        private String cflags;
        private String ccflags;
        private String label;
        private String rootOutDir;
        private String targetOutputName;
        private final List<File> srcFiles = new ArrayList<>();
    }
}
