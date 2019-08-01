package sssii.jreports;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class JReportsTool {

    private static Logger log = LoggerFactory.getLogger(JReportsTool.class);

    //private JReportsTool(){}

    public static void main(String[] args) {

        Properties version = new Properties();
        InputStream is = JReportsTool.class.getResourceAsStream("/version.properties");
        try {
            version.load(is);
            is.close();
        } catch (IOException e) {
            log.warn("cannot load properties", e);
        }

        log.info("=================================================================");
        log.info("JReportsTool " + version.getProperty("version", "unknown version"));
        log.info("rev  " + version.getProperty("git.commit", "unknown") + " " + version.getProperty("git.date", ""));
        log.info("built        " + version.getProperty("build.date", " at unknown date"));
        log.info("built by " + version.getProperty("build.host", "- host unknown"));
        log.info("build number " + version.getProperty("hudson.build.number", "unknown"));
        log.info("build id     " + version.getProperty("hudson.build.id", "unknown"));
        log.info(System.getProperty("java.vm.name") + " " + System.getProperty("java.runtime.version") + " " + System.getProperty("java.home"));
        log.info("-----------------------------------------------------------------");


        Options options = new Options();

        options.addOption(Option.builder("jsrestapiurl")
                .desc("Base URL of JasperReports Server REST API v2, e.g. http://localhost:8080/jasperserver/rest_v2")
                .numberOfArgs(1)
                .argName("url")
                //.required()
                .build());

        options.addOption(Option.builder("jslogin")
                .desc("JasperServer login name")
                .numberOfArgs(1)
                .argName("login")
                //.required()
                .build());

        options.addOption(Option.builder("jspassword")
                .desc("JasperServer password")
                .numberOfArgs(1)
                .argName("password")
                //.required()
                .build());


        options.addOption(Option.builder("delete")
                .desc("Delete specified resource from server, e.g. /reports/permits")
                .numberOfArgs(1)
                .argName("resource")
                .build());


        options.addOption(Option.builder("import")
                .desc("Import zipped file with reports and xml metadata, e.g. d:\\files\\reports.zip")
                .numberOfArgs(1)
                .argName("file.zip")
                .build());

        options.addOption(Option.builder("prepareimport")
                .desc("Prepare reports for importing to JasperReports Server")
                .numberOfArgs(0)
                .build());
        options.addOption(Option.builder("sourcedir")
                .desc("Root folder with source jrxml report files. Option may be specified several times")
                .numberOfArgs(1)
                .argName("dirName")
                .build());
        options.addOption(Option.builder("tempdir")
                .desc("Intermediate folder for generated files")
                .numberOfArgs(1)
                .argName("dirName")
                .build());
        options.addOption(Option.builder("targetzip")
                .desc("Target zip file")
                .numberOfArgs(1)
                .argName("zipFileName")
                .build());
        options.addOption(Option.builder("targetresource")
                .desc("Target resource directory on JasperReports Server, e.g. /reports/permits")
                .numberOfArgs(1)
                .argName("resource")
                .build());
        options.addOption(Option.builder("dsname")
                .desc("Name of the datasource")
                .numberOfArgs(1)
                .argName("name")
                .build());
        options.addOption(Option.builder("dsdriver")
                .desc("Data source jdbc driver class, e.g org.mariadb.jdbc.Driver")
                .numberOfArgs(1)
                .argName("className")
                .build());
        options.addOption(Option.builder("dsurl")
                .desc("Data source connection URL, e.g jdbc:mysql://localhost:3306/permits")
                .numberOfArgs(1)
                .argName("connectionUrl")
                .build());
        options.addOption(Option.builder("dslogin")
                .desc("Data source connection login")
                .numberOfArgs(1)
                .argName("login")
                .build());
        options.addOption(Option.builder("dspassword")
                .desc("Data source connection password")
                .numberOfArgs(1)
                .argName("password")
                .build());
        options.addOption(Option.builder("exclude")
                .desc("Skip report files and dirs which names match patterns listed in this file")
                .numberOfArgs(1)
                .argName("patternsFile")
                .build());

        options.addOption(Option.builder("verbose")
                .desc("Verbosity level: TRACE, DEBUG, INFO, WARN, ERROR, ALL or OFF. Default level is INFO")
                .numberOfArgs(1)
                .argName("level")
                .build());


        HelpFormatter formatter = new HelpFormatter();
        CommandLineParser parser = new DefaultParser();
        formatter.setOptionComparator(null);

        try {
            CommandLine cmdline = parser.parse(options, args);

            // set log level
            String logLevel = "INFO";
            if(cmdline.hasOption("verbose")){
                logLevel = cmdline.getOptionValue("verbose").toUpperCase();
            }
            // note we're using log4j directly while slf4j should manage abstraction
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            Configuration config = ctx.getConfiguration();
            LoggerConfig loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
            loggerConfig.setLevel(Level.toLevel(logLevel));
            ctx.updateLoggers();

            if(cmdline.hasOption("delete") || cmdline.hasOption("import")){

                if(!(cmdline.hasOption("jsrestapiurl") && cmdline.hasOption("jslogin") && cmdline.hasOption("jspassword"))){
                    log.error("For this operation, jsrestapiurl, jslogin and jspassword options are required");
                    System.exit(1);
                    return;
                }

                JrsClient client = new JrsClient(cmdline.getOptionValue("jsrestapiurl"),
                        cmdline.getOptionValue("jslogin"),
                        cmdline.getOptionValue("jspassword")
                        );

                if(cmdline.hasOption("delete")){
                    String resource = cmdline.getOptionValue("delete");
                    if(!client.deleteResource(resource)){
                        log.error("Failed to delete " + resource);
                        System.exit(1);
                        return;
                    }
                }

                if(cmdline.hasOption("import")){
                    try {
                        String filename = cmdline.getOptionValue("import");
                        if(!client.importReports(filename)){
                            log.error("Failed to import " + filename);
                            System.exit(1);
                            return;
                        }
                    } catch (IOException | InterruptedException ex) {
                        log.error("failed to import", ex);
                        System.exit(1);
                        return;
                    }
                }
            }
            else if(cmdline.hasOption("prepareimport")){
                ImportGenerator gen = new ImportGenerator();
                gen.process(//cmdline.getOptionValue("sourcedir"),
                        cmdline.getOptionValues("sourcedir"),
                        cmdline.getOptionValue("tempdir"),
                        cmdline.getOptionValue("targetzip"),
                        cmdline.getOptionValue("targetresource"),
                        cmdline.getOptionValue("dsname"),
                        cmdline.getOptionValue("dsdriver"),
                        cmdline.getOptionValue("dsurl"),
                        cmdline.getOptionValue("dslogin"),
                        cmdline.getOptionValue("dspassword"),
                        cmdline.getOptionValue("exclude"));
            }
            else{
                log.error("one or more options required: delete, import, prepareimport");
                System.out.println();
                formatter.printHelp("jreports-tool", options, true);
            }

        } catch (ParseException ex) {
            System.out.println();
            log.error("Invalid arguments: " + ex.getMessage());
            System.out.println();
            formatter.printHelp("jreports-tool", options, true);
            System.exit(1);
        }
        catch(Exception ex){
            log.error("Failed", ex);
            System.exit(1);
        }

        log.info("Done.");
        //System.exit(0);
    }

}
