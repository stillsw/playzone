import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.PriorityQueue;

/*
Computes the shortest distance in a graph over all start and end vertices. 
Uses Bellman-Ford algorithm and re-weighting, and spits out any graph that contains a negative cycle. 

IMPORTANT: the non-optimized version does loads of extra path searches using Dijkstra's algorithm, but
it's all unnecessary
*/

public class w4_asgn_all_pairs_shortest_path_optimized {
    
    private static boolean debug = false;
    private static long start;
    
    private static int minOverallPath = Integer.MAX_VALUE;
    
    private static void computeAnyShortestPath(String hint, String fname) {
        
		File file = new File(System.getProperty("user.dir"), fname);
        int l = 0;
        
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            
        	String line = br.readLine(); 			// first line is summary data
            String[] summary = line.split("\\s");
            int N = Integer.parseInt(summary[0]);            
            int M = Integer.parseInt(summary[1]);            
            int[][] data = new int[M][];
            int minEdgeCost = Integer.MAX_VALUE;	// trap the trivial no negative edges, then shortest path is the shortest edge
            
            while ((line = br.readLine()) != null) {
            	String[] strs = line.split("\\s");
                int v = Integer.parseInt(strs[0]), w = Integer.parseInt(strs[1]), c = Integer.parseInt(strs[2]);
                
                minEdgeCost = Math.min(minEdgeCost, c);
                
                data[l++] = new int[] { v, w, c };
            }
            
            if (debug) {
            	for (int[] la : data) {
            		System.out.println(String.format("data array line contains array len=%d, v=%d, w=%d, c=%d", la.length, la[0], la[1], la[2]));
            	}
            }
            
            if (minEdgeCost >= 0) {
        		printResults(fname, hint, Integer.toString(minEdgeCost));
            }
            else {
            	computeAnyShortestPath(hint, fname, N, data);
            }

        }
        catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
        }        
    }

    private static void computeAnyShortestPath(String hint, String source, int N, int[][] data) {
    	
    	// add a new vertex to every other vertex with cost 0
    	
    	int s = N;	// append it to the end
    	N++;		// so must increment N
    	
        HashMap<Integer, ArrayList<int[]>> incomingEdges = makeVertexLists(N, s, data);
        
        try {
            // compute all the distances from s
        	int[] weightings = computeBellmanFord(N, s, incomingEdges);
        	
        	System.out.println(String.format("completed bellman-ford weightings in %d, min path=%d", System.currentTimeMillis() - start, minOverallPath));
    		
    		printResults(source, hint, Integer.toString(minOverallPath));
        }
        catch (IllegalStateException e) {
        	System.out.println("should be negative cycle exception thrown: "+e.getMessage());
        	printResults(source, hint, "NULL");
        }
    	
    }

	private static HashMap<Integer, ArrayList<int[]>> makeVertexLists(int N, int s, int[][] data) {
		HashMap<Integer, ArrayList<int[]>> nodesEdges = new HashMap<Integer, ArrayList<int[]>>(N);
		
        for (int i = 0; i < data.length; i++) { 

        	int fromW = data[i][0]-1;
        	int toV = data[i][1]-1;
    		
        	ArrayList<int[]> incomingEdges = nodesEdges.get(toV);
    		if (incomingEdges == null) {						// not already there, lazy init
    			incomingEdges = new ArrayList<int[]>();
        		incomingEdges.add(new int[] { s, 0 });			// add s to each list
    			nodesEdges.put(toV, incomingEdges);
    		}

            incomingEdges.add(new int[] { fromW, data[i][2] } );            	
        }

		return nodesEdges;
	}

    private static int[] computeBellmanFord(int N, int s, HashMap<Integer, ArrayList<int[]>> incomingEdges) {

        // to reconstruct the path would need the full NxN   
    	//int[][] A = new int[N][N];			
        int[][] A = new int[2][N];
        Arrays.fill(A[0], Integer.MAX_VALUE); // base case for the source vertex, 0, otherwise infinity
        A[0][s] = 0;
        final int ai = 1;						// index to first row
        int lastI = ai;
        minOverallPath = Integer.MAX_VALUE;		// reset in case run > 1 time (like for tests)

    	for (int i = 1; i <= N; i++) {			// extra iteration checks for negative cycles

    		boolean somethingUpdated = false; 	// can jump out early if nothing is changed during an iteration
    		
    		for (Entry<Integer, ArrayList<int[]>> entry : incomingEdges.entrySet()) {
    			
    			int v = entry.getKey();
    			
    			if (v == s) {					// value is already 0, just leave the source vertex alone
    				continue;
    			} 
    			
    			int inherit = A[ai-1][v];

    			// find the minimum cost of adding an edge to one of values for a vertex that directs to this one
    			int minWc = inherit;
    			
        		// first index is the node index itself (ie. same as i), but after that there are pairs
        		// of ints, the 1st in the pair is the node that is directed at this node, and the 2nd is the cost
    			for (int[] m : entry.getValue()) {
        			int w = m[0];
        			int c = m[1];
        			
        			int inheritW = A[ai-1][w]; // as int doesn't have infinity, test for it, don't add c to max_value 
        			if (inheritW != Integer.MAX_VALUE) {
        				
        				if (inheritW + c < minWc) {
        					minWc = inheritW + c;
        				}
        			}
        		}
        		
        		if (i == N) { 					// last run through to detect negative cycles
            		if (minWc < inherit) {
            			throw new IllegalStateException("negative cycle detected, can't complete");
            		}
        		}
        		else {
            		lastI = ai; 				// for getting results, even if jump out early
            									// leaving it in for more clarity if ever want to use this for full reconstruction
            		
        			// update the value with the min of the 2 values
	        		if (minWc < inherit) {
	        			somethingUpdated = true;
	        			A[ai][v] = minWc;
	        			minOverallPath = Math.min(minOverallPath, minWc);
	        		}
	        		else {
	        			A[ai][v] = inherit;
	        		}
        		}
    		}
    		
    		if (!somethingUpdated) { 			// nothing changed, return the value now
    			break;
    		}
    		
    		if (i != N) {						// goes to the Nth iteration because checks for negative cycles
    											// copy new values to previous iteration, because using x2 array instead of xN
    			System.arraycopy(A[ai], 0, A[ai-1], 0, A[ai].length);
    		}
    	}

    	return A[lastI];
	}

    private static void printResults(String source, String hint, String result) {
        System.out.println(String.format("result=%s, source='%s', hint='%s', elapsed millis=%d", result, source, hint, System.currentTimeMillis() - start));
    }
    
    public static void main(String[] args) {
        start = System.currentTimeMillis();
        if (args.length == 1) {
            if (args[0].equals("1")) { 
            	computeAnyShortestPath("answer: NULL", "w4_g1.txt"); 
            }
            else if (args[0].equals("2")) { 
            	computeAnyShortestPath("answer: NULL", "w4_g2.txt"); 
            }
            else if (args[0].equals("3")) { 
            	computeAnyShortestPath("answer: -19", "w4_g3.txt"); 
            }
            else if (args[0].equals("big")) { 
            	System.out.println("WANNA RUN THE BIG FILE? unzip it first, it's 13MB");
            	computeAnyShortestPath("answer: -6", "w4_large.txt"); 
            }
            else {
                System.out.println("usage w4_asgn_all_pairs_shortest_path_optimized [1|2|3|big] or leave param blank for tests");
            }
        }
        else {
        	// this runs fast, so use as a test
        	computeAnyShortestPath("answer: -19", "w4_g3.txt");
        }
    }
}
