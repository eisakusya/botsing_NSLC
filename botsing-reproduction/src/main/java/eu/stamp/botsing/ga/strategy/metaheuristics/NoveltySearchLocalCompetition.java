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
    protected List<T> parents = null;
    protected HashMap<T, Double> populationWithNovelty = null;
    protected HashMap<T, Integer> populationWithLC = null;
    protected double noveltyThreshold;
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

        noveltyThreshold = CrashProperties.noveltyThreshold;
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
            parents = new ArrayList<>(population);
//            if (generation > 1) {
//                //将档案中最优解作为种子进行种群生成
//                try {
//                    population = seed();
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                }
//            }
            evolve();
            //子代生成,使用不排序的父代进行遗传变异

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
            this.writeIndividuals(this.population);
        }
        try {
            archive = seed();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void initializePopulation() {
        if (!population.isEmpty()) {
            return;
        }
        //Start generating population.
        LOG.debug("Initializing the population.");
        generatePopulation(populationSize);
//        calculateFitness();     //only one main objective
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
        double e = 0.3;

        //根据非支配性排序好的f0前沿维护档案
        List<T> f0 = sortingOperator.getSubfront(0);
        LOG.info("*Front 0 size:{}", f0.size());

        for (T chromosome : f0) {
            HashMap<T, Double> neighborhood = new HashMap<>();
            /*
            要先对存档初始化两个元素
            否则对于NSLC的计算会造成影响
             */
            if (archive.size() < 2) {
                archive.add(chromosome);
                LOG.info("Archive initialization with {} individual", archive.size());
                break;
            }

            //计算要加进去的个体与已存在存档中的个体的距离
            for (T neighbor : archive) {
                double distance = noveltyFunction.getDistance(chromosome, neighbor);
                neighborhood.put(neighbor, distance);
            }

            //找出邻居中最近的个体，优化：去掉排序
            List<Map.Entry<T, Double>> entryList = new ArrayList<>(neighborhood.entrySet());
            Map.Entry<T, Double> closest = entryList.get(0);
            for (int i = 1; i < entryList.size(); ++i) {
                if (entryList.get(i).getValue() < closest.getValue()) {
                    closest = entryList.get(i);
                }
            }

            //档案维护
            double maxDensity = 0.0;
            for (int i = 0; i < archive.size(); ++i) {
                List<T> niche = new ArrayList<>(archive);
                niche.remove(i);
                double density = noveltyFunction.getNovelty(archive.get(i), niche);
                if (density > maxDensity) {
                    maxDensity = density;
                }
            }
            //threshold取最大的疏松度（新颖性分数）
            noveltyThreshold = maxDensity;

            if (closest.getValue() > noveltyThreshold) {
                //如果当前个体与最近邻个体距离大于设定阈值，则直接加入存档
                archive.add(chromosome);
                LOG.info("A new individual is added to archive");
            } else if (epsilonDominance(chromosome, closest.getKey(), e)) {
                //如果当前个体满足exclusive e-dominance支配最近邻，那么当前个体替换最近邻
                int index1 = archive.indexOf(closest.getKey());
                archive.set(index1, chromosome);
                LOG.info("An old individual is replaced by a new one in position {}", index1);
            }

        }

    }

    protected boolean epsilonDominance(T x1, T x2, double e) {
        //exclusive epsilon-dominance
        //使用新颖性得分和局部竞争目标进行评估
        List<T> niche = new ArrayList<>(archive);
        niche.remove(archive.indexOf(x2));

        double N1 = noveltyFunction.getNovelty(x1, archive);
        double N2 = noveltyFunction.getNovelty(x2, niche);
        double Q1 = -(x1.getFitness());
        double Q2 = -(x2.getFitness());
//        double Q1 = calculateLocalCompetition(x1,archive);
//        double Q2 = calculateLocalCompetition(x2,niche);
        boolean var1 = N1 >= (1 - e) * N2;
        boolean var2 = Q1 >= (1 - e) * Q2;
        boolean var3 = Q2 * (N1 - N2) > (-N2) * (Q1 - Q2);
        return var1 && var2 && var3;
    }

    protected void emerge() {
        //子代和父代合并
        union = new ArrayList<>(parents);
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

            //对其他个体进行距离排序
            List<Map.Entry<T, Double>> entryList = new ArrayList<>(noveltyMap.entrySet());
            entryList.sort(Map.Entry.comparingByValue());

            //取出前k个个体，作为当前个体的邻域
            List<T> neighborhood = new ArrayList<>();
            for (int k = 0; k < nicheSize; ++k) {
                neighborhood.add(entryList.get(k).getKey());
            }

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

    protected int calculateLocalCompetition(T individual, List<T> others) {
        crashCoverage.getFitness(individual);
        double f1 = individual.getFitness();

        int worseCtr = 0;
        if (others.isEmpty()) {
            return worseCtr;
        }
        worseCtr = calculateWorseCtr((List<T>) others, f1, worseCtr);
        return worseCtr;
    }

    public T getBestIndividual() {
        //返回最优解
        if (this.population.isEmpty()) {
            return this.chromosomeFactory.getChromosome();
        }
        //assume that the archive is sorted
        return archive.get(0);
    }

    protected List<T> seed() throws Exception {
        //选出存档中最优作为种子
        if (archive.size() <= 1) {
            return archive;
        }
        List<T> archiveClone = new ArrayList<>(archive);
        HashMap<T, Double> archiveWithNoveltyScore = new HashMap<>();
        HashMap<T, Integer> archiveWithLocalCompetition = new HashMap<>();

        for (int i = 0; i < archiveClone.size(); i++) {
            List<T> neighborhood = new ArrayList<>(archiveClone);
            neighborhood.remove(i);

            //存档内的新颖性计算
            double novelty = noveltyFunction.getNovelty(archiveClone.get(i), neighborhood);
            archiveWithNoveltyScore.put(archiveClone.get(i), novelty);

            //存档内的LC计算
            double f1 = archiveClone.get(i).getFitness();
            int worseCtr = 0;
            worseCtr = calculateWorseCtr((List<T>) neighborhood, f1, worseCtr);
            archiveWithLocalCompetition.put(archiveClone.get(i), worseCtr);
        }

        CoverageAndNoveltyBasedSorting<T> frontOperator = new CoverageAndNoveltyBasedSorting<>(archiveWithNoveltyScore, archiveWithLocalCompetition);
        frontOperator.nondominatedSort();

        List<T> f0 = frontOperator.getSubfront(0);
        T optimal = f0.get(0);
        for (int i = 1; i < f0.size(); ++i) {
            double score = f0.get(i).getFitness();
            if (score < optimal.getFitness()) {
                optimal = f0.get(i);
            }
        }
        List<T> Seed = new ArrayList<>();
        Seed.add(optimal);

        return Seed;
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
