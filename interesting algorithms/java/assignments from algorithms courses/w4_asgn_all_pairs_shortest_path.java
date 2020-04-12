import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.PriorityQueue;

/*
Computes the shortest path in a graph over all start and end vertices. Uses Johnson's algorithm to do this, which 
incorporates Bellman-Ford algorithm and re-weighting, then Dijkstra on every result.
Graph is directed and may include negative edge costs.
Does not work for negative cost cycles, but will spit out any graph that contains one. 

IMPORTANT: this is not the optimal way to get the solution, because the re-weighting step alone will do that.
This class is therefore just a reference for how you might do the job of finding all paths, it contains Dijkstra's
algo, which is interesting anyway. Playing around with some optimizations still too.
See w4_asgn_all_pairs_shortest_path_optimized.java for slimmed down version.
*/

public class w4_asgn_all_pairs_shortest_path {
    
	// indices
	private static final int VERTEX = 0;
	private static final int COST = 1;
	
    private static boolean debug = false;
    private static long start;
    
    private static int[][] directedEdges;
    private static boolean[] containsNegativeEdge;
    private static boolean[] containsIncomingNegativeEdge;
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
    	
    	// use johnson's algorithm
    	// add a new vertex to every other vertex with cost 0
    	
    	int s = N;	// append it to the end
    	N++;		// so must increment N
    	
        HashMap<Integer, ArrayList<int[]>> incomingEdges = makeVertexLists(N, data);
        
        for (ArrayList<int[]> edges : incomingEdges.values()) {
        	edges.add(new int[] { s, 0 });	// add to each list
        }

