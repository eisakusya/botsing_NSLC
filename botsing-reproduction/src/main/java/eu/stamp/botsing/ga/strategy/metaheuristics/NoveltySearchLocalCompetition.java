package eu.stamp.botsing.ga.strategy.metaheuristics;

import eu.stamp.botsing.CrashProperties;
import eu.stamp.botsing.StackTrace;
import eu.stamp.botsing.commons.ga.strategy.operators.Mutation;
import eu.stamp.botsing.fitnessfunction.NoveltyFunction;
import eu.stamp.botsing.fitnessfunction.WeightedSum;
import eu.stamp.botsing.fitnessfunction.testcase.factories.StackTraceChromosomeFactory;
import eu.stamp.botsing.fitnessfunction.utils.WSEvolution;

import eu.stamp.botsing.ga.strategy.operators.CoverageAndNoveltyBasedSorting;
import org.evosuite.Properties;
import org.evosuite.ga.Chromosome;
import org.evosuite.ga.ChromosomeFactory;
import org.evosuite.ga.FitnessFunction;
import org.evosuite.ga.operators.crossover.CrossOverFunction;
import org.evosuite.utils.Randomness;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class NoveltySearchLocalCompetition<T extends Chromosome> extends org.evosuite.ga.metaheuristics.NoveltySearch<T> {
    private static final Logger LOG = LoggerFactory.getLogger(NoveltySearchLocalCompetition.class);
    Mutation mutation;
    protected double nicheFactor = 0.0;
    private final int nicheSize;//k值
    protected HashMap<T, List<T>> Niche = null;
    protected HashMap<T, Double> populationWithNovelty = null;
    protected HashMap<T, Integer> populationWithLC = null;
    protected double noveltyThreshold;
    private final WeightedSum crashCoverage;
    private final NoveltyFunction<T> noveltyFunction;
    private int populationSize;
    protected List<T> archive = null;
    protected List<T> bigArchive = null;
    protected CoverageAndNoveltyBasedSorting<T> sortingOperator;
    protected int stalledThreshold;
    protected int addingThreshold;
    protected double addingArchiveProbability;
    protected boolean considerCoverage = false;

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
        sortingOperator = new CoverageAndNoveltyBasedSorting<>();
        considerCoverage = CrashProperties.considerCoverage;
        noveltyThreshold = CrashProperties.noveltyThreshold;
        nicheFactor = CrashProperties.nicheFactor;
        nicheSize = 50;
        stalledThreshold = CrashProperties.stalledThreshold;
        addingThreshold = CrashProperties.addingThreshold;
        addingArchiveProbability = CrashProperties.addToArchiveProbability;

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
            LOG.info("Size of Big-Archive: {}", bigArchive.size());
            //求出每个个体与他最近的k个个体的新颖性指标
            updateNiche();

            if (considerCoverage) {
                //根据更优个数进行非支配性排序的准备
                calculateLocalCompetition();
            }
            //新颖性得分准备
            try {
                calculateNovelty();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            //排序，放入存档
            try {
                updateArchive();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            LOG.info("Size of Archive: {}|{}", archive.size(), nicheSize);

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

    protected void generatePopulation(int populationSize) {
        LOG.info("Creating random population");
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

    protected void calculateFitness() {
        LOG.debug("Calculate fitness for {} individuals.", populationSize);
        Iterator<T> iterator = population.iterator();
        while (iterator.hasNext()) {
            T c = iterator.next();
            if (isFinished()) {
                if (c.isChanged()) {
                    iterator.remove();
                }
            } else {
                calculateFitness(c);
            }
        }
    }

    protected void calculateFitness(T chromosome) {
        for (FitnessFunction<T> fitnessFunction : fitnessFunctions) {
            notifyEvaluation(chromosome);
            fitnessFunction.getFitness(chromosome);
        }
    }

    protected void updateArchive() throws Exception {
        //根据新颖性指标，将niche中的个体选中加入到存档
        archive = new ArrayList<>();
        int added = 0;
        int notAdded = 0;
        int maxNotAdded = 0;

        if (considerCoverage) {
//            for (Map.Entry<T, Double> individual : populationWithNovelty.entrySet()) {
//                //根据新颖性指标把个体加入存档
//                if ((individual.getValue() > noveltyThreshold) || Randomness.nextDouble() <= addingArchiveProbability) {
//                    archive.add(individual.getKey());
//                    if (notAdded > maxNotAdded) {
//                        maxNotAdded = notAdded;
//                    }
//                    notAdded = 0;
//                    ++added;
//                } else {
//                    notAdded++;
//
//                }
//            }

            //根据覆盖率和更优个数进行非支配性排序
            sortingOperator.computeRankingAssignment((FitnessFunction<T>) crashCoverage, populationWithLC, true);

        } else {
            //根据新颖性和覆盖率进行非支配性排序
            sortingOperator.computeRankingAssignment((FitnessFunction<T>) crashCoverage, populationWithNovelty);

        }
        //根据非支配性排序好的前沿进行是否加入档案的判断与选择
        List<T> nextPopulation = new ArrayList<>();
        int index = 0;
        List<T> front;
        List<T> f0 = sortingOperator.getSubfront(index);
        LOG.info("*Front 0 size:{}", f0.size());
        for (T individual : f0) {
            LOG.info("{}", individual.getFitnessValues().toString());
        }

        while (nextPopulation.size() < nicheSize) {
            front = sortingOperator.getSubfront(index);
            //还差多少个
            int capacity = nicheSize - nextPopulation.size();
            //看看空位能不能把下一个前沿全放进来
            if (capacity >= front.size()) {
                nextPopulation.addAll(front);
                //对成功加入个数进行调整
                added += front.size();
            } else {
                //将front里面的个体在哈希表里面找到对应的个体并连同他们的新颖性一起加入一个新的散列表

                HashMap<T, Double> frontNovelty = new HashMap<>();
                for (T individual : front) {
                    frontNovelty.put(individual, populationWithNovelty.get(individual));
                }
                //然后对该散列表进行排序
                List<Map.Entry<T, Double>> entryList = new ArrayList<>();
                entryList.addAll(frontNovelty.entrySet());
                entryList.sort(Map.Entry.comparingByValue());
                Collections.reverse(entryList);
//                    if (entryList.get(0).getValue() < noveltyThreshold) {
//                        continue;
//                    }
                //取出满足条件的部分放入列表
                //既要满足大于阈值，而且数量也要不超过空位
                int ctr = 0;
                for (Map.Entry<T, Double> entry : entryList) {
                    if (ctr >= capacity) {
                        break;
                    }
                    nextPopulation.add(entry.getKey());
                    ctr++;
                }
            }
            index++;
        }

        archive.clear();
        archive.addAll(nextPopulation);



        //对相关参数的调整更新
        if (maxNotAdded > stalledThreshold) {
            //降低novelty threshold
            noveltyThreshold *= 0.95;
            notAdded = 0;
        } else if ((added > addingThreshold) || (archive.size() >= nicheSize)) {
            //提高novelty threshold
            noveltyThreshold *= 1.05;
        }
        this.population = archive;
    }

    protected void emerge() {
        //将旧的存档和今代的新种群进行合并，形成bigArchive
        bigArchive = new ArrayList<>(archive);
        bigArchive.addAll(this.population);

    }

    protected void updateNiche() {
        Niche = new HashMap<T, List<T>>();
        /*
        目标：对于大存档内每个个体而言，找出他们的k个邻居
        1.对大存档内每个个体进行遍历，求出其他个体到这个个体的距离
        2.对他的其他个体进行排序
        3.把其他前k个个体放到他的List内
         */
        for (T individual : bigArchive) {
            //散列表用于存储其他个体与当前个体之间的距离
            HashMap<T, Double> noveltyMap = new HashMap<>();
            //计算其他个体与当前个体的距离
            for (T others : bigArchive) {
                //如果距离是1，那么就是自己
                double distance = this.noveltyFunction.getDistance(individual, others);
                if(Math.abs((distance-1.0))<1e-10){
                    continue;
                }
                noveltyMap.put(others, distance);
            }
            //对键值对进行排序，从而的出最近的k个个体
            List<Map.Entry<T, Double>> list = new ArrayList<>(noveltyMap.entrySet());
            list.sort(Map.Entry.comparingByValue());
            Collections.reverse(list);
            //新颖性按降序排序
            //将最近的k个个体存储到一个列表内
            List<T> listOther = new ArrayList<>();
            for(int i=0;i< list.size();i++){
                if(i>=nicheSize){
                    break;
                }
                listOther.add(list.get(i).getKey());
            }
            //并根据每个个体与离他最近的k个个体这一关系存储在散列表当中
            Niche.put(individual, listOther);
        }
    }

    protected void calculateNovelty() throws Exception {
        //对每个个体的局部空间进行新颖性计算并用散列表进行记录
        LOG.debug("Calculating novelty for " + this.Niche.size() + " individuals");
        populationWithNovelty = new HashMap<T, Double>();
        if (Niche.isEmpty()) {
            LOG.warn("Niche is empty");
            throw new Exception("Niche is empty!");
        }
        for (Map.Entry<T, List<T>> individual : Niche.entrySet()) {
            List<T> list = individual.getValue();
            Double novelty = noveltyFunction.getNovelty(individual.getKey(), list);
            //该population实际大小是big-archive的大小
            //即对big-archive的每个个体都进行新颖性计算后得到的个体和他们对应的新颖值
            populationWithNovelty.put(individual.getKey(), novelty);
        }
    }

    protected void calculateLocalCompetition() {
        //对每个个体进行局部空间的竞争值计算
        LOG.debug("Calculating local competition for {} individuals", this.Niche.size());
        populationWithLC = new HashMap<>();
        /*
        1.遍历每个个体和他的k个邻居
        2.计算比较个体和邻居的适应度数值，并记录比个体更优的个数
        3.将个体和更有个数作为键值对放到新的散列表进行下一步操作
         */
        for (Map.Entry<T, List<T>> individualAndNeighbor : Niche.entrySet()) {
            T individual = individualAndNeighbor.getKey();
            List<T> neighbors = individualAndNeighbor.getValue();
            //比当前个体更优的个体数
            int betterCtr = 0;
            for (T neighbor : neighbors) {
                if (individual.getFitness(crashCoverage) > neighbor.getFitness(crashCoverage)) {
                    //目标值越小越好
                    betterCtr++;
                }
            }

            populationWithLC.put(individual, betterCtr);
        }

    }

    public T getBestIndividual() {
        //返回最优解
        if (this.population.isEmpty()) {
            return this.chromosomeFactory.getChromosome();
        }
        //对存档进行排序

        return archive.get(0);
    }

}
