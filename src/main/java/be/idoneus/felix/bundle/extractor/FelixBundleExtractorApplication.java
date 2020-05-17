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

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
            String bundlesInputDir = cmd.getOptionValue("bundlesInputDir");
            String bundlesOutputDir = cmd.getOptionValue("bundlesOutputDir");

            BundleExtractorService bundleExtractorService = new BundleExtractorService(bundlesInputDir,
                    bundlesOutputDir);
            bundleExtractorService.run();

        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("felix-bundle-extractor", options);
            System.exit(1);
        }

    }

}
