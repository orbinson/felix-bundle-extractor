package be.orbinson.felix.bundle.extractor;

import org.apache.commons.cli.*;

public class FelixBundleExtractorApplication {

    public static void main(String[] args) {
        Options options = createOptions();
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        try {
            CommandLine cmd = parser.parse(options, args);

            BundleExtractorConfig config = new BundleExtractorConfig();
            config.setBundleInputDir(cmd.getOptionValue("bundlesInputDir"));
            config.setBundleOutputDir(cmd.getOptionValue("bundlesOutputDir"));
            config.setOutputResultFileName(cmd.getOptionValue("outputResultFileName", "result.json"));
            config.setThreadCount(Integer.parseInt(cmd.getOptionValue("threadCount", "8")));
            config.setIncludeGroupIds(cmd.getOptionValue("includedGroupIds"));
            config.setExcludeGroupIds(cmd.getOptionValue("excludedGroupIds"));
            config.setExcludeArtifactIds(cmd.getOptionValue("excludedArtifactIds"));
            config.setExcludeNonMavenArtifacts(cmd.hasOption("excludeNonMavenArtifacts"));

            BundleExtractorManager bundleExtractorService = new BundleExtractorManager(config);
            bundleExtractorService.run();

        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("felix-bundle-extractor", options);
            System.exit(1);
        }

    }

    private static Options createOptions() {
        Options options = new Options();

        Option input = new Option("i", "bundlesInputDir", true, "bundles input directory");
        input.setRequired(true);
        options.addOption(input);

        Option output = new Option("o", "bundlesOutputDir", true, "bundles output directory");
        output.setRequired(true);
        options.addOption(output);

        Option outputResultFileName = new Option("orfn", "outputResultFileName", true, "output result file name");
        options.addOption(outputResultFileName);

        Option threadCount = new Option("t", "threadCount", true, "thread count for the bundle extraction");
        options.addOption(threadCount);

        Option includedGroupIds = new Option("ig", "includedGroupIds", true,
                "Regex pattern to include group id's for decompilation");
        options.addOption(includedGroupIds);

        Option excludedGroupIds = new Option("eg", "excludedGroupIds", true,
                "Regex pattern to exclude group id's from decompilation");
        options.addOption(excludedGroupIds);

        Option excludedArtifactIds = new Option("ea", "excludedArtifactIds", true,
                "Regex pattern to exclude artificact id's from decompilation");
        options.addOption(excludedArtifactIds);

        Option exludeNonMavenArtifacts = new Option("enma", "excludeNonMavenArtifacts", false,
                "Exclude artifacts that don't contain a maven pom");
        options.addOption(exludeNonMavenArtifacts);

        return options;
    }

}
