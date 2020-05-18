package be.idoneus.felix.bundle.extractor;

import java.util.Arrays;
import java.util.Optional;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

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
            config.setThreadCount(Integer.valueOf(cmd.getOptionValue("threadCount", "8")));
            config.setExcludedDecompilation(Arrays
                    .asList(Optional.ofNullable(cmd.getOptionValues("excludeDecompilation")).orElse(new String[0])));

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

        Option excludeDecompilation = new Option("ed", "excludeDecompilation", true,
                "Exclude artificact id's from decompilation");
        options.addOption(excludeDecompilation);

        return options;
    }

}
