package eu.stamp.botsing.testgeneration.strategy;

import eu.stamp.botsing.commons.ga.strategy.mosa.AbstractMOSA;
import eu.stamp.botsing.commons.testgeneration.strategy.AbstractTestGenerationUtility;
import eu.stamp.botsing.fitnessfunction.FitnessFunctions;
import org.evosuite.Properties;
import org.evosuite.ga.metaheuristics.GeneticAlgorithm;
import org.evosuite.ga.metaheuristics.NoveltySearch;
import org.evosuite.ga.stoppingconditions.StoppingCondition;
import org.evosuite.ga.stoppingconditions.ZeroFitnessStoppingCondition;
import org.evosuite.strategy.TestGenerationStrategy;
import org.evosuite.testcase.TestChromosome;
import org.evosuite.testcase.TestFitnessFunction;
import org.evosuite.testcase.execution.ExecutionTracer;
import org.evosuite.testsuite.TestSuiteChromosome;
import org.evosuite.utils.ResourceController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class NSLCStrategy extends TestGenerationStrategy {
    private static final Logger LOG = LoggerFactory.getLogger(NSLCStrategy.class);
    private TestGenerationUtility utility = new TestGenerationUtility();
    private FitnessFunctions fitnessFunctionCollector = new FitnessFunctions();

    public NSLCStrategy() {
    }

    @Override
    public TestSuiteChromosome generateTests() {
        LOG.info("test generation strategy: Novelty Search with Local Competition");
        TestSuiteChromosome suite = new TestSuiteChromosome();
        ExecutionTracer.enableTraceCalls();

        // 获取搜索算法
        GeneticAlgorithm ga = utility.getGA();
        if (!(ga instanceof NoveltySearch)) {
            throw new IllegalArgumentException("The search algorithm of NSLC should be Novelty Search");
        }

        //添加停止条件
        //因为只有一个主目标，即覆盖率
        //因此停止条件添加为budget消耗完或者实现主目标
        StoppingCondition stoppingCondition = getStoppingCondition();
        stoppingCondition.setLimit(Properties.SEARCH_BUDGET);
        ga.addStoppingCondition(new ZeroFitnessStoppingCondition());
        ga.addStoppingCondition(stoppingCondition);

        List<TestFitnessFunction> fitnessFunctions = fitnessFunctionCollector.getFitnessFunctionList();

        // Add listeners
        if (Properties.CHECK_BEST_LENGTH) {
            org.evosuite.testcase.RelativeTestLengthBloatControl bloat_control = new org.evosuite.testcase.RelativeTestLengthBloatControl();
            ga.addBloatControl(bloat_control);
            ga.addListener(bloat_control);
        }
        ga.addListener(new ResourceController());

        //因为已经指定FF为覆盖率
        ga.addFitnessFunctions(fitnessFunctions);

        //开始搜索生成
        ga.generateSolution();

        //搜索完成
        TestChromosome solution = (TestChromosome) ga.getBestIndividual();

        double bestFF=solution.getFitness();
        if (bestFF==0.0){
            LOG.info("* The target crash is covered. Solution: "+solution.getTestCase().toCode());
            LOG.info("{} thrown exception(s) are ddetected in the solution: ",solution.getLastExecutionResult().getAllThrownExceptions().size());
            for (Throwable t: solution.getLastExecutionResult().getAllThrownExceptions()){
                LOG.info(t.toString());
                for (StackTraceElement frame: t.getStackTrace()){
                    LOG.info(frame.toString());
                }
            }
            suite.addTest(solution);
        }else {
            LOG.info("* The target crash is not covered! The best solution has "+bestFF + " fitness value.");

        }

        return suite;

    }
}
