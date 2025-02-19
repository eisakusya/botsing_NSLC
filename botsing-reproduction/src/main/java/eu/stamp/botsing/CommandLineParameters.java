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

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

public class CommandLineParameters {

    public static final String D_OPT = "D";
    public static final String PROJECT_CP_OPT = "project_cp";
    public static final String TARGET_FRAME_OPT = "target_frame";
    public static final String CRASH_LOG_OPT = "crash_log";
    public static final String MODEL_PATH_OPT = "model";
    public static final String HELP_OPT = "help";
    public static final String INTEGRATION_TESTING = "integration_testing";
    public static final String DISABLE_LINE_ESTIMATION = "disable_line_estimation";
    public static final String IO_DIVERSITY = "io_diversity";
    public static final String SEARCH_ALGORITHM = "search_algorithm";
    public static final String FITNESS_FUNCTION = "fitness";
    public static final String CONTINUE_AFTER_REPRODUCTION = "continue_after_reproduction";
    public static final String CRASH_SECONDARY_OBJECTIVE = "crash_secondary_objective";
    //NSLC:
    public static final String NICHE_FACTOR = "niche_factor";
    public static final String EPSILON="epsilon";


    public static Options getCommandLineOptions() {
        Options options = new Options();
        // Properties
        options.addOption(Option.builder(D_OPT)
                .numberOfArgs(2)
                .argName("property=value")
                .valueSeparator()
                .desc("use value for given property")
                .build());
        // Classpath
        options.addOption(Option.builder(PROJECT_CP_OPT)
                .hasArg()
                .desc("classpath of the project under test and all its dependencies")
                .build());
        // Target frame
        options.addOption(Option.builder(TARGET_FRAME_OPT)
                .hasArg()
                .desc("Level of the target frame")
                .build());
        // Stack trace file
        options.addOption(Option.builder(CRASH_LOG_OPT)
                .hasArg()
                .desc("File with the stack trace")
                .build());
        // Models directory
        options.addOption(Option.builder(MODEL_PATH_OPT)
                .hasArg()
                .desc("Directory of models generated by Botsing model generator")
                .build());
        // Search Algorithm
        options.addOption(Option.builder(SEARCH_ALGORITHM)
                .hasArg()
                .desc("Select the search algorithm.")
                .build());

        // FitnessFunction
        options.addOption(Option.builder(FITNESS_FUNCTION)
                .hasArg()
                .desc("Fitness function for guidance of the search algorithm")
                .build());

        // Use integration testing or not
        options.addOption(Option.builder(INTEGRATION_TESTING)
                .desc("Use integration testing for crash reproduction")
                .build());

        // Continue after first crash reproduction
        options.addOption(Option.builder(CONTINUE_AFTER_REPRODUCTION)
                .desc("Continues the search process after finding the first crash reproducing test case")
                .build());
        // Help message
        options.addOption(Option.builder(HELP_OPT)
                .desc("Prints this help message.")
                .build());

        // enable I/O diversity
        options.addOption(Option.builder(IO_DIVERSITY)
                .desc("Enables I/O diversity")
                .build());

        // Secondary Objective
        options.addOption(Option.builder(CRASH_SECONDARY_OBJECTIVE)
                .hasArg()
                .desc("Crash-related secondary search objectives")
                .build());

        // Novelty Search
        options.addOption(Option.builder(NICHE_FACTOR)
                .hasArg()
                .desc("Size-factor of niche in novelty search with local competition, default as 0.5")
                .build());

        options.addOption(Option.builder(EPSILON)
                .hasArg()
                .desc("Epsilon of e-dominance in novelty search with local competition, default as 0.3")
                .build());



        return options;
    }

}
