package eu.stamp.botsing.ga.strategy.metaheuristics;

import eu.stamp.botsing.CrashProperties;
import eu.stamp.botsing.StackTrace;
import eu.stamp.botsing.commons.ga.strategy.operators.Mutation;
import eu.stamp.botsing.fitnessfunction.NoveltyFunction;
import eu.stamp.botsing.fitnessfunction.testcase.factories.StackTraceChromosomeFactory;
import eu.stamp.botsing.fitnessfunction.utils.CrashDistanceEvolution;
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
    private int nicheSize;//k值
    protected HashMap<T, List<T>> Niche = null;
    protected HashMap<T, Double> populationWithNovelty = null;
    protected HashMap<T, Integer> populationWithLC = null;
    protected double noveltyThreshold;
    protected double epsilon;
    private FitnessFunction<T> crashCoverage = null;
    private final NoveltyFunction<T> noveltyFunction;
    private int populationSize;
    protected List<T> archive = null;
    protected List<T> union = null;

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
        //StackTrace targetTrace =CrashProperties.getInstance().getStackTrace(0)
        //crashCoverage = fitnessFunctions.get(0);
        noveltyFunction = new NoveltyFunction<>(targetTrace);
        archive = new ArrayList<>();
        epsilon = CrashProperties.epsilon;
        nicheFactor = CrashProperties.nicheFactor;

    }

    public void generateSolution() {
        crashCoverage = fitnessFunctions.get(0);
        CrashDistanceEvolution.getInstance().setStartTime(this.listeners);
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
        int generation = 0;

        while (!isFinished()) {

            ++generation;
            LOG.info("Number of generations: {}", generation);

            //进化
            if (generation > 1) {
                population.clear();
                population.addAll(archive);
            } else if (generation == 1) {
                //初始化，第一代，存档为空
                archive.clear();
                archive.addAll(population);
            }
            evolve();
            //将子代和父代进行合并
            emerge();
            LOG.info("Size of Union: {}", union.size());

            //求出每个个体与他最近的k个邻居
            updateNiche();

            //根据更优个数进行非支配性排序的准备
            calculateLocalCompetition();

            //新颖性得分准备
            try {
                calculateNovelty();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            //非支配性排序，放入存档
            try {
                updateArchive();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            LOG.info("Size of Archive: {}", archive.size());

            this.notifyIteration();
            this.writeIndividuals(this.archive);
        }

    }

    public void initializePopulation() {
        if (!population.isEmpty()) {
            return;
        }
        //Start generating population.
        LOG.debug("Initializing the population.");
        generatePopulation(populationSize);
        calculateFitness(population);     //only one main objective
        this.notifyIteration();
    }

    protected void generatePopulation(int populationSize) {
        LOG.info("Creating random population");
        population = new ArrayList<>(populationSize);
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

    @Override
    protected void evolve() {
        List<T> offspringPopulation = new ArrayList<>(populationSize);

        for (int i = 0; i < (populationSize / 2); ++i) {
            T parent1 = selectionFunction.select(population);
            T parent2 = selectionFunction.select(population);

            T offspring1 = (T) parent1.clone();
            T offspring2 = (T) parent2.clone();


            try {
                if (Randomness.nextDouble() <= Properties.CROSSOVER_RATE) {
                    crossoverFunction.crossOver(offspring1, offspring2);
                }
            } catch (Exception e) {
                LOG.info("Crossover failed");
            }

            if (Randomness.nextDouble() <= Properties.MUTATION_RATE) {
                notifyMutation(offspring1);
                mutation.mutateOffspring(offspring1);
                notifyMutation(offspring2);
                mutation.mutateOffspring(offspring2);
            }

            calculateFitness(offspring1);
            calculateFitness(offspring2);

            offspringPopulation.add(offspring1);
            offspringPopulation.add(offspring2);

        }

        population = new ArrayList<>(offspringPopulation);
    }

    protected void calculateFitness(List<T> pop) {
        if (pop.isEmpty()) {
            return;
        }
        LOG.debug("Calculate fitness for {} individuals.", pop.size());
        Iterator<T> iterator = pop.iterator();
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
        notifyEvaluation(chromosome);
        crashCoverage.getFitness(chromosome);
    }

    protected void updateArchive() throws Exception {
        //根据新颖性得分和局部竞争目标进行非支配性排序
        CoverageAndNoveltyBasedSorting<T> sortingOperator = new CoverageAndNoveltyBasedSorting<T>(populationWithNovelty, populationWithLC);
        sortingOperator.nondominatedSort();

        List<T> newArchive = new ArrayList<>();
        List<T> front;
        int frontSize = sortingOperator.getNumberOfSubfronts() - 1;
        int index = 0;
        while (newArchive.size() < populationSize && index < frontSize) {
            front = new ArrayList<>(sortingOperator.getSubfront(index));
            int capacity = populationSize - newArchive.size();
            if (capacity >= front.size()) {
                newArchive.addAll(front);
            } else {
                //update algo
                for (T chromosome : front) {
                    //计算要加进去的个体与已存在存档中的个体的距离
                    //找出邻居中最近的个体
                    double min_d = Double.MAX_VALUE;
                    T closest = null;
                    for (T neighbor : newArchive) {
                        double distance = noveltyFunction.getDistance(chromosome, neighbor);
                        if (distance < min_d) {
                            min_d = distance;
                            closest = neighbor;
                        }
                    }

                    //档案更新
                    double maxDensity = 0.0;
                    for (int i = 0; i < newArchive.size(); ++i) {
                        List<T> niche = new ArrayList<>(newArchive);
                        niche.remove(i);
                        double density = noveltyFunction.getNovelty(newArchive.get(i), niche);
                        if (density > maxDensity) {
                            maxDensity = density;
                        }
                    }
                    //threshold取最大的疏松度（新颖性分数）
                    noveltyThreshold = maxDensity;

                    if (min_d > noveltyThreshold) {
                        //如果当前个体与最近邻个体距离大于设定阈值，则直接加入存档
                        newArchive.add(chromosome);
                        //LOG.info("A new individual is added to archive");
                        if (newArchive.size() >= populationSize) {
                            break;
                        }
                    } else if (epsilonDominance(chromosome, closest, newArchive)) {
                        //如果当前个体满足exclusive e-dominance支配最近邻，那么当前个体替换最近邻
                        int index1 = newArchive.indexOf(closest);
                        newArchive.set(index1, chromosome);
                        //LOG.info("An old individual is replaced by a new one in position {}", index1);
                    }
                }
            }
            index++;
        }
        archive.clear();
        archive.addAll(newArchive);

    }

    protected boolean epsilonDominance(T x1, T x2, List<T> pop) {
        //exclusive epsilon-dominance
        //使用新颖性得分和FF进行评估
        double e = epsilon;
        List<T> niche = new ArrayList<>(pop);
        niche.remove(pop.indexOf(x2));

        double N1 = noveltyFunction.getNovelty(x1, pop);
        double N2 = noveltyFunction.getNovelty(x2, niche);
        double Q1 = -(x1.getFitness());
        double Q2 = -(x2.getFitness());
        boolean var1 = N1 >= (1 - e) * N2;
        boolean var2 = Q1 >= (1 - e) * Q2;
        boolean var3 = Q2 * (N1 - N2) > (-N2) * (Q1 - Q2);
        return var1 && var2 && var3;
    }

    protected void emerge() {
        //子代和父代合并
        union = new ArrayList<>(archive);
        union.addAll(this.population);
    }

    protected void updateNiche() {
        //找到每个个体的最近邻
        //邻域大小由合并种群大小以及因子决定
        nicheSize = (int) (union.size() * nicheFactor);
        LOG.info("The size of neighborhood is {}", nicheSize);
        Niche = new HashMap<T, List<T>>();
        /*
        目标：对于大存档内每个个体而言，找出他们最近的k个邻居
        1.对大存档内每个个体进行遍历，求出其他个体到这个个体的距离
        2.把前k个个体放到他的邻域内
         */

        for (int i = 0; i < union.size(); ++i) {
            //散列表用于存储其他个体与当前个体之间的距离
            HashMap<T, Double> noveltyMap = new HashMap<>();

            //得到除了自己以外的个体的列表
            List<T> Others = new ArrayList<>(union);
            Others.remove(i);

            //遍历其他个体，计算当前个体与其他个体的距离
            for (int j = 0; j < Others.size(); ++j) {
                double distance = noveltyFunction.getDistance(union.get(i), Others.get(j));
                noveltyMap.put(Others.get(j), distance);
            }

            //取出前k个个体，作为当前个体的邻域
            List<T> neighborhood = new ArrayList<>();
            for (int j=0;j<nicheSize;++j){
                Double closestValue=Double.MAX_VALUE;
                T closestIndividual=null;
                for (Map.Entry<T,Double> tDoubleEntry:noveltyMap.entrySet()){
                    if (tDoubleEntry.getValue()<closestValue){
                        closestIndividual=tDoubleEntry.getKey();
                        closestValue=tDoubleEntry.getValue();
                    }
                }
                neighborhood.add(closestIndividual);

            }
            //对其他个体进行距离排序
//            List<Map.Entry<T, Double>> entryList = new ArrayList<>(noveltyMap.entrySet());
//            entryList.sort(Map.Entry.comparingByValue());


//            for (int k = 0; k < nicheSize; ++k) {
//                neighborhood.add(entryList.get(k).getKey());
//            }

            //按照个体->邻域的对应关系进行存储
            Niche.put(union.get(i), neighborhood);
        }

    }

    protected void calculateNovelty() throws Exception {
        //f1的计算
        //计算每个个体的新颖性得分
        LOG.debug("Calculating novelty score for " + this.Niche.size() + " individuals");
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
        2.计算比较个体和邻居的适应度数值，并记录比个体更差的个数
        3.将个体和更有个数作为键值对放到新的散列表进行下一步操作
         */

        for (Map.Entry<T, List<T>> individualAndNeighbor : Niche.entrySet()) {
            T individual = individualAndNeighbor.getKey();
            //obtain FF value
            double f1 = individual.getFitness();
            List<T> neighbors = individualAndNeighbor.getValue();
            //比当前个体更差的个体数
            int worseCtr = 0;
            worseCtr = calculateWorseCtr(neighbors, f1, worseCtr);

            populationWithLC.put(individual, worseCtr);
        }

    }

    public T getBestIndividual() {
        //返回最优解
        if (this.population.isEmpty()) {
            return this.chromosomeFactory.getChromosome();
        }
        double bestFF = Double.MAX_VALUE;
        T bestIndividual = null;
        for (T individual : archive) {
            if (individual.getFitness() < bestFF) {
                bestFF = individual.getFitness();
                bestIndividual = individual;
            }
        }

        return bestIndividual;
    }

    protected int calculateWorseCtr(List<T> neighborhood, double f1, int worseCtr) {
        for (int j = 0; j < neighborhood.size(); ++j) {
            crashCoverage.getFitness(neighborhood.get(j));
            double f2 = neighborhood.get(j).getFitness();
            int flag = Double.compare(f1, f2);
            if (flag < 0) {
                //当前个体更优
                worseCtr++;
            }
        }
        return worseCtr;
    }

}
