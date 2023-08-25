package eu.stamp.botsing.fitnessfunction;

import eu.stamp.botsing.StackTrace;
import eu.stamp.botsing.fitnessfunction.calculator.diversity.HammingDiversity;
import org.evosuite.ga.Chromosome;

import java.util.Collection;
import java.util.Iterator;

public class NoveltyFunction<T extends Chromosome> extends org.evosuite.ga.NoveltyFunction<T> {
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

    public double getNovelty(T individual, Collection<T> population) {
        double distance = 0.0;

        for (T other:population) {
            double d=getDistance(individual,other);
            distance+=d;
        }
        distance /= (double)(population.size());
        return distance;
    }
}

