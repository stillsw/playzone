import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.PriorityQueue;

/*
Assignment week 5, traveling salesman problem try using a hybrid dijkstra/greedy approach.
Gave correct results on v small test cases, but ran forever on 25 points from homework, not sure if there's
an infinite loop, or it's just so slow that it would have finished if left alone (needed 2gb memory alloc to run)
*/

public class w5_traveling_salesman_dijkstra {

    private static final int VERTEX_IDX = 0;
    private static final int LEFT_IDX = 1;
    private static final int RIGHT_IDX = 2;
    private static final int DIST_IDX = 3;
    
    private static final int SUCCESSORS_DATA_IDX = 1;
    private static final int SUCCESSORS_LEFT_DIST_IDX = 2;
    private static final int SUCCESSORS_RIGHT_DIST_IDX = 3;
    
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
            
        	computeShortestTour(hint, fname, data);

        }
        catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
        }        
    }
	
    private static Comparator<Object[]> fringeComparator = new Comparator<Object[]>() {

		@Override
		public int compare(Object[] edge1, Object[] edge2) {
			if ((float)edge1[DIST_IDX] > (float)edge2[DIST_IDX]) {
				return 1;
			}
			if ((float)edge1[DIST_IDX] < (float)edge2[DIST_IDX]) {
				return -1;
			}
			return 0;
		}
    };

    private static Comparator<Object[]> successorsComparator = new Comparator<Object[]>() {

		@Override
		public int compare(Object[] edge1, Object[] edge2) {
			if ((float)edge1[DIST_IDX] > (float)edge2[DIST_IDX]) {
				return 1;
			}
			if ((float)edge1[DIST_IDX] < (float)edge2[DIST_IDX]) {
				return -1;
			}
			return 0;
		}
    };

//	private static HashMap<Integer, Float> closedSet = new HashMap<Integer, Float>();
	private static PriorityQueue<Object[]> fringe = new PriorityQueue<Object[]>(1000, fringeComparator);
	private static PriorityQueue<Object[]> possibleSuccessors = new PriorityQueue<Object[]>(25, successorsComparator);

    private static void computeShortestTour(String hint, String source, float[][] data) {
    	
    	if (data.length > 31) {	// using int bit mask and need an extra one to tie the loop at the end
    		throw new IllegalArgumentException("Max 31 vertices supported");
    	}
    	
    	// try an a star approach, go in 2 directions at the same time
//		closedSet.clear();
		fringe.clear();

		{
	    	float accumCost = 0;
	    	int visitedSet = 1, rightIdx = 0, leftIdx = 0; // only the first vertex at the beginning
			fringe.add(new Object[] { visitedSet, leftIdx, rightIdx, accumCost } );	
		}
		
		float totalDist = 0f;
		
    	while (!fringe.isEmpty()) {

    		Object[] edge = fringe.poll();									// take next off the fringe
    		
    		int visitedSet = (int)edge[VERTEX_IDX];
    		float dist = (float)edge[DIST_IDX];
			int leftIdx = (int)edge[LEFT_IDX];
			int rightIdx = (int)edge[RIGHT_IDX];
			int bitCount = Integer.bitCount(visitedSet);
			
    		if (bitCount == data.length + 1) {								// goal test
    			totalDist = dist;
    			break;
    		}
    		
//    		Float closedCost = closedSet.get(visitedSet);
//    		if (closedCost != null
//    			&& closedCost <= dist) {				 					// no improvement in the cost via this route, remains in closed set
//    			continue;
//    		}
//    		
//    		closedSet.put(visitedSet, dist);								// add to closed
    		
    		// successors, special case where there are no vertices left, only remains to join the ends
    		if (bitCount == data.length) {
//    			System.out.println(String.format("final loop join, dist=%.2f, loopedDist=%.2f, visited=%s"
//    					, (float)Math.sqrt(getDistanceSq(data, leftIdx, rightIdx)), dist + (float)Math.sqrt(getDistanceSq(data, leftIdx, rightIdx)), Integer.toBinaryString(visitedSet)));
    			int completeSet = visitedSet | 1 << data.length;
				fringe.add(new Object[] { completeSet, 0, 0, dist + (float)Math.sqrt(getDistanceSq(data, leftIdx, rightIdx)) } );
				continue;
    		}
    		
    		// other cases, find the nearest non-visited vertices to either of the ends and try adding them to each side
    		possibleSuccessors.clear();
    		float[] lastRightLoc = data[rightIdx], lastLeftLoc = data[leftIdx];
    		
    		for (int i = 1; i < data.length; i++) {
    			int mask = 1 << i;
    			if ((mask & visitedSet) == mask) {							// ignore already in the set, this is a closed set test
    				continue;
    			}
    			
    			float distRightSq = getDistanceSq(data, lastRightLoc, i);
    			float distLeftSq = getDistanceSq(data, lastLeftLoc, i);

    			// add to the possible successors heap
    			int possSet = visitedSet | mask;
    			possibleSuccessors.add(new Object[] { possSet, i, distLeftSq, distRightSq } );
    		}

    		// add join to both as successors
    		int maxSuccessors = 2;
    		int numSuccessors = 0;
    		while (!possibleSuccessors.isEmpty()) {
    			
//    			System.out.println(String.format("add successor to left, dist=%.2f, newDist=%.2f, visited=%s"
//				, dist, dist + (float)Math.sqrt(nearestLeft), Integer.toBinaryString(visitedSet)));
//		System.out.println(String.format("add successor to right, dist=%.2f, newDist=%.2f, visited=%s"
//				, dist, dist + (float)Math.sqrt(nearestRight), Integer.toBinaryString(visitedSet)));
    			
        		Object[] successor = possibleSuccessors.poll();				// take next off the fringe

				fringe.add(new Object[] { successor[VERTEX_IDX], successor[SUCCESSORS_DATA_IDX], rightIdx, dist + (float)Math.sqrt((float)successor[SUCCESSORS_LEFT_DIST_IDX]) } );
				if (leftIdx != rightIdx) {									// the first time both left and right are the same, so only add to one
					fringe.add(new Object[] { successor[VERTEX_IDX], leftIdx, successor[SUCCESSORS_DATA_IDX], dist + (float)Math.sqrt((float)successor[SUCCESSORS_RIGHT_DIST_IDX]) } );
				}
    			
    			if (++numSuccessors == maxSuccessors) {						// stop adding successors at the configured number
    				break;
    			}
    		}
    	}
    	
		printResults(source, hint, String.format("%.2f", totalDist));
    }

	private static float getDistanceSq(float[][] data, int i, int j) {
		return getDistanceSq(data, data[i], j);
	}

	private static float getDistanceSq(float[][] data, float[] fromLoc, int j) {
		float[] locJ = data[j];
		float x = locJ[0] - fromLoc[0];
		float y = locJ[1] - fromLoc[1];
		return x*x + y*y;
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
            start = System.currentTimeMillis();
        	computeShortestTour("answer: 7.41", "w5_test1.txt");
            start = System.currentTimeMillis();
        	computeShortestTour("answer: 4", "internal test1", new float[][] { new float[] {0.0f, 0.0f}, new float[] {0.0f, 1.0f}, new float[] {1.0f, 1.0f}, new float[] {1.0f, 0.0f} });
            start = System.currentTimeMillis();
        	computeShortestTour("answer: 10.06", "internal test2", new float[][] { 
        			new float[] {0.0f, 0.0f}, new float[] {0.0f, 1.0f}, new float[] {1.0f, 1.0f}, new float[] {1.0f, 2.0f}, new float[] {2.0f, 1.0f}, new float[] {4.0f, 2.0f} });
        }
    }
}
