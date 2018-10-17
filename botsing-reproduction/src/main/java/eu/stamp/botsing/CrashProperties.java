
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

import org.evosuite.Properties;


import java.io.*;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


public class CrashProperties {

    private static CrashProperties instance = null;
    private StackTrace crash = new StackTrace();
    private String[] projectClassPaths;

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Parameter {
        String key();

        String group() default "Experimental";

        String description();
    }

    public static enum TestGenerationStrategy {
        Single_GA,
        Multi_GA;

        private TestGenerationStrategy() {
        }
    }

    public static enum FitnessFunction {
        WeightedSum,
        SimpleSum;

        private FitnessFunction() {
        }
    }

    public static enum SearchAlgorithm {
        Single_Objective_GGA;

        private SearchAlgorithm() {
        }
    }


    @Properties.Parameter(key = "testGenerationStrategy", group = "Crash reproduction", description = "Which mode to use for crash reproduction")
    public static CrashProperties.TestGenerationStrategy testGenerationStrategy = CrashProperties.TestGenerationStrategy.Single_GA;


    @Properties.Parameter(key = "SearchAlgorithm", group = "Crash reproduction", description = "Which search algorithm to use for crash reproduction")
    public static CrashProperties.SearchAlgorithm searchAlgorithm = SearchAlgorithm.Single_Objective_GGA;


    @Properties.Parameter(key = "FitnessFunctions", group = "Crash reproduction", description = "Which fitness function should be used for the GGA")
    public static CrashProperties.FitnessFunction[] fitnessFunctions = {FitnessFunction.WeightedSum};


    /**
     * The target frame in the crash stack trace
     */
    @Parameter(key = "max_target_injection_tries", group = "Runtime", description = "The maximum number of times the search tries to generate an individuals with the target method.")
    public static int max_target_injection_tries = 150;

    static java.util.Properties configFile = new java.util.Properties();

    private CrashProperties() {
        loadConfig();
        for (String property : configFile.stringPropertyNames()) {
            try {
                if (Properties.hasParameter(property)) {
                    Properties.getInstance().setValue(property, configFile.getProperty(property));
                }
            } catch (Properties.NoSuchParameterException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }


    }

    private void loadConfig() {
        try {
            InputStream inputstream = getClass().getClassLoader().getResourceAsStream("config.properties");
            configFile.load(inputstream);
        } catch (FileNotFoundException eta) {
            eta.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static CrashProperties getInstance() {
        if (instance == null) {
            instance = new CrashProperties();
        }
        return instance;
    }

    public String getStringValue(String property) throws IllegalAccessException, Properties.NoSuchParameterException {
        if (Properties.hasParameter(property)) {
            return Properties.getStringValue(property);
        } else if (configFile.containsKey(property)) {
            return configFile.getProperty(property);
        }
        return null;
    }


    public int getIntValue(String property) throws IllegalAccessException, Properties.NoSuchParameterException {
        return Properties.getIntegerValue(property);
    }


    public long getLongValue(String property) throws IllegalAccessException, Properties.NoSuchParameterException {
        return Properties.getLongValue(property);
    }


    public Boolean getBooleanValue(String property) {
        try {
            if (Properties.hasParameter(property)) {
                return Properties.getBooleanValue(property);
            } else if (configFile.containsKey(property)) {
                return Boolean.valueOf(configFile.getProperty(property));
            }
        } catch (Properties.NoSuchParameterException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void setupStackTrace(String stacktraceFile, int targetFrame) {
        crash.setup(stacktraceFile, targetFrame);
    }

    public void setupStackTrace(StackTrace crash) {
        this.crash = crash;
    }

    public void setClasspath(String projectClassPath) {
        projectClassPaths = projectClassPath.split(File.pathSeparator);
    }

    public void setClasspath(String[] projectClassPath) {
        projectClassPaths = projectClassPath;
    }

    public String[] getProjectClassPaths() {
        return projectClassPaths;
    }

    public StackTrace getStackTrace() {
        return crash;
    }

    public Properties.StoppingCondition getStoppingCondition() {
        return Properties.STOPPING_CONDITION;
    }

    public Throwable getTargetException() {
        StackTraceElement[] stackArray = new StackTraceElement[crash.getNumberOfFrames()];
        stackArray = crash.getFrames().toArray(stackArray);
        Throwable targetException = new Exception();
        targetException.setStackTrace(stackArray);
        return targetException;
    }


    public void resetStackTrace() {
        crash = new StackTrace();
    }


}
