import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Arrays;
import java.util.Random;

/*
Assignment week 6
Note: the assignment says would be faster using SCC algorithm from the Algo 1 course.
Here using a modified local search Papadimitriou algo, it succeeds a bit slowly [32 secs] for 1M rows input test data) only after following mods:
	1) instead of outer loop log2(n), just allow 2x
	2) instead of inner loop 2*n*n, just allow 2*n
	3) a lot of extra space used to make this work, array of mappings of clauses per data line... array for speed means a lot more space than needed.
		similarly, failedClauses array which is initialized to max possible size to avoid dynamic instantiation of any objects/lists
	4) someone realized if a var is only used in a single clause it can be removed from that clause ... obviously makes conditionals on it way better probability
	
This code became quite complex because found that making a complete test of all clauses on every flip was too slow, so now it carefully modifies a list
of failure clauses, each time removing any now satisfied clauses and inserting any now broken (but previously ok) clauses. 
*/

public class w6_2sat_papadimitriou_local_search {

	private static boolean debug = false;
    private static long start;	
	
    private static void compute2SatAssignments(String hint, String fname) throws FileNotFoundException {
        
		File file = new File(System.getProperty("user.dir"), fname);
        int l = 0;
        
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            
        	String line = br.readLine(); 			// first line is summary data
            String[] summary = line.split("\\s");
            int N = Integer.parseInt(summary[0]);

            int[][] data = new int[N][2];
            
            while ((line = br.readLine()) != null) {
            	String[] strs = line.split("\\s");
                int x = Integer.parseInt(strs[0]), y = Integer.parseInt(strs[1]);
                
                data[l++] = new int[] { x, y };
            }
            
            compute2SatAssignments(hint, fname, data);

        }
        catch (FileNotFoundException fe) {
        	throw fe;
        }
        catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
        }        
    }	
	
    private static void compute2SatAssignments(String hint, String source, int[][] rawClauses) {
    	
    	final int n = rawClauses.length;
    	int[] clauseMentions = new int[n];
    	Clause[] clauses = convertRawClauses(rawClauses, clauseMentions);
    	final boolean[] data = new boolean[n];
    	final int[][] dataInClausesMap = mapDataToClauses(clauseMentions, clauses);
    	final boolean[] varPassesBeforeFlip = new boolean[dataInClausesMap[0].length];	// 2 arrays of len max number of clauses any var is used in
    	final boolean[] varPassesAfterFlip = new boolean[dataInClausesMap[0].length];	// these are reused each time a var is flipped, to save object churn
    	final int[] failedClauses = new int[n];
    	final Random r = new Random();

    	if (debug) System.out.println(String.format("num clauses=%d", n));

    	long accumNanosTests = 0L;
    	long accumNanosFlips = 0L;
    	
//    	for (int i = 0; i < log2(n); i++) {		// outer loop repeats log2 n times
    	for (int i = 0; i < 1; i++) {			// suggested on forum only 1 trial needed, prob fine after single clause changes
    		makeRandomAssignment(data, r);		// any random assignment

			long before = System.nanoTime();
			int numFailures = testAllClauses(data, clauses, failedClauses);
			accumNanosTests += System.nanoTime() - before;
			if (numFailures == 0) {
		    	System.out.println(String.format("metrics %.4f secs on tests, %.4f secs on random flips", accumNanosTests / 1000000000.0f, accumNanosFlips / 1000000000.0f));
				printResults(source, hint, "SATISFIED, at initial random assignment");
				return;
			}

			if (debug) reportFailures(failedClauses, numFailures);	    	
			
//    		for (int j = 0; j < 2*n*n; j++) {	// inner loop repeats max 2n**2 times
    		for (int j = 0; j < 2*n; j++) {		// suggested on forum linear in n, try 2 n

				// each time a var is flipped, adjust the array of failures if that particular clause is now satisfied
    			before = System.nanoTime();
				int flippedIdx = chooseFlipVarAtRandom(failedClauses, numFailures, clauses, data, r);
				
				if (debug) System.out.println(String.format("\t\trandom flip index=%d, before flip=%s",  flippedIdx, data[flippedIdx]));
				int failsBeforeFlip = findClausesForFlippedVar(data, clauses, flippedIdx, dataInClausesMap, varPassesBeforeFlip);
				
				data[flippedIdx] = !data[flippedIdx];
				
				if (debug) System.out.println(String.format("\t\trandom flip index=%d, after flip=%s",  flippedIdx, data[flippedIdx]));
				int failsAfterFlip = findClausesForFlippedVar(data, clauses, flippedIdx, dataInClausesMap, varPassesAfterFlip);
				
				if (debug) System.out.println(String.format("\t\trandom flip index=%d, fails before flip=%d, after flip=%d",  flippedIdx, failsBeforeFlip, failsAfterFlip));

//				if (debug) System.out.println(String.format("\t\trandom flip clause=%d, part=%s, index=%d, prevValue=%s, newValue=%s, clauseNow=%s", 
//				randClause, (randVar == 0 ? "a" : "b"), varIdx, Boolean.toString(prevVal), Boolean.toString(data[varIdx]), Boolean.toString(clauses[randClause].passesTest(data))));
				
    			accumNanosFlips += System.nanoTime() - before;
    			
    			before = System.nanoTime();
    			numFailures = retestClausesForFlippedVar(data, clauses, failedClauses, numFailures, flippedIdx, dataInClausesMap, varPassesBeforeFlip, varPassesAfterFlip);
    			accumNanosTests += System.nanoTime() - before;    			

    			if (debug) reportFailures(failedClauses, numFailures);

				if (numFailures == 0) {
			    	System.out.println(String.format("metrics %.4f secs on tests, %.4f secs on random flips", accumNanosTests / 1000000000.0f, accumNanosFlips / 1000000000.0f));
    				printResults(source, hint, String.format("SATISFIED, random assignment #%d after %d random flips", i, j));
    				return;
    			}
    		}
    	}
    	
    	System.out.println(String.format("metrics %.4f secs on tests, %.4f secs on random flips", accumNanosTests / 1000000000.0f, accumNanosFlips / 1000000000.0f));
		printResults(source, hint, String.format("FAILED to find satisfiable assignment"));
    }

    private static int findClausesForFlippedVar(boolean[] data, Clause[] clauses, int flippedIdx, int[][] dataInClausesMap, boolean[] failingClauses) {

    	int failsOnFlipper = 0;
    	
    	for (int i = 0; i < dataInClausesMap[flippedIdx].length; i++) {
    		
    		if (dataInClausesMap[flippedIdx][i] == -1) {
    			break;
    		}
    		
    		int clauseIdx = dataInClausesMap[flippedIdx][i];
    		if (clauses[clauseIdx].idx1 != flippedIdx && clauses[clauseIdx].idx2 != flippedIdx) {
    			throw new RuntimeException(String.format("BUG: some clause doesn't mention id=%d clause=%s", flippedIdx, clauses[clauseIdx]));
    		}
    		
    		failingClauses[i] = clauses[clauseIdx].passesTest(data);
    		if (!failingClauses[i]) {
    	    	if (debug) System.out.println(String.format("\t\t\t\tflipped var idx=%d fails clause=%d", flippedIdx, clauseIdx));
    			failsOnFlipper++;
    		}
    	}
    	
//    	if (debug) System.out.println(String.format("\t\t\tflipped var idx=%d fails=%d", flippedIdx, failsOnFlipper));
    	
    	return failsOnFlipper;

	}

    /**
     * All the clauses that the flipped var are in that failed were logged prior to the flip and again afterwards.
     * This means it's possible to just test for differences, and apply those to the full set of failedClauses
     */
    private static int retestClausesForFlippedVar(boolean[] data, Clause[] clauses, int[] failedClauses, int numFailures, int flippedIdx, int[][] dataInClausesMap, boolean[] varPassesBeforeFlip, boolean[] varPassesAfterFlip) {

    	int failuresIdx = 0;					// as work through the failures for the var this index will go up (never down) so use to search for each subsequent clause
    	
    	// use the mapping of clauses for the var as the driver through the arrays 
    	for (int i = 0; i < dataInClausesMap[flippedIdx].length; i++) {
    		
    		if (dataInClausesMap[flippedIdx][i] == -1) {
    			break;
    		}

    		int clauseIdx = dataInClausesMap[flippedIdx][i];

    		// have a valid line for this var
    		boolean sameStatus = varPassesBeforeFlip[i] == varPassesAfterFlip[i];
    		if (sameStatus) {
    	    	if (debug) System.out.println(String.format("\t\t\t\tflipped var idx=%d after flipping no change to clause=%d (still pass=%s)", flippedIdx, clauseIdx, varPassesBeforeFlip[i]));
    		}
    		else {
    	    	if (debug) System.out.println(String.format("\t\t\t\tflipped var idx=%d after flipping clause=%d changed (pass was=%s, now=%s) action=%s", 
    	    			flippedIdx, clauseIdx, varPassesBeforeFlip[i], varPassesAfterFlip[i], varPassesAfterFlip[i] ? "REMOVE" : "INSERT"));
    	    	
    	    	// a particular clause's status has changed either from pass to fail or fail to pass
    	    	if (varPassesAfterFlip[i]) {							// now passes, remove a clause from failures list

    	    		while (failedClauses[failuresIdx] < clauseIdx) {
    	    			failuresIdx++;									// if this falls off the end of the array, there's a bug somewhere
    	    		}

//	    			if (failuresIdx == numFailures) {
//	    				throw new RuntimeException(String.format("BUG: finding clauseIdx=%d prev in failedClauses, at end of numFailures (=%d), last val=%d", clauseIdx, numFailures, failedClauses[failuresIdx-1]));
//	    			}

    	    		for (int j = failuresIdx; j < numFailures-1; j++) {	// shifting results down by one, so stop one before last
    	    			failedClauses[j] = failedClauses[j+1];			// overwrite with the next value
    	    		}
    	    		
    	    		numFailures--;										// dec total number of failures
    	    	}
    	    	else {													// now fails, have to insert a new failure

//    	    		if (debug) System.out.println(String.format("DEBUGGING insert: add clauseIdx=%d", clauseIdx));
    	    		
    	    		while (failedClauses[failuresIdx] < clauseIdx && failuresIdx < numFailures) {
//        	    		if (debug) System.out.println(String.format("DEBUGGING insert: finding insert point failuresIdx=%d", failuresIdx));
    	    			failuresIdx++;									// if this falls off the end of the array, there's a bug somewhere, but reaching numFailures could happen (it's increasing)
    	    		}
    	    		
    	    		numFailures++;										// inc total number of failures
    	    		
    	    		int prevFailure = clauseIdx;						// first to insert is the new failure
    	    		for (int j = failuresIdx; j < numFailures; j++) {	// shifting results up by one
        	    		
    	    			int swapVal = failedClauses[j];
//        	    		if (debug) System.out.println(String.format("DEBUGGING insert: shifting values up j=%d, swapOld=%d, swapNew=%d", j, swapVal, prevFailure));
        	    		
    	    			failedClauses[j] = prevFailure;					// overwrite with the prev failure
    	    			prevFailure = swapVal;							// store for the next row
    	    		}
    	    		
//    	    		if (debug) {	//TODO remove this, only to find bug with insert
//    	    			System.out.println(String.format("DEBUGGING: **************** Just did insert clauseIdx=%d (num=%d):", clauseIdx, numFailures));
//    	    			for (int j = 0; j < numFailures; j++) {
//    	    				System.out.println(String.format("failed clause idx=%d", failedClauses[j]));
//    	    			}    	    			
//    	    		}
    	    	}
    		}
    	}
    	
    	return numFailures;

	}

	private static void reportFailures(int[] failedClauses, int numFailures) {
		System.out.println(String.format("Sanity check failures (num=%d):", numFailures));
		for (int i = 0; i < numFailures; i++) {
			System.out.println(String.format("failed clause idx=%d", failedClauses[i]));
		}
	}

	private static int[][] mapDataToClauses(int[] clauseMentions, Clause[] clauses) {
		// try to speed up tests by pre-mapping every data element with the clauses it's involved in
    	
    	// find how many there are
    	int maxNum = 0, minNum = Integer.MAX_VALUE, which = -1, zeroMentions = 0, singleMentions = 0;
    	for (int i = 0; i < clauseMentions.length; i++) {
    		if (clauseMentions[i] > maxNum) {
    			if (debug) System.out.println(String.format("clause mentions goes up from %d to %d for line=%d", maxNum, clauseMentions[i], i+1));
    			which = i;
        		maxNum = Math.max(maxNum, clauseMentions[i]);
    		}
    		
    		minNum = Math.min(minNum, clauseMentions[i]);
    		
    		if (clauseMentions[i] == 0) {
    			zeroMentions++;
    		}
    		else if (clauseMentions[i] == 1) { 
    			singleMentions++;
    		}
    	}
    	
    	int[][] mappings = new int[clauseMentions.length][maxNum];
    	for (int i = 0; i < mappings.length; i++) {
    		Arrays.fill(mappings[i], -1);
    	}
    	
    	for (int i = 0; i < clauses.length; i++) {
    		
    		for (int j = 0; j < maxNum; j++) {
    			if (mappings[clauses[i].idx1][j] == -1) {
    				mappings[clauses[i].idx1][j] = i;
        			break;
    			}
    		}
    		for (int j = 0; j < maxNum; j++) {
    			if (mappings[clauses[i].idx2][j] == -1) {
    				mappings[clauses[i].idx2][j] = i;
        			break;
    			}
    		}
    	}
    	
    	// another sanity check, find all the mappings that only have one mention and check the num clauses mapped is one
    	int singleCheck = 0;
    	for (int i = 0; i < clauseMentions.length; i++) {
    		if (clauseMentions[i] == 1) { 
    			int[] varMappings = mappings[i];
    			if (varMappings[0] != -1 && varMappings[1] == -1) {
    				singleCheck++;
    				
    				// checks out, clear the var from the clause
    				clauses[varMappings[0]].removeVar(i);
    			}
    		}
    	}
    	
    	System.out.println(String.format("max times any line used in clauses=%d, (first line of that count=%d), min=%d, (number used no clauses=%d, one clause=%d, double checked=%d)", maxNum, which+1, minNum, zeroMentions, singleMentions, singleCheck));

    	
//    	// sanity check, the max one should have a valid value for every clause mentioned
//    	for (int i = 0; i < maxNum; i++) {
//    		System.out.println(String.format("\tline=%d mentioned in clause=%d", which+1, mappings[which][i]+1));
//    	}
//    	
//    	System.exit(0);
    	
		return mappings;
	}

	private static Clause[] convertRawClauses(int[][] rawClauses, int[] clauseMentions) {
		
    	Clause[] clauses = new Clause[rawClauses.length];
    	
    	for (int i = 0; i < clauses.length; i++) {
    		clauses[i] = new Clause(rawClauses[i]);
    		clauseMentions[clauses[i].idx1]++;
    		clauseMentions[clauses[i].idx2]++;
    	}
    	
		return clauses;
	}

	private static int chooseFlipVarAtRandom(int[] failedClauses, int numFailures, Clause[] clauses, boolean[] data, Random r) {
		// failedClauses is an array partially filled with indexes to the clauses array
		// where there was a failure
		
		int randClause = failedClauses[r.nextInt(numFailures)];
		int randVar = r.nextInt(2);
		
		int varIdx = randVar == 0 ? clauses[randClause].idx1 : clauses[randClause].idx2;
		return varIdx;
	}

	private static int testAllClauses(boolean[] data, Clause[] clauses, int[] failedClauses) {

    	int numFailures = 0;
    	
    	// as soon as get a failure break out
    	for (int i = 0; i < clauses.length; i++) {
    		
    		boolean clauseGood = clauses[i].passesTest(data);

    		if (debug) System.out.println(String.format("\ttest clause=%d result=%s, clause a=%d (requires=%s, val=%s), b=%d (requires=%s, val=%s)"
    				, i, Boolean.toString(clauseGood), 
    				clauses[i].idx1, clauses[i].test1, data[clauses[i].idx1],
    				clauses[i].idx2, clauses[i].test2, data[clauses[i].idx2]
    						));
			
			if (!clauseGood) {
				failedClauses[numFailures++] = i;
			}
    	}
    	
    	return numFailures;
	}

	private static void makeRandomAssignment(boolean[] data, Random r) {
		
		for (int i = 0; i < data.length; i++) {
			data[i] = r.nextBoolean();
			
			if (debug) System.out.println(String.format("\trandom assignment %d=%s (note: line numbers are +1 all indexes in debug statements)", i, data[i]));
		}
	}

	private static class Clause {
		private int idx1, idx2;
		private boolean test1, test2;
		
		public Clause(int[] rawClause) {
			int ri1 = rawClause[0];			// each is a number possibly negative
			int ri2 = rawClause[1];
			
			boolean rt1 = (ri1 >= 0);		// test for that number is negative if < 0
			boolean rt2 = (ri2 >= 0);
			
			if (!rt1) ri1 *= -1;			// it's an index, so no negatives
			if (!rt2) ri2 *= -1;
			
			ri1--;							// raw data indexes from 1, translate all down to 0
			ri2--;
			
			this.idx1 = ri1;
			this.idx2 = ri2;
			this.test1 = rt1;
			this.test2 = rt2;
		}

		public void removeVar(int i) {
			if (idx1 != i && idx2 != i) {
				throw new IllegalArgumentException(String.format("Request to remove var(%d) from clause where not used %s", i, this));
			}
			
			if (idx1 == i) {
				idx1 = -1;
			}
			if (idx2 == i) {
				idx2 = -1;
			}
		}

		public boolean passesTest(boolean[] data) {	// any var removed has idx set to -1 (can happen if only this clauses uses a var)
			return idx1 == -1 || idx2 == -1 || data[idx1] == test1 || data[idx2] == test2;
		}
		
		@Override
		public String toString() {
			return String.format("Clause [idx1=%d, idx2=%d, test1=%s, test2=%s]", idx1+1, idx2+1, test1, test2);
		}
	}
	
	// found this on stackoverflow for computing log to base, which is faster than standard (Math.log(x) / Math.log(2))
    // if ever number of bits in an int changes from 32 though, it will break
    private static int log2(int n){
        if(n <= 0) throw new IllegalArgumentException();
        return 31 - Integer.numberOfLeadingZeros(n);
    }
    
    private static void printResults(String source, String hint, String text) {
    	    	
		Runtime rt = Runtime.getRuntime();
        System.out.println(String.format("result=%s source='%s', hint='%s', elapsed millis=%d, used memory %dMB"
        		, text, source, hint, System.currentTimeMillis() - start
        		, (rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L)));
    }
    
    public static void main(String[] args) {
        start = System.currentTimeMillis();
        if (args.length > 0) {
        	debug = (args.length == 2 && args[1].equals("debug"));
        	try {
				compute2SatAssignments("answer: ?", "w6_2sat"+args[0]+".txt");
			} 
        	catch (FileNotFoundException e) {
				System.out.println("File not found ("+ "w6_2sat"+args[0]+".txt : probably need to unzip it, they are a bit large");
			} 
        }
        else {
        	// don't know the assignment format yet
        	debug = true;
        	start = System.currentTimeMillis();
        	compute2SatAssignments("answer: satisfiable, x1=x3=TRUE and x2=x4=FALSE", "lecture example", new int[][] { new int[] {1, 2}, new int[] {-1, 3}, new int[] {3, 4}, new int[] {-2, -4} });
        	start = System.currentTimeMillis();
//        	compute2SatAssignments("answer: ?", "?.txt");
        }
    }
}
