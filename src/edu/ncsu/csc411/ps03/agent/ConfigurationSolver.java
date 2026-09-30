package edu.ncsu.csc411.ps03.agent;
import java.util.Random;
import edu.ncsu.csc411.ps03.environment.Environment;

/**
	Represents a linear assignment problem where N workers must be assigned
	to N tasks. Each worker/task combination is further associated with some
	value. The goal of this task is the produce an optimal configuration that
	maximizes (or minimizes) the sum of the assigned worker/task values.
	@author aidannunn
*/
public class ConfigurationSolver {
	/** Environment with values and fitness scores*/
	private Environment env;
	/** Current state of the values and tasks, where index i is the task assigned to worker i */
	private int[] configuration;
	/** Highest-scoring configuration so far*/
	private int[] bestConfiguration;
	/** Current time step which is increment per call to updateSearch()*/
	private int t;
	/** A random generator for choosing neighbors and accepting worse moves to get a better configuration*/
	private Random random;
	
	/** Starting temperature, allows worse moves to be accepted earlier*/
	private static final double INITIAL_TEMPERATURE = 100.0;
	/** Multiplier applied to the temperature at each time step to decrease temperature */
	private static final double COOLING_RATE = 0.995;
	/** Temperature below which the configuration is cool enough and stops.*/
	private static final double COOL_ENOUGH = 0.01;
	
	/** Initializes a Configuration Solver for a specific environment. */
	public ConfigurationSolver (Environment env) { 
		this.env = env;
		this.configuration = new int[this.env.getNumWorkers()];
		// Initializing by assigning work to an arbitrary task
		for (int i = 0; i < this.configuration.length; i++) {
			this.configuration[i] = i;
		}
		// Need to clone because of how Java handles assigning arrays
		// as values (relative referencing). Recall this is similar to
		// that one lecture in CSC 116 where if we don't duplicate the second
		// array, it will constantly change as this.configuration changes.
		// Cloning the array resolves this issue.
		this.bestConfiguration = this.configuration.clone();
		this.t = 0;
		this.random = new Random();
	}

	/**
	 * Performs an iteratoin of Simulated Annealing and returns the resulting configuration
	 * The simulation calls this method and acts as the alogorithms "from 1 to infinity"
	 * 
	 * @return return the current configuration, where index i is the task assigned to worker i
	 */
	public int[] updateSearch () {
		//first we need to increment t because we have to advance to the next time step.
		this.t++;
		//then we need to get the temperature according to the temperature schedule
		double T = schedule(this.t);
		//check whether T has hit the floor we have allowed
		if (T < COOL_ENOUGH) {
			//we can return the current configuration;
			return this.configuration;
		}
		
		//pick a random neighbor in this configuration to swap
		int[] candidate = randomNeighbor(this.configuration);
		//then calculate the difference between this potential new configuration score and the current configuration score
		int E = env.calcScore(candidate) - env.calcScore(this.configuration);
		//check if E is greater than 0 if it is, then we can set it to the current configuration
		if (E > 0) {
			this.configuration = candidate;
		}
		else { //else its less than or equal to 0 and we need to determine the probability of taking this new lower configuration for a potential for a higher one
			double prob = probability(E, T);
			//then determine against a psuedo-random generator if we take this "worse" configuration
			if (this.random.nextDouble() < prob) {
				this.configuration = candidate;
			}
		}
		//then we need to track the best configuration we have seen so far, since we may be move away from it
		if (env.calcScore(this.configuration) > env.calcScore(this.bestConfiguration)) {
			//if our current configuration is better than a previous one we have seen, we need to track it.
			this.bestConfiguration = this.configuration.clone();
		}
		return this.configuration;
	}
	
	/**
	 * Exponential cooling schedule which determines the rate of which we drop the temperature
	 * Temperature T starts at INITIAL_TEMPERATURE and is multiplied by COOLING_RATE each time step,
	 * so it drops off quickly and then gradually levels.
	 * 
	 * @param t current time step
	 * @return temperature at time step t
	 */
	private double schedule(int t) {
		return INITIAL_TEMPERATURE * Math.pow(COOLING_RATE, t);
	}
	
	/**
	 * Generates a random neighbor to swap the tasks of two randomly chosen workers. The result
	 * is a valid configuration.
	 * @param state the current configuration
	 * @return a new configuration with two distinct workers' tasks swapped
	 */
	private int[] randomNeighbor(int [] state) {
		//get a copy of the configuration
		int[] neighbor = state.clone();
		//pick a random integer from 0 to length - 1 of this array, serves as an index
		int a = this.random.nextInt(neighbor.length);
		//pick another random integer index from 0 to length - 2 of this array, serves as an index
		int b = this.random.nextInt(neighbor.length - 1);
		//if the second worker index we picked is the same or greater than the first one we need to increment
		//to guarantee we pick a new worker.
		if (b >= a) {
			b++;
		}
		//swap the worker tasks
		int temp = neighbor[a];
		neighbor[a] = neighbor[b];
		neighbor[b] = temp;
		return neighbor;
	}
	
	/**
	 * Probability of accepting a worse neighbor
	 * @param E the change in fitness
	 * @param T the current temperature
	 * @return the probability between 0 and 1, closer to 1 is when T is higher or when E is small.
	 */
	private double probability(int E, double T) {
		return Math.exp(E / T);
	}
	
	/**
	 * In addition to updateSearch, you should also track you BEST OBSERVED CONFIGURATION.
	 * While your search may move to worse configurations (since that's what the algorithm
	 * needs to do), getBestConfiguration will return the best observed configuration by your
	 * agent.
	 * You do not need to change this method. Update bestConfiguration in updateSearch.
	*/
	public int[] getBestConfiguration() {
		return this.bestConfiguration;
	}
	
}