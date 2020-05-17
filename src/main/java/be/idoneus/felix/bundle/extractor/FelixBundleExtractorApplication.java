package be.idoneus.felix.bundle.extractor;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class FelixBundleExtractorApplication {

    public static void main(String[] args) {
        Options options = new Options();

        Option input = new Option("i", "bundlesInputDir", true, "bundles input directory");
        input.setRequired(true);
        options.addOption(input);

        Option output = new Option("o", "bundlesOutputDir", true, "bundles output directory");
        output.setRequired(true);
        options.addOption(output);

        Option threadCount = new Option("t", "threadCount", true, "thread count for the bundle extraction");
        options.addOption(threadCount);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
            
            BundleExtractorConfig config = new BundleExtractorConfig();
            config.setBundleInputDir(cmd.getOptionValue("bundlesInputDir"));
            config.setBundleOutputDir(cmd.getOptionValue("bundlesOutputDir"));
            config.setThreadCount(Integer.valueOf(cmd.getOptionValue("threadCount", "10")));

            BundleExtractorService bundleExtractorService = new BundleExtractorService(config);
            bundleExtractorService.run();

        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("felix-bundle-extractor", options);
            System.exit(1);
        }

    }

}
