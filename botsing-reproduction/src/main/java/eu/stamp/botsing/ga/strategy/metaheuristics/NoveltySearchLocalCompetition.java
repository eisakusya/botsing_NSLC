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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NoveltySearchLocalCompetition<T extends Chromosome> extends org.evosuite.ga.metaheuristics.NoveltySearch<T> {
    private static final Logger LOG = LoggerFactory.getLogger(NoveltySearchLocalCompetition.class);
    Mutation mutation;
    private int nicheSize;
    private final WeightedSum crashCoverage;
    private int populationSize;

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
        this.setNoveltyFunction(new NoveltyFunction(targetTrace));
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

            evolve();
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
            individual.addFitness(crashCoverage);

            population.add(individual);
            if (isFinished()) {
                break;
            }

        }

    }

    protected void evolve(){

    }



}
