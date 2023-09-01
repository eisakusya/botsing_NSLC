package eu.stamp.botsing.ga.strategy.operators;

import org.evosuite.Properties;
import org.evosuite.ga.Chromosome;
import org.evosuite.ga.FitnessFunction;
import org.evosuite.ga.comparators.DominanceComparator;
import org.evosuite.utils.Randomness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class CoverageAndNoveltyBasedSorting<T extends Chromosome> {
    private static final Logger LOG = LoggerFactory.getLogger(CoverageAndNoveltyBasedSorting.class);
    private List<List<T>> fronts = null;
    protected HashMap<T, Double> populationWithNoveltyScore = null;
    protected HashMap<T, Integer> populationWithLocalCompetition = null;
    protected List<T> population = null;

    public CoverageAndNoveltyBasedSorting(HashMap<T, Double> p1, HashMap<T, Integer> p2) {
        populationWithNoveltyScore = p1;
        populationWithLocalCompetition = p2;
        population = new ArrayList<>(populationWithNoveltyScore.keySet());
    }

    public void computeRankingAssignment() {
        fronts = new ArrayList<>(population.size());
        List<T> zero_front = this.getZeroFront();
        this.fronts.add(zero_front);
        int frontIndex = 1;
        if (zero_front.size() < Properties.POPULATION) {
            int rankedSolutions = zero_front.size();
            List<T> remaining = new ArrayList<>(population);
            remaining.removeAll(zero_front);

            while (rankedSolutions < Properties.POPULATION && remaining.size() > 0) {
                List<T> new_front = getNonDominatedSolutions(remaining, frontIndex);
                fronts.add(new_front);
                remaining.removeAll(new_front);
                rankedSolutions += new_front.size();
                ++frontIndex;
            }
        } else {
            List<T> remaining = new ArrayList<>(population);
            remaining.removeAll(zero_front);
            for (T c : remaining) {
                c.setRank(frontIndex);
            }
            fronts.add(remaining);

        }

    }

    public void sort(){
        List<T> solutionSet_ = population;
        int[] dominateMe = new int[population.size()];
        List<Integer>[] iDominate = new List[population.size()];
        List<Integer>[] front = new List[population.size() + 1];

        int i;
        for(i = 0; i < front.length; ++i) {
            front[i] = new LinkedList();
        }

        for(i = 0; i < solutionSet_.size(); ++i) {
            (population.get(i)).setDistance(Double.MAX_VALUE);
        }

        for(i = 0; i < solutionSet_.size(); ++i) {
            iDominate[i] = new LinkedList();
            dominateMe[i] = 0;
        }

        int var;
        for(i = 0; i < solutionSet_.size() - 1; ++i) {
            for(int q = i + 1; q < solutionSet_.size(); ++q) {
                int flagDominate = compare(population.get(i), population.get(q));
                if (flagDominate == -1) {
                    iDominate[i].add(q);
                    var = dominateMe[q]++;
                } else if (flagDominate == 1) {
                    iDominate[q].add(i);
                    var = dominateMe[i]++;
                }
            }
        }

        for(i = 0; i < solutionSet_.size(); ++i) {
            if (dominateMe[i] == 0) {
                front[0].add(i);
                (population.get(i)).setRank(1);
            }
        }

        i = 0;

        Iterator it1;
        while(front[i].size() != 0) {
            ++i;
            it1 = front[i - 1].iterator();

            while(it1.hasNext()) {
                Iterator<Integer> it2 = iDominate[(Integer)it1.next()].iterator();

                while(it2.hasNext()) {
                    int index = (Integer)it2.next();
                    var = dominateMe[index]--;
                    if (dominateMe[index] == 0) {
                        front[i].add(index);
                        (solutionSet_.get(index)).setRank(i + 1);
                    }
                }
            }

        }

        List<T>[] fronts = new ArrayList[i];

        for(int j = 0; j < i; ++j) {
            fronts[j] = new ArrayList();
            it1 = front[j].iterator();

            while(it1.hasNext()) {
                fronts[j].add(population.get((Integer)it1.next()));
            }
        }

    }

    public void nondominatedSort(){
        List<List<Integer>> iDominate=new ArrayList<>();
        List<Integer> dominateMe=new ArrayList<>();
        List<List<Integer>> Fronts=new ArrayList<>();
        int i,var;
        Fronts.add(new ArrayList<>());
        for(i=0;i<population.size();++i){
            //初始化相关记录
            iDominate.add(new ArrayList<>());
            dominateMe.add(0);

            for(int j=0;j< population.size();++j){
                //排除自己
                if (j==i){
                    continue;
                }

                //非支配性比较
                int flag=compare(population.get(i), population.get(j));
                if(flag==-1){
                    iDominate.get(i).add(j);
                } else if (flag==1) {
                    var=dominateMe.get(i);
                    var++;
                    dominateMe.set(i,var);
                }
            }

            //front维护
            if (dominateMe.get(i)==0){
                Fronts.get(0).add(i);
            }
        }

        i=0;
        while(!Fronts.get(i).isEmpty()){
            List<Integer> newFront=new ArrayList<>();

            for(int j=0;j<Fronts.get(i).size();++j){    //第i个Front
                int index=Fronts.get(i).get(j);     //第i个Front之中的第j个个体
                for(int k=0;k<iDominate.get(index).size();++k){     //第j个个体的iDominate集合
                    int index1=iDominate.get(index).get(k);     //iDominate集合中的第k个个体
                    var=dominateMe.get(index1);
                    var--;
                    dominateMe.set(index1,var);
                    if (var==0){
                        newFront.add(index1);
                    }
                }
            }

            ++i;
            Fronts.add(newFront);
        }

        fronts=new ArrayList<>();
        for (i=0;i< Fronts.size();++i){
            int index;
            fronts.add(new ArrayList<>());
            for(int j=0;j<Fronts.get(i).size();++j){
                index=Fronts.get(i).get(j);
                fronts.get(i).add(population.get(index));
            }
        }
    }

    private List<T> getZeroFront() {
        Set<T> zero_front = new LinkedHashSet(population.size());

        T best = null;
        while (best == null) {
            for (T test : population) {
                int flag = compare(test, best);
                if (flag <= 0 && (flag == 0 || Randomness.nextBoolean())) {
                    best = test;
                }
            }
        }
        best.setRank(0);
        zero_front.add(best);

        return new ArrayList<>(zero_front);
    }

    private List<T> getNonDominatedSolutions(List<T> population, int frontIndex) {
        List<T> front = new ArrayList<>(population.size());
        Iterator var1 = population.iterator();
        while (var1.hasNext()) {
            T c = (T) var1.next();
            boolean isDominated = false;
            List<T> dominatedIndividual = new ArrayList<>(population.size());
            Iterator var2 = front.iterator();

            while (var2.hasNext()) {
                T best = (T) var2.next();
                int flag = compare(c, best);

                if (flag == -1) {
                    dominatedIndividual.add(best);
                } else if (flag == 1) {
                    isDominated = true;
                    break;
                }
            }

            if (!isDominated) {
                c.setRank(frontIndex);
                front.add(c);
                front.removeAll(dominatedIndividual);
            }
        }

        return front;

    }

    protected int compare(T var1, T var2) {
        if (var1 == null) {
            //var2更优
            return 1;
        } else if (var2 == null) {
            //var1更优
            return -1;
        } else {
            boolean dominate1 = false;
            boolean dominate2 = false;
            //比较新颖性得分
            double N1=populationWithNoveltyScore.get(var1);
            double N2=populationWithNoveltyScore.get(var2);
            if (N1>N2) {
                dominate1 = true;

            } else if (N1<N2) {
                dominate2 = true;

            }

            //比较局部竞争值
            double LC1=populationWithLocalCompetition.get(var1);
            double LC2=populationWithLocalCompetition.get(var2);
            if (LC1>LC2) {
                dominate1 = true;

            } else if (LC1<LC2) {
                dominate2 = true;

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


    public List<T> getSubfront(int rank) throws Exception {
        if ((fronts.isEmpty())) {
            throw new Exception("Front is empty");
        } else if (rank >= fronts.size()) {
            throw new Exception("Index is out of bound,rank: " + rank + " ,size: " + fronts.size() + " ");
        } else {
            return fronts.get(rank);
        }
    }

    public int getNumberOfSubfronts() {
        return fronts.size();
    }



}
