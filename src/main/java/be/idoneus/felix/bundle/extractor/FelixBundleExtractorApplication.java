package be.idoneus.felix.bundle.extractor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class FelixBundleExtractorApplication implements CommandLineRunner {

    @Autowired
    private BundleExtractorService bundleExtractorService;

    private static ConfigurableApplicationContext ctx;

    public static void main(String[] args) {
        ctx = SpringApplication.run(FelixBundleExtractorApplication.class, args);
    }

    @Override
    public void run(String... strings) {
        bundleExtractorService.run();
        if (ctx != null) {
            SpringApplication.exit(ctx);
        } else {
            System.exit(0);
        }
    }
}
