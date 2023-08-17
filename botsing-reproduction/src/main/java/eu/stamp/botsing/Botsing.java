package eu.stamp.botsing;

/*-
 * #%L
 * botsing-reproduction
 * %%
 * Copyright (C) 2017 - 2018 eu.stamp-project
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import eu.stamp.botsing.reproduction.CrashReproduction;

import static eu.stamp.botsing.CommandLineParameters.*;
import static eu.stamp.botsing.commons.SetupUtility.*;


import org.apache.commons.cli.*;
import org.evosuite.Properties;
import org.evosuite.TestGenerationContext;
import org.evosuite.result.TestGenerationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;



public class Botsing {

    private static final Logger LOG = LoggerFactory.getLogger(Botsing.class);

    public List<TestGenerationResult> parseCommandLine(String[] args) {
        // Get default properties
        CrashProperties crashProperties = CrashProperties.getInstance();

        // Parse commands according to the defined options
        Options options = CommandLineParameters.getCommandLineOptions();
        CommandLine commands = parseCommands(args, options, false);

        // If help option is provided
        if (commands.hasOption(HELP_OPT)) {
            printHelpMessage(options, false);
        } else if(!(commands.hasOption(PROJECT_CP_OPT) && commands.hasOption(CRASH_LOG_OPT) && commands.hasOption(TARGET_FRAME_OPT))) { // Check the required options are there
            LOG.error("A mandatory option -{} -{} -{} is missing!", PROJECT_CP_OPT, CRASH_LOG_OPT, TARGET_FRAME_OPT);
            printHelpMessage(options, false);
        } else {// Otherwise, proceed to crash reproduction

            // Update EvoSuite's properties
            java.util.Properties properties = commands.getOptionProperties(D_OPT);
            updateEvoSuiteProperties(properties);
            // Setup project class paths
            crashProperties.setClasspath(getCompatibleCP(commands.getOptionValue(PROJECT_CP_OPT)));
            setupProjectClasspath(crashProperties.getProjectClassPaths());
            // Setup stack trace(s)
            setupStackTrace(crashProperties, commands);
            // Set the search algorithm
            if(commands.hasOption(SEARCH_ALGORITHM)){
                setSearchAlgorithm(commands.getOptionValue(SEARCH_ALGORITHM));
            }
            // Set fitness function(s)
            if(commands.hasOption(FITNESS_FUNCTION)){
                setFF(commands.getOptionValue(FITNESS_FUNCTION));
            }

            // Set secondary objective(s)
            if(commands.hasOption(CRASH_SECONDARY_OBJECTIVE)){
                setSecondaryObjective(commands.getOptionValue(CRASH_SECONDARY_OBJECTIVE));
            }
            // Enable integration testing in the crash reproduction process if it is necessary.
            if(commands.hasOption(INTEGRATION_TESTING)){
                CrashProperties.integrationTesting = true;
            }
            // Estimating the missing lines in the stack trace
            if(commands.hasOption(DISABLE_LINE_ESTIMATION)){
                CrashProperties.lineEstimation = false;
            }
            // Add I/O Diversity as goals to MOSA
            if(commands.hasOption(IO_DIVERSITY)){
                CrashProperties.IODiversity = true;
            }
            // Use model seeding
            if(commands.hasOption(MODEL_PATH_OPT)){
                setupModelSeedingRelatedProperties(commands);
            }
            // Continue search after first crash reproduction
            if(commands.hasOption(CONTINUE_AFTER_REPRODUCTION)){
                CrashProperties.stopAfterFirstCrashReproduction = false;
            }
            // 设置新颖性指标
            if(commands.hasOption(NOVELTY_THRESHOLD)){
                if(CrashProperties.searchAlgorithm!= CrashProperties.SearchAlgorithm.NoveltySearch){
                    throw new RuntimeException("Search algorithm is not NSLC");
                }
                setNoveltyThreshold(commands.getOptionValue(NOVELTY_THRESHOLD));
            }
            // 设置是否考虑覆盖率
            if(commands.hasOption(NOVELTY_THRESHOLD)){
                if(CrashProperties.searchAlgorithm!= CrashProperties.SearchAlgorithm.NoveltySearch){
                    throw new RuntimeException("Search algorithm is not NSLC");
                }
                setConsiderCoverage();
            }
            // 设置种群大小
            if(commands.hasOption(NICHE_FACTOR)){
                if(CrashProperties.searchAlgorithm!= CrashProperties.SearchAlgorithm.NoveltySearch){
                    throw new RuntimeException("Search algorithm is not NSLC");
                }
                setNicheSize(commands.getOptionValue(NICHE_FACTOR));
            }
            // 设置连续加入失败的允许数
            if(commands.hasOption(STALLED_THRESHOLD)){
                if(CrashProperties.searchAlgorithm!= CrashProperties.SearchAlgorithm.NoveltySearch){
                    throw new RuntimeException("Search algorithm is not NSLC");
                }
                setStalledThreshold(commands.getOptionValue(STALLED_THRESHOLD));
            }
            // 设置加入成功的阈值
            if(commands.hasOption(ADDING_THRESHOLD)){
                if(CrashProperties.searchAlgorithm!= CrashProperties.SearchAlgorithm.NoveltySearch){
                    throw new RuntimeException("Search algorithm is not NSLC");
                }
                setAddingThreshold(commands.getOptionValue(ADDING_THRESHOLD));
            }
            // 设置加入存档的随机数
            if(commands.hasOption(ADD_TO_ARCHIVE_PROB)){
                if(CrashProperties.searchAlgorithm!= CrashProperties.SearchAlgorithm.NoveltySearch){
                    throw new RuntimeException("Search algorithm is not NSLC");
                }
                setATAProbability(commands.getOptionValue(ADD_TO_ARCHIVE_PROB));
            }
            // execute
            return CrashReproduction.execute();
        }
        return null;

    }

    private void setFF(String fitnessFunction) {
        if (fitnessFunction.contains(":")){
            String[] ffs = fitnessFunction.split(":");
            CrashProperties.FitnessFunction[] newFitnessFunctionsArray = new CrashProperties.FitnessFunction[ffs.length];
            int index = 0;
            for (String ff: ffs){
                newFitnessFunctionsArray[index]=CrashProperties.FitnessFunction.valueOf(ff);
                index++;
            }
            CrashProperties.fitnessFunctions = newFitnessFunctionsArray;
        }else {
            CrashProperties.fitnessFunctions = new CrashProperties.FitnessFunction[]{CrashProperties.FitnessFunction.valueOf(fitnessFunction)};
        }

    }

    private void setSecondaryObjective(String secondaryObjective) {
        CrashProperties.secondaryObjectives = new CrashProperties.SecondaryObjective[]{CrashProperties.SecondaryObjective.valueOf(secondaryObjective)};
    }


    private void setSearchAlgorithm(String algorithm) {

        CrashProperties.searchAlgorithm = CrashProperties.SearchAlgorithm.valueOf(algorithm);
        LOG.info(CrashProperties.searchAlgorithm.name());
//        Properties.SELECTION_FUNCTION = Properties.SelectionFunction.RANK_CROWD_DISTANCE_TOURNAMENT;
    }

    private void setupModelSeedingRelatedProperties( CommandLine commands) {
        int numberOfCrashes = CrashProperties.getInstance().getCrashesSize();
        for (int crashIndex=0; crashIndex<numberOfCrashes; crashIndex++){
            for (StackTraceElement ste: CrashProperties.getInstance().getStackTrace(crashIndex).getAllFrames()){
                try {
                    Class.forName(ste.getClassName(), true,
                            TestGenerationContext.getInstance().getClassLoaderForSUT());
                } catch (Exception| Error e) {
                    e.printStackTrace();
                }
            }
        }
        String modelPath = commands.getOptionValue(MODEL_PATH_OPT);
        Properties.MODEL_PATH = modelPath;
    }


    public void setupStackTrace(CrashProperties crashProperties, CommandLine commands){
        // Setup given stack trace
        Path log_dir = new File(commands.getOptionValue(CRASH_LOG_OPT)).toPath();
        if (Files.isDirectory(log_dir)) {
            // We need to setup multiple crashes
            File directory = new File(commands.getOptionValue(CRASH_LOG_OPT));
            File[] directoryListing = directory.listFiles();
            if (directoryListing != null) {
                for (File file : directoryListing) {
                    if(!file.getName().contains(".log")){
                        continue;
                    }
                    String logPath = file.getAbsolutePath();
                    LOG.info("Detected log: {}",logPath);
                    crashProperties.setupStackTrace(logPath,
                            Integer.parseInt(commands.getOptionValue(TARGET_FRAME_OPT)));
                }
            } else {
                throw new IllegalArgumentException("Log directory is empty!");
            }
        } else {
            // We need to setup only one crash
            crashProperties.clearStackTraceList();
            crashProperties.setupStackTrace(commands.getOptionValue(CRASH_LOG_OPT),
                    Integer.parseInt(commands.getOptionValue(TARGET_FRAME_OPT)));
        }


    }

    //NSLC所需
    private void setNoveltyThreshold(String noveltyThreshold) {

        CrashProperties.noveltyThreshold = Double.parseDouble(noveltyThreshold);
        LOG.info("Novelty initialized: {}.",CrashProperties.noveltyThreshold);
    }

    private void setNicheSize(String nicheSize) {

        CrashProperties.nicheFactor = Double.parseDouble(nicheSize);
        LOG.info("Size-factor of niche set: {}.",CrashProperties.nicheFactor);
    }

    private void setStalledThreshold(String stalledThreshold) {

        CrashProperties.stalledThreshold = Integer.parseInt(stalledThreshold);
        LOG.info("Stalled threshold set: {}.",CrashProperties.stalledThreshold);
    }

    private void setAddingThreshold(String addingThreshold) {

        CrashProperties.addingThreshold = Integer.parseInt(addingThreshold);
        LOG.info("Adding threshold set: {}.",CrashProperties.addingThreshold);
    }

    private void setATAProbability(String probability) {

        CrashProperties.addToArchiveProbability = Double.parseDouble(probability);
        LOG.info("Probability initialized: {}.",CrashProperties.addToArchiveProbability);
    }

    private void setConsiderCoverage(){
        CrashProperties.considerCoverage=true;
        LOG.info("Consider coverage in NSLC.");
    }

    @SuppressWarnings("checkstyle:systemexit")
    public static void main(String[] args) {
        Botsing bot = new Botsing();
        bot.parseCommandLine(args);
        System.exit(0);
    }
}
