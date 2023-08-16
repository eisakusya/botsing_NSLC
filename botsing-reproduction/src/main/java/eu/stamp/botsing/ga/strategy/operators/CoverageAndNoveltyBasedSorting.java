package eu.stamp.botsing.ga.strategy.operators;

import org.evosuite.Properties;
import org.evosuite.ga.Chromosome;
import org.evosuite.ga.FitnessFunction;
import org.evosuite.utils.Randomness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class CoverageAndNoveltyBasedSorting<T extends Chromosome> {
    private static final Logger LOG = LoggerFactory.getLogger(CoverageAndNoveltyBasedSorting.class);
    private List<List<T>> fronts = null;


    public void computeRankingAssignment(FitnessFunction<T> fitnessFunction, HashMap<T, Double> population) {
        if (population.isEmpty()) {
            LOG.debug("solution is empty");
        }
        List<Map.Entry<T, Double>> pop = (ArrayList<Map.Entry<T, Double>>) population.entrySet();
        fronts = new ArrayList<>(population.size());
        List<T> zero_front = this.getZeroFront(population, fitnessFunction);
        this.fronts.add(zero_front);
        int frontIndex=1;
        if(zero_front.size()< Properties.POPULATION){
            int rankedSolutions = zero_front.size();
            HashMap<T,Double> remaining=new HashMap<>();
            remaining=(HashMap<T, Double>) population.clone();
            for(T z:zero_front){
                remaining.remove(z);
            }

            while(rankedSolutions<Properties.POPULATION&&remaining.size()>0){
                List<T> new_front = getNonDominatedSolutions(remaining,fitnessFunction,frontIndex);
                fronts.add(new_front);
                for(T n:new_front){
                    remaining.remove(n);
                }
                rankedSolutions+= new_front.size();
                ++frontIndex;
            }
        }else{
            HashMap<T,Double> remaining = new HashMap<>();
            remaining=(HashMap<T, Double>) population.clone();
            for(T z:zero_front){
                remaining.remove(z);
            }
            List<T> remainingList=new ArrayList<>();
            for(Map.Entry<T,Double> remain:remaining.entrySet()){
                remain.getKey().setRank(frontIndex);
                remainingList.add(remain.getKey());
            }

        }
    }

    private List<T> getZeroFront(HashMap<T, Double> population, FitnessFunction<T> fitnessFunction) {
        Set<T> zero_front = new LinkedHashSet(population.size());

        Map.Entry<T, Double> best = null;
        for (Map.Entry<T, Double> test : population.entrySet()) {
            int flag = compare(test, best, fitnessFunction);
            if (flag >= 0 && (flag != 0 || !Randomness.nextBoolean())) {
                best = test;
            }
        }
        assert best!=null;
        best.getKey().setRank(0);
        zero_front.add(best.getKey());

        return new ArrayList<>(zero_front);

    }

    private List<T> getNonDominatedSolutions(HashMap<T, Double> population, FitnessFunction fitnessFunction, int frontIndex) {
        List<T> front = new ArrayList<>(population.size());
        Iterator var1 = population.entrySet().iterator();
        while (var1.hasNext()) {
            Map.Entry<T, Double> c = (Map.Entry<T, Double>) var1.next();
            boolean isDominated = false;
            List<T> dominatedIndividual = new ArrayList<>(population.size());
            Iterator var2 = front.iterator();

            while (var2.hasNext()) {
                Map.Entry<T, Double> best = (Map.Entry<T, Double>) var2.next();
                int flag = compare(c, best, fitnessFunction);
                if (flag == -1) {
                    dominatedIndividual.add(best.getKey());
                } else if (flag == 1) {
                    isDominated = true;
                    break;
                }
            }

            if (!isDominated) {
                c.getKey().setRank(frontIndex);
                front.add(c.getKey());
                front.removeAll(dominatedIndividual);
            }
        }

        return front;
    }

    public List<T> getSubfront(int rank) {
        return (List) (fronts != null && rank < fronts.size() ? (List) this.fronts.get(rank) : new ArrayList<T>());
    }

    public int getNumberOfSubfronts() {
        return fronts.size();
    }

    protected int compare(Map.Entry<T, Double> var1, Map.Entry<T, Double> var2, FitnessFunction<T> fitnessFunction) {
        if (var1 == null) {
            return 1;
        } else if (var2 == null) {
            return -1;
        } else {
            boolean dominate1 = false;
            boolean dominate2 = false;
            int flag = Double.compare(var1.getKey().getFitness(fitnessFunction), var2.getKey().getFitness(fitnessFunction));
            if (flag < 0) {
                dominate1 = true;
                if (dominate2) {
                    return 0;
                }
            } else if (flag > 0) {
                dominate2 = true;
                if (dominate1) {
                    return 0;
                }
            }

            //通过get散列表来比较两者的新颖性指标
            //新颖性指标越大越好
            int flag1 = Double.compare(var1.getValue(), var2.getValue());
            if (flag1 > 0) {
                dominate1 = true;
                if (dominate2) {
                    return 0;
                }
            } else if (flag1 < 0) {
                dominate2 = true;
                if (dominate1) {
                    return 0;
                }
            }

            if (dominate1 == dominate2) {
                return 0;
            } else if (dominate1) {
                return -1;
            } else {
                return 1;
            }
        }
    }

}
