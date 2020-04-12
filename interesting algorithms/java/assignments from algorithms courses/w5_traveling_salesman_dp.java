import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;

/*
Assignment week 5, traveling salesman problem as per pseudo-code in the lectures using a dynamic programming approach
Needs a lot of memory, best to allocate it on the cmd line, as it is it needs 2055MB and runs in best about 40 secs:
	java -Xms2560m w5_traveling_salesman_dp w5_tsp.txt
*/

public class w5_traveling_salesman_dp {

    private static final int START_VERTEX = 0;	// the first vertex in the list is the starting vertex

    private static boolean debug = false;
    private static long start;	
    
    private static void computeShortestTour(String hint, String fname) {
        
		File file = new File(System.getProperty("user.dir"), fname);
        int l = 0;
        
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            
        	String line = br.readLine(); 			// first line is summary data
            String[] summary = line.split("\\s");
            int N = Integer.parseInt(summary[0]);

            float[][] data = new float[N][2];
            
            while ((line = br.readLine()) != null) {
            	String[] strs = line.split("\\s");
                float x = Float.parseFloat(strs[0]), y = Float.parseFloat(strs[1]);
                
                data[l++] = new float[] { x, y };
            }
            
            if (debug) System.out.println(String.format("expected num sets=%d", (int)Math.pow(2, data.length-1)-1));
            
        	computeShortestTour(hint, fname, data);

        }
        catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
        }        
    }

    private static void computeShortestTour(String hint, String source, float[][] data) {
    	
    	// the number of sets doesn't include the start vertex since that is fixed
    	int setCount = (int)Math.pow(2, data.length-1)-1;
    	
//    	int maxMembersInAnySet = getMaxNumSetsForAnyMemberCount(data, setCount);
        int[][] setsByMemberCount = makeSets(data, setCount);
        
//        if (debug) System.out.println(String.format("number of sets S=%d, max members in any set=%d", setCount, maxMembersInAnySet));
        
        // need sets for 2**(datalen-1) since the start vertex is constant, can 
        float[][] A = new float[setCount+1][];						// sets will index by their corresponding number of bits, which indexes from 1        
        for (int i = 1; i < A.length; i++) {
        	float[] a = new float[data.length];
        	A[i] = a;
        	Arrays.fill(a, Float.POSITIVE_INFINITY);			// to be able to see which values are never used, and so optimize the space needed
        }
        
    	for (int m = 1; m < setsByMemberCount.length; m++) { 		// the subproblem size (ie. budget) loop

//            if (debug) System.out.println(String.format("\tsub-problem size=%d", m));

        	int[] subProblemSets = setsByMemberCount[m];	// sets are grouped by size

        	for (int s : subProblemSets) {
//                if (debug) System.out.println(String.format("\t\tprocess set s=%s (%d)", Integer.toBinaryString(s), s));

				for (int j = 1; j < data.length; j++) {	// process every vertex j except the starting one
					int tj = 1 << (j-1);				// test mask j doesn't include the start vertex (ie. j=1 mask = 001, not 010
					
					if ((s & tj) != tj) {				// ignore if it's not in the set
// 	                    if (debug) System.out.println(String.format("\t\t\tignore j=%s (%d) not in s", Integer.toBinaryString(tj), tj));
						continue;
					}
					
//                    if (debug) System.out.print(String.format("\t\t\tconsider j=%s (%d), getMin=", Integer.toBinaryString(tj), tj));
                    A[s][j] = getMinForSetWithoutJ(m-1, s, j, tj, data, A);
				}
	        }
    	}

    
        // the set that visits every point is the last one, and there's only one set of that count (all bits set)
        int completeS = setsByMemberCount[data.length-1][0];
		float minPathOverSet = Float.MAX_VALUE;
		float[] locS = data[START_VERTEX];
        if (debug) System.out.println(String.format("compute final min len for tour starting at vx=%.2f, vy=%.2f", locS[0], locS[1]));            
		
		for (int j = 1; j < data.length; j++) {	// process every vertex j except the start
			int tj = 1 << j;					// test j
			
			float minForJ = A[completeS][j];
			float distSj = getDistanceToJ(data, locS, j);
			float possPath = minForJ + distSj;
			
            if (debug) System.out.println(String.format("\tconsider j=%s (%d), minForJ=%.2f, dist=%.2f, possPath=%.2f, shortest=%s"
            		, Integer.toBinaryString(tj), j+1, minForJ, distSj, possPath, Boolean.toString(possPath < minPathOverSet)));
            
            if (possPath < minPathOverSet) {
            	minPathOverSet = possPath;
            }
		}
		
		// print the memory usage
		Runtime rt = Runtime.getRuntime();
		System.out.println(String.format("used memory %dMB", (rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L)));
		
		if (debug) {
			// analyze the usage of memory in the array... how many have not been touched
			for (int i = 0; i < A.length; i++) {
				if (A[i] == null) {
					System.out.println(String.format("\t A[i=%d] not used", i));
				}
				else {
					int unTouched = 0;
					for (int j = 0; j < A[i].length; j++) {
						if (Float.isInfinite(A[i][j])) {
							unTouched++;
						}
					}
					System.out.println(String.format("\t A[i=%d] len=%d, untouched=%d", i, A[i].length, unTouched));
				}
			}
		}
		
		printResults(source, hint, String.format("%.2f", minPathOverSet));
    }

	private static float getDistanceToJ(float[][] data, float[] fromLoc, int j) {
		float[] locJ = data[j];
		float x = locJ[0] - fromLoc[0];
		float y = locJ[1] - fromLoc[1];
		return (float)Math.sqrt(x*x + y*y);
	}

	private static float getMinForSetWithoutJ(int mLessOne, int s, int j, int tj, float[][] data, float[][] A) {

		// base case
		if (mLessOne == 0) {	// smaller m is 0, the result is the distance from j to the start 
			float distSj = getDistanceToJ(data, data[START_VERTEX], j);
			
//			if (debug) System.out.println(String.format("[StartToJ] dist=%.2f", distSj));

            return distSj;
		}
		
		// s is a bit mask of the complete set, so switching off j (tj is the single bit set for j)
		// produces the number that is the set less j, and that is the index into the data results A
		int sLessJ = s & ~tj;
//		if (debug) System.out.println(String.format("[sLessJ=%s]", Integer.toBinaryString(sLessJ)));

		float minPathOverSet = Float.MAX_VALUE;
		// each remaining element is a value of k, get the min of the kth element from that previous set (not including the start)
		// plus the distance from that to the new point j
		for (int k = 1; k < data.length; k++) {
			int tk = 1 << (k-1);					// k is the index to the data, but in the set which excludes the start, it shifts -1
			
			if ((sLessJ & tk) == tk) {
				float minLastTime = A[sLessJ][k];
				float distKj = getDistanceToJ(data, data[k], j); 
				float possPath = minLastTime + distKj;
				
                if (possPath < minPathOverSet) {
                	minPathOverSet = possPath;
                }
			}
		}
		
		return minPathOverSet; 
	}

	private static int getMaxNumSetsForAnyMemberCount(float[][] data, int setCount) {

		int[] memCounts = new int[data.length]; 

        // add the number that corresponds to the set to the list for that bit count
        for (int i = 1; i <= setCount; i++) {
        	int bc = Integer.bitCount(i);
        	memCounts[bc]++;
        }

        int maxCount = 0;
        for (int memCount : memCounts) {
        	if (memCount > maxCount) {
        		maxCount = memCount;
        	}
        }
        
        return maxCount;
	}

	private static int[][] makeSets(float[][] data, int setCount) {
    	// create a list for each count of bits set, ie. group sets by their bit counts 
        @SuppressWarnings("unchecked")
		ArrayList<Integer>[] sets = new ArrayList[data.length]; // use the full length, the first is 0 length, but they index better
        for (int i = 1; i < data.length; i++) {
        	sets[i] = new ArrayList<Integer>();
        }

        // add the number that corresponds to the set to the list for that bit count
        for (int i = 1; i <= setCount; i++) {
        	int bc = Integer.bitCount(i);
        	sets[bc].add(i);
        }
        
        int[][] setsByMemberCount = new int[sets.length][];
        
        for (int i = 1; i < sets.length; i++) {

        	if (debug) System.out.println(String.format("number of sets of size %d = %d", i, sets[i].size()));
        	
        	setsByMemberCount[i] = new int[sets[i].size()];
        	
        	for (int j = 0; j < sets[i].size(); j++) {
//        		if (debug) System.out.println(String.format("\t includes %s (%d)", Integer.toBinaryString(sets[i].get(j)), sets[i].get(j)));
        		
        		setsByMemberCount[i][j] = sets[i].get(j);
        	}
        }
        
        return setsByMemberCount;
	}

    private static void printResults(String source, String hint, String result) {
        System.out.println(String.format("result=%s, source='%s', hint='%s', elapsed millis=%d", result, source, hint, System.currentTimeMillis() - start));
    }
    
    public static void main(String[] args) {
        start = System.currentTimeMillis();
        if (args.length == 1) {
            if (args[0].equals("w5_tsp.txt")) {
            	computeShortestTour("answer: 26442.73", args[0]); 
            }
            else {
            	computeShortestTour("answer: ?", args[0]);
            }
        }
        else {
        	// this runs fast, so use as a test
        	debug = true;
//        	computeShortestTour("answer: 7.41", "w5_test1.txt");
            start = System.currentTimeMillis();
//        	computeShortestTour("answer: 4", "internal test1", new float[][] { new float[] {0.0f, 0.0f}, new float[] {0.0f, 1.0f}, new float[] {1.0f, 1.0f}, new float[] {1.0f, 0.0f} });
        	computeShortestTour("answer: 10.06", "internal test2", new float[][] { 
        			new float[] {0.0f, 0.0f}, new float[] {0.0f, 1.0f}, new float[] {1.0f, 1.0f}, new float[] {1.0f, 2.0f}, new float[] {2.0f, 1.0f}, new float[] {4.0f, 2.0f} });
        }
    }
}