        try {
            // compute all the distances from s
        	int[] weightings = computeBellmanFord(N, s, incomingEdges);
        	
        	System.out.println(String.format("completed bellman-ford weightings in %d, min path=%d", System.currentTimeMillis() - start, minOverallPath));
        	System.out.println("NOTE: for the problem at hand, that's actually already the correct answer, the rest here is unnecessary!");
        	
        	// result is all the computed pv values
        	// use these to modify costs for Dijkstra
        	int leastCost = Integer.MAX_VALUE;
        	
    		for (int u = 0; u < weightings.length-1; u++) {			// don't test against s (which is the last in the list)

    			if (debug) System.out.println(String.format("pu(%d)=%d (ie. shortest distance from s)", u+1, weightings[u]));

    			// make sure it contains at least one negative outgoing edge, otherwise it can't be part of a shortest path
    			// since it's the starting point on a path
	    		int[] successors = directedEdges[u];
	    		if (successors == null) {							// no successors, nothing to do
    				if (debug) System.out.println("\tcontains no directed edges, ignore"); 
	    			continue;
	    		}
	    		
	    		if (!containsNegativeEdge[u]) {						// don't run djistra if no edges are negative
    				if (debug) System.out.println("\tcontains no negative edges, ignore"); 
	    			continue;
	    		}
	    		
    			for (int v = 0; v < weightings.length-1; v++) { 	// last one is s, ignore that
    				if (v == u 										// ignore self
    					|| !containsIncomingNegativeEdge[v]) {		// and any destinations in a path that has only positive edges to the end
        				if (debug && v != u) System.out.println(String.format("\t\tdestination v=%d contains no negative edges, ignore", v+1)); 
    					continue;
    				}
    				
    				int c = runDijkstra(u, v, weightings);
    				if (c < leastCost) {
    					leastCost = c;
    				}

    				if (debug) {
            			System.out.println(String.format("\tshortest distance from u(%d) to v(%d) = %s", 
            					u+1, v+1, c == Integer.MAX_VALUE ? "no route" : c));
            		}
    			}    			
        	}
    		
    		printResults(source, hint, Integer.toString(leastCost));
        }
        catch (IllegalStateException e) {
        	System.out.println("should be negative cycle exception thrown: "+e.getMessage());
        	printResults(source, hint, "NULL");
        }
    	
    }

    private static Comparator<int[]> fringeComparator = new Comparator<int[]>() {

		@Override
		public int compare(int[] edge1, int[] edge2) {
			return edge1[COST] - edge2[COST];
		}
    };

	private static HashMap<Integer, Integer> closedSet = new HashMap<Integer, Integer>();
	private static PriorityQueue<int[]> fringe = new PriorityQueue<int[]>(1000, fringeComparator);

    private static int runDijkstra(int u, int v, int[] weightings) {
    	// all costs are added modified by adding pu - pv (these are subtracted out afterwards by the caller)
    	
		closedSet.clear();
		fringe.clear();

    	fringe.add(new int[] { u, 0 } );				// add the start vertex to the fringe, accum cost 0
    	
    	while (fringe.size() > 0) {
    		
    		int[] edge = fringe.poll();
			//if (debug) System.out.println(String.format("\t\ttake from fringe: w=%d, accum cost=%d", edge[VERTEX], edge[COST]));
    		
    		if (edge[VERTEX] == v) { 					// goal state: reached destination, peel off the weightings
    			return edge[COST] - weightings[u] + weightings[v];
    		}
    		
    		Integer closedCost = closedSet.get(edge[VERTEX]);
    		if (closedCost != null
    			&& closedCost <= edge[COST]) { 			// no improvement in the cost via this route, remains in closed set
    			continue;
    		}
    		
    		closedSet.put(edge[VERTEX], edge[COST]);	// add to closed
    		int[] successors = directedEdges[edge[VERTEX]];
    		if (successors == null) {
    			continue;
    		}
    		
    		for (int i = 0; i < successors.length; i += 2) {	// successors, pairs of vertex/costs
    			if (edge[COST] - weightings[u] + weightings[v] 
    				 + successors[i+1] >= 0) {					// unweighted cost goes over 0, ignore it
    				continue;
    			}
    			
    			int weightedCost = successors[i+1]				// c' = c + pu - pv
    					+ weightings[edge[VERTEX]] - weightings[successors[i]];
    			
    			if (edge[VERTEX] != u
    					|| (edge[VERTEX] == u && successors[i+1] < 0)) {
    				
    				//if (debug) System.out.println(String.format("\t\tadding to fringe: w=%d, accum cost=%d", successors[i]+1, edge[COST] + weightedCost));

    				fringe.add(new int[] { successors[i], edge[COST] + weightedCost } );
    			}
    		}
    	}
    	
		return Integer.MAX_VALUE;						// never hit goal state, must not be a route to that vertex
	}

	private static void computeSpecificShortestPath(String hint, String source, int s, int t, int N, int[][] data) {
        
        // vertex, so need to have fast access to incoming edges
        HashMap<Integer, ArrayList<int[]>> incomingEdges = makeVertexLists(N+1, data);	// broke this, so the +1 is a fix for the compute specific tests only
        																				// not used in the general tests nor the full run anyway
        
        String result = null;
        try {
        	result = Integer.toString(computeBellmanFord(N, s-1, t-1, incomingEdges));
        }
        catch (IllegalStateException e) {
        	result = "NULL";
        }
        
        printResults(source, hint, result);
    }

	private static HashMap<Integer, ArrayList<int[]>> makeVertexLists(int N, int[][] data) {
		HashMap<Integer, ArrayList<int[]>> nodesEdges = new HashMap<Integer, ArrayList<int[]>>(N);
		
		int lastFrom = Integer.MIN_VALUE;		// keep the edges for the last outgoing (since they're in order)
		ArrayList<int[]> outEdges = new ArrayList<int[]>();
		
        // store the outgoing edges in a 2D array (vertex, then other vertex and cost in pairs) 
        // for faster access during the 2nd stage
		if (debug) {
			System.out.println(String.format("Num vertices=%d", N-1));
		}
		directedEdges = new int[N-1][];			// there's an extra one for the computing the weightings, so don't include it 
		containsNegativeEdge = new boolean[N-1];
		containsIncomingNegativeEdge = new boolean[N-1];
		
        for (int i = 0; i <= data.length; i++) { // go one extra just to populate the outgoing edges arrays

        	boolean done = i == data.length;
        	
        	if (i != 0 &&						// except on the first one, if either finished or the vertex changed
        		(done || data[i][0]-1 != lastFrom)) {
        		
        		directedEdges[lastFrom] = new int[outEdges.size() * 2]; // double it to compress into one array
        		containsNegativeEdge[lastFrom] = false;					// default, overwrite in the for loop
        		
        		if (debug) {
        			System.out.println(String.format("adding directedEdges for %d, size=%d", lastFrom+1, outEdges.size()));
        		}
        		int col = 0;
        		for (int[] edge : outEdges) {
        			directedEdges[lastFrom][col++] = edge[VERTEX];
        			directedEdges[lastFrom][col++] = edge[COST];
        			
        			if (edge[COST] < 0) {
        				containsNegativeEdge[lastFrom] = true;
        			}
        		}
        		
            	if (done) {						// only here to finish the outgoing edges, can now exit
            		break;
            	}
        	}
        		
        	int fromW = data[i][0]-1;
        	if (fromW != lastFrom) {
        		outEdges.clear();
        		lastFrom = fromW;
        	}

        	int toV = data[i][1]-1;
        	if (data[i][2] < 0) {
        		containsIncomingNegativeEdge[toV] = true;
        	}
        	
        	outEdges.add(new int[] { toV, data[i][2] } ); 
        	
    		ArrayList<int[]> incomingEdges = getEdgeList(nodesEdges, toV);
            incomingEdges.add(new int[] { fromW, data[i][2] } );
            	
//        	if (debug) {
//        		int[] e = edges.get(edges.size()-1); // get the last one just added
//        		System.out.println(String.format("data row i=%d, from=%d, to=%d, cost=%d, added edge from=%d, cost=%d",
//        				i, fromW+1, toV+1, data[i][2], e[0]+1, e[1]));
//        	}
        }

        if (debug) {
        	for (int i = 0; i < directedEdges.length; i++) {
        		System.out.println(String.format("directed edges i=%d, u=%d", i, i+1));
        		if (directedEdges[i] == null) {
        			System.out.println("\t has no outgoing edges");
        		}
        		else {
	        		for (int j = 0; j < directedEdges[i].length; j += 2) {
	            		System.out.println(String.format("\t v=%d, cost=%d", directedEdges[i][j]+1, directedEdges[i][j+1]));        			
	        		}
        		}
        	}
        }
        
		return nodesEdges;
	}

	private static ArrayList<int[]> getEdgeList(HashMap<Integer, ArrayList<int[]>> nodesEdges, int vertex) {
		
		ArrayList<int[]> incomingEdges = nodesEdges.get(vertex);
		if (incomingEdges == null) {						// not already there, lazy init
			incomingEdges = new ArrayList<int[]>();
			nodesEdges.put(vertex, incomingEdges);
		}
		return incomingEdges;
	}

    private static int computeBellmanFord(int N, int s, int t, HashMap<Integer, ArrayList<int[]>> incomingEdges) {
    	// run the general algorithm which returns the results and the array index of the last computed results 
    	// (need this because it could end early)
    	int[] res = computeBellmanFord(N, s, incomingEdges);
    	return res[t];
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

        		if (debug) {
        			System.out.println(String.format("budget loop i=%d, v=%d, inherit cost=%s",
            				ai, v+1, inherit == Integer.MAX_VALUE ? "inf" : Integer.toString(inherit)));
        		}
    			
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
        				
	                		if (debug) {
	                			System.out.println(String.format("\t from w=%d, tested new cost=%d, min now=%s",
	                    				w+1, inheritW + c, minWc == Integer.MAX_VALUE ? "inf" : Integer.toString(minWc)));
	                		}
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
        		if (debug) System.out.println(String.format("FINISHED EARLY i=%d", ai));
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
            else {
                System.out.println("usage w4_asgn_all_pairs_shortest_path [1|2|3] or leave param blank for tests");
            }
        }
        else {
        	System.out.println("BASIC TESTS: SHORTEST PATHS FROM/TO SPECIFIC POINTS");
            computeSpecificShortestPath("expected result: 6", "example from video 'the basic algorithm' @1:30", 1, 5, 5, new int[][] { 
            		new int[] {1, 2,2},			// from video, start 1=s, 2=v (cost 2) 
            		new int[] {1, 3,4},			// from video, start 1=s, 3=x (cost 4) 
            		new int[] {2, 3,1},			// 2=v, 3=x (cost 1), 
            		new int[] {2, 4,2},			// 2=v, 4=w (cost 2)
            		new int[] {3, 5,4},			// 3=x, 5=t (cost 4)
            		new int[] {4, 5,2} });		// 5=t, destination (no outgoing edges)

            start = System.currentTimeMillis();
            computeSpecificShortestPath("expected result: 3", "stop early test", 1, 3, 5, new int[][] { 
            		new int[] {1, 2,2}, 
            		new int[] {1, 3,4}, 
            		new int[] {2, 3,1}, 
            		new int[] {2, 4,2},
            		new int[] {3, 5,4},
            		new int[] {4, 5,2} });

            start = System.currentTimeMillis();
            computeSpecificShortestPath("expected result: -6", "nearest edge +4, shortest path -6", 1, 4, 4, new int[][] { 
            		new int[] {1, 2,-1},		// basic negative total 
            		new int[] {1, 4,4},			 
            		new int[] {2, 3,-2},		 
            		new int[] {3, 4,-3}});		

            start = System.currentTimeMillis();
//            debug = true;
            computeSpecificShortestPath("expected result: fail to complete", "contains a negative cycle", 5, 4, 5, new int[][] { 
            		new int[] {1, 2,-1},		// basic negative cycle, 1 to 2 = -1 
            		new int[] {2, 3,-2},		// 2 to 3 = -2
            		new int[] {3, 4,-3},		// 3 to 4 = -3
            		new int[] {4, 1,4},			// 4 back to 1 = 4 net cost = -2
            		new int[] {5, 1,3} });	
            debug = false;

            System.out.println("\nWIDER TESTS: ANY SHORTEST PATHS");
        	
            start = System.currentTimeMillis();
            computeAnyShortestPath("expected result: -5", "from 2 to 3 and 3 to 4", 4, new int[][] { 
            		new int[] {1, 2,1},			// basic negative total 
            		new int[] {1, 4,4},			 
            		new int[] {2, 3,-2},		 
            		new int[] {3, 4,-3}});		

            start = System.currentTimeMillis();
            computeAnyShortestPath("answer: -10003", "w4_testfile1.txt"); 

            start = System.currentTimeMillis();
            computeAnyShortestPath("answer: -6", "w4_testfile2.txt"); 

            start = System.currentTimeMillis();
            computeAnyShortestPath("answer: NULL", "w4_testfile3.txt"); 

            start = System.currentTimeMillis();
            computeAnyShortestPath("answer: 1 (trivially, since no -tve edges)", "w4_testfile4.txt"); 
        }
    }
}
