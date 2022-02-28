package site.hanschen.openharmony.cmake;

import org.apache.commons.cli.*;

import java.io.File;

/**
 * @author chenhang
 */
public class Main {

    public static void main(String[] args) {
        CommandLine commandLine = parserOption(args);
        if (commandLine == null) {
            return;
        }

        String sourceDir = commandLine.getOptionValue("sourceDir", "./");
        String cmakeDir = commandLine.getOptionValue("cmakeDir", sourceDir + "/cmake");
        String productName = commandLine.getOptionValue("productName");
        String targetPlatform = commandLine.getOptionValue("targetPlatform", "linux");
        String transformDir = commandLine.getOptionValue("transformDir");
        try {
            Generator generator = new Generator(new File(sourceDir), new File(cmakeDir), productName, targetPlatform, transformDir);
            generator.generate();
        } catch (Exception e) {
            Log.println(e.toString(), Log.RED);
            Log.printStackTrace(e);
        }
    }

    private static CommandLine parserOption(String[] args) {
        Options options = new Options();

        Option opt = new Option("s", "sourceDir", true, "OpenHarmony Source Dir");
        opt.setRequired(false);
        options.addOption(opt);


        opt = new Option("p", "productName", true, "Product Name");
        opt.setRequired(true);
        options.addOption(opt);


        opt = new Option("c", "cmakeDir", true, "Generate Dir");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("targetPlatform", "targetPlatform", true, "Target Platform, [linux|windows]");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("transformDir", "transformDir", true, "Change source base dir of CMakeLists.txt");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("h", "help", false, "Print help");
        opt.setRequired(false);
        options.addOption(opt);

        HelpFormatter hf = new HelpFormatter();
        hf.setWidth(110);

        try {
            CommandLineParser parser = new DefaultParser();
            CommandLine commandLine = parser.parse(options, args);

            if (commandLine.hasOption("h")) {
                hf.printHelp("CMakeGenerator", options, true);
            }
            return commandLine;
        } catch (Exception e) {
            hf.printHelp("CMakeGenerator", options, true);
            Log.println("\n" + e, Log.RED);
        }
        return null;
    }
}
