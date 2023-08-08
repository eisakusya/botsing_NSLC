package eu.stamp.botsing.fitnessfunction;

import eu.stamp.botsing.StackTrace;
import eu.stamp.botsing.fitnessfunction.calculator.diversity.HammingDiversity;
import org.evosuite.ga.Chromosome;

public class NoveltyFunction<T extends Chromosome> extends org.evosuite.ga.NoveltyFunction {
    /*
    use to calculate the novelty score of every two chromosome
     */
    protected StackTrace targetTrace;
    public NoveltyFunction(StackTrace crash){
        super();
        targetTrace=crash;
    }

    public double getDistance(Chromosome var1, Chromosome var2){
        //use hamming distance to get distance of two chromosome
        return HammingDiversity.getInstance(targetTrace).getHammingDistance(var1,var2);
    }
}
