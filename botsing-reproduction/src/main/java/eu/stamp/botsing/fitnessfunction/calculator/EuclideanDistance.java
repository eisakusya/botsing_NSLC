package eu.stamp.botsing.fitnessfunction.calculator;

import eu.stamp.botsing.StackTrace;
import eu.stamp.botsing.fitnessfunction.calculator.diversity.CallDiversityFitnessCalculator;
import eu.stamp.botsing.fitnessfunction.calculator.diversity.Individual;
import eu.stamp.botsing.fitnessfunction.utils.CallDiversityUtility;
import org.evosuite.ga.Chromosome;
import org.evosuite.testcase.TestChromosome;
import org.evosuite.utils.generic.GenericAccessibleObject;
import org.evosuite.testcase.statements.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EuclideanDistance<T extends Chromosome> extends CallDiversityFitnessCalculator<T> {
    private static final Logger LOG = LoggerFactory.getLogger(EuclideanDistance.class);

    private static EuclideanDistance instance = null;

    public static EuclideanDistance getInstance(StackTrace targetTrace) {
        if (instance == null) {
            instance = new EuclideanDistance(targetTrace);
        }
        if (!instance.targetTrace.equals(targetTrace)) {
            throw new IllegalArgumentException("The target stack trace has been changed");
        }
        return instance;
    }

    private EuclideanDistance(StackTrace targetTrace) {
        super(targetTrace);
    }

    public double getEuclideanDistance(T testChromosome,T chromosome){
        Map<GenericAccessibleObject<?>,Integer> methodCallsOfGivenChromosome=calculateMethodCalls(chromosome);
        Individual<T> individual = new Individual<>(chromosome,methodCallsOfGivenChromosome);
        return calculateEuclideanDistance(testChromosome,individual);
    }

    protected double calculateEuclideanDistance(T testChromosome, Individual<T> individual){
        Map<GenericAccessibleObject<?>,Integer> methodCallsOfGivenChromosome=calculateMethodCalls(testChromosome);
        Map<GenericAccessibleObject<?>,Integer> methodCallsOfIndividual=individual.getMethodCalls();

        if (methodCallsOfGivenChromosome.keySet().size()!=methodCallsOfIndividual.keySet().size()){
            throw new IllegalArgumentException("Size of method calls are not the same");
        }

        double sum=0.0;

        for(GenericAccessibleObject<?> call:methodCallsOfGivenChromosome.keySet()){
            if (!methodCallsOfIndividual.containsKey(call)){
                throw new IllegalArgumentException("The keys of the given tests are not the same");
            }
            double partition=methodCallsOfIndividual.get(call)-methodCallsOfGivenChromosome.get(call);
            sum+=(partition*partition);
        }

        return Math.sqrt(sum);
    }

    public double getSimilarityValue(T testChromosome) {
        return 0.0;
    }

    public void addToPopulation(List<T> chromosomes) {
        return;
    }

    private Map<GenericAccessibleObject<?>, Integer> calculateMethodCalls(T chromosome) {
        if (callables.isEmpty()) {
            throw new IllegalArgumentException("Callables list is empty");
        }

        if (!(chromosome instanceof TestChromosome)) {
            throw new IllegalArgumentException("The given chromosome is not a test case");
        }

        Map<GenericAccessibleObject<?>, Integer> result = new HashMap<>();

        for (GenericAccessibleObject<?> call : callables
        ) {
            result.put(call, 0);
        }

        TestChromosome givenTestChromosome=(TestChromosome) chromosome;

        int testSize=givenTestChromosome.getTestCase().size();

        for(int stmtIndex=0;stmtIndex<testSize;++stmtIndex){
            Statement currentStatement = givenTestChromosome.getTestCase().getStatement(stmtIndex);
            if (CallDiversityUtility.isInteresting(currentStatement,this.targetTrace.getTargetClass())){
                GenericAccessibleObject genObj=currentStatement.getAccessibleObject();

                if(!result.containsKey(genObj)){
                    LOG.debug("detected generic accessible object is not available  in the methods list.");
                }else{
                    result.put(genObj,1);
                }
            }
        }

        return result;

    }

}
