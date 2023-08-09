package eu.stamp.botsing.ga.strategy.metaheuristics;

import eu.stamp.botsing.CrashProperties;
import eu.stamp.botsing.StackTrace;
import eu.stamp.botsing.commons.ga.strategy.operators.Mutation;
import eu.stamp.botsing.fitnessfunction.NoveltyFunction;
import eu.stamp.botsing.fitnessfunction.WeightedSum;
import eu.stamp.botsing.fitnessfunction.testcase.factories.StackTraceChromosomeFactory;
import eu.stamp.botsing.fitnessfunction.utils.WSEvolution;

import org.evosuite.Properties;
import org.evosuite.ga.Chromosome;
import org.evosuite.ga.ChromosomeFactory;
import org.evosuite.ga.operators.crossover.CrossOverFunction;
import org.evosuite.utils.Randomness;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class NoveltySearchLocalCompetition<T extends Chromosome> extends org.evosuite.ga.metaheuristics.NoveltySearch<T> {
    private static final Logger LOG = LoggerFactory.getLogger(NoveltySearchLocalCompetition.class);
    Mutation mutation;
    private final int nicheSize;
    protected List<T> niche = null;
    protected double noveltyThreshold;
    private final WeightedSum crashCoverage;
    private final NoveltyFunction<T> noveltyFunction;
    private int populationSize;
    protected List<T> archive = null;
    protected List<T> bigArchive = null;
    protected int stalledThreshold;
    protected int addingThreshold;
    protected double addingArchiveProbability;

    public NoveltySearchLocalCompetition(ChromosomeFactory<T> factory, CrossOverFunction crossOverOperator, Mutation mutationOperator) {
        super(factory);
        this.stoppingConditions.clear();
        mutation = mutationOperator;
        this.crossoverFunction = crossOverOperator;

        try {
            this.populationSize = CrashProperties.getInstance().getIntValue("population");
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (Properties.NoSuchParameterException e) {
            e.printStackTrace();
        }
        StackTrace targetTrace = ((StackTraceChromosomeFactory) this.chromosomeFactory).getTargetTrace();
        crashCoverage = new WeightedSum(targetTrace);
        noveltyFunction = new NoveltyFunction<>(targetTrace);
        archive = new ArrayList<>();

        noveltyThreshold = CrashProperties.noveltyThreshold;
        nicheSize = CrashProperties.nicheSize;
        stalledThreshold=CrashProperties.stalledThreshold;
        addingThreshold=CrashProperties.addingThreshold;
        addingArchiveProbability=CrashProperties.addToArchiveProbability;

    }

    public void generateSolution() {
        LOG.info("Initializing the first population with size of {} individuals", this.populationSize);
        Boolean initialized = false;
        notifySearchStarted();
        WSEvolution.getInstance().setStartTime(this.listeners);
        while (!initialized) {        //initialize population
            try {
                initializePopulation();
                initialized = true;
            } catch (Exception | Error e) {
                LOG.warn("Botsing was unsuccessful in generating the initial population. cause: {}", e.getMessage());
            }

            if (isFinished()) {
                break;
            }
        }

        while (!isFinished()) {
            LOG.info("Number of generations: {}", currentIteration + 1);

            this.evolve();
            //将现有的population和上一次的archive进行合并
            emerge();
            //在全局空间内进行非支配排序，并根据排序结果把非支配解加入到存档中
            sort();
            updateNiche();
            //再在局部空间内进行新颖性排序
            calculateNoveltyAndSortPopulation();
            updateArchive();

            this.notifyIteration();
            this.writeIndividuals(this.population);
        }
    }

    public void initializePopulation() {
        if (!population.isEmpty()) {
            return;
        }
        //Start generating population.
        LOG.debug("Initializing the population.");
        generatePopulation(populationSize);
        calculateFitness();     //only one main objective
        this.notifyIteration();
    }

    public void generatePopulation(int populationSize) {
        LOG.debug("Creating random population");
        for (int i = 0; i < populationSize; i++) {
            T individual;
            individual = chromosomeFactory.getChromosome();
            //main objective:coverage
            //单一主目标
            individual.addFitness(crashCoverage);

            population.add(individual);
            if (isFinished()) {
                break;
            }

        }

    }

    protected void updateArchive() {
        //根据新颖性指标，将niche中的个体选中加入到存档
        int added = 0;
        int notAdded = 0;
        int maxNotAdded = 0;
        for (T individual : this.niche) {
            //根据新颖性指标把个体加入存档
            if ((noveltyFunction.getNovelty(individual, niche) > noveltyThreshold) || Randomness.nextDouble() <= addingArchiveProbability) {
                archive.add(individual);
                if (notAdded > maxNotAdded) {
                    maxNotAdded = notAdded;
                }
                notAdded = 0;
                ++added;
            } else {
                notAdded++;

            }
        }
        //对相关参数的调整更新
        if (maxNotAdded > stalledThreshold) {
            //降低novelty threshold
            noveltyThreshold *= 0.95;
            notAdded = 0;
        } else if ((added > addingThreshold) && (archive.size() > nicheSize)) {
            //提高novelty threshold
            noveltyThreshold *= 1.05;
        }
        this.population=archive;
    }

    protected void emerge() {
        //将旧的存档和今代的种群进行合并，形成bigArchive
        bigArchive = new ArrayList<>(archive);
        bigArchive.addAll(this.population);
    }

    protected void sort() {
        //在bigArchive中根据FF和新颖性指标进行全局排序
        List<T> pop = this.bigArchive;
        int[] dominateMe = new int[pop.size()];
        List<Integer>[] iDominate = new List[pop.size()];
        List<Integer>[] front = new List[pop.size() + 1];

        int i;
        for (i = 0; i < front.length; ++i) {
            front[i] = new LinkedList<>();
        }

        for (i = 0; i < pop.size(); ++i) {
            iDominate[i] = new LinkedList<>();
            dominateMe[i] = 0;
        }

        int var10002;
        for (i = 0; i < pop.size() - 1; ++i) {
            for (int j = i + 1; j < pop.size(); ++j) {
                int flagDominate = compare((Chromosome) this.bigArchive.get(i), (Chromosome) bigArchive.get(j));
                if (flagDominate == -1) {
                    iDominate[i].add(j);
                    var10002 = dominateMe[j]++;
                } else if (flagDominate == 1) {
                    iDominate[j].add(i);
                    var10002 = dominateMe[i]++;
                }
            }
        }

        for (i = 0; i < pop.size(); ++i) {
            if (dominateMe[i] == 0) {
                front[0].add(i);
                ((Chromosome) this.bigArchive.get(i)).setRank(1);
            }
        }

        i = 0;

        Iterator it1;
        while (front[i].size() != 0) {
            ++i;
            it1 = front[i - 1].iterator();

            while (it1.hasNext()) {
                Iterator<Integer> it2 = iDominate[(Integer) it1.next()].iterator();

                while (it2.hasNext()) {
                    int index = (Integer) it2.next();
                    var10002 = dominateMe[index]--;
                    if (dominateMe[index] == 0) {
                        front[i].add(index);
                        ((Chromosome) pop.get(index)).setRank(i + 1);
                    }
                }
            }
        }

        List<T> newPopulation = new ArrayList<>();
        for (int j = 0; j < i; ++j) {
            it1 = front[j].iterator();
            while (it1.hasNext()) {
                newPopulation.add(this.bigArchive.get((Integer) it1.next()));
            }
        }
        this.bigArchive = newPopulation;
    }

    public int compare(Chromosome c1, Chromosome c2) {
        //在全局空间中根据覆盖率和新颖性进行非支配性比较
        if (c1 == null) {
            return 1;
        } else if (c2 == null) {
            return -1;
        } else {
            boolean dominate1 = false;
            boolean dominate2 = false;
            int flag = Double.compare(c1.getFitness(crashCoverage), c2.getFitness(crashCoverage));
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

            int flag1 = Double.compare(noveltyFunction.getNovelty(c1, this.population), noveltyFunction.getNovelty(c2, this.population));
            if (flag1 < 0) {
                dominate1 = true;
                if (dominate2) {
                    return 0;
                }
            } else if (flag1 > 0) {
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

    protected void updateNiche() {
        //从bigArchive中挑选前k个作为局部空间
        niche = new ArrayList<>();
        for (int i = 0; i < nicheSize; i++) {
            niche.add(bigArchive.get(i));
        }
    }

    protected void calculateNoveltyAndSortPopulation() {
        //局部空间内的新颖性排序
        LOG.debug("Calculating novelty for " + this.niche.size() + " individuals");
        Iterator<T> iterator = this.niche.iterator();
        Map<T, Double> noveltyMap = new LinkedHashMap();

        while (iterator.hasNext()) {
            T c = iterator.next();
            if (this.isFinished()) {
                if (c.isChanged()) {
                    iterator.remove();
                }
            } else {
                double novelty = this.noveltyFunction.getNovelty(c, this.niche);
                noveltyMap.put(c, novelty);
            }
        }

        this.sortPopulation(this.niche, noveltyMap);
    }

    public T getBestIndividual(){
        //返回最优解
        if(this.population.isEmpty()){
            return this.chromosomeFactory.getChromosome();
        }
        //对存档进行排序
        Iterator<T> iterator = this.archive.iterator();
        Map<T, Double> noveltyMap = new LinkedHashMap();

        while (iterator.hasNext()) {
            T c = iterator.next();
            if (this.isFinished()) {
                if (c.isChanged()) {
                    iterator.remove();
                }
            } else {
                double novelty = this.noveltyFunction.getNovelty(c, this.archive);
                noveltyMap.put(c, novelty);
            }
        }

        this.sortPopulation(this.archive, noveltyMap);
        return archive.get(0);
    }

}
