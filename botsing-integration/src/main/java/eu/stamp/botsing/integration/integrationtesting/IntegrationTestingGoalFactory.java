package eu.stamp.botsing.integration.integrationtesting;

import eu.stamp.botsing.integration.IntegrationTestingProperties;
import eu.stamp.botsing.integration.coverage.branch.BranchPair;
import eu.stamp.botsing.integration.coverage.branch.BranchPairPool;
import eu.stamp.botsing.integration.coverage.branch.IntegrationTestingBranchCoverageFactory;
import eu.stamp.botsing.integration.fitnessfunction.BranchPairFF;
import eu.stamp.botsing.integration.fitnessfunction.IndependentPathFF;
import eu.stamp.botsing.integration.graphs.cfg.PathsPool;
import org.evosuite.graphs.cfg.BasicBlock;
import org.evosuite.testcase.TestFitnessFunction;
import org.evosuite.testsuite.AbstractFitnessFactory;

import java.util.LinkedList;
import java.util.List;

public class IntegrationTestingGoalFactory extends AbstractFitnessFactory<TestFitnessFunction> {
    private static List<TestFitnessFunction> goals = new LinkedList<>();

    public IntegrationTestingGoalFactory(){
        goals.clear();
        for(IntegrationTestingProperties.FitnessFunction ff: IntegrationTestingProperties.fitnessFunctions){
            switch (ff){
                case Independent_Paths:
                    // get pathPairs for each call site and  make independentpathFF for each of them and add them to goals
                    String callerClassName = IntegrationTestingProperties.TARGET_CLASSES[1];
                    List<List<BasicBlock>[]> pathPairs = PathsPool.getInstance().getPathPairs(callerClassName);
                    for (List<BasicBlock>[] pair : pathPairs){
                        goals.add(new IndependentPathFF(pair[0],pair[1]));
                    }
                    break;
                case Branch_Pairs:
                    for(BranchPair branchPair: BranchPairPool.getInstance().getBranchPairs()){
                        if(branchPair.isDependent()){
                            // We should make the branch only for the right expression
                            goals.add(new BranchPairFF(branchPair,branchPair.getExpression(), true));
                            goals.add(new BranchPairFF(branchPair,branchPair.getExpression(),false));
                        }else{
                            if(branchPair.getFirstBranch() != null){
                                goals.add(new BranchPairFF(branchPair,true, true));
                                goals.add(new BranchPairFF(branchPair,true,false));
                            }

                            if(branchPair.getSecondBranch() != null) {
                                goals.add(new BranchPairFF(branchPair, false, true));
                                goals.add(new BranchPairFF(branchPair, false, false));
                            }
                        }
                    }
                    break;
                case Regular_Branch_Coverage:
                    IntegrationTestingBranchCoverageFactory branchCoverageFactory = new IntegrationTestingBranchCoverageFactory();
                    goals.addAll(branchCoverageFactory.getCoverageGoals());
            }
        }
    }
    @Override
    public List<TestFitnessFunction> getCoverageGoals() {
        return goals;
    }
}
